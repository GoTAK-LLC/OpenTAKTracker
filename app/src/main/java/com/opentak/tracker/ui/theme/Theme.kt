package com.opentak.tracker.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark theme colors matching TAK operational aesthetic
val DarkBackground = Color(0xFF1C1C1E)
val DarkSurface = Color(0xFF2C2C2E)
val PanelBlack = Color(0xFF000000)
val PanelBorder = Color(0xFF007AFF)
val TextWhite = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8E8E93)
val ConnectedGreen = Color(0xFF34C759)
val ErrorRed = Color(0xFFFF3B30)
val WarningYellow = Color(0xFFFFD60A)
val ReconnectingOrange = Color(0xFFFF9500)

private val DarkColorScheme = darkColorScheme(
    primary = PanelBorder,
    onPrimary = TextWhite,
    secondary = TextSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = TextWhite,
    onSurface = TextWhite,
    error = ErrorRed,
    onError = TextWhite
)

@Composable
fun OpenTAKTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
