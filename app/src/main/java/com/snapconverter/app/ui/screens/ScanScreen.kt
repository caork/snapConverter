package com.snapconverter.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image as ImageContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapconverter.app.scan.FolderSummary
import com.snapconverter.app.scan.ScanItem
import com.snapconverter.app.ui.BatchImageSettings
import com.snapconverter.app.ui.BatchJob
import com.snapconverter.app.ui.BatchJobState
import com.snapconverter.app.ui.BatchSettings
import com.snapconverter.app.ui.BatchVideoSettings
import com.snapconverter.app.ui.DateEdge
import com.snapconverter.app.ui.DateField
import com.snapconverter.app.ui.DateRange
import com.snapconverter.app.ui.SaveFolder
import com.snapconverter.app.ui.ScanStage
import com.snapconverter.app.ui.ScanUiState
import com.snapconverter.app.ui.ScanViewModel
import com.snapconverter.app.ui.TimeFilter
import com.snapconverter.app.ui.components.ActionRow
import com.snapconverter.app.ui.components.BarIconButton
import com.snapconverter.app.ui.components.CardDivider
import com.snapconverter.app.ui.components.CardHeader
import com.snapconverter.app.ui.components.CardSlider
import com.snapconverter.app.ui.components.Chip
import com.snapconverter.app.ui.components.ChipFlow
import com.snapconverter.app.ui.components.ContentCard
import com.snapconverter.app.ui.components.DetailRow
import com.snapconverter.app.ui.components.FilledButton
import com.snapconverter.app.ui.components.GroupedList
import com.snapconverter.app.ui.components.IconTile
import com.snapconverter.app.ui.components.InfoChip
import com.snapconverter.app.ui.components.InfoRow
import com.snapconverter.app.ui.components.InsetGroup
import com.snapconverter.app.ui.components.Ios27LazyScreen
import com.snapconverter.app.ui.components.Ios27Sheet
import com.snapconverter.app.ui.components.IosProgressBar
import com.snapconverter.app.ui.components.MetricTile
import com.snapconverter.app.ui.components.ParamPill
import com.snapconverter.app.ui.components.PillRow
import com.snapconverter.app.ui.components.Segment
import com.snapconverter.app.ui.components.SegmentedControl
import com.snapconverter.app.ui.components.SliderRow
import com.snapconverter.app.ui.components.StackRow
import com.snapconverter.app.ui.components.SwitchRow
import com.snapconverter.app.ui.components.CheckRow
import com.snapconverter.app.ui.components.TileTone
import com.snapconverter.app.ui.components.ValuePill
import com.snapconverter.app.ui.components.ValueRow
import com.snapconverter.app.ui.theme.ios27.Ios27Radius
import com.snapconverter.app.ui.theme.ios27.Ios27Spacing
import com.snapconverter.app.ui.theme.ios27.Ios27Type
import com.snapconverter.app.ui.theme.ios27.LocalIos27Palette
import com.snapconverter.app.ui.theme.pressable
import com.snapconverter.engine.codec.CodecCandidate
import com.snapconverter.engine.codec.MimeTypes
import com.snapconverter.engine.policy.AuditReason
import com.snapconverter.engine.policy.AuditSensitivity
import com.snapconverter.engine.policy.AuditTuning
import com.snapconverter.engine.policy.AuditVerdict
import com.snapconverter.engine.policy.BitrateModeOption
import com.snapconverter.engine.policy.ComplexityOption
import com.snapconverter.engine.policy.CompressionMode
import com.snapconverter.engine.policy.MediaKind
import com.snapconverter.engine.policy.OutputFps
import com.snapconverter.engine.policy.OutputImageCodec
import com.snapconverter.engine.policy.OutputResolution
import com.snapconverter.engine.policy.OutputVideoCodec
import com.snapconverter.engine.policy.VideoProfileOption
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Library scan and batch conversion.
 *
 * The scan reads MediaStore columns only, so the list arrives in a fraction of a
 * second and the screen can state how long it took. Every row explains why it
 * was flagged — measured bitrate against what the encoder's own model says that
 * geometry needs — and the batch applies one set of settings to the selection.
 */
@Composable
fun ScanScreen(viewModel: ScanViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasMediaAccess(context)) }
    var sheet by remember { mutableStateOf(ScanSheet.None) }
    var confirmBatch by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<BatchJob?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted = hasMediaAccess(context) }
    // Overwriting a file this app did not create needs the user's own consent,
    // which MediaStore hands over as an IntentSender rather than a permission.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onReplaceConsentResult(result.resultCode == Activity.RESULT_OK) }
    LaunchedEffect(state.pendingConsent) {
        val sender = state.pendingConsent ?: return@LaunchedEffect
        viewModel.consumePendingConsent()
        runCatching { consentLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
    }

    Ios27LazyScreen(
        title = "媒体库扫描",
        subtitle = headerSubtitle(state, granted),
        leading = {
            BarIconButton(icon = Icons.Rounded.ArrowBack, contentDescription = "返回") {
                onBack()
            }
        },
        trailing = {
            if (state.stage == ScanStage.RESULT) {
                BarIconButton(icon = Icons.Rounded.Tune, contentDescription = "转换设置") {
                    sheet = ScanSheet.Settings
                }
            }
        },
        bottomBar = bottomBar(
            state = state,
            granted = granted,
            onRequest = { permissionLauncher.launch(mediaPermissions()) },
            vm = viewModel,
            onBack = onBack,
            onStartBatch = {
                if (state.selectedItems.size > CONFIRM_ABOVE) {
                    confirmBatch = true
                } else {
                    viewModel.startBatch()
                }
            },
        ),
    ) {
        when {
            !granted -> item { Padded { PermissionCard() } }
            state.stage == ScanStage.IDLE -> item {
                Padded {
                    IntroCard()
                    state.error?.let { Spacer(Modifier.height(14.dp)); ScanErrorCard(it) }
                }
            }
            state.stage == ScanStage.SCANNING -> item { Padded { ScanningCard(state) } }
            state.stage == ScanStage.RESULT -> {
                item {
                    Padded {
                        SummaryCard(state)
                        Spacer(Modifier.height(14.dp))
                        SensitivityCard(
                            state = state,
                            onSelect = { viewModel.setSensitivity(it) },
                            onOpenAdvanced = { sheet = ScanSheet.Tuning },
                        )
                        Spacer(Modifier.height(14.dp))
                        FilterCard(
                            state = state,
                            onOpenFolders = { sheet = ScanSheet.Folders },
                            onOpenTime = { sheet = ScanSheet.Time },
                        )
                        Spacer(Modifier.height(14.dp))
                        SettingsCard(state) { sheet = ScanSheet.Settings }
                        Spacer(Modifier.height(14.dp))
                        SelectionBar(state, viewModel)
                    }
                }
                items(state.items, key = { it.id }) { item ->
                    Padded(vertical = 4.dp) {
                        FindingRow(
                            item = item,
                            selected = item.id in state.selected,
                            estimatedSaving = state.estimatedSaving(item),
                        ) { viewModel.toggle(item.id) }
                    }
                }
                if (state.items.isEmpty()) item { Padded { CleanLibraryCard(state) } }
            }
            else -> {
                item { Padded { BatchProgressCard(state) } }
                if (state.replaceableJobs.isNotEmpty()) {
                    item {
                        Padded(vertical = 4.dp) {
                            ReplaceCard(state) { viewModel.replaceAll() }
                        }
                    }
                }
                state.error?.let { message ->
                    item { Padded(vertical = 4.dp) { ScanErrorCard(message) } }
                }
                itemsIndexed(state.jobs, key = { _, job -> job.item.id }) { _, job ->
                    Padded(vertical = 4.dp) {
                        JobRow(
                            job = job,
                            offerReplace = state.settings.replaceOriginals,
                            busy = state.replacing,
                            onPreview = { preview = job },
                            onReplace = { viewModel.replaceOriginal(job.item.id) },
                        )
                    }
                }
            }
        }
    }

    when (sheet) {
        ScanSheet.Settings -> BatchSettingsSheet(
            state = state,
            onChange = { viewModel.setSettings(it) },
            onDismiss = { sheet = ScanSheet.None },
        )
        ScanSheet.Folders -> FolderFilterSheet(
            state = state,
            vm = viewModel,
            onDismiss = { sheet = ScanSheet.None },
        )
        ScanSheet.Time -> TimeFilterSheet(
            state = state,
            vm = viewModel,
            onDismiss = { sheet = ScanSheet.None },
        )
        ScanSheet.Tuning -> TuningSheet(
            state = state,
            vm = viewModel,
            onDismiss = { sheet = ScanSheet.None },
        )
        ScanSheet.None -> Unit
    }

    // The converted file, full screen, before it takes the original's place.
    preview?.let { job ->
        job.outputUri?.let { uri ->
            MediaPreviewDialog(
                uri = uri,
                kind = job.item.kind,
                displayWidth = job.item.width,
                displayHeight = job.item.height,
            ) { preview = null }
        }
    }

    // A library-sized batch runs for hours and writes a new file per item, so
    // the count and the storage it needs get stated before it starts.
    if (confirmBatch) {
        AlertDialog(
            onDismissRequest = { confirmBatch = false },
            title = { Text("转换 " + state.selectedItems.size + " 个文件") },
            text = {
                Text(
                    "将逐个转换，新增约 " +
                        formatSize(state.selectedSizeBytes - state.selectedSavingBytes) +
                        "，原文件不会被修改或删除。中途可以停止。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmBatch = false
                        viewModel.startBatch()
                    },
                ) { Text("开始") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatch = false }) { Text("取消") }
            },
        )
    }
}

