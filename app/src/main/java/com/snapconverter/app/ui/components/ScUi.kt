package com.snapconverter.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapconverter.app.ui.theme.SnapDimensions
import com.snapconverter.app.ui.theme.bounceClick
import com.snapconverter.app.ui.theme.ios27.GlassPreset
import com.snapconverter.app.ui.theme.ios27.Ios27Colors
import com.snapconverter.app.ui.theme.ios27.Ios27Motion
import com.snapconverter.app.ui.theme.ios27.LocalGlassPreset
import com.snapconverter.app.ui.theme.ios27.LocalIsDark
import com.snapconverter.app.ui.theme.ios27.liquidGlass

/**
 * Shared primitives on the iOS 27 design language: Liquid Glass cards,
 * capsule segmented chips, stat pills, metric tiles. Pure presentation.
 */

/** Liquid Glass group card (radius 26 = iOS 27 xxxl). */
@Composable
fun ScCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dark = LocalIsDark.current
    val preset = LocalGlassPreset.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(SnapDimensions.RadiusLg),
                dark = dark,
                preset = preset,
            ),
    ) { content() }
}

/** Footnote-emphasized section label in the tertiary label color. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.08).sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Footnote caption (hints, helper text). */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * Capsule chip on the small-glass material; selected state takes the mint
 * tint with an ink label (the iOS 27 readability pass applied to tinting).
 */
@Composable
fun ScChip(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val dark = LocalIsDark.current
    val preset = LocalGlassPreset.current
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> if (dark) Color(0x14EBEBF5) else Color(0x14000000)
            selected -> MaterialTheme.colorScheme.primary
            else -> Color.Unspecified
        },
        animationSpec = tween(180, easing = Ios27Motion.AppleDefaultEasing),
        label = "chipContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            selected -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(180),
        label = "chipContent",
    )
    Box(
        modifier = Modifier
            .height(SnapDimensions.ChipHeight)
            .clip(RoundedCornerShape(SnapDimensions.RadiusPill))
            .then(
                if (selected || !enabled) {
                    Modifier
                } else {
                    // Unselected chip: small Liquid Glass fill.
                    Modifier.liquidGlass(
                        shape = RoundedCornerShape(SnapDimensions.RadiusPill),
                        dark = dark,
                        preset = preset,
                        shadowElevation = 0.dp,
                    )
                },
            )
            .then(
                if (selected) Modifier else Modifier,
            )
            .bounceClick(enabled = enabled, onClick = onClick),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .height(SnapDimensions.ChipHeight)
                    .clip(RoundedCornerShape(SnapDimensions.RadiusPill))
                    .liquidGlass(
                        shape = RoundedCornerShape(SnapDimensions.RadiusPill),
                        dark = dark,
                        preset = preset,
                        shadowElevation = 0.dp,
                        prominent = true,
                    ),
            )
        }
        Text(
            text = text,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            letterSpacing = (-0.08).sp,
            color = contentColor,
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .align(Alignment.Center),
        )
    }
}

/** Wrapping row of segmented chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

/** Slider in the iOS style track (thin, tinted). */
@Composable
fun ScSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        enabled = enabled,
        modifier = modifier.height(24.dp),
    )
}

/** Gold badge for hardware / vendor capability markers. */
@Composable
fun HWTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.06.sp,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = modifier
            .clip(RoundedCornerShape(SnapDimensions.RadiusPill))
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Small glass info pill: one-line stat such as "42 fps" or "ETA 12s". */
@Composable
fun StatPill(text: String, modifier: Modifier = Modifier) {
    val dark = LocalIsDark.current
    val preset = LocalGlassPreset.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(SnapDimensions.RadiusPill))
            .liquidGlass(
                shape = RoundedCornerShape(SnapDimensions.RadiusPill),
                dark = dark,
                preset = preset,
                shadowElevation = 0.dp,
            ),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
        )
    }
}

/** Metric tile: value over a small label (VMAF / PSNR / SSIM …). */
@Composable
fun MetricChip(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val dark = LocalIsDark.current
    val preset = LocalGlassPreset.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(SnapDimensions.RadiusSm))
            .then(
                if (highlight) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                } else {
                    Modifier.liquidGlass(
                        shape = RoundedCornerShape(SnapDimensions.RadiusSm),
                        dark = dark,
                        preset = preset,
                        shadowElevation = 0.dp,
                    )
                },
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (highlight) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = label,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Row helper: label on the left, content on the right. */
@Composable
fun SettingRow(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}
