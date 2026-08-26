package com.snapconverter.engine.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BitrateEstimatorTest {

    @Test
    fun targetSize300MbTenMinutes() {
        val bps = BitrateEstimator.videoBitrateForTargetSize(
            targetBytes = 300L * 1024 * 1024,
            durationSeconds = 10 * 60.0,
            audioBitrateBps = 128_000,
        )
        // ~4 Mbps class for 300 MB / 10 min, AAC 128 kbps
        assertTrue("bitrate=$bps", bps in 3_500_000..4_500_000)
    }

    @Test
    fun estimatedSizeRoundTrip() {
        val duration = 120.0
        val video = 4_000_000
        val bytes = BitrateEstimator.estimatedFileBytes(video, duration)
        val recovered = BitrateEstimator.videoBitrateForTargetSize(bytes, duration)
        assertEquals(video.toDouble(), recovered.toDouble(), 2_000.0)
    }
}
