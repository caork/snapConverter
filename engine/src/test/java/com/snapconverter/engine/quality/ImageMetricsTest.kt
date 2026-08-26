package com.snapconverter.engine.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMetricsTest {

    @Test
    fun identicalLumaIsPerfect() {
        val y = FloatArray(64) { 128f }
        assertEquals(ImageMetrics.PSNR_MAX, ImageMetrics.psnrY(y, y.copyOf()), 0.01)
        assertEquals(1.0, ImageMetrics.ssimY(y, y.copyOf(), 8, 8), 0.01)
    }

    @Test
    fun largeDifferenceLowersScores() {
        val a = FloatArray(64) { 255f }
        val b = FloatArray(64) { 0f }
        assertTrue(ImageMetrics.psnrY(a, b) < 20.0)
        assertTrue(ImageMetrics.ssimY(a, b, 8, 8) < 0.2)
    }

    @Test
    fun smallNoiseStaysHighSsim() {
        val a = FloatArray(64) { 100f }
        val b = FloatArray(64) { i -> 100f + (i % 3) }
        assertTrue(ImageMetrics.psnrY(a, b) > 30.0)
        assertTrue(ImageMetrics.ssimY(a, b, 8, 8) > 0.9)
    }
}
