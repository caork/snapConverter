package com.snapconverter.app.ui.screens

import android.media.MediaCodecInfo.EncoderCapabilities
import androidx.compose.runtime.Composable
import com.snapconverter.app.ui.ConvertStage
import com.snapconverter.app.ui.JobViewModel
import com.snapconverter.app.ui.UiState
import com.snapconverter.app.ui.components.GroupedList
import com.snapconverter.app.ui.components.InsetGroup
import com.snapconverter.app.ui.components.Ios27Sheet
import com.snapconverter.app.ui.components.Segment
import com.snapconverter.app.ui.components.SegmentedControl
import com.snapconverter.app.ui.components.SliderRow
import com.snapconverter.app.ui.components.StackRow
import com.snapconverter.app.ui.components.SwitchRow
import com.snapconverter.engine.policy.BitrateModeOption
import com.snapconverter.engine.policy.ComplexityOption
import com.snapconverter.engine.policy.OutputVideoCodec
import com.snapconverter.engine.policy.QualityStrategy
import com.snapconverter.engine.policy.VideoProfileOption

/**
 * Encoder-level parameters, kept off the main screen: rate control, GOP,
 * B-frames, profile, complexity, QP window. Options the probed encoder does not
 * expose are disabled rather than hidden, so the device's real capability stays
 * visible.
 */