/** Above this many files, a batch asks before it starts. */
private const val CONFIRM_ABOVE = 20

private enum class ScanSheet { None, Settings, Folders, Time, Tuning }

/** List rows keep the card stack's 16pt margin without nesting a Column. */
@Composable
private fun Padded(
    vertical: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Ios27Spacing.margin, vertical = vertical),
    ) {
        content()
    }
}

private fun headerSubtitle(state: ScanUiState, granted: Boolean): String {
    if (!granted) return "需要读取媒体库的权限"
    val report = state.report
    return when {
        state.stage == ScanStage.SCANNING ->
            "已读取 " + state.scannedRows + " 个文件"
        state.stage == ScanStage.RUNNING || state.stage == ScanStage.FINISHED ->
            state.doneCount.toString() + " / " + state.jobs.size + " 已转换"
        report != null ->
            report.scanned.toString() + " 个文件 · 用时 " + formatElapsed(report.elapsedMs)
        else -> "按码率与像素密度找出偏大的文件"
    }
}

@Composable
private fun bottomBar(
    state: ScanUiState,
    granted: Boolean,
    onRequest: () -> Unit,
    vm: ScanViewModel,
    onBack: () -> Unit,
    onStartBatch: () -> Unit,
): (@Composable () -> Unit)? = when {
    !granted -> {
        { FilledButton(text = "授予媒体库权限") { onRequest() } }
    }
    state.stage == ScanStage.IDLE -> {
        { FilledButton(text = "开始扫描") { vm.scan() } }
    }
    state.stage == ScanStage.SCANNING -> {
        { FilledButton(text = "停止扫描") { vm.cancelScan() } }
    }
    state.stage == ScanStage.RESULT -> {
        {
            val count = state.selectedItems.size
            FilledButton(
                text = if (count == 0) {
                    "选择要转换的文件"
                } else {
                    "转换 " + count + " 项 · 预计省 " + formatSize(state.selectedSavingBytes)
                },
                enabled = count > 0,
            ) { onStartBatch() }
        }
    }
    state.stage == ScanStage.RUNNING -> {
        { FilledButton(text = "停止转换") { vm.stopBatch() } }
    }
    else -> {
        {
            FilledButton(text = "返回列表") {
                if (state.report != null) vm.backToResult() else onBack()
            }
        }
    }
}

/* ------------------------------------------------------------------ pre-scan */

@Composable
private fun PermissionCard() {
    ContentCard {
        InfoRow(
            title = "读取媒体库",
            icon = Icons.Rounded.PhotoLibrary,
            subtitle = "扫描只读取系统媒体索引里的宽高、时长和体积，不打开文件内容，" +
                "也不会把任何数据发出本机。",
        )
    }
}

@Composable
private fun IntroCard() {
    val palette = LocalIos27Palette.current
    ContentCard {
        CardHeader(
            title = "找出偏大的文件",
            icon = Icons.Rounded.Insights,
            subtitle = "参照编码器自己的码率模型判断",
        )
        Spacer(Modifier.height(Ios27Spacing.md))
        Text(
            text = "视频按实测码率与同分辨率硬件 HEVC 所需码率比较，" +
                "照片按每像素字节数比较，两者都用与转换相同的模型，" +
                "所以“超出 2.6 倍”意味着真的能压回去。",
            style = Ios27Type.footnote,
            color = palette.labelSecondary,
        )
        Spacer(Modifier.height(Ios27Spacing.lg))
        CardDivider()
        Spacer(Modifier.height(Ios27Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Ios27Spacing.sm)) {
            InfoChip(label = "只读索引", tone = TileTone.Green, icon = Icons.Rounded.Speed)
            InfoChip(label = "不解码文件", tone = TileTone.Neutral)
            InfoChip(label = "全部离线", tone = TileTone.Neutral)
        }
    }
}

@Composable
private fun ScanningCard(state: ScanUiState) {
    val palette = LocalIos27Palette.current
    ContentCard {
        Text(text = "正在读取媒体索引", style = Ios27Type.footnote, color = palette.labelSecondary)
        Text(
            text = state.scannedRows.toString(),
            style = Ios27Type.title1,
            color = palette.label,
        )
        Spacer(Modifier.height(Ios27Spacing.md))
        IosProgressBar(progress = 0f)
        Spacer(Modifier.height(Ios27Spacing.md))
        Text(
            text = "已命中 " + state.foundSoFar + " 个偏大文件",
            style = Ios27Type.footnote,
            color = palette.labelSecondary,
        )
    }
}

@Composable
private fun ScanErrorCard(message: String) {
    val palette = LocalIos27Palette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ios27Radius.xxl))
            .background(palette.red.copy(alpha = if (palette.dark) 0.18f else 0.10f))
            .padding(Ios27Spacing.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = palette.redText,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Ios27Spacing.md))
        Text(text = message, style = Ios27Type.footnote, color = palette.redText)
    }
}

/* -------------------------------------------------------------------- results */

