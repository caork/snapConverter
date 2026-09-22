package com.snapconverter.app.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapconverter.app.ui.ConvertStage
import com.snapconverter.app.ui.JobViewModel
import com.snapconverter.app.ui.SaveFolder
import com.snapconverter.app.ui.UiState
import com.snapconverter.app.ui.components.ActionRow
import com.snapconverter.app.ui.components.ActionTile
import com.snapconverter.app.ui.components.BarIconButton
import com.snapconverter.app.ui.components.CardDivider
import com.snapconverter.app.ui.components.CardHeader
import com.snapconverter.app.ui.components.CardSlider
import com.snapconverter.app.ui.components.CardStack
import com.snapconverter.app.ui.components.Chip
import com.snapconverter.app.ui.components.ChipFlow
import com.snapconverter.app.ui.components.ContentCard
import com.snapconverter.app.ui.components.DetailRow
import com.snapconverter.app.ui.components.FilledButton
import com.snapconverter.app.ui.components.IconTile
import com.snapconverter.app.ui.components.InfoChip
import com.snapconverter.app.ui.components.InfoRow
import com.snapconverter.app.ui.components.Ios27Screen
import com.snapconverter.app.ui.components.IosProgressBar
import com.snapconverter.app.ui.components.MetricTile
import com.snapconverter.app.ui.components.ParamPill
import com.snapconverter.app.ui.components.PickerOption
import com.snapconverter.app.ui.components.PickerSheet
import com.snapconverter.app.ui.components.PillRow
import com.snapconverter.app.ui.components.RowSeparator
import com.snapconverter.app.ui.components.SectionLabel
import com.snapconverter.app.ui.components.SwitchRow
import com.snapconverter.app.ui.components.TileNavRow
import com.snapconverter.app.ui.components.TileTone
import com.snapconverter.app.ui.components.ValuePill
import com.snapconverter.app.ui.settings.AppSettings
import com.snapconverter.app.ui.settings.SettingsSheet
import com.snapconverter.app.ui.theme.SnapAnimations
import com.snapconverter.app.ui.theme.ios27.Ios27Radius
import com.snapconverter.app.ui.theme.ios27.Ios27Spacing
import com.snapconverter.app.ui.theme.ios27.Ios27Type
import com.snapconverter.app.ui.theme.ios27.LocalIos27Palette
import com.snapconverter.engine.policy.CompressionMode
import com.snapconverter.engine.policy.MediaKind
import com.snapconverter.engine.policy.OutputFps
import com.snapconverter.engine.policy.OutputImageCodec
import com.snapconverter.engine.policy.OutputResolution
import com.snapconverter.engine.policy.OutputVideoCodec
import com.snapconverter.engine.quality.ImageMetrics

private data class PreviewTarget(
    val uri: Uri,
    val kind: MediaKind?,
    val width: Int,
    val height: Int,
)

private enum class HomeSheet {
    None,
    Settings,
    Capabilities,
    Advanced,
    Trim,
    ImageSize,
    Mode,
    Resolution,
    Fps,
    Folder,
}

private val AllMedia = arrayOf("video/*", "image/*")
private val VideoOnly = arrayOf("video/*")
private val ImageOnly = arrayOf("image/*")

/**
 * The workbench. One screen, four states (empty → ready → running → done),
 * each a stack of cards that owns one decision. Parameters that would bury the
 * screen in rows live behind sheets.
 */
