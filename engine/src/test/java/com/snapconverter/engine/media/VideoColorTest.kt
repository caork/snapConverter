package com.snapconverter.engine.media

import android.media.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoColorTest {

    @Test
    fun st2084IsHdr10() {
        val c = VideoColor(transfer = MediaFormat.COLOR_TRANSFER_ST2084)
        assertTrue(c.isHdr)
        assertEquals("HDR10", c.label)
        assertEquals(VideoColor.EGL_GL_COLORSPACE_BT2020_PQ_EXT, c.eglGlColorspace)
    }

    @Test
    fun hlgIsHdr() {
        val c = VideoColor(transfer = MediaFormat.COLOR_TRANSFER_HLG)
        assertTrue(c.isHdr)
        assertEquals("HLG", c.label)
        assertEquals(VideoColor.EGL_GL_COLORSPACE_BT2020_HLG_EXT, c.eglGlColorspace)
    }

    @Test
    fun sdrIsNotHdr() {
        val c = VideoColor(transfer = MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
        assertFalse(c.isHdr)
        assertEquals("SDR", c.label)
        assertEquals(null, c.eglGlColorspace)
    }

    @Test
    fun hdrEncodeDefaultsFillBt2020Limited() {
        val c = VideoColor(transfer = MediaFormat.COLOR_TRANSFER_HLG).withDefaultsForEncode()
        assertEquals(MediaFormat.COLOR_STANDARD_BT2020, c.standard)
        assertEquals(MediaFormat.COLOR_RANGE_LIMITED, c.range)
        assertEquals(MediaFormat.COLOR_TRANSFER_HLG, c.transfer)
    }
}