@Composable
private fun SummaryCard(state: ScanUiState) {
    val report = state.report ?: return
    val videos = state.items.count { it.kind == MediaKind.VIDEO }
    val images = state.items.size - videos
    ContentCard {
        CardHeader(
            title = state.items.size.toString() + " 个文件可以压缩",
            icon = Icons.Rounded.Savings,
            tone = if (state.items.isEmpty()) TileTone.Green else TileTone.Yellow,
            subtitle = if (state.filtersActive) {
                "筛选自 " + state.allItems.size + " 项 · 全库 " + report.scanned +
                    " 个文件 · 用时 " + formatElapsed(report.elapsedMs)
            } else {
                report.scanned.toString() + " 个文件 · 用时 " +
                    formatElapsed(report.elapsedMs) +
                    if (report.skipped > 0) " · " + report.skipped + " 个缺少索引信息" else ""
            },
        )
        Spacer(Modifier.height(Ios27Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Ios27Spacing.sm)) {
            MetricTile(
                value = formatSize(state.filteredSavingBytes),
                label = "预计可省",
                emphasized = true,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                value = videos.toString(),
                label = "视频",
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                value = images.toString(),
                label = "照片",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CleanLibraryCard(state: ScanUiState) {
    ContentCard {
        InfoRow(
            title = "没有明显偏大的文件",
            icon = Icons.Rounded.CheckCircle,
            tone = TileTone.Green,
            subtitle = "按当前判定强度，" + (state.report?.scanned ?: 0) +
                " 个文件都在合理范围内。调到“严格”会纳入更多文件。",
        )
    }
}

@Composable
private fun SensitivityCard(
    state: ScanUiState,
    onSelect: (AuditSensitivity) -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    val palette = LocalIos27Palette.current
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "判定强度",
                style = Ios27Type.subheadline,
                color = palette.labelSecondary,
                modifier = Modifier.weight(1f),
            )
            ValuePill(
                value = if (state.tuningCustomised) "自定义" else "高级",
                onClick = onOpenAdvanced,
            )
        }
        Spacer(Modifier.height(Ios27Spacing.sm))
        SegmentedControl(
            segments = listOf(
                Segment(AuditSensitivity.RELAXED, "宽松"),
                Segment(AuditSensitivity.STANDARD, "标准"),
                Segment(AuditSensitivity.STRICT, "严格"),
            ),
            selected = state.sensitivity,
        ) { onSelect(it) }
        Spacer(Modifier.height(Ios27Spacing.sm))
        Text(
            text = if (state.tuningCustomised) {
                tuningSummary(state.tuning) + " · 已在“" +
                    sensitivityName(state.sensitivity) + "”预设上手动调整"
            } else {
                sensitivityHint(state.sensitivity)
            },
            style = Ios27Type.footnote,
            color = palette.labelSecondary,
        )
    }
}

private fun sensitivityName(sensitivity: AuditSensitivity): String = when (sensitivity) {
    AuditSensitivity.RELAXED -> "宽松"
    AuditSensitivity.STANDARD -> "标准"
    AuditSensitivity.STRICT -> "严格"
}

private fun sensitivityHint(sensitivity: AuditSensitivity): String = when (sensitivity) {
    AuditSensitivity.RELAXED -> "只列出严重超标的大文件，长边超过 6000 才算尺寸过大。"
    AuditSensitivity.STANDARD -> "码率超过模型 1.6 倍、或长边超过 4096 的文件会被列出。"
    AuditSensitivity.STRICT -> "纳入更小、超标更少的文件，长边超过 3200 就算尺寸过大。"
}

private fun tuningSummary(tuning: AuditTuning): String = buildList {
    add("码率 " + formatMultiple(tuning.videoHigh))
    add("密度 " + formatMultiple(tuning.stillHigh))
    add("长边 " + tuning.maxLongEdge)
}.joinToString(" · ")

/**
 * Every number the audit applies, one control each.
 *
 * The presets are three points in this space; this sheet is the space itself,
 * for the case where the user's library disagrees with all three — a lot of
 * 8 MB screenshots, say, or 4K clips that are worth keeping at 30 Mbps. Each
 * change re-runs the scan (a quarter of a second) so the list below is always
 * the list these numbers produce.
 */
@Composable
private fun TuningSheet(state: ScanUiState, vm: ScanViewModel, onDismiss: () -> Unit) {
    val tuning = state.tuning
    fun apply(next: AuditTuning) = vm.setTuning(next)
    Ios27Sheet(title = "判定强度 · 高级", onDismiss = onDismiss) {
        GroupedList {
            InsetGroup(
                header = "视频码率倍数",
                footer = "实测码率与同分辨率硬件 HEVC 所需码率之比。" +
                    formatMultiple(tuning.videoHigh) + " 起列出，" +
                    formatMultiple(tuning.videoVeryHigh) + " 起算严重。",
            ) {
                row {
                    SliderRow(
                        title = "偏高",
                        valueLabel = formatMultiple(tuning.videoHigh),
                        value = tuning.videoHigh.toFloat(),
                        range = 1.0f..4.0f,
                        steps = 29,
                    ) { value ->
                        val high = value.toDouble()
                        apply(
                            tuning.copy(
                                videoHigh = high,
                                videoVeryHigh = maxOf(tuning.videoVeryHigh, high),
                            ),
                        )
                    }
                }
                row {
                    SliderRow(
                        title = "严重",
                        valueLabel = formatMultiple(tuning.videoVeryHigh),
                        value = tuning.videoVeryHigh.toFloat(),
                        range = 1.0f..6.0f,
                        steps = 49,
                    ) { value ->
                        val veryHigh = value.toDouble()
                        apply(
                            tuning.copy(
                                videoVeryHigh = veryHigh,
                                videoHigh = minOf(tuning.videoHigh, veryHigh),
                            ),
                        )
                    }
                }
            }

            InsetGroup(
                header = "照片像素密度",
                footer = "目标 " + "%.2f".format(Locale.US, tuning.stillTargetBpp) +
                    " 字节/像素是硬件 HEIC 在同等观感下的用量；" +
                    "调高它会同时放宽判定并抬高预估体积。",
            ) {
                row {
                    SliderRow(
                        title = "偏高",
                        valueLabel = formatMultiple(tuning.stillHigh),
                        value = tuning.stillHigh.toFloat(),
                        range = 1.0f..4.0f,
                        steps = 29,
                    ) { value ->
                        val high = value.toDouble()
                        apply(
                            tuning.copy(
                                stillHigh = high,
                                stillVeryHigh = maxOf(tuning.stillVeryHigh, high),
                            ),
                        )
                    }
                }
                row {
                    SliderRow(
                        title = "严重",
                        valueLabel = formatMultiple(tuning.stillVeryHigh),
                        value = tuning.stillVeryHigh.toFloat(),
                        range = 1.0f..6.0f,
                        steps = 49,
                    ) { value ->
                        val veryHigh = value.toDouble()
                        apply(
                            tuning.copy(
                                stillVeryHigh = veryHigh,
                                stillHigh = minOf(tuning.stillHigh, veryHigh),
                            ),
                        )
                    }
                }
                row {
                    SliderRow(
                        title = "目标 字节/像素",
                        valueLabel = "%.2f".format(Locale.US, tuning.stillTargetBpp),
                        value = tuning.stillTargetBpp.toFloat(),
                        range = 0.04f..0.40f,
                        steps = 35,
                    ) { apply(tuning.copy(stillTargetBpp = it.toDouble())) }
                }
            }

            InsetGroup(
                header = "尺寸上限",
                footer = "长边超过这个像素数的照片按“尺寸过大”列出，" +
                    "预估体积也按缩到这个尺寸计算。",
            ) {
                row {
                    SliderRow(
                        title = "长边",
                        valueLabel = tuning.maxLongEdge.toString() + " px",
                        value = tuning.maxLongEdge.toFloat(),
                        range = 1920f..8192f,
                        steps = 97,
                    ) {
                        apply(tuning.copy(maxLongEdge = (it / 64).toInt() * 64))
                    }
                }
            }

            InsetGroup(
                header = "视频门槛",
                footer = "达不到这些门槛的视频不进列表：太小、太短，或压完省不了多少。",
            ) {
                row {
                    SliderRow(
                        title = "最小体积",
                        valueLabel = formatSize(tuning.minVideoBytes),
                        value = tuning.minVideoBytes / MB.toFloat(),
                        range = 1f..256f,
                        steps = 50,
                    ) { apply(tuning.copy(minVideoBytes = (it.toLong() * MB))) }
                }
                row {
                    SliderRow(
                        title = "最短时长",
                        valueLabel = (tuning.minVideoMs / 1000).toString() + " 秒",
                        value = tuning.minVideoMs / 1000f,
                        range = 1f..30f,
                        steps = 28,
                    ) { apply(tuning.copy(minVideoMs = it.toLong() * 1000)) }
                }
                row {
                    SliderRow(
                        title = "最小收益",
                        valueLabel = formatSize(tuning.minVideoSaving),
                        value = tuning.minVideoSaving / MB.toFloat(),
                        range = 1f..64f,
                        steps = 62,
                    ) { apply(tuning.copy(minVideoSaving = it.toLong() * MB)) }
                }
            }

            InsetGroup(
                header = "照片门槛",
                footer = "RAW 负片与动图默认不判定：它们的体积就是格式的用途，" +
                    "重新编码等于丢掉传感器数据或动画。",
            ) {
                row {
                    SliderRow(
                        title = "最小体积",
                        valueLabel = formatSize(tuning.minStillBytes),
                        value = tuning.minStillBytes / 1024f / 100f,
                        range = 1f..200f,
                        steps = 198,
                    ) { apply(tuning.copy(minStillBytes = (it * 100).toLong() * 1024)) }
                }
                row {
                    SliderRow(
                        title = "最小收益",
                        valueLabel = formatSize(tuning.minStillSaving),
                        value = tuning.minStillSaving / 1024f / 100f,
                        range = 1f..100f,
                        steps = 98,
                    ) { apply(tuning.copy(minStillSaving = (it * 100).toLong() * 1024)) }
                }
                row {
                    SwitchRow(
                        title = "跳过 RAW 与动图",
                        subtitle = "关闭后每个 DNG 都会被列出",
                        checked = tuning.skipUnjudgedStills,
                    ) { apply(tuning.copy(skipUnjudgedStills = it)) }
                }
            }

            InsetGroup(footer = "恢复后回到“" + sensitivityName(state.sensitivity) + "”预设的数值。") {
                row {
                    ActionRow(
                        title = "恢复预设数值",
                        enabled = state.tuningCustomised,
                    ) { vm.resetTuning() }
                }
            }
        }
    }
}

private const val MB = 1024L * 1024

/**
 * Folder and date filters.
 *
 * They apply to the scan's result, not to the query: the scan itself is a few
 * hundred milliseconds, so keeping every finding in memory and filtering the
 * view makes switching folders instant and lets the folder list state what
 * each one is worth.
 */
@Composable
private fun FilterCard(
    state: ScanUiState,
    onOpenFolders: () -> Unit,
    onOpenTime: () -> Unit,
) {
    val palette = LocalIos27Palette.current
    ContentCard {
        CardHeader(
            title = "筛选范围",
            icon = Icons.Rounded.FilterAlt,
            tone = if (state.filtersActive) TileTone.Brand else TileTone.Neutral,
            subtitle = if (state.filtersActive) {
                state.items.size.toString() + " / " + state.allItems.size + " 项在范围内" +
                    if (state.timeFilter == TimeFilter.CUSTOM) " · " + state.timeFilterLabel else ""
            } else {
                "共 " + state.folders.size + " 个文件夹 · 不限时间"
            },
        )
        Spacer(Modifier.height(Ios27Spacing.md))
        PillRow {
            ParamPill(
                label = "文件夹",
                value = folderFilterValue(state),
                icon = Icons.Rounded.FolderOpen,
                tone = TileTone.Yellow,
                modifier = Modifier.weight(1f),
                onClick = onOpenFolders,
            )
            ParamPill(
                label = "时间",
                value = if (state.timeFilter == TimeFilter.CUSTOM) {
                    "自定义"
                } else {
                    state.timeFilter.label
                },
                icon = Icons.Rounded.Schedule,
                modifier = Modifier.weight(1f),
                onClick = onOpenTime,
            )
        }
        if (state.filtersActive && state.items.isEmpty()) {
            Spacer(Modifier.height(Ios27Spacing.md))
            Text(
                text = "当前筛选范围内没有文件。",
                style = Ios27Type.footnote,
                color = palette.labelSecondary,
            )
        }
    }
}

private fun folderFilterValue(state: ScanUiState): String {
    if (state.folderFilter.isEmpty()) return "全部"
    if (state.folderFilter.size == 1) {
        val one = state.folders.firstOrNull { it.bucketId in state.folderFilter }
        return one?.name?.substringAfterLast('/') ?: "1 个"
    }
    return state.folderFilter.size.toString() + " 个"
}

/** Multi-select over the folders that actually hold findings. */
@Composable
private fun FolderFilterSheet(state: ScanUiState, vm: ScanViewModel, onDismiss: () -> Unit) {
    val palette = LocalIos27Palette.current
    val all = state.folderFilter.isEmpty()
    val allIds = state.folders.map { it.bucketId }.toSet()
    Ios27Sheet(title = "文件夹", onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Ios27Spacing.margin),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ContentCard {
                Text(
                    text = "只有含可压缩文件的文件夹会列出。取消勾选 DCIM，" +
                        "就能把相机原片留给另一批不同设置的转换。",
                    style = Ios27Type.footnote,
                    color = palette.labelSecondary,
                )
                Spacer(Modifier.height(Ios27Spacing.md))
                ChipFlow {
                    Chip(label = "全选", selected = all) { vm.selectAllFolders() }
                    Chip(label = "全不选", selected = false) {
                        // Clearing every folder would show nothing, so the
                        // "none" shortcut keeps the biggest folder selected.
                        state.folders.firstOrNull()?.let { vm.selectOnlyFolder(it.bucketId) }
                    }
                }
            }
            InsetGroup(footer = "共 " + state.allItems.size + " 项 · " + state.folders.size + " 个文件夹") {
                state.folders.forEach { folder ->
                    row {
                        CheckRow(
                            title = folder.name,
                            subtitle = folderDetail(folder),
                            selected = all || folder.bucketId in state.folderFilter,
                            onClick = { vm.toggleFolder(folder.bucketId) },
                        )
                    }
                }
            }
            if (state.folders.isEmpty()) {
                ContentCard {
                    Text(
                        text = "这次扫描没有发现可压缩的文件。",
                        style = Ios27Type.footnote,
                        color = palette.labelSecondary,
                    )
                }
            }
        }
    }
}

