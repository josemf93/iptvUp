package com.iptvup.app.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0E0E12)
val SurfaceDark = Color(0xFF17171F)
val SurfaceElevated = Color(0xFF21212C)
val Primary = Color(0xFF7C4DFF)
val PrimarySoft = Color(0xFFB39DFF)
val Accent = Color(0xFF00E5C3)
val TextPrimary = Color(0xFFF5F5FA)
val TextSecondary = Color(0xFF9E9EAF)
val Danger = Color(0xFFFF5C6C)

private val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Primary.copy(alpha = 0.22f),
    onPrimaryContainer = PrimarySoft,
    secondary = Accent,
    onSecondary = Color(0xFF00201A),
    background = Background,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = Danger,
    onError = Color.White
)

@Composable
fun IptvUpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
