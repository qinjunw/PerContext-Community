package com.percontext.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.percontext.app.domain.appearance.AppTheme

@Immutable
data class PerContextPalette(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val action: Color,
    val onAction: Color,
    val border: Color,
    val shelf: Color,
    val accent: Color,
    val accentSurface: Color,
    val softSurface: Color,
    val softBorder: Color,
    val hero: Color,
    val heroInk: Color,
    val heroMuted: Color,
    val heroAction: Color,
    val onHeroAction: Color,
    val heroWave: Color,
    val heroWaveAlt: Color,
    val disabledSurface: Color,
    val recordIndicator: Color,
    val isDark: Boolean,
)

private val SkyLight = PerContextPalette(
    background = Color(0xFFE3EEF6),
    surface = Color(0xFFFFFEFA),
    ink = Color(0xFF19374D),
    muted = Color(0xFF526B7B),
    action = Color(0xFFC74D52),
    onAction = Color(0xFFFFFFFF),
    border = Color(0xFFA5BDCC),
    shelf = Color(0xFFCFDFEA),
    accent = Color(0xFF226D7A),
    accentSurface = Color(0xFFD0E7EC),
    softSurface = Color(0xFFEEF4F7),
    softBorder = Color(0xFFC5D7E1),
    hero = Color(0xFFFFFEFA),
    heroInk = Color(0xFF19374D),
    heroMuted = Color(0xFF526B7B),
    heroAction = Color(0xFFC74D52),
    onHeroAction = Color(0xFFFFFFFF),
    heroWave = Color(0xFFE7F1F7),
    heroWaveAlt = Color(0xFFDCEBF5),
    disabledSurface = Color(0xFFE1E7EB),
    recordIndicator = Color(0xFFC74D52),
    isDark = false,
)

private val SkyDark = PerContextPalette(
    background = Color(0xFF111D29),
    surface = Color(0xFF1C2D3C),
    ink = Color(0xFFEDF4F6),
    muted = Color(0xFFACBFCC),
    action = Color(0xFFF2958E),
    onAction = Color(0xFF3A1D20),
    border = Color(0xFF4B6375),
    shelf = Color(0xFF344C60),
    accent = Color(0xFF9AD5DB),
    accentSurface = Color(0xFF2A4557),
    softSurface = Color(0xFF182735),
    softBorder = Color(0xFF334958),
    hero = Color(0xFF1C2D3C),
    heroInk = Color(0xFFEDF4F6),
    heroMuted = Color(0xFFACBFCC),
    heroAction = Color(0xFFF2958E),
    onHeroAction = Color(0xFF3A1D20),
    heroWave = Color(0xFF20374B),
    heroWaveAlt = Color(0xFF29435B),
    disabledSurface = Color(0xFF2D4252),
    recordIndicator = Color(0xFFF2958E),
    isDark = true,
)

private val SeaSaltLight = PerContextPalette(
    background = Color(0xFFF0EEE7),
    surface = Color(0xFFFFFCF5),
    ink = Color(0xFF293D42),
    muted = Color(0xFF5A6C70),
    action = Color(0xFF247C85),
    onAction = Color(0xFFFFFFFF),
    border = Color(0xFFADBCBA),
    shelf = Color(0xFFD3DCD2),
    accent = Color(0xFF246D73),
    accentSurface = Color(0xFFD5E4DA),
    softSurface = Color(0xFFF8F6EF),
    softBorder = Color(0xFFCDD4C8),
    hero = Color(0xFF2E6265),
    heroInk = Color(0xFFFFFCF5),
    heroMuted = Color(0xFFE0ECE6),
    heroAction = Color(0xFFFFFCF5),
    onHeroAction = Color(0xFF28595E),
    heroWave = Color(0xFF356D70),
    heroWaveAlt = Color(0xFF417B7C),
    disabledSurface = Color(0xFFE0E5DD),
    recordIndicator = Color(0xFFB65A42),
    isDark = false,
)

private val SeaSaltDark = PerContextPalette(
    background = Color(0xFF192323),
    surface = Color(0xFF253332),
    ink = Color(0xFFF2F0E6),
    muted = Color(0xFFB5C2BA),
    action = Color(0xFF83C7C9),
    onAction = Color(0xFF17373A),
    border = Color(0xFF526A64),
    shelf = Color(0xFF415A51),
    accent = Color(0xFFADCCBC),
    accentSurface = Color(0xFF344D43),
    softSurface = Color(0xFF212E2B),
    softBorder = Color(0xFF3E524A),
    hero = Color(0xFF2C4140),
    heroInk = Color(0xFFF2F0E6),
    heroMuted = Color(0xFFC4D5C9),
    heroAction = Color(0xFFDCE7D7),
    onHeroAction = Color(0xFF244B49),
    heroWave = Color(0xFF334C49),
    heroWaveAlt = Color(0xFF3D5853),
    disabledSurface = Color(0xFF3C4D45),
    recordIndicator = Color(0xFFE5A38A),
    isDark = true,
)

