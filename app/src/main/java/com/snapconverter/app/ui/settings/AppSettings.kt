package com.snapconverter.app.ui.settings

import android.content.Context
import com.snapconverter.app.ui.theme.SnapThemeMode
import com.snapconverter.app.ui.theme.ios27.GlassPreset

/**
 * Appearance preferences: theme mode, OLED black, and the iOS 27 transparency
 * preset. Material You dynamic color is deliberately absent — wallpaper-derived
 * theming would replace the iOS 27 semantic palette the whole UI is built on.
 */
object AppSettings {
    data class Options(
        val themeMode: SnapThemeMode = SnapThemeMode.SYSTEM,
        val oledBlack: Boolean = true,
        val glassPreset: GlassPreset = GlassPreset.Default,
    )

    private const val PREFS = "snapconverter_appearance"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_OLED = "oled_black"
    private const val KEY_GLASS = "glass_preset"

    fun load(context: Context): Options {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mode = runCatching {
            SnapThemeMode.valueOf(
                prefs.getString(KEY_THEME, SnapThemeMode.SYSTEM.name) ?: SnapThemeMode.SYSTEM.name,
            )
        }.getOrDefault(SnapThemeMode.SYSTEM)
        val glass = runCatching {
            GlassPreset.valueOf(
                prefs.getString(KEY_GLASS, GlassPreset.Default.name) ?: GlassPreset.Default.name,
            )
        }.getOrDefault(GlassPreset.Default)
        return Options(
            themeMode = mode,
            oledBlack = prefs.getBoolean(KEY_OLED, true),
            glassPreset = glass,
        )
    }

    fun save(context: Context, options: Options) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, options.themeMode.name)
            .putBoolean(KEY_OLED, options.oledBlack)
            .putString(KEY_GLASS, options.glassPreset.name)
            .apply()
    }
}
