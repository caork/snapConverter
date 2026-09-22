package com.snapconverter.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snapconverter.app.ui.ConvertStage
import com.snapconverter.app.ui.JobViewModel
import com.snapconverter.app.ui.UiState
import com.snapconverter.app.ui.components.ActionRow
import com.snapconverter.app.ui.components.GroupedList
import com.snapconverter.app.ui.components.InsetGroup
import com.snapconverter.app.ui.components.Ios27Sheet
import com.snapconverter.app.ui.components.Segment
import com.snapconverter.app.ui.components.SegmentedControl
import com.snapconverter.app.ui.components.SliderRow
import com.snapconverter.app.ui.components.StackRow
import com.snapconverter.app.ui.components.SwitchRow
import com.snapconverter.app.ui.components.ValueRow
import com.snapconverter.app.ui.theme.ios27.Ios27Metrics
import com.snapconverter.app.ui.theme.ios27.Ios27Radius
import com.snapconverter.app.ui.theme.ios27.Ios27Spacing
import com.snapconverter.app.ui.theme.ios27.Ios27Type
import com.snapconverter.app.ui.theme.ios27.LocalIos27Palette
import com.snapconverter.app.ui.theme.pressable
import com.snapconverter.engine.policy.OutputResolution

/** Trim window. The selected range is re-encoded through the hardware pipeline. */
@Composable
fun TrimSheet(
    state: UiState,
    vm: JobViewModel,
    onDismiss: () -> Unit,
) {
    val locked = state.stage == ConvertStage.RUNNING
    val duration = state.durationSec
    val end = if (state.trimEndSec < 0) duration else state.trimEndSec
    val kept = (end - state.trimStartSec).coerceAtLeast(1)
    Ios27Sheet(title = "片段", onDismiss = onDismiss) {
        GroupedList {
            InsetGroup(
                header = "保留 " + formatClock(kept) + " / " + formatClock(duration),
                footer = if (state.trimActive) {
                    "只重编码所选区间，音轨同步裁剪，起点精确到帧。"
                } else {
                    "默认转换完整片段；拖动滑杆截取一段。"
                },
            ) {
                row {
                    SliderRow(
                        title = "起点",
                        valueLabel = formatClock(state.trimStartSec),
                        value = state.trimStartSec.toFloat(),
                        range = 0f..(end - 1).coerceAtLeast(1).toFloat(),
                        enabled = !locked,
                    ) { vm.setTrimStartSec(it.toInt()) }
                }
                row {
                    SliderRow(
                        title = "终点",
                        valueLabel = formatClock(end),
                        value = end.toFloat(),
                        range = (state.trimStartSec + 1).toFloat()..duration.coerceAtLeast(2).toFloat(),
                        enabled = !locked,
                    ) { vm.setTrimEndSec(it.toInt()) }
                }
                row {
                    ActionRow(
                        title = "恢复完整片段",
                        enabled = state.trimActive && !locked,
                    ) { vm.resetTrim() }
                }
            }
        }
    }
}

/** Image export sizing: preset long edge, or explicit pixels with aspect lock. */
@Composable
fun ImageSizeSheet(
    state: UiState,
    vm: JobViewModel,
    onDismiss: () -> Unit,
) {
    val source = state.imageInfo
    val locked = state.stage == ConvertStage.RUNNING
    val customWidth = state.imageCustomWidth
    val customHeight = state.imageCustomHeight
    val custom = customWidth != null && customHeight != null
    Ios27Sheet(title = "输出尺寸", onDismiss = onDismiss) {
        GroupedList {
            InsetGroup(
                header = "预设",
                footer = source?.let { "原图 " + it.width + "×" + it.height + "，缩放在 GPU 上完成。" },
            ) {
                row {
                    StackRow {
                        SegmentedControl(
                            segments = listOf(
                                Segment(OutputResolution.ORIGINAL, "原始"),
                                Segment(OutputResolution.UHD_2160, "2160p"),
                                Segment(OutputResolution.QHD_1440, "1440p"),
                                Segment(OutputResolution.FHD_1080, "1080p"),
                                Segment(OutputResolution.HD_720, "720p"),
                            ),
                            selected = state.resolution,
                            enabled = !locked && !custom,
                        ) { vm.setResolution(it) }
                    }
                }
            }

            InsetGroup(
                header = "自定义",
                footer = if (custom) {
                    if (state.imageAspectLock) {
                        "已锁定宽高比，高度跟随宽度；输出自动对齐到偶数像素。"
                    } else {
                        "宽高比已解锁，可任意拉伸。"
                    }
                } else {
                    null
                },
            ) {
                row {
                    SwitchRow(
                        title = "自定义像素",
                        checked = custom,
                        enabled = !locked && source != null,
                    ) { on ->
                        if (on && source != null) {
                            vm.setImageCustomWidth((source.width / 2 * 2).coerceIn(16, 8192))
                            vm.setImageCustomHeight((source.height / 2 * 2).coerceIn(16, 8192))
                        } else {
                            vm.setImageCustomWidth(null)
                            vm.setImageCustomHeight(null)
                        }
                    }
                }
                if (custom) {
                    row {
                        ValueRow(title = "宽") {
                            NumberField(
                                value = customWidth ?: 0,
                                enabled = !locked,
                            ) { vm.setImageCustomWidth(it) }
                        }
                    }
                    row {
                        ValueRow(title = "高") {
                            NumberField(
                                value = customHeight ?: 0,
                                enabled = !locked,
                            ) { vm.setImageCustomHeight(it) }
                        }
                    }
                    row {
                        SwitchRow(
                            title = "锁定宽高比",
                            checked = state.imageAspectLock,
                            enabled = !locked,
                        ) { vm.setImageAspectLock(it) }
                    }
                    row {
                        StackRow {
                            Row(horizontalArrangement = Arrangement.spacedBy(Ios27Spacing.sm)) {
                                listOf(100, 75, 50, 25).forEach { percent ->
                                    TertiaryButton(
                                        text = percent.toString() + "%",
                                        enabled = !locked,
                                        modifier = Modifier.weight(1f),
                                    ) { vm.applyImageScale(percent) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Right-aligned numeric field sized for a list row's trailing slot. */
@Composable
private fun NumberField(
    value: Int,
    enabled: Boolean,
    onChange: (Int?) -> Unit,
) {
    val palette = LocalIos27Palette.current
    var text by remember(value) { mutableStateOf(value.toString()) }
    Box(
        modifier = Modifier
            .width(92.dp)
            .height(Ios27Metrics.buttonSmall)
            .clip(RoundedCornerShape(Ios27Radius.field))
            .background(palette.fillTertiary),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                text = raw.filter { it.isDigit() }.take(5)
                onChange(text.toIntOrNull())
            },
            enabled = enabled,
            singleLine = true,
            textStyle = Ios27Type.body.copy(
                color = palette.label,
                textAlign = TextAlign.End,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.tint),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Ios27Spacing.md),
        )
    }
}

/** Small filled-tertiary action button for in-row shortcuts. */
@Composable
private fun RowScope.TertiaryButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalIos27Palette.current
    Box(
        modifier = modifier
            .height(Ios27Metrics.buttonSmall)
            .clip(RoundedCornerShape(Ios27Radius.full))
            .background(palette.fillTertiary)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = Ios27Type.subheadline,
            color = if (enabled) palette.tintText else palette.labelTertiary,
        )
    }
}
