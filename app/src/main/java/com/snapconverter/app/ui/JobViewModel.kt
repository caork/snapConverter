package com.snapconverter.app.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snapconverter.app.media.NeedsUserConsentException
import com.snapconverter.app.media.OriginalReplacer
import com.snapconverter.app.media.ReplaceRequest
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
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ConvertStage { IDLE, LOADING, READY, RUNNING, DONE }

enum class SaveFolder {
    APP,
    CAMERA,
    ROOT,
    DOWNLOAD,
    CUSTOM,
    ;

    fun relativePath(kind: MediaKind): String = when (this) {
        APP -> if (kind == MediaKind.VIDEO) {
            "${Environment.DIRECTORY_MOVIES}/SnapConverter"
        } else {
            "${Environment.DIRECTORY_PICTURES}/SnapConverter"
        }
        CAMERA -> "${Environment.DIRECTORY_DCIM}/Camera"
        ROOT -> if (kind == MediaKind.VIDEO) {
            Environment.DIRECTORY_MOVIES
        } else {
            Environment.DIRECTORY_PICTURES
        }
        DOWNLOAD -> "${Environment.DIRECTORY_DOWNLOADS}/SnapConverter"
        CUSTOM -> ""
    }

    fun chipLabel(kind: MediaKind?): String = when (this) {
        APP -> "SnapConverter"
        CAMERA -> "相机"
        ROOT -> if (kind == MediaKind.IMAGE) "图片" else "影片"
        DOWNLOAD -> "下载"
        CUSTOM -> "自选"
    }

    fun pathLabel(kind: MediaKind?, customLabel: String?): String = when (this) {
        APP -> if (kind == MediaKind.IMAGE) "图片/SnapConverter" else "影片/SnapConverter"
        CAMERA -> "相机"
        ROOT -> if (kind == MediaKind.IMAGE) "图片" else "影片"
        DOWNLOAD -> "下载/SnapConverter"
        CUSTOM -> customLabel?.ifBlank { null } ?: "自选文件夹"
    }

    /** Relative path for audio-only (extracted M4A) outputs. */
    fun relativeAudioPath(): String = when (this) {
        APP -> Environment.DIRECTORY_MUSIC + "/SnapConverter"
        CAMERA, ROOT -> Environment.DIRECTORY_MUSIC
        DOWNLOAD -> Environment.DIRECTORY_DOWNLOADS + "/SnapConverter"
        CUSTOM -> ""
    }
}

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
    val targetSizeRatio: Float = 0.5f,
    val targetBitrateKbps: Int = 4000,
    val targetSsim: Float = 0.95f,
    val targetVmaf: Float = 90f,
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
    val replacedOriginal: Boolean = false,
    val replaceRunning: Boolean = false,
    val pendingConsent: IntentSender? = null,
    val saveFolder: SaveFolder = SaveFolder.APP,
    val customTreeUri: Uri? = null,
    val customFolderLabel: String? = null,
    val audioOnly: Boolean = false,
    val muteAudio: Boolean = false,
    val trimStartSec: Int = 0,
    /** -1 = to source end; otherwise inclusive upper bound in seconds. */
    val trimEndSec: Int = -1,
    /** Explicit image export size (Photoshop-style); null = use [resolution] preset. */
    val imageCustomWidth: Int? = null,
    val imageCustomHeight: Int? = null,
    val imageAspectLock: Boolean = true,
    val error: String? = null,
) {
    val savePathLabel: String get() = saveFolder.pathLabel(kind, customFolderLabel)

    val durationSec: Int
        get() = videoInfo?.durationUs?.let { (it / 1_000_000L).toInt() } ?: 0

    /** True when the user has actually narrowed the trim window. */
    val trimActive: Boolean
        get() = trimStartSec > 0 || (durationSec > 0 && trimEndSec in 1 until durationSec)
}

class JobViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = (application as SnapConverterApp).engine
    private val replacer = OriginalReplacer(application)
    private val prefs = application.getSharedPreferences("snapconverter", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(loadUi())
    val state: StateFlow<UiState> = _state

    private fun loadUi(): UiState {
        val folder = runCatching {
            SaveFolder.valueOf(prefs.getString(PREF_SAVE_FOLDER, SaveFolder.APP.name)!!)
        }.getOrDefault(SaveFolder.APP)
        val tree = prefs.getString(PREF_CUSTOM_TREE, null)?.let { Uri.parse(it) }
        val label = prefs.getString(PREF_CUSTOM_LABEL, null)
        return UiState(saveFolder = folder, customTreeUri = tree, customFolderLabel = label)
    }

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
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags)
        }.recoverCatching {
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
                    replacedOriginal = false,
                    replaceRunning = false,
                    pendingConsent = null,
                    audioOnly = false,
                    muteAudio = false,
                    trimStartSec = 0,
                    trimEndSec = -1,
                    imageCustomWidth = null,
                    imageCustomHeight = null,
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
                replacedOriginal = false,
                replaceRunning = false,
                pendingConsent = null,
                audioOnly = false,
                muteAudio = false,
                trimStartSec = 0,
                trimEndSec = -1,
                imageCustomWidth = null,
                imageCustomHeight = null,
            )
        }
    }

    fun setAudioOnly(v: Boolean) = _state.update {
        it.copy(audioOnly = v, muteAudio = if (v) false else it.muteAudio)
    }

    fun setMuteAudio(v: Boolean) = _state.update { it.copy(muteAudio = v) }

    fun setTrimStartSec(sec: Int) = _state.update { s ->
        val end = if (s.trimEndSec < 0) s.durationSec else s.trimEndSec
        s.copy(trimStartSec = sec.coerceIn(0, (end - 1).coerceAtLeast(0)))
    }

    fun setTrimEndSec(sec: Int) = _state.update { s ->
        val dur = s.durationSec
        if (dur <= 1) {
            s
        } else {
            s.copy(trimEndSec = sec.coerceIn((s.trimStartSec + 1).coerceAtLeast(1), dur))
        }
    }

    fun resetTrim() = _state.update { it.copy(trimStartSec = 0, trimEndSec = -1) }

    fun setMode(mode: CompressionMode) = _state.update {
        it.copy(mode = mode, bitrateMode = BitrateModeOption.AUTO)
    }
    fun setQuality(q: Int) = _state.update { it.copy(quality = q) }

    fun targetSizeBytes(state: UiState): Long =
        (state.fileSizeBytes * state.targetSizeRatio)
            .roundToLong()
            .coerceIn(64L * 1024, Long.MAX_VALUE)

    fun setTargetSizeRatio(r: Float) = _state.update {
        it.copy(targetSizeRatio = r.coerceIn(0.05f, 1.5f))
    }
    fun setTargetSsim(v: Float) = _state.update { it.copy(targetSsim = v.coerceIn(0.80f, 0.99f)) }
    fun setTargetVmaf(v: Float) = _state.update { it.copy(targetVmaf = v.coerceIn(60f, 98f)) }
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
    fun setResolution(r: OutputResolution) = _state.update {
        it.copy(resolution = r, imageCustomWidth = null, imageCustomHeight = null)
    }

    fun setImageAspectLock(v: Boolean) = _state.update { it.copy(imageAspectLock = v) }

    /** Custom width in dp-free pixels; aspect lock derives the height. */
    fun setImageCustomWidth(w: Int?) = _state.update { s ->
        applyCustomSize(s, w, s.imageCustomHeight, lockWidth = true)
    }

    fun setImageCustomHeight(h: Int?) = _state.update { s ->
        applyCustomSize(s, s.imageCustomWidth, h, lockWidth = false)
    }

    /** Quick scale percentage of the source size (100 / 75 / 50 / 25 …). */
    fun applyImageScale(percent: Int) = _state.update { s ->
        val src = s.imageInfo ?: return@update s
        val w = ((src.width * percent / 100) / 2 * 2).coerceIn(16, 8192)
        val h = ((src.height * percent / 100) / 2 * 2).coerceIn(16, 8192)
        s.copy(imageCustomWidth = w, imageCustomHeight = h)
    }

    private fun applyCustomSize(
        s: UiState,
        w: Int?,
        h: Int?,
        lockWidth: Boolean,
    ): UiState {
        val src = s.imageInfo ?: return s
        val cw = w?.coerceIn(16, 8192)
        val ch = h?.coerceIn(16, 8192)
        if (cw == null || ch == null) return s.copy(imageCustomWidth = cw, imageCustomHeight = ch)
        return if (s.imageAspectLock) {
            if (lockWidth) {
                val derived = ((cw.toLong() * src.height / src.width.coerceAtLeast(1)) / 2 * 2)
                    .toInt().coerceIn(16, 8192)
                s.copy(imageCustomWidth = cw, imageCustomHeight = derived)
            } else {
                val derived = ((ch.toLong() * src.width / src.height.coerceAtLeast(1)) / 2 * 2)
                    .toInt().coerceIn(16, 8192)
                s.copy(imageCustomWidth = derived, imageCustomHeight = ch)
            }
        } else {
            s.copy(imageCustomWidth = cw, imageCustomHeight = ch)
        }
    }
    fun setFps(f: OutputFps) = _state.update { it.copy(fps = f) }
    fun setPreserveCaptureTime(v: Boolean) = _state.update { it.copy(preserveCaptureTime = v) }
    fun setSaveFolder(folder: SaveFolder) {
        _state.update { it.copy(saveFolder = folder) }
        prefs.edit().putString(PREF_SAVE_FOLDER, folder.name).apply()
    }
    fun onCustomFolderPicked(uri: Uri?) {
        if (uri == null) {
            if (_state.value.customTreeUri == null) setSaveFolder(SaveFolder.APP)
            return
        }
        val resolver = getApplication<Application>().contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(uri, flags) }
        val label = queryTreeName(uri)
        _state.update {
            it.copy(saveFolder = SaveFolder.CUSTOM, customTreeUri = uri, customFolderLabel = label)
        }
        prefs.edit()
            .putString(PREF_SAVE_FOLDER, SaveFolder.CUSTOM.name)
            .putString(PREF_CUSTOM_TREE, uri.toString())
            .putString(PREF_CUSTOM_LABEL, label)
            .apply()
    }
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
                    targetSizeBytes(state),
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
        // Audio-only extraction is pure passthrough (no encoder involved), so
        // it stays available regardless of the Qualcomm V1 gate.
        if (kind == MediaKind.VIDEO && snapshot.audioOnly) {
            if (snapshot.videoInfo?.audioMime == null) {
                _state.update { it.copy(error = "此视频没有音轨，无法提取音频。") }
                return
            }
        }
        if (kind == MediaKind.VIDEO && snapshot.mode == CompressionMode.TARGET_VMAF && caps?.vmafAvailable != true) {
            _state.update { it.copy(error = "当前版本没有 VMAF 库，无法使用目标 VMAF。") }
            return
        }
        if (kind == MediaKind.IMAGE && snapshot.imageCodec == OutputImageCodec.AVIF) {
            if (Build.VERSION.SDK_INT < 31) {
                _state.update { it.copy(error = "AVIF 需要 Android 12 以上。") }
                return
            }
            if (caps?.hardwareAv1Encoder != true) {
                _state.update { it.copy(error = "此设备没有硬件 AV1 编码器，无法输出 AVIF。") }
                return
            }
        }
        val request = CompressionRequest(
            kind = kind,
            mode = snapshot.mode,
            appQuality = snapshot.quality,
            targetSizeBytes = targetSizeBytes(snapshot),
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
            targetSsim = snapshot.targetSsim.toDouble(),
            targetVmaf = snapshot.targetVmaf.toDouble(),
            trimStartUs = snapshot.trimStartSec * 1_000_000L,
            trimEndUs = if (snapshot.trimEndSec < 0) -1L else snapshot.trimEndSec * 1_000_000L,
            muteAudio = snapshot.muteAudio && !snapshot.audioOnly,
            imageCustomWidth = if (kind == MediaKind.IMAGE) snapshot.imageCustomWidth else null,
            imageCustomHeight = if (kind == MediaKind.IMAGE) snapshot.imageCustomHeight else null,
        )
        viewModelScope.launch {
            _state.update {
                it.copy(
                    stage = ConvertStage.RUNNING,
                    progress = EncodeProgress(0f),
                    error = null,
                    outputUri = null,
                    message = "正在启动硬件编码器…",
                    replacedOriginal = false,
                    replaceRunning = false,
                    pendingConsent = null,
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
                        qualityRunning = !snapshot.audioOnly,
                        qualityReport = null,
                    )
                }
                if (snapshot.audioOnly) {
                    // Passthrough audio has no re-encode; quality compare is
                    // meaningless for a bit-extract of the source track.
                    _state.update { it.copy(qualityRunning = false, qualityReport = null) }
                } else {
                    val durationUs = snapshot.videoInfo?.durationUs ?: 0L
                    val report = withContext(Dispatchers.Default) {
                        runCatching {
                            engine.compareQuality(kind, input, published.uri, durationUs)
                        }.getOrNull()
                    }
                    _state.update { it.copy(qualityRunning = false, qualityReport = report) }
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

    fun replaceOriginal() {
        val snapshot = _state.value
        val original = snapshot.input ?: return
        val encoded = snapshot.outputUri ?: return
        val kind = snapshot.kind ?: return
        if (snapshot.stage != ConvertStage.DONE || snapshot.replaceRunning) return
        viewModelScope.launch {
            _state.update { it.copy(replaceRunning = true, error = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    replacer.replace(
                        ReplaceRequest(
                            original = original,
                            encoded = encoded,
                            kind = kind,
                            preserveCaptureTime = snapshot.preserveCaptureTime,
                            captureTimeMs = snapshot.captureTimeMs,
                            imageCodec = snapshot.imageCodec,
                        ),
                    )
                }
            }
            result.onSuccess { published ->
                _state.update {
                    it.copy(
                        replaceRunning = false,
                        replacedOriginal = true,
                        outputUri = published.uri,
                        outputName = published.displayName,
                        outputFolder = published.folderLabel,
                        outputSizeBytes = published.sizeBytes,
                        message = "已替换原文件",
                    )
                }
            }.onFailure { t ->
                if (t is NeedsUserConsentException) {
                    _state.update {
                        it.copy(replaceRunning = false, pendingConsent = t.sender)
                    }
                } else {
                    _state.update {
                        it.copy(
                            replaceRunning = false,
                            error = t.message ?: "替换原文件失败",
                        )
                    }
                }
            }
        }
    }

    fun consumePendingConsent() = _state.update { it.copy(pendingConsent = null) }

    fun deleteExportedOutput() {
        val snapshot = _state.value
        val output = snapshot.outputUri ?: return
        if (snapshot.replacedOriginal) {
            _state.update { it.copy(message = "已替换原文件，无需删除导出文件") }
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    if (snapshot.saveFolder == SaveFolder.CUSTOM) {
                        DocumentsContract.deleteDocument(resolver, output)
                    } else {
                        resolver.delete(output, null, null)
                    }
                }
            }
            _state.update {
                it.copy(
                    outputUri = null,
                    outputName = null,
                    outputSizeBytes = 0,
                    qualityReport = null,
                    message = "已删除导出文件",
                )
            }
        }
    }

    fun onReplaceConsentResult(granted: Boolean) {
        if (granted) {
            replaceOriginal()
        } else {
            _state.update { it.copy(error = "未授权改写原文件") }
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
        val audioOnly = kind == MediaKind.VIDEO && snapshot.audioOnly
        val ext = when {
            audioOnly -> "m4a"
            kind == MediaKind.VIDEO -> "mp4"
            request.imageCodec == OutputImageCodec.AVIF -> "avif"
            request.imageCodec == OutputImageCodec.HEIC -> "heic"
            else -> "jpg"
        }
        val displayName = outputName(snapshot.displayName, ext)
        if (snapshot.saveFolder == SaveFolder.CUSTOM) {
            return encodeToTree(kind, input, request, snapshot, displayName, ext)
        }
        val relative = if (audioOnly) {
            snapshot.saveFolder.relativeAudioPath()
        } else {
            snapshot.saveFolder.relativePath(kind)
        }
        val capture = snapshot.captureTimeMs
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(
                MediaStore.MediaColumns.MIME_TYPE,
                when {
                    audioOnly -> "audio/mp4"
                    kind == MediaKind.VIDEO -> "video/mp4"
                    ext == "avif" -> MimeTypes.AVIF
                    ext == "heic" -> "image/heif"
                    else -> "image/jpeg"
                },
            )
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            if (snapshot.preserveCaptureTime && capture != null) {
                put(MediaStore.MediaColumns.DATE_TAKEN, capture)
                put(MediaStore.MediaColumns.DATE_ADDED, capture / 1000)
                put(MediaStore.MediaColumns.DATE_MODIFIED, capture / 1000)
            }
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = when {
            audioOnly -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            kind == MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val out = resolver.insert(collection, values) ?: error("无法写入媒体库")
        try {
            resolver.openFileDescriptor(out, "w")?.use { pfd ->
                if (audioOnly) {
                    engine.extractAudio(input, pfd) { update ->
                        _state.update {
                            it.copy(
                                progress = update,
                                message = update.message ?: "正在提取音频…",
                            )
                        }
                    }
                } else {
                    engine.compress(kind, input, pfd, request) { update ->
                        _state.update {
                            it.copy(
                                progress = update,
                                message = update.message ?: "正在硬件转码…",
                            )
                        }
                    }
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
                folderLabel = snapshot.saveFolder.pathLabel(kind, snapshot.customFolderLabel),
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

    private fun encodeToTree(
        kind: MediaKind,
        input: Uri,
        request: CompressionRequest,
        snapshot: UiState,
        displayName: String,
        ext: String,
    ): Published {
        val resolver = getApplication<Application>().contentResolver
        val tree = snapshot.customTreeUri ?: error("未选择保存文件夹")
        val docId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
        val audioOnly = kind == MediaKind.VIDEO && snapshot.audioOnly
        val mime = when {
            audioOnly -> "audio/mp4"
            kind == MediaKind.VIDEO -> "video/mp4"
            ext == "avif" -> MimeTypes.AVIF
            ext == "heic" -> "image/heif"
            else -> "image/jpeg"
        }
        val out = DocumentsContract.createDocument(resolver, parent, mime, displayName)
            ?: error("无法在所选文件夹创建文件")
        try {
            resolver.openFileDescriptor(out, "w")?.use { pfd ->
                if (audioOnly) {
                    engine.extractAudio(input, pfd) { update ->
                        _state.update {
                            it.copy(
                                progress = update,
                                message = update.message ?: "正在提取音频…",
                            )
                        }
                    }
                } else {
                    engine.compress(kind, input, pfd, request) { update ->
                        _state.update {
                            it.copy(
                                progress = update,
                                message = update.message ?: "正在硬件转码…",
                            )
                        }
                    }
                }
            } ?: error("无法打开输出文件")
            val capture = snapshot.captureTimeMs
            if (snapshot.preserveCaptureTime && capture != null) {
                applyCaptureTime(out, kind, input, capture)
            }
            val size = querySize(out).takeIf { it > 0 } ?: runCatching {
                resolver.openFileDescriptor(out, "r")?.statSize ?: 0L
            }.getOrDefault(0L)
            return Published(
                uri = out,
                displayName = displayName,
                folderLabel = snapshot.saveFolder.pathLabel(kind, snapshot.customFolderLabel),
                sizeBytes = size,
            )
        } catch (t: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, out) }
            throw t
        }
    }

    private fun queryTreeName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val doc = runCatching {
            val id = DocumentsContract.getTreeDocumentId(uri)
            DocumentsContract.buildDocumentUriUsingTree(uri, id)
        }.getOrDefault(uri)
        resolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0)
                    if (!name.isNullOrBlank()) return name
                }
            }
        return uri.lastPathSegment?.substringAfterLast(':')?.replace("%2F", "/") ?: "自选文件夹"
    }

    companion object {
        private const val PREF_SAVE_FOLDER = "save_folder"
        private const val PREF_CUSTOM_TREE = "custom_tree"
        private const val PREF_CUSTOM_LABEL = "custom_label"

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