/**
 * Date window, including an explicit start and end.
 *
 * Material's date-range picker is off limits here (the kit allows four M3
 * components), and a keyboard for six numbers would be worse than this anyway:
 * pick a step unit, then move each edge. Every step lands on a real date — a
 * 31st plus one month becomes the 30th, not the 1st of the month after.
 */
@Composable
private fun TimeFilterSheet(state: ScanUiState, vm: ScanViewModel, onDismiss: () -> Unit) {
    var field by remember { mutableStateOf(DateField.DAY) }
    val range = state.customRange
    Ios27Sheet(title = "时间范围", onDismiss = onDismiss) {
        GroupedList {
            InsetGroup(footer = "按文件的修改时间筛选，基准是这次扫描的时刻。") {
                TimeFilter.entries.forEach { option ->
                    row {
                        CheckRow(
                            title = option.label,
                            subtitle = if (option == TimeFilter.CUSTOM && range != null) {
                                range.label
                            } else {
                                option.detail
                            },
                            selected = state.timeFilter == option,
                        ) { vm.setTimeFilter(option) }
                    }
                }
            }
            if (state.timeFilter == TimeFilter.CUSTOM && range != null) {
                InsetGroup(
                    header = "自定义区间",
                    footer = "起止两天都计入。步进单位决定加减一次走多远。",
                ) {
                    row {
                        StackRow {
                            SegmentedControl(
                                segments = listOf(
                                    Segment(DateField.YEAR, "年"),
                                    Segment(DateField.MONTH, "月"),
                                    Segment(DateField.DAY, "日"),
                                ),
                                selected = field,
                            ) { field = it }
                        }
                    }
                    row {
                        DateStepRow(
                            title = "开始",
                            value = edgeLabel(range, DateEdge.FROM),
                        ) { vm.shiftCustomRange(DateEdge.FROM, field, it) }
                    }
                    row {
                        DateStepRow(
                            title = "结束",
                            value = edgeLabel(range, DateEdge.TO),
                        ) { vm.shiftCustomRange(DateEdge.TO, field, it) }
                    }
                    row {
                        DetailRow(
                            title = "范围内",
                            value = state.items.size.toString() + " 项 · 可省 " +
                                formatSize(state.filteredSavingBytes),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateStepRow(title: String, value: String, onStep: (Int) -> Unit) {
    val palette = LocalIos27Palette.current
    ValueRow(title = title) {
        BarIconButton(icon = Icons.Rounded.Remove, contentDescription = "往前") { onStep(-1) }
        Text(
            text = value,
            style = Ios27Type.body,
            color = palette.label,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(104.dp),
        )
        BarIconButton(icon = Icons.Rounded.Add, contentDescription = "往后") { onStep(1) }
    }
}

private fun edgeLabel(range: DateRange, edge: DateEdge): String = if (edge == DateEdge.FROM) {
    "%04d-%02d-%02d".format(Locale.US, range.fromYear, range.fromMonth, range.fromDay)
} else {
    "%04d-%02d-%02d".format(Locale.US, range.toYear, range.toMonth, range.toDay)
}

private fun folderDetail(folder: FolderSummary): String = buildList {
    add(folder.count.toString() + " 项")
    if (folder.videos > 0) add(folder.videos.toString() + " 视频")
    if (folder.images > 0) add(folder.images.toString() + " 照片")
    add("可省 " + formatSize(folder.savingBytes))
}.joinToString(" · ")

/** The batch settings, summarised where the user is about to press Convert. */
@Composable
private fun SettingsCard(state: ScanUiState, onOpen: () -> Unit) {
    val settings = state.settings
    val video = settings.video
    val image = settings.image
    ContentCard(onClick = onOpen) {
        CardHeader(
            title = "统一转换设置",
            icon = Icons.Rounded.Tune,
            subtitle = "视频与照片分别设置，作用于所有选中的文件",
        )
        Spacer(Modifier.height(Ios27Spacing.md))
        ChipFlow {
            InfoChip(
                label = "视频 " + videoCodecLabel(video.codec) + " · " + videoTargetLabel(video),
                tone = TileTone.Brand,
            )
            InfoChip(
                label = "照片 " + imageCodecLabel(image.codec) + " · 画质 " + image.quality,
                tone = if (image.codec == OutputImageCodec.JPEG) {
                    TileTone.Yellow
                } else {
                    TileTone.Brand
                },
            )
            InfoChip(label = "尺寸 " + sizeSummary(settings))
            if (video.muteAudio) InfoChip(label = "静音")
            InfoChip(
                label = if (settings.replaceOriginals) {
                    "替换原文件"
                } else {
                    settings.saveFolder.chipLabel(null)
                },
                icon = if (settings.replaceOriginals) {
                    Icons.Rounded.SwapHoriz
                } else {
                    Icons.Rounded.FolderOpen
                },
                tone = if (settings.replaceOriginals) TileTone.Red else TileTone.Neutral,
            )
        }
    }
}

private fun videoTargetLabel(video: BatchVideoSettings): String = when (video.mode) {
    CompressionMode.QUALITY -> "画质 " + video.quality
    CompressionMode.TARGET_SIZE -> "%.0f%%".format(Locale.US, video.targetSizeRatio * 100)
    CompressionMode.TARGET_BITRATE -> video.targetBitrateKbps.toString() + " kbps"
    CompressionMode.TARGET_SSIM -> "SSIM %.2f".format(Locale.US, video.targetSsim)
    CompressionMode.TARGET_VMAF -> "VMAF %.0f".format(Locale.US, video.targetVmaf)
    CompressionMode.LOSSLESS_REMUX -> "直通"
}

private fun sizeSummary(settings: BatchSettings): String {
    val video = resolutionLabel(settings.video.resolution)
    val image = settings.image.maxLongEdge?.let { "长边 " + it }
        ?: resolutionLabel(settings.image.resolution)
    return if (video == image) video else video + " / " + image
}

@Composable
private fun SelectionBar(state: ScanUiState, vm: ScanViewModel) {
    if (state.items.isEmpty()) return
    val palette = LocalIos27Palette.current
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "已选 " + state.selectedItems.size + " / " + state.items.size,
                style = Ios27Type.headline,
                color = palette.label,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatSize(state.selectedSizeBytes) + " → " +
                    formatSize(state.selectedSizeBytes - state.selectedSavingBytes),
                style = Ios27Type.footnote,
                color = palette.labelSecondary,
            )
        }
        Spacer(Modifier.height(Ios27Spacing.md))
        ChipFlow {
            Chip(label = "全选", selected = false) { vm.selectAll() }
            Chip(label = "全不选", selected = false) { vm.selectNone() }
            Chip(label = "仅严重超标", selected = false) {
                vm.selectOnly(AuditVerdict.VERY_HIGH)
            }
            Chip(label = "仅视频", selected = false) { vm.selectKind(MediaKind.VIDEO) }
            Chip(label = "仅照片", selected = false) { vm.selectKind(MediaKind.IMAGE) }
        }
    }
}

/** One flagged file: thumbnail, geometry, why it was flagged, what it can give back. */
@Composable
private fun FindingRow(
    item: ScanItem,
    selected: Boolean,
    estimatedSaving: Long,
    onToggle: () -> Unit,
) {
    val palette = LocalIos27Palette.current
    val container = if (selected) {
        palette.tint.copy(alpha = if (palette.dark) 0.18f else 0.09f)
    } else {
        palette.groupedContent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ios27Radius.xl))
            .background(container)
            .pressable(onClick = onToggle)
            .padding(Ios27Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(uri = item.uri, kind = item.kind)
        Spacer(Modifier.width(Ios27Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name.ifBlank { "未命名" },
                style = Ios27Type.subheadlineEmphasized,
                color = palette.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = itemGeometry(item),
                style = Ios27Type.caption1,
                color = palette.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Ios27Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Ios27Spacing.xs)) {
                InfoChip(
                    label = verdictLabel(item),
                    tone = if (item.audit.verdict == AuditVerdict.VERY_HIGH) {
                        TileTone.Red
                    } else {
                        TileTone.Yellow
                    },
                )
                if (estimatedSaving > 0) {
                    InfoChip(label = "省 " + formatSize(estimatedSaving), tone = TileTone.Green)
                }
            }
        }
        Spacer(Modifier.width(Ios27Spacing.sm))
        SelectionMark(selected)
    }
}

@Composable
private fun SelectionMark(selected: Boolean) {
    val palette = LocalIos27Palette.current
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) palette.tintFill else palette.fillTertiary),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = palette.onTintFill,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * MediaStore's own thumbnail cache — `loadThumbnail` returns an already-shrunk
 * frame without decoding the full file, which is what keeps a list of hundreds
 * of 4K videos scrollable.
 */
@Composable
private fun Thumb(uri: Uri, kind: MediaKind) {
    val palette = LocalIos27Palette.current
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.loadThumbnail(uri, Size(THUMB_PX, THUMB_PX), null)
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.fillTertiary),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            ImageContent(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = if (kind == MediaKind.VIDEO) {
                    Icons.Rounded.Movie
                } else {
                    Icons.Rounded.Image
                },
                contentDescription = null,
                tint = palette.labelTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
        if (kind == MediaKind.VIDEO && bitmap != null) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Movie,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

private fun itemGeometry(item: ScanItem): String = buildList {
    add(formatSize(item.sizeBytes))
    add(item.width.toString() + "×" + item.height)
    if (item.kind == MediaKind.VIDEO) {
        // Bitrate earns its place over duration here: it is what got the clip
        // flagged, and the line has room for exactly one of the two.
        if (item.audit.bitrateBps > 0) {
            add(formatBitrate(item.audit.bitrateBps))
        } else {
            add(formatDuration(item.durationMs * 1000))
        }
    } else {
        add("%.1f MP".format(Locale.US, item.width.toLong() * item.height / 1_000_000.0))
    }
}.joinToString(" · ")

private fun verdictLabel(item: ScanItem): String {
    val audit = item.audit
    val severity = if (audit.verdict == AuditVerdict.VERY_HIGH) "严重" else "偏高"
    return when (audit.reason) {
        AuditReason.BITRATE -> "码率" + severity + " " + formatMultiple(audit.overshoot)
        AuditReason.DENSITY -> "体积" + severity + " " + formatMultiple(audit.overshoot)
        AuditReason.DIMENSION -> "尺寸过大"
        AuditReason.NONE -> severity
    }
}

private fun formatMultiple(value: Double): String = "%.1f×".format(Locale.US, value)

private fun formatElapsed(ms: Long): String =
    if (ms < 1000) ms.toString() + " 毫秒" else "%.1f 秒".format(Locale.US, ms / 1000.0)

/* ---------------------------------------------------------------- batch run */

@Composable
private fun BatchProgressCard(state: ScanUiState) {
    val palette = LocalIos27Palette.current
    val total = state.jobs.size.coerceAtLeast(1)
    val current = state.jobs.indexOfFirst { it.state == BatchJobState.RUNNING }
    val running = state.runningJob
    val ratio = (state.doneCount + state.failedCount + (running?.ratio ?: 0f)) / total
    val load = state.load
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.stage == ScanStage.RUNNING) "批量转换中" else "批量转换结束",
                    style = Ios27Type.footnote,
                    color = palette.labelSecondary,
                )
                Text(
                    text = state.doneCount.toString() + " / " + state.jobs.size,
                    style = Ios27Type.title1,
                    color = palette.label,
                )
            }
            if (state.actualSavingBytes > 0) {
                InfoChip(
                    label = "已省 " + formatSize(state.actualSavingBytes),
                    tone = TileTone.Green,
                )
            }
        }
        Spacer(Modifier.height(Ios27Spacing.md))
        IosProgressBar(progress = ratio)
        if (state.stage == ScanStage.RUNNING) {
            Spacer(Modifier.height(Ios27Spacing.lg))
            // Throughput and load, sampled while the encoder runs: the fps says
            // whether the hardware path is working, the CPU share says how much
            // of the work is not on it.
            Row(horizontalArrangement = Arrangement.spacedBy(Ios27Spacing.sm)) {
                MetricTile(
                    value = throughputValue(running),
                    label = throughputLabel(running),
                    emphasized = true,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    value = percentLabel(load.appCpuPercent),
                    label = "本应用 CPU",
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    value = load.gpuPercent?.let { percentLabel(it) } ?: "—",
                    label = "GPU",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(Ios27Spacing.md))
        Text(
            text = buildList {
                if (current >= 0) add("正在处理第 " + (current + 1) + " 个")
                running?.etaMs?.takeIf { it > 0 }?.let { add("剩余 " + formatEta(it)) }
                load.systemCpuPercent?.let { add("全机 CPU " + percentLabel(it)) }
                if (state.stage == ScanStage.RUNNING) {
                    // Say which counters this device keeps from apps, rather
                    // than showing a zero that reads like an idle GPU.
                    val hidden = buildList {
                        if (!load.gpuReadable) add("GPU")
                        if (load.systemCpuPercent == null) add("全机 CPU")
                    }
                    if (hidden.isNotEmpty()) {
                        add("系统未开放 " + hidden.joinToString(" 与 ") + " 读数")
                    }
                }
                if (state.failedCount > 0) add(state.failedCount.toString() + " 个失败")
                add("硬件编码器串行工作，逐个处理")
            }.joinToString(" · "),
            style = Ios27Type.footnote,
            color = palette.labelSecondary,
        )
    }
}

