package com.snapconverter.app.scan

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.snapconverter.engine.policy.AuditResult
import com.snapconverter.engine.policy.AuditSensitivity
import com.snapconverter.engine.policy.AuditTuning
import com.snapconverter.engine.policy.MediaAudit
import com.snapconverter.engine.policy.MediaFact
import com.snapconverter.engine.policy.MediaKind
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One library entry that the audit flagged. */
data class ScanItem(
    val id: Long,
    val uri: Uri,
    val kind: MediaKind,
    val name: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val mime: String,
    val modifiedSec: Long,
    /** MediaStore's album id — the folder this file sits in. */
    val bucketId: Long,
    val folder: String,
    val audit: AuditResult,
) {
    val fact: MediaFact
        get() = MediaFact(kind, width, height, sizeBytes, durationMs, mime)
}

/** One folder's share of the findings, for the folder filter. */
data class FolderSummary(
    val bucketId: Long,
    val name: String,
    val count: Int,
    val videos: Int,
    val images: Int,
    val sizeBytes: Long,
    val savingBytes: Long,
)

data class ScanReport(
    val items: List<ScanItem>,
    /** Rows walked, including the ones that came back clean. */
    val scanned: Int,
    val videosScanned: Int,
    val imagesScanned: Int,
    /** Rows MediaStore could not describe well enough to judge. */
    val skipped: Int,
    val elapsedMs: Long,
    /** Folders that hold at least one flagged file, biggest win first. */
    val folders: List<FolderSummary> = emptyList(),
    /** Wall clock when the scan ran; the date filter measures from here. */
    val scannedAtSec: Long = 0L,
) {
    val totalSizeBytes: Long get() = items.sumOf { it.sizeBytes }
    val totalSavingBytes: Long get() = items.sumOf { it.audit.savingBytes }
}

/**
 * Finds the files in the user's library that are larger than they need to be.
 *
 * Speed comes from what this does *not* do: MediaStore already indexes width,
 * height, duration, size, mime, folder and date for every item, so the whole
 * library is two cursor walks over indexed columns and no file ever gets
 * opened — no `MediaMetadataRetriever`, no decode, no thumbnail. Bitrate is
 * `size * 8 / duration`, and the verdict is pure arithmetic in [MediaAudit].
 * A ten-thousand item library is a few hundred milliseconds, and it costs the
 * same whether the files are on internal storage or an SD card.
 *
 * Column indices are resolved once per cursor rather than per row, rows are
 * sorted biggest-first so the interesting results are also the first ones, and
 * the loop checks for cancellation as it goes.
 */