private val LavenderLight = PerContextPalette(
    background = Color(0xFFEAEAF3),
    surface = Color(0xFFFDFAFF),
    ink = Color(0xFF30374C),
    muted = Color(0xFF60677D),
    action = Color(0xFF6869A7),
    onAction = Color(0xFFFFFFFF),
    border = Color(0xFFB1B4CE),
    shelf = Color(0xFFD2D3E7),
    accent = Color(0xFF5B5C8B),
    accentSurface = Color(0xFFDDDCF0),
    softSurface = Color(0xFFF3F1F8),
    softBorder = Color(0xFFCFCFE0),
    hero = Color(0xFFFDFAFF),
    heroInk = Color(0xFF30374C),
    heroMuted = Color(0xFF60677D),
    heroAction = Color(0xFF6869A7),
    onHeroAction = Color(0xFFFFFFFF),
    heroWave = Color(0xFFEDEAF7),
    heroWaveAlt = Color(0xFFE2DFF2),
    disabledSurface = Color(0xFFE1E0ED),
    recordIndicator = Color(0xFFB85D6B),
    isDark = false,
)

private val LavenderDark = PerContextPalette(
    background = Color(0xFF1B1D2B),
    surface = Color(0xFF292D40),
    ink = Color(0xFFF1EFF8),
    muted = Color(0xFFBABFD5),
    action = Color(0xFFB6B3ED),
    onAction = Color(0xFF29263E),
    border = Color(0xFF575E7E),
    shelf = Color(0xFF454B68),
    accent = Color(0xFFC3BDED),
    accentSurface = Color(0xFF3E405B),
    softSurface = Color(0xFF242738),
    softBorder = Color(0xFF444960),
    hero = Color(0xFF292D40),
    heroInk = Color(0xFFF1EFF8),
    heroMuted = Color(0xFFBABFD5),
    heroAction = Color(0xFFB6B3ED),
    onHeroAction = Color(0xFF29263E),
    heroWave = Color(0xFF33354E),
    heroWaveAlt = Color(0xFF3B3D59),
    disabledSurface = Color(0xFF3E425B),
    recordIndicator = Color(0xFFE6A1AC),
    isDark = true,
)

fun paletteFor(theme: AppTheme, darkTheme: Boolean): PerContextPalette = when (theme) {
    AppTheme.SKY -> if (darkTheme) SkyDark else SkyLight
    AppTheme.SEA_SALT -> if (darkTheme) SeaSaltDark else SeaSaltLight
    AppTheme.LAVENDER -> if (darkTheme) LavenderDark else LavenderLight
}

private val LocalPerContextPalette = staticCompositionLocalOf { SkyLight }

object PerContextTheme {
    val colors: PerContextPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalPerContextPalette.current
}

@Composable
fun PerContextTheme(
    theme: AppTheme = AppTheme.SKY,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = remember(theme, darkTheme) { paletteFor(theme, darkTheme) }
    val scheme = remember(palette) { palette.colorScheme() }
    CompositionLocalProvider(LocalPerContextPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(),
            content = content,
        )
    }
}

internal fun PerContextPalette.colorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = if (isDark) action else lerp(action, ink, 0.18f),
        onPrimary = onAction,
        primaryContainer = accentSurface,
        onPrimaryContainer = ink,
        secondary = accent,
        onSecondary = if (isDark) background else surface,
        secondaryContainer = accentSurface,
        onSecondaryContainer = accent,
        tertiary = recordIndicator,
        onTertiary = if (isDark) background else surface,
        tertiaryContainer = softSurface,
        onTertiaryContainer = ink,
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = softSurface,
        onSurfaceVariant = muted,
        surfaceTint = Color.Transparent,
        outline = muted,
        outlineVariant = softBorder,
        inverseSurface = ink,
        inverseOnSurface = surface,
        inversePrimary = if (isDark) onAction else surface,
        surfaceDim = background,
        surfaceBright = surface,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = softSurface,
        surfaceContainerHighest = accentSurface,
    )
}

val CanvasColor: Color
    @Composable
    @ReadOnlyComposable
    get() = PerContextTheme.colors.background

val SurfaceColor: Color
    @Composable
    @ReadOnlyComposable
    get() = PerContextTheme.colors.surface

val InkColor: Color
    @Composable
    @ReadOnlyComposable
    get() = PerContextTheme.colors.ink

val MutedColor: Color
    @Composable
    @ReadOnlyComposable
    get() = PerContextTheme.colors.muted

val RecordColor: Color
    @Composable
    @ReadOnlyComposable
    get() = PerContextTheme.colors.recordIndicator

val LocalColor: Color
    @Composable
    @ReadOnlyComposable
    get() = PerContextTheme.colors.accent

val HairlineColor: Color
    @Composable
    @ReadOnlyComposable
    get() = PerContextTheme.colors.border

val AcrylicBackColor: Color
    @Composable
    @ReadOnlyComposable
    get() = PerContextTheme.colors.shelf

val AcrylicEdgeColor: Color
    @Composable
    @ReadOnlyComposable
    get() = PerContextTheme.colors.border

val ActionSurfaceColor: Color
    @Composable
    @ReadOnlyComposable
    get() = PerContextTheme.colors.accentSurface
