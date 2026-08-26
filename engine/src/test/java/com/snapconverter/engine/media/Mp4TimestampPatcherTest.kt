package com.snapconverter.engine.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp4TimestampPatcherTest {

    @Test
    fun unixToMp4Epoch() {
        // 1970-01-01 → 2082844800
        assertEquals(2_082_844_800L, Mp4TimestampPatcher.unixMsToMp4Seconds(0))
        assertEquals(2_082_844_801L, Mp4TimestampPatcher.unixMsToMp4Seconds(1_500))
    }

    @Test
    fun patchesMvhdCreationAndModification() {
        val ftyp = box("ftyp", byteArrayOf(0, 0, 0, 0))
        val mvhdBody = ByteArray(20)
        // version 0 + flags, then two zero timestamps, timescale, duration
        mvhdBody[8] = 0x00
        mvhdBody[9] = 0x00
        mvhdBody[10] = 0x03
        mvhdBody[11] = 0xE8.toByte() // timescale 1000
        val moov = box("moov", box("mvhd", mvhdBody))
        val file = concat(ftyp, moov)

        val capture = 1_530_000_000_000L // 2018-ish
        val patched = Mp4TimestampPatcher.patchBuffer(file, capture)
        assertTrue(patched >= 1)

        val expected = Mp4TimestampPatcher.unixMsToMp4Seconds(capture)
        val mvhdAt = indexOf(file, "mvhd")
        // payload starts 4 bytes after the fourcc: version/flags, creation, modification
        val creation = u32(file, mvhdAt + 8)
        val modification = u32(file, mvhdAt + 12)
        assertEquals(expected, creation)
        assertEquals(expected, modification)
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val size = 8 + payload.size
        val out = ByteArray(size)
        out[0] = (size shr 24).toByte()
        out[1] = (size shr 16).toByte()
        out[2] = (size shr 8).toByte()
        out[3] = size.toByte()
        type.toByteArray().copyInto(out, 4)
        payload.copyInto(out, 8)
        return out
    }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val all = ByteArray(parts.sumOf { it.size })
        var i = 0
        parts.forEach {
            it.copyInto(all, i)
            i += it.size
        }
        return all
    }

    private fun indexOf(file: ByteArray, fourCc: String): Int {
        val needle = fourCc.toByteArray()
        outer@ for (i in 0..file.size - 4) {
            for (j in 0..3) if (file[i + j] != needle[j]) continue@outer
            return i
        }
        error("$fourCc not found")
    }

    private fun u32(file: ByteArray, offset: Int): Long {
        return ((file[offset].toLong() and 0xFF) shl 24) or
            ((file[offset + 1].toLong() and 0xFF) shl 16) or
            ((file[offset + 2].toLong() and 0xFF) shl 8) or
            (file[offset + 3].toLong() and 0xFF)
    }
}
