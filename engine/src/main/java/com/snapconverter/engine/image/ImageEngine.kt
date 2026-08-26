package com.snapconverter.engine.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.opengl.EGL14
import android.os.ParcelFileDescriptor
import android.view.Surface
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
        val bitmap = decodeForGpu(input)
        var writer: HeifWriter? = null
        var egl: EglCore? = null
        var window: WindowSurface? = null
        var renderer: ImageTextureRenderer? = null
        try {
            val preference = EncoderPreference.Builder()
                .setEncoderType(EncoderPreference.HARDWARE_ENCODER_ONLY)
                .setBitrateMode(
                    if (plan.requireConstantQuality) {
                        EncoderPreference.CONSTANT_QUALITY_MODE_ONLY
                    } else {
                        EncoderPreference.CONSTANT_QUALITY_MODE_PREFERRED
                    },
                )
                .build()
            writer = HeifWriter.Builder(
                outputPfd.fileDescriptor,
                plan.width,
                plan.height,
                HeifWriter.INPUT_MODE_SURFACE,
            )
                .setQuality(plan.quality)
                .setMaxImages(1)
                .setGridEnabled(false)
                .setEncoderPreference(preference)
                .build()
            writer.start()
            val surface: Surface = writer.inputSurface
            egl = EglCore()
            window = WindowSurface(egl, surface, releaseSurface = false)
            window.makeCurrent()
            renderer = ImageTextureRenderer()
            renderer.create()
            renderer.upload(bitmap)
            renderer.draw(plan.width, plan.height)
            window.setPresentationTime(0)
            window.swapBuffers()
            writer.setInputEndOfStreamTimestamp(0)
            writer.stop(10_000)
        } finally {
            runCatching { renderer?.release() }
            runCatching { window?.release() }
            runCatching { egl?.makeNothingCurrent() }
            runCatching { egl?.release() }
            runCatching { writer?.close() }
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
