package com.snapconverter.app.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.snapconverter.engine.CompressionEngine
import com.snapconverter.engine.codec.MimeTypes
import com.snapconverter.engine.media.CaptureTimestamp
import com.snapconverter.engine.media.Mp4TimestampPatcher
import com.snapconverter.engine.policy.CompressionRequest
import com.snapconverter.engine.policy.MediaKind
import com.snapconverter.engine.policy.OutputImageCodec
import com.snapconverter.engine.progress.EncodeProgress
import java.io.File

/**
 * Where an encode lands and how it gets published.
 *
 * One writer for both callers: the single-file screen and the batch runner.
 * They must agree on the pending-then-commit MediaStore dance, on the capture
 * timestamp patching, and on cleaning up a half-written row when an encode
 * throws — a second copy of this logic would drift within a release.
 */
class MediaOutputWriter(context: Context, private val engine: CompressionEngine) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /** Where the file goes: a MediaStore relative path, or a SAF tree. */
    sealed interface Destination {
        data class Library(val relativePath: String, val label: String) : Destination
        data class Tree(val treeUri: Uri, val label: String) : Destination
    }

    data class Request(
        val kind: MediaKind,
        val input: Uri,
        val sourceName: String,
        val destination: Destination,
        val compression: CompressionRequest,
        /** Passthrough audio extraction instead of an encode. */
        val audioOnly: Boolean = false,
        val preserveCaptureTime: Boolean = true,
        val captureTimeMs: Long? = null,
    )

    data class Published(
        val uri: Uri,
        val displayName: String,
        val folderLabel: String,
        val sizeBytes: Long,
    )

    /** Blocking; call from [kotlinx.coroutines.Dispatchers.IO]. */
    fun write(request: Request, onProgress: (EncodeProgress) -> Unit = {}): Published {
        val ext = extensionFor(request)
        val displayName = outputName(request.sourceName, ext)
        return when (val destination = request.destination) {
            is Destination.Library -> writeToLibrary(request, destination, displayName, ext, onProgress)
            is Destination.Tree -> writeToTree(request, destination, displayName, ext, onProgress)
        }
    }

    private fun writeToLibrary(
        request: Request,
        destination: Destination.Library,
        displayName: String,
        ext: String,
        onProgress: (EncodeProgress) -> Unit,
    ): Published {
        val capture = request.captureTimeMs.takeIf { request.preserveCaptureTime }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(request, ext))
            put(MediaStore.MediaColumns.RELATIVE_PATH, destination.relativePath)
            if (capture != null) {
                put(MediaStore.MediaColumns.DATE_TAKEN, capture)
                put(MediaStore.MediaColumns.DATE_ADDED, capture / 1000)
                put(MediaStore.MediaColumns.DATE_MODIFIED, capture / 1000)
            }
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = when {
            request.audioOnly ->
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            request.kind == MediaKind.VIDEO ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else ->
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val out = resolver.insert(collection, values) ?: error("无法写入媒体库")
        try {
            encodeInto(out, request, onProgress)
            if (capture != null) applyCaptureTime(out, request.kind, request.input, capture)
            val done = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
                if (capture != null) {
                    put(MediaStore.MediaColumns.DATE_TAKEN, capture)
                    put(MediaStore.MediaColumns.DATE_MODIFIED, capture / 1000)
                }
            }
            resolver.update(out, done, null, null)
            return Published(
                uri = out,
                displayName = displayName,
                folderLabel = destination.label,
                sizeBytes = querySize(out),
            )
        } catch (t: Throwable) {
            runCatching { resolver.delete(out, null, null) }
            throw t
        }
    }

    private fun writeToTree(
        request: Request,
        destination: Destination.Tree,
        displayName: String,
        ext: String,
        onProgress: (EncodeProgress) -> Unit,
    ): Published {
        val docId = DocumentsContract.getTreeDocumentId(destination.treeUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(destination.treeUri, docId)
        val out = DocumentsContract.createDocument(
            resolver,
            parent,
            mimeFor(request, ext),
            displayName,
        ) ?: error("无法在所选文件夹创建文件")
        try {
            encodeInto(out, request, onProgress)
            val capture = request.captureTimeMs.takeIf { request.preserveCaptureTime }
            if (capture != null) applyCaptureTime(out, request.kind, request.input, capture)
            val size = querySize(out).takeIf { it > 0 } ?: runCatching {
                resolver.openFileDescriptor(out, "r")?.use { it.statSize } ?: 0L
            }.getOrDefault(0L)
            return Published(
                uri = out,
                displayName = displayName,
                folderLabel = destination.label,
                sizeBytes = size,
            )
        } catch (t: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, out) }
            throw t
        }
    }

    private fun encodeInto(
        output: Uri,
        request: Request,
        onProgress: (EncodeProgress) -> Unit,
    ) {
        resolver.openFileDescriptor(output, "w")?.use { pfd ->
            if (request.audioOnly) {
                engine.extractAudio(request.input, pfd) { onProgress(it) }
            } else {
                engine.compress(
                    kind = request.kind,
                    input = request.input,
                    outputPfd = pfd,
                    request = request.compression,
                ) { onProgress(it) }
            }
        } ?: error("无法打开输出文件")
    }

    private fun extensionFor(request: Request): String = when {
        request.audioOnly -> "m4a"
        request.kind == MediaKind.VIDEO -> "mp4"
        request.compression.imageCodec == OutputImageCodec.AVIF -> "avif"
        request.compression.imageCodec == OutputImageCodec.HEIC -> "heic"
        else -> "jpg"
    }

    private fun mimeFor(request: Request, ext: String): String = when {
        request.audioOnly -> "audio/mp4"
        request.kind == MediaKind.VIDEO -> "video/mp4"
        ext == "avif" -> MimeTypes.AVIF
        // image/heif maps to the .heif extension, and MediaStore then renames
        // the file to name_sc.heic.heif. image/heic matches what we wrote.
        ext == "heic" -> "image/heic"
        else -> "image/jpeg"
    }

    private fun applyCaptureTime(output: Uri, kind: MediaKind, input: Uri, captureMs: Long) {
        val path = queryPath(output)
        if (kind == MediaKind.VIDEO) {
            path?.let { runCatching { Mp4TimestampPatcher.patchFile(it, captureMs) } }
        } else {
            runCatching { CaptureTimestamp.copyExifDates(resolver, input, output) }
            runCatching { CaptureTimestamp.stampExif(resolver, output, captureMs) }
        }
        path?.let { File(it).setLastModified(captureMs) }
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

    fun querySize(uri: Uri): Long {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return 0
    }

    private fun outputName(original: String, ext: String): String {
        val stem = original.substringBeforeLast('.', original)
            .ifBlank { "snapconverter" }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return "${stem}_sc.$ext"
    }
}
