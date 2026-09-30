package com.example.musicsmd.audio

import com.example.musicsmd.ui.components.LocalBottomBarPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.musicsmd.playback.PlayerController
import com.example.musicsmd.settings.SettingsStore
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.components.musicSmSliderColors
import com.example.musicsmd.ui.theme.Coral
import com.example.musicsmd.ui.theme.GlassFillStrong
import java.util.Locale
import kotlin.math.roundToInt

/** libVLC equalizer controls: presets, preamp and per-band gain, persisted in desktop settings. */
@Composable
fun EqualizerScreen(
    settingsStore: SettingsStore,
    player: PlayerController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by settingsStore.settings.collectAsState()
    val presets = remember { player.equalizerPresets() }
    val frequencies = remember { player.equalizerBands() }
    val bandCount = frequencies.size.coerceAtLeast(settings.equalizerBands.size).coerceAtLeast(10)
    val bands = settings.equalizerBands.normalizeBands(bandCount)

    fun persist(enabled: Boolean = settings.equalizerEnabled, preset: String? = settings.equalizerPreset, preamp: Float = settings.equalizerPreamp, values: List<Float> = bands) {
        settingsStore.update { it.copy(equalizerEnabled = enabled, equalizerPreset = preset, equalizerPreamp = preamp, equalizerBands = values) }
        player.applyEqualizer(enabled, preset, preamp, values)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 28.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 36.dp + LocalBottomBarPadding.current),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Coral, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Equalizer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Tune libVLC output with MusicSM's glass controls", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { persist(enabled = false, preset = null, preamp = 0f, values = List(bandCount) { 0f }) }) {
                    Text("Reset", color = Coral, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        item {
            GlassPanel(shape = RoundedCornerShape(28.dp), tint = GlassFillStrong, liquid = true) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Enable equalizer", style = MaterialTheme.typography.titleMedium)
                        Text("Applies to every song through libVLC", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.equalizerEnabled,
                        onCheckedChange = { persist(enabled = it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Coral),
                    )
                }
            }
        }

        if (presets.isNotEmpty()) {
            item { SectionTitle("Presets") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PresetChip("Custom", settings.equalizerPreset == null, settings.equalizerEnabled) { persist(preset = null) }
                    presets.forEach { preset ->
                        PresetChip(preset, settings.equalizerPreset == preset, settings.equalizerEnabled) {
                            val (preamp, amps) = player.equalizerPresetValues(preset) ?: (settings.equalizerPreamp to emptyList())
                            persist(preset = preset, preamp = preamp, values = amps)
                        }
                    }
                }
            }
        }

        item { SectionTitle("Preamp") }
        item {
            GainSlider(
                label = "Preamp",
                value = settings.equalizerPreamp,
                enabled = settings.equalizerEnabled,
                onChange = { persist(preset = null, preamp = it) },
            )
        }

        item { SectionTitle("Bands") }
        item {
            GlassPanel(shape = RoundedCornerShape(28.dp), tint = GlassFillStrong) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp).alpha(if (settings.equalizerEnabled) 1f else 0.42f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    bands.forEachIndexed { index, value ->
                        VerticalBandSlider(
                            label = formatFrequency(frequencies.getOrNull(index), index),
                            value = value,
                            enabled = settings.equalizerEnabled,
                            onChange = { gain ->
                                val updated = bands.toMutableList().apply { set(index, gain) }
                                persist(preset = null, values = updated)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelLarge, color = Coral, fontWeight = FontWeight.Bold)
}

@Composable
private fun PresetChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, maxLines = 1) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) Coral else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            labelColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun GainSlider(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    GlassPanel(shape = RoundedCornerShape(24.dp), tint = GlassFillStrong) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.width(78.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = value.coerceIn(MIN_DB, MAX_DB),
                onValueChange = { onChange(it.roundToOneDecimal()) },
                valueRange = MIN_DB..MAX_DB,
                enabled = enabled,
                colors = musicSmSliderColors(),
                modifier = Modifier.weight(1f),
            )
            Text(formatDb(value), modifier = Modifier.width(72.dp), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun VerticalBandSlider(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(formatDb(value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(modifier = Modifier.width(46.dp).height(180.dp), contentAlignment = Alignment.Center) {
            Slider(
                value = value.coerceIn(MIN_DB, MAX_DB),
                onValueChange = { onChange(it.roundToOneDecimal()) },
                valueRange = MIN_DB..MAX_DB,
                enabled = enabled,
                colors = musicSmSliderColors(),
                modifier = Modifier.width(170.dp).height(44.dp).rotate(-90f),
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

private fun List<Float>.normalizeBands(count: Int): List<Float> =
    if (size == count) this else List(count) { index -> getOrNull(index) ?: 0f }

private fun Float.roundToOneDecimal(): Float = (this * 10f).roundToInt() / 10f

private fun formatFrequency(value: Float?, index: Int): String {
    val hz = value ?: DEFAULT_FREQUENCIES.getOrNull(index) ?: return "B${index + 1}"
    return if (hz >= 1000f) String.format(Locale.getDefault(), "%.0fk", hz / 1000f) else hz.roundToInt().toString()
}

private fun formatDb(value: Float): String = String.format(Locale.getDefault(), if (value > 0f) "+%.1f dB" else "%.1f dB", value)

private const val MIN_DB = -20f
private const val MAX_DB = 20f
private val DEFAULT_FREQUENCIES = listOf(32f, 64f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
