package com.snapconverter.app.ui.screens

import android.media.MediaCodecInfo.EncoderCapabilities
import com.snapconverter.app.ui.UiState
import com.snapconverter.engine.codec.CodecCandidate
import com.snapconverter.engine.codec.MimeTypes
import com.snapconverter.engine.policy.BitrateEstimator
import com.snapconverter.engine.policy.BitrateModeOption
import com.snapconverter.engine.policy.CompressionMode
import com.snapconverter.engine.policy.MediaKind
import com.snapconverter.engine.policy.OutputImageCodec
import com.snapconverter.engine.policy.OutputResolution
import com.snapconverter.engine.policy.OutputVideoCodec
import com.snapconverter.engine.policy.QualityStrategy

/**
 * Footer copy: what the current settings will actually do. Each string is
 * derived from the probed encoder, never from a marketing name.
 */

internal fun selectedVideoEncoder(state: UiState): CodecCandidate? {
    val mime = when (state.videoCodec) {
        OutputVideoCodec.HEVC -> MimeTypes.HEVC
        OutputVideoCodec.AVC -> MimeTypes.AVC
        OutputVideoCodec.AV1 -> MimeTypes.AV1
    }
    return state.capabilities?.encoders?.firstOrNull {
        it.isEncoder && it.mime.equals(mime, ignoreCase = true)
    }
}

/** Estimated output size for the current quality setting, or 0 when unknown. */
internal fun estimatedOutputBytes(state: UiState): Long {
    val video = state.videoInfo ?: return 0
    val durationSec = video.durationUs / 1_000_000.0
    if (durationSec <= 0.05) return 0
    val kbps = estimatedVbrKbps(state)
    val audio = video.audioBitrateBps.takeIf { it > 0 }
        ?: BitrateEstimator.DEFAULT_AUDIO_BITRATE_BPS
    return BitrateEstimator.estimatedFileBytes(kbps * 1000, durationSec, audio)
}

private fun outputPixels(state: UiState): Pair<Int, Int> {
    val video = state.videoInfo
    val srcW = video?.displayWidth ?: state.imageInfo?.width ?: 1920
    val srcH = video?.displayHeight ?: state.imageInfo?.height ?: 1080
    val anchor = QualityStrategy.interpolate(state.quality)
    val resCap = when (state.resolution) {
        OutputResolution.ORIGINAL -> Int.MAX_VALUE
        OutputResolution.UHD_2160 -> 3840
        OutputResolution.QHD_1440 -> 2560
        OutputResolution.FHD_1080 -> 1920
        OutputResolution.HD_720 -> 1280
    }
    val qualityCap = if (state.resolution == OutputResolution.ORIGINAL) {
        anchor.maxLongEdge
    } else {
        Int.MAX_VALUE
    }
    val cap = minOf(resCap, qualityCap)
    val long = maxOf(srcW, srcH).coerceAtLeast(1)
    val scale = if (long > cap) cap.toDouble() / long else 1.0
    val outW = ((srcW * scale).toInt() / 2 * 2).coerceAtLeast(2)
    val outH = ((srcH * scale).toInt() / 2 * 2).coerceAtLeast(2)
    return outW to outH
}

private fun estimatedVbrKbps(state: UiState): Int {
    val anchor = QualityStrategy.interpolate(state.quality)
    val (outW, outH) = outputPixels(state)
    val mime = when (state.videoCodec) {
        OutputVideoCodec.HEVC -> MimeTypes.HEVC
        OutputVideoCodec.AVC -> MimeTypes.AVC
        OutputVideoCodec.AV1 -> MimeTypes.AV1
    }
    val model = QualityStrategy.scaleBitrateForPixels(
        anchor.bitrate1080pHevcBps,
        outW,
        outH,
        mime,
    ) / 1000
    val cap = state.videoInfo?.bitrateBps?.takeIf { it > 0 }
        ?.let { (it * 3L / 2L).toInt() / 1000 }
        ?: Int.MAX_VALUE
    return minOf(model, cap)
}

