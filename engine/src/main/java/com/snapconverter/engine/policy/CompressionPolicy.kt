package com.snapconverter.engine.policy

import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.EncoderCapabilities
import com.snapconverter.engine.codec.CodecCandidate
import com.snapconverter.engine.codec.MimeTypes
import kotlin.math.roundToInt

class CompressionPolicy {

    fun planVideo(
        source: VideoSourceInfo,
        request: CompressionRequest,
        encoder: CodecCandidate,
    ): VideoEncodePlan {
        val display = displaySize(source)
        val capped = capResolution(display.first, display.second, request.resolution)
        val quality = QualityStrategy.interpolate(request.appQuality)
        val longEdgeCap = when (request.resolution) {
            OutputResolution.ORIGINAL -> quality.maxLongEdge
            else -> Int.MAX_VALUE
        }
        val sized = fitLongEdge(capped.first, capped.second, longEdgeCap)
        val width = alignEven(sized.first)
        val height = alignEven(sized.second)

        val sourceFps = source.frameRate.takeIf { it > 1f }?.roundToInt() ?: 30
        val fpsFromRequest = when (request.fps) {
            OutputFps.ORIGINAL -> sourceFps
            OutputFps.FPS_60 -> 60
            OutputFps.FPS_30 -> 30
            OutputFps.FPS_24 -> 24
        }
        val fps = minOf(fpsFromRequest, quality.fpsCap ?: fpsFromRequest, sourceFps)

        val mime = encoder.mime
        val durationSec = (source.durationUs / 1_000_000.0).coerceAtLeast(0.1)
        val qualityBitrate = QualityStrategy.scaleBitrateForPixels(
            quality.bitrate1080pHevcBps,
            width,
            height,
            mime,
        )

        val bitrate = when (request.mode) {
            CompressionMode.TARGET_BITRATE ->
                request.targetBitrateBps ?: qualityBitrate
            CompressionMode.TARGET_SIZE ->
                BitrateEstimator.videoBitrateForTargetSize(
                    targetBytes = request.targetSizeBytes ?: 50L * 1024 * 1024,
                    durationSeconds = durationSec,
                    audioBitrateBps = source.audioBitrateBps.takeIf { it > 0 }
                        ?: BitrateEstimator.DEFAULT_AUDIO_BITRATE_BPS,
                )
            CompressionMode.LOSSLESS_REMUX -> source.bitrateBps.takeIf { it > 0 } ?: qualityBitrate
            CompressionMode.QUALITY -> qualityBitrate
        }.coerceIn(80_000, 80_000_000)

        val cqSupported = encoder.supportsBitrateMode(EncoderCapabilities.BITRATE_MODE_CQ) &&
            encoder.qualityRange != null &&
            request.mode == CompressionMode.QUALITY

        val bitrateMode = when (request.bitrateMode) {
            BitrateModeOption.VBR -> EncoderCapabilities.BITRATE_MODE_VBR
            BitrateModeOption.CBR -> EncoderCapabilities.BITRATE_MODE_CBR
            BitrateModeOption.CQ -> EncoderCapabilities.BITRATE_MODE_CQ
            BitrateModeOption.AUTO -> when {
                cqSupported -> EncoderCapabilities.BITRATE_MODE_CQ
                encoder.supportsBitrateMode(EncoderCapabilities.BITRATE_MODE_VBR) ->
                    EncoderCapabilities.BITRATE_MODE_VBR
                encoder.supportsBitrateMode(EncoderCapabilities.BITRATE_MODE_CBR) ->
                    EncoderCapabilities.BITRATE_MODE_CBR
                else -> EncoderCapabilities.BITRATE_MODE_VBR
            }
        }

        val useCq = bitrateMode == EncoderCapabilities.BITRATE_MODE_CQ && encoder.qualityRange != null
        val codecQuality = if (useCq) {
            QualityStrategy.mapToCodecQuality(request.appQuality, encoder.qualityRange!!)
        } else {
            null
        }

        val complexity = when (request.complexity) {
            ComplexityOption.AUTO -> encoder.complexityRange?.last
            ComplexityOption.LOW -> encoder.complexityRange?.first
            ComplexityOption.HIGH -> encoder.complexityRange?.last
        }

        return VideoEncodePlan(
            mime = mime,
            width = width,
            height = height,
            frameRate = fps.coerceIn(1, 120),
            bitrateBps = bitrate,
            bitrateMode = bitrateMode,
            codecQuality = codecQuality,
            iFrameIntervalSec = request.iFrameIntervalSec
                ?: if (request.appQuality >= 85) 2 else 3,
            maxBFrames = request.maxBFrames
                ?: if (mime == MimeTypes.HEVC || mime == MimeTypes.AV1) 1 else 0,
            operatingRate = fps.coerceIn(1, 120),
            profile = profileFor(request.profile, mime),
            complexity = complexity,
            qpIMin = request.qpMin,
            qpIMax = request.qpMax,
            qpPMin = request.qpMin,
            qpPMax = request.qpMax,
        )
    }

