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
}
