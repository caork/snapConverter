package com.snapconverter.app

import android.content.ContentUris
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.snapconverter.engine.CompressionEngine
import com.snapconverter.engine.policy.CompressionMode
import com.snapconverter.engine.policy.CompressionRequest
import com.snapconverter.engine.policy.MediaKind
import com.snapconverter.engine.policy.OutputImageCodec
import com.snapconverter.engine.policy.OutputVideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device hardware smoke tests for the new conversion features:
 * audio passthrough extraction, precise trim, and mute. These run the real
 * Qualcomm MediaCodec pipeline; they are skipped when no suitable source
 * video exists on the device.
 */
@RunWith(AndroidJUnit4::class)
class EngineHardwareSmokeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val engine = CompressionEngine(context)

    private fun pickSourceVideo(): Pair<Uri, Long>? {
        val collection = Uri.parse("content://media/external/video/media")
        val projection = arrayOf("_id", "duration")
        context.contentResolver.query(
            collection,
            projection,
            "duration >= 10000 AND duration <= 90000",
            null,
            "duration DESC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                val duration = cursor.getLong(1)
                return ContentUris.withAppendedId(collection, id) to duration
            }
        }
        return null
    }

    private fun openOutput(name: String): Pair<File, ParcelFileDescriptor> {
        val dir = context.externalCacheDir ?: File(context.cacheDir, "smoke")
        dir.mkdirs()
        val file = File(dir, name)
        if (file.exists()) file.delete()
        val pfd = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_WRITE or
                ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE,
        )
        return file to pfd
    }

    private fun trackInfo(file: File): Pair<String?, Long> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var mime: String? = null
            var durationUs = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val m = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("video/")) {
                    mime = m
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    }
                }
            }
            return mime to durationUs
        } finally {
            extractor.release()
        }
    }

    private fun audioTrackDurationUs(file: File): Long? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val m = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("audio/")) {
                    return if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        format.getLong(MediaFormat.KEY_DURATION)
                    } else {
                        0L
                    }
                }
            }
            return null
        } finally {
            extractor.release()
        }
    }

    private fun hasAudioTrack(file: File): Boolean = audioTrackDurationUs(file) != null

    @Test
    fun audioExtractionProducesM4aWithAudioTrack() {
        val source = pickSourceVideo()
        assumeTrue("no suitable test video on device", source != null)
        val (uri, _) = source!!
        // Expectation is the SOURCE audio track duration (not MediaStore's
        // video duration): some screen recordings carry a truncated track.
        val sourceAudioUs = sourceAudioTrackDurationUs(uri)
        assumeTrue("source has no audio", sourceAudioUs != null)
        val (file, pfd) = openOutput("smoke_audio.m4a")
        pfd.use {
            val samples = engine.extractAudio(uri, pfd)
            assertTrue("expected samples copied", samples > 0)
        }
        assertTrue("output missing", file.length() > 1024)
        val audioUs = audioTrackDurationUs(file)
        assertNotNull("extracted file has no audio track", audioUs)
        val expectedMs = (sourceAudioUs ?: 0) / 1000
        assertTrue(
            "source audio too short to be meaningful: " + expectedMs + "ms",
            expectedMs > 2000,
        )
        // Passthrough must preserve the source audio track duration closely.
        val outMs = (audioUs ?: 0) / 1000
        assertTrue(
            "duration drift too large: " + outMs + "ms vs " + expectedMs + "ms",
            Math.abs(outMs - expectedMs) < 2000,
        )
    }

    private fun sourceAudioTrackDurationUs(uri: Uri): Long? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val m = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("audio/")) {
                    return if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        format.getLong(MediaFormat.KEY_DURATION)
                    } else {
                        0L
                    }
                }
            }
            return null
        } finally {
            extractor.release()
        }
    }

    private fun hasAudioTrack(uri: Uri): Boolean {
        return sourceAudioTrackDurationUs(uri) != null
    }

    @Test
    fun trimTranscodesOnlySelectedWindow() {
        val source = pickSourceVideo()
        assumeTrue("no suitable test video on device", source != null)
        val (uri, _) = source!!
        val (file, pfd) = openOutput("smoke_trim.mp4")
        val request = CompressionRequest(
            kind = MediaKind.VIDEO,
            mode = CompressionMode.QUALITY,
            appQuality = 70,
            videoCodec = OutputVideoCodec.HEVC,
            trimStartUs = 2_000_000L,
            trimEndUs = 6_000_000L,
        )
        pfd.use { engine.compress(MediaKind.VIDEO, uri, pfd, request) }
        assertTrue("output missing", file.length() > 1024)
        val (mime, durationUs) = trackInfo(file)
        assertTrue("expected HEVC output, was " + mime, mime?.contains("hevc") == true)
        val durationMs = durationUs / 1000
        assertTrue(
            "trimmed duration " + durationMs + "ms not near 4000ms",
            durationMs in 3000..5500,
        )
    }

    @Test
    fun muteDropsAudioTrack() {
        val source = pickSourceVideo()
        assumeTrue("no suitable test video on device", source != null)
        val (uri, _) = source!!
        assumeTrue("source has no audio", hasAudioTrack(uri))
        val (file, pfd) = openOutput("smoke_mute.mp4")
        val request = CompressionRequest(
            kind = MediaKind.VIDEO,
            mode = CompressionMode.QUALITY,
            appQuality = 70,
            videoCodec = OutputVideoCodec.HEVC,
            muteAudio = true,
        )
        pfd.use { engine.compress(MediaKind.VIDEO, uri, pfd, request) }
        assertTrue("output missing", file.length() > 1024)
        assertTrue("muted output still has an audio track", !hasAudioTrack(file))
        val (mime, _) = trackInfo(file)
        assertTrue("expected video track", mime?.startsWith("video/") == true)
    }

    @Test
    fun emptyTrimRangeFailsLoudly() {
        val source = pickSourceVideo()
        assumeTrue("no suitable test video on device", source != null)
        val (uri, _) = source!!
        val (file, pfd) = openOutput("smoke_empty_trim.mp4")
        val request = CompressionRequest(
            kind = MediaKind.VIDEO,
            mode = CompressionMode.QUALITY,
            trimStartUs = 5_000_000L,
            trimEndUs = 5_000_000L,
        )
        try {
            pfd.use { engine.compress(MediaKind.VIDEO, uri, pfd, request) }
            throw AssertionError("empty trim range should throw")
        } catch (expected: IllegalArgumentException) {
            assertEquals(true, expected.message?.contains("裁剪范围") == true)
        } finally {
            pfd.close()
            file.delete()
        }
    }

    /**
     * Test fixture only: draws a gradient PNG into the cache dir. The product
     * encode path under test is the engine's hardware AVIF/HEIC pipeline;
     * Bitmap.compress here never touches product code.
     */
    private fun createTestImage(): Uri? {
        return try {
            val dir = context.externalCacheDir ?: File(context.cacheDir, "smoke")
            dir.mkdirs()
            val file = File(dir, "fixture_source.png")
            if (file.exists()) file.delete()
            val w = 2000
            val h = 1500
            val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint()
            val shader = android.graphics.LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                0xFF3DDC84.toInt(), 0xFF118060.toInt(),
                android.graphics.Shader.TileMode.CLAMP,
            )
            paint.shader = shader
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
            paint.color = 0xFF10241C.toInt()
            paint.textSize = 220f
            canvas.drawText("SNAP", 160f, h / 2f + 60f, paint)
            file.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            Uri.fromFile(file)
        } catch (t: Throwable) {
            null
        }
    }

    private fun decodeSize(file: File): Pair<Int, Int> {
        val source = android.graphics.ImageDecoder.createSource(file)
        var w = 0
        var h = 0
        android.graphics.ImageDecoder.decodeBitmap(source) { decoder, header, _ ->
            w = header.size.width
            h = header.size.height
            decoder.setTargetSize(1, 1)
        }
        return w to h
    }

    @Test
    fun avifHardwareEncodeRoundTrip() {
        val uri = createTestImage()
        assumeTrue("could not create fixture image", uri != null)
        val engineCaps = engine.probeDevice()
        assumeTrue("no hardware AV1 encoder", engineCaps.hardwareAv1Encoder)
        val (file, pfd) = openOutput("smoke_avif.avif")
        val request = CompressionRequest(
            kind = MediaKind.IMAGE,
            mode = CompressionMode.QUALITY,
            appQuality = 80,
            imageCodec = OutputImageCodec.AVIF,
        )
        pfd.use { engine.compress(MediaKind.IMAGE, uri!!, pfd, request) }
        assertTrue("avif output too small: " + file.length(), file.length() > 2048)
        val (w, h) = decodeSize(file)
        assertTrue("unexpected decode size " + w + "x" + h, w > 0 && h > 0)
    }

    @Test
    fun customImageSizeApplied() {
        val uri = createTestImage()
        assumeTrue("could not create fixture image", uri != null)
        val (file, pfd) = openOutput("smoke_custom.heic")
        val request = CompressionRequest(
            kind = MediaKind.IMAGE,
            mode = CompressionMode.QUALITY,
            appQuality = 80,
            imageCodec = OutputImageCodec.HEIC,
            imageCustomWidth = 1234,
            imageCustomHeight = 567,
        )
        pfd.use { engine.compress(MediaKind.IMAGE, uri!!, pfd, request) }
        assertTrue("heic output too small: " + file.length(), file.length() > 2048)
        val (w, h) = decodeSize(file)
        assertEquals(1234, w)
        assertEquals(566, h)
    }
}
