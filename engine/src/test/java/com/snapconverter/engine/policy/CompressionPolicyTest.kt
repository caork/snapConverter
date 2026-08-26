package com.snapconverter.engine.policy

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecInfo.EncoderCapabilities
import com.snapconverter.engine.codec.CodecCandidate
import com.snapconverter.engine.codec.MimeTypes
import com.snapconverter.engine.codec.VendorFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionPolicyTest {

    private val policy = CompressionPolicy()
    private val source = VideoSourceInfo(
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

    @Test
    fun vbrUsesTargetAndMaxBitrate() {
        val plan = policy.planVideo(
            source,
            request(bitrateMode = BitrateModeOption.VBR, targetBitrateBps = 5_000_000, maxBitrateBps = 12_000_000),
            encoder(modes = setOf(EncoderCapabilities.BITRATE_MODE_VBR, EncoderCapabilities.BITRATE_MODE_CBR)),
        )
        assertEquals(5_000_000, plan.bitrateBps)
        assertEquals(12_000_000, plan.maxBitrateBps)
        assertEquals(EncoderCapabilities.BITRATE_MODE_VBR, plan.bitrateMode)
        assertNull(plan.codecQuality)
    }

    @Test
    fun cbrUsesConstantBitrateAsPeak() {
        val plan = policy.planVideo(
            source,
            request(bitrateMode = BitrateModeOption.CBR, targetBitrateBps = 4_000_000, maxBitrateBps = 20_000_000),
            encoder(modes = setOf(EncoderCapabilities.BITRATE_MODE_VBR, EncoderCapabilities.BITRATE_MODE_CBR)),
        )
        assertEquals(4_000_000, plan.bitrateBps)
        assertEquals(4_000_000, plan.maxBitrateBps)
        assertEquals(EncoderCapabilities.BITRATE_MODE_CBR, plan.bitrateMode)
    }

    @Test
    fun cqUsesEncoderQualityWhenSupported() {
        val plan = policy.planVideo(
            source,
            request(bitrateMode = BitrateModeOption.CQ, appQuality = 70),
            encoder(
                modes = setOf(EncoderCapabilities.BITRATE_MODE_CQ, EncoderCapabilities.BITRATE_MODE_VBR),
                qualityRange = 0..100,
            ),
        )
        assertEquals(EncoderCapabilities.BITRATE_MODE_CQ, plan.bitrateMode)
        assertEquals(70, plan.codecQuality)
        assertNull(plan.qpIMin)
    }

    @Test
    fun cqFallsBackToVbrAndQpWhenEncoderHasNoCq() {
        val plan = policy.planVideo(
            source,
            request(bitrateMode = BitrateModeOption.CQ, appQuality = 100),
            encoder(modes = setOf(EncoderCapabilities.BITRATE_MODE_VBR, EncoderCapabilities.BITRATE_MODE_CBR)),
        )
        assertEquals(EncoderCapabilities.BITRATE_MODE_VBR, plan.bitrateMode)
        assertNull(plan.codecQuality)
        assertNotNull(plan.qpIMin)
        assertTrue((plan.qpIMax ?: 51) <= (plan.qpPMax ?: 51))
        assertEquals(QualityStrategy.qpWindowForQuality(100).pMax, plan.qpPMax)
    }

    @Test
    fun autoComplexityDoesNotForceHigh() {
        val plan = policy.planVideo(
            source,
            request(complexity = ComplexityOption.AUTO),
            encoder(complexityRange = 0..2),
        )
        assertNull(plan.complexity)
    }

    @Test
    fun baselineForcesZeroBFrames() {
        val plan = policy.planVideo(
            source,
            request(
                videoCodec = OutputVideoCodec.AVC,
                profile = VideoProfileOption.BASELINE,
                maxBFrames = 3,
            ),
            encoder(
                mime = MimeTypes.AVC,
                modes = setOf(EncoderCapabilities.BITRATE_MODE_VBR),
                profileLevels = listOf(
                    CodecProfileLevel.AVCProfileBaseline to CodecProfileLevel.AVCLevel42,
                ),
            ),
        )
        assertEquals(0, plan.maxBFrames)
        assertEquals(CodecProfileLevel.AVCProfileBaseline, plan.profile)
        assertEquals(CodecProfileLevel.AVCLevel42, plan.level)
    }

    @Test
    fun hevcIgnoresAvcBaselineProfile() {
        val plan = policy.planVideo(
            source,
            request(profile = VideoProfileOption.BASELINE, maxBFrames = 2),
            encoder(modes = setOf(EncoderCapabilities.BITRATE_MODE_VBR)),
        )
        assertNull(plan.profile)
        assertEquals(2, plan.maxBFrames)
    }

    @Test
    fun customQpSplitsIAndPAndDerivesB() {
        val plan = policy.planVideo(
            source,
            request(qpIMin = 10, qpIMax = 20, qpPMin = 16, qpPMax = 32, maxBFrames = 1),
            encoder(modes = setOf(EncoderCapabilities.BITRATE_MODE_VBR)),
        )
        assertEquals(10, plan.qpIMin)
        assertEquals(20, plan.qpIMax)
        assertEquals(16, plan.qpPMin)
        assertEquals(32, plan.qpPMax)
        assertEquals(18, plan.qpBMin)
        assertEquals(34, plan.qpBMax)
    }

    private fun request(
        bitrateMode: BitrateModeOption = BitrateModeOption.AUTO,
        targetBitrateBps: Int? = null,
        maxBitrateBps: Int? = null,
        appQuality: Int = 70,
        videoCodec: OutputVideoCodec = OutputVideoCodec.HEVC,
        profile: VideoProfileOption = VideoProfileOption.AUTO,
        maxBFrames: Int? = null,
        complexity: ComplexityOption = ComplexityOption.AUTO,
        qpIMin: Int? = null,
        qpIMax: Int? = null,
        qpPMin: Int? = null,
        qpPMax: Int? = null,
    ) = CompressionRequest(
        kind = MediaKind.VIDEO,
        mode = CompressionMode.QUALITY,
        appQuality = appQuality,
        targetBitrateBps = targetBitrateBps,
        videoCodec = videoCodec,
        bitrateMode = bitrateMode,
        maxBitrateBps = maxBitrateBps,
        profile = profile,
        maxBFrames = maxBFrames,
        complexity = complexity,
        qpIMin = qpIMin,
        qpIMax = qpIMax,
        qpPMin = qpPMin,
        qpPMax = qpPMax,
    )

    private fun encoder(
        mime: String = MimeTypes.HEVC,
        modes: Set<Int> = setOf(EncoderCapabilities.BITRATE_MODE_VBR),
        qualityRange: IntRange? = null,
        complexityRange: IntRange? = null,
        profileLevels: List<Pair<Int, Int>> = emptyList(),
    ) = CodecCandidate(
        name = "c2.qti.hevc.encoder",
        mime = mime,
        isEncoder = true,
        hardwareAccelerated = true,
        softwareOnly = false,
        vendor = true,
        vendorFamily = VendorFamily.QUALCOMM,
        maxWidth = 3840,
        maxHeight = 2160,
        bitrateModes = modes,
        qualityRange = qualityRange,
        complexityRange = complexityRange,
        profileLevels = profileLevels,
    )
}
