package com.snapconverter.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snapconverter.app.SnapConverterApp
import com.snapconverter.app.media.MediaOutputWriter
import com.snapconverter.app.scan.MediaLibraryScanner
import com.snapconverter.app.scan.ScanItem
import com.snapconverter.app.scan.ScanReport
import com.snapconverter.engine.device.DeviceCapabilityReport
import com.snapconverter.engine.policy.AuditSensitivity
import com.snapconverter.engine.policy.AuditVerdict
import com.snapconverter.engine.policy.CompressionMode
import com.snapconverter.engine.policy.CompressionRequest
import com.snapconverter.engine.policy.MediaAudit
import com.snapconverter.engine.policy.MediaKind
import com.snapconverter.engine.policy.OutputImageCodec
import com.snapconverter.engine.policy.OutputResolution
import com.snapconverter.engine.policy.OutputVideoCodec
import com.snapconverter.engine.policy.longEdge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScanStage { IDLE, SCANNING, RESULT, RUNNING, FINISHED }

enum class BatchJobState { PENDING, RUNNING, DONE, FAILED }

data class BatchJob(
    val item: ScanItem,
    val state: BatchJobState = BatchJobState.PENDING,
    val ratio: Float = 0f,
    val outputBytes: Long = 0L,
    val outputName: String? = null,
    val error: String? = null,
)

/**
 * Settings shared by every file in a batch. Deliberately smaller than the
 * single-file screen: the point of a batch is that one decision applies to
 * hundreds of files, so per-file parameters (trim, custom pixel size, QP
 * windows) are not offered here.
 */
data class BatchSettings(
    val videoCodec: OutputVideoCodec = OutputVideoCodec.HEVC,
    val imageCodec: OutputImageCodec = OutputImageCodec.HEIC,
    /** ORIGINAL keeps the source geometry — the default the user asked for. */
    val resolution: OutputResolution = OutputResolution.ORIGINAL,
    val quality: Int = 70,
    val preserveCaptureTime: Boolean = true,
    val saveFolder: SaveFolder = SaveFolder.APP,
)

data class ScanUiState(
    val stage: ScanStage = ScanStage.IDLE,
    val capabilities: DeviceCapabilityReport? = null,
    val sensitivity: AuditSensitivity = AuditSensitivity.STANDARD,
    val settings: BatchSettings = BatchSettings(),
    val scannedRows: Int = 0,
    val foundSoFar: Int = 0,
    val report: ScanReport? = null,
    val selected: Set<Long> = emptySet(),
    val jobs: List<BatchJob> = emptyList(),
    val error: String? = null,
) {
    val items: List<ScanItem> get() = report?.items.orEmpty()

    val selectedItems: List<ScanItem> get() = items.filter { it.id in selected }

    /** Sum of what the audit thinks the selected files can give back. */
    val selectedSavingBytes: Long
        get() = selectedItems.sumOf { estimatedSaving(it) }

    val selectedSizeBytes: Long get() = selectedItems.sumOf { it.sizeBytes }

    val doneCount: Int get() = jobs.count { it.state == BatchJobState.DONE }
    val failedCount: Int get() = jobs.count { it.state == BatchJobState.FAILED }

    /** Bytes actually saved by the finished jobs — measured, not estimated. */
    val actualSavingBytes: Long
        get() = jobs.filter { it.state == BatchJobState.DONE }
            .sumOf { (it.item.sizeBytes - it.outputBytes).coerceAtLeast(0L) }

    /**
     * Re-estimates with the *batch's* settings instead of the audit defaults,
     * so the number on the button matches what the user is about to run.
     */
    fun estimatedSaving(item: ScanItem): Long {
        val estimate = MediaAudit.estimateOutputBytes(
            fact = item.fact,
            quality = settings.quality,
            maxLongEdge = settings.resolution.longEdge ?: Int.MAX_VALUE,
        )
        return (item.sizeBytes - estimate).coerceAtLeast(0L)
    }
}

/**
 * Library scan and batch conversion. Separate from [JobViewModel] because the
 * two have almost nothing in common: this one owns a list and a queue, that
 * one owns a single file with every parameter exposed. They share the engine
 * and [MediaOutputWriter], which is where the real work lives.
 */
