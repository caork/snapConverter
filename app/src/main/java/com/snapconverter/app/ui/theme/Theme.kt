package com.snapconverter.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.snapconverter.app.ui.theme.ios27.GlassPreset
import com.snapconverter.app.ui.theme.ios27.Ios27Colors
import com.snapconverter.app.ui.theme.ios27.Ios27Type
import com.snapconverter.app.ui.theme.ios27.LocalGlassPreset
import com.snapconverter.app.ui.theme.ios27.LocalIsDark

/** Theme mode preference, System / Light / Dark tri-state. */
enum class SnapThemeMode { SYSTEM, LIGHT, DARK }

/**
 * iOS 27 typographic scale (SF styles) mapped onto the Material 3 slots used
 * by existing components. display/headline carry the large titles; label
 * styles carry footnote/caption emphasis.
 */
private val Type = Typography(
    headlineLarge = Ios27Type.largeTitle,
    headlineMedium = Ios27Type.title1,
    headlineSmall = Ios27Type.title2,
    titleLarge = Ios27Type.title3,
    titleMedium = Ios27Type.headline,
    titleSmall = Ios27Type.subheadline.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    bodyLarge = Ios27Type.body,
    bodyMedium = Ios27Type.callout,
    bodySmall = Ios27Type.subheadline,
    labelLarge = Ios27Type.headline,
    labelMedium = Ios27Type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    labelSmall = Ios27Type.caption1.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
)

/**
 * iOS 27 semantic palette: grouped backgrounds, label ramp, separator outline,
 * systemMint tint (which doubles as the SnapConverter brand). OLED keeps the
 * pure-black base; dark elevated surfaces match #1c1c1e/#2c2c2e.
 */
private fun ios27LightScheme() = lightColorScheme(
    primary = Ios27Colors.mintLight,
    onPrimary = Ios27Colors.inkOnMint,
    primaryContainer = Ios27Colors.mintLight,
    onPrimaryContainer = Ios27Colors.inkOnMint,
    secondary = Ios27Colors.mintLight,
    background = Ios27Colors.groupedLight,
    surface = androidx.compose.ui.graphics.Color.White,
    onBackground = Ios27Colors.labelPrimaryLight,
    onSurface = Ios27Colors.labelPrimaryLight,
    onSurfaceVariant = Ios27Colors.labelSecondaryLight,
    error = androidx.compose.ui.graphics.Color(0xFFFF3B30),
    outline = Ios27Colors.separatorLight,
    outlineVariant = Ios27Colors.separatorLight,
    tertiary = Ios27Colors.goldLight,
    onTertiary = androidx.compose.ui.graphics.Color.White,
)

private fun ios27DarkScheme(oled: Boolean) = darkColorScheme(
    primary = Ios27Colors.mintDark,
    onPrimary = Ios27Colors.inkOnMint,
    primaryContainer = Ios27Colors.mintDark,
    onPrimaryContainer = Ios27Colors.inkOnMint,
    secondary = Ios27Colors.mintDark,
    background = if (oled) Ios27Colors.groupedDarkBase else androidx.compose.ui.graphics.Color(0xFF050506),
    surface = if (oled) Ios27Colors.groupedDarkBase else Ios27Colors.groupedDarkElevated,
    onBackground = Ios27Colors.labelPrimaryDark,
    onSurface = Ios27Colors.labelPrimaryDark,
    onSurfaceVariant = Ios27Colors.labelSecondaryDark,
    error = androidx.compose.ui.graphics.Color(0xFFFF453A),
    outline = Ios27Colors.separatorDark,
    outlineVariant = Ios27Colors.separatorDark,
    tertiary = Ios27Colors.goldDark,
    onTertiary = Ios27Colors.inkOnMint,
)

/**
 * App theme on the iOS 27 design language: grouped-background palette,
 * SF-scale typography, and the Liquid Glass transparency slider exposed to
 * every glass surface via [LocalGlassPreset].
 */
@Composable
fun SnapConverterTheme(
    themeMode: SnapThemeMode = SnapThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
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
    val base = if (dark) ios27DarkScheme(oledBlack) else ios27LightScheme()
    val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val view = LocalView.current
        if (dark) dynamicDarkColorScheme(view.context) else dynamicLightColorScheme(view.context)
    } else {
        base
    }

    // Keep status / navigation bar icon contrast in sync with the theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view, dark) {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = scheme.background.toArgb()
                window.navigationBarColor = scheme.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            onDispose {}
        }
    }

    CompositionLocalProvider(
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
