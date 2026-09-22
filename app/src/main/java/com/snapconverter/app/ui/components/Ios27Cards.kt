package com.snapconverter.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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

/**
 * The workbench layer: cards, icon tiles, chips and parameter pills.
 *
 * The main screen is a tool, not a Settings pane, so it is built from discrete
 * cards that each own one decision — source, output format, compression, size —
 * instead of one long hairline-separated list. Inset grouped lists
 * ([InsetGroup]) are still the right idiom *inside* sheets, which really are
 * settings. Everything here stays opaque; glass belongs to the bars.
 */

/** Page body: 16pt side margin, 14pt between cards. */
@Composable
fun CardStack(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Ios27Spacing.margin),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/**
 * An opaque content card. Light mode gets a soft drop shadow so cards read as
 * lifted off the cool grey page; dark mode relies on the lighter surface, the
 * way iOS does.
 */
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(Ios27Spacing.lg),
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalIos27Palette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null && enabled) 0.98f else 1f,
        animationSpec = Ios27Motion.Snappy,
        label = "cardPress",
    )
    val shape = RoundedCornerShape(Ios27Radius.xxl)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (palette.dark) 0.dp else 6.dp,
                shape = shape,
                ambientColor = palette.cardShadow,
                spotColor = palette.cardShadow,
            )
            .clip(shape)
            .background(palette.groupedContent)
            .then(
                if (onClick != null) {
                    Modifier.pressable(interaction, enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(padding),
        content = content,
    )
}

/** Small caption above a card or a group of cards. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val palette = LocalIos27Palette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Ios27Spacing.xs, end = Ios27Spacing.xs, top = Ios27Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = Ios27Type.footnoteEmphasized,
            color = palette.labelSecondary,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(text = trailing, style = Ios27Type.footnote, color = palette.labelTertiary)
        }
    }
}

/** Colour role for an [IconTile]; the palette resolves the actual values. */
enum class TileTone { Brand, Green, Yellow, Red, Neutral }

@Composable
private fun tileContent(tone: TileTone): Color {
    val palette = LocalIos27Palette.current
    return when (tone) {
        TileTone.Brand -> palette.tintText
        TileTone.Green -> palette.green
        TileTone.Yellow -> palette.yellowText
        TileTone.Red -> palette.redText
        TileTone.Neutral -> palette.labelSecondary
    }
}

@Composable
private fun tileContainer(tone: TileTone): Color {
    val palette = LocalIos27Palette.current
    val alpha = if (palette.dark) 0.20f else 0.12f
    return when (tone) {
        TileTone.Brand -> palette.tint.copy(alpha = alpha)
        TileTone.Green -> palette.green.copy(alpha = alpha)
        TileTone.Yellow -> palette.yellow.copy(alpha = alpha + 0.04f)
        TileTone.Red -> palette.red.copy(alpha = alpha)
        TileTone.Neutral -> palette.fillTertiary
    }
}

/** Rounded-square glyph tile — the visual anchor of every card row. */
@Composable
fun IconTile(
    icon: ImageVector,
    tone: TileTone = TileTone.Brand,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(tileContainer(tone)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tileContent(tone),
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** Card header: icon tile, title, optional subtitle, trailing slot. */
@Composable
fun CardHeader(
    title: String,
    icon: ImageVector? = null,
    tone: TileTone = TileTone.Brand,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val palette = LocalIos27Palette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(icon = icon, tone = tone, size = 32.dp)
            Spacer(Modifier.width(Ios27Spacing.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = Ios27Type.headline, color = palette.label)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = Ios27Type.footnote,
                    color = palette.labelSecondary,
                )
            }
        }
        trailing()
    }
}

/**
 * Tappable row inside a card: icon tile, title over subtitle, chevron. This is
 * the "pick a source" / "open a sheet" affordance.
 */
@Composable
fun TileNavRow(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: TileTone = TileTone.Brand,
    subtitle: String? = null,
    enabled: Boolean = true,
    chevron: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalIos27Palette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ios27Radius.card))
            .pressable(enabled = enabled, onClick = onClick)
            .heightIn(min = Ios27Metrics.touchTarget)
            .padding(vertical = Ios27Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = icon, tone = if (enabled) tone else TileTone.Neutral)
        Spacer(Modifier.width(Ios27Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = Ios27Type.body,
                color = if (enabled) palette.label else palette.labelTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = Ios27Type.footnote,
                    color = palette.labelSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (chevron) {
            Spacer(Modifier.width(Ios27Spacing.sm))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = palette.labelTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Non-interactive row inside a card: icon tile, title, explanation. */
@Composable
fun InfoRow(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: TileTone = TileTone.Brand,
    subtitle: String? = null,
) {
    val palette = LocalIos27Palette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Ios27Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = icon, tone = tone, size = 32.dp)
        Spacer(Modifier.width(Ios27Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = Ios27Type.subheadline, color = palette.label)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = Ios27Type.footnote,
                    color = palette.labelSecondary,
                )
            }
        }
    }
}

