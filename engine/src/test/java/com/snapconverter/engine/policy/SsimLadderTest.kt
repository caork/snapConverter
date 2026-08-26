package com.snapconverter.engine.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SsimLadderTest {

    @Test
    fun shortClipUsesSingleWindow() {
        val w = SsimLadder.windows(1_200_000L)
        assertEquals(1, w.size)
        assertEquals(0L, w[0].startUs)
        assertEquals(1_200_000L, w[0].endUs)
    }

    @Test
    fun longClipUsesMultipleWindowsInsideDuration() {
        val duration = 300_000_000L
        val w = SsimLadder.windows(duration)
        assertTrue(w.size >= 2)
        w.forEach {
            assertTrue(it.startUs >= 0)
            assertTrue(it.endUs <= duration)
            assertTrue(it.durationUs >= 400_000L)
        }
    }

    @Test
    fun binarySearchPicksLowestBitrateThatMeetsTarget() {
        // Fake encoder: SSIM = 0.70 + bitrate/10_000_000, so 0.95 needs 2_500_000.
        val result = SsimLadder.minBitrateForSsim(
            loBps = 200_000,
            hiBps = 8_000_000,
            targetSsim = 0.95,
            maxIters = 8,
            minStepBps = 50_000,
        ) { bps -> 0.70 + bps / 10_000_000.0 }
        assertTrue(result.metTarget)
        assertTrue(result.bitrateBps in 2_400_000..2_800_000)
        assertTrue(result.ssim + 1e-6 >= 0.95)
    }

    @Test
    fun unreachableTargetReturnsUpperBound() {
        val result = SsimLadder.minBitrateForSsim(
            loBps = 200_000,
            hiBps = 1_000_000,
            targetSsim = 0.99,
        ) { 0.80 }
        assertFalse(result.metTarget)
        assertEquals(1_000_000, result.bitrateBps)
    }

    @Test
    fun alreadyGoodAtFloorReturnsFloor() {
        val result = SsimLadder.minBitrateForSsim(
            loBps = 400_000,
            hiBps = 8_000_000,
            targetSsim = 0.90,
            maxIters = 8,
        ) { 0.96 }
        assertTrue(result.metTarget)
        assertEquals(400_000, result.bitrateBps)
    }
}