@Composable
fun HomeScreen(
    viewModel: JobViewModel,
    settings: AppSettings.Options,
    onSettingsChange: (AppSettings.Options) -> Unit,
    onOpenScan: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.onPicked(it) } }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> viewModel.onCustomFolderPicked(uri) }
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onReplaceConsentResult(result.resultCode == Activity.RESULT_OK)
    }
    var preview by remember { mutableStateOf<PreviewTarget?>(null) }
    var confirmReplace by remember { mutableStateOf(false) }
    var confirmDeleteExported by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(HomeSheet.None) }
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) confirmDeleteExported = true
    }

    LaunchedEffect(state.pendingConsent) {
        val sender = state.pendingConsent ?: return@LaunchedEffect
        viewModel.consumePendingConsent()
        consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    val pick: (Array<String>) -> Unit = { mimes -> picker.launch(mimes) }

    Ios27Screen(
        title = screenTitle(state),
        subtitle = headerSubtitle(state),
        trailing = {
            BarIconButton(
                icon = Icons.Rounded.Memory,
                contentDescription = "设备能力",
            ) { sheet = HomeSheet.Capabilities }
            BarIconButton(
                icon = Icons.Rounded.Settings,
                contentDescription = "外观设置",
            ) { sheet = HomeSheet.Settings }
        },
        bottomBar = bottomBar(state, viewModel, onPick = { pick(AllMedia) }),
    ) {
        CardStack {
            if (state.stage == ConvertStage.IDLE || state.stage == ConvertStage.LOADING) {
                ImportCard(loading = state.stage == ConvertStage.LOADING, onPick = pick)
                ScanEntryCard(onOpenScan)
                CapabilityCard(state) { sheet = HomeSheet.Capabilities }
            } else {
                SourceCard(
                    state = state,
                    onPreview = { uri, kind, w, h -> preview = PreviewTarget(uri, kind, w, h) },
                    onChange = {
                        viewModel.reset()
                        pick(AllMedia)
                    },
                )
                when (state.stage) {
                    ConvertStage.RUNNING -> ProgressCard(state)
                    ConvertStage.DONE -> ResultCards(
                        state = state,
                        onPreview = { uri, kind, w, h -> preview = PreviewTarget(uri, kind, w, h) },
                        onOpen = { uri -> openOutput(context, uri, state.kind) },
                        onShare = { uri ->
                            shareLauncher.launch(shareOutput(context, uri, state.kind))
                        },
                        onReplace = { confirmReplace = true },
                    )
                    else -> Unit
                }
                if (state.stage != ConvertStage.DONE) {
                    OutputCards(
                        state = state,
                        vm = viewModel,
                        onOpenSheet = { sheet = it },
                    )
                }
                state.error?.let { ErrorCard(it) }
                if (state.capabilities?.v1Supported == false) UnsupportedCard()
            }
        }
    }

    preview?.let { target ->
        MediaPreviewDialog(
            uri = target.uri,
            kind = target.kind,
            displayWidth = target.width,
            displayHeight = target.height,
            onDismiss = { preview = null },
        )
    }

    when (sheet) {
        HomeSheet.Settings -> SettingsSheet(
            options = settings,
            onChange = {
                onSettingsChange(it)
                AppSettings.save(context, it)
            },
            onOpenCapabilities = { sheet = HomeSheet.Capabilities },
            onDismiss = { sheet = HomeSheet.None },
        )
        HomeSheet.Capabilities -> CapabilityScreen(
            report = state.capabilities,
            onDismiss = { sheet = HomeSheet.None },
        )
        HomeSheet.Advanced -> AdvancedSheet(
            state = state,
            vm = viewModel,
            onDismiss = { sheet = HomeSheet.None },
        )
        HomeSheet.Trim -> TrimSheet(
            state = state,
            vm = viewModel,
            onDismiss = { sheet = HomeSheet.None },
        )
        HomeSheet.ImageSize -> ImageSizeSheet(
            state = state,
            vm = viewModel,
            onDismiss = { sheet = HomeSheet.None },
        )
        HomeSheet.Mode -> PickerSheet(
            title = "压缩目标",
            options = modeOptions(state),
            selected = state.mode,
            onSelect = { viewModel.setMode(it) },
            onDismiss = { sheet = HomeSheet.None },
            footer = "目标 VMAF / SSIM 会先标定码率再整片编码，耗时更长但体积更省。",
        )
        HomeSheet.Resolution -> PickerSheet(
            title = "分辨率",
            options = OutputResolution.entries.map { PickerOption(it, resolutionLabel(it)) },
            selected = state.resolution,
            onSelect = { viewModel.setResolution(it) },
            onDismiss = { sheet = HomeSheet.None },
            footer = "缩放在 GPU 上完成，不经过 CPU。",
        )
        HomeSheet.Fps -> PickerSheet(
            title = "帧率",
            options = OutputFps.entries.map { PickerOption(it, fpsLabel(it)) },
            selected = state.fps,
            onSelect = { viewModel.setFps(it) },
            onDismiss = { sheet = HomeSheet.None },
            footer = "降帧通过丢帧实现，不做插值。",
        )
        HomeSheet.Folder -> PickerSheet(
            title = "保存位置",
            options = SaveFolder.entries.map {
                PickerOption(it, it.chipLabel(state.kind), it.pathLabel(state.kind, state.customFolderLabel))
            },
            selected = state.saveFolder,
            onSelect = { folder ->
                if (folder == SaveFolder.CUSTOM && state.customTreeUri == null) {
                    folderPicker.launch(null)
                } else {
                    viewModel.setSaveFolder(folder)
                }
            },
            onDismiss = { sheet = HomeSheet.None },
        )
        HomeSheet.None -> Unit
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text(if (state.kind == MediaKind.IMAGE) "替换原图" else "替换原视频") },
            text = { Text("用转换结果覆盖原来的文件，原文件无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReplace = false
                        viewModel.replaceOriginal()
                    },
                ) { Text("替换") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReplace = false }) { Text("取消") }
            },
        )
    }

    if (confirmDeleteExported) {
        AlertDialog(
            onDismissRequest = { confirmDeleteExported = false },
            title = { Text("删除导出文件") },
            text = { Text("分享已完成。删除刚导出的转换文件？原始文件不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteExported = false
                        viewModel.deleteExportedOutput()
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteExported = false }) { Text("保留") }
            },
        )
    }
}

