package com.snapconverter.engine.media

import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * Rewrites QuickTime/MP4 creation and modification times in `mvhd` / `tkhd` / `mdhd`.
 * MediaMuxer always stamps "now"; gallery capture time also needs MediaStore DATE_TAKEN.
 */
object Mp4TimestampPatcher {

    /** Seconds between 1904-01-01 and 1970-01-01 UTC. */
    const val MAC_EPOCH_DELTA_SEC = 2_082_844_800L

    fun unixMsToMp4Seconds(epochMs: Long): Long =
        (epochMs / 1000L + MAC_EPOCH_DELTA_SEC).coerceAtLeast(0L)

    fun patchFile(path: String, captureEpochMs: Long): Int {
        RandomAccessFile(path, "rw").use { raf ->
            return walkRaf(raf, 0L, raf.length(), unixMsToMp4Seconds(captureEpochMs))
        }
    }

    fun patchBuffer(file: ByteArray, captureEpochMs: Long): Int {
        return walkBytes(file, 0, file.size, unixMsToMp4Seconds(captureEpochMs))
    }

    private fun walkRaf(raf: RandomAccessFile, start: Long, end: Long, mp4Time: Long): Int {
        var pos = start
        var patched = 0
        val header = ByteArray(16)
        while (pos + 8 <= end) {
            raf.seek(pos)
            raf.readFully(header, 0, 8)
            val boxSize32 = u32(header, 0)
            val type = String(header, 4, 4, StandardCharsets.US_ASCII)
            var headerLen = 8L
            val boxSize: Long = when {
                boxSize32 == 1L -> {
                    if (pos + 16 > end) break
                    raf.readFully(header, 8, 8)
                    headerLen = 16
                    u64(header, 8)
                }
                boxSize32 == 0L -> end - pos
                else -> boxSize32
            }
            if (boxSize < headerLen || pos + boxSize > end) break
            val bodyStart = pos + headerLen
            val bodyEnd = pos + boxSize
            when (type) {
                "moov", "trak", "mdia" -> patched += walkRaf(raf, bodyStart, bodyEnd, mp4Time)
                "mvhd", "tkhd", "mdhd" -> if (patchRafFullBox(raf, bodyStart, bodyEnd, mp4Time)) patched++
            }
            pos += boxSize
        }
        return patched
    }

    private fun patchRafFullBox(raf: RandomAccessFile, bodyStart: Long, bodyEnd: Long, mp4Time: Long): Boolean {
        if (bodyStart + 12 > bodyEnd) return false
        raf.seek(bodyStart)
        val version = raf.readUnsignedByte()
        val payload = ByteArray(16)
        if (version == 1) {
            if (bodyStart + 20 > bodyEnd) return false
            putU64(payload, 0, mp4Time)
            putU64(payload, 8, mp4Time)
            raf.seek(bodyStart + 4)
            raf.write(payload, 0, 16)
        } else {
            putU32(payload, 0, mp4Time)
            putU32(payload, 4, mp4Time)
            raf.seek(bodyStart + 4)
            raf.write(payload, 0, 8)
        }
        return true
    }

    private fun walkBytes(file: ByteArray, start: Int, end: Int, mp4Time: Long): Int {
        var pos = start
        var patched = 0
        while (pos + 8 <= end && pos + 8 <= file.size) {
            val boxSize32 = u32(file, pos)
            val type = String(file, pos + 4, 4, StandardCharsets.US_ASCII)
            var header = 8
            val boxSize: Long = when {
                boxSize32 == 1L -> {
                    if (pos + 16 > end) break
                    header = 16
                    u64(file, pos + 8)
                }
                boxSize32 == 0L -> (end - pos).toLong()
                else -> boxSize32
            }
            if (boxSize < header || pos + boxSize > end) break
            val bodyStart = pos + header
            val bodyEnd = pos + boxSize.toInt()
            when (type) {
                "moov", "trak", "mdia" -> patched += walkBytes(file, bodyStart, bodyEnd, mp4Time)
                "mvhd", "tkhd", "mdhd" -> {
                    if (patchBytesFullBox(file, bodyStart, bodyEnd, mp4Time)) patched++
                }
            }
            pos += boxSize.toInt()
        }
        return patched
    }

    private fun patchBytesFullBox(file: ByteArray, bodyStart: Int, bodyEnd: Int, mp4Time: Long): Boolean {
        if (bodyStart + 12 > bodyEnd) return false
        val version = file[bodyStart].toInt() and 0xFF
        return if (version == 1) {
            if (bodyStart + 20 > bodyEnd) return false
            putU64(file, bodyStart + 4, mp4Time)
            putU64(file, bodyStart + 12, mp4Time)
            true
        } else {
            putU32(file, bodyStart + 4, mp4Time)
            putU32(file, bodyStart + 8, mp4Time)
            true
        }
    }

    private fun u32(file: ByteArray, offset: Int): Long {
        return ((file[offset].toLong() and 0xFF) shl 24) or
            ((file[offset + 1].toLong() and 0xFF) shl 16) or
            ((file[offset + 2].toLong() and 0xFF) shl 8) or
            (file[offset + 3].toLong() and 0xFF)
    }

    private fun u64(file: ByteArray, offset: Int): Long {
        return (u32(file, offset) shl 32) or u32(file, offset + 4)
    }

    private fun putU32(file: ByteArray, offset: Int, value: Long) {
        val v = value and 0xFFFF_FFFFL
        file[offset] = (v shr 24).toByte()
        file[offset + 1] = (v shr 16).toByte()
        file[offset + 2] = (v shr 8).toByte()
        file[offset + 3] = v.toByte()
    }

    private fun putU64(file: ByteArray, offset: Int, value: Long) {
        putU32(file, offset, value ushr 32)
        putU32(file, offset + 4, value)
    }
}
