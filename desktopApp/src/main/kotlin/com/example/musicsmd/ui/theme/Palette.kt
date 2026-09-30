package com.example.musicsmd.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Bespoke colour tokens ported from the mobile app's "Stitch Glassmorphic Music Streamer" design
 * (`ui/theme/Palette.kt`). Material's `ColorScheme` doesn't cover the glass surfaces this UI
 * needs, so they live here and are published through [LocalMusicSmPalette] — byte-identical to
 * the mobile app's dark palette, since desktop stays dark-only for now.
 */
@Immutable
data class MusicSmPalette(
    val background: Color,
    val surfaceLowest: Color,
    val surfaceLow: Color,
    val surfaceContainer: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val surfaceBright: Color,
    val accent: Color,
    val accentDark: Color,
    val accentLight: Color,
    val teal: Color,
    val lavender: Color,
    val purpleContainer: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val onSurfaceMuted: Color,
    val divider: Color,
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassStroke: Color,
    val glassStrokeSoft: Color,
    val overlayTint: Color,
    val onAccent: Color,
    val hazeTint: Color,
)

/** The original Stitch "Glassmorphic Music Streamer" palette — byte-identical to mobile's dark theme. */
val DarkPalette = MusicSmPalette(
    background = Color(0xFF121318),
    surfaceLowest = Color(0xFF0D0E13),
    surfaceLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceHigh = Color(0xFF292A2F),
    surfaceHighest = Color(0xFF34343A),
    surfaceBright = Color(0xFF38393F),
    accent = Color(0xFFFF525E),
    accentDark = Color(0xFFD8323E),
    accentLight = Color(0xFFFFB3B2),
    teal = Color(0xFF00DFC1),
    lavender = Color(0xFFDDB7FF),
    purpleContainer = Color(0xFF6F00BE),
    onSurface = Color(0xFFE3E1E9),
    onSurfaceVariant = Color(0xFFE6BDBC),
    outline = Color(0xFFAD8887),
    onSurfaceMuted = Color(0x80FFFFFF),
    divider = Color(0x1FFFFFFF),
    glassFill = Color(0x1FFFFFFF),
    glassFillStrong = Color(0x40292A2F),
    glassStroke = Color(0x33FFFFFF),
    glassStrokeSoft = Color(0x1AFFFFFF),
    overlayTint = Color.White,
    onAccent = Color.White,
    hazeTint = Color(0x1AFFFFFF),
)

val LocalMusicSmPalette = staticCompositionLocalOf { DarkPalette }
