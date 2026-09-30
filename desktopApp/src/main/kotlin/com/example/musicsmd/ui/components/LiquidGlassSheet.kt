package com.example.musicsmd.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.musicsmd.ui.theme.SurfaceLow
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import kotlin.math.roundToInt

/**
 * A floating Liquid Glass card that slides up over a dimmed scrim — the mobile app's
 * `OutputPickerSheet` card, generalised so every Now Playing sheet (output, sleep timer) is the
 * same piece of glass. The screen behind it is blurred, saturated and bent by a lens at the card's
 * rounded edge, the way Apple's Liquid Glass refracts what it sits on.
 *
 * [backdrop] must record the content *behind* the sheet (artwork and controls) and must not
 * contain the sheet itself; drawn in-window rather than as a popup, whose separate window could not
 * sample it at all. Drag it down or click the scrim to dismiss.
 */
@Composable
fun LiquidGlassSheet(
    visible: Boolean,
    backdrop: Backdrop?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(SCRIM_FADE_MS)),
            exit = fadeOut(tween(SCRIM_FADE_MS)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it } +
                fadeIn(tween(SCRIM_FADE_MS)),
            exit = slideOutVertically(tween(SHEET_EXIT_MS)) { it } + fadeOut(tween(SHEET_EXIT_MS)),
        ) {
            GlassCard(backdrop = backdrop, onDismiss = onDismiss, content = content)
        }
    }
}

@Composable
private fun GlassCard(
    backdrop: Backdrop?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dismissDistancePx = with(LocalDensity.current) { DRAG_DISMISS_DISTANCE.toPx() }
    var dragY by remember { mutableFloatStateOf(0f) }
    // Without a real blur behind it the pane would show the controls straight through the text,
    // so with no recording available it falls back to an opaque surface.
    val glass = backdrop != null && isGlassAllowed()
    val surface = if (glass) Color.Black.copy(alpha = 0.30f) else SurfaceLow
    val shape = RoundedCornerShape(SHEET_CORNER)

    val base = Modifier
        .widthIn(max = SHEET_MAX_WIDTH)
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 10.dp)
        .offset { IntOffset(0, dragY.roundToInt()) }
        .draggable(
            orientation = Orientation.Vertical,
            state = rememberDraggableState { delta -> dragY = (dragY + delta).coerceAtLeast(0f) },
            onDragStopped = { velocity ->
                if (dragY > dismissDistancePx || velocity > DRAG_DISMISS_VELOCITY) onDismiss() else dragY = 0f
            },
        )
        // Swallow clicks so tapping the card never reaches the scrim behind it.
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    val surfaced = if (glass) {
        base.drawBackdrop(
            backdrop = backdrop!!,
            shape = { shape },
            effects = {
                vibrancy()
                blur(BLUR_RADIUS.toPx())
                lens(
                    refractionHeight = LENS_HEIGHT.toPx(),
                    refractionAmount = LENS_AMOUNT.toPx(),
                    depthEffect = true,
                )
            },
            highlight = { Highlight.Default },
            onDrawSurface = { drawRect(surface) },
        )
    } else {
        base.clip(shape).background(surface)
    }

    Column(
        surfaced
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp, bottom = 16.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.35f)),
        )
        Spacer(Modifier.height(14.dp))
        content()
    }
}

private const val SCRIM_FADE_MS = 200
private const val SHEET_EXIT_MS = 220
private val SHEET_CORNER = 32.dp
private val BLUR_RADIUS = 10.dp
private val LENS_HEIGHT = 24.dp
private val LENS_AMOUNT = 48.dp
private val DRAG_DISMISS_DISTANCE = 96.dp
private const val DRAG_DISMISS_VELOCITY = 1_200f

/** A phone-width card; a window-wide pane of glass would read as a banner, not a sheet. */
private val SHEET_MAX_WIDTH = 560.dp
