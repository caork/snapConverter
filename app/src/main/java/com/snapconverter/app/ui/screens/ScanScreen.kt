package com.snapconverter.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image as ImageContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapconverter.app.scan.FolderSummary
import com.snapconverter.app.scan.ScanItem
import com.snapconverter.app.ui.BatchJob
import com.snapconverter.app.ui.BatchJobState
import com.snapconverter.app.ui.BatchSettings
import com.snapconverter.app.ui.SaveFolder
import com.snapconverter.app.ui.ScanStage
import com.snapconverter.app.ui.ScanUiState
import com.snapconverter.app.ui.ScanViewModel
import com.snapconverter.app.ui.TimeFilter
import com.snapconverter.app.ui.components.BarIconButton
import com.snapconverter.app.ui.components.CardDivider
import com.snapconverter.app.ui.components.CardHeader
import com.snapconverter.app.ui.components.CardSlider
import com.snapconverter.app.ui.components.Chip
import com.snapconverter.app.ui.components.ChipFlow
import com.snapconverter.app.ui.components.ContentCard
import com.snapconverter.app.ui.components.FilledButton
import com.snapconverter.app.ui.components.IconTile
import com.snapconverter.app.ui.components.InfoChip
import com.snapconverter.app.ui.components.InfoRow
import com.snapconverter.app.ui.components.InsetGroup
import com.snapconverter.app.ui.components.Ios27LazyScreen
import com.snapconverter.app.ui.components.Ios27Sheet
import com.snapconverter.app.ui.components.IosProgressBar
import com.snapconverter.app.ui.components.MetricTile
import com.snapconverter.app.ui.components.ParamPill
import com.snapconverter.app.ui.components.PickerOption
import com.snapconverter.app.ui.components.PickerSheet
import com.snapconverter.app.ui.components.PillRow
import com.snapconverter.app.ui.components.Segment
import com.snapconverter.app.ui.components.SegmentedControl
import com.snapconverter.app.ui.components.SwitchRow
import com.snapconverter.app.ui.components.CheckRow
import com.snapconverter.app.ui.components.TileTone
import com.snapconverter.app.ui.theme.ios27.Ios27Radius
import com.snapconverter.app.ui.theme.ios27.Ios27Spacing
import com.snapconverter.app.ui.theme.ios27.Ios27Type
import com.snapconverter.app.ui.theme.ios27.LocalIos27Palette
import com.snapconverter.app.ui.theme.pressable
import com.snapconverter.engine.policy.AuditReason
import com.snapconverter.engine.policy.AuditSensitivity
import com.snapconverter.engine.policy.AuditVerdict
import com.snapconverter.engine.policy.MediaKind
import com.snapconverter.engine.policy.OutputImageCodec
import com.snapconverter.engine.policy.OutputResolution
import com.snapconverter.engine.policy.OutputVideoCodec
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
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted = hasMediaAccess(context) }

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
                        SensitivityCard(state) { viewModel.setSensitivity(it) }
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
                itemsIndexed(state.jobs, key = { _, job -> job.item.id }) { _, job ->
                    Padded(vertical = 4.dp) { JobRow(job) }
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
        ScanSheet.Time -> PickerSheet(
            title = "时间范围",
            options = TimeFilter.entries.map { PickerOption(it, it.label, it.detail) },
            selected = state.timeFilter,
            onSelect = { viewModel.setTimeFilter(it) },
            onDismiss = { sheet = ScanSheet.None },
            footer = "按文件的修改时间筛选，基准是这次扫描的时刻。",
        )
        ScanSheet.None -> Unit
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

private enum class ScanSheet { None, Settings, Folders, Time }

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
private fun SensitivityCard(state: ScanUiState, onSelect: (AuditSensitivity) -> Unit) {
    val palette = LocalIos27Palette.current
    ContentCard {
        Text(text = "判定强度", style = Ios27Type.subheadline, color = palette.labelSecondary)
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
            text = sensitivityHint(state.sensitivity),
            style = Ios27Type.footnote,
            color = palette.labelSecondary,
        )
    }
}

private fun sensitivityHint(sensitivity: AuditSensitivity): String = when (sensitivity) {
    AuditSensitivity.RELAXED -> "只列出严重超标的大文件，长边超过 6000 才算尺寸过大。"
    AuditSensitivity.STANDARD -> "码率超过模型 1.6 倍、或长边超过 4096 的文件会被列出。"
    AuditSensitivity.STRICT -> "纳入更小、超标更少的文件，长边超过 3200 就算尺寸过大。"
}

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
                state.items.size.toString() + " / " + state.allItems.size + " 项在范围内"
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
                value = state.timeFilter.label,
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
    ContentCard(onClick = onOpen) {
        CardHeader(
            title = "统一转换设置",
            icon = Icons.Rounded.Tune,
            subtitle = "作用于所有选中的文件",
        )
        Spacer(Modifier.height(Ios27Spacing.md))
        ChipFlow {
            InfoChip(label = videoCodecLabel(settings.videoCodec), tone = TileTone.Brand)
            InfoChip(
                label = imageCodecLabel(settings.imageCodec),
                tone = if (settings.imageCodec == OutputImageCodec.JPEG) {
                    TileTone.Yellow
                } else {
                    TileTone.Brand
                },
            )
            InfoChip(label = "尺寸 " + resolutionLabel(settings.resolution))
            InfoChip(label = "画质 " + settings.quality)
            InfoChip(label = settings.saveFolder.chipLabel(null), icon = Icons.Rounded.FolderOpen)
        }
    }
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
    val ratio = (state.doneCount + state.failedCount).toFloat() / total
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
        Spacer(Modifier.height(Ios27Spacing.md))
        Text(
            text = buildList {
                if (current >= 0) add("正在处理第 " + (current + 1) + " 个")
                if (state.failedCount > 0) add(state.failedCount.toString() + " 个失败")
                add("硬件编码器串行工作，逐个处理")
            }.joinToString(" · "),
            style = Ios27Type.footnote,
            color = palette.labelSecondary,
        )
    }
}