/** Read-only pill for metadata: container and content come from the tone. */
@Composable
fun InfoChip(
    label: String,
    modifier: Modifier = Modifier,
    tone: TileTone = TileTone.Neutral,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Ios27Radius.full))
            .background(tileContainer(tone))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tileContent(tone),
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(Ios27Spacing.xs))
        }
        Text(
            text = label,
            style = Ios27Type.caption1Emphasized,
            color = tileContent(tone),
            maxLines = 1,
        )
    }
}

/** Tappable value pill with a disclosure caret — a card's "Options ⌄". */
@Composable
fun ValuePill(
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalIos27Palette.current
    Row(
        modifier = modifier
            .heightIn(min = 30.dp)
            .clip(RoundedCornerShape(Ios27Radius.full))
            .background(palette.fillTertiary)
            .pressable(enabled = enabled, onClick = onClick)
            .padding(start = Ios27Spacing.md, end = Ios27Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value,
            style = Ios27Type.subheadlineEmphasized,
            color = if (enabled) palette.label else palette.labelTertiary,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = palette.labelSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Slider inside a card: caption on the left, live value on the right in
 * headline weight, track below. The number is the point of the control, so it
 * gets the emphasis.
 */
@Composable
fun CardSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    val palette = LocalIos27Palette.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = label,
                style = Ios27Type.subheadline,
                color = palette.labelSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = Ios27Type.headline,
                color = if (enabled) palette.label else palette.labelTertiary,
                maxLines = 1,
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

/**
 * Selectable chip. Selected is a solid brand fill — a tint on a tint is
 * invisible, which is how the previous pass shipped an unreadable selection.
 */
@Composable
fun Chip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalIos27Palette.current
    val container = when {
        !enabled -> palette.fillQuaternary
        selected -> palette.tintFill
        else -> palette.fillTertiary
    }
    val content = when {
        !enabled -> palette.labelTertiary
        selected -> palette.onTintFill
        else -> palette.label
    }
    Row(
        modifier = modifier
            .heightIn(min = Ios27Metrics.buttonSmall)
            .clip(RoundedCornerShape(Ios27Radius.full))
            .background(container)
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (selected) Ios27Type.subheadlineEmphasized else Ios27Type.subheadline,
            color = content,
            maxLines = 1,
        )
        if (detail != null) {
            Spacer(Modifier.width(Ios27Spacing.sm))
            Text(
                text = detail,
                style = Ios27Type.caption1,
                color = if (selected) palette.onTintFill.copy(alpha = 0.75f) else palette.labelSecondary,
                maxLines = 1,
            )
        }
    }
}

/** Wrapping chip row, 8pt gaps. */
@Composable
fun ChipFlow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Ios27Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Ios27Spacing.sm),
    ) {
        content()
    }
}

/**
 * Parameter pill: one named value that opens a sheet. Two of these sit side by
 * side, which is how a tool shows its current settings without turning into a
 * list of rows.
 */
@Composable
fun ParamPill(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: TileTone = TileTone.Brand,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalIos27Palette.current
    ContentCard(
        modifier = modifier,
        padding = PaddingValues(horizontal = Ios27Spacing.md, vertical = Ios27Spacing.md),
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = icon, tone = if (enabled) tone else TileTone.Neutral, size = 30.dp)
            Spacer(Modifier.width(Ios27Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = Ios27Type.caption1,
                    color = palette.labelSecondary,
                    maxLines = 1,
                )
                Text(
                    text = value,
                    style = Ios27Type.subheadlineEmphasized,
                    color = if (enabled) palette.label else palette.labelTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = palette.labelTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Two parameter pills per line. */
@Composable
fun PillRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Ios27Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Square action tile with a glyph over a label (preview / open / share). */
@Composable
fun ActionTile(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: TileTone = TileTone.Brand,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalIos27Palette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Ios27Radius.xl))
            .background(palette.groupedContentInner)
            .pressable(enabled = enabled, onClick = onClick)
            .padding(vertical = Ios27Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Ios27Spacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tileContent(tone) else palette.labelTertiary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = Ios27Type.caption1,
            color = if (enabled) palette.label else palette.labelTertiary,
            maxLines = 1,
        )
    }
}

/** Hairline divider inside a card. */
@Composable
fun CardDivider(modifier: Modifier = Modifier) {
    val palette = LocalIos27Palette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Ios27Metrics.separator)
            .background(palette.separator),
    )
}
