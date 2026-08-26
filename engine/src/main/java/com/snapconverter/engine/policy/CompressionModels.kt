package com.snapconverter.engine.policy

enum class MediaKind { VIDEO, IMAGE }

enum class CompressionMode { QUALITY, TARGET_SIZE, TARGET_BITRATE, LOSSLESS_REMUX }

enum class OutputVideoCodec { HEVC, AVC, AV1 }

enum class OutputImageCodec { HEIC, JPEG }

enum class OutputResolution {
    ORIGINAL,
    UHD_2160,
    QHD_1440,
    FHD_1080,
    HD_720,
}

enum class OutputFps {
    ORIGINAL,
    FPS_60,
    FPS_30,
    FPS_24,
}

data class VideoSourceInfo(
    val width: Int,
    val height: Int,
    val rotation: Int,
    val durationUs: Long,
    val frameRate: Float,
    val bitrateBps: Int,
    val mime: String,
    val audioMime: String?,
    val audioBitrateBps: Int,
)

data class ImageSourceInfo(
    val width: Int,
    val height: Int,
    val rotation: Int,
    val mime: String,
)

data class CompressionRequest(
    val kind: MediaKind,
    val mode: CompressionMode,
    val appQuality: Int = 70,
    val targetSizeBytes: Long? = null,
    val targetBitrateBps: Int? = null,
    val videoCodec: OutputVideoCodec = OutputVideoCodec.HEVC,
    val imageCodec: OutputImageCodec = OutputImageCodec.HEIC,
    val resolution: OutputResolution = OutputResolution.ORIGINAL,
    val fps: OutputFps = OutputFps.ORIGINAL,
)

data class VideoEncodePlan(
    val mime: String,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val bitrateBps: Int,
    val bitrateMode: Int,
    val codecQuality: Int?,
    val iFrameIntervalSec: Int,
    val maxBFrames: Int,
    val operatingRate: Int,
    val colorFormatSurface: Boolean = true,
)

data class ImageEncodePlan(
    val codec: OutputImageCodec,
    val width: Int,
    val height: Int,
    val quality: Int,
    val requireHardware: Boolean = true,
    val requireConstantQuality: Boolean = true,
)
