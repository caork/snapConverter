package com.snapconverter.engine.media

import android.media.MediaFormat
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Reads ftyp / hvcC / colr / Dolby boxes. MediaExtractor often maps HLG/PQ
 * phone files to BT.709; the container is the source of truth.
 */
internal object Mp4ColorProbe {

    fun probe(input: FileInputStream): VideoColor {
        return runCatching { parse(input.channel) }.getOrDefault(VideoColor())
    }

    fun parse(channel: FileChannel): VideoColor {
        val found = Found()
        walk(channel, 0L, channel.size(), found)
        return found.toColor()
    }

    /** Visible for tests: parse an in-memory ISO-BMFF snippet. */
    fun parseBytes(bytes: ByteArray): VideoColor {
        val found = Found()
        walkBuffer(ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN), found)
        return found.toColor()
    }

    private class Found {
        var primaries: Int? = null
        var transfer: Int? = null
        var matrix: Int? = null
        var fullRange: Boolean? = null
        var bitDepth: Int? = null
        var profileIdc: Int? = null
        var dolby = false

        fun toColor(): VideoColor {
            val mappedTransfer = when {
                transfer == 16 || dolby -> MediaFormat.COLOR_TRANSFER_ST2084
                transfer == 18 -> MediaFormat.COLOR_TRANSFER_HLG
                transfer == 14 && (bitDepth == 10 || profileIdc == 2) ->
                    MediaFormat.COLOR_TRANSFER_HLG
                else -> null
            }
            val hdr = VideoColor.isHdrTransfer(mappedTransfer)
            val standard = when {
                primaries == 9 || hdr -> MediaFormat.COLOR_STANDARD_BT2020
                primaries == 1 -> MediaFormat.COLOR_STANDARD_BT709
                else -> null
            }
            val range = when (fullRange) {
                true -> MediaFormat.COLOR_RANGE_FULL
                false -> MediaFormat.COLOR_RANGE_LIMITED
                null -> null
            }
            return VideoColor(
                standard = standard,
                transfer = mappedTransfer,
                range = range,
                tenBit = bitDepth == 10 || profileIdc == 2 || dolby,
            )
        }
    }

    private fun walk(ch: FileChannel, start: Long, end: Long, found: Found) {
        var pos = start
        while (pos + 8 <= end) {
            ch.position(pos)
            val hdr = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
            val n = ch.read(hdr)
            if (n < 8) break
            hdr.flip()
            var size = hdr.int.toLong() and 0xFFFFFFFFL
            val type = fourcc(hdr)
            var header = 8
            if (size == 1L) {
                if (hdr.remaining() < 8) break
                size = hdr.long
                header = 16
            } else if (size == 0L) {
                size = end - pos
            }
            if (size < header) break
            val boxEnd = (pos + size).coerceAtMost(end)
            when (type) {
                "mdat", "free", "skip" -> Unit
                "moov", "trak", "mdia", "minf", "stbl" ->
                    walk(ch, pos + header, boxEnd, found)
                "stsd" -> walk(ch, pos + header + 8, boxEnd, found)
                "hvc1", "hev1", "dvh1", "dvhe" ->
                    walk(ch, pos + header + 78, boxEnd, found)
                "hvcC" -> readHvcc(read(ch, pos + header, boxEnd), found)
                "colr" -> readColr(read(ch, pos + header, boxEnd), found)
                "dvvC", "dvcC", "dvwC" -> found.dolby = true
            }
            if (type == "dvh1" || type == "dvhe") found.dolby = true
            pos = boxEnd
        }
    }

    private fun walkBuffer(buf: ByteBuffer, found: Found) {
        while (buf.remaining() >= 8) {
            val origin = buf.position()
            var size = buf.int.toLong() and 0xFFFFFFFFL
            val type = fourcc(buf)
            var header = 8
            if (size == 1L && buf.remaining() >= 8) {
                size = buf.long
                header = 16
            } else if (size == 0L) {
                size = (buf.limit() - origin).toLong()
            }
            if (size < header) break
            val end = (origin + size.toInt()).coerceAtMost(buf.limit())
            val payloadStart = origin + header
            fun slice(from: Int): ByteBuffer =
                buf.duplicate().order(ByteOrder.BIG_ENDIAN).apply {
                    position(from.coerceAtMost(end))
                    limit(end)
                }
            when (type) {
                "moov", "trak", "mdia", "minf", "stbl" -> walkBuffer(slice(payloadStart), found)
                "stsd" -> walkBuffer(slice(payloadStart + 8), found)
                "hvc1", "hev1", "dvh1", "dvhe" -> walkBuffer(slice(payloadStart + 78), found)
                "hvcC" -> readHvcc(slice(payloadStart), found)
                "colr" -> readColr(slice(payloadStart), found)
                "dvvC", "dvcC", "dvwC" -> found.dolby = true
            }
            if (type == "dvh1" || type == "dvhe") found.dolby = true
            buf.position(end)
        }
    }

    private fun read(ch: FileChannel, start: Long, end: Long): ByteBuffer {
        val n = (end - start).toInt().coerceAtLeast(0).coerceAtMost(512)
        val buf = ByteBuffer.allocate(n).order(ByteOrder.BIG_ENDIAN)
        ch.position(start)
        ch.read(buf)
        buf.flip()
        return buf
    }

    private fun readHvcc(buf: ByteBuffer, found: Found) {
        if (buf.remaining() < 19) return
        val profile = buf.get(buf.position() + 1).toInt() and 0x1F
        found.profileIdc = profile
        found.bitDepth = (buf.get(buf.position() + 17).toInt() and 0x07) + 8
    }

    private fun readColr(buf: ByteBuffer, found: Found) {
        if (buf.remaining() < 11) return
        val kind = ByteArray(4).also { buf.get(it) }.decodeToString()
        if (kind != "nclx") return
        found.primaries = buf.short.toInt() and 0xFFFF
        found.transfer = buf.short.toInt() and 0xFFFF
        found.matrix = buf.short.toInt() and 0xFFFF
        if (buf.hasRemaining()) {
            found.fullRange = (buf.get().toInt() and 0x80) != 0
        }
    }

    private fun fourcc(buf: ByteBuffer): String {
        val b = ByteArray(4)
        buf.get(b)
        return b.decodeToString()
    }
}
