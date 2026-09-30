package com.example.musicsmd.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The header gradient at the top of the Search page and every tab: [color] fading down to nothing.
 *
 * Drawn once, behind the pages rather than inside each of them. Two pages that each drew their own
 * would both be half-faded midway through a tab switch, and the gradient would dip and come back
 * on every switch. Both arguments are read while drawing, so easing them repaints the wash without
 * recomposing the app.
 */
@Composable
fun HeaderWash(color: () -> Color, opacity: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(HEADER_WASH_HEIGHT)
            .drawBehind {
                val o = opacity()
                if (o > 0f) {
                    drawRect(Brush.verticalGradient(listOf(color().copy(alpha = HEADER_WASH_ALPHA * o), Color.Transparent)))
                }
            },
    )
}

private const val HEADER_WASH_ALPHA = 0.45f

/** Search's header — title, field and padding — so the gradient fades out at the same depth on every tab. */
private val HEADER_WASH_HEIGHT = 134.dp
