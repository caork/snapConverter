package com.snapconverter.engine

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.snapconverter.engine.codec.HardwareCodecSelector
import com.snapconverter.engine.device.DeviceCapabilityProbe
import com.snapconverter.engine.device.DeviceCapabilityReport
import com.snapconverter.engine.image.ImageEngine
import com.snapconverter.engine.policy.CompressionRequest
import com.snapconverter.engine.policy.ImageSourceInfo
import com.snapconverter.engine.policy.MediaKind
import com.snapconverter.engine.policy.VideoSourceInfo
import com.snapconverter.engine.video.TranscodeProgress
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

    fun probeDevice(): DeviceCapabilityReport = probe.probe()

    fun inspectVideo(uri: Uri): VideoSourceInfo = video.inspect(uri)

    fun inspectImage(uri: Uri): ImageSourceInfo = image.inspect(uri)

    fun compress(
        kind: MediaKind,
        input: Uri,
        outputPfd: ParcelFileDescriptor,
        request: CompressionRequest,
        progress: TranscodeProgress? = null,
    ) {
        val caps = probeDevice()
        if (!caps.v1Supported) {
            throw QualcommEncoderRequiredException(
                request.videoCodec.name,
                caps.encoders.map { it.name },
            )
        }
        when (kind) {
            MediaKind.VIDEO -> video.compress(input, outputPfd, request, progress)
            MediaKind.IMAGE -> image.compress(input, outputPfd, request)
        }
    }
}