private fun percentLabel(value: Float): String = "%.0f%%".format(Locale.US, value)

/** Videos report frames per second; stills finish too fast to, so they report bytes. */
private fun throughputValue(job: BatchJob?): String = when {
    job == null -> "—"
    job.fps > 0.05f -> "%.0f".format(Locale.US, job.fps)
    job.mbps > 0.005f -> "%.1f".format(Locale.US, job.mbps)
    else -> "—"
}

private fun throughputLabel(job: BatchJob?): String =
    if (job != null && job.fps > 0.05f) "编码 fps" else "MB/秒"

/** Offered once the outputs exist and the user has had a chance to look. */
@Composable
private fun ReplaceCard(state: ScanUiState, onReplaceAll: () -> Unit) {
    val palette = LocalIos27Palette.current
    val pending = state.replaceableJobs
    ContentCard {
        CardHeader(
            title = "替换原文件",
            icon = Icons.Rounded.SwapHoriz,
            tone = TileTone.Red,
            subtitle = pending.size.toString() + " 个结果等待确认 · 可省 " +
                formatSize(pending.sumOf { it.savedBytes }),
        )
        Spacer(Modifier.height(Ios27Spacing.md))
        Text(
            text = "先在下面逐个预览，确认画质没问题再替换。替换会改写原文件本身，" +
                "无法撤销；格式变了的文件，扩展名会一并改掉。",
            style = Ios27Type.footnote,
            color = palette.labelSecondary,
        )
        Spacer(Modifier.height(Ios27Spacing.md))
        FilledButton(
            text = if (state.replacing) "替换中…" else "全部替换（" + pending.size + "）",
            enabled = !state.replacing,
        ) { onReplaceAll() }
    }
}

