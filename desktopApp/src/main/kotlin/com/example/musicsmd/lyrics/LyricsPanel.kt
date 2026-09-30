package com.example.musicsmd.lyrics

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicsm.domain.model.LyricLine
import com.example.musicsm.domain.model.LyricWord
import com.example.musicsm.domain.model.Lyrics
import com.example.musicsm.domain.model.Song
import com.example.musicsmd.ui.components.ArtworkImage
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.components.rememberDominantColorState
import com.example.musicsmd.ui.theme.Coral
import com.example.musicsmd.ui.theme.asThemeAccent
import com.example.musicsmd.ui.theme.GlassFillStrong
import com.example.musicsmd.ui.theme.OnDarkMuted
import java.util.Locale
import kotlin.math.abs

@Composable
fun LyricsPanel(
    lyricsState: LyricsUiState,
    positionMs: Long,
    song: Song?,
    lyricsOffsetMs: Long,
    onSeekMs: (Long) -> Unit,
    onClose: () -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onSetLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loaded = (lyricsState as? LyricsUiState.Loaded)?.lyrics
    val syncedPositionMs = (positionMs - lyricsOffsetMs).coerceAtLeast(0L)
    val activeIndex = if (loaded?.synced == true) {
        loaded.lines.indexOfLast { line -> line.timeMs?.let { it <= syncedPositionMs } == true }
    } else {
        -1
    }
    val activeLineTimeMs = loaded?.lines?.getOrNull(activeIndex)?.timeMs
    val accent = rememberDominantColorState(song?.artworkUrl, fallback = Coral)

    GlassPanel(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(32.dp),
        tint = GlassFillStrong,
        liquid = true,
    ) {
        // Mobile's lyrics backdrop: the cover's dominant tint under a darkening gradient, read in
        // the draw phase so the colour animation between tracks only repaints.
        Box(
            Modifier.matchParentSize().drawBehind {
                drawRect(accent.value.copy(alpha = 0.30f))
                drawRect(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.20f),
                        0.6f to Color.Black.copy(alpha = 0.35f),
                        1.0f to Color.Black.copy(alpha = 0.55f),
                    ),
                )
            },
        )
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            LyricsHeader(
                song = song,
                lyrics = loaded,
                accent = accent.value.asThemeAccent() ?: Coral,
                offsetMs = lyricsOffsetMs,
                activeLineTimeMs = activeLineTimeMs,
                positionMs = positionMs,
                onClose = onClose,
                onAdjustLyricsOffset = onAdjustLyricsOffset,
                onSetLyricsOffset = onSetLyricsOffset,
                onResetLyricsOffset = onResetLyricsOffset,
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (lyricsState) {
                LyricsUiState.Idle -> CenterMessage("Play a song to load lyrics.")
                LyricsUiState.Loading -> LoadingLyrics(accent = accent.value.asThemeAccent() ?: Coral)
                LyricsUiState.NotFound -> CenterMessage("No lyrics found for this track.")
                is LyricsUiState.Error -> CenterMessage(lyricsState.message)
                is LyricsUiState.Loaded -> LyricsLines(
                    lyrics = lyricsState.lyrics,
                    activeIndex = activeIndex,
                    syncedPositionMs = syncedPositionMs,
                    offsetMs = lyricsOffsetMs,
                    onSeekMs = onSeekMs,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LyricsHeader(
    song: Song?,
    lyrics: Lyrics?,
    accent: Color,
    offsetMs: Long,
    activeLineTimeMs: Long?,
    positionMs: Long,
    onClose: () -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onSetLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lyrics",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
                Text(
                    text = lyrics?.source?.let { "From ${it.label}" } ?: "Synced lyrics",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Hide lyrics", tint = Color.White.copy(alpha = 0.86f))
            }
        }

        if (song != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(url = song.artworkUrl, size = 44.dp, shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (lyrics?.synced == true) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                LyricsChip("-250ms") { onAdjustLyricsOffset(-250L) }
                LyricsChip("+250ms") { onAdjustLyricsOffset(250L) }
                LyricsChip("Sync to line") { activeLineTimeMs?.let { onSetLyricsOffset(positionMs - it) } }
                LyricsChip("Reset", leadingIcon = true, onClick = onResetLyricsOffset)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatLyricsOffset(offsetMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.74f),
                )
            }
            if (!lyrics.timingVerified && offsetMs == 0L) {
                Text(
                    text = "Timing is approximate. Use sync controls if the words drift.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LyricsChip(
    label: String,
    leadingIcon: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon) {
            Icon(Icons.Filled.RestartAlt, contentDescription = null, tint = Color.White.copy(alpha = 0.82f), modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.88f))
    }
}

@Composable
private fun LyricsLines(
    lyrics: Lyrics,
    activeIndex: Int,
    syncedPositionMs: Long,
    offsetMs: Long,
    onSeekMs: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            LyricsLine(
                line = line,
                isSynced = lyrics.synced,
                isActive = index == activeIndex,
                syncedPositionMs = syncedPositionMs,
                offsetMs = offsetMs,
                onSeekMs = onSeekMs,
            )
        }
        lyrics.source?.let { source ->
            item {
                Text(
                    text = "Lyrics provided by ${source.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.48f),
                    modifier = Modifier.padding(top = 18.dp, start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LyricsLine(
    line: LyricLine,
    isSynced: Boolean,
    isActive: Boolean,
    syncedPositionMs: Long,
    offsetMs: Long,
    onSeekMs: (Long) -> Unit,
) {
    val t by animateFloatAsState(
        targetValue = if (isActive || !isSynced) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "lyricActive",
    )
    val baseAlpha = if (isSynced) 0.34f else 0.86f
    val alpha = baseAlpha + (1f - baseAlpha) * t
    val modifier = Modifier
        .fillMaxWidth()
        .graphicsLayer {
            val s = 1f + if (isSynced) 0.055f * t else 0f
            scaleX = s
            scaleY = s
            this.alpha = alpha
        }
        .blur(if (isSynced && !isActive) 0.35.dp else 0.dp)
        .clip(RoundedCornerShape(14.dp))
        .clickable(enabled = isSynced && line.timeMs != null) {
            line.timeMs?.let { onSeekMs((it + offsetMs).coerceAtLeast(0L)) }
        }
        .padding(horizontal = 8.dp, vertical = 11.dp)

    if (isActive && line.words.isNotEmpty()) {
        KaraokeLineText(
            text = line.text,
            words = line.words,
            positionMs = syncedPositionMs,
            layerAlpha = alpha,
            modifier = modifier,
        )
    } else {
        Text(
            text = line.text.ifBlank { "…" },
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 31.sp,
            modifier = modifier,
        )
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LoadingLyrics(accent: Color) {
    val transition = rememberInfiniteTransition(label = "lyricsLoading")
    val pulse by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "lyricsLoadingPulse",
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(8) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (index % 3 == 0) 0.82f else 1f)
                    .height(if (index == 0) 28.dp else 22.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.08f),
                                Color.White.copy(alpha = 0.08f + pulse * 0.16f),
                                Color.White.copy(alpha = 0.08f),
                            ),
                        ),
                    ),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        CircularProgressIndicator(color = accent, modifier = Modifier.align(Alignment.CenterHorizontally).size(28.dp))
    }
}

private fun formatLyricsOffset(offsetMs: Long): String {
    if (offsetMs == 0L) return "0.00s"
    val sign = if (offsetMs > 0) "+" else "-"
    return "%s%.2fs".format(Locale.US, sign, abs(offsetMs) / 1_000f)
}

private const val KARAOKE_UNSUNG_ALPHA = 0.45f

@Composable
private fun KaraokeLineText(
    text: String,
    words: List<LyricWord>,
    positionMs: Long,
    layerAlpha: Float,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val fadePx = with(LocalDensity.current) { 16.dp.toPx() }
    val sung = sungCharOffset(words, positionMs)
    val unsungAlpha = KARAOKE_UNSUNG_ALPHA.coerceAtMost(layerAlpha) / layerAlpha.coerceAtLeast(0.01f)
    Box(modifier) {
        Text(
            text = text,
            color = Color.White.copy(alpha = unsungAlpha.coerceIn(0f, 1f)),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 31.sp,
            onTextLayout = { layout = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = text,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 31.sp,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    layout?.let { eraseUnsung(it, sung, fadePx) }
                },
        )
    }
}

internal fun sungCharOffset(words: List<LyricWord>, positionMs: Long): Float {
    var sung = 0f
    for (word in words) {
        if (positionMs >= word.endMs) {
            sung = word.charEnd.toFloat()
        } else {
            if (positionMs > word.startMs) {
                val fraction = (positionMs - word.startMs).toFloat() / (word.endMs - word.startMs)
                sung = word.charStart + (word.charEnd - word.charStart) * fraction
            }
            return sung
        }
    }
    return Float.POSITIVE_INFINITY
}

private fun DrawScope.eraseUnsung(layout: TextLayoutResult, sung: Float, fadePx: Float) {
    val len = layout.layoutInput.text.length
    if (len == 0 || sung >= len) return
    val firstErasedLine: Int
    if (sung <= 0f) {
        firstErasedLine = 0
    } else {
        val index = sung.toInt().coerceIn(0, len - 1)
        val fraction = sung - index
        val box = layout.getBoundingBox(index)
        val rtl = layout.getParagraphDirection(index) == ResolvedTextDirection.Rtl
        val line = layout.getLineForOffset(index)
        val top = layout.getLineTop(line)
        val height = layout.getLineBottom(line) - top
        if (rtl) {
            val x = box.right - box.width * fraction
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = x - fadePx / 2, endX = x + fadePx / 2),
                topLeft = Offset(0f, top),
                size = Size((x + fadePx / 2).coerceAtLeast(0f), height),
                blendMode = BlendMode.DstOut,
            )
        } else {
            val x = box.left + box.width * fraction
            val left = (x - fadePx / 2).coerceAtLeast(0f)
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = x - fadePx / 2, endX = x + fadePx / 2),
                topLeft = Offset(left, top),
                size = Size((size.width - left).coerceAtLeast(0f), height),
                blendMode = BlendMode.DstOut,
            )
        }
        firstErasedLine = line + 1
    }
    for (line in firstErasedLine until layout.lineCount) {
        val top = layout.getLineTop(line)
        drawRect(
            color = Color.Black,
            topLeft = Offset(0f, top),
            size = Size(size.width, layout.getLineBottom(line) - top),
            blendMode = BlendMode.DstOut,
        )
    }
}
