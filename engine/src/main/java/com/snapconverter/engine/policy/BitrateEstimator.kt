package com.snapconverter.engine.policy

object BitrateEstimator {

    const val DEFAULT_AUDIO_BITRATE_BPS = 128_000
    const val CONTAINER_OVERHEAD_BPS = 16_000

    /**
     * Video bitrate such that `audio + video + mux overhead` fits in [targetBytes].
     */
    fun videoBitrateForTargetSize(
        targetBytes: Long,
        durationSeconds: Double,
        audioBitrateBps: Int = DEFAULT_AUDIO_BITRATE_BPS,
        overheadBps: Int = CONTAINER_OVERHEAD_BPS,
    ): Int {
        val duration = durationSeconds.coerceAtLeast(0.1)
        val budgetBits = targetBytes.coerceAtLeast(1L) * 8.0
        val reserved = (audioBitrateBps + overheadBps) * duration
        val videoBits = (budgetBits - reserved).coerceAtLeast(50_000.0)
        return (videoBits / duration).toInt().coerceIn(80_000, 80_000_000)
    }

    fun estimatedFileBytes(
        videoBitrateBps: Int,
        durationSeconds: Double,
        audioBitrateBps: Int = DEFAULT_AUDIO_BITRATE_BPS,
        overheadBps: Int = CONTAINER_OVERHEAD_BPS,
    ): Long {
        val bps = videoBitrateBps + audioBitrateBps + overheadBps
        return ((bps * durationSeconds) / 8.0).toLong().coerceAtLeast(1L)
    }
}
