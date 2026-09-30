package com.example.musicsmd.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private fun MusicSmPalette.toColorScheme() = darkColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = accent,
    onPrimaryContainer = onAccent,
    secondary = lavender,
    onSecondary = androidx.compose.ui.graphics.Color(0xFF490080),
    secondaryContainer = purpleContainer,
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFF0DBFF),
    tertiary = teal,
    onTertiary = androidx.compose.ui.graphics.Color(0xFF00382F),
    background = background,
    onBackground = onSurface,
    surface = surfaceLow,
    onSurface = onSurface,
    surfaceVariant = surfaceHigh,
    onSurfaceVariant = onSurfaceVariant,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceHigh,
    surfaceContainerHighest = surfaceHighest,
    surfaceBright = surfaceBright,
    outline = outline,
    outlineVariant = divider,
    error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
)

/**
 * App theme, ported from the mobile app's Stitch "Glassmorphic Music Streamer" design
 * (`ui/theme/Theme.kt`) — dark-only on desktop, byte-identical in colour. [amoled] swaps in the
 * pure-black base tiers; [accent], when set, re-tints the accent family (mobile's "Theme from
 * artwork").
 */
@Composable
fun MusicSMTheme(accent: Color? = null, amoled: Boolean = false, content: @Composable () -> Unit) {
    val palette = remember(accent, amoled) {
        val base = if (amoled) AmoledPalette else DarkPalette
        accent?.let { base.withAccent(it) } ?: base
    }
    CompositionLocalProvider(LocalMusicSmPalette provides palette) {
        MaterialTheme(colorScheme = palette.toColorScheme(), content = content)
    }
}
