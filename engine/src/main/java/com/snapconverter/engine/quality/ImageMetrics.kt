package com.snapconverter.engine.quality

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Full-reference metrics on 8-bit luma (BT.601).
 * PSNR-Y and SSIM are the usual pair for transcode comparison without libvmaf.
 */
object ImageMetrics {

    const val PSNR_MAX = 99.0

    fun lumaBt601(argb: IntArray, out: FloatArray = FloatArray(argb.size)): FloatArray {
        for (i in argb.indices) {
            val c = argb[i]
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            out[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        return out
    }

    fun mse(ref: FloatArray, dist: FloatArray): Double {
        val n = minOf(ref.size, dist.size).coerceAtLeast(1)
        var sum = 0.0
        for (i in 0 until n) {
            val d = (ref[i] - dist[i]).toDouble()
            sum += d * d
        }
        return sum / n
    }

    fun psnrY(ref: FloatArray, dist: FloatArray): Double {
        val err = mse(ref, dist)
        if (err < 1e-12) return PSNR_MAX
        return (10.0 * log10(255.0 * 255.0 / err)).coerceAtMost(PSNR_MAX)
    }

    /**
     * Mean SSIM over non-overlapping 8×8 windows on luma. Constants from Wang et al. 2004.
     */
    fun ssimY(ref: FloatArray, dist: FloatArray, width: Int, height: Int, window: Int = 8): Double {
        if (width < window || height < window) {
            return ssimWindow(ref, dist, 0, 0, width, height, width)
        }
        val cols = width / window
        val rows = height / window
        var sum = 0.0
        var count = 0
        for (by in 0 until rows) {
            for (bx in 0 until cols) {
                sum += ssimWindow(ref, dist, bx * window, by * window, window, window, width)
                count++
            }
        }
        return if (count == 0) 0.0 else (sum / count).coerceIn(0.0, 1.0)
    }

    private fun ssimWindow(
        ref: FloatArray,
        dist: FloatArray,
        x0: Int,
        y0: Int,
        ww: Int,
        hh: Int,
        stride: Int,
    ): Double {
        val n = (ww * hh).toDouble().coerceAtLeast(1.0)
        var meanR = 0.0
        var meanD = 0.0
        for (y in 0 until hh) {
            val row = (y0 + y) * stride + x0
            for (x in 0 until ww) {
                meanR += ref[row + x]
                meanD += dist[row + x]
            }
        }
        meanR /= n
        meanD /= n
        var varR = 0.0
        var varD = 0.0
        var cov = 0.0
        for (y in 0 until hh) {
            val row = (y0 + y) * stride + x0
            for (x in 0 until ww) {
                val r = ref[row + x] - meanR
                val d = dist[row + x] - meanD
                varR += r * r
                varD += d * d
                cov += r * d
            }
        }
        varR /= n
        varD /= n
        cov /= n
        val c1 = 6.5025 // (0.01 * 255)^2
        val c2 = 58.5225 // (0.03 * 255)^2
        val num = (2 * meanR * meanD + c1) * (2 * cov + c2)
        val den = (meanR * meanR + meanD * meanD + c1) * (varR + varD + c2)
        return if (den == 0.0) 1.0 else (num / den).coerceIn(0.0, 1.0)
    }

    fun psnrLabel(psnr: Double): String = when {
        psnr >= 45 -> "几乎无损"
        psnr >= 40 -> "优秀"
        psnr >= 30 -> "良好"
        psnr >= 20 -> "一般"
        else -> "较差"
    }

    fun ssimLabel(ssim: Double): String = when {
        ssim >= 0.99 -> "几乎无损"
        ssim >= 0.95 -> "优秀"
        ssim >= 0.90 -> "良好"
        ssim >= 0.80 -> "一般"
        else -> "较差"
    }
}