class MediaLibraryScanner(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    suspend fun scan(
        sensitivity: AuditSensitivity = AuditSensitivity.STANDARD,
        quality: Int = MediaAudit.AUDIT_QUALITY,
        onProgress: (scanned: Int, found: Int) -> Unit = { _, _ -> },
    ): ScanReport = scan(sensitivity.tuning(), quality, onProgress)

    suspend fun scan(
        tuning: AuditTuning,
        quality: Int = MediaAudit.AUDIT_QUALITY,
        onProgress: (scanned: Int, found: Int) -> Unit = { _, _ -> },
    ): ScanReport {
        val started = System.nanoTime()
        val found = ArrayList<ScanItem>(64)
        var scanned = 0
        var skipped = 0
        var videos = 0
        var images = 0

        for (kind in listOf(MediaKind.VIDEO, MediaKind.IMAGE)) {
            val collection = when (kind) {
                MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(VOLUME)
                MediaKind.IMAGE -> MediaStore.Images.Media.getContentUri(VOLUME)
            }
            val projection = when (kind) {
                MediaKind.VIDEO -> VIDEO_PROJECTION
                MediaKind.IMAGE -> IMAGE_PROJECTION
            }
            resolver.query(collection, projection, null, null, SORT)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val widthIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val modifiedIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val bucketIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val bucketNameIdx =
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val durationIdx = if (kind == MediaKind.VIDEO) {
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                } else {
                    -1
                }
                while (cursor.moveToNext()) {
                    if (scanned % CANCEL_CHECK_ROWS == 0) currentCoroutineContext().ensureActive()
                    scanned++
                    if (kind == MediaKind.VIDEO) videos++ else images++
                    val size = cursor.getLongOrZero(sizeIdx)
                    val width = cursor.getIntOrZero(widthIdx)
                    val height = cursor.getIntOrZero(heightIdx)
                    val duration = if (durationIdx >= 0) cursor.getLongOrZero(durationIdx) else 0L
                    if (size <= 0L || width <= 0 || height <= 0 ||
                        (kind == MediaKind.VIDEO && duration <= 0L)
                    ) {
                        // MediaStore has no geometry for this row (some
                        // screenshots, some sideloaded files). Judging it would
                        // mean guessing, so it is reported as skipped instead.
                        skipped++
                        continue
                    }
                    val mime = cursor.getString(mimeIdx).orEmpty()
                    val fact = MediaFact(kind, width, height, size, duration, mime)
                    val audit = MediaAudit.audit(fact, tuning, quality)
                    if (!audit.flagged) continue
                    val id = cursor.getLong(idIdx)
                    found += ScanItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        kind = kind,
                        name = cursor.getString(nameIdx).orEmpty(),
                        sizeBytes = size,
                        width = width,
                        height = height,
                        durationMs = duration,
                        mime = mime,
                        modifiedSec = cursor.getLongOrZero(modifiedIdx),
                        bucketId = cursor.getLongOrZero(bucketIdx),
                        folder = folderName(
                            bucket = cursor.getString(bucketNameIdx),
                            relativePath = cursor.getString(pathIdx),
                        ),
                        audit = audit,
                    )
                    if (found.size % PROGRESS_EVERY == 0) onProgress(scanned, found.size)
                }
            }
            onProgress(scanned, found.size)
        }

        // Biggest win first, regardless of which collection it came from.
        found.sortByDescending { it.audit.savingBytes }
        return ScanReport(
            items = found,
            scanned = scanned,
            videosScanned = videos,
            imagesScanned = images,
            skipped = skipped,
            elapsedMs = (System.nanoTime() - started) / 1_000_000L,
            folders = summariseFolders(found),
            scannedAtSec = System.currentTimeMillis() / 1000L,
        )
    }

    /** Folders are derived from the findings, so the filter only ever lists
     *  places that actually hold something worth converting. */
    private fun summariseFolders(items: List<ScanItem>): List<FolderSummary> =
        items.groupBy { it.bucketId }
            .map { (bucketId, group) ->
                FolderSummary(
                    bucketId = bucketId,
                    name = group.first().folder,
                    count = group.size,
                    videos = group.count { it.kind == MediaKind.VIDEO },
                    images = group.count { it.kind == MediaKind.IMAGE },
                    sizeBytes = group.sumOf { it.sizeBytes },
                    savingBytes = group.sumOf { it.audit.savingBytes },
                )
            }
            .sortedByDescending { it.savingBytes }

    /**
     * BUCKET_DISPLAY_NAME is the album name ("Camera"); the relative path adds
     * the parent ("DCIM/Camera"), which is what distinguishes a camera roll
     * from a chat app's download folder of the same name.
     */
    private fun folderName(bucket: String?, relativePath: String?): String {
        val path = relativePath?.trim('/').orEmpty()
        if (path.isNotEmpty()) return path
        return bucket?.takeIf { it.isNotBlank() } ?: "未知文件夹"
    }

    private fun Cursor.getLongOrZero(index: Int): Long =
        if (isNull(index)) 0L else getLong(index)

    private fun Cursor.getIntOrZero(index: Int): Int =
        if (isNull(index)) 0 else getInt(index)

    private companion object {
        const val VOLUME = MediaStore.VOLUME_EXTERNAL
        const val CANCEL_CHECK_ROWS = 256
        const val PROGRESS_EVERY = 8
        val SORT = MediaStore.MediaColumns.SIZE + " DESC"

        val IMAGE_PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )

        val VIDEO_PROJECTION = IMAGE_PROJECTION + MediaStore.MediaColumns.DURATION
    }
}