private fun screenTitle(state: UiState): String = when (state.stage) {
    ConvertStage.IDLE, ConvertStage.LOADING -> "SnapConverter"
    ConvertStage.READY -> "转换"
    ConvertStage.RUNNING -> "转换中"
    ConvertStage.DONE -> "已完成"
}

/** The header's second line: what the device can actually do, when known. */
private fun headerSubtitle(state: UiState): String =
    capabilitySummary(state) ?: "硬件编解码 · 文件不离开本机"

@Composable
private fun bottomBar(
    state: UiState,
    vm: JobViewModel,
    onPick: () -> Unit,
): (@Composable () -> Unit)? = when (state.stage) {
    ConvertStage.READY -> {
        {
            FilledButton(
                text = if (state.audioOnly) "提取音频" else "开始转换",
                // Audio extraction is a passthrough remux: no encoder gate.
                enabled = state.audioOnly || state.capabilities?.v1Supported == true,
            ) { vm.start() }
        }
    }
    ConvertStage.DONE -> {
        {
            FilledButton(text = "再转一个", enabled = !state.replaceRunning) {
                vm.reset()
                onPick()
            }
        }
    }
    else -> null
}

/* --------------------------------------------------------------- empty state */

/** The two real entry points: the picker filtered to video, or to photos. */
@Composable
private fun ImportCard(loading: Boolean, onPick: (Array<String>) -> Unit) {
    ContentCard(padding = PaddingValues(horizontal = Ios27Spacing.lg, vertical = Ios27Spacing.md)) {
        CardHeader(
            title = if (loading) "正在读取…" else "选择文件",
            icon = Icons.Rounded.FolderOpen,
            subtitle = "解码、缩放、编码都在本机硬件上完成",
        )
        Spacer(Modifier.height(Ios27Spacing.sm))
        TileNavRow(
            title = "视频",
            icon = Icons.Rounded.Movie,
            subtitle = "转成 H.265 / H.264 / AV1",
            enabled = !loading,
        ) { onPick(VideoOnly) }
        CardDivider()
        TileNavRow(
            title = "照片",
            icon = Icons.Rounded.PhotoLibrary,
            tone = TileTone.Green,
            subtitle = "转成 HEIC / AVIF / JPEG",
            enabled = !loading,
        ) { onPick(ImageOnly) }
    }
}

/** Entry to the library scan — the other way in, when there is no file in hand. */
@Composable
private fun ScanEntryCard(onOpen: () -> Unit) {
    ContentCard(padding = PaddingValues(horizontal = Ios27Spacing.lg, vertical = Ios27Spacing.md)) {
        TileNavRow(
            title = "扫描媒体库",
            icon = Icons.Rounded.Savings,
            tone = TileTone.Yellow,
            subtitle = "找出偏大的视频和照片，批量转换",
            onClick = onOpen,
        )
    }
}

/** What this device's silicon exposes, enumerated at runtime. */
@Composable
private fun CapabilityCard(state: UiState, onOpen: () -> Unit) {
    val caps = state.capabilities ?: return
    val supported = caps.v1Supported
    ContentCard(
        padding = PaddingValues(horizontal = Ios27Spacing.lg, vertical = Ios27Spacing.lg),
        onClick = onOpen,
    ) {
        CardHeader(
            title = if (supported) "硬件编码器就绪" else "此设备暂不支持",
            icon = Icons.Rounded.Memory,
            tone = if (supported) TileTone.Green else TileTone.Yellow,
            subtitle = caps.socModel.ifBlank { caps.device },
            trailing = { ValuePill(value = "详情", onClick = onOpen) },
        )
        Spacer(Modifier.height(Ios27Spacing.md))
        ChipFlow {
            capabilityChips(state).forEach { (label, available) ->
                InfoChip(
                    label = label,
                    tone = if (available) TileTone.Green else TileTone.Neutral,
                )
            }
        }
    }
}