@Composable
private fun JobRow(
    job: BatchJob,
    offerReplace: Boolean,
    busy: Boolean,
    onPreview: () -> Unit,
    onReplace: () -> Unit,
) {
    val palette = LocalIos27Palette.current
    val done = job.state == BatchJobState.DONE && job.outputUri != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ios27Radius.xl))
            .background(palette.groupedContent)
            .padding(Ios27Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            icon = when (job.state) {
                BatchJobState.DONE -> Icons.Rounded.CheckCircle
                BatchJobState.FAILED -> Icons.Rounded.ErrorOutline
                else -> if (job.item.kind == MediaKind.VIDEO) {
                    Icons.Rounded.Movie
                } else {
                    Icons.Rounded.Image
                }
            },
            tone = when (job.state) {
                BatchJobState.DONE -> TileTone.Green
                BatchJobState.FAILED -> TileTone.Red
                BatchJobState.RUNNING -> TileTone.Brand
                BatchJobState.PENDING -> TileTone.Neutral
            },
            size = 32.dp,
        )
        Spacer(Modifier.width(Ios27Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = job.item.name.ifBlank { "未命名" },
                style = Ios27Type.subheadline,
                color = palette.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = jobDetail(job),
                style = Ios27Type.caption1,
                color = if (job.state == BatchJobState.FAILED) {
                    palette.redText
                } else {
                    palette.labelSecondary
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (job.state == BatchJobState.RUNNING) {
                Spacer(Modifier.height(Ios27Spacing.xs))
                IosProgressBar(progress = job.ratio)
            }
            if (done) {
                Spacer(Modifier.height(Ios27Spacing.sm))
                ChipFlow {
                    Chip(label = "预览", selected = false) { onPreview() }
                    if (job.replaced) {
                        InfoChip(label = "已替换原文件", tone = TileTone.Green)
                    } else if (offerReplace) {
                        Chip(label = "替换原文件", selected = false, enabled = !busy) {
                            onReplace()
                        }
                    }
                }
            }
        }
    }
}

private fun jobDetail(job: BatchJob): String = when (job.state) {
    BatchJobState.PENDING -> "等待 · " + formatSize(job.item.sizeBytes)
    BatchJobState.RUNNING -> (job.ratio * 100).toInt().toString() + "% · " +
        formatSize(job.item.sizeBytes) +
        if (job.fps > 0.05f) " · %.0f fps".format(Locale.US, job.fps) else ""
    BatchJobState.DONE -> formatSize(job.item.sizeBytes) + " → " +
        formatSize(job.outputBytes) + savedSuffix(job)
    BatchJobState.FAILED -> job.error ?: "转换失败"
}

private fun savedSuffix(job: BatchJob): String {
    if (job.item.sizeBytes <= 0 || job.outputBytes <= 0) return ""
    val saved = (1.0 - job.outputBytes.toDouble() / job.item.sizeBytes) * 100
    return if (saved > 0.5) " · −%.0f%%".format(Locale.US, saved) else ""
}

/* ------------------------------------------------------------- settings sheet */

/**
 * The batch's parameters, the same set the single-file screen offers, split by
 * media type: one library run touches both photos and videos, and the settings
 * that suit a 4K clip say nothing about a screenshot. Trim is the one thing the
 * single-file screen has that a batch cannot: there is no timeline to point at.
 */
