package com.snapconverter.engine.video

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import com.snapconverter.engine.HdrEncodeUnavailableException
import com.snapconverter.engine.codec.CodecCandidate
import com.snapconverter.engine.codec.HardwareCodecSelector
import com.snapconverter.engine.ScLog
import com.snapconverter.engine.media.CaptureTimestamp
import com.snapconverter.engine.media.Mp4ColorProbe
import com.snapconverter.engine.media.VideoColor
import com.snapconverter.engine.media.VideoGeometry
import com.snapconverter.engine.policy.BitrateModeOption
import com.snapconverter.engine.policy.CompressionMode
import com.snapconverter.engine.policy.CompressionPolicy
import com.snapconverter.engine.policy.CompressionRequest
import com.snapconverter.engine.policy.OutputVideoCodec
import com.snapconverter.engine.policy.VideoSourceInfo
import com.snapconverter.engine.progress.EncodeProgressListener
import com.snapconverter.engine.quality.QualityAnalyzer

class VideoEngine(
    private val context: Context,
    private val selector: HardwareCodecSelector = HardwareCodecSelector(),
    private val policy: CompressionPolicy = CompressionPolicy(),
) {
    private val quality = QualityAnalyzer(context, selector)
    private val qualityCalibrator = QualityTargetCalibrator(context, selector, policy, quality)

    fun inspect(uri: Uri): VideoSourceInfo {
        val extractor = MediaExtractor()
        val retriever = MediaMetadataRetriever()
        try {
            extractor.setDataSource(context, uri, null)
            retriever.setDataSource(context, uri)
            var videoMime = "video/avc"
            var width = 0
            var height = 0
            var rotation = 0
            var frameRate = 30f
            var bitrate = 0
            var durationUs = 0L
            var audioMime: String? = null
            var audioBitrate = 0
            var color = VideoColor()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    videoMime = mime
                    val coded = codedFrameSize(format)
                    width = coded.first
                    height = coded.second
                    rotation = formatInt(format, MediaFormat.KEY_ROTATION)
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        frameRate = try {
                            format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                        } catch (_: Exception) {
                            format.getFloat(MediaFormat.KEY_FRAME_RATE)
                        }
                    }
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    }
                    color = VideoColor.from(format, mime)
                } else if (mime.startsWith("audio/")) {
                    audioMime = mime
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        audioBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                    }
                }
            }
            if (width == 0 || height == 0) {
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            }
            val retrieverRotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION,
            )?.toIntOrNull() ?: 0
            val retrieverWidth = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH,
            )?.toIntOrNull() ?: 0
            val retrieverHeight = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT,
            )?.toIntOrNull() ?: 0
            rotation = VideoGeometry.detectRotation(
                extractorRotation = rotation,
                retrieverRotation = retrieverRotation,
                codedWidth = width,
                codedHeight = height,
                retrieverWidth = retrieverWidth,
                retrieverHeight = retrieverHeight,
            )
            ScLog.i(
                "inspect coded=${width}x${height} rot=$rotation " +
                    "(extractor/retriever=$retrieverRotation ${retrieverWidth}x${retrieverHeight}) " +
                    "display=${VideoGeometry.displayWidth(width, height, rotation)}x" +
                    "${VideoGeometry.displayHeight(width, height, rotation)}",
            )
            if (durationUs == 0L) {
                durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            }
            if (bitrate == 0) {
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            }
            color = VideoColor.merge(color, VideoColor.from(retriever))
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { stream ->
                        color = VideoColor.merge(Mp4ColorProbe.probe(stream), color)
                    }
                }
            }
            ScLog.i(
                "inspect color std=${color.standard} xfer=${color.transfer} " +
                    "range=${color.range} tenBit=${color.tenBit} hdr=${color.isHdr} ${color.label}",
            )
            val identity = CaptureTimestamp.read(context, uri, videoMime)
            return VideoSourceInfo(
                width = width,
                height = height,
                rotation = rotation,
                durationUs = durationUs,
                frameRate = frameRate,
                bitrateBps = bitrate,
                mime = videoMime,
                audioMime = audioMime,
                audioBitrateBps = audioBitrate,
                displayName = identity.displayName,
                fileSizeBytes = identity.fileSizeBytes,
                captureTimeMs = identity.captureTimeMs,
                colorStandard = color.standard,
                colorRange = color.range,
                colorTransfer = color.transfer,
                hdrStaticInfo = color.hdrStaticInfo,
                tenBit = color.tenBit,
            )
        } finally {
            extractor.release()
            retriever.release()
        }
    }

    private fun codedFrameSize(format: MediaFormat): Pair<Int, Int> {
        var w = format.getInteger(MediaFormat.KEY_WIDTH)
        var h = format.getInteger(MediaFormat.KEY_HEIGHT)
        if (format.containsKey("crop-right") && format.containsKey("crop-left")) {
            w = format.getInteger("crop-right") - format.getInteger("crop-left") + 1
        }
        if (format.containsKey("crop-bottom") && format.containsKey("crop-top")) {
            h = format.getInteger("crop-bottom") - format.getInteger("crop-top") + 1
        }
        return w to h
    }

    private fun formatInt(format: MediaFormat, key: String): Int {
        if (!format.containsKey(key)) return 0
        return runCatching { format.getInteger(key) }.getOrDefault(0)
    }

    private fun formatIntOrNull(format: MediaFormat, key: String): Int? {
        if (!format.containsKey(key)) return null
        return runCatching { format.getInteger(key) }.getOrNull()
    }

    fun compress(
        input: Uri,
        outputPfd: ParcelFileDescriptor,
        request: CompressionRequest,
        progress: EncodeProgressListener? = null,
    ) {
        val source = inspect(input)
        if (source.isHdr && request.videoCodec == OutputVideoCodec.AVC) {
            throw HdrEncodeUnavailableException("H.264 cannot carry HDR. Choose H.265.")
        }
        if (request.trimEndUs > 0L && request.trimStartUs >= request.trimEndUs) {
            throw IllegalArgumentException("裁剪范围为空：终点必须大于起点。")
        }
        // Bitrate / target-size planning must run on the trimmed duration,
        // otherwise the estimator assumes the full-length clip.
        val planningSource = if (request.trims) {
            source.copy(durationUs = request.clipDurationUs(source.durationUs))
        } else {
            source
        }
        val decoder = selector.selectDecoder(source.mime)
        val encoder = selector.selectPreferredVideoEncoder(
            preferredMimes = policy.preferredMimes(request.videoCodec, hdr = source.isHdr),
            width = source.displayWidth,
            height = source.displayHeight,
        )
        val resolved = resolveMetricTarget(input, planningSource, decoder, encoder, request, progress)
        val plan = policy.planVideo(planningSource, resolved, encoder)
        val encodeProgress = if (isMetricTarget(request.mode)) {
            EncodeProgressListener { update ->
                progress?.onProgress(
                    update.copy(
                        ratio = 0.18f + update.ratio * 0.82f,
                        message = update.message ?: "正在硬件转码…",
                    ),
                )
            }
        } else {
            progress
        }
        SurfaceTranscoder(context, selector).transcode(
            input = input,
            outputPfd = outputPfd,
            source = source,
            decoder = decoder,
            encoder = encoder,
            plan = plan,
            progress = encodeProgress,
            rangeStartUs = request.trimStartUs.coerceAtLeast(0L),
            rangeEndUs = request.trimEndUs,
            copyAudio = !request.muteAudio,
        )
    }

    private fun resolveMetricTarget(
        input: Uri,
        source: VideoSourceInfo,
        decoder: CodecCandidate,
        encoder: CodecCandidate,
        request: CompressionRequest,
        progress: EncodeProgressListener?,
    ): CompressionRequest {
        val metric = when (request.mode) {
            CompressionMode.TARGET_SSIM -> QualityTargetMetric.SSIM
            CompressionMode.TARGET_VMAF -> QualityTargetMetric.VMAF
            else -> return request
        }
        val target = when (metric) {
            QualityTargetMetric.SSIM -> request.targetSsim
            QualityTargetMetric.VMAF -> request.targetVmaf
        }
        val found = qualityCalibrator.calibrate(
            input, source, decoder, encoder, request, metric, target, progress,
        )
        return request.copy(
            targetBitrateBps = found.bitrateBps,
            bitrateMode = when (request.bitrateMode) {
                BitrateModeOption.AUTO -> BitrateModeOption.VBR
                else -> request.bitrateMode
            },
            maxBitrateBps = (found.bitrateBps * 1.35).toInt(),
        )
    }

    private fun isMetricTarget(mode: CompressionMode): Boolean =
        mode == CompressionMode.TARGET_SSIM || mode == CompressionMode.TARGET_VMAF
}
