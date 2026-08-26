package com.snapconverter.engine.media

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureTimestampNormalizeTest {
    @Test
    fun secondsBecomeMillis() {
        assertEquals(1_530_000_000_000L, EpochMs.normalize(1_530_000_000L))
    }

    @Test
    fun millisStayMillis() {
        assertEquals(1_530_000_000_000L, EpochMs.normalize(1_530_000_000_000L))
    }

    @Test
    fun mp4EpochZeroIsNotACaptureTime() {
        org.junit.Assert.assertNull(EpochMs.plausible(0L))
        org.junit.Assert.assertNull(EpochMs.plausible(-2_082_844_800_000L))
    }

    @Test
    fun dashcamFilenameBecomesCaptureTime() {
        val ms = CaptureTimestamp.parseFilename("2025_07_05_153052_00.mp4")
        org.junit.Assert.assertNotNull(ms)
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = ms!!
        assertEquals(2025, cal.get(java.util.Calendar.YEAR))
        assertEquals(6, cal.get(java.util.Calendar.MONTH))
        assertEquals(5, cal.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(15, cal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(java.util.Calendar.MINUTE))
        assertEquals(52, cal.get(java.util.Calendar.SECOND))
    }
}
