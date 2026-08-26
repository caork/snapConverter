package com.snapconverter.engine.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityStrategyTest {

    @Test
    fun quality70IsFourMbps1080pHevcAnchor() {
        val a = QualityStrategy.interpolate(70)
        assertEquals(4_000_000, a.bitrate1080pHevcBps)
        assertEquals(1920, a.maxLongEdge)
    }

    @Test
    fun avcNeedsMoreBitsThanHevc() {
        val hevc = QualityStrategy.scaleBitrateForPixels(4_000_000, 1920, 1080, "video/hevc")
        val avc = QualityStrategy.scaleBitrateForPixels(4_000_000, 1920, 1080, "video/avc")
        assertTrue(avc > hevc)
        assertEquals(4_000_000, hevc)
    }

    @Test
    fun codecQualityMapsThroughEncoderRange() {
        assertEquals(70, QualityStrategy.mapToCodecQuality(70, 0..100))
        assertEquals(0, QualityStrategy.mapToCodecQuality(0, 0..100))
        assertEquals(51, QualityStrategy.mapToCodecQuality(100, 1..51))
    }

    @Test
    fun higherQualityMapsToLowerQp() {
        val low = QualityStrategy.qpWindowForQuality(0)
        val high = QualityStrategy.qpWindowForQuality(100)
        assertTrue(high.pMax < low.pMax)
        assertTrue(high.iMax <= high.pMax)
        assertEquals(18, high.pMax)
        assertEquals(48, low.pMax)
    }
}
