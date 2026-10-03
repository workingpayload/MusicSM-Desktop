package com.example.musicsmd.playback

import com.example.musicsm.domain.model.PlayableStream
import com.example.musicsmd.playback.mix.DeckEqualizer
import com.example.musicsmd.playback.mix.DeckLevel
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.AudioDevice
import uk.co.caprica.vlcj.player.base.Equalizer
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.State
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent

/** Desktop audio output exposed by libVLC. */
data class AudioOutputDeviceInfo(
    val id: String,
    val name: String,
    val isCurrent: Boolean,
)

/**
 * Desktop equivalent of the mobile app's Media3/ExoPlayer bridge. Wraps libVLC (via vlcj) since
 * ExoPlayer doesn't run on the JVM — everything above this (queue, UI) is provider-agnostic, so
 * only this class and [com.example.musicsmd.player.PlayerViewModel] know about libVLC.
 *
 * There are two players ("decks") so crossfade and Mix can overlap tracks: the active deck is the
 * one the app sees, and the other one either waits paused with the next track ("standby") or fades
 * out the previous one ("outgoing"). Events from the other deck are ignored. Outside a blend this
 * behaves exactly like a single player.
 *
 * Requires VLC to be installed on the host machine (libvlc on the system path).
 */
class PlayerController {
    private enum class Role { IDLE, ACTIVE, STANDBY, OUTGOING }

    private inner class Deck(val component: AudioPlayerComponent) {
        val player: MediaPlayer get() = component.mediaPlayer()

        @Volatile
        var role = Role.IDLE

        /** The standby track's own events have begun; earlier ones belong to what was on the deck before. */
        @Volatile
        var opened = false

        @Volatile
        var standbyReady = false

        @Volatile
        var awaitingAudio = false
        var url: String? = null
        var level = DeckLevel.FULL
        var equalizer: Equalizer? = null

        // The last time libVLC reported and when, to interpolate between its coarse ticks.
        @Volatile
        var tickTimeMs = 0L

        @Volatile
        var tickNanos = 0L
    }

    private val first = AudioPlayerComponent()
    private val second = AudioPlayerComponent(first.mediaPlayerFactory())
    private val decks = listOf(Deck(first), Deck(second))

    @Volatile
    private var active = decks[0].apply { role = Role.ACTIVE }
    private val other: Deck get() = if (active === decks[0]) decks[1] else decks[0]

    /** The libVLC instance both decks share, for helpers such as snippet decoding. */
    val factory: MediaPlayerFactory get() = first.mediaPlayerFactory()

    var onEndReached: (() -> Unit)? = null
    var onError: (() -> Unit)? = null

    /** Fired once per [play], when the new track's first audio has actually played. */
    var onAudioStarted: (() -> Unit)? = null
    var onPositionChanged: ((positionMs: Long, durationMs: Long) -> Unit)? = null

    /** The track waiting on the standby deck failed to load. */
    var onStandbyError: (() -> Unit)? = null

    private var userEqualizer: UserEqualizer? = null
    private var rate = 1f
    private var outputDeviceId: String? = null

