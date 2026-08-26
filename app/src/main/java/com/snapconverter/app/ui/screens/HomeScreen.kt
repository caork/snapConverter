package com.snapconverter.app.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapconverter.app.R
import com.snapconverter.app.ui.ConvertStage
import com.snapconverter.app.ui.JobViewModel
import com.snapconverter.app.ui.UiState
import com.snapconverter.engine.policy.BitrateModeOption
import com.snapconverter.engine.policy.ComplexityOption
import com.snapconverter.engine.policy.CompressionMode
import com.snapconverter.engine.policy.MediaKind
import com.snapconverter.engine.policy.OutputFps
import com.snapconverter.engine.policy.OutputImageCodec
import com.snapconverter.engine.policy.OutputResolution
import com.snapconverter.engine.policy.OutputVideoCodec
import com.snapconverter.engine.policy.VideoProfileOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardShape = RoundedCornerShape(14.dp)
private val ThumbShape = RoundedCornerShape(10.dp)

@Composable
fun HomeScreen(viewModel: JobViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.onPicked(it) } }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeaderBar(qualcomm = state.capabilities?.hasQualcommEncoder == true)
            when (state.stage) {
                ConvertStage.IDLE, ConvertStage.LOADING -> IdleCard(
                    loading = state.stage == ConvertStage.LOADING,
                    onPick = { picker.launch(arrayOf("video/*", "image/*")) },
                )
                ConvertStage.READY, ConvertStage.RUNNING, ConvertStage.DONE -> {
                    MediaHero(
                        state,
                        onChange = {
                            viewModel.reset()
                            picker.launch(arrayOf("video/*", "image/*"))
                        },
                    )
                    if (state.stage != ConvertStage.DONE) {
                        SettingsPanel(state, viewModel)
                    }
                    if (state.stage == ConvertStage.RUNNING) ProgressCard(state)
                    if (state.stage == ConvertStage.DONE) {
                        ResultCard(state, onReset = { viewModel.reset() })
                    }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            if (state.stage == ConvertStage.READY) {
                Button(
                    onClick = { viewModel.start() },
                    enabled = state.capabilities?.v1Supported == true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("开始转换", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HeaderBar(qualcomm: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "SnapConverter",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        )
        Spacer(Modifier.weight(1f))
        if (qualcomm) {
            Icon(
                painter = painterResource(R.drawable.ic_qti),
                contentDescription = "Qualcomm",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun IdleCard(loading: Boolean, onPick: () -> Unit) {
    Button(
        onClick = onPick,
        enabled = !loading,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(if (loading) "读取中…" else "选择照片或视频")
    }
}

@Composable
private fun MediaHero(state: UiState, onChange: () -> Unit) {
    val uri = state.input
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (uri != null) {
            MediaThumb(uri, Modifier.size(72.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "更换",
                    fontSize = 12.sp,
                    color = if (state.stage == ConvertStage.RUNNING) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.clickable(
                        enabled = state.stage != ConvertStage.RUNNING,
                        onClick = onChange,
                    ),
                )
            }
            Text(
                buildString {
                    val video = state.videoInfo
                    val image = state.imageInfo
                    if (video != null) {
                        append("${video.width}×${video.height}")
                        append(" · ${formatDuration(video.durationUs)}")
                        append(" · ${formatSize(state.fileSizeBytes)}")
                    } else if (image != null) {
                        append("${image.width}×${image.height}")
                        append(" · ${formatSize(state.fileSizeBytes)}")
                    }
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Text(
                state.captureTimeMs?.let { formatCapture(it) } ?: "无拍摄时间",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun MediaThumb(uri: Uri, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(uri, Size(320, 320), null) }.getOrNull()
        }
    }
    Box(
        modifier = modifier
            .clip(ThumbShape)
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(Icons.Rounded.Photo, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPanel(state: UiState, vm: JobViewModel) {
    val locked = state.stage == ConvertStage.RUNNING
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.kind == MediaKind.VIDEO) {
            ChipRow {
                MiniChip("H.265", state.videoCodec == OutputVideoCodec.HEVC, !locked && state.capabilities?.hardwareHevcEncoder == true) {
                    vm.setVideoCodec(OutputVideoCodec.HEVC)
                }
                MiniChip("H.264", state.videoCodec == OutputVideoCodec.AVC, !locked && state.capabilities?.hardwareAvcEncoder == true) {
                    vm.setVideoCodec(OutputVideoCodec.AVC)
                }
                if (state.capabilities?.hardwareAv1Encoder == true) {
                    MiniChip("AV1", state.videoCodec == OutputVideoCodec.AV1, !locked) {
                        vm.setVideoCodec(OutputVideoCodec.AV1)
                    }
                }
            }
            ChipRow {
                MiniChip("画质", state.mode == CompressionMode.QUALITY, !locked) { vm.setMode(CompressionMode.QUALITY) }
                MiniChip("目标大小", state.mode == CompressionMode.TARGET_SIZE, !locked) { vm.setMode(CompressionMode.TARGET_SIZE) }
                MiniChip("码率", state.mode == CompressionMode.TARGET_BITRATE, !locked) { vm.setMode(CompressionMode.TARGET_BITRATE) }
            }
        } else {
            ChipRow {
                MiniChip("HEIC", state.imageCodec == OutputImageCodec.HEIC, !locked) {
                    vm.setImageCodec(OutputImageCodec.HEIC)
                }
                MiniChip(
                    "JPEG",
                    state.imageCodec == OutputImageCodec.JPEG,
                    !locked && state.capabilities?.hardwareJpegEncoder == true,
                ) { vm.setImageCodec(OutputImageCodec.JPEG) }
            }
        }

        when (state.mode) {
            CompressionMode.QUALITY, CompressionMode.LOSSLESS_REMUX -> {
                Label("Quality  ${state.quality}")
                CompactSlider(state.quality.toFloat(), 0f..100f, !locked) { vm.setQuality(it.toInt()) }
            }
            CompressionMode.TARGET_SIZE -> {
                Label("Target size  ${state.targetSizeMb} MB")
                CompactSlider(state.targetSizeMb.toFloat(), 8f..1024f, !locked) {
                    vm.setTargetSizeMb(it.toInt().coerceIn(8, 2048))
                }
            }
            CompressionMode.TARGET_BITRATE -> {
                Label("Bitrate  ${state.targetBitrateKbps} kbps")
                CompactSlider(state.targetBitrateKbps.toFloat(), 200f..20000f, !locked) {
                    vm.setTargetBitrateKbps(it.toInt().coerceIn(200, 40000))
                }
            }
        }

        Label("Resolution")
        ChipRow {
            listOf(
                OutputResolution.ORIGINAL to "原始",
                OutputResolution.UHD_2160 to "2160p",
                OutputResolution.QHD_1440 to "1440p",
                OutputResolution.FHD_1080 to "1080p",
                OutputResolution.HD_720 to "720p",
            ).forEach { (v, t) ->
                MiniChip(t, state.resolution == v, !locked) { vm.setResolution(v) }
            }
        }
        if (state.kind == MediaKind.VIDEO) {
            Label("Frame rate")
            ChipRow {
                listOf(
                    OutputFps.ORIGINAL to "原始",
                    OutputFps.FPS_60 to "60",
                    OutputFps.FPS_30 to "30",
                    OutputFps.FPS_24 to "24",
                ).forEach { (v, t) ->
                    MiniChip(t, state.fps == v, !locked) { vm.setFps(v) }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("保留拍摄时间", fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = state.preserveCaptureTime,
                onCheckedChange = { vm.setPreserveCaptureTime(it) },
                enabled = !locked,
            )
        }
        Text(
            if (state.kind == MediaKind.VIDEO) "影片/SnapConverter" else "图片/SnapConverter",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )

        if (state.kind == MediaKind.VIDEO) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !locked) { vm.setAdvancedOpen(!state.advancedOpen) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("高级参数", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Icon(
                    if (state.advancedOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            AnimatedVisibility(visible = state.advancedOpen) {
                AdvancedParams(state, vm, locked)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedParams(state: UiState, vm: JobViewModel, locked: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label("Bitrate mode")
        ChipRow {
            MiniChip("Auto", state.bitrateMode == BitrateModeOption.AUTO, !locked) { vm.setBitrateMode(BitrateModeOption.AUTO) }
            MiniChip("VBR", state.bitrateMode == BitrateModeOption.VBR, !locked) { vm.setBitrateMode(BitrateModeOption.VBR) }
            MiniChip("CBR", state.bitrateMode == BitrateModeOption.CBR, !locked) { vm.setBitrateMode(BitrateModeOption.CBR) }
            MiniChip("CQ", state.bitrateMode == BitrateModeOption.CQ, !locked) { vm.setBitrateMode(BitrateModeOption.CQ) }
        }
        Label("I-frame interval")
        ChipRow {
            MiniChip("Auto", state.iFrameIntervalSec == null, !locked) { vm.setIFrameIntervalSec(null) }
            listOf(1, 2, 3, 5, 10).forEach { s ->
                MiniChip("${s}s", state.iFrameIntervalSec == s, !locked) { vm.setIFrameIntervalSec(s) }
            }
        }
        Label("B-frames")
        ChipRow {
            MiniChip("Auto", state.maxBFrames == null, !locked) { vm.setMaxBFrames(null) }
            listOf(0, 1, 2, 3).forEach { n ->
                MiniChip("$n", state.maxBFrames == n, !locked) { vm.setMaxBFrames(n) }
            }
        }
        Label("Profile")
        ChipRow {
            MiniChip("Auto", state.profile == VideoProfileOption.AUTO, !locked) { vm.setProfile(VideoProfileOption.AUTO) }
            if (state.videoCodec == OutputVideoCodec.AVC) {
                MiniChip("Baseline", state.profile == VideoProfileOption.BASELINE, !locked) { vm.setProfile(VideoProfileOption.BASELINE) }
                MiniChip("Main", state.profile == VideoProfileOption.MAIN, !locked) { vm.setProfile(VideoProfileOption.MAIN) }
                MiniChip("High", state.profile == VideoProfileOption.HIGH, !locked) { vm.setProfile(VideoProfileOption.HIGH) }
            } else {
                MiniChip("Main", state.profile == VideoProfileOption.MAIN, !locked) { vm.setProfile(VideoProfileOption.MAIN) }
                MiniChip("Main10", state.profile == VideoProfileOption.MAIN10, !locked) { vm.setProfile(VideoProfileOption.MAIN10) }
            }
        }
        Label("Complexity")
        ChipRow {
            MiniChip("Auto", state.complexity == ComplexityOption.AUTO, !locked) { vm.setComplexity(ComplexityOption.AUTO) }
            MiniChip("Low", state.complexity == ComplexityOption.LOW, !locked) { vm.setComplexity(ComplexityOption.LOW) }
            MiniChip("High", state.complexity == ComplexityOption.HIGH, !locked) { vm.setComplexity(ComplexityOption.HIGH) }
        }
        Label("QP")
        ChipRow {
            MiniChip("Auto", state.qpMin == null, !locked) { vm.setQpRange(null, null) }
            MiniChip("Custom", state.qpMin != null, !locked) {
                if (state.qpMin == null) vm.setQpRange(16, 36)
            }
        }
        if (state.qpMin != null && state.qpMax != null) {
            Label("QP min  ${state.qpMin}")
            CompactSlider(state.qpMin.toFloat(), 1f..51f, !locked) { min ->
                vm.setQpRange(min.toInt(), maxOf(min.toInt(), state.qpMax ?: min.toInt()))
            }
            Label("QP max  ${state.qpMax}")
            CompactSlider(state.qpMax.toFloat(), 1f..51f, !locked) { max ->
                vm.setQpRange(minOf(state.qpMin ?: max.toInt(), max.toInt()), max.toInt())
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        content = { content() },
    )
}

@Composable
private fun MiniChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(),
        border = FilterChipDefaults.filterChipBorder(enabled, selected),
        modifier = Modifier.height(28.dp),
    )
}

@Composable
private fun CompactSlider(value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, onChange: (Float) -> Unit) {
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        enabled = enabled,
        modifier = Modifier.height(20.dp),
    )
}

@Composable
private fun ProgressCard(state: UiState) {
    val p = state.progress
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("${(p.ratio * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        LinearProgressIndicator(
            progress = { p.ratio.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            buildString {
                if (p.elapsedMs > 400 && p.framesEncoded > 0) append("%.0f fps".format(p.framesPerSecond))
                if (p.elapsedMs > 400 && p.bytesWritten > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("%.1f MB/s".format(p.megabytesPerSecond))
                }
                val eta = p.etaMs
                if (eta > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("ETA ${formatEta(eta)}")
                }
                if (isEmpty()) append(state.message ?: "…")
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultCard(state: UiState, onReset: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("已保存", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            "${state.outputFolder.orEmpty()}/${state.outputName.orEmpty()}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Text(
            buildString {
                append(formatSize(state.outputSizeBytes))
                if (state.fileSizeBytes > 0) {
                    append(" · 原 ${formatSize(state.fileSizeBytes)}")
                }
            },
            fontSize = 12.sp,
        )
        if (state.preserveCaptureTime && state.captureTimeMs != null) {
            Text("拍摄时间 ${formatCapture(state.captureTimeMs)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { state.outputUri?.let { openOutput(context, it, state.kind) } }) { Text("打开") }
            OutlinedButton(onClick = { state.outputUri?.let { shareOutput(context, it, state.kind) } }) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("分享")
            }
            TextButton(onClick = onReset) { Text("再转一个") }
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

private fun shareOutput(context: Context, uri: Uri, kind: MediaKind?) {
    val mime = if (kind == MediaKind.IMAGE) "image/*" else "video/mp4"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "output", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享"))
}

private fun formatCapture(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

private fun formatDuration(durationUs: Long): String {
    val sec = durationUs / 1_000_000.0
    return if (sec < 60) "%.1fs".format(Locale.US, sec)
    else "%d:%02d".format(Locale.US, (sec / 60).toInt(), (sec % 60).toInt())
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

private fun formatEta(ms: Long): String {
    val sec = (ms / 1000.0).toInt().coerceAtLeast(1)
    return if (sec < 60) "${sec}s" else "${sec / 60}m${sec % 60}s"
}
