package com.snapconverter.app.ui

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snapconverter.app.SnapConverterApp
import com.snapconverter.engine.device.DeviceCapabilityReport
import com.snapconverter.engine.media.CaptureTimestamp
import com.snapconverter.engine.media.Mp4TimestampPatcher
import com.snapconverter.engine.policy.BitrateModeOption
import com.snapconverter.engine.policy.ComplexityOption
import com.snapconverter.engine.policy.CompressionMode
import com.snapconverter.engine.policy.CompressionRequest
import com.snapconverter.engine.policy.ImageSourceInfo
import com.snapconverter.engine.policy.MediaKind
import com.snapconverter.engine.policy.OutputFps
import com.snapconverter.engine.policy.OutputImageCodec
import com.snapconverter.engine.policy.OutputResolution
import com.snapconverter.engine.policy.OutputVideoCodec
import com.snapconverter.engine.policy.VideoProfileOption
import com.snapconverter.engine.policy.VideoSourceInfo
import com.snapconverter.engine.progress.EncodeProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ConvertStage { IDLE, LOADING, READY, RUNNING, DONE }

data class UiState(
    val stage: ConvertStage = ConvertStage.IDLE,
    val capabilities: DeviceCapabilityReport? = null,
    val kind: MediaKind? = null,
    val input: Uri? = null,
    val displayName: String = "",
    val fileSizeBytes: Long = 0,
    val captureTimeMs: Long? = null,
    val videoInfo: VideoSourceInfo? = null,
    val imageInfo: ImageSourceInfo? = null,
    val mode: CompressionMode = CompressionMode.QUALITY,
    val quality: Int = 70,
    val targetSizeMb: Int = 200,
    val targetBitrateKbps: Int = 4000,
    val videoCodec: OutputVideoCodec = OutputVideoCodec.HEVC,
    val imageCodec: OutputImageCodec = OutputImageCodec.HEIC,
    val resolution: OutputResolution = OutputResolution.ORIGINAL,
    val fps: OutputFps = OutputFps.ORIGINAL,
    val preserveCaptureTime: Boolean = true,
    val advancedOpen: Boolean = false,
    val bitrateMode: BitrateModeOption = BitrateModeOption.AUTO,
    val iFrameIntervalSec: Int? = null,
    val maxBFrames: Int? = null,
    val profile: VideoProfileOption = VideoProfileOption.AUTO,
    val complexity: ComplexityOption = ComplexityOption.AUTO,
    val qpMin: Int? = null,
    val qpMax: Int? = null,
    val progress: EncodeProgress = EncodeProgress(0f),
    val message: String? = null,
    val outputUri: Uri? = null,
    val outputName: String? = null,
    val outputFolder: String? = null,
    val outputSizeBytes: Long = 0,
    val error: String? = null,
)

class JobViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = (application as SnapConverterApp).engine
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val report = runCatching { engine.probeDevice() }.getOrElse { t ->
                _state.update { it.copy(error = t.message) }
                return@launch
            }
            _state.update { it.copy(capabilities = report) }
        }
    }

    fun onPicked(uri: Uri, kindHint: MediaKind? = null, autostart: Boolean = false) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    stage = ConvertStage.LOADING,
                    input = uri,
                    error = null,
                    outputUri = null,
                    outputName = null,
                    message = null,
                    progress = EncodeProgress(0f),
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val identity = CaptureTimestamp.read(getApplication(), uri)
                    val kind = kindHint ?: detectKind(identity.mimeType, identity.displayName)
                    val info = when (kind) {
                        MediaKind.VIDEO -> engine.inspectVideo(uri)
                        MediaKind.IMAGE -> engine.inspectImage(uri)
                    }
                    Triple(kind, identity, info)
                }
            }.onSuccess { (kind, identity, info) ->
                _state.update {
                    val video = info as? VideoSourceInfo
                    val image = info as? ImageSourceInfo
                    it.copy(
                        stage = ConvertStage.READY,
                        kind = kind,
                        displayName = identity.displayName.ifBlank {
                            video?.displayName ?: image?.displayName ?: "media"
                        },
                        fileSizeBytes = identity.fileSizeBytes.takeIf { s -> s > 0 }
                            ?: video?.fileSizeBytes ?: image?.fileSizeBytes ?: 0,
                        captureTimeMs = identity.captureTimeMs ?: video?.captureTimeMs ?: image?.captureTimeMs,
                        videoInfo = video,
                        imageInfo = image,
                        imageCodec = if (it.capabilities?.hardwareJpegEncoder == true) {
                            it.imageCodec
                        } else {
                            OutputImageCodec.HEIC
                        },
                    )
                }
                if (autostart) start()
            }.onFailure { t ->
                _state.update {
                    it.copy(stage = ConvertStage.IDLE, error = t.message ?: t.toString())
                }
            }
        }
    }

    fun reset() {
        _state.update {
            it.copy(
                stage = ConvertStage.IDLE,
                kind = null,
                input = null,
                videoInfo = null,
                imageInfo = null,
                outputUri = null,
                outputName = null,
                error = null,
                message = null,
                progress = EncodeProgress(0f),
            )
        }
    }

    fun setMode(mode: CompressionMode) = _state.update { it.copy(mode = mode) }
    fun setQuality(q: Int) = _state.update { it.copy(quality = q) }
    fun setTargetSizeMb(v: Int) = _state.update { it.copy(targetSizeMb = v) }
    fun setTargetBitrateKbps(v: Int) = _state.update { it.copy(targetBitrateKbps = v) }
    fun setVideoCodec(c: OutputVideoCodec) = _state.update { it.copy(videoCodec = c) }
    fun setImageCodec(c: OutputImageCodec) = _state.update { it.copy(imageCodec = c) }
    fun setResolution(r: OutputResolution) = _state.update { it.copy(resolution = r) }
    fun setFps(f: OutputFps) = _state.update { it.copy(fps = f) }
    fun setPreserveCaptureTime(v: Boolean) = _state.update { it.copy(preserveCaptureTime = v) }
    fun setAdvancedOpen(v: Boolean) = _state.update { it.copy(advancedOpen = v) }
    fun setBitrateMode(v: BitrateModeOption) = _state.update { it.copy(bitrateMode = v) }
    fun setIFrameIntervalSec(v: Int?) = _state.update { it.copy(iFrameIntervalSec = v) }
    fun setMaxBFrames(v: Int?) = _state.update { it.copy(maxBFrames = v) }
    fun setProfile(v: VideoProfileOption) = _state.update { it.copy(profile = v) }
    fun setComplexity(v: ComplexityOption) = _state.update { it.copy(complexity = v) }
    fun setQpRange(min: Int?, max: Int?) = _state.update { it.copy(qpMin = min, qpMax = max) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun start() {
        val snapshot = _state.value
        val input = snapshot.input ?: return
        val kind = snapshot.kind ?: return
        val caps = snapshot.capabilities
        if (caps?.v1Supported != true) {
            _state.update { it.copy(error = "这台设备没有可用的高通硬件编码器。") }
            return
        }
        if (kind == MediaKind.IMAGE && snapshot.imageCodec == OutputImageCodec.JPEG && caps.hardwareJpegEncoder.not()) {
            _state.update { it.copy(error = "没有公开的 JPEG 硬件编码器，请选择 HEIC。") }
            return
        }
        val request = CompressionRequest(
            kind = kind,
            mode = snapshot.mode,
            appQuality = snapshot.quality,
            targetSizeBytes = snapshot.targetSizeMb * 1024L * 1024L,
            targetBitrateBps = snapshot.targetBitrateKbps * 1000,
            videoCodec = snapshot.videoCodec,
            imageCodec = snapshot.imageCodec,
            resolution = snapshot.resolution,
            fps = snapshot.fps,
            bitrateMode = snapshot.bitrateMode,
            iFrameIntervalSec = snapshot.iFrameIntervalSec,
            maxBFrames = snapshot.maxBFrames,
            profile = snapshot.profile,
            complexity = snapshot.complexity,
            qpMin = snapshot.qpMin,
            qpMax = snapshot.qpMax,
        )
        viewModelScope.launch {
            _state.update {
                it.copy(
                    stage = ConvertStage.RUNNING,
                    progress = EncodeProgress(0f),
                    error = null,
                    outputUri = null,
                    message = "正在启动硬件编码器…",
                )
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { encodeToMediaStore(kind, input, request, snapshot) }
            }
            result.onSuccess { published ->
                _state.update {
                    it.copy(
                        stage = ConvertStage.DONE,
                        progress = EncodeProgress(1f, elapsedMs = it.progress.elapsedMs),
                        outputUri = published.uri,
                        outputName = published.displayName,
                        outputFolder = published.folderLabel,
                        outputSizeBytes = published.sizeBytes,
                        message = "已保存",
                    )
                }
            }.onFailure { t ->
                _state.update {
                    it.copy(
                        stage = ConvertStage.READY,
                        error = t.message ?: t.toString(),
                        message = null,
                    )
                }
            }
        }
    }

    private data class Published(
        val uri: Uri,
        val displayName: String,
        val folderLabel: String,
        val sizeBytes: Long,
    )

    private fun encodeToMediaStore(
        kind: MediaKind,
        input: Uri,
        request: CompressionRequest,
        snapshot: UiState,
    ): Published {
        val resolver = getApplication<Application>().contentResolver
        val ext = if (kind == MediaKind.VIDEO) {
            "mp4"
        } else if (request.imageCodec == OutputImageCodec.HEIC) {
            "heic"
        } else {
            "jpg"
        }
        val displayName = outputName(snapshot.displayName, ext)
        val relative = if (kind == MediaKind.VIDEO) {
            Environment.DIRECTORY_MOVIES + "/SnapConverter"
        } else {
            Environment.DIRECTORY_PICTURES + "/SnapConverter"
        }
        val capture = snapshot.captureTimeMs
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(
                MediaStore.MediaColumns.MIME_TYPE,
                if (kind == MediaKind.VIDEO) "video/mp4"
                else if (ext == "heic") "image/heif" else "image/jpeg",
            )
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            if (snapshot.preserveCaptureTime && capture != null) {
                put(MediaStore.MediaColumns.DATE_TAKEN, capture)
                put(MediaStore.MediaColumns.DATE_ADDED, capture / 1000)
                put(MediaStore.MediaColumns.DATE_MODIFIED, capture / 1000)
            }
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (kind == MediaKind.VIDEO) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val out = resolver.insert(collection, values) ?: error("无法写入媒体库")
        try {
            resolver.openFileDescriptor(out, "w")?.use { pfd ->
                engine.compress(kind, input, pfd, request) { update ->
                    _state.update { it.copy(progress = update, message = "正在硬件转码…") }
                }
            } ?: error("无法打开输出文件")

            if (snapshot.preserveCaptureTime && capture != null) {
                applyCaptureTime(out, kind, input, capture)
            }

            val done = ContentValues().apply {
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.IS_PENDING, 0)
                if (snapshot.preserveCaptureTime && capture != null) {
                    put(MediaStore.MediaColumns.DATE_TAKEN, capture)
                    put(MediaStore.MediaColumns.DATE_MODIFIED, capture / 1000)
                }
            }
            resolver.update(out, done, null, null)
            val size = querySize(out)
            return Published(
                uri = out,
                displayName = displayName,
                folderLabel = relative.replace("Movies", "影片").replace("Pictures", "图片"),
                sizeBytes = size,
            )
        } catch (t: Throwable) {
            resolver.delete(out, null, null)
            throw t
        }
    }

    private fun applyCaptureTime(output: Uri, kind: MediaKind, input: Uri, captureMs: Long) {
        val resolver = getApplication<Application>().contentResolver
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
        val resolver = getApplication<Application>().contentResolver
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

    private fun querySize(uri: Uri): Long {
        val resolver = getApplication<Application>().contentResolver
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

    companion object {
        fun detectKind(mime: String, name: String): MediaKind {
            val m = mime.lowercase()
            val n = name.lowercase()
            if (m.startsWith("image/") || n.matches(Regex(".*\\.(jpe?g|png|webp|heic|heif|avif|gif|bmp)$"))) {
                return MediaKind.IMAGE
            }
            return MediaKind.VIDEO
        }
    }
}
