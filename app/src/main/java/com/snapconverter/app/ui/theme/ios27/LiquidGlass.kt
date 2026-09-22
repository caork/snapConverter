package com.snapconverter.app.ui.theme.ios27

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass, iOS 27 retune: translucent fill + darkened rim outline +
 * brighter specular highlight on the top edge + soft drop shadow. The iOS 27
 * transparency slider scales fill opacity (and the backdrop blur radius).
 *
 * Transcribed from materials.json (regular.large): bg white/#1a1a1a @0.7,
 * rim #dbdbdb/#a6a6a6, shadow blur 48 offsetY 8.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    dark: Boolean,
    preset: GlassPreset,
    shadowElevation: Dp = 10.dp,
    prominent: Boolean = false,
): Modifier {
    val glassShape = shape
    val elevation = shadowElevation
    return drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val bg = if (dark) Ios27Colors.glassBgDark else Ios27Colors.glassBgLight
    val alpha = (bg.alpha * preset.opacityScale).coerceAtMost(1f)
    // Prominent ≈ glassProminent: nearly opaque backing for primary controls.
    val fillAlpha = if (prominent) alpha.coerceAtLeast(0.9f) else alpha
    drawOutline(outline, color = bg.copy(alpha = fillAlpha))
    if (!dark) {
        drawOutline(
            outline,
            color = Ios27Colors.glassOverlayLight,
            alpha = (preset.opacityScale * 0.1f).coerceAtMost(1f),
        )
    }
    // Darkened edge: a 1dp ring in the rim color separates glass from content.
    drawOutline(
        outline,
        color = if (dark) Ios27Colors.rimDark else Ios27Colors.rimLight,
        alpha = 0.6f,
        style = Stroke(width = 1.dp.toPx()),
    )
    // Brighter specular: light catching the top edge, fading out by mid-height.
    drawOutline(
        outline,
        brush = Brush.verticalGradient(
            colors = listOf(
                if (dark) Ios27Colors.specularDark else Ios27Colors.specularLight,
                Color.Transparent,
            ),
            startY = 0f,
            endY = size.height * 0.45f,
        ),
        style = Stroke(width = 1.5.dp.toPx()),
    )
}.graphicsLayer {
    this.shadowElevation = elevation.toPx()
    this.shape = glassShape
    clip = false
}
}

/** The system-wide transparency slider value, provided by the theme. */
val LocalGlassPreset = staticCompositionLocalOf { GlassPreset.Default }

val LocalIsDark = staticCompositionLocalOf { true }

/**
 * Wallpaper-like backdrop the glass refracts: a calm mint-to-neutral mesh with
 * soft color blobs, blurred by the transparency preset's blur scale
 * (API 31+; the unblurred mesh remains below that). This mirrors iOS
 * wallpapers sitting under Liquid Glass chrome.
 */
@Composable
fun Ios27Backdrop(modifier: Modifier = Modifier) {
    val dark = LocalIsDark.current
    val preset = LocalGlassPreset.current
    val colors = if (dark) Ios27Colors.backdropDark else Ios27Colors.backdropLight
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    // graphicsLayer precedes the draws so they render INTO the
                    // blurred layer.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.graphicsLayer {
                            // iOS 27 stronger diffusion: radius follows the slider.
                            val radius = 26f * preset.blurScale
                            val effect = android.graphics.RenderEffect.createBlurEffect(
                                radius,
                                radius,
                                android.graphics.Shader.TileMode.CLAMP,
                            )
                            renderEffect = effect.asComposeRenderEffect()
                        }
                    } else {
                        Modifier
                    },
                )
                .background(Brush.linearGradient(colors))
                .drawBehind {
                    drawCircle(
                        color = if (dark) Color(0x3F00DAC3) else Color(0x5900C8B3),
                        radius = size.minDimension * 0.55f,
                        center = Offset(size.width * 0.8f, size.height * 0.12f),
                    )
                    drawCircle(
                        color = if (dark) Color(0x3300D2E0) else Color(0x4000C3D0),
                        radius = size.minDimension * 0.45f,
                        center = Offset(size.width * 0.1f, size.height * 0.85f),
                    )
                    drawCircle(
                        color = if (dark) Color(0x2EFFD600) else Color(0x2EFFCC00),
                        radius = size.minDimension * 0.3f,
                        center = Offset(size.width * 0.35f, size.height * 0.45f),
                    )
                },
        )
    }
}
