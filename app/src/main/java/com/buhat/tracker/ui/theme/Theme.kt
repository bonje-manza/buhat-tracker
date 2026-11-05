package com.buhat.tracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BuhatDarkColors = darkColorScheme(
    primary = BuhatOrange,
    onPrimary = BuhatBackground,
    secondary = BuhatLime,
    onSecondary = BuhatBackground,
    tertiary = BuhatAmber,
    onTertiary = BuhatBackground,
    background = BuhatBackground,
    onBackground = BuhatOnSurface,
    surface = BuhatSurface,
    onSurface = BuhatOnSurface,
    surfaceVariant = BuhatSurfaceVariant,
    surfaceContainerLow = Color(0xFF1C1C1C),
    onSurfaceVariant = BuhatOnSurfaceVariant,
    error = BuhatRed,
    onError = BuhatBackground,
)

private val BuhatDarkOnSecondary = Color(0xFF221C03)
private val BuhatDarkOnTertiary = Color(0xFF33260B)

private val BuhatLightColors = lightColorScheme(
    primary = Color(0xFFA43F16),
    onPrimary = Color.White,
    secondary = Color(0xFF617016),
    onSecondary = Color.White,
    tertiary = BuhatAmber,
    onTertiary = BuhatDarkOnTertiary,
    background = BuhatLightBackground,
    onBackground = BuhatOnLightSurface,
    surface = BuhatLightSurface,
    onSurface = BuhatOnLightSurface,
    surfaceVariant = BuhatLightSurfaceVariant,
    surfaceContainerLow = BuhatLightSurfaceVariant,
    onSurfaceVariant = BuhatOnLightSurfaceVariant,
    error = Color(0xFFB3261E),
    onError = Color.White,
)

// Default is Dark (roadmap); Settings wires the stored theme in a later phase.
@Composable
fun BuhatTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) BuhatDarkColors else BuhatLightColors,
        typography = BuhatTypography,
        content = content,
    )
}
