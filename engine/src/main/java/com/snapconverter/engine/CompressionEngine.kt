package com.snapconverter.engine

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.snapconverter.engine.audio.AudioExtractor
import com.snapconverter.engine.codec.HardwareCodecSelector
import com.snapconverter.engine.device.DeviceCapabilityProbe
import com.snapconverter.engine.device.DeviceCapabilityReport
import com.snapconverter.engine.image.ImageEngine
import com.snapconverter.engine.policy.CompressionRequest
import com.snapconverter.engine.policy.ImageSourceInfo
import com.snapconverter.engine.policy.MediaKind
import com.snapconverter.engine.policy.OutputImageCodec
import com.snapconverter.engine.policy.VideoSourceInfo
import com.snapconverter.engine.progress.EncodeProgress
import com.snapconverter.engine.progress.EncodeProgressListener
import com.snapconverter.engine.quality.QualityAnalyzer
import com.snapconverter.engine.quality.QualityReport
import com.snapconverter.engine.video.VideoEngine

/**
 * App-facing facade. UI code talks to this, not to MediaCodec.
 */
class CompressionEngine(
    context: Context,
    private val selector: HardwareCodecSelector = HardwareCodecSelector(),
) {
    private val appContext = context.applicationContext
    private val probe = DeviceCapabilityProbe()
    private val video = VideoEngine(appContext, selector)
    private val image = ImageEngine(appContext, selector)
    private val quality = QualityAnalyzer(appContext, selector)
    private val audio = AudioExtractor(appContext)

    fun probeDevice(): DeviceCapabilityReport = probe.probe()

    fun inspectVideo(uri: Uri): VideoSourceInfo = video.inspect(uri)

    fun inspectImage(uri: Uri): ImageSourceInfo = image.inspect(uri)

    fun compress(
        kind: MediaKind,
        input: Uri,
        outputPfd: ParcelFileDescriptor,
        request: CompressionRequest,
        progress: EncodeProgressListener? = null,
    ) {
        // JPEG is the declared CPU path (see AGENTS.md constraint 4), so it is
        // the one job that does not need a Qualcomm hardware encoder.
        val needsHardwareEncoder =
            kind == MediaKind.VIDEO || request.imageCodec != OutputImageCodec.JPEG
        val caps = probeDevice()
        if (needsHardwareEncoder && !caps.v1Supported) {
            throw QualcommEncoderRequiredException(
                request.videoCodec.name,
                caps.encoders.map { it.name },
            )
        }
        when (kind) {
            MediaKind.VIDEO -> video.compress(input, outputPfd, request, progress)
            MediaKind.IMAGE -> {
                progress?.onProgress(EncodeProgress(ratio = 0.05f))
                image.compress(input, outputPfd, request)
                progress?.onProgress(EncodeProgress(ratio = 1f))
            }
        }
    }

    /**
     * Passthrough audio extraction (Extractor -> Muxer, M4A). No codec is
     * involved, so this is deliberately NOT gated on Qualcomm encoder
     * availability — the hardware-first rule concerns encode paths.
     */
    fun extractAudio(
        input: Uri,
        outputPfd: ParcelFileDescriptor,
        progress: EncodeProgressListener? = null,
    ): Long = audio.extract(input, outputPfd, progress)

    fun compareQuality(
        kind: MediaKind,
        original: Uri,
        encoded: Uri,
        durationUs: Long = 0L,
    ): QualityReport? {
        val width: Int
        val height: Int
        val duration: Long
        when (kind) {
            MediaKind.VIDEO -> {
                val info = video.inspect(encoded)
                width = info.displayWidth
                height = info.displayHeight
                duration = if (durationUs > 0) durationUs else info.durationUs
            }
            MediaKind.IMAGE -> {
                val info = image.inspect(encoded)
                width = info.width
                height = info.height
                duration = 0L
            }
        }
        return quality.compare(kind, original, encoded, duration, width, height)
    }
}
