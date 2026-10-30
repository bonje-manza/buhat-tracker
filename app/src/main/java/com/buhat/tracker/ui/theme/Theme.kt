package com.buhat.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

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
    onSurfaceVariant = BuhatOnSurfaceVariant,
    error = BuhatRed,
)

@Composable
fun BuhatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BuhatDarkColors,
        typography = BuhatTypography,
        content = content,
    )
}
