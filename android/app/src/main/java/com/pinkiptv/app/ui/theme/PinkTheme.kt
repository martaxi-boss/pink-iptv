package com.pinkiptv.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PinkPrimary = Color(0xFFFF2D8D)
val MagentaDeep = Color(0xFFD4146C)
val PinkSoft = Color(0xFFFF8FC1)
val Ink = Color(0xFF141722)
val SurfaceDark = Color(0xFF1C2030)
val SurfaceRaised = Color(0xFF252A3A)
val Blush = Color(0xFFFDF7FB)
val White = Color(0xFFFFFFFF)

private val PinkDarkScheme = darkColorScheme(
    primary = PinkPrimary,
    onPrimary = White,
    secondary = PinkSoft,
    onSecondary = Ink,
    background = Ink,
    onBackground = White,
    surface = SurfaceDark,
    onSurface = White,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = Blush,
    error = Color(0xFFFFB4AB),
)

@Composable
fun PinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PinkDarkScheme,
        typography = Typography(),
        content = content,
    )
}