@Composable
private fun BatchSettingsSheet(
    state: ScanUiState,
    onChange: (BatchSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableStateOf(MediaKind.VIDEO) }
    val videos = state.selectedItems.count { it.kind == MediaKind.VIDEO }
    val images = state.selectedItems.size - videos
    Ios27Sheet(title = "统一转换设置", onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Ios27Spacing.margin),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SegmentedControl(
                segments = listOf(
                    Segment(MediaKind.VIDEO, "视频 " + videos),
                    Segment(MediaKind.IMAGE, "照片 " + images),
                ),
                selected = tab,
            ) { tab = it }
            if (tab == MediaKind.VIDEO) {
                VideoBatchCards(state, onChange)
            } else {
                ImageBatchCards(state, onChange)
            }
            BatchDestinationGroup(state, onChange)
        }
    }
}

@Composable
private fun VideoBatchCards(state: ScanUiState, onChange: (BatchSettings) -> Unit) {
    val palette = LocalIos27Palette.current
    val settings = state.settings
    val video = settings.video
    val caps = state.capabilities
    val encoder = batchVideoEncoder(state, video.codec)
    val complexityOk = encoder?.complexityRange != null
    val baseline = video.profile == VideoProfileOption.BASELINE
    fun set(next: BatchVideoSettings) = onChange(settings.copy(video = next))

    ContentCard {
        CardHeader(title = "视频编码", icon = Icons.Rounded.Movie)
        Spacer(Modifier.height(Ios27Spacing.md))
        ChipFlow {
            Chip(
                label = "H.265",
                detail = "HEVC",
                selected = video.codec == OutputVideoCodec.HEVC,
                enabled = caps?.hardwareHevcEncoder != false,
            ) { set(video.copy(codec = OutputVideoCodec.HEVC)) }
            Chip(
                label = "H.264",
                detail = "AVC",
                selected = video.codec == OutputVideoCodec.AVC,
                enabled = caps?.hardwareAvcEncoder != false,
            ) { set(video.copy(codec = OutputVideoCodec.AVC)) }
            if (caps?.hardwareAv1Encoder == true) {
                Chip(
                    label = "AV1",
                    selected = video.codec == OutputVideoCodec.AV1,
                ) { set(video.copy(codec = OutputVideoCodec.AV1)) }
            }
        }
    }

    ContentCard {
        CardHeader(
            title = "压缩目标",
            icon = Icons.Rounded.Insights,
            subtitle = "决定每个文件的码率怎么定",
        )
        Spacer(Modifier.height(Ios27Spacing.md))
        ChipFlow {
            Chip(label = "画质", selected = video.mode == CompressionMode.QUALITY) {
                set(video.copy(mode = CompressionMode.QUALITY))
            }
            Chip(label = "目标大小", selected = video.mode == CompressionMode.TARGET_SIZE) {
                set(video.copy(mode = CompressionMode.TARGET_SIZE))
            }
            Chip(label = "平均码率", selected = video.mode == CompressionMode.TARGET_BITRATE) {
                set(video.copy(mode = CompressionMode.TARGET_BITRATE))
            }
            Chip(label = "目标 SSIM", selected = video.mode == CompressionMode.TARGET_SSIM) {
                set(video.copy(mode = CompressionMode.TARGET_SSIM))
            }
            Chip(
                label = "目标 VMAF",
                selected = video.mode == CompressionMode.TARGET_VMAF,
                enabled = caps?.vmafAvailable == true,
            ) { set(video.copy(mode = CompressionMode.TARGET_VMAF)) }
        }
        Spacer(Modifier.height(Ios27Spacing.lg))
        when (video.mode) {
            CompressionMode.TARGET_SIZE -> CardSlider(
                label = "目标大小",
                valueLabel = "%.0f%%".format(Locale.US, video.targetSizeRatio * 100) + " 原体积",
                value = video.targetSizeRatio,
                range = 0.05f..1.5f,
                steps = 28,
            ) { set(video.copy(targetSizeRatio = it)) }
            CompressionMode.TARGET_BITRATE -> CardSlider(
                label = "平均码率",
                valueLabel = video.targetBitrateKbps.toString() + " kbps",
                value = video.targetBitrateKbps.toFloat(),
                range = 200f..40000f,
            ) { set(video.copy(targetBitrateKbps = it.toInt().coerceIn(200, 40000))) }
            CompressionMode.TARGET_SSIM -> CardSlider(
                label = "目标 SSIM",
                valueLabel = "%.3f".format(Locale.US, video.targetSsim),
                value = video.targetSsim,
                range = 0.80f..0.99f,
            ) { set(video.copy(targetSsim = it)) }
            CompressionMode.TARGET_VMAF -> CardSlider(
                label = "目标 VMAF",
                valueLabel = "%.0f".format(Locale.US, video.targetVmaf),
                value = video.targetVmaf,
                range = 60f..98f,
            ) { set(video.copy(targetVmaf = it)) }
            else -> CardSlider(
                label = "画质",
                valueLabel = video.quality.toString(),
                value = video.quality.toFloat(),
                range = 0f..100f,
            ) { set(video.copy(quality = it.toInt())) }
        }
        Spacer(Modifier.height(Ios27Spacing.md))
        Text(
            text = batchModeHint(video),
            style = Ios27Type.footnote,
            color = palette.labelSecondary,
        )
    }

    ContentCard {
        CardHeader(
            title = "尺寸与帧率",
            icon = Icons.Rounded.Tv,
            subtitle = "默认保持每个文件原本的分辨率和帧率",
        )
        Spacer(Modifier.height(Ios27Spacing.md))
        SegmentedControl(
            segments = OutputResolution.entries.map { Segment(it, resolutionLabel(it)) },
            selected = video.resolution,
        ) { set(video.copy(resolution = it)) }
        Spacer(Modifier.height(Ios27Spacing.sm))
        SegmentedControl(
            segments = OutputFps.entries.map { Segment(it, fpsLabel(it)) },
            selected = video.fps,
        ) { set(video.copy(fps = it)) }
    }

    InsetGroup(header = "码率模式", footer = batchBitrateModeFooter(video)) {
        row {
            StackRow {
                SegmentedControl(
                    segments = listOf(
                        Segment(BitrateModeOption.AUTO, "Auto"),
                        Segment(BitrateModeOption.VBR, "VBR"),
                        Segment(BitrateModeOption.CBR, "CBR"),
                        Segment(BitrateModeOption.CQ, "CQ"),
                    ),
                    selected = video.bitrateMode,
                ) { set(video.copy(bitrateMode = it)) }
            }
        }
    }

    InsetGroup(
        header = "GOP 与参考帧",
        footer = if (baseline) "Baseline profile 不支持 B 帧。" else null,
    ) {
        row {
            StackRow {
                SegmentedControl(
                    segments = listOf(
                        Segment(null as Int?, "Auto"),
                        Segment(1 as Int?, "1s"),
                        Segment(2 as Int?, "2s"),
                        Segment(3 as Int?, "3s"),
                        Segment(5 as Int?, "5s"),
                        Segment(10 as Int?, "10s"),
                    ),
                    selected = video.iFrameIntervalSec,
                ) { set(video.copy(iFrameIntervalSec = it)) }
            }
        }
        row {
            StackRow {
                SegmentedControl(
                    segments = listOf(
                        Segment(null as Int?, "Auto"),
                        Segment(0 as Int?, "B0"),
                        Segment(1 as Int?, "B1"),
                        Segment(2 as Int?, "B2"),
                        Segment(3 as Int?, "B3"),
                    ),
                    selected = if (baseline) 0 else video.maxBFrames,
                    enabled = !baseline,
                ) { set(video.copy(maxBFrames = it)) }
            }
        }
    }

    InsetGroup(
        header = "Profile 与复杂度",
        footer = if (!complexityOk) "此编码器不暴露 complexity。" else null,
    ) {
        row {
            StackRow {
                SegmentedControl(
                    segments = if (video.codec == OutputVideoCodec.AVC) {
                        listOf(
                            Segment(VideoProfileOption.AUTO, "Auto"),
                            Segment(VideoProfileOption.BASELINE, "Baseline"),
                            Segment(VideoProfileOption.MAIN, "Main"),
                            Segment(VideoProfileOption.HIGH, "High"),
                        )
                    } else {
                        listOf(
                            Segment(VideoProfileOption.AUTO, "Auto"),
                            Segment(VideoProfileOption.MAIN, "Main"),
                            Segment(VideoProfileOption.MAIN10, "Main10"),
                        )
                    },
                    selected = video.profile,
                ) { set(video.copy(profile = it)) }
            }
        }
        row {
            StackRow {
                SegmentedControl(
                    segments = listOf(
                        Segment(ComplexityOption.AUTO, "Auto"),
                        Segment(ComplexityOption.LOW, "Low", complexityOk),
                        Segment(ComplexityOption.MEDIUM, "Medium", complexityOk),
                        Segment(ComplexityOption.HIGH, "High", complexityOk),
                    ),
                    selected = video.complexity,
                ) { set(video.copy(complexity = it)) }
            }
        }
    }

    InsetGroup(footer = "编码器：" + (encoder?.name ?: "—")) {
        row {
            SwitchRow(
                title = "静音输出",
                subtitle = "去掉音轨",
                checked = video.muteAudio,
            ) { set(video.copy(muteAudio = it)) }
        }
    }
}

