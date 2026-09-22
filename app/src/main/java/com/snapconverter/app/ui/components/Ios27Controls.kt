package com.snapconverter.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.snapconverter.app.ui.theme.ios27.Ios27Metrics
import com.snapconverter.app.ui.theme.ios27.Ios27Motion
import com.snapconverter.app.ui.theme.ios27.Ios27Radius
import com.snapconverter.app.ui.theme.ios27.Ios27Spacing
import com.snapconverter.app.ui.theme.ios27.Ios27Type
import com.snapconverter.app.ui.theme.ios27.LocalIos27Palette
import com.snapconverter.app.ui.theme.pressable
import kotlin.math.roundToInt

/**
 * iOS 27 controls: segmented control, slider, switch, buttons, tiles. All
 * opaque — glass stays on the navigation layer (see LiquidGlass.kt).
 */

data class Segment<T>(
    val value: T,
    val label: String,
    val enabled: Boolean = true,
)

/**
 * Segmented control, 32pt: fill-tertiary track with a solid selected thumb
 * (#fff light, white @27% dark) that slides on the snappy spring. Selection has
 * to be unmistakable, so the thumb is a real fill, never a tint on a tint.
 */
@Composable
fun <T> SegmentedControl(
    segments: List<Segment<T>>,
    selected: T,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    if (segments.isEmpty()) return
    val palette = LocalIos27Palette.current
    val index = segments.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(Ios27Metrics.segmented)
            .clip(RoundedCornerShape(9.dp))
            .background(palette.fillTertiary),
    ) {
        val segmentWidth = maxWidth / segments.size
        val thumbOffset by animateDpAsState(
            targetValue = segmentWidth * index,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = 0.8f,
                stiffness = 700f,
            ),
            label = "segmentThumb",
        )
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .padding(2.dp)
                .width(segmentWidth - 4.dp)
                .fillMaxHeight()
                .then(
                    if (palette.dark) {
                        Modifier
                    } else {
                        Modifier.shadow(2.dp, RoundedCornerShape(7.dp))
                    },
                )
                .clip(RoundedCornerShape(7.dp))
                .background(palette.segmentedThumb),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            segments.forEachIndexed { i, segment ->
                val active = i == index
                val usable = enabled && segment.enabled
                Box(
                    modifier = Modifier
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .pressable(enabled = usable) { onSelect(segment.value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = segment.label,
                        style = if (active) {
                            Ios27Type.subheadlineEmphasized
                        } else {
                            Ios27Type.subheadline
                        },
                        color = when {
                            !usable -> palette.labelTertiary
                            else -> palette.label
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * iOS slider: 4pt track, 28pt white thumb with a soft shadow. Hand-rolled
 * because the Material 3 slider's 16pt track and stop indicator read as
 * Android, not iOS.
 */
@Composable
fun IosSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    val palette = LocalIos27Palette.current
    val thumb = Ios27Metrics.sliderThumb
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(thumb),
    ) {
        val travel = (maxWidth - thumb).coerceAtLeast(0.dp)
        val density = LocalDensity.current
        val travelPx = with(density) { travel.toPx() }
        val halfThumbPx = with(density) { (thumb / 2).toPx() }
        val resolve: (Float) -> Float = { x ->
            val raw = if (travelPx <= 0f) 0f else ((x - halfThumbPx) / travelPx).coerceIn(0f, 1f)
            val snapped = if (steps > 0) {
                val notches = steps + 1
                (raw * notches).roundToInt().toFloat() / notches
            } else {
                raw
            }
            range.start + snapped * span
        }
        val gestures = if (enabled) {
            Modifier
                .pointerInput(travelPx, steps, range) {
                    detectTapGestures { offset -> onChange(resolve(offset.x)) }
                }
                .pointerInput(travelPx, steps, range) {
                    detectHorizontalDragGestures { change, _ ->
                        onChange(resolve(change.position.x))
                    }
                }
        } else {
            Modifier
        }
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight().then(gestures)) {
            // Inactive track.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(Ios27Metrics.progressTrack)
                    .clip(CircleShape)
                    .background(palette.fillTertiary),
            )
            // Active track.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(thumb / 2 + travel * fraction)
                    .height(Ios27Metrics.progressTrack)
                    .clip(CircleShape)
                    .background(if (enabled) palette.tint else palette.fill),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = travel * fraction)
                    .size(thumb)
                    .shadow(3.dp, CircleShape)
                    .background(
                        color = if (enabled) Color.White else palette.fillSecondary,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/** Switch row trailing control, retinted to the iOS accent. */
@Composable
fun IosSwitch(
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = LocalIos27Palette.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = palette.tint,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = palette.fillSecondary,
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedThumbColor = Color.White,
            disabledCheckedTrackColor = palette.tint.copy(alpha = 0.4f),
            disabledCheckedBorderColor = Color.Transparent,
            disabledUncheckedThumbColor = Color.White,
            disabledUncheckedTrackColor = palette.fillQuaternary,
            disabledUncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** Switch row. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ValueRow(title = title, subtitle = subtitle) {
        IosSwitch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

/**
 * Slider row: label and live value on one line, track below. The value belongs
 * in the row, not in the section header, so the control reads as one object.
 */
@Composable
fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    val palette = LocalIos27Palette.current
    StackRow {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = Ios27Type.body,
                color = if (enabled) palette.label else palette.labelTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = Ios27Type.body,
                color = if (enabled) palette.labelSecondary else palette.labelTertiary,
            )
        }
        IosSlider(
            value = value,
            range = range,
            enabled = enabled,
            steps = steps,
            onChange = onChange,
        )
    }
}

/** Prominent filled button, 50pt pill — one per screen. */
@Composable
fun FilledButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalIos27Palette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.82f else 1f,
        animationSpec = Ios27Motion.Snappy,
        label = "buttonPress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Ios27Metrics.buttonLarge)
            .clip(RoundedCornerShape(Ios27Radius.full))
            .background(
                if (enabled) palette.tintFill.copy(alpha = alpha) else palette.fillSecondary,
            )
            .pressable(interaction, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = Ios27Type.headline,
            color = if (enabled) palette.onTintFill else palette.labelTertiary,
        )
    }
}

/** Plain tinted text button used in bars and sheet headers. */
@Composable
fun PlainButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalIos27Palette.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Ios27Radius.full))
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Ios27Spacing.md, vertical = Ios27Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (emphasized) Ios27Type.headline else Ios27Type.body,
            color = if (enabled) palette.tintText else palette.labelTertiary,
        )
    }
}

