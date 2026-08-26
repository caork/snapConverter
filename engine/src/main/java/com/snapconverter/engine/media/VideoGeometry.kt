package com.snapconverter.engine.media

/**
 * Display orientation for container-rotated video.
 *
 * Phone cameras often store 1920×1080 buffers with rotation=90, which players
 * show as 1080×1920 portrait. Encoder output must use the *display* size and
 * bake the rotation into pixels, otherwise the result is landscape.
 */
object VideoGeometry {

    fun normalizeRotation(degrees: Int): Int {
        var d = degrees % 360
        if (d < 0) d += 360
        return when (d) {
            in 45..134 -> 90
            in 135..224 -> 180
            in 225..314 -> 270
            else -> 0
        }
    }

    /**
     * Prefer a non-zero metadata rotation. If metadata is 0 but MediaMetadataRetriever
     * reports a swapped frame size, infer 90°.
     */
    fun detectRotation(
        extractorRotation: Int,
        retrieverRotation: Int,
        codedWidth: Int,
        codedHeight: Int,
        retrieverWidth: Int,
        retrieverHeight: Int,
    ): Int {
        val meta = sequenceOf(retrieverRotation, extractorRotation)
            .map { normalizeRotation(it) }
            .firstOrNull { it != 0 }
            ?: 0

        if (codedWidth <= 0 || codedHeight <= 0) return meta

        val codedPortrait = codedHeight > codedWidth
        val retrieverHasSize = retrieverWidth > 0 && retrieverHeight > 0
        val retrieverPortrait = retrieverHasSize && retrieverHeight > retrieverWidth

        if (meta != 0) {
            val displayPortrait = if (meta % 180 != 0) !codedPortrait else codedPortrait
            if (retrieverHasSize && displayPortrait != retrieverPortrait) {
                return if (retrieverPortrait != codedPortrait) 90 else 0
            }
            return meta
        }

        if (retrieverHasSize && codedPortrait != retrieverPortrait) {
            return 90
        }
        return 0
    }

    fun displayWidth(codedWidth: Int, codedHeight: Int, rotation: Int): Int =
        if (normalizeRotation(rotation) % 180 != 0) codedHeight else codedWidth

    fun displayHeight(codedWidth: Int, codedHeight: Int, rotation: Int): Int =
        if (normalizeRotation(rotation) % 180 != 0) codedWidth else codedHeight

    /**
     * Android KEY_ROTATION is clockwise. OpenGL rotateM is counter-clockwise.
     */
    fun glRotationDegrees(androidRotation: Int): Int = -normalizeRotation(androidRotation)
}