/** What the quality slider translates into for the selected encoder. */
internal fun qualityModeHint(state: UiState): String {
    if (state.kind != MediaKind.VIDEO) {
        return when (state.imageCodec) {
            OutputImageCodec.HEIC -> "写入 HEIC 的 quality，走硬件 HEVC still 编码。"
            OutputImageCodec.AVIF -> "写入 AVIF 的 quality，走硬件 AV1 单帧编码。"
            OutputImageCodec.JPEG -> "写入 libjpeg 的 quality，这一路在 CPU 上编码。"
        }
    }
    val encoder = selectedVideoEncoder(state) ?: return "正在读取编码器能力…"
    val cqCapable = encoder.supportsBitrateMode(EncoderCapabilities.BITRATE_MODE_CQ) &&
        encoder.qualityRange != null
    val qualityRange = encoder.qualityRange
    if (state.bitrateMode == BitrateModeOption.CQ && cqCapable && qualityRange != null) {
        val mapped = QualityStrategy.mapToCodecQuality(state.quality, qualityRange)
        return "CQ 恒定质量，KEY_QUALITY=" + mapped +
            "（范围 " + qualityRange.first + "–" + qualityRange.last + "），码率随画面复杂度变化。"
    }
    val hdr = if (state.videoInfo?.isHdr == true) "HDR 码率 ×1.5，" else ""
    val kbps = estimatedVbrKbps(state)
    val est = estimatedOutputBytes(state)
    val sizeNote = if (est > 0) "，预计输出约 " + formatSize(est) else ""
    val (outW, outH) = outputPixels(state)
    val anchor = QualityStrategy.interpolate(state.quality)
    val long = maxOf(state.videoInfo?.displayWidth ?: 0, state.videoInfo?.displayHeight ?: 0)
    val capNote = if (state.resolution == OutputResolution.ORIGINAL && long > anchor.maxLongEdge) {
        "。分辨率「原始」会被画质档限制到 " + outW + "×" + outH
    } else {
        ""
    }
    return hdr + "预计平均码率 " + kbps + " kbps" + sizeNote + capNote
}

/** One-line summary of the active compression target. */
internal fun modeHint(state: UiState): String = when (state.mode) {
    CompressionMode.QUALITY, CompressionMode.LOSSLESS_REMUX -> qualityModeHint(state)
    CompressionMode.TARGET_SIZE ->
        "范围 5%–150%。短文件受音频直通与编码器最低码率限制，结果可能略大于目标。"
    CompressionMode.TARGET_VMAF ->
        "先用 Netflix VMAF 标定能达标的最低码率，再整片编码。标定会多花一些时间。"
    CompressionMode.TARGET_SSIM ->
        "先用 SSIM 标定能达标的最低码率，再整片编码。"
    CompressionMode.TARGET_BITRATE ->
        "直接指定平均码率，画质由编码器分配。"
}

/** Advanced-parameter row subtitle: what is no longer on Auto. */
internal fun advancedSummary(state: UiState): String {
    val parts = mutableListOf<String>()
    if (state.bitrateMode != BitrateModeOption.AUTO) parts += bitrateModeLabel(state.bitrateMode)
    if (state.iFrameIntervalSec != null) parts += "I 帧 " + state.iFrameIntervalSec + "s"
    if (state.maxBFrames != null) parts += "B 帧 " + state.maxBFrames
    if (state.profile != com.snapconverter.engine.policy.VideoProfileOption.AUTO) {
        parts += profileLabel(state.profile)
    }
    if (state.complexity != com.snapconverter.engine.policy.ComplexityOption.AUTO) {
        parts += complexityLabel(state.complexity)
    }
    if (state.qpIMin != null) parts += "QP 自定义"
    return if (parts.isEmpty()) "Auto" else parts.joinToString(" · ")
}

/** Codec availability note for the output format group. */
internal fun formatHint(state: UiState): String? {
    val caps = state.capabilities ?: return null
    if (state.kind == MediaKind.IMAGE) {
        return when {
            state.imageCodec == OutputImageCodec.AVIF ->
                "AVIF 走硬件 AV1 单帧编码，体积通常比 HEIC 更小。"
            state.imageCodec == OutputImageCodec.JPEG ->
                "JPEG 没有可用 Surface 的硬件编码器，这一路由 CPU 编码，" +
                    "界面上会标成「CPU 编码」。要硬件编码请选 HEIC。"
            state.imageCodec == OutputImageCodec.HEIC ->
                "HEIC 走硬件 HEVC 单帧编码，同画质体积约为 JPEG 的一半。"
            else -> null
        }
    }
    if (state.audioOnly) {
        val audio = state.videoInfo?.audioMime?.substringAfter('/')?.uppercase() ?: "—"
        return "直通复制音轨（" + audio + "）到 M4A：不重新编码，无损且极快。"
    }
    if (state.videoCodec == OutputVideoCodec.AV1 && caps.hardwareAv1Encoder) {
        return "AV1 硬件编码：同画质体积最小，编码速度低于 H.265。"
    }
    return null
}
