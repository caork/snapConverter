package com.snapconverter.engine.policy

import android.media.MediaCodecInfo.EncoderCapabilities
import com.snapconverter.engine.codec.CodecCandidate
import com.snapconverter.engine.codec.MimeTypes
import com.snapconverter.engine.codec.VendorFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimRequestTest {

    private val policy = CompressionPolicy()

    @Test
    fun clipDurationClampsToSourceAndKeepsPositive() {
        val request = CompressionRequest(
            kind = MediaKind.VIDEO,
            mode = CompressionMode.QUALITY,
            trimStartUs = 2_000_000L,
            trimEndUs = 8_000_000L,
        )
        assertEquals(6_000_000L, request.clipDurationUs(10_000_000L))
        // End beyond source duration clamps to the source end.
        assertEquals(3_000_000L, request.clipDurationUs(5_000_000L))
        // Start beyond the end still yields a positive, non-zero window.
        assertTrue(request.clipDurationUs(1_000_000L) >= 1L)
    }

    @Test
    fun noTrimUsesFullDuration() {
        val request = CompressionRequest(kind = MediaKind.VIDEO, mode = CompressionMode.QUALITY)
        assertFalse(request.trims)
        assertEquals(10_000_000L, request.clipDurationUs(10_000_000L))
        assertEquals(-1L, request.trimEndUs)
    }

    @Test
    fun trimEndOnlyCountsAsTrim() {
        val request = CompressionRequest(
            kind = MediaKind.VIDEO,
            mode = CompressionMode.QUALITY,
            trimEndUs = 4_000_000L,
        )
        assertTrue(request.trims)
        assertEquals(4_000_000L, request.clipDurationUs(10_000_000L))
    }

    @Test
    fun targetSizePlansOnTrimmedDurationWithHigherBitrate() {
        // Same target size over half the clip must roughly double the bitrate.
        val encoder = encoder()
        val full = policy.planVideo(
            source(),
            request(targetSizeBytes = 10L * 1024 * 1024),
            encoder,
        )
        val trimmed = policy.planVideo(
            source().copy(durationUs = 5_000_000L),
            request(targetSizeBytes = 10L * 1024 * 1024),
            encoder,
        )
        assertTrue(trimmed.bitrateBps > full.bitrateBps)
        val ratio = trimmed.bitrateBps.toDouble() / full.bitrateBps
        assertTrue("expected ~2x bitrate, was " + ratio, ratio in 1.8..2.2)
    }

    private fun source() = VideoSourceInfo(
        width = 1920,
        height = 1080,
        rotation = 0,
        durationUs = 10_000_000,
        frameRate = 30f,
        bitrateBps = 8_000_000,
        mime = MimeTypes.HEVC,
        audioMime = MimeTypes.AAC,
        audioBitrateBps = 128_000,
    )

    private fun request(targetSizeBytes: Long) = CompressionRequest(
        kind = MediaKind.VIDEO,
        mode = CompressionMode.TARGET_SIZE,
        targetSizeBytes = targetSizeBytes,
    )

    private fun encoder() = CodecCandidate(
        name = "c2.qti.hevc.encoder",
        mime = MimeTypes.HEVC,
        isEncoder = true,
        hardwareAccelerated = true,
        softwareOnly = false,
        vendor = true,
        vendorFamily = VendorFamily.QUALCOMM,
        maxWidth = 3840,
        maxHeight = 2160,
        bitrateModes = setOf(EncoderCapabilities.BITRATE_MODE_VBR),
        qualityRange = null,
        complexityRange = null,
        profileLevels = emptyList(),
    )
}
