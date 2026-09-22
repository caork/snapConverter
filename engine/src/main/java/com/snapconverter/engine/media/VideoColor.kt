package com.snapconverter.engine.media

import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import java.nio.ByteBuffer

/**
 * Color / HDR metadata from the container. HDR in → HDR out uses this as-is;
 * we do not tone-map.
 */
data class VideoColor(
    val standard: Int? = null,
    val transfer: Int? = null,
    val range: Int? = null,
    val hdrStaticInfo: ByteArray? = null,
    val tenBit: Boolean = false,
) {
    val isHdr: Boolean get() = isHdrTransfer(transfer)

    val label: String get() = when {
        transfer == MediaFormat.COLOR_TRANSFER_ST2084 -> "HDR10"
        transfer == MediaFormat.COLOR_TRANSFER_HLG -> "HLG"
        tenBit -> "HDR"
        standard == MediaFormat.COLOR_STANDARD_BT2020 -> "BT.2020"
        else -> "SDR"
    }

    /** EGL_GL_COLORSPACE_* for the encoder window, or null for SDR. */
    val eglGlColorspace: Int? get() = when (transfer) {
        MediaFormat.COLOR_TRANSFER_ST2084 -> EGL_GL_COLORSPACE_BT2020_PQ_EXT
        MediaFormat.COLOR_TRANSFER_HLG -> EGL_GL_COLORSPACE_BT2020_HLG_EXT
        else -> null
    }

    fun withDefaultsForEncode(): VideoColor {
        if (isHdr) {
            return copy(
                standard = standard ?: MediaFormat.COLOR_STANDARD_BT2020,
                transfer = transfer,
                range = range ?: MediaFormat.COLOR_RANGE_LIMITED,
            )
        }
        return copy(
            standard = standard ?: MediaFormat.COLOR_STANDARD_BT709,
            transfer = transfer ?: MediaFormat.COLOR_TRANSFER_SDR_VIDEO,
            range = range ?: MediaFormat.COLOR_RANGE_LIMITED,
        )
    }

    companion object {
        const val EGL_GL_COLORSPACE_KHR = 0x309D
        const val EGL_GL_COLORSPACE_BT2020_PQ_EXT = 0x3340
        const val EGL_GL_COLORSPACE_BT2020_HLG_EXT = 0x3540

        fun isHdrTransfer(transfer: Int?): Boolean =
            transfer == MediaFormat.COLOR_TRANSFER_ST2084 ||
                transfer == MediaFormat.COLOR_TRANSFER_HLG

        fun from(format: MediaFormat, mime: String? = null): VideoColor {
            val standard = intOrNull(format, MediaFormat.KEY_COLOR_STANDARD)
            val transfer = intOrNull(format, MediaFormat.KEY_COLOR_TRANSFER)
            val range = intOrNull(format, MediaFormat.KEY_COLOR_RANGE)
            val staticInfo = byteArrayOrNull(format, MediaFormat.KEY_HDR_STATIC_INFO)
            val hdrByMime = mime?.contains("dolby-vision", ignoreCase = true) == true
            return VideoColor(
                standard = standard,
                transfer = transfer
                    ?: if (hdrByMime) MediaFormat.COLOR_TRANSFER_ST2084 else null,
                range = range,
                hdrStaticInfo = staticInfo,
                tenBit = hdrByMime,
            )
        }

        fun from(retriever: MediaMetadataRetriever): VideoColor {
            if (Build.VERSION.SDK_INT < 31) return VideoColor()
            return VideoColor(
                standard = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_STANDARD)
                    ?.toIntOrNull(),
                transfer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)
                    ?.toIntOrNull(),
                range = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_RANGE)
                    ?.toIntOrNull(),
            )
        }

        fun merge(primary: VideoColor, fallback: VideoColor): VideoColor {
            val transfer = when {
                isHdrTransfer(primary.transfer) -> primary.transfer
                isHdrTransfer(fallback.transfer) -> fallback.transfer
                else -> primary.transfer ?: fallback.transfer
            }
            return VideoColor(
                standard = primary.standard ?: fallback.standard,
                transfer = transfer,
                range = primary.range ?: fallback.range,
                hdrStaticInfo = primary.hdrStaticInfo ?: fallback.hdrStaticInfo,
                tenBit = primary.tenBit || fallback.tenBit,
            )
        }

        private fun intOrNull(format: MediaFormat, key: String): Int? {
            if (!format.containsKey(key)) return null
            return runCatching { format.getInteger(key) }.getOrNull()
        }

        private fun byteArrayOrNull(format: MediaFormat, key: String): ByteArray? {
            if (!format.containsKey(key)) return null
            val buf: ByteBuffer = runCatching { format.getByteBuffer(key) }.getOrNull() ?: return null
            val dup = buf.duplicate()
            if (!dup.hasRemaining()) return null
            val out = ByteArray(dup.remaining())
            dup.get(out)
            return out
        }
    }
}
