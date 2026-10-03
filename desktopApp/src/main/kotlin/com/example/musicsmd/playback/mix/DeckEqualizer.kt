package com.example.musicsmd.playback.mix

import kotlin.math.log10

/**
 * One player deck's place in a blend: its level (0..1, applied as a gain) and how far its bass is
 * cut (0 = none, 1 = fully).
 */
data class DeckLevel(val gain: Double = 1.0, val bassCut: Double = 0.0) {
    companion object {
        val FULL = DeckLevel()
        val SILENT = DeckLevel(gain = 0.0)
    }
}

/**
 * Applies a [DeckLevel] through libVLC's per-player equalizer. libVLC's volume can't be used: on
 * Windows every player in a process shares one audio-session volume, so fading one deck would
 * fade the other. The equalizer is per player, so the fade goes on its preamp (spilling into the
 * bands below -20 dB) and the bass cut on the low bands, on top of the user's own settings.
 */
object DeckEqualizer {
    const val MIN_DB = -20f
    const val MAX_DB = 20f

    /** A silent deck sits this far down: inaudible under the other deck. */
    const val FLOOR_DB = -40.0
    const val BASS_CUT_DB = -20f

    /** Bands centred below this carry the bass that a mix swaps between the decks. */
    const val BASS_MAX_HZ = 200f

    /** Equalizer settings for one deck. */
    class Values(val preamp: Float, val bands: FloatArray)

    fun gainDb(gain: Double): Double =
        if (gain <= 0.0) FLOOR_DB else (20 * log10(gain)).coerceIn(FLOOR_DB, 0.0)

    /**
     * The user's [basePreamp]/[baseBands] (flat when the equalizer is off) with [level] applied.
     * [bandFrequencies] are the band centres in Hz, in the same order as [baseBands].
     *
     * Each band's overall gain (preamp + band) drops by the fade, and towards silence any boost
     * the user has is taken away too, so a silent deck is silent however much they boost.
     */
    fun compute(basePreamp: Float, baseBands: FloatArray, bandFrequencies: List<Float>, level: DeckLevel): Values {
        val fade = gainDb(level.gain)
        val towardsSilence = (fade / FLOOR_DB).toFloat()
        val preamp = (basePreamp + fade.toFloat()).coerceIn(MIN_DB, MAX_DB)
        val cut = BASS_CUT_DB * level.bassCut.coerceIn(0.0, 1.0).toFloat()
        val bands = FloatArray(baseBands.size) { i ->
            val overall = basePreamp + baseBands[i]
            val bass = if ((bandFrequencies.getOrNull(i) ?: Float.MAX_VALUE) < BASS_MAX_HZ) cut else 0f
            val target = overall + fade.toFloat() - overall.coerceAtLeast(0f) * towardsSilence + bass
            (target - preamp).coerceIn(MIN_DB, MAX_DB)
        }
        return Values(preamp, bands)
    }
}
