package com.snapconverter.engine.quality

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.snapconverter.engine.ScLog
import com.snapconverter.engine.policy.MediaKind
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Full-reference PSNR-Y / SSIM after encode. Samples frames; not the hot encode path.
 */
class QualityAnalyzer(context: Context) {
    private val appContext = context.applicationContext

    fun compare(
        kind: MediaKind,
        original: Uri,
        encoded: Uri,
        durationUs: Long,
        encodedWidth: Int,
        encodedHeight: Int,
    ): QualityReport? {
        return runCatching {
            if (kind == MediaKind.IMAGE) {
                compareImages(original, encoded, encodedWidth, encodedHeight)
            } else {
                compareVideo(original, encoded, durationUs, encodedWidth, encodedHeight)
            }
        }.onFailure { ScLog.w("quality compare failed", it) }.getOrNull()
    }

    private fun compareImages(original: Uri, encoded: Uri, outW: Int, outH: Int): QualityReport? {
        val (cw, ch) = compareSize(outW, outH)
        val a = decodeScaled(original, cw, ch) ?: return null
        val b = decodeScaled(encoded, cw, ch) ?: return null
        return try {
            scoreBitmaps(listOf(a to b), cw, ch)
        } finally {
            a.recycle()
            b.recycle()
        }
    }

    private fun compareVideo(
        original: Uri,
        encoded: Uri,
        durationUs: Long,
        outW: Int,
        outH: Int,
    ): QualityReport? {
        val duration = durationUs.coerceAtLeast(1L)
        val (cw, ch) = compareSize(outW, outH)
        val n = sampleCount(duration)
        val orig = MediaMetadataRetriever()
        val dist = MediaMetadataRetriever()
        val pairs = mutableListOf<Pair<Bitmap, Bitmap>>()
        try {
            orig.setDataSource(appContext, original)
            dist.setDataSource(appContext, encoded)
            for (i in 0 until n) {
                val t = if (n == 1) duration / 2 else duration * i / (n - 1)
                val a = frameAt(orig, t, cw, ch) ?: continue
                val b = frameAt(dist, t, cw, ch)
                if (b == null) {
                    a.recycle()
                    continue
                }
                pairs += a to b
            }
            if (pairs.isEmpty()) return null
            return scoreBitmaps(pairs, cw, ch)
        } finally {
            pairs.forEach { (a, b) ->
                if (!a.isRecycled) a.recycle()
                if (!b.isRecycled) b.recycle()
            }
            orig.release()
            dist.release()
        }
    }

    private fun scoreBitmaps(pairs: List<Pair<Bitmap, Bitmap>>, width: Int, height: Int): QualityReport {
        var psnrSum = 0.0
        var ssimSum = 0.0
        val ref = FloatArray(width * height)
        val enc = FloatArray(width * height)
        val pixA = IntArray(width * height)
        val pixB = IntArray(width * height)
        val refI420 = ArrayList<ByteArray>(pairs.size)
        val distI420 = ArrayList<ByteArray>(pairs.size)
        var counted = 0
        val evenW = width / 2 * 2
        val evenH = height / 2 * 2
        for ((a, b) in pairs) {
            if (a.width != width || a.height != height || b.width != width || b.height != height) continue
            a.getPixels(pixA, 0, width, 0, 0, width, height)
            b.getPixels(pixB, 0, width, 0, 0, width, height)
            ImageMetrics.lumaBt601(pixA, ref)
            ImageMetrics.lumaBt601(pixB, enc)
            psnrSum += ImageMetrics.psnrY(ref, enc)
            ssimSum += ImageMetrics.ssimY(ref, enc, width, height)
            if (evenW >= 16 && evenH >= 16 && VmafNative.available) {
                refI420 += ArgbToI420.convert(pixA, evenW, evenH)
                distI420 += ArgbToI420.convert(pixB, evenW, evenH)
            }
            counted++
        }
        val n = counted.coerceAtLeast(1)
        val vmaf = if (refI420.size >= 1) {
            VmafNative.scoreI420(evenW, evenH, refI420.toTypedArray(), distI420.toTypedArray())
        } else {
            null
        }
        return QualityReport(
            psnrY = psnrSum / n,
            ssim = ssimSum / n,
            vmaf = vmaf,
            samples = counted,
            compareWidth = width,
            compareHeight = height,
        )
    }

    private fun frameAt(retriever: MediaMetadataRetriever, timeUs: Long, w: Int, h: Int): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    w,
                    h,
                )
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        }.getOrNull()?.let { raw ->
            if (raw.width == w && raw.height == h) raw
            else {
                val scaled = Bitmap.createScaledBitmap(raw, w, h, true)
                if (scaled !== raw) raw.recycle()
                scaled
            }
        }
    }

    private fun decodeScaled(uri: Uri, w: Int, h: Int): Bitmap? {
        return runCatching {
            val source = ImageDecoder.createSource(appContext.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val srcW = info.size.width.coerceAtLeast(1)
                val srcH = info.size.height.coerceAtLeast(1)
                val scale = maxOf(w.toFloat() / srcW, h.toFloat() / srcH)
                decoder.setTargetSize(
                    (srcW * scale).roundToInt().coerceAtLeast(1),
                    (srcH * scale).roundToInt().coerceAtLeast(1),
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }.let { bmp ->
                if (bmp.width == w && bmp.height == h) bmp
                else {
                    val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
                    if (scaled !== bmp) bmp.recycle()
                    scaled.copy(Bitmap.Config.ARGB_8888, false).also {
                        if (it !== scaled) scaled.recycle()
                    }
                }
            }
        }.getOrNull()
    }

    private fun compareSize(encodedW: Int, encodedH: Int): Pair<Int, Int> {
        val w = encodedW.coerceAtLeast(16)
        val h = encodedH.coerceAtLeast(16)
        val long = max(w, h)
        if (long <= MAX_COMPARE) return even(w) to even(h)
        val scale = MAX_COMPARE.toFloat() / long
        return even((w * scale).roundToInt()) to even((h * scale).roundToInt())
    }

    private fun even(v: Int): Int = (v / 2 * 2).coerceAtLeast(16)

    private fun sampleCount(durationUs: Long): Int {
        val sec = durationUs / 1_000_000.0
        return when {
            sec < 2 -> 4
            sec < 10 -> 8
            sec < 60 -> 12
            else -> 16
        }
    }

    companion object {
        private const val MAX_COMPARE = 1920
    }
}