@Composable
fun AdvancedSheet(
    state: UiState,
    vm: JobViewModel,
    onDismiss: () -> Unit,
) {
    val locked = state.stage == ConvertStage.RUNNING
    val encoder = selectedVideoEncoder(state)
    val complexityOk = encoder?.complexityRange != null
    val nativeCq = encoder != null &&
        encoder.supportsBitrateMode(EncoderCapabilities.BITRATE_MODE_CQ) &&
        encoder.qualityRange != null
    val baseline = state.profile == VideoProfileOption.BASELINE

    Ios27Sheet(title = "高级参数", onDismiss = onDismiss) {
        GroupedList {
            InsetGroup(header = "码率模式", footer = bitrateModeFooter(state, nativeCq, encoder)) {
                row {
                    StackRow {
                        SegmentedControl(
                            segments = listOf(
                                Segment(BitrateModeOption.AUTO, "Auto"),
                                Segment(BitrateModeOption.VBR, "VBR"),
                                Segment(BitrateModeOption.CBR, "CBR"),
                                Segment(BitrateModeOption.CQ, "CQ"),
                            ),
                            selected = state.bitrateMode,
                            enabled = !locked,
                        ) { vm.setBitrateMode(it) }
                    }
                }
                when (state.bitrateMode) {
                    BitrateModeOption.VBR -> {
                        row {
                            SliderRow(
                                title = "平均码率",
                                valueLabel = state.targetBitrateKbps.toString() + " kbps",
                                value = state.targetBitrateKbps.toFloat(),
                                range = 200f..40000f,
                                enabled = !locked,
                            ) { vm.setTargetBitrateKbps(it.toInt().coerceIn(200, 40000)) }
                        }
                        row {
                            SliderRow(
                                title = "峰值码率",
                                valueLabel = state.maxBitrateKbps.toString() + " kbps",
                                value = state.maxBitrateKbps.toFloat(),
                                range = 200f..80000f,
                                enabled = !locked,
                            ) { vm.setMaxBitrateKbps(it.toInt().coerceIn(200, 80000)) }
                        }
                    }
                    BitrateModeOption.CBR -> row {
                        SliderRow(
                            title = "恒定码率",
                            valueLabel = state.targetBitrateKbps.toString() + " kbps",
                            value = state.targetBitrateKbps.toFloat(),
                            range = 200f..40000f,
                            enabled = !locked,
                        ) { vm.setTargetBitrateKbps(it.toInt().coerceIn(200, 40000)) }
                    }
                    BitrateModeOption.CQ -> row {
                        SliderRow(
                            title = "画质",
                            valueLabel = state.quality.toString(),
                            value = state.quality.toFloat(),
                            range = 0f..100f,
                            enabled = !locked,
                        ) { vm.setQuality(it.toInt()) }
                    }
                    BitrateModeOption.AUTO -> Unit
                }
            }

            InsetGroup(header = "GOP 与参考帧", footer = if (baseline) "Baseline profile 不支持 B 帧。" else null) {
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
                            selected = state.iFrameIntervalSec,
                            enabled = !locked,
                        ) { vm.setIFrameIntervalSec(it) }
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
                            selected = if (baseline) 0 else state.maxBFrames,
                            enabled = !locked && !baseline,
                        ) { vm.setMaxBFrames(it) }
                    }
                }
            }

            InsetGroup(header = "Profile 与复杂度", footer = if (!complexityOk) "此编码器不暴露 complexity。" else null) {
                row {
                    StackRow {
                        SegmentedControl(
                            segments = if (state.videoCodec == OutputVideoCodec.AVC) {
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
                            selected = state.profile,
                            enabled = !locked,
                        ) { vm.setProfile(it) }
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
                            selected = state.complexity,
                            enabled = !locked,
                        ) { vm.setComplexity(it) }
                    }
                }
            }

            InsetGroup(
                header = "QP 窗口",
                footer = "手动 QP 会覆盖码率模式的分配；I 帧越小越清晰，P 帧控制运动区域。",
            ) {
                row {
                    SwitchRow(
                        title = "自定义 QP",
                        checked = state.qpIMin != null,
                        enabled = !locked,
                    ) { vm.setQpCustom(it) }
                }
                val qpIMin = state.qpIMin
                val qpIMax = state.qpIMax
                val qpPMin = state.qpPMin
                val qpPMax = state.qpPMax
                if (qpIMin != null && qpIMax != null && qpPMin != null && qpPMax != null) {
                    row {
                        SliderRow(
                            title = "I 帧 min",
                            valueLabel = qpIMin.toString(),
                            value = qpIMin.toFloat(),
                            range = 1f..51f,
                            enabled = !locked,
                            steps = 49,
                        ) { vm.setQpIMin(it.toInt()) }
                    }
                    row {
                        SliderRow(
                            title = "I 帧 max",
                            valueLabel = qpIMax.toString(),
                            value = qpIMax.toFloat(),
                            range = 1f..51f,
                            enabled = !locked,
                            steps = 49,
                        ) { vm.setQpIMax(it.toInt()) }
                    }
                    row {
                        SliderRow(
                            title = "P 帧 min",
                            valueLabel = qpPMin.toString(),
                            value = qpPMin.toFloat(),
                            range = 1f..51f,
                            enabled = !locked,
                            steps = 49,
                        ) { vm.setQpPMin(it.toInt()) }
                    }
                    row {
                        SliderRow(
                            title = "P 帧 max",
                            valueLabel = qpPMax.toString(),
                            value = qpPMax.toFloat(),
                            range = 1f..51f,
                            enabled = !locked,
                            steps = 49,
                        ) { vm.setQpPMax(it.toInt()) }
                    }
                }
            }

            InsetGroup(header = "编码器") {
                row {
                    com.snapconverter.app.ui.components.DetailRow(
                        title = "名称",
                        value = encoder?.name ?: "—",
                    )
                }
                row {
                    com.snapconverter.app.ui.components.DetailRow(
                        title = "厂商",
                        value = when {
                            encoder == null -> "—"
                            encoder.isQualcomm -> "Qualcomm"
                            else -> "其他硬件"
                        },
                    )
                }
            }
        }
    }
}

private fun bitrateModeFooter(
    state: UiState,
    nativeCq: Boolean,
    encoder: com.snapconverter.engine.codec.CodecCandidate?,
): String = when (state.bitrateMode) {
    BitrateModeOption.AUTO -> "Auto 跟随主界面的压缩目标。"
    BitrateModeOption.VBR -> "平均码率配峰值上限，动态画面分配更多码率。"
    BitrateModeOption.CBR -> "恒定码率，适合固定带宽的场合。"
    BitrateModeOption.CQ -> if (nativeCq) {
        val range = encoder?.qualityRange
        val mapped = range?.let { QualityStrategy.mapToCodecQuality(state.quality, it) }
        "CQ 恒定质量，KEY_QUALITY=" + (mapped ?: state.quality) +
            "（范围 " + (range?.first ?: 0) + "–" + (range?.last ?: 100) + "），码率随画面变。"
    } else {
        val qp = QualityStrategy.qpWindowForQuality(state.quality)
        "此编码器无原生 CQ，用 QP I " + qp.iMin + "–" + qp.iMax +
            " / P " + qp.pMin + "–" + qp.pMax + " 近似恒定质量。"
    }
}
