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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.media.MediaCodecInfo.EncoderCapabilities
import androidx.compose.foundation.text.KeyboardOptions
import com.snapconverter.app.ui.ConvertStage
import com.snapconverter.app.ui.JobViewModel
import com.snapconverter.app.ui.SaveFolder
import com.snapconverter.app.ui.UiState
import com.snapconverter.app.ui.components.Caption
import com.snapconverter.app.ui.components.ChipRow
import com.snapconverter.app.ui.components.HWTag
import com.snapconverter.app.ui.components.MetricChip
import com.snapconverter.app.ui.components.ScCard
import com.snapconverter.app.ui.components.ScChip
import com.snapconverter.app.ui.components.ScSlider
import com.snapconverter.app.ui.components.SectionLabel
import com.snapconverter.app.ui.components.SettingRow
import com.snapconverter.app.ui.components.StatPill
import com.snapconverter.app.ui.settings.AppSettings
import com.snapconverter.app.ui.settings.SettingsSheet
import com.snapconverter.app.ui.theme.SnapAnimations
import com.snapconverter.app.ui.theme.SnapDimensions
import com.snapconverter.app.ui.theme.bounceClick
import com.snapconverter.app.ui.theme.ios27.Ios27Backdrop
import com.snapconverter.app.ui.theme.ios27.LocalGlassPreset
import com.snapconverter.app.ui.theme.ios27.LocalIsDark
import com.snapconverter.app.ui.theme.ios27.liquidGlass
import com.snapconverter.engine.codec.CodecCandidate
import com.snapconverter.engine.codec.MimeTypes
import com.snapconverter.engine.policy.BitrateEstimator
import com.snapconverter.engine.policy.BitrateModeOption
import com.snapconverter.engine.policy.ComplexityOption
import com.snapconverter.engine.policy.CompressionMode
import com.snapconverter.engine.policy.MediaKind
import com.snapconverter.engine.policy.OutputFps
import com.snapconverter.engine.policy.OutputImageCodec
import com.snapconverter.engine.policy.OutputResolution
import com.snapconverter.engine.policy.OutputVideoCodec
import com.snapconverter.engine.policy.QualityStrategy
import com.snapconverter.engine.policy.VideoProfileOption
import com.snapconverter.engine.quality.ImageMetrics
import kotlin.math.abs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PreviewTarget(
    val uri: Uri,
    val kind: MediaKind?,
    val width: Int,
    val height: Int,
)

@Composable
fun HomeScreen(
    viewModel: JobViewModel,
    settings: AppSettings.Options,
    onSettingsChange: (AppSettings.Options) -> Unit,
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
    var showSettings by remember { mutableStateOf(false) }
    var showCapabilities by remember { mutableStateOf(false) }
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

    preview?.let { target ->
        MediaPreviewDialog(
            uri = target.uri,
            kind = target.kind,
            displayWidth = target.width,
            displayHeight = target.height,
            onDismiss = { preview = null },
        )
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

    if (showSettings) {
        SettingsSheet(
            options = settings,
            onChange = {
                onSettingsChange(it)
                AppSettings.save(context, it)
            },
            onOpenCapabilities = {
                showSettings = false
                showCapabilities = true
            },
            onDismiss = { showSettings = false },
        )
    }
    if (showCapabilities) {
        CapabilityScreen(
            report = state.capabilities,
            onDismiss = { showCapabilities = false },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // iOS 27 wallpaper mesh: Liquid Glass surfaces refract this backdrop.
        Ios27Backdrop()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = SnapDimensions.SpacingLg, vertical = SnapDimensions.SpacingSm),
            verticalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingMd),
        ) {
            HeaderBar(
                qualcomm = state.capabilities?.hasQualcommEncoder == true,
                onOpenSettings = { showSettings = true },
            )
            val idle = state.stage == ConvertStage.IDLE || state.stage == ConvertStage.LOADING
            AnimatedContent(
                targetState = idle,
                transitionSpec = {
                    fadeIn(tween(SnapAnimations.ContentFadeDuration)) togetherWith
                        fadeOut(tween(SnapAnimations.ContentFadeOutDuration))
                },
                label = "stage",
            ) { isIdle ->
                if (isIdle) {
                    HeroEmptyState(
                        loading = state.stage == ConvertStage.LOADING,
                        onPick = { picker.launch(arrayOf("video/*", "image/*")) },
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingMd),
                    ) {
                        MediaHero(
                            state,
                            onChange = {
                                viewModel.reset()
                                picker.launch(arrayOf("video/*", "image/*"))
                            },
                            onPreview = { uri, kind, w, h ->
                                preview = PreviewTarget(uri, kind, w, h)
                            },
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingMd),
                        ) {
                            if (state.stage != ConvertStage.DONE) {
                                SettingsPanel(state, viewModel, onPickFolder = { folderPicker.launch(null) })
                            }
                            if (state.stage == ConvertStage.RUNNING) ProgressCard(state)
                            if (state.stage == ConvertStage.DONE) {
                                ResultCard(
                                    state,
                                    onReset = { viewModel.reset() },
                                    onPreview = { uri, kind, w, h ->
                                        preview = PreviewTarget(uri, kind, w, h)
                                    },
                                    onReplace = { confirmReplace = true },
                                    onShare = { uri, kind ->
                                        shareLauncher.launch(shareOutput(context, uri, kind))
                                    },
                                )
                            }
                            if (state.capabilities?.v1Supported == false) {
                                UnsupportedBanner()
                            }
                        }
                        state.error?.let {
                            ErrorBanner(it)
                        }
                        if (state.stage == ConvertStage.READY) {
                            Button(
                                onClick = { viewModel.start() },
                                // Audio-only extraction is passthrough: no encoder gate.
                                enabled = state.audioOnly || state.capabilities?.v1Supported == true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(SnapDimensions.ButtonHeight),
                                shape = RoundedCornerShape(SnapDimensions.RadiusPill),
                                colors = ButtonDefaults.buttonColors(),
                            ) {
                                Text(
                                    if (state.audioOnly) "提取音频" else "开始转换",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderBar(qualcomm: Boolean, onOpenSettings: () -> Unit) {
    val dark = LocalIsDark.current
    val preset = LocalGlassPreset.current
    // iOS 27 uniform toolbar: one glass bar across the top edge.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SnapDimensions.RadiusGlass))
            .liquidGlass(
                shape = RoundedCornerShape(SnapDimensions.RadiusGlass),
                dark = dark,
                preset = preset,
            )
            .padding(horizontal = SnapDimensions.SpacingMd, vertical = SnapDimensions.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Brand mark: gradient tile with a bolt, echoing the hardware-first story.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(SnapDimensions.RadiusSm))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "SnapConverter",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.43).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "硬件优先 · 本地转换",
                fontSize = 11.sp,
                lineHeight = 13.sp,
                letterSpacing = 0.06.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (qualcomm) {
            HWTag("Qualcomm")
            Spacer(Modifier.width(SnapDimensions.SpacingSm))
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = "外观设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SnapDimensions.IconMd),
            )
        }
    }
}

/**
 * Signature empty state in the spirit of ZenConverter's hero "+": a large
 * gradient circle that invites the pick action, with the pipeline story below.
 */
@Composable
private fun HeroEmptyState(loading: Boolean, onPick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = SnapDimensions.SpacingXl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingMd),
        ) {
            Box(
                modifier = Modifier
                    .size(SnapDimensions.HeroButtonSize)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ),
                    )
                    // Brighter specular rim on the tinted circle (iOS 27).
                    .drawBehind {
                        drawCircle(
                            color = Color(0x66FFFFFF),
                            radius = size.minDimension / 2f - 1.dp.toPx(),
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                    .bounceClick(enabled = !loading, onClick = onPick),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(34.dp),
                    )
                } else {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "选择文件",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Text(
                if (loading) "读取中…" else "选择照片或视频",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "视频 H.265 / H.264 · 照片 HEIC · 全程硬件编解码",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HWTag("硬件解码")
                HWTag("GPU 处理")
                HWTag("硬件编码")
            }
        }
    }
}

