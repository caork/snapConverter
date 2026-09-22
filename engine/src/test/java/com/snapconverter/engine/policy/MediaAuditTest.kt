package com.snapconverter.engine.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAuditTest {

    private fun video(
        width: Int = 1920,
        height: Int = 1080,
        seconds: Int = 60,
        mbps: Double,
        mime: String = "video/avc",
    ) = MediaFact(
        kind = MediaKind.VIDEO,
        width = width,
        height = height,
        sizeBytes = (mbps * 1_000_000 * seconds / 8).toLong(),
        durationMs = seconds * 1000L,
        mime = mime,
    )

    private fun still(width: Int, height: Int, bytes: Long) = MediaFact(
        kind = MediaKind.IMAGE,
        width = width,
        height = height,
        sizeBytes = bytes,
        mime = "image/jpeg",
    )

    @Test
    fun cameraH264FlagsAsVeryHigh() {
        // A 1080p phone clip at ~17 Mbps against a 4 Mbps HEVC reference.
        val result = MediaAudit.audit(video(mbps = 17.0))
        assertEquals(AuditVerdict.VERY_HIGH, result.verdict)
        assertEquals(AuditReason.BITRATE, result.reason)
        assertTrue("overshoot ${result.overshoot}", result.overshoot > 4.0)
        assertTrue(result.savingBytes > 90L * 1024 * 1024)
    }

    @Test
    fun efficientHevcAtTheModelBitrateIsLeftAlone() {
        val result = MediaAudit.audit(video(mbps = 4.0, mime = "video/hevc", seconds = 120))
        assertFalse(result.flagged)
        assertEquals(AuditReason.NONE, result.reason)
    }

    @Test
    fun shortOrSmallClipsNeverAppear() {
        // Two seconds, however fat, is not worth a batch job.
        assertFalse(MediaAudit.audit(video(seconds = 2, mbps = 60.0)).flagged)
        // 8 MB total is under the standard floor.
        assertFalse(MediaAudit.audit(video(seconds = 30, mbps = 2.0)).flagged)
    }

    @Test
    fun fourKAtFiftyMbpsIsJudgedAgainstFourKReferenceNotAgainst1080p() {
        val uhd = MediaAudit.audit(video(width = 3840, height = 2160, mbps = 50.0))
        val reference = MediaAudit.referenceBitrate(3840, 2160)
        assertEquals(16_000_000, reference)
        assertEquals(AuditVerdict.VERY_HIGH, uhd.verdict)
        // The same bitrate at 1080p would overshoot much further.
        val fhd = MediaAudit.audit(video(mbps = 50.0))
        assertTrue(fhd.overshoot > uhd.overshoot)
    }

    @Test
    fun strictSensitivityFlagsWhatStandardLetsThrough() {
        val fact = video(mbps = 5.4, mime = "video/hevc", seconds = 120)
        assertFalse(MediaAudit.audit(fact, AuditSensitivity.STANDARD).flagged)
        assertTrue(MediaAudit.audit(fact, AuditSensitivity.STRICT).flagged)
    }

    @Test
    fun relaxedSensitivityRaisesTheFloorAndTheThreshold() {
        val fact = video(mbps = 7.0, mime = "video/hevc", seconds = 60)
        assertTrue(MediaAudit.audit(fact, AuditSensitivity.STANDARD).flagged)
        assertFalse(MediaAudit.audit(fact, AuditSensitivity.RELAXED).flagged)
    }

    @Test
    fun fatJpegFlagsOnBytesPerPixel() {
        // 12 MP at 5 MB is ~0.42 B/px against a 0.12 B/px HEIC target.
        val result = MediaAudit.audit(still(4000, 3000, 5L * 1024 * 1024))
        assertEquals(AuditVerdict.VERY_HIGH, result.verdict)
        assertEquals(AuditReason.DENSITY, result.reason)
        assertTrue(result.bytesPerPixel > 0.4)
    }

    @Test
    fun leanHeicIsLeftAlone() {
        // The same 12 MP frame already stored at 0.12 B/px.
        val result = MediaAudit.audit(still(4000, 3000, 1_440_000))
        assertFalse(result.flagged)
    }

    @Test
    fun hugeGeometryFlagsEvenWhenDensityIsFine() {
        // 12000x9000 at a good 0.12 B/px: nothing to gain from re-encoding, but
        // capping the long edge is worth a lot.
        val result = MediaAudit.audit(still(12000, 9000, 12_960_000))
        assertEquals(AuditVerdict.HIGH, result.verdict)
        assertEquals(AuditReason.DIMENSION, result.reason)
        assertTrue(result.savingBytes > 10L * 1024 * 1024)
    }

    @Test
    fun rawNegativesAreNeverFlagged() {
        // A 16 MP DNG at ~2 B/px overshoots the still model by 17x, but its
        // size is the format's purpose: re-encoding it would discard the
        // sensor data, so the audit refuses to judge it.
        val dng = still(4624, 3472, 32_000_000).copy(mime = "image/x-adobe-dng")
        assertFalse(MediaAudit.audit(dng, AuditSensitivity.STRICT).flagged)
        val nef = still(6000, 4000, 30_000_000).copy(mime = "image/x-nikon-nef")
        assertFalse(MediaAudit.audit(nef).flagged)
        // Animated stills would lose their animation.
        val gif = still(800, 600, 6_000_000).copy(mime = "image/gif")
        assertFalse(MediaAudit.audit(gif).flagged)
        // The ordinary JPEG at the same density still gets flagged.
        assertTrue(MediaAudit.audit(still(4624, 3472, 32_000_000)).flagged)
    }

    @Test
    fun presetsExpandIntoTheNumbersTheAuditApplies() {
        val standard = AuditSensitivity.STANDARD.tuning()
        assertEquals(MediaAudit.VIDEO_HIGH, standard.videoHigh, 0.001)
        assertEquals(4096, standard.maxLongEdge)
        val strict = AuditSensitivity.STRICT.tuning()
        assertTrue(strict.videoHigh < standard.videoHigh)
        assertTrue(strict.minVideoBytes < standard.minVideoBytes)
    }

    @Test
    fun aTunedThresholdOverridesThePreset() {
        // 6 Mbps at 1080p is 1.5x the model: under the stock 1.6x bar.
        val fact = video(mbps = 6.0, seconds = 60)
        assertFalse(MediaAudit.audit(fact).flagged)
        val eager = AuditSensitivity.STANDARD.tuning().copy(videoHigh = 1.2)
        assertEquals(AuditVerdict.HIGH, MediaAudit.audit(fact, eager).verdict)
    }

    @Test
    fun aTunedStillTargetMovesBothTheVerdictAndTheEstimate() {
        // 12 MP at 0.25 B/px: 2.1x the stock 0.12 target, so HIGH.
        val fact = still(4000, 3000, 3_000_000)
        assertEquals(AuditVerdict.HIGH, MediaAudit.audit(fact).verdict)
        // Ask for 0.30 B/px instead and the same file is fine — and the
        // estimate it is judged against grows with the target.
        val lenient = AuditSensitivity.STANDARD.tuning().copy(stillTargetBpp = 0.30)
        val result = MediaAudit.audit(fact, lenient)
        assertFalse(result.flagged)
        assertTrue(result.estimatedBytes > MediaAudit.audit(fact).estimatedBytes)
    }

    @Test
    fun rawCanBeJudgedWhenTheCallerAsksForIt() {
        val dng = still(4624, 3472, 32_000_000).copy(mime = "image/x-adobe-dng")
        assertFalse(MediaAudit.audit(dng).flagged)
        val including = AuditSensitivity.STANDARD.tuning().copy(skipUnjudgedStills = false)
        assertEquals(AuditVerdict.VERY_HIGH, MediaAudit.audit(dng, including).verdict)
    }

    @Test
    fun aRaisedFloorKeepsMidSizedFilesOut() {
        val fact = video(mbps = 17.0, seconds = 10)
        assertTrue(MediaAudit.audit(fact).flagged)
        val floored = AuditSensitivity.STANDARD.tuning()
            .copy(minVideoBytes = 64L * 1024 * 1024)
        assertFalse(MediaAudit.audit(fact, floored).flagged)
    }

    @Test
    fun smallStillsNeverAppear() {
        assertFalse(MediaAudit.audit(still(1200, 800, 400_000)).flagged)
    }

    @Test
    fun estimateFollowsTheChosenQualityAndCap() {
        val fact = video(width = 3840, height = 2160, seconds = 60, mbps = 50.0)
        val q70 = MediaAudit.estimateOutputBytes(fact, quality = 70)
        val q40 = MediaAudit.estimateOutputBytes(fact, quality = 40)
        assertTrue(q40 < q70)
        val capped = MediaAudit.estimateOutputBytes(fact, quality = 70, maxLongEdge = 1920)
        assertTrue("capped $capped vs $q70", capped < q70)
    }

    @Test
    fun cappedSizeKeepsAspectAndEvenOrder() {
        assertEquals(1920 to 1080, MediaAudit.cappedSize(3840, 2160, 1920))
        assertEquals(1080 to 1920, MediaAudit.cappedSize(2160, 3840, 1920))
        assertEquals(1280 to 720, MediaAudit.cappedSize(1280, 720, 1920))
    }
}
