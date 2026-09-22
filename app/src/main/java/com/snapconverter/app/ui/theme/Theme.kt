package com.snapconverter.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.snapconverter.app.ui.theme.ios27.GlassPreset
import com.snapconverter.app.ui.theme.ios27.Ios27Palette
import com.snapconverter.app.ui.theme.ios27.Ios27Type
import com.snapconverter.app.ui.theme.ios27.LocalGlassPreset
import com.snapconverter.app.ui.theme.ios27.LocalIos27Palette
import com.snapconverter.app.ui.theme.ios27.LocalIsDark
import com.snapconverter.app.ui.theme.ios27.ios27Palette

/** Theme mode preference: System / Light / Dark. */
enum class SnapThemeMode { SYSTEM, LIGHT, DARK }

/** SF type scale mapped onto the Material slots the few M3 widgets still use. */
private val Type = Typography(
    headlineLarge = Ios27Type.largeTitle,
    headlineMedium = Ios27Type.title1,
    headlineSmall = Ios27Type.title2,
    titleLarge = Ios27Type.title3,
    titleMedium = Ios27Type.headline,
    titleSmall = Ios27Type.subheadlineEmphasized,
    bodyLarge = Ios27Type.body,
    bodyMedium = Ios27Type.callout,
    bodySmall = Ios27Type.footnote,
    labelLarge = Ios27Type.headline,
    labelMedium = Ios27Type.footnoteEmphasized,
    labelSmall = Ios27Type.caption1Emphasized,
)

/**
 * Maps the iOS palette onto the Material scheme so the remaining framework
 * widgets (alerts, sheets, text fields) inherit the same colors.
 */
private fun materialScheme(p: Ios27Palette) = if (p.dark) {
    darkColorScheme(
        primary = p.tintText,
        onPrimary = p.onTintFill,
        secondary = p.tintText,
        background = p.groupedBackground,
        onBackground = p.label,
        surface = p.groupedContent,
        onSurface = p.label,
        surfaceVariant = p.groupedContentInner,
        onSurfaceVariant = p.labelSecondary,
        surfaceContainerHigh = p.groupedContent,
        error = p.red,
        onError = Color.White,
        errorContainer = p.red.copy(alpha = 0.18f),
        onErrorContainer = p.redText,
        outline = p.separatorOpaque,
        outlineVariant = p.separator,
        tertiary = p.yellowText,
    )
} else {
    lightColorScheme(
        primary = p.tintText,
        onPrimary = p.onTintFill,
        secondary = p.tintText,
        background = p.groupedBackground,
        onBackground = p.label,
        surface = p.groupedContent,
        onSurface = p.label,
        surfaceVariant = p.groupedContentInner,
        onSurfaceVariant = p.labelSecondary,
        surfaceContainerHigh = p.groupedContent,
        error = p.red,
        onError = Color.White,
        errorContainer = p.red.copy(alpha = 0.12f),
        onErrorContainer = p.redText,
        outline = p.separatorOpaque,
        outlineVariant = p.separator,
        tertiary = p.yellowText,
    )
}

/**
 * App theme on the iOS 27 design language: grouped-background palette, the SF
 * type scale, and the system transparency slider exposed to the chrome through
 * [LocalGlassPreset].
 */
@Composable
fun SnapConverterTheme(
    themeMode: SnapThemeMode = SnapThemeMode.SYSTEM,
    oledBlack: Boolean = false,
    glassPreset: GlassPreset = GlassPreset.Default,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        SnapThemeMode.SYSTEM -> systemDark
        SnapThemeMode.LIGHT -> false
        SnapThemeMode.DARK -> true
    }
    // The grouped dark background is already pure black; the preference only
    // lifts the non-OLED case off it.
    val palette = ios27Palette(dark).let { base ->
        if (dark && !oledBlack) {
            base.copy(groupedBackground = Color(0xFF0B0B0C))
        } else {
            base
        }
    }
    val scheme = materialScheme(palette)

    // Edge-to-edge: the bars stay transparent, icon contrast follows the theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view, dark) {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            onDispose {}
        }
    }

    CompositionLocalProvider(
        LocalIos27Palette provides palette,
        LocalGlassPreset provides glassPreset,
        LocalIsDark provides dark,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Type,
            content = content,
        )
    }
}
