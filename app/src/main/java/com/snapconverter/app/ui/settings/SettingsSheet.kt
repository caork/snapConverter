package com.snapconverter.app.ui.settings

import androidx.compose.runtime.Composable
import com.snapconverter.app.ui.components.GroupedList
import com.snapconverter.app.ui.components.InsetGroup
import com.snapconverter.app.ui.components.Ios27Sheet
import com.snapconverter.app.ui.components.NavRow
import com.snapconverter.app.ui.components.Segment
import com.snapconverter.app.ui.components.SegmentedControl
import com.snapconverter.app.ui.components.StackRow
import com.snapconverter.app.ui.components.SwitchRow
import com.snapconverter.app.ui.theme.SnapThemeMode
import com.snapconverter.app.ui.theme.ios27.GlassPreset

/**
 * Appearance sheet: theme mode, the iOS 27 transparency slider, OLED black, and
 * the device capability report.
 */
@Composable
fun SettingsSheet(
    options: AppSettings.Options,
    onChange: (AppSettings.Options) -> Unit,
    onOpenCapabilities: () -> Unit,
    onDismiss: () -> Unit,
) {
    Ios27Sheet(title = "外观", onDismiss = onDismiss) {
        GroupedList {
            InsetGroup(header = "主题") {
                row {
                    StackRow {
                        SegmentedControl(
                            segments = listOf(
                                Segment(SnapThemeMode.SYSTEM, "跟随系统"),
                                Segment(SnapThemeMode.LIGHT, "浅色"),
                                Segment(SnapThemeMode.DARK, "深色"),
                            ),
                            selected = options.themeMode,
                        ) { onChange(options.copy(themeMode = it)) }
                    }
                }
                row {
                    SwitchRow(
                        title = "OLED 纯黑",
                        subtitle = "深色模式用纯黑底，省电",
                        checked = options.oledBlack,
                    ) { onChange(options.copy(oledBlack = it)) }
                }
            }

            InsetGroup(
                header = "透明度",
                footer = "iOS 27 的系统透明度滑块。越清晰越透出下层内容，越遮盖文字对比越强。" +
                    "仅作用于顶栏与底栏——内容卡片始终不透明。",
            ) {
                row {
                    StackRow {
                        SegmentedControl(
                            segments = listOf(
                                Segment(GlassPreset.UltraClear, "超清"),
                                Segment(GlassPreset.Clear, "清晰"),
                                Segment(GlassPreset.Default, "默认"),
                                Segment(GlassPreset.Tinted, "遮盖"),
                                Segment(GlassPreset.FullyTinted, "全遮"),
                            ),
                            selected = options.glassPreset,
                        ) { onChange(options.copy(glassPreset = it)) }
                    }
                }
            }

            InsetGroup(footer = "运行时枚举的硬件编解码器与厂商扩展。") {
                row {
                    NavRow(
                        title = "设备能力",
                        onClick = onOpenCapabilities,
                    )
                }
            }
        }
    }
}
