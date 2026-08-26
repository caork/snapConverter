package com.snapconverter.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF071018)
private val Panel = Color(0xFF0E1A24)
private val Mint = Color(0xFF7CFFD4)
private val MintDim = Color(0xFF1FAE86)
private val Paper = Color(0xFFE7F3EE)
private val Danger = Color(0xFFFF6B6B)

private val Colors = darkColorScheme(
    primary = Mint,
    onPrimary = Ink,
    primaryContainer = MintDim,
    onPrimaryContainer = Ink,
    secondary = MintDim,
    background = Ink,
    surface = Panel,
    onBackground = Paper,
    onSurface = Paper,
    onSurfaceVariant = Color(0xFF9BB0A8),
    error = Danger,
    outline = Color(0xFF2A3C48),
)

private val Type = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.4.sp,
    ),
)

@Composable
fun SnapConverterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = Type,
        content = content,
    )
}
