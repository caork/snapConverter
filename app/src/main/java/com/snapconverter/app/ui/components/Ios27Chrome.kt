package com.snapconverter.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snapconverter.app.ui.theme.ios27.BarEdge
import com.snapconverter.app.ui.theme.ios27.Ios27Metrics
import com.snapconverter.app.ui.theme.ios27.Ios27Motion
import com.snapconverter.app.ui.theme.ios27.Ios27Radius
import com.snapconverter.app.ui.theme.ios27.Ios27Spacing
import com.snapconverter.app.ui.theme.ios27.Ios27Type
import com.snapconverter.app.ui.theme.ios27.LocalIos27Palette
import com.snapconverter.app.ui.theme.ios27.ios27BarChrome

/**
 * Screen chrome: an app header that collapses into the navigation bar on
 * scroll, the bottom action bar, and sheets. These are the only surfaces in the
 * app allowed to be translucent.
 *
 * The header carries the mark, the title and a one-line subtitle — the app
 * introduces itself, which a bare Settings-style large title does not do. The
 * bar's trailing buttons stay put while the header scrolls away under them.
 */
@Composable
fun Ios27Screen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalIos27Palette.current
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // The large title has scrolled away once it has travelled its own height.
    val collapseAt = remember(density) { with(density) { 44.dp.toPx() } }
    val collapsed = scroll.value > collapseAt
    val titleAlpha by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = Ios27Motion.Gentle,
        label = "inlineTitle",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.groupedBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
        ) {
            Spacer(
                Modifier
                    .statusBarsPadding()
                    .height(Ios27Metrics.navBar),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(1f - titleAlpha)
                    .padding(
                        start = Ios27Spacing.margin,
                        end = Ios27Spacing.margin,
                        bottom = Ios27Spacing.lg,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppMark(size = 42.dp)
                Spacer(Modifier.size(Ios27Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = Ios27Type.title2,
                        color = palette.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = Ios27Type.footnote,
                            color = palette.labelSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            content()
            Spacer(Modifier.height(if (bottomBar != null) 120.dp else Ios27Spacing.xxxl))
            Spacer(Modifier.navigationBarsPadding())
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .ios27BarChrome(active = collapsed, edge = BarEdge.Top),
        ) {
            Spacer(Modifier.statusBarsPadding())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Ios27Metrics.navBar)
                    .padding(horizontal = Ios27Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.size(Ios27Metrics.touchTarget))
                Text(
                    text = title,
                    style = Ios27Type.headline,
                    color = palette.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .alpha(titleAlpha),
                )
                trailing()
            }
        }

        if (bottomBar != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .ios27BarChrome(active = true, edge = BarEdge.Bottom),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = Ios27Spacing.margin,
                            vertical = Ios27Spacing.md,
                        ),
                ) {
                    bottomBar()
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

/**
 * Same chrome as [Ios27Screen], but the body is a lazy list.
 *
 * The scan can return hundreds of rows, each with a thumbnail, so that screen
 * cannot live in a scrolling [Column]. The header scrolls as the list's first
 * item; everything else — collapse threshold, bar chrome, bottom bar — behaves
 * exactly as it does on the workbench.
 */
@Composable
fun Ios27LazyScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showMark: Boolean = false,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable (() -> Unit)? = null,
    header: @Composable (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val palette = LocalIos27Palette.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val collapseAt = remember(density) { with(density) { 44.dp.toPx() } }
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > collapseAt
    val titleAlpha by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = Ios27Motion.Gentle,
        label = "inlineTitle",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.groupedBackground),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                Column {
                    Spacer(
                        Modifier
                            .statusBarsPadding()
                            .height(Ios27Metrics.navBar),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(1f - titleAlpha)
                            .padding(
                                start = Ios27Spacing.margin,
                                end = Ios27Spacing.margin,
                                bottom = Ios27Spacing.lg,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showMark) {
                            AppMark(size = 42.dp)
                            Spacer(Modifier.size(Ios27Spacing.md))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = Ios27Type.title2,
                                color = palette.label,
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
                    }
                    header?.invoke()
                }
            }
            content()
            item(key = "tail") {
                Spacer(Modifier.height(if (bottomBar != null) 120.dp else Ios27Spacing.xxxl))
                Spacer(Modifier.navigationBarsPadding())
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .ios27BarChrome(active = collapsed, edge = BarEdge.Top),
        ) {
            Spacer(Modifier.statusBarsPadding())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Ios27Metrics.navBar)
                    .padding(horizontal = Ios27Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.widthIn(min = Ios27Metrics.touchTarget),
                    verticalAlignment = Alignment.CenterVertically,
                ) { leading() }
                Text(
                    text = title,
                    style = Ios27Type.headline,
                    color = palette.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .alpha(titleAlpha),
                )
                trailing()
            }
        }

        if (bottomBar != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .ios27BarChrome(active = true, edge = BarEdge.Bottom),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = Ios27Spacing.margin,
                            vertical = Ios27Spacing.md,
                        ),
                ) {
                    bottomBar()
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

/** 58×4 grabber at 5pt from the sheet's top edge. */
@Composable
private fun SheetGrabber() {
    val palette = LocalIos27Palette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp, bottom = Ios27Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = Ios27Metrics.grabberWidth, height = Ios27Metrics.grabberHeight)
                .clip(RoundedCornerShape(Ios27Radius.full))
                .background(palette.labelTertiary),
        )
    }
}

/**
 * Sheet with the kit's 38pt top radius, grabber, and a 54pt toolbar carrying
 * the title and a single trailing action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Ios27Sheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    doneLabel: String = "完成",
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalIos27Palette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.groupedBackground,
        contentColor = palette.label,
        scrimColor = palette.overlayDim,
        shape = RoundedCornerShape(
            topStart = Ios27Radius.sheet,
            topEnd = Ios27Radius.sheet,
        ),
        dragHandle = { SheetGrabber() },
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Ios27Metrics.navBar)
                    .padding(horizontal = Ios27Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.size(Ios27Metrics.touchTarget))
                Text(
                    text = title,
                    style = Ios27Type.headline,
                    color = palette.label,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                PlainButton(text = doneLabel, emphasized = true, onClick = onDismiss)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = Ios27Spacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(Ios27Spacing.xxl),
                content = content,
            )
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

data class PickerOption<T>(
    val value: T,
    val label: String,
    val detail: String? = null,
    val enabled: Boolean = true,
)

/** Single-choice picker sheet — the iOS answer to a wall of chips. */
@Composable
fun <T> PickerSheet(
    title: String,
    options: List<PickerOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    footer: String? = null,
) {
    Ios27Sheet(title = title, onDismiss = onDismiss) {
        GroupedList {
            InsetGroup(footer = footer) {
                options.forEach { option ->
                    row {
                        CheckRow(
                            title = option.label,
                            subtitle = option.detail,
                            selected = option.value == selected,
                            enabled = option.enabled,
                            onClick = {
                                onSelect(option.value)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}
