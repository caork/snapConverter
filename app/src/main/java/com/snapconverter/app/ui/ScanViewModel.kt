package com.snapconverter.app.ui

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snapconverter.app.SnapConverterApp
import com.snapconverter.app.media.MediaOutputWriter
import com.snapconverter.app.media.NeedsUserConsentException
import com.snapconverter.app.media.OriginalReplacer
import com.snapconverter.app.media.ReplaceRequest
import com.snapconverter.app.scan.FolderSummary
import com.snapconverter.app.scan.MediaLibraryScanner
import com.snapconverter.app.scan.ScanItem
import com.snapconverter.app.scan.ScanReport
import com.snapconverter.app.scan.SystemLoad
import com.snapconverter.app.scan.SystemLoadMonitor
import com.snapconverter.engine.device.DeviceCapabilityReport
import com.snapconverter.engine.policy.AuditSensitivity
import com.snapconverter.engine.policy.AuditTuning
import com.snapconverter.engine.policy.AuditVerdict
import com.snapconverter.engine.policy.BitrateModeOption
import com.snapconverter.engine.policy.ComplexityOption
import com.snapconverter.engine.policy.CompressionMode
import com.snapconverter.engine.policy.CompressionRequest
import com.snapconverter.engine.policy.MediaAudit
import com.snapconverter.engine.policy.MediaKind
import com.snapconverter.engine.policy.OutputFps
import com.snapconverter.engine.policy.OutputImageCodec
import com.snapconverter.engine.policy.OutputResolution
import com.snapconverter.engine.policy.OutputVideoCodec
import com.snapconverter.engine.policy.VideoProfileOption
import com.snapconverter.engine.policy.longEdge
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val outputUri: Uri? = null,
    /** Live encoder throughput, straight from the engine's progress. */
    val fps: Float = 0f,
    val mbps: Float = 0f,
    val etaMs: Long = 0L,
    /** Set once the converted file has taken the original's place. */
    val replaced: Boolean = false,
    val error: String? = null,
) {
    val savedBytes: Long
        get() = if (state == BatchJobState.DONE) {
            (item.sizeBytes - outputBytes).coerceAtLeast(0L)
        } else {
            0L
        }
}

/** Video parameters for a batch — the single-file screen's set, minus trim. */
data class BatchVideoSettings(
    val codec: OutputVideoCodec = OutputVideoCodec.HEVC,
    val mode: CompressionMode = CompressionMode.QUALITY,
    val quality: Int = 70,
    /** Target size as a share of each source file, for TARGET_SIZE. */
    val targetSizeRatio: Float = 0.5f,
    val targetBitrateKbps: Int = 8_000,
    val targetSsim: Float = 0.95f,
    val targetVmaf: Float = 90f,
    /** ORIGINAL keeps the source geometry — the default the user asked for. */
    val resolution: OutputResolution = OutputResolution.ORIGINAL,
    val fps: OutputFps = OutputFps.ORIGINAL,
    val bitrateMode: BitrateModeOption = BitrateModeOption.AUTO,
    val profile: VideoProfileOption = VideoProfileOption.AUTO,
    val complexity: ComplexityOption = ComplexityOption.AUTO,
    val iFrameIntervalSec: Int? = null,
    val maxBFrames: Int? = null,
    val muteAudio: Boolean = false,
)

/**
 * Still parameters for a batch. The single-file screen offers an exact output
 * W×H; a batch spans portrait and landscape, so the equivalent control here is
 * a long-edge cap that every file is fitted into.
 */
data class BatchImageSettings(
    val codec: OutputImageCodec = OutputImageCodec.HEIC,
    val quality: Int = 70,
    val resolution: OutputResolution = OutputResolution.ORIGINAL,
    /** null = keep each file's own geometry. */
    val maxLongEdge: Int? = null,
)

/**
 * Settings shared by every file in a batch, split by media type because a
 * library holds both: one run can leave photos at HEIC 70 while videos go to
 * H.265 at a target bitrate.
 */
data class BatchSettings(
    val video: BatchVideoSettings = BatchVideoSettings(),
    val image: BatchImageSettings = BatchImageSettings(),
    val preserveCaptureTime: Boolean = true,
    val saveFolder: SaveFolder = SaveFolder.APP,
    /**
     * Write the results to the app folder first and offer to put each one in
     * its original's place after the user has looked at it. Replacing without
     * that look is not offered — a batch cannot judge its own quality.
     */
    val replaceOriginals: Boolean = false,
) {
    fun quality(kind: MediaKind): Int =
        if (kind == MediaKind.VIDEO) video.quality else image.quality

    /** Long-edge cap that will actually be applied to a file of this kind. */
    fun longEdgeCap(kind: MediaKind): Int = when (kind) {
        MediaKind.VIDEO -> video.resolution.longEdge ?: Int.MAX_VALUE
        MediaKind.IMAGE -> image.maxLongEdge
            ?: image.resolution.longEdge
            ?: Int.MAX_VALUE
    }
}

