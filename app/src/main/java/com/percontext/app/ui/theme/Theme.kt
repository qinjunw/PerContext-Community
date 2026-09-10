package com.percontext.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CanvasColor = Color(0xFFEDF7FF)
val SurfaceColor = Color(0xF2FFFFFF)
val InkColor = Color(0xFF0E2E56)
val MutedColor = Color(0xFF7B98B5)
val RecordColor = Color(0xFFFF4E50)
val LocalColor = Color(0xFF0FAFC0)
val HairlineColor = Color(0xFFD6EAF8)
val AcrylicBackColor = Color(0x99DDF4FF)
val AcrylicEdgeColor = Color(0xBFFFFFFF)
val ActionSurfaceColor = Color(0xBFE8F7FF)

private val PerContextColors = lightColorScheme(
    primary = RecordColor,
    onPrimary = Color.White,
    secondary = LocalColor,
    onSecondary = Color.White,
    background = CanvasColor,
    onBackground = InkColor,
    surface = SurfaceColor,
    onSurface = InkColor,
    surfaceVariant = Color(0xFFE8F5FC),
    onSurfaceVariant = MutedColor,
    outline = HairlineColor,
    error = Color(0xFFB3261E),
)

@Composable
fun PerContextTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PerContextColors,
        typography = Typography(),
        content = content,
    )
}
