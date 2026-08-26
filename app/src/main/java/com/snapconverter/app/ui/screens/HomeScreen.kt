package com.snapconverter.app.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapconverter.app.ui.ConvertStage
import com.snapconverter.app.ui.JobViewModel
import com.snapconverter.app.ui.UiState
import com.snapconverter.engine.policy.CompressionMode
import com.snapconverter.engine.policy.MediaKind
import com.snapconverter.engine.policy.OutputFps
import com.snapconverter.engine.policy.OutputImageCodec
import com.snapconverter.engine.policy.OutputResolution
import com.snapconverter.engine.policy.OutputVideoCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardShape = RoundedCornerShape(20.dp)
private val ThumbShape = RoundedCornerShape(16.dp)

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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(state)
            DeviceStrip(state)
            when (state.stage) {
                ConvertStage.IDLE, ConvertStage.LOADING -> IdleCard(
                    loading = state.stage == ConvertStage.LOADING,
                    onPick = { picker.launch(arrayOf("video/*", "image/*")) },
                )
                ConvertStage.READY, ConvertStage.RUNNING, ConvertStage.DONE -> {
                    MediaHero(state, onChange = { viewModel.reset(); picker.launch(arrayOf("video/*", "image/*")) })
                    if (state.stage != ConvertStage.DONE) {
                        SettingsPanel(state, viewModel)
                    }
                    if (state.stage == ConvertStage.RUNNING) {
                        ProgressCard(state)
                    }
                    if (state.stage == ConvertStage.DONE) {
                        ResultCard(state, onReset = { viewModel.reset() })
                    }
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (state.stage == ConvertStage.READY) {
                Button(
                    onClick = { viewModel.start() },
                    enabled = state.capabilities?.v1Supported == true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("开始转换", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun Header(state: UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("SnapConverter", style = MaterialTheme.typography.headlineLarge)
        Text(
            when (state.stage) {
                ConvertStage.IDLE -> "打开或分享照片、视频，用高通硬件转码"
                ConvertStage.LOADING -> "正在读取媒体信息…"
                ConvertStage.READY -> "选好目标格式和参数，再开始转换"
                ConvertStage.RUNNING -> "硬件编码器工作中"
                ConvertStage.DONE -> "转换完成，拍摄时间默认保持不变"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeviceStrip(state: UiState) {
    val caps = state.capabilities ?: return
    val ok = caps.v1Supported
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (ok) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                ),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                if (ok) "高通硬件就绪" else "未检测到高通硬件编码器",
                style = MaterialTheme.typography.titleMedium,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Text(
                buildList {
                    add(caps.socModel.ifBlank { caps.hardware })
                    if (caps.hardwareHevcEncoder) add("HEVC")
                    if (caps.hardwareAvcEncoder) add("AVC")
                    if (caps.hardwareAv1Encoder) add("AV1")
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IdleCard(loading: Boolean, onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Icon(
            imageVector = Icons.Rounded.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
        )
        Text("打开媒体", style = MaterialTheme.typography.titleMedium)
        Text(
            "从相册选择，或在其他 App 里点分享 / 打开方式，选 SnapConverter。加载后会显示预览、拍摄时间和可转格式。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onPick,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(if (loading) "读取中…" else "选择照片或视频")
        }
    }
}

@Composable
private fun MediaHero(state: UiState, onChange: () -> Unit) {
    val uri = state.input
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (uri != null) {
            MediaThumb(uri)
        }
        Text(state.displayName, style = MaterialTheme.typography.titleMedium)
        Text(
            buildString {
                val video = state.videoInfo
                val image = state.imageInfo
                if (video != null) {
                    append("${video.width}×${video.height}")
                    append("  ·  ${formatDuration(video.durationUs)}")
                    append("  ·  ${formatSize(state.fileSizeBytes)}")
                } else if (image != null) {
                    append("${image.width}×${image.height}")
                    append("  ·  ${formatSize(state.fileSizeBytes)}")
                }
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                state.captureTimeMs?.let { "拍摄 ${formatCapture(it)}" } ?: "未读到拍摄时间",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onChange, enabled = state.stage != ConvertStage.RUNNING) {
            Text("换一个文件")
        }
    }
}

@Composable
private fun MediaThumb(uri: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(uri, Size(960, 540), null) }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
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
            Icon(
                Icons.Rounded.Photo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("目标格式", style = MaterialTheme.typography.titleMedium)
        if (state.kind == MediaKind.VIDEO) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.videoCodec == OutputVideoCodec.HEVC,
                    onClick = { vm.setVideoCodec(OutputVideoCodec.HEVC) },
                    enabled = !locked && state.capabilities?.hardwareHevcEncoder == true,
                    label = { Text("H.265 / HEVC") },
                )
                FilterChip(
                    selected = state.videoCodec == OutputVideoCodec.AVC,
                    onClick = { vm.setVideoCodec(OutputVideoCodec.AVC) },
                    enabled = !locked && state.capabilities?.hardwareAvcEncoder == true,
                    label = { Text("H.264 / AVC") },
                )
                if (state.capabilities?.hardwareAv1Encoder == true) {
                    FilterChip(
                        selected = state.videoCodec == OutputVideoCodec.AV1,
                        onClick = { vm.setVideoCodec(OutputVideoCodec.AV1) },
                        enabled = !locked,
                        label = { Text("AV1") },
                    )
                }
            }
            Text("压缩方式", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.mode == CompressionMode.QUALITY,
                    onClick = { vm.setMode(CompressionMode.QUALITY) },
                    enabled = !locked,
                    label = { Text("画质") },
                )
                FilterChip(
                    selected = state.mode == CompressionMode.TARGET_SIZE,
                    onClick = { vm.setMode(CompressionMode.TARGET_SIZE) },
                    enabled = !locked,
                    label = { Text("目标大小") },
                )
                FilterChip(
                    selected = state.mode == CompressionMode.TARGET_BITRATE,
                    onClick = { vm.setMode(CompressionMode.TARGET_BITRATE) },
                    enabled = !locked,
                    label = { Text("码率") },
                )
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.imageCodec == OutputImageCodec.HEIC,
                    onClick = { vm.setImageCodec(OutputImageCodec.HEIC) },
                    enabled = !locked,
                    label = { Text("HEIC") },
                )
                FilterChip(
                    selected = state.imageCodec == OutputImageCodec.JPEG,
                    onClick = { vm.setImageCodec(OutputImageCodec.JPEG) },
                    enabled = !locked && state.capabilities?.hardwareJpegEncoder == true,
                    label = { Text("JPEG") },
                )
            }
            if (state.capabilities?.hardwareJpegEncoder != true) {
                Text(
                    "这台设备没有公开 JPEG 硬件编码器，图片请用 HEIC。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (state.mode) {
            CompressionMode.QUALITY, CompressionMode.LOSSLESS_REMUX -> {
                Text("画质 ${state.quality}    更小文件 ← → 更高画质")
                Slider(
                    value = state.quality.toFloat(),
                    onValueChange = { vm.setQuality(it.toInt()) },
                    valueRange = 0f..100f,
                    enabled = !locked,
                )
            }
            CompressionMode.TARGET_SIZE -> {
                Text("目标体积  ${state.targetSizeMb} MB")
                Slider(
                    value = state.targetSizeMb.toFloat(),
                    onValueChange = { vm.setTargetSizeMb(it.toInt().coerceIn(8, 2048)) },
                    valueRange = 8f..1024f,
                    enabled = !locked,
                )
            }
            CompressionMode.TARGET_BITRATE -> {
                Text("视频码率  ${state.targetBitrateKbps} kbps")
                Slider(
                    value = state.targetBitrateKbps.toFloat(),
                    onValueChange = { vm.setTargetBitrateKbps(it.toInt().coerceIn(200, 40000)) },
                    valueRange = 200f..20000f,
                    enabled = !locked,
                )
            }
        }

        Text("分辨率", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                OutputResolution.ORIGINAL to "原始",
                OutputResolution.UHD_2160 to "2160p",
                OutputResolution.QHD_1440 to "1440p",
                OutputResolution.FHD_1080 to "1080p",
                OutputResolution.HD_720 to "720p",
            ).forEach { (value, label) ->
                FilterChip(
                    selected = state.resolution == value,
                    onClick = { vm.setResolution(value) },
                    enabled = !locked,
                    label = { Text(label) },
                )
            }
        }
        if (state.kind == MediaKind.VIDEO) {
            Text("帧率", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    OutputFps.ORIGINAL to "原始",
                    OutputFps.FPS_60 to "60",
                    OutputFps.FPS_30 to "30",
                    OutputFps.FPS_24 to "24",
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = state.fps == value,
                        onClick = { vm.setFps(value) },
                        enabled = !locked,
                        label = { Text(label) },
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("保留原来的拍摄时间", style = MaterialTheme.typography.titleMedium)
                Text(
                    "相册和文件里仍显示原拍摄日期，不会变成今天。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.preserveCaptureTime,
                onCheckedChange = { vm.setPreserveCaptureTime(it) },
                enabled = !locked,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (state.kind == MediaKind.VIDEO) "保存到  影片/SnapConverter" else "保存到  图片/SnapConverter",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProgressCard(state: UiState) {
    val p = state.progress
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("转换进度  ${(p.ratio * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { p.ratio.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        val speed = buildString {
            if (p.elapsedMs > 400 && p.framesEncoded > 0) {
                append("%.0f fps".format(p.framesPerSecond))
                append("  ·  ")
            }
            if (p.elapsedMs > 400 && p.bytesWritten > 0) {
                append("%.1f MB/s".format(p.megabytesPerSecond))
            } else {
                append(state.message ?: "启动中")
            }
            val eta = p.etaMs
            if (eta > 0) {
                append("  ·  还剩 ")
                append(formatEta(eta))
            }
        }
        Text(speed, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text("已保存", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "${state.outputFolder.orEmpty()}/${state.outputName.orEmpty()}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            buildString {
                append(formatSize(state.outputSizeBytes))
                if (state.fileSizeBytes > 0 && state.outputSizeBytes > 0) {
                    append("  ·  原来 ")
                    append(formatSize(state.fileSizeBytes))
                }
            },
        )
        Text(
            if (state.preserveCaptureTime && state.captureTimeMs != null) {
                "拍摄时间 ${formatCapture(state.captureTimeMs)}  ·  未改"
            } else if (state.preserveCaptureTime) {
                "未读到原拍摄时间，已尽量不改文件日期"
            } else {
                "已按当前时间写入"
            },
            color = MaterialTheme.colorScheme.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { state.outputUri?.let { openOutput(context, it, state.kind) } },
            ) { Text("打开") }
            OutlinedButton(
                onClick = { state.outputUri?.let { shareOutput(context, it, state.kind) } },
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
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
    val sec = (durationUs / 1_000_000.0)
    return if (sec < 60) "%.1f 秒".format(Locale.US, sec)
    else "%d 分 %02d 秒".format(Locale.US, (sec / 60).toInt(), (sec % 60).toInt())
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
    return if (sec < 60) "${sec}s" else "${sec / 60} 分 ${sec % 60} 秒"
}
