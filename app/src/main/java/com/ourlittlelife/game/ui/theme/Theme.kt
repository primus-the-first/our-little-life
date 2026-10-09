package com.ourlittlelife.game.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalOllColors = staticCompositionLocalOf { DayColors }

/** Access the design-system tokens: `OllTheme.colors.peach`, `OllType.body`. */
object OllTheme {
    val colors: OllColors
        @Composable @ReadOnlyComposable
        get() = LocalOllColors.current
}

/** Day and Night themes follow the phone's light or dark setting. */
@Composable
fun OurLittleLifeTheme(night: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (night) NightColors else DayColors
    val scheme = if (night) {
        darkColorScheme(
            primary = colors.peach, onPrimary = colors.onFill,
            background = colors.surface, onBackground = colors.ink,
            surface = colors.surfaceRaised, onSurface = colors.ink,
            error = colors.berry,
        )
    } else {
        lightColorScheme(
            primary = colors.peach, onPrimary = colors.onFill,
            background = colors.surface, onBackground = colors.ink,
            surface = colors.surfaceRaised, onSurface = colors.ink,
            error = colors.berry,
        )
    }
    CompositionLocalProvider(LocalOllColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
