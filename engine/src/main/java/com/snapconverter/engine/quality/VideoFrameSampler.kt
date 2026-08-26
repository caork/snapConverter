package com.snapconverter.engine.quality

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import com.snapconverter.engine.ScLog
import com.snapconverter.engine.codec.HardwareCodecSelector
import kotlin.math.abs

/**
 * Grabs luma / I420 near target timestamps with a hardware decoder.
 * Used only for post-encode quality, not the transcode hot path.
 */
internal class VideoFrameSampler(
    private val context: Context,
    private val selector: HardwareCodecSelector,
) {
    data class Frame(
        val width: Int,
        val height: Int,
        val yFull: FloatArray,
        val i420Limited: ByteArray,
        val ptsUs: Long,
    )

    fun sample(uri: Uri, timesUs: LongArray, outW: Int, outH: Int): List<Frame> {
        if (timesUs.isEmpty() || outW < 16 || outH < 16) return emptyList()
        return runCatching { sampleWithDecoder(uri, timesUs, outW, outH) }
            .onFailure { ScLog.w("frame sampler decoder failed, falling back", it) }
            .getOrDefault(emptyList())
    }

    private fun sampleWithDecoder(uri: Uri, timesUs: LongArray, outW: Int, outH: Int): List<Frame> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return emptyList()
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return emptyList()
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            )
            val candidate = selector.selectDecoder(mime)
            codec = selector.createByName(candidate)
            codec.configure(format, null, null, 0)
            codec.start()
            val limitedHint = !format.containsKey(MediaFormat.KEY_COLOR_RANGE) ||
                format.getInteger(MediaFormat.KEY_COLOR_RANGE) != MediaFormat.COLOR_RANGE_FULL
            val frames = ArrayList<Frame>(timesUs.size)
            for (t in timesUs) {
                val frame = grabNear(extractor, codec, t, outW, outH, limitedHint) ?: continue
                frames += frame
            }
            ScLog.i("frame sampler ${frames.size}/${timesUs.size} ${outW}x$outH limitedHint=$limitedHint $mime")
            return frames
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun grabNear(
        extractor: MediaExtractor,
        codec: MediaCodec,
        targetUs: Long,
        outW: Int,
        outH: Int,
        srcLimited: Boolean,
    ): Frame? {
        extractor.seekTo(targetUs.coerceAtLeast(0L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        codec.flush()
        val info = MediaCodec.BufferInfo()
        var best: Frame? = null
        var bestDt = Long.MAX_VALUE
        var inputEos = false
        val deadline = SystemClock.elapsedRealtime() + 4_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!inputEos) {
                val inIx = codec.dequeueInputBuffer(8_000)
                if (inIx >= 0) {
                    val buf = codec.getInputBuffer(inIx)!!
                    val n = extractor.readSampleData(buf, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(inIx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputEos = true
                    } else {
                        codec.queueInputBuffer(inIx, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIx = codec.dequeueOutputBuffer(info, 8_000)
            if (outIx == MediaCodec.INFO_TRY_AGAIN_LATER) continue
            if (outIx < 0) continue
            val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            if (info.size > 0) {
                val pts = info.presentationTimeUs
                val dt = abs(pts - targetUs)
                if (dt < bestDt) {
                    val image = codec.getOutputImage(outIx)
                    if (image != null) {
                        best = imageToFrame(image, outW, outH, pts, srcLimited)
                        bestDt = dt
                        image.close()
                    }
                }
                codec.releaseOutputBuffer(outIx, false)
                if (pts >= targetUs || dt <= 40_000L) return best
                if (pts > targetUs + 120_000L && best != null) return best
            } else {
                codec.releaseOutputBuffer(outIx, false)
            }
            if (eos) break
        }
        return best
    }

    private fun imageToFrame(
        image: android.media.Image,
        outW: Int,
        outH: Int,
        ptsUs: Long,
        srcLimited: Boolean,
    ): Frame {
        val crop = image.cropRect
        val srcW = crop.width().coerceAtLeast(2)
        val srcH = crop.height().coerceAtLeast(2)
        val w = outW.coerceAtMost(srcW) / 2 * 2
        val h = outH.coerceAtMost(srcH) / 2 * 2
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yFull = FloatArray(w * h)
        val i420 = ByteArray(w * h + w * h / 2)
        val rawY = IntArray(w * h)
        val yRow = yPlane.rowStride
        val yPix = yPlane.pixelStride
        val yBuf = yPlane.buffer.duplicate()
        val x0 = crop.left
        val y0 = crop.top
        var o = 0
        var yMax = 0
        for (row in 0 until h) {
            val srcRow = y0 + row * srcH / h
            for (col in 0 until w) {
                val srcCol = x0 + col * srcW / w
                val idx = srcRow * yRow + srcCol * yPix
                val yv = yBuf.get(idx).toInt() and 0xFF
                rawY[o++] = yv
                if (yv > yMax) yMax = yv
            }
        }
        val limited = if (yMax > 240) false else srcLimited
        for (i in rawY.indices) {
            yFull[i] = yToFull(rawY[i], limited)
            i420[i] = yToLimited(rawY[i], limited)
        }
        val uvW = w / 2
        val uvH = h / 2
        var ui = w * h
        var vi = ui + uvW * uvH
        val uRow = uPlane.rowStride
        val uPix = uPlane.pixelStride
        val vRow = vPlane.rowStride
        val vPix = vPlane.pixelStride
        val uBuf = uPlane.buffer.duplicate()
        val vBuf = vPlane.buffer.duplicate()
        val uvX0 = x0 / 2
        val uvY0 = y0 / 2
        val srcUvW = srcW / 2
        val srcUvH = srcH / 2
        for (row in 0 until uvH) {
            val srcRow = uvY0 + row * srcUvH / uvH
            for (col in 0 until uvW) {
                val srcCol = uvX0 + col * srcUvW / uvW
                i420[ui++] = uBuf.get(srcRow * uRow + srcCol * uPix)
                i420[vi++] = vBuf.get(srcRow * vRow + srcCol * vPix)
            }
        }
        return Frame(w, h, yFull, i420, ptsUs)
    }

    private fun yToLimited(y: Int, srcLimited: Boolean): Byte {
        return if (srcLimited) y.coerceIn(16, 235).toByte()
        else ((y * 219 + 127) / 255 + 16).coerceIn(16, 235).toByte()
    }

    private fun yToFull(y: Int, srcLimited: Boolean): Float {
        return if (srcLimited) {
            ((y - 16).coerceAtLeast(0) * 255f / 219f).coerceIn(0f, 255f)
        } else {
            y.toFloat()
        }
    }
}
