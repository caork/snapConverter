package com.snapconverter.app.ui.theme.ios27

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass, applied the way the design system says to apply it: glass is a
 * property of the **navigation layer** only — the top bar and the bottom action
 * platter. Content (cards, rows, chips, metric tiles) stays opaque, because
 * material stacked on material compounds blur and kills contrast.
 *
 * Android has no backdrop-filter for sibling content, and the kit itself ships
 * a blur-free *hard* scroll edge for exactly that case, so the chrome uses a
 * near-opaque fill instead of faking a blur — a 90%-transparent bar with
 * unblurred text ghosting through reads as a bug, not as glass. Fill opacity
 * still follows the iOS 27 transparency slider through
 * [GlassPreset.opacityScale], so "ultra clear" really does let content show.
 *
 * [ios27BarChrome] is the whole material: a fill plus the edge treatment, faded
 * in once content scrolls under the bar (the iOS 27 uniform toolbar). The top
 * edge gets the hairline; the bottom bar gets the soft scroll-edge gradient.
 */
@Composable
fun Modifier.ios27BarChrome(
    active: Boolean,
    edge: BarEdge,
): Modifier {
    val palette = LocalIos27Palette.current
    val preset = LocalGlassPreset.current
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = Ios27Motion.Gentle,
        label = "chromeFade",
    )
    val fillAlpha = (palette.chrome.alpha * preset.opacityScale).coerceAtMost(1f)
    return this.drawBehind {
        if (progress <= 0.01f) return@drawBehind
        val fill = palette.chrome.copy(alpha = fillAlpha * progress)
        when (edge) {
            BarEdge.Top -> {
                // Uniform toolbar: flat fill plus the hairline at the content edge.
                drawRect(color = fill)
                drawLine(
                    color = palette.separator.copy(
                        alpha = palette.separator.alpha * progress,
                    ),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = Ios27Metrics.separator.toPx(),
                )
            }
            BarEdge.Bottom -> {
                // Soft scroll edge: content fades into the bar instead of
                // hitting a hard line.
                val fade = minOf(size.height * 0.4f, 28.dp.toPx())
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, fill),
                        startY = 0f,
                        endY = fade,
                    ),
                    size = androidx.compose.ui.geometry.Size(size.width, fade),
                )
                drawRect(
                    color = fill,
                    topLeft = Offset(0f, fade),
                    size = androidx.compose.ui.geometry.Size(
                        size.width,
                        (size.height - fade).coerceAtLeast(0f),
                    ),
                )
            }
        }
    }
}

enum class BarEdge { Top, Bottom }
