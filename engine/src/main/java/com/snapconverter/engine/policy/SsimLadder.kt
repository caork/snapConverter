package com.snapconverter.engine.policy

/**
 * Minimum bitrate such that a monotonic SSIM-vs-bitrate probe stays ≥ target.
 */
object SsimLadder {

    data class Result(
        val bitrateBps: Int,
        val score: Double,
        val iterations: Int,
        val metTarget: Boolean,
    ) {
        val ssim: Double get() = score
    }

    data class Window(val startUs: Long, val endUs: Long) {
        val durationUs: Long get() = (endUs - startUs).coerceAtLeast(1L)
    }

    fun windows(durationUs: Long, windowUs: Long = DEFAULT_WINDOW_US): List<Window> {
        val duration = durationUs.coerceAtLeast(1L)
        val win = windowUs.coerceIn(400_000L, duration)
        if (duration <= win * 2) {
            return listOf(Window(0L, duration))
        }
        val starts = listOf(
            (duration * 0.20).toLong(),
            (duration * 0.70).toLong() - win,
        ).map { it.coerceIn(0L, duration - win) }.distinct()
        return starts.map { Window(it, it + win) }
    }

    /**
     * Binary-search the lowest bitrate in [loBps, hiBps] whose [measure] ≥ [targetSsim].
     * [measure] must be non-decreasing in bitrate.
     */
    fun minBitrateForSsim(
        loBps: Int,
        hiBps: Int,
        targetSsim: Double,
        maxIters: Int = 6,
        minStepBps: Int = 80_000,
        measure: (Int) -> Double,
    ): Result = minBitrateForScore(
        loBps = loBps,
        hiBps = hiBps,
        target = targetSsim,
        targetMin = 0.50,
        targetMax = 0.999,
        maxIters = maxIters,
        minStepBps = minStepBps,
        measure = measure,
    )

    /**
     * Same search as [minBitrateForSsim], for any monotonic score (SSIM 0–1 or VMAF 0–100).
     */
    fun minBitrateForScore(
        loBps: Int,
        hiBps: Int,
        target: Double,
        targetMin: Double,
        targetMax: Double,
        maxIters: Int = 6,
        minStepBps: Int = 80_000,
        measure: (Int) -> Double,
    ): Result {
        val lo0 = minOf(loBps, hiBps).coerceAtLeast(80_000)
        val hi0 = maxOf(loBps, hiBps).coerceAtLeast(lo0)
        val goal = target.coerceIn(targetMin, targetMax)
        val hiScore = measure(hi0)
        if (hiScore < goal) {
            return Result(hi0, hiScore, 1, metTarget = false)
        }
        val loScore = measure(lo0)
        if (loScore >= goal) {
            return Result(lo0, loScore, 2, metTarget = true)
        }
        var lo = lo0
        var hi = hi0
        var bestBitrate = hi0
        var bestScore = hiScore
        var iters = 2
        while (iters < maxIters && hi - lo > minStepBps) {
            val mid = (lo + hi) ushr 1
            val score = measure(mid)
            iters++
            if (score >= goal) {
                hi = mid
                bestBitrate = mid
                bestScore = score
            } else {
                lo = mid
            }
        }
        return Result(bestBitrate, bestScore, iters, metTarget = bestScore >= goal)
    }

    const val DEFAULT_WINDOW_US = 1_600_000L
}
