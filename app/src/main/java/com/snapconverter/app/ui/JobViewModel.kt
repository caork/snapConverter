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
import com.snapconverter.engine.codec.MimeTypes
import com.snapconverter.engine.policy.BitrateEstimator
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
import com.snapconverter.engine.policy.QualityStrategy
import com.snapconverter.engine.policy.VideoProfileOption
import com.snapconverter.engine.policy.VideoSourceInfo
import com.snapconverter.engine.progress.EncodeProgress
import com.snapconverter.engine.quality.QualityReport
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
    val maxBitrateKbps: Int = 8000,
    val iFrameIntervalSec: Int? = null,
    val maxBFrames: Int? = null,
    val profile: VideoProfileOption = VideoProfileOption.AUTO,
    val complexity: ComplexityOption = ComplexityOption.AUTO,
    val qpIMin: Int? = null,
    val qpIMax: Int? = null,
    val qpPMin: Int? = null,
    val qpPMax: Int? = null,
    val progress: EncodeProgress = EncodeProgress(0f),
    val message: String? = null,
    val outputUri: Uri? = null,
    val outputName: String? = null,
    val outputFolder: String? = null,
    val outputSizeBytes: Long = 0,
    val qualityReport: QualityReport? = null,
    val qualityRunning: Boolean = false,
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
                    qualityReport = null,
                    qualityRunning = false,
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
                qualityReport = null,
                qualityRunning = false,
            )
        }
    }

    fun setMode(mode: CompressionMode) = _state.update {
        it.copy(mode = mode, bitrateMode = BitrateModeOption.AUTO)
    }
    fun setQuality(q: Int) = _state.update { it.copy(quality = q) }
    fun setTargetSizeMb(v: Int) = _state.update { it.copy(targetSizeMb = v) }
    fun setTargetBitrateKbps(v: Int) = _state.update {
        val max = maxOf(it.maxBitrateKbps, (v * 1.5).toInt())
        it.copy(targetBitrateKbps = v, maxBitrateKbps = max.coerceAtMost(80_000))
    }
    fun setMaxBitrateKbps(v: Int) = _state.update {
        it.copy(maxBitrateKbps = v.coerceAtLeast(it.targetBitrateKbps))
    }
    fun setVideoCodec(c: OutputVideoCodec) = _state.update {
        val profile = if (profileAllowed(c, it.profile)) it.profile else VideoProfileOption.AUTO
        it.copy(
            videoCodec = c,
            profile = profile,
            maxBFrames = if (profile == VideoProfileOption.BASELINE) 0 else it.maxBFrames,
        )
    }
    fun setImageCodec(c: OutputImageCodec) = _state.update { it.copy(imageCodec = c) }
    fun setResolution(r: OutputResolution) = _state.update { it.copy(resolution = r) }
    fun setFps(f: OutputFps) = _state.update { it.copy(fps = f) }
    fun setPreserveCaptureTime(v: Boolean) = _state.update { it.copy(preserveCaptureTime = v) }
    fun setAdvancedOpen(v: Boolean) = _state.update { it.copy(advancedOpen = v) }
    fun setBitrateMode(v: BitrateModeOption) = _state.update {
        val enteringRateControl =
            it.bitrateMode == BitrateModeOption.AUTO &&
                (v == BitrateModeOption.VBR || v == BitrateModeOption.CBR)
        val seeded = if (enteringRateControl) estimatedBitrateKbps(it) else it.targetBitrateKbps
        it.copy(
            bitrateMode = v,
            targetBitrateKbps = seeded,
            maxBitrateKbps = if (v == BitrateModeOption.VBR) {
                maxOf(it.maxBitrateKbps, (seeded * 2).coerceAtMost(40_000))
            } else {
                it.maxBitrateKbps
            },
        )
    }
    fun setIFrameIntervalSec(v: Int?) = _state.update { it.copy(iFrameIntervalSec = v) }
    fun setMaxBFrames(v: Int?) = _state.update { it.copy(maxBFrames = v) }
    fun setProfile(v: VideoProfileOption) = _state.update {
        it.copy(
            profile = v,
            maxBFrames = if (v == VideoProfileOption.BASELINE) 0 else it.maxBFrames,
        )
    }
    fun setComplexity(v: ComplexityOption) = _state.update { it.copy(complexity = v) }
    fun setQpCustom(enabled: Boolean) = _state.update {
        if (enabled) {
            it.copy(qpIMin = 16, qpIMax = 36, qpPMin = 18, qpPMax = 40)
        } else {
            it.copy(qpIMin = null, qpIMax = null, qpPMin = null, qpPMax = null)
        }
    }
    fun setQpIMin(v: Int) = _state.update { it.copy(qpIMin = v, qpIMax = maxOf(v, it.qpIMax ?: v)) }
    fun setQpIMax(v: Int) = _state.update { it.copy(qpIMax = v, qpIMin = minOf(v, it.qpIMin ?: v)) }
    fun setQpPMin(v: Int) = _state.update { it.copy(qpPMin = v, qpPMax = maxOf(v, it.qpPMax ?: v)) }
    fun setQpPMax(v: Int) = _state.update { it.copy(qpPMax = v, qpPMin = minOf(v, it.qpPMin ?: v)) }
    fun clearError() = _state.update { it.copy(error = null) }

    private fun profileAllowed(codec: OutputVideoCodec, profile: VideoProfileOption): Boolean =
        when (codec) {
            OutputVideoCodec.AVC -> profile in setOf(
                VideoProfileOption.AUTO,
                VideoProfileOption.BASELINE,
                VideoProfileOption.MAIN,
                VideoProfileOption.HIGH,
            )
            OutputVideoCodec.HEVC, OutputVideoCodec.AV1 -> profile in setOf(
                VideoProfileOption.AUTO,
                VideoProfileOption.MAIN,
                VideoProfileOption.MAIN10,
            )
        }

    private fun estimatedBitrateKbps(state: UiState): Int {
        val video = state.videoInfo ?: return state.targetBitrateKbps
        return when (state.mode) {
            CompressionMode.TARGET_BITRATE -> state.targetBitrateKbps
            CompressionMode.TARGET_SIZE -> {
                val durationSec = (video.durationUs / 1_000_000.0).coerceAtLeast(0.1)
                val audio = video.audioBitrateBps.takeIf { it > 0 }
                    ?: BitrateEstimator.DEFAULT_AUDIO_BITRATE_BPS
                BitrateEstimator.videoBitrateForTargetSize(
                    state.targetSizeMb * 1024L * 1024L,
                    durationSec,
                    audio,
                ) / 1000
            }
            else -> {
                val mime = when (state.videoCodec) {
                    OutputVideoCodec.AVC -> MimeTypes.AVC
                    else -> MimeTypes.HEVC
                }
                val longCap = when (state.resolution) {
                    OutputResolution.ORIGINAL -> Int.MAX_VALUE
                    OutputResolution.UHD_2160 -> 3840
                    OutputResolution.QHD_1440 -> 2560
                    OutputResolution.FHD_1080 -> 1920
                    OutputResolution.HD_720 -> 1280
                }
                var w = video.displayWidth
                var h = video.displayHeight
                val longEdge = maxOf(w, h)
                if (longEdge > longCap) {
                    val scale = longCap.toDouble() / longEdge
                    w = (w * scale).toInt()
                    h = (h * scale).toInt()
                }
                val bitrate1080 = QualityStrategy.interpolate(state.quality).bitrate1080pHevcBps
                QualityStrategy.scaleBitrateForPixels(bitrate1080, w, h, mime) / 1000
            }
        }.coerceIn(200, 40_000)
    }

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
            maxBitrateBps = snapshot.maxBitrateKbps * 1000,
            iFrameIntervalSec = snapshot.iFrameIntervalSec,
            maxBFrames = snapshot.maxBFrames,
            profile = snapshot.profile,
            complexity = snapshot.complexity,
            qpIMin = snapshot.qpIMin,
            qpIMax = snapshot.qpIMax,
            qpPMin = snapshot.qpPMin,
            qpPMax = snapshot.qpPMax,
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
                        qualityRunning = true,
                        qualityReport = null,
                    )
                }
                val durationUs = snapshot.videoInfo?.durationUs ?: 0L
                val report = withContext(Dispatchers.Default) {
                    runCatching {
                        engine.compareQuality(kind, input, published.uri, durationUs)
                    }.getOrNull()
                }
                _state.update { it.copy(qualityRunning = false, qualityReport = report) }
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
