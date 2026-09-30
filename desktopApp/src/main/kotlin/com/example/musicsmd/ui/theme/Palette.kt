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

/** Pure-black variant for OLED panels, as on mobile: only the *base* tiers collapse to black. */
val AmoledPalette = DarkPalette.copy(
    background = Color.Black,
    surfaceLowest = Color.Black,
    surfaceLow = Color(0xFF0A0A0C),
    surfaceContainer = Color(0xFF121214),
    surfaceHigh = Color(0xFF1C1C1F),
    surfaceHighest = Color(0xFF26262A),
    surfaceBright = Color(0xFF2B2B30),
    glassFillStrong = Color(0x401C1C1F),
)

/**
 * Re-tints the palette around [seed] while keeping its surface tiers — mobile's `withAccent`
 * (dark branch). The artwork only drives the accent family, so the glassmorphic surfaces stay
 * recognisably MusicSM.
 */
fun MusicSmPalette.withAccent(seed: Color): MusicSmPalette = copy(
    accent = seed,
    accentDark = seed.shade(0.16f),
    accentLight = seed.tint(0.55f),
    onAccent = if (seed.isDarkEnoughForWhiteText()) Color.White else Color(0xFF1B1B1F),
)

/** Mixes [this] toward black by [amount]. */
private fun Color.shade(amount: Float): Color = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = alpha,
)

/** Mixes [this] toward white by [amount]. */
private fun Color.tint(amount: Float): Color = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)

/** Relative luminance test (WCAG-ish) deciding whether white or near-black content reads better. */
fun Color.isDarkEnoughForWhiteText(): Boolean {
    fun channel(c: Float) = if (c <= 0.03928f) c / 12.92f else Math.pow(
        ((c + 0.055f) / 1.055f).toDouble(), 2.4,
    ).toFloat()
    val luminance = 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
    return luminance < 0.45f
}
