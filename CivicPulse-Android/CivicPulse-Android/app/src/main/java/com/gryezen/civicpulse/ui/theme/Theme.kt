package com.gryezen.civicpulse.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Paper,
    primaryContainer = PaperDim,
    onPrimaryContainer = Navy,
    secondary = Saffron,
    onSecondary = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = PaperDim,
    onSurfaceVariant = TextMid,
    outline = HairlineDark,
    outlineVariant = Hairline,
    error = Red,
    onError = Paper
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C93E0),
    onPrimary = Ink,
    background = Color(0xFF14161C),
    onBackground = Color(0xFFEDEDE8),
    surface = Color(0xFF1D2027),
    onSurface = Color(0xFFEDEDE8),
    surfaceVariant = Color(0xFF262A33),
    onSurfaceVariant = Color(0xFFB8B8B0),
    outline = Color(0xFF454B57),
    error = Color(0xFFE0736C)
)

@Composable
fun CivicPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = CivicPulseTypography,
        content = content
    )
}
