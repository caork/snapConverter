package com.snapconverter.app.media

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.snapconverter.engine.media.CaptureTimestamp
import com.snapconverter.engine.media.Mp4TimestampPatcher
import com.snapconverter.engine.policy.MediaKind
import com.snapconverter.engine.policy.OutputImageCodec
import java.io.File
import java.io.FileOutputStream

class NeedsUserConsentException(val sender: IntentSender) :
    Exception("需要系统授权才能改写原文件")

data class ReplaceRequest(
    val original: Uri,
    val encoded: Uri,
    val kind: MediaKind,
    val preserveCaptureTime: Boolean,
    val captureTimeMs: Long?,
    val imageCodec: OutputImageCodec,
)

data class ReplaceResult(
    val uri: Uri,
    val displayName: String,
    val folderLabel: String,
    val sizeBytes: Long,
)

class OriginalReplacer(private val app: Application) {
    private val resolver get() = app.contentResolver

    fun replace(request: ReplaceRequest): ReplaceResult {
        val original = request.original
        val encoded = request.encoded
        if (original == encoded) {
            val meta = queryMeta(original)
            return ReplaceResult(
                uri = original,
                displayName = meta.displayName.ifBlank { "media" },
                folderLabel = folderLabel(meta.relativePath),
                sizeBytes = meta.size.takeIf { it > 0 } ?: querySize(original),
            )
        }
        ensureWritable(original)
        overwrite(original, encoded)
        val mime = outputMime(request.kind, request.imageCodec)
        val size = querySize(original).takeIf { it > 0 } ?: querySize(encoded)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.SIZE, size)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            val modified = if (request.preserveCaptureTime && request.captureTimeMs != null) {
                request.captureTimeMs / 1000
            } else {
                System.currentTimeMillis() / 1000
            }
            put(MediaStore.MediaColumns.DATE_MODIFIED, modified)
            if (request.preserveCaptureTime && request.captureTimeMs != null) {
                put(MediaStore.MediaColumns.DATE_TAKEN, request.captureTimeMs)
            }
        }
        runCatching { resolver.update(original, values, null, null) }
        if (request.preserveCaptureTime && request.captureTimeMs != null) {
            applyCaptureTime(original, request.kind, encoded, request.captureTimeMs)
        }
        if (encoded != original) {
            runCatching { resolver.delete(encoded, null, null) }
        }
        val meta = queryMeta(original)
        return ReplaceResult(
            uri = original,
            displayName = meta.displayName.ifBlank { "media" },
            folderLabel = folderLabel(meta.relativePath),
            sizeBytes = meta.size.takeIf { it > 0 } ?: size,
        )
    }

    private fun ensureWritable(uri: Uri) {
        if (canWrite(uri)) return
        if (Build.VERSION.SDK_INT >= 30 && isMediaStore(uri)) {
            val pending = MediaStore.createWriteRequest(resolver, listOf(uri))
            throw NeedsUserConsentException(pending.intentSender)
        }
    }

    private fun canWrite(uri: Uri): Boolean {
        val persisted = resolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
        if (persisted) return true
        return runCatching {
            resolver.openFileDescriptor(uri, "rw")?.close()
            true
        }.getOrDefault(false)
    }

    private fun overwrite(dest: Uri, src: Uri) {
        try {
            (resolver.openFileDescriptor(dest, "rwt")
                ?: resolver.openFileDescriptor(dest, "rw"))?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { out ->
                    resolver.openInputStream(src)?.use { input ->
                        input.copyTo(out)
                    } ?: error("无法读取转换结果")
                    out.flush()
                    out.channel.truncate(out.channel.position())
                    out.fd.sync()
                }
            } ?: error("无法写入原文件")
        } catch (e: RecoverableSecurityException) {
            throw NeedsUserConsentException(e.userAction.actionIntent.intentSender)
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= 30 && isMediaStore(dest)) {
                val pending = MediaStore.createWriteRequest(resolver, listOf(dest))
                throw NeedsUserConsentException(pending.intentSender)
            }
            throw e
        }
    }

    private fun applyCaptureTime(output: Uri, kind: MediaKind, source: Uri, captureMs: Long) {
        val path = queryPath(output)
        if (kind == MediaKind.VIDEO) {
            path?.let { runCatching { Mp4TimestampPatcher.patchFile(it, captureMs) } }
        } else {
            runCatching { CaptureTimestamp.copyExifDates(resolver, source, output) }
            runCatching { CaptureTimestamp.stampExif(resolver, output, captureMs) }
        }
        path?.let { File(it).setLastModified(captureMs) }
    }

    private fun outputMime(kind: MediaKind, imageCodec: OutputImageCodec): String = when (kind) {
        MediaKind.VIDEO -> "video/mp4"
        MediaKind.IMAGE -> when (imageCodec) {
            OutputImageCodec.HEIC -> "image/heif"
            OutputImageCodec.AVIF -> "image/avif"
            OutputImageCodec.JPEG -> "image/jpeg"
        }
    }

    private fun isMediaStore(uri: Uri): Boolean =
        uri.authority?.startsWith("media") == true ||
            uri.authority == MediaStore.AUTHORITY

    private data class Meta(
        val displayName: String,
        val relativePath: String?,
        val size: Long,
    )

    private fun queryMeta(uri: Uri): Meta {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
        )
        resolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val pathIdx = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                return Meta(
                    displayName = if (nameIdx >= 0) c.getString(nameIdx).orEmpty() else "",
                    relativePath = if (pathIdx >= 0) c.getString(pathIdx) else null,
                    size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L,
                )
            }
        }
        return Meta(uri.lastPathSegment.orEmpty(), null, 0L)
    }

    private fun querySize(uri: Uri): Long {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return resolver.openFileDescriptor(uri, "r")?.statSize ?: 0L
    }

    private fun queryPath(uri: Uri): String? {
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
        )
        resolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
            if (dataIdx >= 0) {
                val data = c.getString(dataIdx)
                if (!data.isNullOrBlank()) return data
            }
            val rel = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val name = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            if (rel >= 0 && name >= 0) {
                val relative = c.getString(rel)?.trimEnd('/') ?: return null
                val display = c.getString(name) ?: return null
                return "/storage/emulated/0/$relative/$display"
            }
        }
        return null
    }

    private fun folderLabel(relativePath: String?): String {
        val path = relativePath?.trimEnd('/') ?: return "原位置"
        return path
            .replace("Movies", "影片")
            .replace("Pictures", "图片")
            .replace("DCIM", "相机")
    }
}
