package com.example.musicsmd.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.example.musicsmd.ui.theme.ArtworkColors
import kotlin.math.abs

/**
 * Loads [url] and extracts a vibrant dominant colour for artwork-driven theming (Apple
 * Music-style), ported from the mobile app. Smoothly animates between tracks and falls back to
 * [fallback].
 *
 * Read the returned state's value inside a draw-phase lambda (e.g. `drawBehind`) where possible,
 * so the colour animation only re-draws instead of recomposing the caller.
 */
@Composable
fun rememberDominantColorState(url: String?, fallback: Color): State<Color> {
    var target by remember { mutableStateOf(fallback) }

    LaunchedEffect(url, fallback) {
        target = ArtworkColors.accentFor(url) ?: fallback
    }

    return animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 700),
        label = "dominantColor",
    )
}

/** Convenience wrapper that reads the animated value in composition. */
@Composable
fun rememberDominantColor(url: String?, fallback: Color): Color =
    rememberDominantColorState(url, fallback).value

/**
 * A stable colour for [seed] from a Spotify-like set — mobile's fallback while artwork loads or
 * when a page has no cover, so every album or song still gets its own colour.
 */
fun accentColorFor(seed: String?): Color {
    if (seed.isNullOrEmpty()) return Color(0xFF3A3A3A)
    val palette = listOf(
        0xFF1E3264, 0xFF8D67AB, 0xFFBA5D07, 0xFFE13300, 0xFF27856A,
        0xFF503750, 0xFFDC148C, 0xFF477D95, 0xFF7358FF, 0xFF608108,
    )
    val idx = abs(seed.hashCode()) % palette.size
    return Color(palette[idx])
}