private fun capabilityChips(state: UiState): List<Pair<String, Boolean>> {
    val caps = state.capabilities ?: return emptyList()
    return listOf(
        "H.265" to caps.hardwareHevcEncoder,
        "H.264" to caps.hardwareAvcEncoder,
        "AV1" to caps.hardwareAv1Encoder,
        "HEIC" to caps.hardwareHeicPath,
        "AVIF" to (caps.hardwareAv1Encoder && Build.VERSION.SDK_INT >= 31),
        "JPEG 硬编" to caps.hardwareJpegEncoder,
        "VMAF 评分" to caps.vmafAvailable,
        "厂商参数" to caps.vendorExtensionsApi,
    )
}

private fun capabilitySummary(state: UiState): String? {
    val caps = state.capabilities ?: return null
    if (!caps.v1Supported) return "没有可用的高通硬件编码器"
    val codecs = buildList {
        if (caps.hardwareHevcEncoder) add("H.265")
        if (caps.hardwareAvcEncoder) add("H.264")
        if (caps.hardwareAv1Encoder) add("AV1")
    }
    val vendor = if (caps.hasQualcommEncoder) "Qualcomm" else "硬件"
    return vendor + " 编码器 · " + codecs.joinToString(" / ")
}

/* ---------------------------------------------------------------- source card */

