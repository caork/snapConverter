package com.snapconverter.app.ui.theme.ios27

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * iOS 27 design tokens from seunghan91/ios27-design-system (measured off
 * Apple's iOS / iPadOS 27 Figma kit 27.0.2): colors.json, spacing.json,
 * typography.json, animations.json, materials.json.
 *
 * Screens read colors through [LocalIos27Palette] and never name a raw hex
 * value, so light and dark stay in lockstep.
 */
object Ios27Spacing {
    /** 8pt grid: 4, 8, 12, 16, 20, 24, 32. */
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** iPhone content side margin. */
    val margin = 16.dp

    /** Grouped-list row insets: 16pt horizontal, 11pt vertical. */
    val rowH = 16.dp
    val rowV = 11.dp
}

object Ios27Radius {
    val xs = 4.dp
    val sm = 8.dp

    /** Text field. */
    val field = 10.dp

    /** Semantic card / button radius — used by grouped list groups. */
    val card = 12.dp
    val xl = 16.dp
    val xxl = 20.dp

    /** Alert, menu, context menu. */
    val alert = 14.dp

    /** Liquid Glass regular, floating sheet top. */
    val glass = 34.dp
    val sheet = 38.dp
    val full = 999.dp
}

/** Measured component metrics (several grew from iOS 26 to 27). */
object Ios27Metrics {
    val navBar = 54.dp
    val navBarLarge = 125.dp
    val toolbarBottom = 84.dp
    val listRow = 52.dp
    val listRowTall = 68.dp
    val segmented = 32.dp
    val buttonSmall = 34.dp
    val buttonRegular = 44.dp
    val buttonLarge = 50.dp
    val progressTrack = 4.dp
    val sliderThumb = 28.dp
    val touchTarget = 44.dp
    val separator = 0.5.dp
    val separatorInset = 16.dp
    val grabberWidth = 58.dp
    val grabberHeight = 4.dp
}

/** Semantic colors for one appearance. */
@Immutable
data class Ios27Palette(
    val dark: Boolean,
    // Labels.
    val label: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val labelQuaternary: Color,
    // Grouped backgrounds — the Settings-style list idiom this app uses.
    val groupedBackground: Color,
    val groupedContent: Color,
    val groupedContentInner: Color,
    // Fills for controls sitting on content.
    val fill: Color,
    val fillSecondary: Color,
    val fillTertiary: Color,
    val fillQuaternary: Color,
    val separator: Color,
    val separatorOpaque: Color,
    // Accent: the launcher icon's blue in light, its cyan in dark. The kit's
    // systemMint is the only token deliberately replaced — an app's accent is
    // its brand, and the icon is the brand.
    val tint: Color,
    val tintText: Color,
    val tintFill: Color,
    val onTintFill: Color,
    val tintSoft: Color,
    // Status.
    val green: Color,
    val red: Color,
    val redText: Color,
    val yellow: Color,
    val yellowText: Color,
    // Chrome (nav bar / bottom bar) and its shadow.
    val chrome: Color,
    val chromeShadow: Color,
    /** Drop shadow under content cards. Transparent in dark, where the card is
     *  already lighter than its background. */
    val cardShadow: Color,
    val segmentedThumb: Color,
    val overlayDim: Color,
)

/** Raw token values, referenced only by [ios27Palette]. */
private object Raw {
    /** Sampled from the launcher icon: the blue tile's mid tone and its deep
     *  end, and the cyan tile's mid tone. */
    val brandBlue = Color(0xFF1361F8)
    val brandBlueDeep = Color(0xFF0B4FE0)
    val brandCyan = Color(0xFF2AD3FE)
    val brandCyanBright = Color(0xFF47EAFD)
    val greenLight = Color(0xFF34C759)
    val greenDark = Color(0xFF30D158)
    val redLight = Color(0xFFFF383C)
    val redDark = Color(0xFFFF4245)
    val redIcLight = Color(0xFFE9152D)
    val yellowLight = Color(0xFFFFCC00)
    val yellowDark = Color(0xFFFFD600)
    val yellowIcLight = Color(0xFFA16A00)

    val labelLight = Color(0xFF000000)
    val labelDark = Color(0xFFFFFFFF)
    val labelBaseLight = Color(0xFF3C3C43)
    val labelBaseDark = Color(0xFFEBEBF5)

    val groupedPrimaryLight = Color(0xFFF2F2F7)
    val groupedSecondaryLight = Color(0xFFFFFFFF)
    val groupedPrimaryDark = Color(0xFF000000)
    val groupedSecondaryDark = Color(0xFF1C1C1E)
    val groupedTertiaryDark = Color(0xFF2C2C2E)

    val fillLight = Color(0xFF787878)
    val fillDark = Color(0xFF787880)
    val fill3 = Color(0xFF767680)
    val fill4 = Color(0xFF747480)

    val separatorOpaqueLight = Color(0xFFC6C6C8)
    val separatorOpaqueDark = Color(0xFF38383A)
}

fun ios27Palette(dark: Boolean): Ios27Palette = if (dark) {
    Ios27Palette(
        dark = true,
        label = Raw.labelDark,
        labelSecondary = Raw.labelBaseDark.copy(alpha = 0.70f),
        labelTertiary = Raw.labelBaseDark.copy(alpha = 0.30f),
        labelQuaternary = Raw.labelBaseDark.copy(alpha = 0.16f),
        groupedBackground = Raw.groupedPrimaryDark,
        groupedContent = Raw.groupedSecondaryDark,
        groupedContentInner = Raw.groupedTertiaryDark,
        fill = Raw.fillDark.copy(alpha = 0.36f),
        fillSecondary = Raw.fillDark.copy(alpha = 0.32f),
        fillTertiary = Raw.fill3.copy(alpha = 0.24f),
        fillQuaternary = Raw.fill3.copy(alpha = 0.18f),
        separator = Color.White.copy(alpha = 0.17f),
        separatorOpaque = Raw.separatorOpaqueDark,
        tint = Raw.brandCyan,
        tintText = Raw.brandCyanBright,
        tintFill = Raw.brandCyan,
        onTintFill = Color(0xFF00202E),
        tintSoft = Raw.brandCyan.copy(alpha = 0.18f),
        green = Raw.greenDark,
        red = Raw.redDark,
        redText = Raw.redDark,
        yellow = Raw.yellowDark,
        yellowText = Raw.yellowDark,
        chrome = Color.Black.copy(alpha = 0.94f),
        chromeShadow = Color.Black.copy(alpha = 0.45f),
        cardShadow = Color.Transparent,
        segmentedThumb = Color.White.copy(alpha = 0.27f),
        overlayDim = Color(0xFF121212).copy(alpha = 0.56f),
    )
} else {
    Ios27Palette(
        dark = false,
        label = Raw.labelLight,
        labelSecondary = Raw.labelBaseLight.copy(alpha = 0.60f),
        labelTertiary = Raw.labelBaseLight.copy(alpha = 0.30f),
        labelQuaternary = Raw.labelBaseLight.copy(alpha = 0.18f),
        groupedBackground = Raw.groupedPrimaryLight,
        groupedContent = Raw.groupedSecondaryLight,
        groupedContentInner = Raw.groupedPrimaryLight,
        fill = Raw.fillLight.copy(alpha = 0.20f),
        fillSecondary = Raw.fillDark.copy(alpha = 0.16f),
        fillTertiary = Raw.fill3.copy(alpha = 0.12f),
        fillQuaternary = Raw.fill4.copy(alpha = 0.08f),
        separator = Color.Black.copy(alpha = 0.12f),
        separatorOpaque = Raw.separatorOpaqueLight,
        tint = Raw.brandBlue,
        // The mid blue is 4.2:1 as text on white, so labels, links and filled
        // buttons take the deep end of the icon's gradient instead.
        tintText = Raw.brandBlueDeep,
        tintFill = Raw.brandBlueDeep,
        onTintFill = Color.White,
        tintSoft = Raw.brandBlue.copy(alpha = 0.12f),
        green = Raw.greenLight,
        red = Raw.redLight,
        redText = Raw.redIcLight,
        yellow = Raw.yellowLight,
        yellowText = Raw.yellowIcLight,
        chrome = Color.White.copy(alpha = 0.94f),
        chromeShadow = Color.Black.copy(alpha = 0.25f),
        cardShadow = Color(0xFF0A2A66).copy(alpha = 0.10f),
        segmentedThumb = Color.White,
        overlayDim = Color(0xFF29293A).copy(alpha = 0.23f),
    )
}

val LocalIos27Palette = staticCompositionLocalOf { ios27Palette(dark = false) }

/**
 * The launcher artwork's own colours, for when the app draws its own mark. Not
 * part of the palette: the icon looks the same in both appearances, exactly as
 * it does on the home screen.
 */
object Ios27Brand {
    val markTop = Color(0xFFFEFEFE)
    val markBottom = Color(0xFFB4DCFC)
}

/**
 * The iOS 27 system transparency slider, ultra clear (1) → fully tinted (0).
 * Chrome opacity scales with it; content surfaces are opaque either way.
 */
enum class GlassPreset(val slider: Float) {
    FullyTinted(0f),
    Tinted(0.25f),
    Default(0.5f),
    Clear(0.75f),
    UltraClear(1f);

    /** materials.json `_derive`: opacityScale = 1.6 − 1.1·t. */
    val opacityScale: Float get() = 1.6f - 1.1f * slider
}

val LocalGlassPreset = staticCompositionLocalOf { GlassPreset.Default }

val LocalIsDark = staticCompositionLocalOf { false }

/** animations.json spring presets mapped to Compose specs. */
object Ios27Motion {
    /** response 0.3 / damping 0.8 — buttons, segmented thumb, tab indicator. */
    val Snappy = spring<Float>(dampingRatio = 0.8f, stiffness = 700f)

    /** response 0.5 / damping 0.65 — launch, notification. */
    val Bouncy = spring<Float>(dampingRatio = 0.65f, stiffness = 380f)

    /** response 0.55 / damping 0.825 — sheets, page transitions. */
    val Gentle = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)

    /** response 0.25 / damping 1.0 — alerts, menus. */
    val Stiff = spring<Float>(dampingRatio = 1f, stiffness = 1200f)

    const val SheetPresentMs = 500
    const val SheetDismissMs = 300
    const val AlertPresentMs = 200
    const val ContentFadeMs = 220

    val AppleDefault = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
}

