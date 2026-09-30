package com.example.musicsmd.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicsmd.ui.theme.GlassFill
import com.example.musicsmd.ui.theme.GlassStroke
import com.example.musicsmd.ui.theme.GlassStrokeSoft
import com.example.musicsmd.ui.theme.LocalMusicSmPalette
import com.example.musicsmd.ui.theme.OverlayTint
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * Frosted-glass design system ported from the mobile app's `ui/components/Glass.kt`. Desktop
 * drops the Android-only "Liquid Glass" lens/backdrop refraction (RuntimeShader, API 33+) and the
 * low-RAM device gating, but keeps the same frosted-panel look: a blurred backdrop, a translucent
 * tint, a top-left gloss sheen and an additive rim.
 *
 * Shared [HazeState] for the whole window: background content registers as the blur source
 * ([glassBackdrop]), glass panels ([GlassPanel]) sample it.
 */
val LocalHazeState: ProvidableCompositionLocal<HazeState?> = compositionLocalOf { null }

@Composable
fun rememberHazeState(): HazeState = remember { HazeState() }

/** Marks this content as the backdrop that glass panels blur. No-op if no haze state is present. */
fun Modifier.glassBackdrop(state: HazeState?): Modifier =
    if (state != null) this.hazeSource(state) else this

/** Traces [shape]'s edge with an additive light line — a rim is light caught on an edge. */
private fun Modifier.glassRim(shape: Shape, brush: Brush, width: Dp): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(width.toPx())
    onDrawWithContent {
        drawContent()
        drawOutline(outline, brush, style = stroke, blendMode = BlendMode.Plus)
    }
}

/**
 * A frosted-glass panel: blurs whatever [LocalHazeState] content sits behind it, with a subtle
 * tint, a top-left gloss sheen and an additive rim, so it floats above the content it frosts.
 * Degrades to a translucent surface when no blur source is registered.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = GlassFill,
    liquid: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val hazeState = LocalHazeState.current
    val frosted = hazeState != null
    val hazeTint = LocalMusicSmPalette.current.hazeTint
    val style = remember(hazeTint, liquid) {
        HazeStyle(
            blurRadius = if (liquid) 8.dp else 20.dp,
            tint = HazeTint(hazeTint),
            noiseFactor = 0f,
        )
    }
    val sheen = remember {
        Brush.linearGradient(
            0.0f to Color.White.copy(alpha = 0.48f),
            0.16f to Color.White.copy(alpha = 0.14f),
            0.5f to Color.Transparent,
            1.0f to Color.White.copy(alpha = 0.10f),
            start = Offset.Zero,
            end = Offset.Infinite,
        )
    }
    val rim = remember {
        Brush.linearGradient(
            0.0f to Color.White.copy(alpha = RIM_ALPHA),
            0.55f to Color.White.copy(alpha = RIM_ALPHA * 0.25f),
            1.0f to Color.White.copy(alpha = RIM_ALPHA * 0.55f),
            start = Offset.Zero,
            end = Offset.Infinite,
        )
    }
    val shadowed = if (liquid) {
        modifier.shadow(
            elevation = 14.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.45f),
            spotColor = Color.Black.copy(alpha = 0.45f),
        )
    } else {
        modifier
    }

    val blur = remember(frosted, hazeState, style) {
        if (frosted) Modifier.hazeEffect(state = hazeState!!, style = style) else null
    }
    Box(modifier = shadowed.clip(shape)) {
        val backdrop = Modifier.matchParentSize()
        if (blur != null) {
            Box(backdrop.then(blur))
        } else {
            Box(backdrop.background(OverlayTint.copy(alpha = 0.08f)))
        }
        Box(backdrop.background(tint))
        if (liquid) Box(backdrop.background(sheen))
        Box(
            if (liquid) {
                backdrop.glassRim(shape, rim, RIM_WIDTH)
            } else {
                backdrop.border(BorderStroke(0.5.dp, Brush.verticalGradient(listOf(GlassStroke, GlassStrokeSoft))), shape)
            },
        )
        content()
    }
}

/** Peak opacity of the additive rim. Additive light saturates fast, so this stays low. */
private const val RIM_ALPHA = 0.48f
private val RIM_WIDTH = 0.8.dp