@Composable
private fun SourceCard(
    state: UiState,
    onPreview: (Uri, MediaKind?, Int, Int) -> Unit,
    onChange: () -> Unit,
) {
    val palette = LocalIos27Palette.current
    val uri = state.input
    val width = state.videoInfo?.displayWidth ?: state.imageInfo?.width ?: 16
    val height = state.videoInfo?.displayHeight ?: state.imageInfo?.height ?: 9
    val running = state.stage == ConvertStage.RUNNING
    ContentCard(padding = PaddingValues(horizontal = Ios27Spacing.lg, vertical = Ios27Spacing.md)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Ios27Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (uri != null) {
                Box(modifier = Modifier.clip(RoundedCornerShape(Ios27Radius.card))) {
                    AspectMediaThumb(
                        uri = uri,
                        kind = state.kind,
                        displayWidth = width,
                        displayHeight = height,
                        maxWidth = 64,
                        maxHeight = 64,
                        onClick = { onPreview(uri, state.kind, width, height) },
                    )
                }
                Spacer(Modifier.width(Ios27Spacing.md))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.displayName,
                    style = Ios27Type.headline,
                    color = palette.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sourceGeometry(state),
                    style = Ios27Type.footnote,
                    color = palette.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ChipFlow {
            sourceChips(state).forEach { chip ->
                InfoChip(label = chip.first, tone = chip.second)
            }
        }
        Spacer(Modifier.height(Ios27Spacing.sm))
        CardDivider()
        TileNavRow(
            title = "更换文件",
            icon = Icons.Rounded.SwapHoriz,
            tone = TileTone.Neutral,
            enabled = !running,
            onClick = onChange,
        )
    }
}

private fun sourceGeometry(state: UiState): String {
    val video = state.videoInfo
    val image = state.imageInfo
    return when {
        video != null -> buildString {
            append(video.displayWidth).append("×").append(video.displayHeight)
            append(if (video.isPortrait) " · 竖屏" else " · 横屏")
            append(" · ").append(formatDuration(video.durationUs))
            append(" · ").append(state.captureTimeMs?.let { formatCapture(it) } ?: "无拍摄时间")
        }
        image != null -> image.width.toString() + "×" + image.height
        else -> "—"
    }
}

/** Source metadata as pills: container, codec, bitrate, size, HDR. */
private fun sourceChips(state: UiState): List<Pair<String, TileTone>> = buildList {
    add(formatSize(state.fileSizeBytes) to TileTone.Neutral)
    val video = state.videoInfo
    if (video != null) {
        add(sourceCodecLabel(video.mime) to TileTone.Brand)
        if (video.bitrateBps > 0) add(formatBitrate(video.bitrateBps) to TileTone.Neutral)
        if (video.isHdr || video.tenBit) add(video.hdrLabel to TileTone.Yellow)
    }
}

/* ---------------------------------------------------------------- output cards */

@Composable
private fun OutputCards(
    state: UiState,
    vm: JobViewModel,
    onOpenSheet: (HomeSheet) -> Unit,
) {
    val palette = LocalIos27Palette.current
    val locked = state.stage == ConvertStage.RUNNING
    val video = state.kind == MediaKind.VIDEO

    SectionLabel("输出格式")
    ContentCard {
        ChipFlow {
            if (video) VideoFormatChips(state, vm, locked) else ImageFormatChips(state, vm, locked)
        }
        formatHint(state)?.let {
            Spacer(Modifier.height(Ios27Spacing.md))
            Text(text = it, style = Ios27Type.footnote, color = palette.labelSecondary)
        }
    }

    if (!state.audioOnly) {
        SectionLabel("压缩")
        ContentCard {
            CardHeader(
                title = "压缩目标",
                icon = Icons.Rounded.Insights,
                trailing = {
                    ValuePill(value = modeLabel(state.mode), enabled = !locked) {
                        onOpenSheet(HomeSheet.Mode)
                    }
                },
            )
            Spacer(Modifier.height(Ios27Spacing.lg))
            ModeSlider(state, vm, locked)
            Spacer(Modifier.height(Ios27Spacing.md))
            Text(
                text = modeHint(state),
                style = Ios27Type.footnote,
                color = palette.labelSecondary,
            )
        }

        SectionLabel("参数")
        if (video) {
            PillRow {
                ParamPill(
                    label = "分辨率",
                    value = resolutionLabel(state.resolution),
                    icon = Icons.Rounded.Tv,
                    enabled = !locked,
                    modifier = Modifier.weight(1f),
                ) { onOpenSheet(HomeSheet.Resolution) }
                ParamPill(
                    label = "帧率",
                    value = fpsLabel(state.fps),
                    icon = Icons.Rounded.Speed,
                    tone = TileTone.Green,
                    enabled = !locked,
                    modifier = Modifier.weight(1f),
                ) { onOpenSheet(HomeSheet.Fps) }
            }
            PillRow {
                ParamPill(
                    label = "片段",
                    value = trimValue(state),
                    icon = Icons.Rounded.ContentCut,
                    tone = TileTone.Yellow,
                    enabled = !locked && state.durationSec > 1,
                    modifier = Modifier.weight(1f),
                ) { onOpenSheet(HomeSheet.Trim) }
                ParamPill(
                    label = "保存位置",
                    value = state.saveFolder.chipLabel(state.kind),
                    icon = Icons.Rounded.FolderOpen,
                    tone = TileTone.Neutral,
                    enabled = !locked,
                    modifier = Modifier.weight(1f),
                ) { onOpenSheet(HomeSheet.Folder) }
            }
            ParamPill(
                label = "高级参数",
                value = advancedSummary(state),
                icon = Icons.Rounded.Tune,
                enabled = !locked,
            ) { onOpenSheet(HomeSheet.Advanced) }
        } else {
            PillRow {
                ParamPill(
                    label = "输出尺寸",
                    value = imageSizeValue(state),
                    icon = Icons.Rounded.PhotoSizeSelectLarge,
                    enabled = !locked,
                    modifier = Modifier.weight(1f),
                ) { onOpenSheet(HomeSheet.ImageSize) }
                ParamPill(
                    label = "保存位置",
                    value = state.saveFolder.chipLabel(state.kind),
                    icon = Icons.Rounded.FolderOpen,
                    tone = TileTone.Neutral,
                    enabled = !locked,
                    modifier = Modifier.weight(1f),
                ) { onOpenSheet(HomeSheet.Folder) }
            }
        }
    } else {
        SectionLabel("参数")
        ParamPill(
            label = "保存位置",
            value = state.saveFolder.chipLabel(state.kind),
            icon = Icons.Rounded.FolderOpen,
            tone = TileTone.Neutral,
            enabled = !locked,
        ) { onOpenSheet(HomeSheet.Folder) }
    }

    ContentCard(padding = PaddingValues(0.dp)) {
        if (video && !state.audioOnly) {
            SwitchRow(
                title = "静音输出",
                subtitle = "去掉音轨",
                checked = state.muteAudio,
                enabled = !locked,
            ) { vm.setMuteAudio(it) }
            RowSeparator()
        }
        SwitchRow(
            title = "保留拍摄时间",
            checked = state.preserveCaptureTime,
            enabled = !locked,
        ) { vm.setPreserveCaptureTime(it) }
        RowSeparator()
        DetailRow(title = "保存到", value = state.savePathLabel)
    }
}

/** Output format as one honest set of chips: codec, or audio-only passthrough. */
@Composable
private fun VideoFormatChips(state: UiState, vm: JobViewModel, locked: Boolean) {
    val caps = state.capabilities
    val selectedCodec = if (state.audioOnly) null else state.videoCodec
    Chip(
        label = "H.265",
        detail = "HEVC",
        selected = selectedCodec == OutputVideoCodec.HEVC,
        enabled = !locked && caps?.hardwareHevcEncoder == true,
    ) {
        vm.setAudioOnly(false)
        vm.setVideoCodec(OutputVideoCodec.HEVC)
    }
    Chip(
        label = "H.264",
        detail = "AVC",
        selected = selectedCodec == OutputVideoCodec.AVC,
        enabled = !locked && caps?.hardwareAvcEncoder == true,
    ) {
        vm.setAudioOnly(false)
        vm.setVideoCodec(OutputVideoCodec.AVC)
    }
    if (caps?.hardwareAv1Encoder == true) {
        Chip(
            label = "AV1",
            selected = selectedCodec == OutputVideoCodec.AV1,
            enabled = !locked,
        ) {
            vm.setAudioOnly(false)
            vm.setVideoCodec(OutputVideoCodec.AV1)
        }
    }
    Chip(
        label = "仅音频",
        detail = "M4A 直通",
        selected = state.audioOnly,
        enabled = !locked,
    ) { vm.setAudioOnly(true) }
}

@Composable
private fun ImageFormatChips(state: UiState, vm: JobViewModel, locked: Boolean) {
    val caps = state.capabilities
    Chip(
        label = "HEIC",
        selected = state.imageCodec == OutputImageCodec.HEIC,
        enabled = !locked,
    ) { vm.setImageCodec(OutputImageCodec.HEIC) }
    Chip(
        label = "AVIF",
        selected = state.imageCodec == OutputImageCodec.AVIF,
        enabled = !locked && caps?.hardwareAv1Encoder == true && Build.VERSION.SDK_INT >= 31,
    ) { vm.setImageCodec(OutputImageCodec.AVIF) }
    // JPEG is always offered, but it is the one CPU-encoded format, so the chip
    // says so up front instead of the UI claiming hardware later.
    Chip(
        label = "JPEG",
        detail = if (caps?.hardwareJpegEncoder == true) null else "CPU",
        selected = state.imageCodec == OutputImageCodec.JPEG,
        enabled = !locked,
    ) { vm.setImageCodec(OutputImageCodec.JPEG) }
}

/** The slider that belongs to the active compression target. */
@Composable
private fun ModeSlider(state: UiState, vm: JobViewModel, locked: Boolean) {
    val palette = LocalIos27Palette.current
    // With a non-Auto bitrate mode the encoder takes its target from the
    // advanced sheet, so the quality slider there is the one that matters.
    val overridden = state.kind == MediaKind.VIDEO &&
        state.bitrateMode != com.snapconverter.engine.policy.BitrateModeOption.AUTO &&
        state.mode != CompressionMode.TARGET_VMAF &&
        state.mode != CompressionMode.TARGET_SSIM
    when {
        overridden -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "码率由高级参数决定",
                style = Ios27Type.subheadline,
                color = palette.labelSecondary,
                modifier = Modifier.weight(1f),
            )
            InfoChip(label = bitrateModeLabel(state.bitrateMode), tone = TileTone.Brand)
        }
        state.mode == CompressionMode.TARGET_SIZE -> CardSlider(
            label = "目标大小",
            valueLabel = "%.0f".format(state.targetSizeRatio * 100) + "% · " +
                formatSize(vm.targetSizeBytes(state)),
            value = state.targetSizeRatio,
            range = 0.05f..1.5f,
            enabled = !locked,
            steps = 28,
        ) { vm.setTargetSizeRatio(it) }
        state.mode == CompressionMode.TARGET_VMAF -> CardSlider(
            label = "目标 VMAF",
            valueLabel = "%.0f".format(state.targetVmaf) + " · " +
                ImageMetrics.vmafLabel(state.targetVmaf.toDouble()),
            value = state.targetVmaf,
            range = 60f..98f,
            enabled = !locked,
        ) { vm.setTargetVmaf(it) }
        state.mode == CompressionMode.TARGET_SSIM -> CardSlider(
            label = "目标 SSIM",
            valueLabel = "%.3f".format(state.targetSsim) + " · " +
                ImageMetrics.ssimLabel(state.targetSsim.toDouble()),
            value = state.targetSsim,
            range = 0.80f..0.99f,
            enabled = !locked,
        ) { vm.setTargetSsim(it) }
        state.mode == CompressionMode.TARGET_BITRATE -> CardSlider(
            label = "平均码率",
            valueLabel = state.targetBitrateKbps.toString() + " kbps",
            value = state.targetBitrateKbps.toFloat(),
            range = 200f..40000f,
            enabled = !locked,
        ) { vm.setTargetBitrateKbps(it.toInt().coerceIn(200, 40000)) }
        else -> CardSlider(
            label = "画质",
            valueLabel = state.quality.toString(),
            value = state.quality.toFloat(),
            range = 0f..100f,
            enabled = !locked,
        ) { vm.setQuality(it.toInt()) }
    }
}