@Composable
private fun ImageBatchCards(state: ScanUiState, onChange: (BatchSettings) -> Unit) {
    val palette = LocalIos27Palette.current
    val settings = state.settings
    val image = settings.image
    val caps = state.capabilities
    val custom = image.maxLongEdge != null
    fun set(next: BatchImageSettings) = onChange(settings.copy(image = next))

    ContentCard {
        CardHeader(title = "照片编码", icon = Icons.Rounded.Image, tone = TileTone.Green)
        Spacer(Modifier.height(Ios27Spacing.md))
        ChipFlow {
            Chip(
                label = "HEIC",
                selected = image.codec == OutputImageCodec.HEIC,
            ) { set(image.copy(codec = OutputImageCodec.HEIC)) }
            Chip(
                label = "AVIF",
                selected = image.codec == OutputImageCodec.AVIF,
                enabled = caps?.hardwareAv1Encoder == true && Build.VERSION.SDK_INT >= 31,
            ) { set(image.copy(codec = OutputImageCodec.AVIF)) }
            Chip(
                label = "JPEG",
                detail = if (caps?.hardwareJpegEncoder == true) null else "CPU",
                selected = image.codec == OutputImageCodec.JPEG,
            ) { set(image.copy(codec = OutputImageCodec.JPEG)) }
        }
    }

    ContentCard {
        CardSlider(
            label = "画质",
            valueLabel = image.quality.toString(),
            value = image.quality.toFloat(),
            range = 0f..100f,
        ) { set(image.copy(quality = it.toInt())) }
        Spacer(Modifier.height(Ios27Spacing.sm))
        Text(
            text = "画质同时决定编码器的取值和列表里的预估体积。",
            style = Ios27Type.footnote,
            color = palette.labelSecondary,
        )
    }

    ContentCard {
        CardHeader(
            title = "输出尺寸",
            icon = Icons.Rounded.PhotoLibrary,
            subtitle = "默认保持原始像素",
        )
        Spacer(Modifier.height(Ios27Spacing.md))
        SegmentedControl(
            segments = OutputResolution.entries.map { Segment(it, resolutionLabel(it)) },
            selected = image.resolution,
            enabled = !custom,
        ) { set(image.copy(resolution = it)) }
        Spacer(Modifier.height(Ios27Spacing.md))
        // A batch spans portrait and landscape, so the equivalent of the single
        // file screen's exact W×H is a long edge every file is fitted into.
        ChipFlow {
            Chip(label = "自定义长边上限", selected = custom) {
                set(
                    image.copy(
                        maxLongEdge = if (custom) null else 4096,
                        resolution = OutputResolution.ORIGINAL,
                    ),
                )
            }
        }
        if (custom) {
            Spacer(Modifier.height(Ios27Spacing.md))
            CardSlider(
                label = "长边上限",
                valueLabel = image.maxLongEdge.toString() + " px",
                value = (image.maxLongEdge ?: 4096).toFloat(),
                range = 512f..8192f,
                steps = 119,
            ) { set(image.copy(maxLongEdge = (it / 64).toInt() * 64)) }
        }
    }
}

@Composable
private fun BatchDestinationGroup(state: ScanUiState, onChange: (BatchSettings) -> Unit) {
    val settings = state.settings
    InsetGroup(
        header = "输出",
        footer = if (settings.replaceOriginals) {
            "结果先写进 SnapConverter 文件夹；在转换结果里预览确认后，" +
                "点“替换”才会改写原文件。改写无法撤销，格式变了的文件扩展名会一并改掉。"
        } else {
            "批量转换只写入新文件，不会覆盖或删除原文件。"
        },
    ) {
        row {
            SwitchRow(
                title = "保留拍摄时间",
                checked = settings.preserveCaptureTime,
            ) { onChange(settings.copy(preserveCaptureTime = it)) }
        }
        row {
            SwitchRow(
                title = "替换原文件",
                subtitle = "转换后逐个预览，确认再替换",
                checked = settings.replaceOriginals,
            ) { onChange(settings.copy(replaceOriginals = it)) }
        }
        if (!settings.replaceOriginals) {
            BatchFolders.forEach { folder ->
                row {
                    CheckRow(
                        title = folder.chipLabel(null),
                        subtitle = folder.pathLabel(null, null),
                        selected = settings.saveFolder == folder,
                    ) { onChange(settings.copy(saveFolder = folder)) }
                }
            }
        }
    }
}

private fun batchModeHint(video: BatchVideoSettings): String = when (video.mode) {
    CompressionMode.QUALITY -> "按画质档推算码率，分辨率不同的文件各自取值。"
    CompressionMode.TARGET_SIZE -> "按每个文件自己的体积取百分比，不是把所有文件压成同一个大小。"
    CompressionMode.TARGET_BITRATE -> "所有文件用同一个平均码率，4K 与 1080p 会得到不同的画质。"
    CompressionMode.TARGET_SSIM -> "每个文件先标定再编码，批量会明显变慢。"
    CompressionMode.TARGET_VMAF -> "每个文件先标定再编码，批量会明显变慢。"
    CompressionMode.LOSSLESS_REMUX -> "直接复制视频流，不重新编码。"
}

private fun batchBitrateModeFooter(video: BatchVideoSettings): String =
    when (video.bitrateMode) {
        BitrateModeOption.AUTO -> "Auto 跟随上面的压缩目标。"
        BitrateModeOption.VBR -> "动态画面分配更多码率；码率取自压缩目标。"
        BitrateModeOption.CBR -> "恒定码率，适合固定带宽的场合。"
        BitrateModeOption.CQ -> "恒定质量，码率随画面变化，体积无法预先估算。"
    }

/** The encoder this batch would actually select for the chosen codec. */
private fun batchVideoEncoder(state: ScanUiState, codec: OutputVideoCodec): CodecCandidate? {
    val mime = when (codec) {
        OutputVideoCodec.HEVC -> MimeTypes.HEVC
        OutputVideoCodec.AVC -> MimeTypes.AVC
        OutputVideoCodec.AV1 -> MimeTypes.AV1
    }
    return state.capabilities?.encoders?.firstOrNull {
        it.isEncoder && it.mime.equals(mime, ignoreCase = true)
    }
}

/** A SAF tree needs a per-run picker, so the batch offers library folders only. */
private val BatchFolders = listOf(
    SaveFolder.APP,
    SaveFolder.CAMERA,
    SaveFolder.ROOT,
    SaveFolder.DOWNLOAD,
)

/* -------------------------------------------------------------- permissions */

private const val THUMB_PX = 192

internal fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/**
 * True when MediaStore will return media this app did not create. Any one of
 * the read permissions is enough to start: on Android 14 a partial grant lets
 * the scan audit exactly the files the user shared, and an images-only grant
 * simply produces a photo-only report.
 */
internal fun hasMediaAccess(context: Context): Boolean =
    mediaPermissions().any {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }
