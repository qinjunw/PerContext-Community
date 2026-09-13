package com.percontext.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.percontext.app.domain.appearance.AppTheme
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun bodyAndSecondaryTextRemainReadableOnEveryPageSurface() = everyPalette { palette ->
        listOf(palette.background, palette.surface, palette.softSurface).forEach { surface ->
            assertReadable(palette.ink, surface)
            assertReadable(palette.muted, surface)
        }
        assertReadable(palette.accent, palette.background)
        assertReadable(palette.accent, palette.surface)
    }

    @Test
    fun recordingTextAndBothButtonStatesRemainReadable() = everyPalette { palette ->
        assertReadable(palette.heroInk, palette.hero)
        assertReadable(palette.heroMuted, palette.hero)
        assertReadable(palette.onHeroAction, palette.heroAction)
        assertReadable(palette.surface, palette.ink)
    }

    @Test
    fun materialButtonsAndTextActionsRemainReadableInDialogsAndSettings() = everyPalette { palette ->
        val scheme = palette.colorScheme()
        assertReadable(scheme.onPrimary, scheme.primary)
        listOf(scheme.background, scheme.surface, scheme.surfaceContainerHigh).forEach { surface ->
            assertReadable(scheme.primary, surface)
            assertReadable(scheme.onSurfaceVariant, surface)
        }
        assertReadable(scheme.onSecondary, scheme.secondary)
        assertReadable(scheme.inverseOnSurface, scheme.inverseSurface)
    }

    private fun everyPalette(check: (PerContextPalette) -> Unit) {
        AppTheme.entries.forEach { theme ->
            listOf(false, true).forEach { darkTheme -> check(paletteFor(theme, darkTheme)) }
        }
    }

    private fun assertReadable(foreground: Color, background: Color) {
        val a = foreground.luminance()
        val b = background.luminance()
        val contrast = (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
        assertTrue("Contrast $contrast is below 4.5 for $foreground on $background", contrast >= 4.5f)
    }
}
