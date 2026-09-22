package com.snapconverter.app.ui.theme.ios27

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * iOS 27 design tokens, transcribed from seunghan91/ios27-design-system
 * (measured off Apple's official iOS 27 UI kit 27.0.2 via Figma REST).
 * Values: spacing.json / radius, colors.json, typography.json,
 * animations.json, materials.json (Liquid Glass retuned at WWDC 2026).
 */
object Ios27Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** Standard iPhone content side margin (measured: 16pt on 402pt frame). */
    val contentMargin = 16.dp
}

object Ios27Radius {
    val xs = 4.dp
    val sm = 8.dp
    val md = 10.dp
    val lg = 12.dp
    val xl = 16.dp
    val xxl = 20.dp
    val xxxl = 26.dp

    /** Liquid Glass Large/Medium corner radius (measured 34pt). */
    val glass = 34.dp
    val full = 100.dp
}

/** iOS 27 semantic label colors (alpha applied over base backgrounds). */
object Ios27Colors {
    // Tint: systemMint — also SnapConverter's brand family.
    val mintLight = androidx.compose.ui.graphics.Color(0xFF00A394)     // mint tuned for white-text-less light UI
    val mintDark = androidx.compose.ui.graphics.Color(0xFF00DAC3)
    val inkOnMint = androidx.compose.ui.graphics.Color(0xFF00342E)

    // Qualcomm hardware badge keeps the gold identity (iOS yellow family).
    val goldLight = androidx.compose.ui.graphics.Color(0xFF8A6200)
    val goldDark = androidx.compose.ui.graphics.Color(0xFFFFD600)

    // Labels.
    val labelPrimaryLight = androidx.compose.ui.graphics.Color(0xFF000000)
    val labelPrimaryDark = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val labelSecondaryLight = androidx.compose.ui.graphics.Color(0xFF6C6C70)  // #3c3c43 @60% on white
    val labelSecondaryDark = androidx.compose.ui.graphics.Color(0xFFA8A8B3)   // #ebebf5 @70% on black
    val labelTertiaryLight = androidx.compose.ui.graphics.Color(0xFFB0B0B5)   // @30%
    val labelTertiaryDark = androidx.compose.ui.graphics.Color(0xFF565660)

    // Grouped backgrounds.
    val groupedLight = androidx.compose.ui.graphics.Color(0xFFF2F2F7)
    val groupedDarkBase = androidx.compose.ui.graphics.Color(0xFF000000)
    val groupedDarkElevated = androidx.compose.ui.graphics.Color(0xFF1C1C1E)
    val groupedDarkTertiary = androidx.compose.ui.graphics.Color(0xFF2C2C2E)

    // Separators (nonOpaque).
    val separatorLight = androidx.compose.ui.graphics.Color(0x1F000000) // black @12%
    val separatorDark = androidx.compose.ui.graphics.Color(0x2BFFFFFF)  // white @17%

    // Liquid Glass fills (materials.json regular.large).
    val glassBgLight = androidx.compose.ui.graphics.Color(0xB3FFFFFF)   // white @0.7
    val glassBgDark = androidx.compose.ui.graphics.Color(0xB31A1A1A)    // #1a1a1a @0.7
    val glassOverlayLight = androidx.compose.ui.graphics.Color(0x1ABFBFBF)
    val rimLight = androidx.compose.ui.graphics.Color(0xFFDBDBDB)
    val rimDark = androidx.compose.ui.graphics.Color(0xFFA6A6A6)
    val specularLight = androidx.compose.ui.graphics.Color(0x59FFFFFF)  // brighter specular (iOS 27)
    val specularDark = androidx.compose.ui.graphics.Color(0x2EFFFFFF)

    // Backdrop mesh behind glass (so the material has something to refract).
    val backdropLight = listOf(
        androidx.compose.ui.graphics.Color(0xFFDDF5F0),
        androidx.compose.ui.graphics.Color(0xFFE9F6F3),
        androidx.compose.ui.graphics.Color(0xFFF2F2F7),
    )
    val backdropDark = listOf(
        androidx.compose.ui.graphics.Color(0xFF04211D),
        androidx.compose.ui.graphics.Color(0xFF0A1512),
        androidx.compose.ui.graphics.Color(0xFF000000),
    )
}

/**
 * The iOS 27 transparency slider: ultra clear (1) → fully tinted (0).
 * opacityScale = 1.6 − 1.1·t, blurScale = 0.5 + 0.8·t (materials.json _derive).
 */
enum class GlassPreset(val slider: Float) {
    FullyTinted(0f),
    Tinted(0.25f),
    Default(0.5f),
    Clear(0.75f),
    UltraClear(1f);

    val opacityScale: Float get() = 1.6f - 1.1f * slider
    val blurScale: Float get() = 0.5f + 0.8f * slider
}

/** iOS spring presets (animations.json) mapped to Compose spring specs. */
object Ios27Motion {
    /** response 0.3 / damping 0.8 — tab indicator, buttons. */
    val Snappy = spring<Float>(dampingRatio = 0.8f, stiffness = 700f)

    /** response 0.5 / damping 0.65 — launch, notification. */
    val Bouncy = spring<Float>(dampingRatio = 0.65f, stiffness = 380f)

    /** response 0.55 / damping 0.825 — sheet, page transition. */
    val Gentle = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)

    /** response 0.25 / damping 1.0 — alerts, menus (no bounce). */
    val Stiff = spring<Float>(dampingRatio = 1f, stiffness = 1200f)

    /** Sheet present/dismiss durations. */
    const val SheetPresentMs = 500
    const val SheetDismissMs = 300
    const val AlertPresentMs = 200
    const val ContentFadeMs = 220

    /** The CSS snappy curve (0.34, 1.56, 0.64, 1.0) as a Compose easing. */
    val SnappyEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val AppleDefaultEasing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
}

/** iOS 27 text styles (SF scale; Roboto stands in for SF Pro on Android). */
object Ios27Type {
    val largeTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = 0.4.sp,
    )
    val title1 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.38.sp,
    )
    val title2 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.26).sp,
    )
    val title3 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.45).sp,
    )
    val headline = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.43).sp,
    )
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.43).sp,
    )
    val callout = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.31).sp,
    )
    val subheadline = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.23).sp,
    )
    val footnote = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.08).sp,
    )
    val footnoteEmphasized = footnote.copy(fontWeight = FontWeight.SemiBold)
    val caption1 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    )
    val caption2 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.06.sp,
    )
}
