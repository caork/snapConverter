package com.snapconverter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.snapconverter.app.ui.theme.ios27.Ios27Metrics
import com.snapconverter.app.ui.theme.ios27.Ios27Radius
import com.snapconverter.app.ui.theme.ios27.Ios27Spacing
import com.snapconverter.app.ui.theme.ios27.Ios27Type
import com.snapconverter.app.ui.theme.ios27.LocalIos27Palette
import com.snapconverter.app.ui.theme.pressable

/**
 * The inset grouped list: iOS 27's primary content structure and the right
 * shape for a converter with this many parameters. A group is an opaque card on
 * the grouped background, rows are 52pt with a 16pt separator inset, and the
 * section header / footer carry the explanation instead of the row itself.
 */

/** Footnote header above a group. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val palette = LocalIos27Palette.current
    Text(
        text = text,
        style = Ios27Type.footnote,
        color = palette.labelSecondary,
        modifier = modifier.padding(
            start = Ios27Spacing.rowH,
            end = Ios27Spacing.rowH,
            bottom = Ios27Spacing.sm,
        ),
    )
}

/** Footnote explanation below a group. */
@Composable
fun GroupFooter(text: String, modifier: Modifier = Modifier) {
    val palette = LocalIos27Palette.current
    Text(
        text = text,
        style = Ios27Type.footnote,
        color = palette.labelSecondary,
        modifier = modifier.padding(
            start = Ios27Spacing.rowH,
            end = Ios27Spacing.rowH,
            top = Ios27Spacing.sm,
        ),
    )
}

class InsetGroupScope internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    /** Adds one row; separators between rows are drawn by the group. */
    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

@Composable
fun InsetGroup(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: InsetGroupScope.() -> Unit,
) {
    val palette = LocalIos27Palette.current
    val scope = InsetGroupScope().apply(content)
    Column(modifier = modifier.fillMaxWidth()) {
        if (header != null) SectionHeader(header)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Ios27Radius.card))
                .background(palette.groupedContent),
        ) {
            scope.rows.forEachIndexed { index, row ->
                if (index > 0) RowSeparator()
                row()
            }
        }
        if (footer != null) GroupFooter(footer)
    }
}

@Composable
fun RowSeparator(inset: Dp = Ios27Metrics.separatorInset) {
    val palette = LocalIos27Palette.current
    Box(
        modifier = Modifier
            .padding(start = inset)
            .fillMaxWidth()
            .height(Ios27Metrics.separator)
            .background(palette.separatorOpaque),
    )
}

/**
 * Row container: 52pt minimum, 16pt / 11pt insets, and the iOS press
 * highlight (the whole row fills, nothing scales).
 */
@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    minHeight: Dp = Ios27Metrics.listRow,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    val palette = LocalIos27Palette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .then(
                if (onClick != null) {
                    Modifier.pressable(interaction, enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .background(if (pressed && enabled) palette.fillQuaternary else Color.Transparent)
            .padding(horizontal = Ios27Spacing.rowH, vertical = Ios27Spacing.rowV),
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

/** Row body: title over an optional subtitle. */
@Composable
fun RowScope.RowLabel(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    tinted: Boolean = false,
    destructive: Boolean = false,
) {
    val palette = LocalIos27Palette.current
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = title,
            style = Ios27Type.body,
            color = when {
                !enabled -> palette.labelTertiary
                destructive -> palette.redText
                tinted -> palette.tintText
                else -> palette.label
            },
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = Ios27Type.footnote,
                color = palette.labelSecondary,
            )
        }
    }
}

/** Value + chevron row that opens a picker. */
@Composable
fun NavRow(
    title: String,
    value: String? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalIos27Palette.current
    ListRow(onClick = onClick, enabled = enabled) {
        RowLabel(title = title, subtitle = subtitle, enabled = enabled)
        if (value != null) {
            Spacer(Modifier.width(Ios27Spacing.sm))
            Text(
                text = value,
                style = Ios27Type.body,
                color = if (enabled) palette.labelSecondary else palette.labelTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Ios27Spacing.xs))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = palette.labelTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Tinted action row (预览 / 分享 / 再转一个). */
@Composable
fun ActionRow(
    title: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    ListRow(onClick = onClick, enabled = enabled) {
        RowLabel(
            title = title,
            enabled = enabled,
            tinted = !destructive,
            destructive = destructive,
        )
        trailing?.invoke(this)
    }
}

/** Static row: label plus free-form trailing content. */
@Composable
fun ValueRow(
    title: String,
    subtitle: String? = null,
    minHeight: Dp = Ios27Metrics.listRow,
    trailing: @Composable RowScope.() -> Unit,
) {
    ListRow(minHeight = minHeight) {
        RowLabel(title = title, subtitle = subtitle)
        Spacer(Modifier.width(Ios27Spacing.sm))
        trailing()
    }
}

/** Read-only detail row: label left, value right. */
@Composable
fun DetailRow(title: String, value: String, mono: Boolean = false) {
    val palette = LocalIos27Palette.current
    ListRow {
        Text(
            text = title,
            style = Ios27Type.body,
            color = palette.label,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Ios27Spacing.sm))
        Text(
            text = value,
            style = if (mono) Ios27Type.monoDigits else Ios27Type.body,
            color = palette.labelSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/** Full-width row hosting a control that needs the whole width. */
@Composable
fun StackRow(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Ios27Spacing.rowH, vertical = Ios27Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Ios27Spacing.sm),
        content = content,
    )
}

/** Checkmark row used by picker sheets. */
@Composable
fun CheckRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalIos27Palette.current
    ListRow(onClick = onClick, enabled = enabled) {
        RowLabel(title = title, subtitle = subtitle, enabled = enabled)
        if (selected) {
            Spacer(Modifier.width(Ios27Spacing.sm))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = palette.tintText,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Column of grouped sections with the standard 16pt margin. */
@Composable
fun GroupedList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .padding(horizontal = Ios27Spacing.margin),
        verticalArrangement = Arrangement.spacedBy(Ios27Spacing.xxl),
        content = content,
    )
}
