package com.snapconverter.engine.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.opengl.EGL14
import android.os.ParcelFileDescriptor
import android.view.Surface
import androidx.heifwriter.AvifWriter
import androidx.heifwriter.EncoderPreference
import androidx.heifwriter.HeifWriter
import com.snapconverter.engine.JpegHardwareUnavailableException
import com.snapconverter.engine.codec.HardwareCodecSelector
import com.snapconverter.engine.gpu.EglCore
import com.snapconverter.engine.gpu.ImageTextureRenderer
import com.snapconverter.engine.gpu.WindowSurface
import com.snapconverter.engine.policy.CompressionPolicy
import com.snapconverter.engine.policy.CompressionRequest
import com.snapconverter.engine.policy.ImageEncodePlan
import com.snapconverter.engine.media.CaptureTimestamp
import com.snapconverter.engine.policy.ImageSourceInfo
import com.snapconverter.engine.policy.OutputImageCodec

class ImageEngine(
    private val context: Context,
    private val selector: HardwareCodecSelector = HardwareCodecSelector(),
    private val policy: CompressionPolicy = CompressionPolicy(),
) {

    fun inspect(uri: Uri): ImageSourceInfo {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        var width = 0
        var height = 0
        ImageDecoder.decodeBitmap(source) { decoder, header, _ ->
            width = header.size.width
            height = header.size.height
            decoder.setTargetSize(1, 1)
        }
        val identity = CaptureTimestamp.read(context, uri, context.contentResolver.getType(uri) ?: "image/*")
        return ImageSourceInfo(
            width = width,
            height = height,
            rotation = 0,
            mime = identity.mimeType.ifBlank { "image/*" },
            displayName = identity.displayName,
            fileSizeBytes = identity.fileSizeBytes,
            captureTimeMs = identity.captureTimeMs,
        )
    }

    fun compress(
        input: Uri,
        outputPfd: ParcelFileDescriptor,
        request: CompressionRequest,
    ) {
        val source = inspect(input)
        val plan = policy.planImage(source, request)
        when (plan.codec) {
            OutputImageCodec.HEIC -> encodeHeic(input, outputPfd, plan)
            OutputImageCodec.AVIF -> encodeAvif(input, outputPfd, plan)
            OutputImageCodec.JPEG -> encodeJpegOrThrow()
        }
    }

    private fun encodeJpegOrThrow(): Nothing {
        val jpeg = selector.findJpegHardwareEncoder()
            ?: throw JpegHardwareUnavailableException()
        // A public MediaCodec JPEG encoder, when present, almost never accepts
        // COLOR_FormatSurface. SnapConverter refuses a CPU Bitmap.compress path.
        throw JpegHardwareUnavailableException().also {
            it.initCause(
                IllegalStateException(
                    "Enumerated ${jpeg.name} but JPEG Surface encode is not wired; use HEIC.",
                ),
            )
        }
    }

    private fun encodeHeic(
        input: Uri,
        outputPfd: ParcelFileDescriptor,
        plan: ImageEncodePlan,
    ) {
        if (!selector.hasHardwareHeicEncoder()) {
            throw com.snapconverter.engine.HardwareEncoderRequiredException("HEIC/HEVC still")
        }
        encodeStillViaSurface(input, plan) {
            HeifWriter.Builder(
                outputPfd.fileDescriptor,
                plan.width,
                plan.height,
                HeifWriter.INPUT_MODE_SURFACE,
            )
                .setQuality(plan.quality)
                .setMaxImages(1)
                .setGridEnabled(false)
                .setEncoderPreference(hardwareOnlyPreference(plan))
                .build()
                .let { writer -> StillWriterSession.of(writer) }
        }
    }

    /**
     * AVIF still image: same Surface/GLES path, but the container muxer is
     * AvifWriter and the encode runs on a hardware AV1 encoder (single intra
     * frame). No software fallback exists.
     */
    private fun encodeAvif(
        input: Uri,
        outputPfd: ParcelFileDescriptor,
        plan: ImageEncodePlan,
    ) {
        if (!selector.hasHardwareAv1StillEncoder()) {
            throw com.snapconverter.engine.HardwareEncoderRequiredException("AVIF/AV1 still")
        }
        encodeStillViaSurface(input, plan) {
            AvifWriter.Builder(
                outputPfd.fileDescriptor,
                plan.width,
                plan.height,
                AvifWriter.INPUT_MODE_SURFACE,
            )
                .setQuality(plan.quality)
                .setMaxImages(1)
                .setGridEnabled(false)
                .setEncoderPreference(hardwareOnlyPreference(plan))
                .build()
                .let { writer -> StillWriterSession.of(writer) }
        }
    }

    private fun hardwareOnlyPreference(plan: ImageEncodePlan): EncoderPreference =
        EncoderPreference.Builder()
            .setEncoderType(EncoderPreference.HARDWARE_ENCODER_ONLY)
            .setBitrateMode(
                if (plan.requireConstantQuality) {
                    EncoderPreference.CONSTANT_QUALITY_MODE_ONLY
                } else {
                    EncoderPreference.CONSTANT_QUALITY_MODE_PREFERRED
                },
            )
            .build()

    /**
     * Minimal lifecycle adapter shared by HeifWriter and AvifWriter sessions.
     * The two writers disagree on start/surface ordering in heifwriter
     * 1.2.0-alpha01, so each adapter owns its own begin() sequence.
     */
    private interface StillWriterSession {
        /** Prepare and start the writer; returns the encoder input Surface. */
        fun begin(): Surface
        fun endOfStream(ptsUs: Long)
        fun awaitStop(timeoutMs: Long)
        fun close()

        companion object {
            fun of(writer: HeifWriter) = object : StillWriterSession {
                override fun begin(): Surface {
                    // HeifWriter 1.2.0-alpha01 hands out the input surface
                    // before start(); calling start() first throws
                    // "Already started" on the subsequent surface query.
                    val surface = writer.inputSurface
                    writer.start()
                    return surface
                }

                override fun endOfStream(ptsUs: Long) = writer.setInputEndOfStreamTimestamp(ptsUs)
                override fun awaitStop(timeoutMs: Long) { writer.stop(timeoutMs) }
                override fun close() = writer.close()
            }

            fun of(writer: AvifWriter) = object : StillWriterSession {
                override fun begin(): Surface {
                    writer.start()
                    return writer.inputSurface
                }

                override fun endOfStream(ptsUs: Long) = writer.setInputEndOfStreamTimestamp(ptsUs)
                override fun awaitStop(timeoutMs: Long) { writer.stop(timeoutMs) }
                override fun close() = writer.close()
            }
        }
    }

    /** Decode → GL texture → draw into the encoder Surface at the plan size. */
    private fun encodeStillViaSurface(
        input: Uri,
        plan: ImageEncodePlan,
        openSession: () -> StillWriterSession,
    ) {
        val bitmap = decodeForGpu(input)
        var session: StillWriterSession? = null
        var egl: EglCore? = null
        var window: WindowSurface? = null
        var renderer: ImageTextureRenderer? = null
        try {
            val surface = openSession().also { session = it }.begin()
            egl = EglCore()
            window = WindowSurface(egl, surface, releaseSurface = false)
            window.makeCurrent()
            renderer = ImageTextureRenderer()
            renderer.create()
            renderer.upload(bitmap)
            renderer.draw(plan.width, plan.height)
            window.setPresentationTime(0)
            window.swapBuffers()
            val active = requireNotNull(session)
            active.endOfStream(0)
            active.awaitStop(10_000)
        } finally {
            runCatching { renderer?.release() }
            runCatching { window?.release() }
            runCatching { egl?.makeNothingCurrent() }
            runCatching { egl?.release() }
            runCatching { session?.close() }
            if (!bitmap.isRecycled) bitmap.recycle()
            EGL14.eglReleaseThread()
        }
    }

    /**
     * Decode at source resolution. Resize happens in GLES, not
     * [android.graphics.Bitmap.createScaledBitmap].
     */
    private fun decodeForGpu(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }.copy(Bitmap.Config.ARGB_8888, false)
    }
}