/**
 * Date window over the findings. The reference point is the moment the scan
 * ran, not "now", so the list cannot shift under the user while they pick.
 */
enum class TimeFilter(val label: String, val detail: String? = null) {
    ALL("全部时间"),
    LAST_30_DAYS("近 30 天"),
    LAST_YEAR("近一年"),
    OLDER_THAN_YEAR("一年以前", "适合先压存量"),
    OLDER_THAN_3_YEARS("三年以前", "最少改动近期文件"),
    CUSTOM("自定义区间", "指定起止日期"),
    ;

    fun accepts(modifiedSec: Long, nowSec: Long, range: DateRange? = null): Boolean {
        if (this == ALL) return true
        // An undated row cannot be placed in a window, so it stays out of
        // every window rather than being guessed into one.
        if (modifiedSec <= 0L || nowSec <= 0L) return false
        if (this == CUSTOM) return range?.contains(modifiedSec) ?: true
        val age = nowSec - modifiedSec
        return when (this) {
            LAST_30_DAYS -> age <= 30 * DAY
            LAST_YEAR -> age <= 365 * DAY
            OLDER_THAN_YEAR -> age > 365 * DAY
            OLDER_THAN_3_YEARS -> age > 3 * 365 * DAY
            else -> true
        }
    }

    private companion object {
        const val DAY = 24L * 60 * 60
    }
}

/**
 * Inclusive custom window, stored as calendar days so the label and the filter
 * cannot disagree: [from] starts at 00:00 local time, [to] ends at 23:59:59.
 */
data class DateRange(
    val fromYear: Int,
    val fromMonth: Int,
    val fromDay: Int,
    val toYear: Int,
    val toMonth: Int,
    val toDay: Int,
) {
    val fromSec: Long get() = startOfDay(fromYear, fromMonth, fromDay)
    val toSec: Long get() = startOfDay(toYear, toMonth, toDay) + 24L * 60 * 60 - 1

    val ordered: DateRange
        get() = if (fromSec <= toSec) {
            this
        } else {
            DateRange(toYear, toMonth, toDay, fromYear, fromMonth, fromDay)
        }

    fun contains(sec: Long): Boolean {
        val window = ordered
        return sec in window.fromSec..window.toSec
    }

    val label: String get() = ordered.let { day(it.fromYear, it.fromMonth, it.fromDay) + " – " + day(it.toYear, it.toMonth, it.toDay) }

    /** Shifts one edge by whole years, months or days, keeping it a real date. */
    fun shift(edge: DateEdge, field: DateField, delta: Int): DateRange {
        val y = if (edge == DateEdge.FROM) fromYear else toYear
        val m = if (edge == DateEdge.FROM) fromMonth else toMonth
        val d = if (edge == DateEdge.FROM) fromDay else toDay
        val cal = Calendar.getInstance().apply {
            clear()
            set(y, m - 1, 1)
            // Move on the first of the month, then clamp the day, so 1-31 + one
            // month lands on the 28th/30th instead of rolling into next month.
            when (field) {
                DateField.YEAR -> add(Calendar.YEAR, delta)
                DateField.MONTH -> add(Calendar.MONTH, delta)
                DateField.DAY -> Unit
            }
        }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val day = when (field) {
            DateField.DAY -> d + delta
            else -> d
        }
        return if (field == DateField.DAY && (day < 1 || day > maxDay)) {
            // Day steps roll into the neighbouring month.
            val rolled = Calendar.getInstance().apply {
                clear()
                set(y, m - 1, d)
                add(Calendar.DAY_OF_MONTH, delta)
            }
            replace(
                edge,
                rolled.get(Calendar.YEAR),
                rolled.get(Calendar.MONTH) + 1,
                rolled.get(Calendar.DAY_OF_MONTH),
            )
        } else {
            replace(edge, year, month, day.coerceIn(1, maxDay))
        }
    }

    private fun replace(edge: DateEdge, y: Int, m: Int, d: Int): DateRange =
        if (edge == DateEdge.FROM) {
            copy(fromYear = y, fromMonth = m, fromDay = d)
        } else {
            copy(toYear = y, toMonth = m, toDay = d)
        }

    companion object {
        private fun startOfDay(year: Int, month: Int, day: Int): Long {
            val cal = Calendar.getInstance().apply {
                clear()
                set(year, (month - 1).coerceIn(0, 11), day.coerceAtLeast(1))
            }
            return cal.timeInMillis / 1000L
        }

        private fun day(year: Int, month: Int, dayOfMonth: Int): String =
            "%04d-%02d-%02d".format(year, month, dayOfMonth)

        /** Defaults to "the year before the scan" — the common cleanup window. */
        fun aroundNow(nowSec: Long): DateRange {
            val cal = Calendar.getInstance().apply {
                timeInMillis = if (nowSec > 0) nowSec * 1000L else System.currentTimeMillis()
            }
            val toYear = cal.get(Calendar.YEAR)
            val toMonth = cal.get(Calendar.MONTH) + 1
            val toDay = cal.get(Calendar.DAY_OF_MONTH)
            return DateRange(toYear - 1, toMonth, toDay, toYear, toMonth, toDay)
        }
    }
}

