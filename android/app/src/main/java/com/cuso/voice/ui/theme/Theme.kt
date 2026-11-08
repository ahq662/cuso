package com.cuso.voice.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CusoColors = darkColorScheme(
    primary = Primary,
    onPrimary = PrimaryInk,
    secondary = Primary2,
    onSecondary = PrimaryInk,
    background = BgBase,
    onBackground = Text,
    surface = BgSurface,
    onSurface = Text,
    surfaceVariant = BgElevated,
    onSurfaceVariant = TextDim,
    outline = Border,
    error = Danger,
    onError = DangerInk,
)

@Composable
fun CusoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CusoColors,
        typography = CusoTypography,
        content = content,
    )
}
