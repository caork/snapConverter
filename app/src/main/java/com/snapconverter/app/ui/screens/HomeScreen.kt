package com.snapconverter.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapconverter.app.ui.JobViewModel
import com.snapconverter.app.ui.UiState
import com.snapconverter.engine.policy.CompressionMode
import com.snapconverter.engine.policy.MediaKind
import com.snapconverter.engine.policy.OutputFps
import com.snapconverter.engine.policy.OutputImageCodec
import com.snapconverter.engine.policy.OutputResolution
import com.snapconverter.engine.policy.OutputVideoCodec
import java.util.Locale

@Composable
fun HomeScreen(viewModel: JobViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.onPicked(it, MediaKind.VIDEO) } }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.onPicked(it, MediaKind.IMAGE) } }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("SnapConverter", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Hardware path only: Qualcomm MediaCodec + Adreno GLES. No FFmpeg, no CPU x264.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CapabilityCard(state)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { videoPicker.launch(arrayOf("video/mp4", "video/quicktime", "video/*")) },
                    enabled = !state.running,
                ) { Text("Pick video") }
                OutlinedButton(
                    onClick = { imagePicker.launch(arrayOf("image/*")) },
                    enabled = !state.running,
                ) { Text("Pick image") }
            }
            MediaSummary(state)
            if (state.kind != null) {
                SettingsPanel(state, viewModel)
            }
            if (state.running) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { viewModel.start() },
                enabled = state.input != null && !state.running && state.capabilities?.v1Supported == true,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.running) "Working on silicon…" else "Compress on hardware")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CapabilityCard(state: UiState) {
    val caps = state.capabilities
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("This device", style = MaterialTheme.typography.titleMedium)
        if (caps == null) {
            Text("Probing MediaCodecList…")
            return
        }
        Text("${caps.device} · API ${caps.sdkInt}")
        Text("SoC ${caps.socModel.ifBlank { "unknown" }} · ${caps.hardware}")
        Text(
            caps.summaryLine,
            color = if (caps.v1Supported) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
        val codecs = buildList {
            if (caps.hardwareHevcEncoder) add("HEVC HW")
            if (caps.hardwareAvcEncoder) add("AVC HW")
            if (caps.hardwareAv1Encoder) add("AV1 HW")
            if (caps.hardwareHeicPath) add("HEIC path")
            if (caps.hardwareJpegEncoder) add("JPEG HW") else add("JPEG blocked")
        }
        Text(codecs.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
        caps.encoders.take(6).forEach { c ->
            Text(
                c.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        caps.notes.forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun MediaSummary(state: UiState) {
    val video = state.videoInfo
    val image = state.imageInfo
    if (video == null && image == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Source", style = MaterialTheme.typography.titleMedium)
        if (video != null) {
            Text("${video.width}×${video.height}  ${video.mime}")
            val seconds = video.durationUs / 1_000_000.0
            Text(
                String.format(
                    Locale.US,
                    "%.1fs · %.1f fps · %.1f Mbps",
                    seconds,
                    video.frameRate,
                    video.bitrateBps / 1_000_000.0,
                ),
            )
        }
        if (image != null) {
            Text("${image.width}×${image.height}  ${image.mime}")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPanel(state: UiState, vm: JobViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Compression", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip("Quality", CompressionMode.QUALITY, state, vm)
            if (state.kind == MediaKind.VIDEO) {
                ModeChip("Target size", CompressionMode.TARGET_SIZE, state, vm)
                ModeChip("Bitrate", CompressionMode.TARGET_BITRATE, state, vm)
            }
        }
        if (state.mode == CompressionMode.QUALITY) {
            Text("Quality ${state.quality}  (policy, not a raw KEY_QUALITY dump)")
            Slider(
                value = state.quality.toFloat(),
                onValueChange = { vm.setQuality(it.toInt()) },
                valueRange = 0f..100f,
            )
        }
        if (state.mode == CompressionMode.TARGET_SIZE) {
            Text("Target ${state.targetSizeMb} MB")
            Slider(
                value = state.targetSizeMb.toFloat(),
                onValueChange = { vm.setTargetSizeMb(it.toInt().coerceIn(8, 2048)) },
                valueRange = 8f..1024f,
            )
        }
        if (state.mode == CompressionMode.TARGET_BITRATE) {
            Text("Video ${state.targetBitrateKbps} kbps")
            Slider(
                value = state.targetBitrateKbps.toFloat(),
                onValueChange = { vm.setTargetBitrateKbps(it.toInt().coerceIn(200, 40000)) },
                valueRange = 200f..20000f,
            )
        }
        Text("Resolution")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResChip("Original", OutputResolution.ORIGINAL, state, vm)
            ResChip("2160p", OutputResolution.UHD_2160, state, vm)
            ResChip("1440p", OutputResolution.QHD_1440, state, vm)
            ResChip("1080p", OutputResolution.FHD_1080, state, vm)
            ResChip("720p", OutputResolution.HD_720, state, vm)
        }
        if (state.kind == MediaKind.VIDEO) {
            Text("Codec")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.videoCodec == OutputVideoCodec.HEVC,
                    onClick = { vm.setVideoCodec(OutputVideoCodec.HEVC) },
                    label = { Text("H.265") },
                    enabled = state.capabilities?.hardwareHevcEncoder == true,
                )
                FilterChip(
                    selected = state.videoCodec == OutputVideoCodec.AVC,
                    onClick = { vm.setVideoCodec(OutputVideoCodec.AVC) },
                    label = { Text("H.264") },
                    enabled = state.capabilities?.hardwareAvcEncoder == true,
                )
                if (state.capabilities?.hardwareAv1Encoder == true) {
                    FilterChip(
                        selected = state.videoCodec == OutputVideoCodec.AV1,
                        onClick = { vm.setVideoCodec(OutputVideoCodec.AV1) },
                        label = { Text("AV1") },
                    )
                }
            }
            Text("Frame rate")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FpsChip("Original", OutputFps.ORIGINAL, state, vm)
                FpsChip("60", OutputFps.FPS_60, state, vm)
                FpsChip("30", OutputFps.FPS_30, state, vm)
                FpsChip("24", OutputFps.FPS_24, state, vm)
            }
        } else {
            Text("Image output")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.imageCodec == OutputImageCodec.HEIC,
                    onClick = { vm.setImageCodec(OutputImageCodec.HEIC) },
                    label = { Text("HEIC (HW HEVC)") },
                )
                FilterChip(
                    selected = state.imageCodec == OutputImageCodec.JPEG,
                    onClick = { vm.setImageCodec(OutputImageCodec.JPEG) },
                    label = { Text("JPEG") },
                    enabled = state.capabilities?.hardwareJpegEncoder == true,
                )
            }
            if (state.capabilities?.hardwareJpegEncoder != true) {
                Text(
                    "JPEG is hidden as a silent CPU path. This device has no public hardware JPEG encoder.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, mode: CompressionMode, state: UiState, vm: JobViewModel) {
    FilterChip(
        selected = state.mode == mode,
        onClick = { vm.setMode(mode) },
        label = { Text(label) },
    )
}

@Composable
private fun ResChip(label: String, value: OutputResolution, state: UiState, vm: JobViewModel) {
    FilterChip(
        selected = state.resolution == value,
        onClick = { vm.setResolution(value) },
        label = { Text(label) },
    )
}

@Composable
private fun FpsChip(label: String, value: OutputFps, state: UiState, vm: JobViewModel) {
    FilterChip(
        selected = state.fps == value,
        onClick = { vm.setFps(value) },
        label = { Text(label) },
    )
}
