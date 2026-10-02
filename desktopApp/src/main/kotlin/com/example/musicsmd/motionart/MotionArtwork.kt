package com.example.musicsmd.motionart

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

@Composable
fun MotionArtwork(
    url: String,
    playing: Boolean,
    modifier: Modifier = Modifier,
    onRenderedChange: (Boolean) -> Unit = {},
) {
    key(url) {
        MotionArtworkContent(url, playing, modifier, onRenderedChange)
    }
}

@Composable
private fun MotionArtworkContent(
    url: String,
    playing: Boolean,
    modifier: Modifier,
    onRenderedChange: (Boolean) -> Unit,
) {
    val currentPlaying by rememberUpdatedState(playing)
    val player by produceState<MotionArtworkPlayer?>(null, url) {
        var opened: MotionArtworkPlayer? = null
        try {
            withContext(Dispatchers.IO) { opened = MotionArtworkPlayer.open() }
            withContext(Dispatchers.IO) { checkNotNull(opened).start(url, currentPlaying) }
            value = opened
            awaitCancellation()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            System.err.println("Animated artwork unavailable: ${error.message}")
        } finally {
            value = null
            withContext(NonCancellable + Dispatchers.IO) { opened?.release() }
        }
    }
    LaunchedEffect(player, playing) {
        withContext(Dispatchers.IO) { player?.setPlaying(playing) }
        val current = player
        if (current != null && playing && current.frame.value == null) {
            val firstFrame = withTimeoutOrNull(15_000L) { current.frame.filterNotNull().first() }
            if (firstFrame == null) {
                System.err.println("Animated artwork playback failed: no video frame within 15 seconds")
                withContext(NonCancellable + Dispatchers.IO) { current.release() }
            }
        }
    }
    val frame by (player?.frame ?: EmptyFrame).collectAsState()
    val rendered = frame != null
    val onRendered by rememberUpdatedState(onRenderedChange)
    LaunchedEffect(rendered) { onRendered(rendered) }
    DisposableEffect(url) { onDispose { onRendered(false) } }
    val opacity by animateFloatAsState(if (rendered) 1f else 0f, tween(500), label = "motionArtAlpha")
    val bitmap = remember(frame) {
        frame?.let {
            Image.makeRaster(
                ImageInfo(it.width, it.height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE),
                it.pixels,
                it.width * 4,
            ).toComposeImageBitmap()
        }
    }
    Box(modifier.clipToBounds()) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = opacity },
            )
        }
    }
}

private val EmptyFrame = kotlinx.coroutines.flow.MutableStateFlow<VideoFrame?>(null)