    /**
     * Keeps an equalizer on both decks even when the user's is off. Adding libVLC's equalizer to a
     * playing track restarts its audio filters (a click), so while crossfade/Mix is on it stays
     * attached (flat) and only its values change during a blend.
     */
    var keepEqualizerAttached: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            synchronized(this) { decks.forEach(::applyEqualizer) }
        }

    private val bandFrequencies: List<Float> by lazy {
        runCatching { factory.equalizer().bands() }.getOrDefault(emptyList())
    }

    init {
        decks.forEach { deck ->
            deck.player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                    deck.tickTimeMs = newTime
                    deck.tickNanos = System.nanoTime()
                    if (deck !== active) return
                    if (deck.awaitingAudio && newTime > 0) {
                        deck.awaitingAudio = false
                        onAudioStarted?.invoke()
                    }
                }

                override fun opening(mediaPlayer: MediaPlayer) {
                    deck.opened = true
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    if (deck.role == Role.STANDBY && deck.opened) deck.standbyReady = true
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    if (deck === active) onEndReached?.invoke()
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    when {
                        deck === active -> onError?.invoke()
                        deck.role == Role.STANDBY && deck.opened -> onStandbyError?.invoke()
                    }
                }

                override fun positionChanged(mediaPlayer: MediaPlayer, newPosition: Float) {
                    if (deck !== active) return
                    val durationMs = mediaPlayer.status().length()
                    onPositionChanged?.invoke((newPosition * durationMs).toLong(), durationMs)
                }
            })
        }
    }

    @Synchronized
    fun play(
        stream: PlayableStream,
        rate: Float = 1f,
        startPositionMs: Long = 0L,
        outputDeviceId: String? = null,
        paused: Boolean = false,
    ) {
        releaseOther()
        val deck = active
        deck.level = DeckLevel.FULL
        applyEqualizer(deck)
        deck.awaitingAudio = !paused
        deck.url = stream.url
        deck.tickTimeMs = startPositionMs
        deck.tickNanos = System.nanoTime()
        deck.player.media().play(stream.url, *mediaOptions(startPositionMs, paused))
        setPlaybackSpeed(rate)
        outputDeviceId?.let(::setOutputDevice)
    }

    fun pause() = active.player.controls().setPause(true)

    fun resume() = active.player.controls().setPause(false)

    fun togglePlayPause() {
        if (active.player.status().isPlaying) pause() else resume()
    }

    fun seekTo(positionMs: Long) {
        val deck = active
        deck.tickTimeMs = positionMs
        deck.tickNanos = System.nanoTime()
        deck.player.controls().setTime(positionMs)
    }

    fun setVolume(percent: Int) {
        decks.forEach { it.player.audio().setVolume(percent.coerceIn(0, 100)) }
    }

    fun setPlaybackSpeed(rate: Float) {
        this.rate = rate.coerceIn(0.5f, 2f)
        decks.forEach { it.player.controls().setRate(this.rate) }
    }

    // ---- Crossfade / Mix ----

    val isPlaying: Boolean get() = active.player.status().isPlaying

    val playbackRate: Float get() = rate

    /** The URL the active deck is playing. */
    val currentUrl: String? get() = active.url

    val durationMs: Long get() = active.player.status().length()

    /** The active deck's position, interpolated between libVLC's coarse (~250 ms) ticks. */
    fun positionMs(): Long {
        val deck = active
        val base = deck.tickTimeMs
        if (!deck.player.status().isPlaying) return base
        val sinceTick = (System.nanoTime() - deck.tickNanos) / 1_000_000.0
        return base + (sinceTick.coerceIn(0.0, MAX_INTERPOLATION_MS) * rate).toLong()
    }

    /**
     * Loads [stream] on the other deck, paused at [startPositionMs] and silent, so it can start at
     * once. Anything still on that deck is stopped.
     */
    @Synchronized
    fun prepareStandby(stream: PlayableStream, startPositionMs: Long) {
        val deck = other
        deck.player.controls().stop()
        deck.role = Role.STANDBY
        deck.opened = false
        deck.standbyReady = false
        deck.awaitingAudio = false
        deck.url = stream.url
        deck.level = DeckLevel.SILENT
        deck.tickTimeMs = startPositionMs
        deck.tickNanos = System.nanoTime()
        applyEqualizer(deck)
        deck.player.controls().setRate(rate)
        deck.player.media().play(stream.url, *mediaOptions(startPositionMs, paused = true))
        outputDeviceId?.let { runCatching { deck.player.audio().setOutputDevice(null, it) } }
    }

    /** Whether the standby deck has its track open and waiting. */
    val isStandbyReady: Boolean
        get() {
            val deck = other
            if (deck.role != Role.STANDBY || !deck.opened) return false
            if (!deck.standbyReady && deck.player.status().state() == State.PAUSED) deck.standbyReady = true
            return deck.standbyReady
        }

    /**
     * Starts the standby deck and makes it the active one; the previous track keeps playing on the
     * other deck (at its current level) until [releaseOther]. False if nothing is waiting.
     */
    @Synchronized
    fun startStandby(): Boolean {
        val deck = other
        if (deck.role != Role.STANDBY) return false
        val previous = active
        // Swapped before it starts, so from here on only the new track's end or error counts.
        previous.role = Role.OUTGOING
        deck.role = Role.ACTIVE
        active = deck
        deck.tickNanos = System.nanoTime()
        deck.player.controls().setPause(false)
        return true
    }

    /** Sets the levels of the active deck and the outgoing one during a blend. */
    @Synchronized
    fun setLevels(active: DeckLevel, outgoing: DeckLevel) {
        setLevel(this.active, active)
        other.takeIf { it.role == Role.OUTGOING }?.let { setLevel(it, outgoing) }
    }

    /** Stops whatever the other deck holds (a waiting or a fading track); the active one plays on at full level. */
    @Synchronized
    fun releaseOther() {
        val deck = other
        if (deck.role != Role.IDLE) {
            deck.player.controls().stop()
            deck.role = Role.IDLE
            deck.standbyReady = false
            deck.url = null
        }
        setLevel(deck, DeckLevel.FULL)
        setLevel(active, DeckLevel.FULL)
    }

    private fun setLevel(deck: Deck, level: DeckLevel) {
        if (deck.level == level) return
        deck.level = level
        applyEqualizer(deck)
    }

    private fun mediaOptions(startPositionMs: Long, paused: Boolean): Array<String> = buildList {
        if (paused) add(":start-paused")
        // Opened at the position, rather than played from 0:00 and sought a moment later,
        // which was heard as a stutter.
        if (startPositionMs > 0L) add(":start-time=${startPositionMs / 1000.0}")
    }.toTypedArray()

    // ---- Output & effects ----

    fun outputDevices(): List<AudioOutputDeviceInfo> {
        val player = active.player
        val current = runCatching { player.audio().outputDevice() }.getOrNull()
        val devices = runCatching { player.audio().outputDevices() }.getOrDefault(emptyList())
            .ifEmpty {
                runCatching {
                    factory.audio().audioOutputs().flatMap { it.devices }
                }.getOrDefault(emptyList())
            }
        return devices.distinctBy(AudioDevice::getDeviceId).map { device ->
            AudioOutputDeviceInfo(
                id = device.deviceId,
                name = device.longName.ifBlank { device.deviceId },
                isCurrent = device.deviceId == current,
            )
        }
    }

    fun setOutputDevice(deviceId: String) {
        outputDeviceId = deviceId
        decks.forEach { deck -> runCatching { deck.player.audio().setOutputDevice(null, deviceId) } }
    }

    fun equalizerPresets(): List<String> =
        runCatching { factory.equalizer().presets() }.getOrDefault(emptyList())

    fun equalizerBands(): List<Float> = bandFrequencies

    /** A libVLC preset's preamp and per-band gains, so the UI can show what the preset does. */
    fun equalizerPresetValues(preset: String): Pair<Float, List<Float>>? = runCatching {
        val eq = factory.equalizer().newEqualizer(preset)
        eq.preamp() to eq.amps().toList()
    }.getOrNull()

    @Synchronized
    fun applyEqualizer(enabled: Boolean, preset: String?, preamp: Float, bands: List<Float>) {
        userEqualizer = if (enabled) {
            runCatching {
                val api = factory.equalizer()
                val eq: Equalizer = preset?.takeIf { it in api.presets() }?.let(api::newEqualizer) ?: api.newEqualizer()
                eq.setPreamp(preamp.coerceIn(MIN_EQ_DB, MAX_EQ_DB))
                bands.take(eq.bandCount()).forEachIndexed { index, amp ->
                    eq.setAmp(index, amp.coerceIn(MIN_EQ_DB, MAX_EQ_DB))
                }
                UserEqualizer(eq.preamp(), eq.amps())
            }.getOrNull()
        } else {
            null
        }
        decks.forEach(::applyEqualizer)
    }

    /** The user's equalizer with the deck's blend level on top; detached when neither is needed. */
    private fun applyEqualizer(deck: Deck) {
        val user = userEqualizer
        if (user == null && !keepEqualizerAttached && deck.level == DeckLevel.FULL) {
            if (deck.equalizer != null) {
                deck.player.audio().setEqualizer(null)
                deck.equalizer = null
            }
            return
        }
        runCatching {
            val existing = deck.equalizer
            val eq = existing ?: factory.equalizer().newEqualizer()
            val base = user?.bands ?: FloatArray(eq.bandCount())
            val values = DeckEqualizer.compute(user?.preamp ?: 0f, base, bandFrequencies, deck.level)
            eq.setPreamp(values.preamp)
            eq.setAmps(values.bands)
            // A newly attached equalizer is applied as a whole; an attached one applies its changes.
            if (existing == null) {
                deck.player.audio().setEqualizer(eq)
                deck.equalizer = eq
            }
        }
    }

    @Synchronized
    fun stop() {
        releaseOther()
        active.player.controls().stop()
    }

    fun release() {
        second.release()
        first.release()
    }

    private class UserEqualizer(val preamp: Float, val bands: FloatArray)

    private companion object {
        const val MIN_EQ_DB = -20f
        const val MAX_EQ_DB = 20f
        const val MAX_INTERPOLATION_MS = 1_000.0
    }
}
