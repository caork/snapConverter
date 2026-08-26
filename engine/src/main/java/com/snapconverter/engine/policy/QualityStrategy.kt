package com.snapconverter.engine.policy

/**
 * Maps AppQuality 0..100 to a resolution cap, fps cap, and a 1080p-HEVC bitrate.
 *
 * This is a starting table, not a claim that `quality=70` means the same QP
 * on every Snapdragon. Device-specific CQ calibration belongs in a later
 * capability model.
 */
object QualityStrategy {

    data class Anchor(
        val quality: Int,
        val bitrate1080pHevcBps: Int,
        val maxLongEdge: Int,
        val fpsCap: Int?,
    )

    val ANCHORS = listOf(
        Anchor(0, 400_000, 1280, 24),
        Anchor(30, 1_500_000, 1280, 30),
        Anchor(50, 2_500_000, 1920, 30),
        Anchor(70, 4_000_000, 1920, 30),
        Anchor(85, 6_000_000, 1920, null),
        Anchor(100, 12_000_000, Int.MAX_VALUE, null),
    )

    fun interpolate(quality: Int): Anchor {
        val q = quality.coerceIn(0, 100)
        val hiIndex = ANCHORS.indexOfFirst { it.quality >= q }.let { if (it < 0) ANCHORS.lastIndex else it }
        val loIndex = (hiIndex - 1).coerceAtLeast(0)
        val lo = ANCHORS[loIndex]
        val hi = ANCHORS[hiIndex]
        if (hi.quality == lo.quality) return lo
        val t = (q - lo.quality).toDouble() / (hi.quality - lo.quality)
        val bitrate = (lo.bitrate1080pHevcBps + (hi.bitrate1080pHevcBps - lo.bitrate1080pHevcBps) * t).toInt()
        val edge = if (t < 0.5) lo.maxLongEdge else hi.maxLongEdge
        val fps = if (t < 0.5) lo.fpsCap else hi.fpsCap
        return Anchor(q, bitrate, edge, fps)
    }

    fun scaleBitrateForPixels(
        bitrate1080p: Int,
        width: Int,
        height: Int,
        mime: String,
    ): Int {
        val pixels = (width * height).coerceAtLeast(1)
        val ref = 1920 * 1080
        val scale = (pixels.toDouble() / ref).coerceIn(0.15, 6.0)
        val codecFactor = when {
            mime.contains("hevc") || mime.contains("av01") -> 1.0
            else -> 1.6 // AVC needs more bits for similar visual quality
        }
        return (bitrate1080p * scale * codecFactor).toInt().coerceIn(80_000, 80_000_000)
    }

    /** Rough CQ mapping when encoder quality range is known. Higher AppQuality → higher codec quality. */
    fun mapToCodecQuality(appQuality: Int, encoderRange: IntRange): Int {
        val q = appQuality.coerceIn(0, 100) / 100.0
        val span = encoderRange.last - encoderRange.first
        return (encoderRange.first + span * q).toInt().coerceIn(encoderRange.first, encoderRange.last)
    }
}
