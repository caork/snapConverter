package com.snapconverter.engine.policy

import com.snapconverter.engine.media.VideoGeometry

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

enum class BitrateModeOption { AUTO, VBR, CBR, CQ }

enum class VideoProfileOption { AUTO, BASELINE, MAIN, HIGH, MAIN10 }

enum class ComplexityOption { AUTO, LOW, MEDIUM, HIGH }

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
    val displayName: String = "",
    val fileSizeBytes: Long = 0,
    val captureTimeMs: Long? = null,
    val colorStandard: Int? = null,
    val colorRange: Int? = null,
    val colorTransfer: Int? = null,
) {
    val displayWidth: Int get() = VideoGeometry.displayWidth(width, height, rotation)
    val displayHeight: Int get() = VideoGeometry.displayHeight(width, height, rotation)
    val isPortrait: Boolean get() = displayHeight > displayWidth
}

data class ImageSourceInfo(
    val width: Int,
    val height: Int,
    val rotation: Int,
    val mime: String,
    val displayName: String = "",
    val fileSizeBytes: Long = 0,
    val captureTimeMs: Long? = null,
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
    val bitrateMode: BitrateModeOption = BitrateModeOption.AUTO,
    val maxBitrateBps: Int? = null,
    val iFrameIntervalSec: Int? = null,
    val maxBFrames: Int? = null,
    val profile: VideoProfileOption = VideoProfileOption.AUTO,
    val complexity: ComplexityOption = ComplexityOption.AUTO,
    val qpIMin: Int? = null,
    val qpIMax: Int? = null,
    val qpPMin: Int? = null,
    val qpPMax: Int? = null,
)

data class VideoEncodePlan(
    val mime: String,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val bitrateBps: Int,
    val maxBitrateBps: Int? = null,
    val bitrateMode: Int,
    val codecQuality: Int?,
    val iFrameIntervalSec: Int,
    val maxBFrames: Int,
    val operatingRate: Int,
    val colorFormatSurface: Boolean = true,
    val profile: Int? = null,
    val level: Int? = null,
    val complexity: Int? = null,
    val qpIMin: Int? = null,
    val qpIMax: Int? = null,
    val qpPMin: Int? = null,
    val qpPMax: Int? = null,
    val qpBMin: Int? = null,
    val qpBMax: Int? = null,
    val colorStandard: Int? = null,
    val colorRange: Int? = null,
    val colorTransfer: Int? = null,
)

data class ImageEncodePlan(
    val codec: OutputImageCodec,
    val width: Int,
    val height: Int,
    val quality: Int,
    val requireHardware: Boolean = true,
    val requireConstantQuality: Boolean = true,
)
