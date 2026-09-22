package com.snapconverter.app.ui.screens

import com.snapconverter.engine.policy.BitrateModeOption
import com.snapconverter.engine.policy.ComplexityOption
import com.snapconverter.engine.policy.CompressionMode
import com.snapconverter.engine.policy.OutputFps
import com.snapconverter.engine.policy.OutputImageCodec
import com.snapconverter.engine.policy.OutputResolution
import com.snapconverter.engine.policy.OutputVideoCodec
import com.snapconverter.engine.policy.VideoProfileOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Value formatting and option labels shared by the screen and its sheets. */

internal fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> "%.2f GB".format(Locale.US, gb)
        mb >= 1 -> "%.1f MB".format(Locale.US, mb)
        else -> "%.0f KB".format(Locale.US, kb)
    }
}

internal fun formatBitrate(bps: Number): String {
    val v = bps.toLong()
    return when {
        v >= 100_000_000 -> "%.1f Gbps".format(Locale.US, v / 1_000_000_000.0)
        v >= 1_000_000 -> "%.1f Mbps".format(Locale.US, v / 1_000_000.0)
        else -> "%d kbps".format(Locale.US, (v + 500) / 1000)
    }
}

internal fun formatDuration(durationUs: Long): String {
    val sec = durationUs / 1_000_000.0
    return if (sec < 60) {
        "%.1fs".format(Locale.US, sec)
    } else {
        "%d:%02d".format(Locale.US, (sec / 60).toInt(), (sec % 60).toInt())
    }
}

internal fun formatClock(sec: Int): String =
    "%d:%02d".format(Locale.US, sec / 60, sec % 60)

internal fun formatEta(ms: Long): String {
    val sec = (ms / 1000.0).toInt().coerceAtLeast(1)
    return if (sec < 60) sec.toString() + "s" else (sec / 60).toString() + "m" + (sec % 60) + "s"
}

internal fun formatCapture(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

internal fun sourceCodecLabel(mime: String): String = when {
    mime.contains("hevc", ignoreCase = true) || mime.contains("heif", ignoreCase = true) -> "HEVC"
    mime.contains("avc", ignoreCase = true) -> "H.264"
    mime.contains("av01", ignoreCase = true) || mime.contains("av1", ignoreCase = true) -> "AV1"
    mime.contains("prores", ignoreCase = true) -> "ProRes"
    else -> mime.substringAfter('/').uppercase()
}

internal fun videoCodecLabel(codec: OutputVideoCodec): String = when (codec) {
    OutputVideoCodec.HEVC -> "H.265"
    OutputVideoCodec.AVC -> "H.264"
    OutputVideoCodec.AV1 -> "AV1"
}

internal fun imageCodecLabel(codec: OutputImageCodec): String = when (codec) {
    OutputImageCodec.HEIC -> "HEIC"
    OutputImageCodec.AVIF -> "AVIF"
    OutputImageCodec.JPEG -> "JPEG"
}

internal fun resolutionLabel(resolution: OutputResolution): String = when (resolution) {
    OutputResolution.ORIGINAL -> "原始"
    OutputResolution.UHD_2160 -> "2160p"
    OutputResolution.QHD_1440 -> "1440p"
    OutputResolution.FHD_1080 -> "1080p"
    OutputResolution.HD_720 -> "720p"
}

internal fun fpsLabel(fps: OutputFps): String = when (fps) {
    OutputFps.ORIGINAL -> "原始"
    OutputFps.FPS_60 -> "60"
    OutputFps.FPS_30 -> "30"
    OutputFps.FPS_24 -> "24"
}

internal fun modeLabel(mode: CompressionMode): String = when (mode) {
    CompressionMode.QUALITY -> "画质"
    CompressionMode.TARGET_SIZE -> "目标大小"
    CompressionMode.TARGET_VMAF -> "目标 VMAF"
    CompressionMode.TARGET_SSIM -> "目标 SSIM"
    CompressionMode.TARGET_BITRATE -> "平均码率"
    CompressionMode.LOSSLESS_REMUX -> "直通复制"
}

internal fun bitrateModeLabel(mode: BitrateModeOption): String = when (mode) {
    BitrateModeOption.AUTO -> "Auto"
    BitrateModeOption.VBR -> "VBR"
    BitrateModeOption.CBR -> "CBR"
    BitrateModeOption.CQ -> "CQ"
}

internal fun profileLabel(profile: VideoProfileOption): String = when (profile) {
    VideoProfileOption.AUTO -> "Auto"
    VideoProfileOption.BASELINE -> "Baseline"
    VideoProfileOption.MAIN -> "Main"
    VideoProfileOption.MAIN10 -> "Main10"
    VideoProfileOption.HIGH -> "High"
}

internal fun complexityLabel(complexity: ComplexityOption): String = when (complexity) {
    ComplexityOption.AUTO -> "Auto"
    ComplexityOption.LOW -> "Low"
    ComplexityOption.MEDIUM -> "Medium"
    ComplexityOption.HIGH -> "High"
}
