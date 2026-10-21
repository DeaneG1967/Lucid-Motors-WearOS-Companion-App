package com.droid.lucidgravitywatch.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LucidWatchDarkScheme = darkColorScheme(
    primary = EurekaGold,
    onPrimary = ObsidianBlack,
    secondary = CryoBlue,
    onSecondary = ObsidianBlack,
    tertiary = AuroraGreen,
    background = ObsidianBlack,
    onBackground = LucidPlatinum,
    surface = StealthSurface,
    onSurface = LucidPlatinum,
    surfaceVariant = StealthCard,
    onSurfaceVariant = LucidSilver,
    error = CrimsonAlert,
    onError = Color.White
)

@Composable
fun LucidGravityWatchTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LucidWatchDarkScheme,
        typography = Typography,
        content = content
    )
}