/** 44×44 glyph button for the nav bar. */
@Composable
fun BarIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalIos27Palette.current
    Box(
        modifier = modifier
            .size(Ios27Metrics.touchTarget)
            .clip(CircleShape)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = palette.tintText,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Determinate progress bar, 4pt track with rounded caps. */
@Composable
fun IosProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val palette = LocalIos27Palette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Ios27Metrics.progressTrack)
            .clip(CircleShape)
            .background(palette.fillTertiary),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(palette.tint),
        )
    }
}

/** Soft pill badge (Qualcomm marker, size saving, HDR marker). */
@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Neutral,
) {
    val palette = LocalIos27Palette.current
    val content = when (tone) {
        BadgeTone.Neutral -> palette.labelSecondary
        BadgeTone.Tint -> palette.tintText
        BadgeTone.Good -> palette.green
        BadgeTone.Warn -> palette.yellowText
    }
    val container = when (tone) {
        BadgeTone.Neutral -> palette.fillTertiary
        BadgeTone.Tint -> palette.tintSoft
        BadgeTone.Good -> palette.green.copy(alpha = 0.16f)
        BadgeTone.Warn -> palette.yellow.copy(alpha = 0.18f)
    }
    Text(
        text = text,
        style = Ios27Type.caption1Emphasized,
        color = content,
        modifier = modifier
            .clip(RoundedCornerShape(Ios27Radius.sm))
            .background(container)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

enum class BadgeTone { Neutral, Tint, Good, Warn }

/** Metric tile: value over a caption (VMAF / PSNR-Y / SSIM). */
@Composable
fun MetricTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val palette = LocalIos27Palette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Ios27Radius.field))
            .background(if (emphasized) palette.tintSoft else palette.groupedContentInner)
            .padding(horizontal = Ios27Spacing.md, vertical = Ios27Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = value,
            style = Ios27Type.headline,
            color = if (emphasized) palette.tintText else palette.label,
        )
        Text(
            text = label,
            style = Ios27Type.caption2,
            color = palette.labelSecondary,
        )
    }
}

/** Spacer that matches the standard group gap. */
@Composable
fun GroupGap(height: Dp = Ios27Spacing.xxl) {
    Spacer(Modifier.height(height))
}