private fun modeOptions(state: UiState): List<PickerOption<CompressionMode>> = buildList {
    add(PickerOption(CompressionMode.QUALITY, "画质", "0–100 画质档，码率由模型推算"))
    add(PickerOption(CompressionMode.TARGET_SIZE, "目标大小", "按原文件百分比定体积"))
    add(
        PickerOption(
            CompressionMode.TARGET_VMAF,
            "目标 VMAF",
            "Netflix 感知指标，先标定再编码",
            enabled = state.capabilities?.vmafAvailable == true,
        ),
    )
    add(PickerOption(CompressionMode.TARGET_SSIM, "目标 SSIM", "结构相似度，先标定再编码"))
    add(PickerOption(CompressionMode.TARGET_BITRATE, "平均码率", "直接指定 kbps"))
}

private fun trimValue(state: UiState): String {
    if (!state.trimActive) return "完整"
    val end = if (state.trimEndSec < 0) state.durationSec else state.trimEndSec
    return formatClock(state.trimStartSec) + " – " + formatClock(end)
}

private fun imageSizeValue(state: UiState): String {
    val w = state.imageCustomWidth
    val h = state.imageCustomHeight
    return if (w != null && h != null) w.toString() + "×" + h else resolutionLabel(state.resolution)
}

/* ------------------------------------------------------------ progress + result */

