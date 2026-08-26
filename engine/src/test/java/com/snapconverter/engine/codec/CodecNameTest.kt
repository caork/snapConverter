package com.snapconverter.engine.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecNameTest {

    @Test
    fun classifiesQualcommC2AndOmx() {
        assertEquals(VendorFamily.QUALCOMM, CodecName.vendorFamily("c2.qti.hevc.encoder"))
        assertEquals(VendorFamily.QUALCOMM, CodecName.vendorFamily("c2.qti.avc.encoder"))
        assertEquals(VendorFamily.QUALCOMM, CodecName.vendorFamily("OMX.qcom.video.encoder.hevc"))
        assertTrue(CodecName.isQualcomm("c2.qti.av1.encoder"))
    }

    @Test
    fun rejectsGoogleSoftwareCodecs() {
        assertTrue(CodecName.isGoogleAosp("c2.android.avc.encoder"))
        assertTrue(CodecName.isGoogleAosp("OMX.google.h264.encoder"))
        assertEquals(VendorFamily.GOOGLE_SOFTWARE, CodecName.vendorFamily("c2.android.hevc.encoder"))
        assertTrue(CodecName.looksLikeSoftware("OMX.google.vp9.decoder"))
    }

    @Test
    fun classifiesOtherVendorsWithoutTreatingThemAsQualcomm() {
        assertEquals(VendorFamily.MEDIATEK, CodecName.vendorFamily("c2.mtk.hevc.encoder"))
        assertEquals(VendorFamily.EXYNOS, CodecName.vendorFamily("OMX.Exynos.HEVC.Encoder"))
        assertFalse(CodecName.isQualcomm("c2.mtk.hevc.encoder"))
    }
}
