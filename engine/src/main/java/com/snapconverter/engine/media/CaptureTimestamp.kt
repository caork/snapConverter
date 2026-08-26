package com.snapconverter.engine.media

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class SourceIdentity(
    val displayName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    /** Wall-clock capture/shooting time, milliseconds since Unix epoch. Null if unknown. */
    val captureTimeMs: Long?,
)

object CaptureTimestamp {

    private val EXIF_DATE = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }
    private val ISO_BASIC = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun read(context: Context, uri: Uri, fallbackMime: String = ""): SourceIdentity {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: fallbackMime
        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "media"
        var size = 0L
        var capture = queryMediaStoreTimestamp(resolver, uri)
        resolver.query(uri, null, null, null, null)?.use { c ->
            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
            val takenIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
            val addedIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val modifiedIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            if (c.moveToFirst()) {
                if (nameIdx >= 0) displayName = c.getString(nameIdx) ?: displayName
                if (sizeIdx >= 0) size = c.getLong(sizeIdx)
                if (capture == null && takenIdx >= 0 && !c.isNull(takenIdx)) {
                    capture = EpochMs.normalize(c.getLong(takenIdx))
                }
                if (capture == null && addedIdx >= 0 && !c.isNull(addedIdx)) {
                    capture = EpochMs.normalize(c.getLong(addedIdx))
                }
                if (capture == null && modifiedIdx >= 0 && !c.isNull(modifiedIdx)) {
                    capture = EpochMs.normalize(c.getLong(modifiedIdx))
                }
            }
        }
        if (capture == null) capture = readExifTimestamp(resolver, uri)
        if (capture == null) capture = readRetrieverTimestamp(context, uri)
        if (size <= 0) {
            runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { size = it.length }
            }
        }
        return SourceIdentity(
            displayName = displayName,
            mimeType = mime,
            fileSizeBytes = size.coerceAtLeast(0),
            captureTimeMs = capture,
        )
    }

    fun formatExif(epochMs: Long): String = EXIF_DATE.format(epochMs)

    fun parseFlexible(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim().removeSuffix("Z")
        runCatching { return EXIF_DATE.parse(trimmed)?.time }.getOrNull()?.let { return it }
        runCatching {
            val basic = trimmed.take(15)
            return ISO_BASIC.parse(basic)?.time
        }.getOrNull()?.let { return it }
        raw.toLongOrNull()?.let { return EpochMs.normalize(it) }
        return null
    }

    fun copyExifDates(resolver: ContentResolver, input: Uri, output: Uri) {
        val inStream = resolver.openInputStream(input) ?: return
        val inExif = inStream.use { ExifInterface(it) }
        resolver.openFileDescriptor(output, "rw")?.use { pfd ->
            val outExif = ExifInterface(pfd.fileDescriptor)
            DATE_TAGS.forEach { tag ->
                inExif.getAttribute(tag)?.let { outExif.setAttribute(tag, it) }
            }
            outExif.saveAttributes()
        }
    }

    fun stampExif(resolver: ContentResolver, output: Uri, captureEpochMs: Long) {
        val formatted = formatExif(captureEpochMs)
        resolver.openFileDescriptor(output, "rw")?.use { pfd ->
            val exif = ExifInterface(pfd.fileDescriptor)
            exif.setAttribute(ExifInterface.TAG_DATETIME, formatted)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, formatted)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, formatted)
            exif.saveAttributes()
        }
    }

    private fun queryMediaStoreTimestamp(resolver: ContentResolver, uri: Uri): Long? {
        val projection = arrayOf(
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        return runCatching {
            resolver.query(uri, projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val taken = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val added = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val modified = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                when {
                    taken >= 0 && !c.isNull(taken) -> EpochMs.normalize(c.getLong(taken))
                    added >= 0 && !c.isNull(added) -> EpochMs.normalize(c.getLong(added))
                    modified >= 0 && !c.isNull(modified) -> EpochMs.normalize(c.getLong(modified))
                    else -> null
                }
            }
        }.getOrNull()
    }

    private fun readExifTimestamp(resolver: ContentResolver, uri: Uri): Long? {
        return runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                parseFlexible(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                    ?: parseFlexible(exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED))
                    ?: parseFlexible(exif.getAttribute(ExifInterface.TAG_DATETIME))
            }
        }.getOrNull()
    }

    private fun readRetrieverTimestamp(context: Context, uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            parseFlexible(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE))
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private val DATE_TAGS = listOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_ORIENTATION,
    )
}