@Composable
private fun UnsupportedBanner() {
    ScCard {
        Row(
            modifier = Modifier.padding(SnapDimensions.SpacingMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Memory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(SnapDimensions.IconMd),
            )
            Spacer(Modifier.width(SnapDimensions.SpacingSm))
            Text(
                "此设备没有可用的高通硬件编码器，V1 暂不支持。编码器由运行时枚举决定。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        shape = RoundedCornerShape(SnapDimensions.RadiusSm),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SnapDimensions.SpacingMd, vertical = SnapDimensions.SpacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(SnapDimensions.IconSm),
            )
            Spacer(Modifier.width(SnapDimensions.SpacingSm))
            Text(
                message,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MediaHero(
    state: UiState,
    onChange: () -> Unit,
    onPreview: (Uri, MediaKind?, Int, Int) -> Unit,
) {
    val uri = state.input
    val previewW = state.videoInfo?.displayWidth ?: state.imageInfo?.width ?: 16
    val previewH = state.videoInfo?.displayHeight ?: state.imageInfo?.height ?: 9
    val running = state.stage == ConvertStage.RUNNING
    ScCard {
        Row(
            modifier = Modifier.padding(SnapDimensions.SpacingMd),
            horizontalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingMd),
            verticalAlignment = Alignment.Top,
        ) {
            if (uri != null) {
                Box(modifier = Modifier.clip(RoundedCornerShape(SnapDimensions.RadiusMd))) {
                    AspectMediaThumb(
                        uri = uri,
                        kind = state.kind,
                        displayWidth = previewW,
                        displayHeight = previewH,
                        onClick = { onPreview(uri, state.kind, previewW, previewH) },
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    state.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        val video = state.videoInfo
                        val image = state.imageInfo
                        if (video != null) {
                            append(video.displayWidth.toString() + "×" + video.displayHeight)
                            append(if (video.isPortrait) " · 竖屏" else " · 横屏")
                            append(" · " + formatDuration(video.durationUs))
                        } else if (image != null) {
                            append(image.width.toString() + "×" + image.height)
                        }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(formatSize(state.fileSizeBytes))
                        val v = state.videoInfo
                        if (v != null && v.bitrateBps > 0) {
                            append(" · " + sourceCodecLabel(v.mime) + " " + formatBitrate(v.bitrateBps))
                        }
                        append(" · ")
                        append(state.captureTimeMs?.let { formatCapture(it) } ?: "无拍摄时间")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    state.videoInfo?.let {
                        ColorTag(it.hdrLabel, it.isHdr || it.tenBit)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        "更换",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (running) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.bounceClick(enabled = !running, onClick = onChange),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(state: UiState, vm: JobViewModel, onPickFolder: () -> Unit) {
    val locked = state.stage == ConvertStage.RUNNING
    ScCard {
        Column(
            modifier = Modifier.padding(SnapDimensions.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingSm),
        ) {
            if (state.kind == MediaKind.VIDEO) {
                SectionLabel("输出内容")
                ChipRow {
                    ScChip("视频", !state.audioOnly, !locked) { vm.setAudioOnly(false) }
                    ScChip("仅音频", state.audioOnly, !locked) { vm.setAudioOnly(true) }
                }
                if (state.audioOnly) {
                    val audioLabel = state.videoInfo?.audioMime?.substringAfter('/')?.uppercase() ?: "—"
                    Caption(
                        "直通复制音轨（" + audioLabel + "）到 M4A，不重新编码：无损、极快，也不经过任何编码器。",
                    )
                }
            }
            if (state.kind == MediaKind.VIDEO && !state.audioOnly) {
                SectionLabel("输出格式")
                ChipRow {
                    ScChip("H.265", state.videoCodec == OutputVideoCodec.HEVC, !locked && state.capabilities?.hardwareHevcEncoder == true) {
                        vm.setVideoCodec(OutputVideoCodec.HEVC)
                    }
                    ScChip("H.264", state.videoCodec == OutputVideoCodec.AVC, !locked && state.capabilities?.hardwareAvcEncoder == true) {
                        vm.setVideoCodec(OutputVideoCodec.AVC)
                    }
                    if (state.capabilities?.hardwareAv1Encoder == true) {
                        ScChip("AV1", state.videoCodec == OutputVideoCodec.AV1, !locked) {
                            vm.setVideoCodec(OutputVideoCodec.AV1)
                        }
                    }
                }
                SectionLabel("压缩模式")
                ChipRow {
                    ScChip("画质", state.mode == CompressionMode.QUALITY, !locked) { vm.setMode(CompressionMode.QUALITY) }
                    ScChip("目标大小", state.mode == CompressionMode.TARGET_SIZE, !locked) { vm.setMode(CompressionMode.TARGET_SIZE) }
                    ScChip(
                        "目标 VMAF",
                        state.mode == CompressionMode.TARGET_VMAF,
                        !locked && state.capabilities?.vmafAvailable == true,
                    ) {
                        vm.setMode(CompressionMode.TARGET_VMAF)
                    }
                }
            } else if (state.kind == MediaKind.IMAGE) {
                SectionLabel("输出格式")
                ChipRow {
                    ScChip("HEIC", state.imageCodec == OutputImageCodec.HEIC, !locked) {
                        vm.setImageCodec(OutputImageCodec.HEIC)
                    }
                    ScChip(
                        "AVIF",
                        state.imageCodec == OutputImageCodec.AVIF,
                        !locked && state.capabilities?.hardwareAv1Encoder == true && Build.VERSION.SDK_INT >= 31,
                    ) { vm.setImageCodec(OutputImageCodec.AVIF) }
                    ScChip(
                        "JPEG",
                        state.imageCodec == OutputImageCodec.JPEG,
                        !locked && state.capabilities?.hardwareJpegEncoder == true,
                    ) { vm.setImageCodec(OutputImageCodec.JPEG) }
                }
                val caps = state.capabilities
                if (state.imageCodec == OutputImageCodec.AVIF) {
                    Caption("AVIF：硬件 AV1 单帧编码，体积通常比 HEIC 更小。")
                }
                if (caps?.hardwareJpegEncoder != true) {
                    Caption(
                        "JPEG 需要设备公开硬件 JPEG 编码器；本机未枚举到，为避免 CPU 软压缩已禁用。可选 HEIC 或 AVIF。",
                    )
                }
                ImageExportSizeSection(state, vm, locked)
            }

            if (!state.audioOnly &&
                (state.kind != MediaKind.VIDEO ||
                    state.bitrateMode == BitrateModeOption.AUTO ||
                    state.mode == CompressionMode.TARGET_VMAF)
            ) {
                when (state.mode) {
                    CompressionMode.QUALITY, CompressionMode.LOSSLESS_REMUX -> {
                        SectionLabel("画质  " + state.quality)
                        ScSlider(state.quality.toFloat(), 0f..100f, !locked) { vm.setQuality(it.toInt()) }
                        Caption(qualityModeHint(state))
                    }
                    CompressionMode.TARGET_SIZE -> {
                        SectionLabel(
                            "目标大小 · 原文件的 " + "%.0f".format(state.targetSizeRatio * 100) + "%" +
                                "  ≈ " + formatSize(vm.targetSizeBytes(state)),
                        )
                        ScSlider(
                            state.targetSizeRatio,
                            0.05f..1.5f,
                            !locked,
                            steps = 28,
                            onChange = { r -> vm.setTargetSizeRatio(r) },
                        )
                        Caption(
                            "范围 5%–150%，150% 是硬上限。短或小文件受音频直通与编码器最低码率限制，结果可能略大于目标。",
                        )
                    }
                    CompressionMode.TARGET_VMAF -> {
                        SectionLabel(
                            "目标 VMAF  " + "%.0f".format(state.targetVmaf) + "  " +
                                ImageMetrics.vmafLabel(state.targetVmaf.toDouble()),
                        )
                        ScSlider(state.targetVmaf, 60f..98f, !locked) { vm.setTargetVmaf(it) }
                        ChipRow {
                            listOf(80f, 90f, 95f).forEach { v ->
                                ScChip("%.0f".format(v), abs(state.targetVmaf - v) < 0.5f, !locked) {
                                    vm.setTargetVmaf(v)
                                }
                            }
                        }
                        Caption("先用 Netflix VMAF 标定最低码率，再整片编码")
                    }
                    CompressionMode.TARGET_BITRATE, CompressionMode.TARGET_SSIM -> Unit
                }
            }

            if (!state.audioOnly) {
                SectionLabel("分辨率")
                ChipRow {
                    listOf(
                        OutputResolution.ORIGINAL to "原始",
                        OutputResolution.UHD_2160 to "2160p",
                        OutputResolution.QHD_1440 to "1440p",
                        OutputResolution.FHD_1080 to "1080p",
                        OutputResolution.HD_720 to "720p",
                    ).forEach { (v, t) ->
                        ScChip(t, state.resolution == v, !locked) { vm.setResolution(v) }
                    }
                }
            }
            if (state.kind == MediaKind.VIDEO && !state.audioOnly) {
                SectionLabel("帧率")
                ChipRow {
                    listOf(
                        OutputFps.ORIGINAL to "原始",
                        OutputFps.FPS_60 to "60",
                        OutputFps.FPS_30 to "30",
                        OutputFps.FPS_24 to "24",
                    ).forEach { (v, t) ->
                        ScChip(t, state.fps == v, !locked) { vm.setFps(v) }
                    }
                }
            }

            if (state.kind == MediaKind.VIDEO && !state.audioOnly) {
                TrimSection(state, vm, locked)
                SettingRow("静音输出（去除音轨）") {
                    Switch(
                        checked = state.muteAudio,
                        onCheckedChange = { vm.setMuteAudio(it) },
                        enabled = !locked,
                    )
                }
            }

            SettingRow("保留拍摄时间") {
                Switch(
                    checked = state.preserveCaptureTime,
                    onCheckedChange = { vm.setPreserveCaptureTime(it) },
                    enabled = !locked,
                )
            }
            SectionLabel("保存位置")
            ChipRow {
                listOf(SaveFolder.APP, SaveFolder.CAMERA, SaveFolder.ROOT, SaveFolder.DOWNLOAD, SaveFolder.CUSTOM).forEach { folder ->
                    ScChip(
                        folder.chipLabel(state.kind),
                        state.saveFolder == folder,
                        !locked,
                    ) {
                        if (folder == SaveFolder.CUSTOM) {
                            if (state.customTreeUri == null) onPickFolder()
                            else vm.setSaveFolder(SaveFolder.CUSTOM)
                        } else {
                            vm.setSaveFolder(folder)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.savePathLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "更改",
                    color = if (locked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontSize = 11.sp,
                    modifier = Modifier.bounceClick(enabled = !locked, onClick = onPickFolder),
                )
            }

            if (state.kind == MediaKind.VIDEO && !state.audioOnly) {
                AdvancedHeader(state, vm, locked)
            }
        }
    }
}

/** Precise trim controls: the window is re-encoded through the hardware pipeline. */
@Composable
private fun TrimSection(state: UiState, vm: JobViewModel, locked: Boolean) {
    val dur = state.durationSec
    if (dur <= 1) return
    val end = if (state.trimEndSec < 0) dur else state.trimEndSec
    val clip = (end - state.trimStartSec).coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(
            "片段裁剪 · 保留 " + clip + "s / " + dur + "s",
            modifier = Modifier.weight(1f),
        )
        Text(
            "重置",
            fontSize = 11.sp,
            color = if (state.trimActive && !locked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
            modifier = Modifier.bounceClick(enabled = state.trimActive && !locked) { vm.resetTrim() },
        )
    }
    SectionLabel("起点  " + formatSec(state.trimStartSec))
    ScSlider(
        value = state.trimStartSec.toFloat(),
        range = 0f..(end - 1).toFloat(),
        enabled = !locked,
    ) { vm.setTrimStartSec(it.toInt()) }
    SectionLabel("终点  " + formatSec(end))
    ScSlider(
        value = end.toFloat(),
        range = (state.trimStartSec + 1).toFloat()..dur.toFloat(),
        enabled = !locked,
    ) { vm.setTrimEndSec(it.toInt()) }
    Caption(
        if (state.trimActive) {
            "硬件管线精剪：仅重编码所选区间，音轨同步裁剪，起点精确到帧。"
        } else {
            "默认转换完整片段；拖动滑杆可精确截取一段。"
        },
    )
}

@Composable
private fun AdvancedHeader(state: UiState, vm: JobViewModel, locked: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (state.advancedOpen) 180f else 0f,
        animationSpec = SnapAnimations.ChevronRotationSpring,
        label = "chevron",
    )
    Column(verticalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingSm)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bounceClick(enabled = !locked) { vm.setAdvancedOpen(!state.advancedOpen) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                advancedTitle(state),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = rotation },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = state.advancedOpen,
            enter = SnapAnimations.PanelEnter,
            exit = SnapAnimations.PanelExit,
        ) {
            AdvancedParams(state, vm, locked)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedParams(state: UiState, vm: JobViewModel, locked: Boolean) {
    val encoder = selectedVideoEncoder(state)
    val complexityOk = encoder?.complexityRange != null
    val nativeCq = encoder != null &&
        encoder.supportsBitrateMode(EncoderCapabilities.BITRATE_MODE_CQ) &&
        encoder.qualityRange != null
    Column(verticalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingSm)) {
        SectionLabel("编码目标")
        ChipRow {
            ScChip("码率", state.mode == CompressionMode.TARGET_BITRATE, !locked) {
                vm.setMode(CompressionMode.TARGET_BITRATE)
            }
            ScChip("目标 SSIM", state.mode == CompressionMode.TARGET_SSIM, !locked) {
                vm.setMode(CompressionMode.TARGET_SSIM)
            }
        }
        when (state.mode) {
            CompressionMode.TARGET_BITRATE -> {
                SectionLabel("Bitrate  " + state.targetBitrateKbps + " kbps")
                ScSlider(state.targetBitrateKbps.toFloat(), 200f..20000f, !locked) {
                    vm.setTargetBitrateKbps(it.toInt().coerceIn(200, 40000))
                }
            }
            CompressionMode.TARGET_SSIM -> {
                SectionLabel("目标 SSIM  " + "%.3f".format(state.targetSsim) + "  " + ImageMetrics.ssimLabel(state.targetSsim.toDouble()))
                ScSlider(state.targetSsim, 0.80f..0.99f, !locked) { vm.setTargetSsim(it) }
                ChipRow {
                    listOf(0.90f, 0.95f, 0.99f).forEach { v ->
                        ScChip("%.2f".format(v), abs(state.targetSsim - v) < 0.005f, !locked) {
                            vm.setTargetSsim(v)
                        }
                    }
                }
                Caption("先标定最低码率，再整片编码")
            }
            else -> Unit
        }
        SectionLabel("码率模式")
        ChipRow {
            ScChip("Auto", state.bitrateMode == BitrateModeOption.AUTO, !locked) { vm.setBitrateMode(BitrateModeOption.AUTO) }
            ScChip("VBR", state.bitrateMode == BitrateModeOption.VBR, !locked) { vm.setBitrateMode(BitrateModeOption.VBR) }
            ScChip("CBR", state.bitrateMode == BitrateModeOption.CBR, !locked) { vm.setBitrateMode(BitrateModeOption.CBR) }
            ScChip("CQ", state.bitrateMode == BitrateModeOption.CQ, !locked) { vm.setBitrateMode(BitrateModeOption.CQ) }
        }
        when (state.bitrateMode) {
            BitrateModeOption.AUTO -> Caption("Auto：跟随上方 画质 / 目标大小 / 目标 VMAF")
            BitrateModeOption.VBR -> {
                SectionLabel("Bitrate（平均）  " + state.targetBitrateKbps + " kbps")
                ScSlider(state.targetBitrateKbps.toFloat(), 200f..40000f, !locked) {
                    vm.setTargetBitrateKbps(it.toInt().coerceIn(200, 40000))
                }
                SectionLabel("Max bitrate（峰值）  " + state.maxBitrateKbps + " kbps")
                ScSlider(state.maxBitrateKbps.toFloat(), 200f..80000f, !locked) {
                    vm.setMaxBitrateKbps(it.toInt().coerceIn(200, 80000))
                }
            }
            BitrateModeOption.CBR -> {
                SectionLabel("Bitrate（恒定）  " + state.targetBitrateKbps + " kbps")
                ScSlider(state.targetBitrateKbps.toFloat(), 200f..40000f, !locked) {
                    vm.setTargetBitrateKbps(it.toInt().coerceIn(200, 40000))
                }
            }
            BitrateModeOption.CQ -> {
                SectionLabel("画质  " + state.quality)
                ScSlider(state.quality.toFloat(), 0f..100f, !locked) { vm.setQuality(it.toInt()) }
                Caption(
                    if (nativeCq) {
                        val range = encoder?.qualityRange
                        val mapped = range?.let { QualityStrategy.mapToCodecQuality(state.quality, it) }
                        "CQ 恒定质量，KEY_QUALITY=" + (mapped ?: state.quality) +
                            "（范围 " + (range?.first ?: 0) + "–" + (range?.last ?: 100) + "），码率随画面变"
                    } else {
                        val qp = QualityStrategy.qpWindowForQuality(state.quality)
                        "该编码器无 CQ，用 QP I " + qp.iMin + "–" + qp.iMax +
                            " / P " + qp.pMin + "–" + qp.pMax + " 近似恒定质量"
                    },
                )
            }
        }
        SectionLabel("I-frame interval")
        ChipRow {
            ScChip("Auto", state.iFrameIntervalSec == null, !locked) { vm.setIFrameIntervalSec(null) }
            listOf(1, 2, 3, 5, 10).forEach { s ->
                ScChip(s.toString() + "s", state.iFrameIntervalSec == s, !locked) { vm.setIFrameIntervalSec(s) }
            }
        }
        SectionLabel("B-frames")
        ChipRow {
            val baseline = state.profile == VideoProfileOption.BASELINE
            ScChip("Auto", state.maxBFrames == null && !baseline, !locked && !baseline) { vm.setMaxBFrames(null) }
            listOf(0, 1, 2, 3).forEach { n ->
                ScChip(n.toString(), state.maxBFrames == n || (baseline && n == 0), !locked && !baseline) {
                    vm.setMaxBFrames(n)
                }
            }
        }
        SectionLabel("Profile")
        ChipRow {
            ScChip("Auto", state.profile == VideoProfileOption.AUTO, !locked) { vm.setProfile(VideoProfileOption.AUTO) }
            if (state.videoCodec == OutputVideoCodec.AVC) {
                ScChip("Baseline", state.profile == VideoProfileOption.BASELINE, !locked) { vm.setProfile(VideoProfileOption.BASELINE) }
                ScChip("Main", state.profile == VideoProfileOption.MAIN, !locked) { vm.setProfile(VideoProfileOption.MAIN) }
                ScChip("High", state.profile == VideoProfileOption.HIGH, !locked) { vm.setProfile(VideoProfileOption.HIGH) }
            } else {
                ScChip("Main", state.profile == VideoProfileOption.MAIN, !locked) { vm.setProfile(VideoProfileOption.MAIN) }
                ScChip("Main10", state.profile == VideoProfileOption.MAIN10, !locked) { vm.setProfile(VideoProfileOption.MAIN10) }
            }
        }
        SectionLabel("Complexity")
        ChipRow {
            ScChip("Auto", state.complexity == ComplexityOption.AUTO, !locked) { vm.setComplexity(ComplexityOption.AUTO) }
            ScChip("Low", state.complexity == ComplexityOption.LOW, !locked && complexityOk) { vm.setComplexity(ComplexityOption.LOW) }
            ScChip("Medium", state.complexity == ComplexityOption.MEDIUM, !locked && complexityOk) { vm.setComplexity(ComplexityOption.MEDIUM) }
            ScChip("High", state.complexity == ComplexityOption.HIGH, !locked && complexityOk) { vm.setComplexity(ComplexityOption.HIGH) }
        }
        if (!complexityOk) Caption("此编码器不暴露 Complexity")
        SectionLabel("QP")
        ChipRow {
            ScChip("Auto", state.qpIMin == null, !locked) { vm.setQpCustom(false) }
            ScChip("Custom", state.qpIMin != null, !locked) { vm.setQpCustom(true) }
        }
        if (state.qpIMin != null && state.qpIMax != null && state.qpPMin != null && state.qpPMax != null) {
            SectionLabel("QP I min  " + state.qpIMin)
            ScSlider(state.qpIMin.toFloat(), 1f..51f, !locked) { vm.setQpIMin(it.toInt()) }
            SectionLabel("QP I max  " + state.qpIMax)
            ScSlider(state.qpIMax.toFloat(), 1f..51f, !locked) { vm.setQpIMax(it.toInt()) }
            SectionLabel("QP P min  " + state.qpPMin)
            ScSlider(state.qpPMin.toFloat(), 1f..51f, !locked) { vm.setQpPMin(it.toInt()) }
            SectionLabel("QP P max  " + state.qpPMax)
            ScSlider(state.qpPMax.toFloat(), 1f..51f, !locked) { vm.setQpPMax(it.toInt()) }
        }
    }
}

@Composable
private fun HardwareBadge(state: UiState) {
    val caps = state.capabilities ?: return
    if (!caps.v1Supported) return
    val codec = if (state.kind == MediaKind.IMAGE) {
        if (state.imageCodec == OutputImageCodec.JPEG && caps.hardwareJpegEncoder) "JPEG"
        else "HEIC"
    } else {
        when (state.videoCodec) {
            OutputVideoCodec.HEVC -> "HEVC"
            OutputVideoCodec.AVC -> "H.264"
            OutputVideoCodec.AV1 -> "AV1"
        }
    }
    HWTag("硬件加速 · " + codec)
}

private fun qualityModeHint(state: UiState): String {
    if (state.kind != MediaKind.VIDEO) {
        return if (state.imageCodec == OutputImageCodec.HEIC) {
            "写入 HEIC 的 quality，走硬件 HEVC still"
        } else {
            "写入硬件 JPEG 的 quality"
        }
    }
    val encoder = selectedVideoEncoder(state) ?: return "正在读取编码器能力…"
    val anchor = QualityStrategy.interpolate(state.quality)
    val cq = encoder.supportsBitrateMode(EncoderCapabilities.BITRATE_MODE_CQ) &&
        encoder.qualityRange != null
    val useCq = state.bitrateMode == BitrateModeOption.CQ
    val video = state.videoInfo
    val srcW = video?.displayWidth ?: 1920
    val srcH = video?.displayHeight ?: 1080
    val resCap = when (state.resolution) {
        OutputResolution.ORIGINAL -> Int.MAX_VALUE
        OutputResolution.UHD_2160 -> 3840
        OutputResolution.QHD_1440 -> 2560
        OutputResolution.FHD_1080 -> 1920
        OutputResolution.HD_720 -> 1280
    }
    val qualityCap = if (state.resolution == OutputResolution.ORIGINAL) {
        anchor.maxLongEdge
    } else {
        Int.MAX_VALUE
    }
    val cap = minOf(resCap, qualityCap)
    val long = maxOf(srcW, srcH).coerceAtLeast(1)
    val scale = if (long > cap) cap.toDouble() / long else 1.0
    val outW = ((srcW * scale).toInt() / 2 * 2).coerceAtLeast(2)
    val outH = ((srcH * scale).toInt() / 2 * 2).coerceAtLeast(2)
    val mime = when (state.videoCodec) {
        OutputVideoCodec.HEVC -> MimeTypes.HEVC
        OutputVideoCodec.AVC -> MimeTypes.AVC
        OutputVideoCodec.AV1 -> MimeTypes.AV1
    }
    val vbrKbps = run {
        val model = QualityStrategy.scaleBitrateForPixels(
            anchor.bitrate1080pHevcBps, outW, outH, mime,
        ) / 1000
        val bitrateCap = state.videoInfo?.bitrateBps?.takeIf { it > 0 }?.let { (it * 3L / 2L).toInt() / 1000 } ?: Int.MAX_VALUE
        minOf(model, bitrateCap)
    }
    val qualityRange = encoder.qualityRange
    val main = if (useCq && cq && qualityRange != null) {
        val mapped = QualityStrategy.mapToCodecQuality(state.quality, qualityRange)
        "CQ 恒定质量 KEY_QUALITY=" + mapped + "（范围 " + qualityRange.first + "–" + qualityRange.last +
            "），码率随画面复杂度自动变化"
    } else {
        val hdr = if (state.videoInfo?.isHdr == true) "HDR（码率 ×1.5） · " else ""
        val durationSec = (state.videoInfo?.durationUs ?: 0L) / 1_000_000.0
        val audio = state.videoInfo?.audioBitrateBps?.takeIf { it > 0 }
            ?: BitrateEstimator.DEFAULT_AUDIO_BITRATE_BPS
        val sizeNote = if (durationSec > 0.05) {
            val est = BitrateEstimator.estimatedFileBytes(vbrKbps * 1000, durationSec, audio)
            "，预计输出约 " + formatSize(est)
        } else {
            ""
        }
        hdr + "画质 " + state.quality + "（0–100）→ 预计平均码率 " + vbrKbps + " kbps" + sizeNote
    }
    val capNote = if (state.resolution == OutputResolution.ORIGINAL && long > anchor.maxLongEdge) {
        "；分辨率「原始」会被画质档限制到 " + outW + "×" + outH
    } else {
        ""
    }
    return main + capNote
}

private fun selectedVideoEncoder(state: UiState): CodecCandidate? {
    val mime = when (state.videoCodec) {
        OutputVideoCodec.HEVC -> MimeTypes.HEVC
        OutputVideoCodec.AVC -> MimeTypes.AVC
        OutputVideoCodec.AV1 -> MimeTypes.AV1
    }
    return state.capabilities?.encoders?.firstOrNull {
        it.isEncoder && it.mime.equals(mime, ignoreCase = true)
    }
}

private fun advancedTitle(state: UiState): String = when {
    state.mode == CompressionMode.TARGET_BITRATE -> "高级参数 · 码率"
    state.mode == CompressionMode.TARGET_SSIM -> "高级参数 · 目标 SSIM"
    state.bitrateMode != BitrateModeOption.AUTO -> "高级参数 · " + state.bitrateMode.name
    else -> "高级参数"
}

@Composable
private fun ColorTag(label: String, hdr: Boolean) {
    Text(
        label,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (hdr) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (hdr) {
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun ProgressCard(state: UiState) {
    val p = state.progress
    ScCard {
        Column(
            modifier = Modifier.padding(SnapDimensions.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingSm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (p.ratio * 100).toInt().toString() + "%",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.capabilities?.v1Supported == true) HWTag("硬件加速")
            }
            LinearProgressIndicator(
                progress = { p.ratio.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (p.elapsedMs > 400 && p.framesEncoded > 0) {
                    StatPill("%.0f fps".format(p.framesPerSecond))
                }
                if (p.elapsedMs > 400 && p.bytesWritten > 0) {
                    StatPill("%.1f MB/s".format(p.megabytesPerSecond))
                }
                val eta = p.etaMs
                if (eta > 0) {
                    StatPill("ETA " + formatEta(eta))
                }
                if (p.elapsedMs <= 400 || (p.framesEncoded == 0 && p.bytesWritten <= 0L)) {
                    StatPill(state.message ?: "准备中…")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(
    state: UiState,
    onReset: () -> Unit,
    onPreview: (Uri, MediaKind?, Int, Int) -> Unit,
    onReplace: () -> Unit,
    onShare: (Uri, MediaKind?) -> Unit,
) {
    val context = LocalContext.current
    val previewW = state.videoInfo?.displayWidth ?: state.imageInfo?.width ?: 16
    val previewH = state.videoInfo?.displayHeight ?: state.imageInfo?.height ?: 9
    val replaceLabel = if (state.kind == MediaKind.IMAGE) "替换原图" else "替换原视频"
    // Success pop: the check icon scales in with a bouncy spring.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val checkScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.3f,
        animationSpec = SnapAnimations.SuccessPopSpring,
        label = "checkScale",
    )
    ScCard {
        Column(
            modifier = Modifier.padding(SnapDimensions.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingSm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                        },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        state.replaceRunning -> "正在替换…"
                        state.replacedOriginal -> "已替换原文件"
                        else -> "已保存"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(8.dp))
                HardwareBadge(state)
            }
            Text(
                (state.outputFolder.orEmpty()) + "/" + (state.outputName.orEmpty()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatSize(state.fileSizeBytes),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatSize(state.outputSizeBytes),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (state.fileSizeBytes > 0 && state.outputSizeBytes > 0) {
                    Spacer(Modifier.width(8.dp))
                    val saved = (1.0 - state.outputSizeBytes.toDouble() / state.fileSizeBytes) * 100
                    if (saved > 0.5) {
                        Text(
                            "↓ " + "%.0f".format(saved) + "%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(SnapDimensions.RadiusPill))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            val v = state.videoInfo
            if (v != null && v.bitrateBps > 0) {
                Caption("原片 " + sourceCodecLabel(v.mime) + " " + formatBitrate(v.bitrateBps))
            }
            if (state.preserveCaptureTime && state.captureTimeMs != null) {
                Caption("拍摄时间 " + formatCapture(state.captureTimeMs))
            }
            QualityBlock(state)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingSm),
                verticalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingXs),
            ) {
                PillAction(
                    label = "预览",
                    enabled = state.outputUri != null && !state.replaceRunning,
                ) {
                    state.outputUri?.let { onPreview(it, state.kind, previewW, previewH) }
                }
                PillAction(
                    label = "打开",
                    enabled = state.outputUri != null && !state.replaceRunning,
                ) {
                    state.outputUri?.let { openOutput(context, it, state.kind) }
                }
                PillAction(
                    label = "分享",
                    icon = { Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    enabled = state.outputUri != null && !state.replaceRunning,
                ) {
                    state.outputUri?.let { onShare(it, state.kind) }
                }
                PillAction(
                    label = replaceLabel,
                    enabled = !state.replacedOriginal && !state.replaceRunning && state.outputUri != null,
                    onClick = onReplace,
                )
                TextButton(onClick = onReset, enabled = !state.replaceRunning) { Text("再转一个") }
            }
        }
    }
}

@Composable
private fun PillAction(
    label: String,
    enabled: Boolean,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val dark = LocalIsDark.current
    val preset = LocalGlassPreset.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(SnapDimensions.RadiusPill))
            .liquidGlass(
                shape = RoundedCornerShape(SnapDimensions.RadiusPill),
                dark = dark,
                preset = preset,
                shadowElevation = 0.dp,
            )
            .bounceClick(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.invoke()
            if (icon != null) Spacer(Modifier.width(4.dp))
            Text(
                label,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QualityBlock(state: UiState) {
    when {
        state.qualityRunning -> Caption("对比原片：PSNR / SSIM / VMAF…")
        state.qualityReport != null -> {
            val q = state.qualityReport
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("相对原片")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingSm),
                    verticalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingXs),
                ) {
                    q.vmaf?.let {
                        MetricChip(
                            value = "%.1f".format(it),
                            label = "VMAF " + q.vmafLabel,
                            highlight = true,
                        )
                    }
                    MetricChip(
                        value = "%.1f".format(q.psnrY) + " dB",
                        label = "PSNR-Y " + q.psnrLabel,
                    )
                    MetricChip(
                        value = "%.3f".format(q.ssim),
                        label = "SSIM " + q.ssimLabel,
                    )
                }
                Caption(q.samples.toString() + " 帧 · " + q.compareWidth + "×" + q.compareHeight)
            }
        }
    }
}

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

private fun formatCapture(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

private fun formatDuration(durationUs: Long): String {
    val sec = durationUs / 1_000_000.0
    return if (sec < 60) "%.1fs".format(Locale.US, sec)
    else "%d:%02d".format(Locale.US, (sec / 60).toInt(), (sec % 60).toInt())
}

private fun sourceCodecLabel(mime: String): String = when {
    mime.contains("hevc", ignoreCase = true) || mime.contains("heif", ignoreCase = true) -> "HEVC"
    mime.contains("avc", ignoreCase = true) -> "H.264"
    mime.contains("av01", ignoreCase = true) || mime.contains("av1", ignoreCase = true) -> "AV1"
    mime.contains("prores", ignoreCase = true) -> "ProRes"
    else -> mime.substringAfter('/').uppercase()
}

private fun formatBitrate(bps: Number): String {
    val v = bps.toLong()
    return when {
        v >= 100_000_000 -> "%.1f Gbps".format(Locale.US, v / 1_000_000_000.0)
        v >= 1_000_000 -> "%.1f Mbps".format(Locale.US, v / 1_000_000.0)
        else -> "%d kbps".format(Locale.US, (v + 500) / 1000)
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> "%.2f GB".format(Locale.US, gb)
        mb >= 1 -> "%.1f MB".format(Locale.US, mb)
        else -> "%.0f KB".format(Locale.US, kb)
    }
}

private fun formatSec(sec: Int): String {
    return if (sec < 60) sec.toString() + "s" else (sec / 60).toString() + ":" +
        (sec % 60).toString().padStart(2, '0')
}

private fun formatEta(ms: Long): String {
    val sec = (ms / 1000.0).toInt().coerceAtLeast(1)
    return if (sec < 60) sec.toString() + "s" else (sec / 60).toString() + "m" + (sec % 60) + "s"
}

/** Photoshop-style export sizing: presets, custom W/H with aspect lock, quick scale. */
@Composable
private fun ImageExportSizeSection(state: UiState, vm: JobViewModel, locked: Boolean) {
    val src = state.imageInfo ?: return
    val customActive = state.imageCustomWidth != null && state.imageCustomHeight != null
    val outW = state.imageCustomWidth ?: 0
    val outH = state.imageCustomHeight ?: 0
    SectionLabel(
        "尺寸  " + src.width + "×" + src.height + " → " +
            if (customActive) outW.toString() + "×" + outH else "预设",
    )
    ChipRow {
        listOf(
            OutputResolution.ORIGINAL to "原始",
            OutputResolution.UHD_2160 to "2160p",
            OutputResolution.QHD_1440 to "1440p",
            OutputResolution.FHD_1080 to "1080p",
            OutputResolution.HD_720 to "720p",
        ).forEach { (v, t) ->
            ScChip(t, state.resolution == v && !customActive, !locked) { vm.setResolution(v) }
        }
        ScChip("自定义", customActive, !locked) {
            vm.setImageCustomWidth((src.width / 2 * 2).coerceIn(16, 8192))
            vm.setImageCustomHeight((src.height / 2 * 2).coerceIn(16, 8192))
        }
    }
    if (customActive) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingSm),
        ) {
            SizeField(
                label = "宽",
                value = outW,
                enabled = !locked,
                modifier = Modifier.weight(1f),
            ) { vm.setImageCustomWidth(it) }
            Text("×", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SizeField(
                label = "高",
                value = outH,
                enabled = !locked,
                modifier = Modifier.weight(1f),
            ) { vm.setImageCustomHeight(it) }
            IconButton(
                onClick = { vm.setImageAspectLock(!state.imageAspectLock) },
                enabled = !locked,
            ) {
                Icon(
                    if (state.imageAspectLock) Icons.Rounded.Link else Icons.Rounded.LinkOff,
                    contentDescription = if (state.imageAspectLock) "锁定宽高比" else "宽高比已解锁",
                    tint = if (state.imageAspectLock) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        ChipRow {
            listOf(100, 75, 50, 25).forEach { p ->
                ScChip(p.toString() + "%", false, !locked) { vm.applyImageScale(p) }
            }
        }
        Caption(
            if (state.imageAspectLock) {
                "已锁定宽高比；GPU 重采样输出 " + outW + "×" + outH + "（自动对齐偶数）。"
            } else {
                "宽高比已解锁，可任意拉伸；GPU 重采样输出 " + outW + "×" + outH + "。"
            },
        )
    }
}

@Composable
private fun SizeField(
    label: String,
    value: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Int?) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            text = s.filter { it.isDigit() }.take(5)
            onChange(text.toIntOrNull())
        },
        label = { Text(label, fontSize = 10.sp) },
        singleLine = true,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.height(58.dp),
    )
}