class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = (application as SnapConverterApp).engine
    private val scanner = MediaLibraryScanner(application)
    private val writer = MediaOutputWriter(application, engine)

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state

    private var scanJob: Job? = null
    private var batchJob: Job? = null

    init {
        // The batch sheet only offers formats this silicon can actually encode.
        viewModelScope.launch {
            val caps = withContext(Dispatchers.IO) { runCatching { engine.probeDevice() } }
            caps.getOrNull()?.let { report -> _state.update { it.copy(capabilities = report) } }
        }
    }

    fun scan() {
        scanJob?.cancel()
        val sensitivity = _state.value.sensitivity
        val quality = _state.value.settings.quality
        _state.update {
            it.copy(
                stage = ScanStage.SCANNING,
                scannedRows = 0,
                foundSoFar = 0,
                report = null,
                selected = emptySet(),
                jobs = emptyList(),
                error = null,
            )
        }
        scanJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    scanner.scan(sensitivity, quality) { scanned, found ->
                        _state.update { it.copy(scannedRows = scanned, foundSoFar = found) }
                    }
                }
            }
            result.onSuccess { report ->
                _state.update {
                    it.copy(
                        stage = ScanStage.RESULT,
                        report = report,
                        scannedRows = report.scanned,
                        foundSoFar = report.items.size,
                        // Pre-select the clear wins; "high" is left to the user.
                        selected = report.items
                            .filter { item -> item.audit.verdict == AuditVerdict.VERY_HIGH }
                            .map { item -> item.id }
                            .toSet(),
                    )
                }
            }.onFailure { t ->
                _state.update {
                    it.copy(stage = ScanStage.IDLE, error = t.message ?: "扫描失败")
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _state.update { it.copy(stage = if (it.report != null) ScanStage.RESULT else ScanStage.IDLE) }
    }

    fun setSensitivity(value: AuditSensitivity) {
        if (_state.value.sensitivity == value) return
        _state.update { it.copy(sensitivity = value) }
        if (_state.value.report != null || _state.value.stage == ScanStage.SCANNING) scan()
    }

    fun setSettings(settings: BatchSettings) = _state.update { it.copy(settings = settings) }

    fun toggle(id: Long) = _state.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun selectAll() = _state.update { it.copy(selected = it.items.map { item -> item.id }.toSet()) }

    fun selectNone() = _state.update { it.copy(selected = emptySet()) }

    fun selectOnly(verdict: AuditVerdict) = _state.update { state ->
        state.copy(
            selected = state.items.filter { it.audit.verdict == verdict }.map { it.id }.toSet(),
        )
    }

    fun selectKind(kind: MediaKind) = _state.update { state ->
        state.copy(selected = state.items.filter { it.kind == kind }.map { it.id }.toSet())
    }

    /** Runs the selected files one after another. Sequential on purpose: the
     *  hardware encoder is a single shared block, so two jobs at once would
     *  serialise inside the codec anyway while doubling peak memory. */
    fun startBatch() {
        if (_state.value.stage == ScanStage.RUNNING) return
        val targets = _state.value.selectedItems
        if (targets.isEmpty()) return
        val settings = _state.value.settings
        _state.update {
            it.copy(
                stage = ScanStage.RUNNING,
                error = null,
                jobs = targets.map { item -> BatchJob(item) },
            )
        }
        batchJob = viewModelScope.launch {
            targets.forEachIndexed { index, item ->
                updateJob(index) { it.copy(state = BatchJobState.RUNNING, ratio = 0f) }
                val outcome = runCatching {
                    withContext(Dispatchers.IO) { convert(item, settings, index) }
                }
                outcome.onSuccess { published ->
                    updateJob(index) {
                        it.copy(
                            state = BatchJobState.DONE,
                            ratio = 1f,
                            outputBytes = published.sizeBytes,
                            outputName = published.displayName,
                        )
                    }
                }.onFailure { t ->
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    updateJob(index) {
                        it.copy(
                            state = BatchJobState.FAILED,
                            error = t.message ?: t.javaClass.simpleName,
                        )
                    }
                }
            }
            _state.update { it.copy(stage = ScanStage.FINISHED) }
        }
    }

    fun stopBatch() {
        batchJob?.cancel()
        _state.update { it.copy(stage = ScanStage.FINISHED) }
    }

    /** Back to the list, keeping the scan so the user can run a second pass. */
    fun backToResult() = _state.update {
        it.copy(
            stage = if (it.report != null) ScanStage.RESULT else ScanStage.IDLE,
            selected = it.selected - it.jobs
                .filter { job -> job.state == BatchJobState.DONE }
                .map { job -> job.item.id }
                .toSet(),
        )
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun convert(
        item: ScanItem,
        settings: BatchSettings,
        index: Int,
    ): MediaOutputWriter.Published {
        val request = CompressionRequest(
            kind = item.kind,
            mode = CompressionMode.QUALITY,
            appQuality = settings.quality,
            videoCodec = settings.videoCodec,
            imageCodec = settings.imageCodec,
            resolution = settings.resolution,
            // "尺寸 原始" in a batch means the pixels survive, not "as large as
            // the quality tier allows".
            keepOriginalPixels = settings.resolution == OutputResolution.ORIGINAL,
        )
        return writer.write(
            MediaOutputWriter.Request(
                kind = item.kind,
                input = item.uri,
                sourceName = item.name,
                destination = MediaOutputWriter.Destination.Library(
                    relativePath = settings.saveFolder.relativePath(item.kind),
                    label = settings.saveFolder.pathLabel(item.kind, null),
                ),
                compression = request,
                preserveCaptureTime = settings.preserveCaptureTime,
                captureTimeMs = item.modifiedSec.takeIf { it > 0 }?.times(1000L),
            ),
        ) { progress ->
            updateJob(index) { it.copy(ratio = progress.ratio) }
        }
    }

    private fun updateJob(index: Int, transform: (BatchJob) -> BatchJob) {
        _state.update { state ->
            if (index !in state.jobs.indices) return@update state
            state.copy(
                jobs = state.jobs.toMutableList().also { it[index] = transform(it[index]) },
            )
        }
    }

    fun previewUri(item: ScanItem): Uri = item.uri
}