@Composable
private fun ProgressCard(state: UiState) {
    val palette = LocalIos27Palette.current
    val progress = state.progress
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.message ?: "编码中",
                    style = Ios27Type.footnote,
                    color = palette.labelSecondary,
                )
                Text(
                    text = (progress.ratio * 100).toInt().toString() + "%",
                    style = Ios27Type.title1,
                    color = palette.label,
                )
            }
            EncoderChip(state)
        }
        Spacer(Modifier.height(Ios27Spacing.md))
        IosProgressBar(progress = progress.ratio)
        Spacer(Modifier.height(Ios27Spacing.md))
        Text(
            text = progressDetail(state),
            style = Ios27Type.footnote,
            color = palette.labelSecondary,
        )
    }
}

/**
 * Who is doing the encoding. JPEG runs on the CPU, so it says "CPU" — the app
 * never labels a CPU encode as hardware or as Qualcomm.
 */
@Composable
private fun EncoderChip(state: UiState) {
    when {
        state.usesCpuEncoder -> InfoChip(
            label = "CPU 编码",
            tone = TileTone.Yellow,
            icon = Icons.Rounded.Memory,
        )
        state.audioOnly -> InfoChip(
            label = "直通复制",
            tone = TileTone.Neutral,
            icon = Icons.Rounded.Bolt,
        )
        state.capabilities?.v1Supported == true -> InfoChip(
            label = "硬件编码",
            tone = TileTone.Brand,
            icon = Icons.Rounded.Bolt,
        )
    }
}

private fun progressDetail(state: UiState): String {
    val p = state.progress
    val warm = p.elapsedMs <= 400 || (p.framesEncoded == 0 && p.bytesWritten <= 0L)
    if (warm) return state.message ?: "准备中…"
    return buildList {
        if (p.framesEncoded > 0) add("%.0f fps".format(p.framesPerSecond))
        if (p.bytesWritten > 0) add("%.1f MB/s".format(p.megabytesPerSecond))
        if (p.etaMs > 0) add("剩余 " + formatEta(p.etaMs))
    }.joinToString(" · ").ifEmpty { state.message ?: "编码中…" }
}

