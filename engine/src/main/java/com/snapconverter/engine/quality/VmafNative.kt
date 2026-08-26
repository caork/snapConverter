package com.snapconverter.engine.quality

import com.snapconverter.engine.ScLog

internal object VmafNative {
    val available: Boolean

    init {
        available = runCatching {
            System.loadLibrary("snapvmaf")
            nativeAvailable()
        }.onFailure { ScLog.w("libsnapvmaf not loaded", it) }.getOrDefault(false)
    }

    fun scoreI420(width: Int, height: Int, ref: Array<ByteArray>, dist: Array<ByteArray>): Double? {
        if (!available || ref.isEmpty() || ref.size != dist.size) return null
        val model = if (maxOf(width, height) >= 2160) "vmaf_4k_v0.6.1" else "vmaf_v0.6.1"
        val score = nativeScoreI420(width, height, ref, dist, model)
        ScLog.i("VMAF $model ${width}x${height} n=${ref.size} score=$score")
        return score.takeIf { it in 0.0..100.0 }
    }

    private external fun nativeAvailable(): Boolean

    private external fun nativeScoreI420(
        width: Int,
        height: Int,
        ref: Array<ByteArray>,
        dist: Array<ByteArray>,
        modelVersion: String,
    ): Double
}

internal object ArgbToI420 {
    fun convert(argb: IntArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val uvW = width / 2
        val uvH = height / 2
        val out = ByteArray(ySize + 2 * uvW * uvH)
        var yi = 0
        for (y in 0 until height) {
            var x = 0
            while (x < width) {
                val c = argb[y * width + x]
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                out[yi++] = ((((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(16, 235)).toByte()
                x++
            }
        }
        var ui = ySize
        var vi = ySize + uvW * uvH
        var row = 0
        while (row < height) {
            var col = 0
            while (col < width) {
                val c0 = argb[row * width + col]
                val c1 = argb[row * width + col + 1]
                val c2 = argb[(row + 1) * width + col]
                val c3 = argb[(row + 1) * width + col + 1]
                val r = ((c0 ushr 16) and 0xFF) + ((c1 ushr 16) and 0xFF) +
                    ((c2 ushr 16) and 0xFF) + ((c3 ushr 16) and 0xFF)
                val g = ((c0 ushr 8) and 0xFF) + ((c1 ushr 8) and 0xFF) +
                    ((c2 ushr 8) and 0xFF) + ((c3 ushr 8) and 0xFF)
                val b = (c0 and 0xFF) + (c1 and 0xFF) + (c2 and 0xFF) + (c3 and 0xFF)
                val rr = r / 4
                val gg = g / 4
                val bb = b / 4
                out[ui++] = (((-38 * rr - 74 * gg + 112 * bb + 128) shr 8) + 128).coerceIn(16, 240).toByte()
                out[vi++] = (((112 * rr - 94 * gg - 18 * bb + 128) shr 8) + 128).coerceIn(16, 240).toByte()
                col += 2
            }
            row += 2
        }
        return out
    }
}