enum class DateEdge { FROM, TO }

enum class DateField { YEAR, MONTH, DAY }

data class ScanUiState(
    val stage: ScanStage = ScanStage.IDLE,
    val capabilities: DeviceCapabilityReport? = null,
    val sensitivity: AuditSensitivity = AuditSensitivity.STANDARD,
    /** The numbers actually applied; a preset seeds it, the advanced sheet edits it. */
    val tuning: AuditTuning = AuditSensitivity.STANDARD.tuning(),
    val settings: BatchSettings = BatchSettings(),
    val scannedRows: Int = 0,
    val foundSoFar: Int = 0,
    val report: ScanReport? = null,
    /** Bucket ids to keep; empty means every folder. */
    val folderFilter: Set<Long> = emptySet(),
    val timeFilter: TimeFilter = TimeFilter.ALL,
    val customRange: DateRange? = null,
    val selected: Set<Long> = emptySet(),
    val jobs: List<BatchJob> = emptyList(),
    /** Live CPU / GPU sample while a batch runs. */
    val load: SystemLoad = SystemLoad(),
    /** Set when MediaStore wants the user to authorise overwriting originals. */
    val pendingConsent: IntentSender? = null,
    /** The job that prompt belongs to; null when it covers the whole queue. */
    val consentJobId: Long? = null,
    val replacing: Boolean = false,
    val error: String? = null,
) {
    /** Everything the scan flagged, before the filters. */
    val allItems: List<ScanItem> get() = report?.items.orEmpty()

    val folders: List<FolderSummary> get() = report?.folders.orEmpty()

    val filtersActive: Boolean
        get() = folderFilter.isNotEmpty() || timeFilter != TimeFilter.ALL

    /** True when the tuning no longer matches the preset it started from. */
    val tuningCustomised: Boolean get() = tuning != sensitivity.tuning()

    fun visible(item: ScanItem): Boolean {
        if (folderFilter.isNotEmpty() && item.bucketId !in folderFilter) return false
        return timeFilter.accepts(item.modifiedSec, report?.scannedAtSec ?: 0L, customRange)
    }

    val timeFilterLabel: String
        get() = if (timeFilter == TimeFilter.CUSTOM && customRange != null) {
            customRange.label
        } else {
            timeFilter.label
        }

    /** What the list shows and what every selection action operates on. */
    val items: List<ScanItem> get() = allItems.filter { visible(it) }

    val filteredSavingBytes: Long get() = items.sumOf { it.audit.savingBytes }

    val selectedItems: List<ScanItem> get() = items.filter { it.id in selected }

    /** Sum of what the audit thinks the selected files can give back. */
    val selectedSavingBytes: Long
        get() = selectedItems.sumOf { estimatedSaving(it) }

    val selectedSizeBytes: Long get() = selectedItems.sumOf { it.sizeBytes }

    val doneCount: Int get() = jobs.count { it.state == BatchJobState.DONE }
    val failedCount: Int get() = jobs.count { it.state == BatchJobState.FAILED }

    /** Bytes actually saved by the finished jobs — measured, not estimated. */
    val actualSavingBytes: Long get() = jobs.sumOf { it.savedBytes }

    val runningJob: BatchJob? get() = jobs.firstOrNull { it.state == BatchJobState.RUNNING }

    /** Jobs whose output is waiting for the user to look at it and decide. */
    val replaceableJobs: List<BatchJob>
        get() = if (settings.replaceOriginals) {
            jobs.filter { it.state == BatchJobState.DONE && !it.replaced }
        } else {
            emptyList()
        }

    /**
     * Re-estimates with the *batch's* settings instead of the audit defaults,
     * so the number on the button matches what the user is about to run.
     */
    fun estimatedSaving(item: ScanItem): Long {
        val estimate = MediaAudit.estimateOutputBytes(
            fact = item.fact,
            quality = settings.quality(item.kind),
            maxLongEdge = settings.longEdgeCap(item.kind),
            stillBpp = tuning.stillTargetBpp,
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
    private val replacer = OriginalReplacer(application)
    private val loadMonitor = SystemLoadMonitor()

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state

    private var scanJob: Job? = null
    private var batchJob: Job? = null
    private var loadJob: Job? = null
    private var rescanDebounce: Job? = null

    init {
        // The batch sheet only offers formats this silicon can actually encode.
        viewModelScope.launch {
            val caps = withContext(Dispatchers.IO) { runCatching { engine.probeDevice() } }
            caps.getOrNull()?.let { report -> _state.update { it.copy(capabilities = report) } }
        }
    }

    fun scan() {
        scanJob?.cancel()
        val snapshot = _state.value
        val tuning = snapshot.tuning
        val quality = snapshot.settings.video.quality
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
                    scanner.scan(tuning, quality) { scanned, found ->
                        _state.update { it.copy(scannedRows = scanned, foundSoFar = found) }
                    }
                }
            }
            result.onSuccess { report ->
                _state.update { current ->
                    val buckets = report.folders.map { it.bucketId }.toSet()
                    val next = current.copy(
                        stage = ScanStage.RESULT,
                        report = report,
                        scannedRows = report.scanned,
                        foundSoFar = report.items.size,
                        // A folder that no longer holds findings drops out of
                        // the filter instead of silently emptying the list.
                        folderFilter = current.folderFilter intersect buckets,
                    )
                    // Pre-select the clear wins among the visible files;
                    // "high" is left to the user.
                    next.copy(
                        selected = next.items
                            .filter { item -> item.audit.verdict == AuditVerdict.VERY_HIGH }
                            .map { item -> item.id }
                            .toSet(),
                    )
                }
            }.onFailure { t ->
                if (t is CancellationException) return@onFailure
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
        _state.update { it.copy(sensitivity = value, tuning = value.tuning()) }
        rescanIfShowingResults()
    }

    /** Advanced tuning: any single threshold or floor, independent of the presets. */
    fun setTuning(tuning: AuditTuning) {
        if (_state.value.tuning == tuning) return
        _state.update { it.copy(tuning = tuning) }
        rescanIfShowingResults()
    }

    fun resetTuning() = setTuning(_state.value.sensitivity.tuning())

    /**
     * Sliders emit on every pixel of travel, and a full scan is ~250 ms, so a
     * short debounce keeps the list from being rebuilt dozens of times per
     * drag while still feeling immediate when the finger stops.
     */
    private fun rescanIfShowingResults() {
        val stage = _state.value.stage
        if (stage != ScanStage.RESULT && stage != ScanStage.SCANNING) return
        rescanDebounce?.cancel()
        rescanDebounce = viewModelScope.launch {
            delay(RESCAN_DEBOUNCE_MS)
            scan()
        }
    }

    fun setSettings(settings: BatchSettings) = _state.update { it.copy(settings = settings) }

    /**
     * Filters change what the list shows, so anything they hide also leaves the
     * selection — a file the user can no longer see must not be converted by a
     * button that counts files they can.
     */
    fun toggleFolder(bucketId: Long) = _state.update { state ->
        val all = state.folders.map { it.bucketId }.toSet()
        val current = state.folderFilter.ifEmpty { all }
        val next = if (bucketId in current) current - bucketId else current + bucketId
        state.withFilter(folderFilter = if (next == all) emptySet() else next)
    }

    fun selectAllFolders() = _state.update { it.withFilter(folderFilter = emptySet()) }

    fun selectOnlyFolder(bucketId: Long) =
        _state.update { it.withFilter(folderFilter = setOf(bucketId)) }

    fun setTimeFilter(filter: TimeFilter) = _state.update { state ->
        val range = if (filter == TimeFilter.CUSTOM) {
            state.customRange ?: DateRange.aroundNow(state.report?.scannedAtSec ?: 0L)
        } else {
            state.customRange
        }
        state.withFilter(timeFilter = filter, customRange = range)
    }

    fun setCustomRange(range: DateRange) = _state.update {
        it.withFilter(timeFilter = TimeFilter.CUSTOM, customRange = range)
    }

    fun shiftCustomRange(edge: DateEdge, field: DateField, delta: Int) = _state.update { state ->
        val base = state.customRange ?: DateRange.aroundNow(state.report?.scannedAtSec ?: 0L)
        state.withFilter(
            timeFilter = TimeFilter.CUSTOM,
            customRange = base.shift(edge, field, delta),
        )
    }

    private fun ScanUiState.withFilter(
        folderFilter: Set<Long> = this.folderFilter,
        timeFilter: TimeFilter = this.timeFilter,
        customRange: DateRange? = this.customRange,
    ): ScanUiState {
        val next = copy(
            folderFilter = folderFilter,
            timeFilter = timeFilter,
            customRange = customRange,
        )
        val visible = next.items.map { it.id }.toSet()
        return next.copy(selected = next.selected intersect visible)
    }

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

    /**
     * Runs the selected files one after another. Sequential on purpose: the
     * hardware encoder is a single shared block, so two jobs at once would
     * serialise inside the codec anyway while doubling peak memory.
     */
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
        startLoadSampling()
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
                            outputUri = published.uri,
                        )
                    }
                }.onFailure { t ->
                    if (t is CancellationException) throw t
                    updateJob(index) {
                        it.copy(
                            state = BatchJobState.FAILED,
                            error = t.message ?: t.javaClass.simpleName,
                        )
                    }
                }
            }
            finishBatch()
        }
    }

    fun stopBatch() {
        batchJob?.cancel()
        finishBatch()
    }

    private fun finishBatch() {
        loadJob?.cancel()
        loadJob = null
        _state.update { it.copy(stage = ScanStage.FINISHED, load = SystemLoad()) }
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

    /* ------------------------------------------------------- replace originals */

    /**
     * Puts one converted file in its original's place, after the user has
     * previewed it. MediaStore may want the user to authorise writing to a
     * file this app did not create; that arrives as [ScanUiState.pendingConsent]
     * and the caller launches it, then calls this again.
     */
    fun replaceOriginal(jobId: Long) {
        if (_state.value.replacing) return
        val job = _state.value.jobs.firstOrNull { it.item.id == jobId } ?: return
        _state.update { it.copy(replacing = true, error = null) }
        viewModelScope.launch {
            performReplace(job)
            _state.update { it.copy(replacing = false) }
        }
    }

    /**
     * Replaces every reviewed-and-still-pending output. The consent for all of
     * them is asked for once, up front: fifty system dialogs in a row would be
     * worse than the batch itself.
     */
    fun replaceAll() {
        if (_state.value.replacing) return
        val pending = _state.value.replaceableJobs
        if (pending.isEmpty()) return
        _state.update { it.copy(replacing = true, error = null) }
        viewModelScope.launch {
            val sender = withContext(Dispatchers.IO) {
                runCatching {
                    replacer.writeConsentSender(pending.map { job -> job.item.uri })
                }.getOrNull()
            }
            if (sender != null) {
                _state.update {
                    it.copy(replacing = false, pendingConsent = sender, consentJobId = null)
                }
                return@launch
            }
            for (job in pending) {
                if (!performReplace(job)) break
            }
            _state.update { it.copy(replacing = false) }
        }
    }

    /** False when the queue must stop: consent is needed, or one failed. */
    private suspend fun performReplace(job: BatchJob): Boolean {
        val output = job.outputUri ?: return true
        if (job.state != BatchJobState.DONE || job.replaced) return true
        val index = _state.value.jobs.indexOfFirst { it.item.id == job.item.id }
        if (index < 0) return true
        val settings = _state.value.settings
        return try {
            val result = withContext(Dispatchers.IO) {
                replacer.replace(
                    ReplaceRequest(
                        original = job.item.uri,
                        encoded = output,
                        kind = job.item.kind,
                        preserveCaptureTime = settings.preserveCaptureTime,
                        captureTimeMs = job.item.modifiedSec.takeIf { it > 0 }?.times(1000L),
                        imageCodec = settings.image.codec,
                    ),
                )
            }
            updateJob(index) {
                it.copy(
                    replaced = true,
                    outputUri = result.uri,
                    outputName = result.displayName,
                    outputBytes = result.sizeBytes.takeIf { size -> size > 0 } ?: it.outputBytes,
                )
            }
            true
        } catch (t: CancellationException) {
            throw t
        } catch (t: NeedsUserConsentException) {
            _state.update { it.copy(pendingConsent = t.sender, consentJobId = job.item.id) }
            false
        } catch (t: Throwable) {
            _state.update { it.copy(error = t.message ?: "替换失败") }
            false
        }
    }

    /** Called once the screen has handed the prompt to the system. */
    fun consumePendingConsent() = _state.update { it.copy(pendingConsent = null) }

    fun onReplaceConsentResult(granted: Boolean) {
        val jobId = _state.value.consentJobId
        _state.update { it.copy(consentJobId = null) }
        if (!granted) {
            _state.update { it.copy(error = "未获得改写原文件的授权") }
            return
        }
        if (jobId == null) replaceAll() else replaceOriginal(jobId)
    }

    /* ------------------------------------------------------------- monitoring */

    private fun startLoadSampling() {
        loadJob?.cancel()
        loadMonitor.reset()
        loadJob = viewModelScope.launch {
            while (true) {
                delay(LOAD_SAMPLE_MS)
                val sample = withContext(Dispatchers.IO) { loadMonitor.sample() }
                _state.update { it.copy(load = sample) }
            }
        }
    }

    /* ---------------------------------------------------------------- encoding */

    private fun convert(
        item: ScanItem,
        settings: BatchSettings,
        index: Int,
    ): MediaOutputWriter.Published {
        val request = compressionRequest(item, settings)
        // With "replace originals" the output is staged in the app folder and
        // only moved into place after the user has looked at it.
        val folder = if (settings.replaceOriginals) SaveFolder.APP else settings.saveFolder
        return writer.write(
            MediaOutputWriter.Request(
                kind = item.kind,
                input = item.uri,
                sourceName = item.name,
                destination = MediaOutputWriter.Destination.Library(
                    relativePath = folder.relativePath(item.kind),
                    label = folder.pathLabel(item.kind, null),
                ),
                compression = request,
                preserveCaptureTime = settings.preserveCaptureTime,
                captureTimeMs = item.modifiedSec.takeIf { it > 0 }?.times(1000L),
            ),
        ) { progress ->
            updateJob(index) {
                it.copy(
                    ratio = progress.ratio,
                    fps = progress.framesPerSecond,
                    mbps = progress.megabytesPerSecond,
                    etaMs = progress.etaMs,
                )
            }
        }
    }

    private fun compressionRequest(item: ScanItem, settings: BatchSettings): CompressionRequest =
        when (item.kind) {
            MediaKind.VIDEO -> {
                val video = settings.video
                CompressionRequest(
                    kind = MediaKind.VIDEO,
                    mode = video.mode,
                    appQuality = video.quality,
                    // Target size is a share of each source file, so one
                    // setting means "halve everything" rather than "make every
                    // clip the same size".
                    targetSizeBytes = (item.sizeBytes * video.targetSizeRatio)
                        .toLong()
                        .coerceAtLeast(64L * 1024),
                    targetBitrateBps = video.targetBitrateKbps * 1000,
                    videoCodec = video.codec,
                    resolution = video.resolution,
                    keepOriginalPixels = video.resolution == OutputResolution.ORIGINAL,
                    fps = video.fps,
                    bitrateMode = video.bitrateMode,
                    iFrameIntervalSec = video.iFrameIntervalSec,
                    maxBFrames = video.maxBFrames,
                    profile = video.profile,
                    complexity = video.complexity,
                    targetSsim = video.targetSsim.toDouble(),
                    targetVmaf = video.targetVmaf.toDouble(),
                    muteAudio = video.muteAudio,
                )
            }
            MediaKind.IMAGE -> {
                val image = settings.image
                // A batch spans both orientations, so the long-edge cap is
                // turned into this file's own output size.
                val sized = image.maxLongEdge?.let {
                    MediaAudit.cappedSize(item.width, item.height, it)
                }
                CompressionRequest(
                    kind = MediaKind.IMAGE,
                    mode = CompressionMode.QUALITY,
                    appQuality = image.quality,
                    imageCodec = image.codec,
                    resolution = image.resolution,
                    keepOriginalPixels = image.resolution == OutputResolution.ORIGINAL,
                    imageCustomWidth = sized?.first,
                    imageCustomHeight = sized?.second,
                )
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

    private companion object {
        const val RESCAN_DEBOUNCE_MS = 260L
        const val LOAD_SAMPLE_MS = 800L
    }
}
