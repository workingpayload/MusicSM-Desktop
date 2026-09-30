package com.example.musicsmd.ui.components

import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.musicsmd.ui.theme.Coral

/**
 * Material sliders in the app's palette. The stock inactive track uses `secondaryContainer`,
 * which is a saturated purple in the Stitch palette; this matches [AppleSeekBar]'s faint track.
 */
@Composable
fun musicSmSliderColors(): SliderColors = SliderDefaults.colors(
    thumbColor = Coral,
    activeTrackColor = Coral,
    inactiveTrackColor = Color.White.copy(alpha = 0.18f),
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
    disabledThumbColor = Color.White.copy(alpha = 0.35f),
    disabledActiveTrackColor = Color.White.copy(alpha = 0.3f),
    disabledInactiveTrackColor = Color.White.copy(alpha = 0.1f),
)
