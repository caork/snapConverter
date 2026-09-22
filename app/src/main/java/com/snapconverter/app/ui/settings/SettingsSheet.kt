package com.snapconverter.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapconverter.app.ui.components.ScCard
import com.snapconverter.app.ui.components.ScChip
import com.snapconverter.app.ui.components.ChipRow
import com.snapconverter.app.ui.components.SectionLabel
import com.snapconverter.app.ui.theme.SnapDimensions
import com.snapconverter.app.ui.theme.SnapThemeMode
import com.snapconverter.app.ui.theme.bounceClick
import com.snapconverter.app.ui.theme.ios27.GlassPreset

/**
 * Appearance settings as an iOS 27 grouped sheet: theme mode, Material You
 * dynamic color, OLED black, and the Liquid Glass transparency presets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    options: AppSettings.Options,
    onChange: (AppSettings.Options) -> Unit,
    onOpenCapabilities: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SnapDimensions.SpacingLg)
                .padding(bottom = SnapDimensions.SpacingXxl),
            verticalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingLg),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("外观", style = MaterialTheme.typography.titleLarge)
                Text(
                    "主题跟随系统或单独设置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ScCard {
                Column(
                    modifier = Modifier.padding(SnapDimensions.SpacingMd),
                    verticalArrangement = Arrangement.spacedBy(SnapDimensions.SpacingSm),
                ) {
                    SectionLabel("主题模式")
                    ChipRow {
                        listOf(
                            SnapThemeMode.SYSTEM to "跟随系统",
                            SnapThemeMode.LIGHT to "浅色",
                            SnapThemeMode.DARK to "深色",
                        ).forEach { (mode, label) ->
                            ScChip(
                                text = label,
                                selected = options.themeMode == mode,
                            ) { onChange(options.copy(themeMode = mode)) }
                        }
                    }

                    SectionLabel("Liquid Glass 透明度")
                    ChipRow {
                        listOf(
                            GlassPreset.UltraClear to "超清晰",
                            GlassPreset.Clear to "清晰",
                            GlassPreset.Default to "默认",
                            GlassPreset.Tinted to "遮盖",
                            GlassPreset.FullyTinted to "全遮盖",
                        ).forEach { (preset, label) ->
                            ScChip(
                                text = label,
                                selected = options.glassPreset == preset,
                            ) { onChange(options.copy(glassPreset = preset)) }
                        }
                    }
                    Text(
                        "iOS 27 的系统级透明度滑块：越清晰越能透出背景，越遮盖文字对比越强。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    SettingSwitchRow(
                        title = "Material You 动态取色",
                        subtitle = "Android 12+ 使用系统壁纸配色",
                        checked = options.dynamicColor,
                        onCheckedChange = { onChange(options.copy(dynamicColor = it)) },
                    )
                    SettingSwitchRow(
                        title = "OLED 纯黑",
                        subtitle = "深色模式下使用纯黑背景，省电",
                        checked = options.oledBlack,
                        onCheckedChange = { onChange(options.copy(oledBlack = it)) },
                    )
                }
            }

            ScCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick(onClick = onOpenCapabilities)
                        .padding(SnapDimensions.SpacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(SnapDimensions.IconMd),
                    )
                    Spacer(Modifier.width(SnapDimensions.SpacingMd))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "设备能力",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "实时枚举的硬件编解码器与厂商扩展",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "查看",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