    fun planImage(source: ImageSourceInfo, request: CompressionRequest): ImageEncodePlan {
        val sized = capResolution(source.width, source.height, request.resolution)
        return ImageEncodePlan(
            codec = request.imageCodec,
            width = alignEven(sized.first.coerceAtLeast(2)),
            height = alignEven(sized.second.coerceAtLeast(2)),
            quality = request.appQuality.coerceIn(0, 100),
            requireHardware = true,
            requireConstantQuality = request.mode == CompressionMode.QUALITY,
        )
    }

    fun mimeFor(codec: OutputVideoCodec): String = when (codec) {
        OutputVideoCodec.HEVC -> MimeTypes.HEVC
        OutputVideoCodec.AVC -> MimeTypes.AVC
        OutputVideoCodec.AV1 -> MimeTypes.AV1
    }

    fun preferredMimes(codec: OutputVideoCodec): List<String> = when (codec) {
        OutputVideoCodec.HEVC -> listOf(MimeTypes.HEVC, MimeTypes.AVC)
        OutputVideoCodec.AVC -> listOf(MimeTypes.AVC)
        OutputVideoCodec.AV1 -> listOf(MimeTypes.AV1, MimeTypes.HEVC, MimeTypes.AVC)
    }

    private fun displaySize(source: VideoSourceInfo): Pair<Int, Int> {
        return if (source.rotation % 180 != 0) {
            source.height to source.width
        } else {
            source.width to source.height
        }
    }

    private fun capResolution(width: Int, height: Int, cap: OutputResolution): Pair<Int, Int> {
        val long = capLongEdge(cap) ?: return width to height
        return fitLongEdge(width, height, long)
    }

    private fun capLongEdge(cap: OutputResolution): Int? = when (cap) {
        OutputResolution.ORIGINAL -> null
        OutputResolution.UHD_2160 -> 3840
        OutputResolution.QHD_1440 -> 2560
        OutputResolution.FHD_1080 -> 1920
        OutputResolution.HD_720 -> 1280
    }

    private fun fitLongEdge(width: Int, height: Int, maxLong: Int): Pair<Int, Int> {
        val long = maxOf(width, height)
        if (long <= maxLong || maxLong == Int.MAX_VALUE) return width to height
        val scale = maxLong.toDouble() / long
        return (width * scale).roundToInt() to (height * scale).roundToInt()
    }

    private fun alignEven(value: Int): Int = (value / 2 * 2).coerceAtLeast(2)

    private fun profileFor(option: VideoProfileOption, mime: String): Int? = when (option) {
        VideoProfileOption.AUTO -> null
        VideoProfileOption.BASELINE -> MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
        VideoProfileOption.MAIN -> if (mime == MimeTypes.HEVC) {
            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain
        } else {
            MediaCodecInfo.CodecProfileLevel.AVCProfileMain
        }
        VideoProfileOption.HIGH -> MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
        VideoProfileOption.MAIN10 -> MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
    }
}