/** SF Pro text styles; Roboto stands in for SF on Android. */
object Ios27Type {
    private val sf = FontFamily.SansSerif

    val largeTitle = TextStyle(
        fontFamily = sf,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = 0.4.sp,
    )
    val title1 = TextStyle(
        fontFamily = sf,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.38.sp,
    )
    val title2 = TextStyle(
        fontFamily = sf,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.26).sp,
    )
    val title3 = TextStyle(
        fontFamily = sf,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.45).sp,
    )
    val headline = TextStyle(
        fontFamily = sf,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.43).sp,
    )
    val body = TextStyle(
        fontFamily = sf,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.43).sp,
    )
    val callout = TextStyle(
        fontFamily = sf,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.31).sp,
    )
    val subheadline = TextStyle(
        fontFamily = sf,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.23).sp,
    )
    val footnote = TextStyle(
        fontFamily = sf,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.08).sp,
    )
    val caption1 = TextStyle(
        fontFamily = sf,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    )
    val caption2 = TextStyle(
        fontFamily = sf,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.06.sp,
    )

    val footnoteEmphasized = footnote.copy(fontWeight = FontWeight.SemiBold)
    val caption1Emphasized = caption1.copy(fontWeight = FontWeight.SemiBold)
    val subheadlineEmphasized = subheadline.copy(fontWeight = FontWeight.SemiBold)

    /** Numeric readouts (bitrate, QP, metrics) keep digits from reflowing. */
    val monoDigits = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )
}
