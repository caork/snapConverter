package com.snapconverter.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.snapconverter.app.ui.components.ChipRow
import com.snapconverter.app.ui.components.HWTag
import com.snapconverter.app.ui.components.ScCard
import com.snapconverter.app.ui.components.SectionLabel
import com.snapconverter.app.ui.theme.SnapDimensions
import com.snapconverter.engine.device.DeviceCapabilityReport

/**
 * Live device capability screen (V1 scope): renders the MediaCodecList probe
 * behind a full-screen dialog. Capability-driven, never SoC-name driven.
 */
@Composable
fun CapabilityScreen(
    report: DeviceCapabilityReport?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(SnapDimensions.SpacingLg),
                verticalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingMd),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "设备能力",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("关闭")
                    }
                }

                if (report == null) {
                    Text(
                        "正在枚举 MediaCodecList…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    return@Column
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingMd),
                ) {
                    ScCard {
                        Column(
                            modifier = Modifier.padding(SnapDimensions.SpacingMd),
                            verticalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingSm),
                        ) {
                            SectionLabel("设备")
                            Text(
                                report.manufacturer + " " + report.device,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "SoC: " + report.socModel +
                                    " · hardware: " + report.hardware +
                                    " · Android API " + report.sdkInt,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = if (report.v1Supported) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (report.v1Supported) "V1 硬件管线可用" else "V1 不支持此设备",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (report.v1Supported) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                            }
                        }
                    }

                    ScCard {
                        Column(
                            modifier = Modifier.padding(SnapDimensions.SpacingMd),
                            verticalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingSm),
                        ) {
                            SectionLabel("硬件编码能力（运行时枚举）")
                            ChipRow {
                                CapChip("HEVC 编码", report.hardwareHevcEncoder)
                                CapChip("H.264 编码", report.hardwareAvcEncoder)
                                if (report.hardwareAv1Encoder) CapChip("AV1 编码", true)
                                CapChip("JPEG 编码", report.hardwareJpegEncoder)
                                CapChip("HEIC 管线", report.hardwareHeicPath)
                                CapChip("高通解码器", report.hasQualcommDecoder)
                                CapChip("厂商扩展 API", report.vendorExtensionsApi)
                                if (report.vmafAvailable) CapChip("VMAF", true)
                            }
                        }
                    }

                    ScCard {
                        Column(
                            modifier = Modifier.padding(SnapDimensions.SpacingMd),
                            verticalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingSm),
                        ) {
                            SectionLabel("硬件编码器（" + report.encoders.size + "）")
                            report.encoders.forEach { codec ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            codec.name,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            codec.mime,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (codec.isQualcomm) HWTag("Qualcomm")
                                }
                            }
                        }
                    }

                    if (report.notes.isNotEmpty()) {
                        ScCard {
                            Column(
                                modifier = Modifier.padding(SnapDimensions.SpacingMd),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                SectionLabel("说明")
                                report.notes.forEach { note ->
                                    Text(
                                        "· " + note,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapChip(label: String, ok: Boolean) {
    Text(
        text = if (ok) "✓ " + label else "✕ " + label,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = if (ok) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        },
        modifier = Modifier
            .padding(2.dp)
            .fillMaxWidth(),
    )
}