@Composable
private fun ResultCards(
    state: UiState,
    onPreview: (Uri, MediaKind?, Int, Int) -> Unit,
    onOpen: (Uri) -> Unit,
    onShare: (Uri) -> Unit,
    onReplace: () -> Unit,
) {
    val palette = LocalIos27Palette.current
    val width = state.videoInfo?.displayWidth ?: state.imageInfo?.width ?: 16
    val height = state.videoInfo?.displayHeight ?: state.imageInfo?.height ?: 9
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val checkScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.3f,
        animationSpec = SnapAnimations.SuccessPop,
        label = "checkScale",
    )
    val saved = if (state.fileSizeBytes > 0 && state.outputSizeBytes > 0) {
        (1.0 - state.outputSizeBytes.toDouble() / state.fileSizeBytes) * 100
    } else {
        0.0
    }
    val output = state.outputUri
    val usable = output != null && !state.replaceRunning

    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = checkScale
                    scaleY = checkScale
                },
            ) {
                IconTile(
                    icon = Icons.Rounded.CheckCircle,
                    tone = TileTone.Green,
                    size = 40.dp,
                )
            }
            Spacer(Modifier.width(Ios27Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        state.replaceRunning -> "正在替换原文件…"
                        state.replacedOriginal -> "已替换原文件"
                        else -> "已保存"
                    },
                    style = Ios27Type.headline,
                    color = palette.label,
                )
                Text(
                    text = state.outputName.orEmpty(),
                    style = Ios27Type.footnote,
                    color = palette.labelSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Ios27Spacing.sm))
            EncoderChip(state)
        }
        Spacer(Modifier.height(Ios27Spacing.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatSize(state.fileSizeBytes) + " → " + formatSize(state.outputSizeBytes),
                style = Ios27Type.body,
                color = palette.label,
                modifier = Modifier.weight(1f),
            )
            if (saved > 0.5) {
                InfoChip(
                    label = "−" + "%.0f".format(saved) + "%",
                    tone = TileTone.Green,
                )
            } else if (saved < -0.5) {
                InfoChip(
                    label = "+" + "%.0f".format(-saved) + "%",
                    tone = TileTone.Yellow,
                )
            }
        }
        Spacer(Modifier.height(Ios27Spacing.xs))
        Text(
            text = resultDetail(state),
            style = Ios27Type.footnote,
            color = palette.labelSecondary,
        )
        Spacer(Modifier.height(Ios27Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Ios27Spacing.sm)) {
            ActionTile(
                label = "预览",
                icon = Icons.Rounded.Visibility,
                enabled = usable,
                modifier = Modifier.weight(1f),
            ) { output?.let { onPreview(it, state.kind, width, height) } }
            ActionTile(
                label = "打开",
                icon = Icons.Rounded.OpenInNew,
                enabled = usable,
                modifier = Modifier.weight(1f),
            ) { output?.let { onOpen(it) } }
            ActionTile(
                label = "分享",
                icon = Icons.Rounded.Share,
                enabled = usable,
                modifier = Modifier.weight(1f),
            ) { output?.let { onShare(it) } }
        }
    }

    val report = state.qualityReport
    if (state.qualityRunning || report != null) {
        ContentCard {
            CardHeader(
                title = "相对原片",
                icon = Icons.Rounded.Insights,
                subtitle = report?.let {
                    it.samples.toString() + " 帧 · " + it.compareWidth + "×" + it.compareHeight
                },
            )
            Spacer(Modifier.height(Ios27Spacing.md))
            if (report == null) {
                Text(
                    text = "正在比对 PSNR / SSIM / VMAF…",
                    style = Ios27Type.footnote,
                    color = palette.labelSecondary,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Ios27Spacing.sm)) {
                    report.vmaf?.let {
                        MetricTile(
                            value = "%.1f".format(it),
                            label = "VMAF " + report.vmafLabel,
                            emphasized = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    MetricTile(
                        value = "%.1f".format(report.psnrY),
                        label = "PSNR-Y dB",
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        value = "%.3f".format(report.ssim),
                        label = "SSIM " + report.ssimLabel,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    ContentCard(padding = PaddingValues(0.dp)) {
        ActionRow(
            title = if (state.kind == MediaKind.IMAGE) "替换原图" else "替换原视频",
            enabled = usable && !state.replacedOriginal,
            destructive = true,
            onClick = onReplace,
        )
    }
}

private fun resultDetail(state: UiState): String = buildList {
    add(state.outputCodecLabel())
    state.outputFolder?.takeIf { it.isNotBlank() }?.let { add(it) }
    val video = state.videoInfo
    if (video != null && video.bitrateBps > 0) {
        add("原片 " + sourceCodecLabel(video.mime) + " " + formatBitrate(video.bitrateBps))
    }
}.joinToString(" · ")

private fun UiState.outputCodecLabel(): String = when {
    audioOnly -> "M4A"
    kind == MediaKind.IMAGE -> imageCodecLabel(imageCodec)
    else -> videoCodecLabel(videoCodec)
}

/* ------------------------------------------------------------------- notices */

@Composable
private fun ErrorCard(message: String) {
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
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = palette.redText,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Ios27Spacing.md))
        Text(text = message, style = Ios27Type.footnote, color = palette.redText)
    }
}

@Composable
private fun UnsupportedCard() {
    ContentCard {
        InfoRow(
            title = "没有可用的高通硬件编码器",
            icon = Icons.Rounded.Memory,
            tone = TileTone.Yellow,
            subtitle = "V1 只支持高通硬件编码，不会退回 CPU 软编码。" +
                "编码器由运行时枚举 MediaCodecList 决定，不按 SoC 型号判断。",
        )
    }
}

/* -------------------------------------------------------------------- intents */

private fun openOutput(context: Context, uri: Uri, kind: MediaKind?) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, if (kind == MediaKind.IMAGE) "image/*" else "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, "output", uri)
    }
    context.startActivity(Intent.createChooser(intent, "打开"))
}

private fun shareOutput(context: Context, uri: Uri, kind: MediaKind?): Intent {
    val mime = if (kind == MediaKind.IMAGE) "image/*" else "video/mp4"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "output", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(intent, "分享")
}
