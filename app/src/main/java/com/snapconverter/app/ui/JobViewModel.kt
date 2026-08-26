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
import com.snapconverter.engine.policy.CompressionMode
import com.snapconverter.engine.policy.CompressionRequest
import com.snapconverter.engine.policy.ImageSourceInfo
import com.snapconverter.engine.policy.MediaKind
import com.snapconverter.engine.policy.OutputFps
import com.snapconverter.engine.policy.OutputImageCodec
import com.snapconverter.engine.policy.OutputResolution
import com.snapconverter.engine.policy.OutputVideoCodec
import com.snapconverter.engine.policy.VideoSourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val capabilities: DeviceCapabilityReport? = null,
    val kind: MediaKind? = null,
    val input: Uri? = null,
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
    val running: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
    val outputUri: Uri? = null,
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

    fun onPicked(uri: Uri, kind: MediaKind) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    input = uri,
                    kind = kind,
                    error = null,
                    outputUri = null,
                    message = "Inspecting…",
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    when (kind) {
                        MediaKind.VIDEO -> engine.inspectVideo(uri)
                        MediaKind.IMAGE -> engine.inspectImage(uri)
                    }
                }
            }.onSuccess { info ->
                _state.update {
                    when (info) {
                        is VideoSourceInfo -> it.copy(videoInfo = info, imageInfo = null, message = null)
                        is ImageSourceInfo -> it.copy(imageInfo = info, videoInfo = null, message = null)
                        else -> it.copy(message = null)
                    }
                }
            }.onFailure { t ->
                _state.update { it.copy(error = t.message ?: t.toString(), message = null) }
            }
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
    fun clearError() = _state.update { it.copy(error = null) }

    fun start() {
        val snapshot = _state.value
        val input = snapshot.input ?: return
        val kind = snapshot.kind ?: return
        val caps = snapshot.capabilities
        if (caps?.v1Supported != true) {
            _state.update { it.copy(error = caps?.summaryLine ?: "Qualcomm hardware encoder required.") }
            return
        }
        if (kind == MediaKind.IMAGE && snapshot.imageCodec == OutputImageCodec.JPEG && caps.hardwareJpegEncoder.not()) {
            _state.update { it.copy(error = "No public hardware JPEG encoder. Choose HEIC.") }
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
        )
        viewModelScope.launch {
            _state.update { it.copy(running = true, progress = 0f, error = null, outputUri = null, message = "Encoding on hardware…") }
            val result = withContext(Dispatchers.IO) {
                runCatching { encodeToMediaStore(kind, input, request) }
            }
            result.onSuccess { uri ->
                _state.update {
                    it.copy(running = false, progress = 1f, outputUri = uri, message = "Saved to Pictures/Movies/SnapConverter")
                }
            }.onFailure { t ->
                _state.update {
                    it.copy(running = false, error = t.message ?: t.toString(), message = null)
                }
            }
        }
    }

    private fun encodeToMediaStore(
        kind: MediaKind,
        input: Uri,
        request: CompressionRequest,
    ): Uri {
        val resolver = getApplication<Application>().contentResolver
        val name = "snapconverter_${System.currentTimeMillis()}"
        val values = ContentValues()
        val collection: Uri
        if (kind == MediaKind.VIDEO) {
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.mp4")
            values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            values.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/SnapConverter",
            )
            collection = if (Build.VERSION.SDK_INT >= 29) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            val heic = request.imageCodec == OutputImageCodec.HEIC
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, if (heic) "$name.heic" else "$name.jpg")
            values.put(MediaStore.MediaColumns.MIME_TYPE, if (heic) "image/heif" else "image/jpeg")
            values.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/SnapConverter",
            )
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val out = resolver.insert(collection, values) ?: error("MediaStore insert failed")
        try {
            resolver.openFileDescriptor(out, "w")?.use { pfd ->
                engine.compress(kind, input, pfd, request) { ratio ->
                    _state.update { it.copy(progress = ratio) }
                }
            } ?: error("Unable to open output")
            if (Build.VERSION.SDK_INT >= 29) {
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(out, done, null, null)
            }
            return out
        } catch (t: Throwable) {
            resolver.delete(out, null, null)
            throw t
        }
    }
}
