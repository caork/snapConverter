package com.snapconverter.engine.quality

data class QualityReport(
    val psnrY: Double,
    val ssim: Double,
    val vmaf: Double? = null,
    val samples: Int,
    val compareWidth: Int,
    val compareHeight: Int,
) {
    val psnrLabel: String get() = ImageMetrics.psnrLabel(psnrY)
    val ssimLabel: String get() = ImageMetrics.ssimLabel(ssim)
    val vmafLabel: String? get() = vmaf?.let { ImageMetrics.vmafLabel(it) }
}
