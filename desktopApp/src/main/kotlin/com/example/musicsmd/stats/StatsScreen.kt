package com.example.musicsmd.stats

import com.example.musicsmd.ui.components.LocalBottomBarPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsm.domain.model.ArtistPlayCount
import com.example.musicsm.domain.model.DailyPlayCount
import com.example.musicsm.domain.model.HourlyPlayCount
import com.example.musicsm.domain.model.ListeningStats
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.model.SongPlayCount
import com.example.musicsm.domain.model.StatsRange
import com.example.musicsm.domain.repository.StatsRepository
import com.example.musicsmd.ui.components.ArtworkImage
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.components.SongRow
import com.example.musicsmd.ui.theme.Coral
import com.example.musicsmd.ui.theme.GlassFill
import com.example.musicsmd.ui.theme.GlassFillStrong
import com.example.musicsmd.ui.theme.Lavender
import com.example.musicsmd.ui.theme.OnAccent
import com.example.musicsmd.ui.theme.OnDarkMuted
import com.example.musicsmd.ui.theme.Teal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun StatsScreen(
    repository: StatsRepository,
    isLiked: (String) -> Boolean,
    onToggleLike: (Song) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onArtistSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var range by remember { mutableStateOf(StatsRange.LAST_4_WEEKS) }
    val stats by remember(repository, range) { repository.stats(range) }
        .collectAsState(ListeningStats(range = range))
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Listening stats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Your playback history, charts and streaks", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!stats.isEmpty) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear listening history", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        RangeChips(selected = range, onSelect = { range = it })

        if (stats.isEmpty) {
            EmptyStats(Modifier.weight(1f))
        } else {
            StatsBody(
                stats = stats,
                isLiked = isLiked,
                onToggleLike = onToggleLike,
                onPlaySongs = onPlaySongs,
                onArtistSearch = onArtistSearch,
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear listening history?") },
            text = { Text("This removes the append-only stats log. Your liked songs and playlists stay untouched.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.clear() }
                        confirmClear = false
                    },
                ) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatsBody(
    stats: ListeningStats,
    isLiked: (String) -> Boolean,
    onToggleLike: (Song) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onArtistSearch: (String) -> Unit,
) {
    val topSongs = remember(stats.topSongs) { stats.topSongs.map { it.song } }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp + LocalBottomBarPadding.current),
    ) {
        item { SummaryGrid(stats) }
        item { Highlights(stats) }
        if (stats.byDay.isNotEmpty()) item { ActivityChart(stats.byDay) }
        if (stats.topArtists.isNotEmpty()) item { TopArtistsRow(stats.topArtists, onArtistSearch) }
        if (stats.byHour.isNotEmpty()) item { ListeningClock(stats.byHour) }
        if (stats.topSongs.isNotEmpty()) {
            item { SectionTitle("Top songs") }
            itemsIndexed(stats.topSongs, key = { index, entry -> "$index-${entry.song.id}" }) { index, entry ->
                TopSongRow(
                    rank = index + 1,
                    entry = entry,
                    isLiked = isLiked(entry.song.id),
                    onToggleLike = { onToggleLike(entry.song) },
                    onClick = { onPlaySongs(topSongs, index) },
                )
            }
        }
    }
}

@Composable
private fun RangeChips(selected: StatsRange, onSelect: (StatsRange) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatsRange.entries.forEach { range ->
            val active = range == selected
            Text(
                text = range.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) OnAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) Coral else GlassFill)
                    .clickable { onSelect(range) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SummaryGrid(stats: ListeningStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(formatMinutes(stats.totalMs), "minutes", Coral, Modifier.weight(1f))
            MetricCard(formatCount(stats.totalPlays), "plays", Lavender, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(formatCount(stats.distinctSongs), "songs", Teal, Modifier.weight(1f))
            MetricCard(formatCount(stats.distinctArtists), "artists", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCard(value: String, label: String, accent: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier, shape = RoundedCornerShape(22.dp), tint = GlassFillStrong) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = accent)
            Text(label.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = OnDarkMuted)
        }
    }
}

@Composable
private fun Highlights(stats: ListeningStats) {
    val lines = buildList {
        if (stats.currentStreakDays > 1) add("${stats.currentStreakDays}-day listening streak")
        stats.peakHour?.let { add("Peak listening time: ${formatHour(it)}") }
        if (stats.range == StatsRange.ALL_TIME) stats.firstPlayedAt?.let { add("Tracking since ${formatDate(it)}") }
    }
    if (lines.isEmpty()) return
    GlassPanel(shape = RoundedCornerShape(22.dp), tint = GlassFill) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lines.forEach { Text(it, color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
private fun ActivityChart(byDay: List<DailyPlayCount>) {
    val bars = remember(byDay) {
        val counts = byDay.associate { it.epochDay to it.playCount }
        val last = maxOf(LocalDate.now().toEpochDay(), byDay.maxOf { it.epochDay })
        // Always span at least four weeks ending today, so a new history isn't one giant bar.
        val start = minOf(byDay.minOf { it.epochDay }, last - MIN_CHART_DAYS + 1)
            .coerceAtLeast(last - MAX_CHART_DAYS + 1)
        (start..last).map { DailyPlayCount(it, counts[it] ?: 0) }
    }
    val peak = remember(bars) { bars.maxOf { it.playCount }.coerceAtLeast(1) }
    ChartPanel("Activity") {
        Row(
            modifier = Modifier.fillMaxWidth().height(104.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            bars.forEach { bar ->
                val fraction = (bar.playCount.toFloat() / peak).coerceIn(MIN_BAR_FRACTION, 1f)
                Box(
                    Modifier.weight(1f).fillMaxHeight(fraction).clip(RoundedCornerShape(4.dp))
                        .background(if (bar.playCount > 0) Coral else GlassFill),
                )
            }
        }
    }
}

@Composable
private fun ListeningClock(byHour: List<HourlyPlayCount>) {
    val counts = remember(byHour) { byHour.associate { it.hour to it.playCount } }
    val peak = remember(counts) { (counts.values.maxOrNull() ?: 0).coerceAtLeast(1) }
    ChartPanel("Listening clock") {
        Row(
            modifier = Modifier.fillMaxWidth().height(78.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (0..23).forEach { hour ->
                val count = counts[hour] ?: 0
                val fraction = (count.toFloat() / peak).coerceIn(MIN_BAR_FRACTION, 1f)
                Box(
                    Modifier.weight(1f).fillMaxHeight(fraction).clip(RoundedCornerShape(4.dp))
                        .background(if (count > 0) Lavender else GlassFill),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 6, 12, 18, 23).forEach { Text(formatHour(it), style = MaterialTheme.typography.labelSmall, color = OnDarkMuted) }
        }
    }
}

@Composable
private fun ChartPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        GlassPanel(shape = RoundedCornerShape(22.dp), tint = GlassFill) {
            Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
        }
    }
}

@Composable
private fun TopArtistsRow(artists: List<ArtistPlayCount>, onArtistSearch: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Top artists")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(artists, key = { it.name }) { artist ->
                GlassPanel(
                    modifier = Modifier.width(132.dp).clickable { onArtistSearch(artist.name) },
                    shape = RoundedCornerShape(20.dp),
                    tint = GlassFill,
                ) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ArtworkImage(url = artist.artworkUrl, size = 78.dp, shape = RoundedCornerShape(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Text("${artist.playCount} plays", style = MaterialTheme.typography.labelSmall, color = OnDarkMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopSongRow(rank: Int, entry: SongPlayCount, isLiked: Boolean, onToggleLike: () -> Unit, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            rank.toString(),
            modifier = Modifier.width(34.dp),
            textAlign = TextAlign.Center,
            color = if (rank <= 3) Coral else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        SongRow(
            song = entry.song,
            onClick = onClick,
            isLiked = isLiked,
            onToggleLike = onToggleLike,
            modifier = Modifier.weight(1f),
        )
        Text("${entry.playCount}×", color = OnDarkMuted, modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun EmptyStats(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No listening history yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Play a track long enough and your top songs, artists and charts will appear here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val StatsRange.label: String
    get() = when (this) {
        StatsRange.LAST_4_WEEKS -> "4 weeks"
        StatsRange.LAST_6_MONTHS -> "6 months"
        StatsRange.THIS_YEAR -> "This year"
        StatsRange.ALL_TIME -> "All time"
    }

private fun formatCount(value: Int): String = when {
    value < 1_000 -> value.toString()
    value < 1_000_000 -> String.format(Locale.getDefault(), "%.1fK", value / 1_000.0)
    else -> String.format(Locale.getDefault(), "%.1fM", value / 1_000_000.0)
}

private fun formatMinutes(ms: Long): String = formatCount((ms / 60_000L).toInt())

private fun formatHour(hour: Int): String =
    LocalDate.now().atStartOfDay().withHour(hour.coerceIn(0, 23)).format(DateTimeFormatter.ofPattern("h a", Locale.getDefault()))

private fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

private const val MAX_CHART_DAYS = 90L
private const val MIN_CHART_DAYS = 28L
private const val MIN_BAR_FRACTION = 0.04f
