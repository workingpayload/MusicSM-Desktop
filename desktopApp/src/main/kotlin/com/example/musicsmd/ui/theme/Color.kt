package com.example.musicsmd.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/** Theme-aware colour tokens — thin accessors over [LocalMusicSmPalette], ported from mobile. */
val AppBackground: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.background
val SurfaceLowest: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.surfaceLowest
val SurfaceLow: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.surfaceLow
val SurfaceContainer: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.surfaceContainer
val SurfaceHigh: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.surfaceHigh
val SurfaceHighest: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.surfaceHighest

val Coral: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.accent
val Teal: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.teal
val Lavender: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.lavender

val OnSurfaceVariantPink: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.onSurfaceVariant
val OnDark: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.onSurface
val OnDarkVariant: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.onSurfaceVariant
val OnDarkMuted: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.onSurfaceMuted
val DividerColor: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.divider

val GlassFill: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.glassFill
val GlassFillStrong: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.glassFillStrong
val GlassStroke: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.glassStroke
val GlassStrokeSoft: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.glassStrokeSoft
val OverlayTint: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.overlayTint
val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalMusicSmPalette.current.onAccent
