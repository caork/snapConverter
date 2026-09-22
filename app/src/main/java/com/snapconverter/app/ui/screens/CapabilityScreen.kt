package com.snapconverter.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.snapconverter.app.ui.components.Badge
import com.snapconverter.app.ui.components.BadgeTone
import com.snapconverter.app.ui.components.DetailRow
import com.snapconverter.app.ui.components.GroupedList
import com.snapconverter.app.ui.components.InsetGroup
import com.snapconverter.app.ui.components.Ios27Sheet
import com.snapconverter.app.ui.components.ListRow
import com.snapconverter.app.ui.components.RowLabel
import com.snapconverter.app.ui.theme.ios27.Ios27Spacing
import com.snapconverter.app.ui.theme.ios27.Ios27Type
import com.snapconverter.app.ui.theme.ios27.LocalIos27Palette
import com.snapconverter.engine.device.DeviceCapabilityReport

/**
 * The live MediaCodecList probe. Capability-driven: every line here comes from
 * runtime enumeration, never from a SoC marketing name.
 */
@Composable
fun CapabilityScreen(
    report: DeviceCapabilityReport?,
    onDismiss: () -> Unit,
) {
    val palette = LocalIos27Palette.current
    Ios27Sheet(title = "设备能力", onDismiss = onDismiss) {
        if (report == null) {
            GroupedList {
                InsetGroup {
                    row {
                        ListRow {
                            Text(
                                text = "正在枚举 MediaCodecList…",
                                style = Ios27Type.body,
                                color = palette.labelSecondary,
                            )
                        }
                    }
                }
            }
            return@Ios27Sheet
        }

        GroupedList {
            InsetGroup(header = "设备", footer = deviceFooter(report)) {
                row { DetailRow(title = "机型", value = report.manufacturer + " " + report.device) }
                row { DetailRow(title = "SoC", value = report.socModel) }
                row { DetailRow(title = "hardware", value = report.hardware) }
                row { DetailRow(title = "Android API", value = report.sdkInt.toString()) }
                row {
                    ListRow {
                        RowLabel(title = "V1 硬件管线")
                        Spacer(Modifier.width(Ios27Spacing.sm))
                        Badge(
                            text = if (report.v1Supported) "可用" else "不支持",
                            tone = if (report.v1Supported) BadgeTone.Good else BadgeTone.Warn,
                        )
                    }
                }
            }

            InsetGroup(
                header = "运行时枚举的能力",
                footer = "JPEG 必须有公开的硬件编码器才启用，否则宁可拒绝也不做 CPU 软压缩。",
            ) {
                capabilityRows(report).forEach { (label, ok) ->
                    row {
                        ListRow {
                            RowLabel(title = label)
                            Spacer(Modifier.width(Ios27Spacing.sm))
                            Badge(
                                text = if (ok) "支持" else "无",
                                tone = if (ok) BadgeTone.Good else BadgeTone.Neutral,
                            )
                        }
                    }
                }
            }

            InsetGroup(header = "硬件编码器（" + report.encoders.size + "）") {
                report.encoders.forEach { codec ->
                    row {
                        ListRow {
                            RowLabel(title = codec.name, subtitle = codec.mime)
                            if (codec.isQualcomm) {
                                Spacer(Modifier.width(Ios27Spacing.sm))
                                Badge(text = "Qualcomm", tone = BadgeTone.Tint)
                            }
                        }
                    }
                }
            }

            if (report.notes.isNotEmpty()) {
                InsetGroup(header = "说明") {
                    report.notes.forEach { note ->
                        row {
                            ListRow {
                                Text(
                                    text = note,
                                    style = Ios27Type.footnote,
                                    color = palette.labelSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun deviceFooter(report: DeviceCapabilityReport): String = when {
    !report.v1Supported -> "V1 需要高通硬件编码器；本机未枚举到，因此不提供编码。"
    report.hardwareHevcEncoder -> "已枚举到高通硬件 HEVC 编码器。"
    else -> "已枚举到高通硬件 H.264 编码器（未枚举到 HEVC）。"
}

private fun capabilityRows(report: DeviceCapabilityReport): List<Pair<String, Boolean>> = buildList {
    add("HEVC 硬件编码" to report.hardwareHevcEncoder)
    add("H.264 硬件编码" to report.hardwareAvcEncoder)
    add("AV1 硬件编码" to report.hardwareAv1Encoder)
    add("JPEG 硬件编码" to report.hardwareJpegEncoder)
    add("HEIC 写入管线" to report.hardwareHeicPath)
    add("高通硬件解码器" to report.hasQualcommDecoder)
    add("厂商扩展 API" to report.vendorExtensionsApi)
    add("VMAF 评分" to report.vmafAvailable)
}