@Composable
private fun JobRow(job: BatchJob) {
    val palette = LocalIos27Palette.current
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
        }
    }
}

private fun jobDetail(job: BatchJob): String = when (job.state) {
    BatchJobState.PENDING -> "等待 · " + formatSize(job.item.sizeBytes)
    BatchJobState.RUNNING -> (job.ratio * 100).toInt().toString() + "% · " +
        formatSize(job.item.sizeBytes)
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

@Composable
private fun BatchSettingsSheet(
    state: ScanUiState,
    onChange: (BatchSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val settings = state.settings
    val caps = state.capabilities
    val av1 = caps?.hardwareAv1Encoder == true
    Ios27Sheet(title = "统一转换设置", onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Ios27Spacing.margin),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ContentCard {
                CardHeader(title = "视频编码", icon = Icons.Rounded.Movie)
                Spacer(Modifier.height(Ios27Spacing.md))
                ChipFlow {
                    Chip(
                        label = "H.265",
                        detail = "HEVC",
                        selected = settings.videoCodec == OutputVideoCodec.HEVC,
                        enabled = caps?.hardwareHevcEncoder != false,
                    ) { onChange(settings.copy(videoCodec = OutputVideoCodec.HEVC)) }
                    Chip(
                        label = "H.264",
                        detail = "AVC",
                        selected = settings.videoCodec == OutputVideoCodec.AVC,
                        enabled = caps?.hardwareAvcEncoder != false,
                    ) { onChange(settings.copy(videoCodec = OutputVideoCodec.AVC)) }
                    if (av1) {
                        Chip(
                            label = "AV1",
                            selected = settings.videoCodec == OutputVideoCodec.AV1,
                        ) { onChange(settings.copy(videoCodec = OutputVideoCodec.AV1)) }
                    }
                }
            }

            ContentCard {
                CardHeader(title = "照片编码", icon = Icons.Rounded.Image, tone = TileTone.Green)
                Spacer(Modifier.height(Ios27Spacing.md))
                ChipFlow {
                    Chip(
                        label = "HEIC",
                        selected = settings.imageCodec == OutputImageCodec.HEIC,
                    ) { onChange(settings.copy(imageCodec = OutputImageCodec.HEIC)) }
                    Chip(
                        label = "AVIF",
                        selected = settings.imageCodec == OutputImageCodec.AVIF,
                        enabled = av1 && Build.VERSION.SDK_INT >= 31,
                    ) { onChange(settings.copy(imageCodec = OutputImageCodec.AVIF)) }
                    Chip(
                        label = "JPEG",
                        detail = if (caps?.hardwareJpegEncoder == true) null else "CPU",
                        selected = settings.imageCodec == OutputImageCodec.JPEG,
                    ) { onChange(settings.copy(imageCodec = OutputImageCodec.JPEG)) }
                }
            }

            ContentCard {
                CardHeader(
                    title = "输出尺寸",
                    icon = Icons.Rounded.PhotoLibrary,
                    subtitle = "默认保持原始分辨率不变",
                )
                Spacer(Modifier.height(Ios27Spacing.md))
                SegmentedControl(
                    segments = OutputResolution.entries.map {
                        Segment(it, resolutionLabel(it))
                    },
                    selected = settings.resolution,
                ) { onChange(settings.copy(resolution = it)) }
            }

            ContentCard {
                CardSlider(
                    label = "画质目标",
                    valueLabel = settings.quality.toString(),
                    value = settings.quality.toFloat(),
                    range = 0f..100f,
                ) { onChange(settings.copy(quality = it.toInt())) }
                Spacer(Modifier.height(Ios27Spacing.sm))
                Text(
                    text = "画质决定码率模型的取值，也决定扫描时的预估体积。",
                    style = Ios27Type.footnote,
                    color = LocalIos27Palette.current.labelSecondary,
                )
            }

            InsetGroup(footer = "批量转换只写入新文件，不会覆盖或删除原文件。") {
                row {
                    SwitchRow(
                        title = "保留拍摄时间",
                        checked = settings.preserveCaptureTime,
                    ) { onChange(settings.copy(preserveCaptureTime = it)) }
                }
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
