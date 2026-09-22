package com.snapconverter.engine.media

import android.media.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Mp4ColorProbeTest {

    @Test
    fun nclxHlgIsHlgHdr() {
        val colr = nclx(primaries = 9, transfer = 18, matrix = 9)
        val color = Mp4ColorProbe.parseBytes(colr)
        assertTrue(color.isHdr)
        assertEquals("HLG", color.label)
        assertEquals(MediaFormat.COLOR_TRANSFER_HLG, color.transfer)
        assertEquals(MediaFormat.COLOR_STANDARD_BT2020, color.standard)
    }

    @Test
    fun nclxPqIsHdr10() {
        val color = Mp4ColorProbe.parseBytes(nclx(primaries = 9, transfer = 16, matrix = 9))
        assertEquals("HDR10", color.label)
        assertEquals(MediaFormat.COLOR_TRANSFER_ST2084, color.transfer)
    }

    @Test
    fun nclxBt709IsSdr() {
        val color = Mp4ColorProbe.parseBytes(nclx(primaries = 1, transfer = 1, matrix = 1))
        assertEquals("SDR", color.label)
        assertEquals(null, color.transfer)
    }

    private fun nclx(primaries: Int, transfer: Int, matrix: Int): ByteArray {
        val buf = ByteBuffer.allocate(19).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(19)
        buf.put("colr".toByteArray())
        buf.put("nclx".toByteArray())
        buf.putShort(primaries.toShort())
        buf.putShort(transfer.toShort())
        buf.putShort(matrix.toShort())
        buf.put(0)
        return buf.array()
    }
}
