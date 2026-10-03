package com.example.musicsmd.playback

import com.example.musicsm.domain.model.PlayableStream
import com.example.musicsm.domain.model.Song
import com.example.musicsmd.playback.mix.Audibility
import com.example.musicsmd.playback.mix.BeatAnalyzer
import com.example.musicsmd.playback.mix.BeatGrid
import com.example.musicsmd.playback.mix.DeckLevel
import com.example.musicsmd.playback.mix.FadeShape
import com.example.musicsmd.playback.mix.MixCurves
import com.example.musicsmd.playback.mix.MixPlan
import com.example.musicsmd.playback.mix.PcmSnippet
import com.example.musicsmd.playback.mix.SnippetDecoder
import com.example.musicsmd.playback.mix.TransitionPlanner
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Crossfade and DJ-style "Mix" transitions, ported from the mobile app's `CrossfadeController`.
 * When [crossfadeMs] is 0 and Mix is off it does nothing and tracks change as before.
 *
 * Mobile blends inside ExoPlayer's own audio stream; libVLC has no such hook, so here the next
 * track is opened, paused and silent, on the [PlayerController]'s second deck a while before the
 * end. At the hand-off it starts and becomes the current track while the previous one fades out
 * on the other deck. Levels and the bass swap are driven through each deck's equalizer
 * (see [com.example.musicsmd.playback.mix.DeckEqualizer]).
 *
 * In Mix mode both tracks are analysed first, as on mobile: silent run-outs and lead-ins are
 * skipped ([Audibility]), beats are found ([BeatAnalyzer]) and, when the tempos already agree,
 * the blend is placed so downbeats land together ([TransitionPlanner]). The bass moves from one
 * track to the other part-way through. Otherwise it's a shorter timed blend with the same bass
 * handling. A plain crossfade is an equal-power fade over [crossfadeMs].
 */
class MixController(
    private val player: PlayerController,
    private val decoder: SnippetDecoder,
    private val host: Host,
    parentScope: CoroutineScope,
) {
    /** The queue entry playback would move on to by itself. */
    data class Upcoming(val song: Song, val index: Int)

    interface Host {
        /** The song the player has loaded, or null while switching. */
        val currentSongId: String?

        /** Where playback goes when this track ends; null when it shouldn't blend (repeat-one, sleep timer, end). */
        fun upcoming(): Upcoming?

        suspend fun resolve(song: Song): PlayableStream?

        /** The blend has started: [next] is now the current track, playing from [startMs]. */
        fun onHandoff(next: Upcoming, startMs: Long)
    }

    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val lock = Any()

    @Volatile
    var crossfadeMs: Int = 0
        set(value) {
            val clamped = value.coerceIn(0, MAX_CROSSFADE_MS)
            if (field == clamped) return
            field = clamped
            settingsChanged()
        }

    /** Seamless DJ-style transitions of [MIX_WINDOW_MS] (or a longer crossfade), beat-matched when possible. */
    @Volatile
    var mixMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            settingsChanged()
        }

    val enabled: Boolean get() = crossfadeMs > 0 || mixMode

    private fun activeWindowMs(): Long = (if (mixMode) max(crossfadeMs, MIX_WINDOW_MS) else crossfadeMs).toLong()

    private var monitorJob: Job? = null
    private var pending: Pending? = null
    private var blendJob: Job? = null
    private var released = false

    init {
        player.onStandbyError = {
            scope.launch { synchronized(lock) { pending?.cancel("next track failed to load") } }
        }
    }

    /** The user switched or stopped tracks: drop any prepared blend and end a running one at once. */
    fun cancel() = synchronized(lock) {
        pending?.cancel("interrupted")
        endBlend()
    }

    /** Pause or seek: a running blend ends at once (the new track carries on); a prepared one waits. */
    fun finishBlend() = synchronized(lock) { endBlend() }

    /** Stops for good, waiting (briefly) for snippet decoding to let go of libVLC first. */
    fun release() {
        synchronized(lock) {
            released = true
            monitorJob?.cancel()
            pending?.cancel("released")
            endBlend()
        }
        val job = scope.coroutineContext[Job]
        scope.cancel()
        if (job != null) runBlocking { withTimeoutOrNull(RELEASE_WAIT_MS) { job.join() } }
    }

    /** A different length or mode: replan any prepared transition (a running blend finishes as it is). */
    private fun settingsChanged() = synchronized(lock) {
        if (released) return@synchronized
        pending?.cancel("settings changed")
        if (enabled) {
            if (monitorJob?.isActive != true) {
                monitorJob = scope.launch {
                    while (isActive) delay(step())
                }
            }
        } else {
            monitorJob?.cancel()
            monitorJob = null
        }
    }

    private fun endBlend() {
        val job = blendJob ?: return
        job.cancel()
        blendJob = null
        player.releaseOther()
    }

    /** One look at the player; returns how long to wait before the next. */
    private fun step(): Long {
        synchronized(lock) { return stepLocked() }
    }

    private fun stepLocked(): Long {
        if (blendJob?.isActive == true) return POLL_MS
        val songId = host.currentSongId
        val p = pending
        if (songId == null) {
            p?.cancel("no track")
            return POLL_MS
        }
        if (p != null && p.outgoingId != songId) {
            p.cancel("track changed")
            return POLL_MS
        }
        if (!player.isPlaying) return POLL_MS
        val duration = player.durationMs
        if (duration <= 0L) return POLL_MS
        val position = player.positionMs()
        val rate = player.playbackRate
        val left = duration - position

        if (p == null) {
            val url = player.currentUrl ?: return POLL_MS
            val window = activeWindowMs().coerceAtMost(duration / 2)
            if (window < MIN_FADE_MS) return POLL_MS
            val lead = window + if (mixMode) ANALYSIS_LEAD_MS else PREPARE_LEAD_MS
            if (left > lead * rate) return POLL_MS
            val next = host.upcoming() ?: return POLL_MS
            // Repeat-one (or the same song queued next) shouldn't blend a track into itself.
            if (next.song.id == songId) return POLL_MS
            pending = Pending(songId, url, next, duration, window, mixMode, lead).also { it.start(position, rate) }
            return POLL_MS
        }

        if (left > (p.leadMs + RESEEK_SLACK_MS) * rate) {
            p.cancel("sought back")
            return POLL_MS
        }
        if (host.upcoming()?.song?.id != p.next.song.id) {
            p.cancel("queue changed")
            return POLL_MS
        }
        val plan = p.plan ?: return POLL_MS
        if (!player.isStandbyReady) {
            if (position > plan.handoffAtMs + plan.fadeMs / 2) p.cancel("next track not ready in time")
            return POLL_MS
        }
        val untilHandoff = plan.handoffAtMs - position
        if (untilHandoff > 0) {
            // Close to the hand-off, wake exactly on it rather than on the next poll.
            return (untilHandoff / rate).toLong().coerceIn(1L, POLL_MS)
        }
        startBlend(p, plan, position, duration)
        return POLL_MS
    }

    private fun startBlend(p: Pending, plan: MixPlan, position: Long, duration: Long) {
        var fade = plan.fadeMs.toDouble()
        var matched = plan.beatMatched
        if (position - plan.handoffAtMs > LATE_TOLERANCE_MS) {
            // Sought into the blend, or the next track loaded late: blend over what's left.
            fade = min(fade, (duration - position).toDouble())
            matched = false
        }
        if (fade < MIN_FADE_MS) {
            p.cancel("too late to blend")
            return
        }
        if (!player.startStandby()) {
            p.cancel("next track wasn't waiting")
            return
        }
        p.close()
        log("blend $p: fade ${fade.roundToLong()}ms matched=$matched from ${plan.incomingStartMs}ms")
        host.onHandoff(p.next, plan.incomingStartMs)
        val mix = p.mix
        blendJob = scope.launch { automate(fade, plan, matched, mix) }
    }

    /** Fades by wall time scaled to media time, since both decks play at the user's speed. */
    private suspend fun automate(fade: Double, plan: MixPlan, matched: Boolean, mix: Boolean) {
        val shape = if (mix) FadeShape.MIX else FadeShape.EQUAL_POWER
        val start = System.nanoTime()
        while (true) {
            val t = (System.nanoTime() - start) / 1e6 * player.playbackRate
            if (t >= fade) break
            val u = t / fade
            val outgoing = DeckLevel(MixCurves.fadeOut(u, shape), if (mix) outgoingBassCut(t, fade, plan, matched) else 0.0)
            val incoming = DeckLevel(MixCurves.fadeIn(u), if (mix) incomingBassCut(t, fade, plan, matched) else 0.0)
            synchronized(lock) {
                if (!scope.isActive || blendJob?.isActive != true) return
                player.setLevels(active = incoming, outgoing = outgoing)
            }
            delay(STEP_MS)
        }
        synchronized(lock) {
            if (blendJob?.isActive == true) {
                blendJob = null
                player.releaseOther()
            }
        }
    }

    /** Mix: the outgoing track keeps its bass until the swap, then loses it. */
    private fun outgoingBassCut(t: Double, fade: Double, plan: MixPlan, matched: Boolean): Double =
        if (matched) {
            ramp((t - plan.bassSwapMs) / max(plan.swapRampMs, 1L))
        } else {
            ramp(t / (UNMATCHED_OUT_CUT * fade))
        }

    /** Mix: the next track comes in without its bass and gets it at the swap. */
    private fun incomingBassCut(t: Double, fade: Double, plan: MixPlan, matched: Boolean): Double =
        1.0 - if (matched) {
            ramp((t - plan.bassSwapMs) / max(plan.swapRampMs, 1L))
        } else {
            ramp((t - UNMATCHED_IN_HOLD * fade) / ((UNMATCHED_IN_OPEN - UNMATCHED_IN_HOLD) * fade))
        }

    private fun ramp(x: Double): Double = x.coerceIn(0.0, 1.0)

    /** One transition from [outgoingId] into [next], from preparing it to the hand-off. */
    private inner class Pending(
        val outgoingId: String,
        private val outgoingUrl: String,
        val next: Upcoming,
        private val duration: Long,
        private val window: Long,
        val mix: Boolean,
        val leadMs: Long,
    ) {
        private var job: Job? = null
        private var standbyLoaded = false
        private var closed = false

        /** Set once the next track is waiting on the standby deck. */
        var plan: MixPlan? = null
            private set

        fun start(position: Long, rate: Float) {
            job = scope.launch {
                val ready = try {
                    prepare(position, rate)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("preparing $this failed: $e")
                    null
                }
                synchronized(lock) {
                    if (closed || pending !== this@Pending) return@launch
                    if (ready == null) {
                        // Stays pending (unprepared) so it isn't retried; the track ends as usual.
                        log("no blend for $this")
                        return@launch
                    }
                    player.prepareStandby(ready.first, ready.second.incomingStartMs)
                    standbyLoaded = true
                    plan = ready.second
                }
            }
        }

        private suspend fun prepare(position: Long, rate: Float): Pair<PlayableStream, MixPlan>? {
            val stream = host.resolve(next.song) ?: return null
            val plan = if (mix) {
                val a = analyse(outgoingUrl, stream.url, duration, window)
                TransitionPlanner.plan(
                    durationMs = duration,
                    windowMs = window,
                    earliestHandoffMs = position + (PLAN_GRACE_MS * rate).roundToLong(),
                    outgoing = a.outGrid,
                    incoming = a.inGrid,
                    outgoingEndMs = a.outgoingEndMs ?: duration,
                    incomingStartMs = a.incomingStartMs,
                    maxStretch = MATCH_TOLERANCE,
                ).also {
                    log(
                        "plan $this: matched=${it.beatMatched} handoff=${it.handoffAtMs}/$duration fade=${it.fadeMs} " +
                            "swap=${it.bassSwapMs} incomingStart=${it.incomingStartMs} out=${a.outGrid} in=${a.inGrid}",
                    )
                }
            } else {
                MixPlan(
                    handoffAtMs = duration - window,
                    fadeMs = window,
                    outgoingRate = 1f,
                    beatMatched = false,
                    bassSwapMs = window / 2,
                    swapRampMs = 0,
                )
            }
            return stream to plan
        }

        fun cancel(reason: String) {
            if (closed) return
            close()
            job?.cancel()
            if (standbyLoaded) player.releaseOther()
            log("cancel $this: $reason")
        }

        fun close() {
            closed = true
            if (pending === this) pending = null
        }

        override fun toString() = "$outgoingId>${next.song.id}"
    }

    /** Decodes the end of the outgoing track and the start of the next and finds their beats and silences. */
    private suspend fun analyse(outgoingUrl: String, nextUrl: String, duration: Long, window: Long): Analysis =
        coroutineScope {
            val started = System.currentTimeMillis()
            // Both reads are mostly network/decoder bound, so run them side by side.
            val tailJob = async(Dispatchers.IO) {
                decodeOrNull("tail") {
                    decoder.decode(outgoingUrl, (duration - window - TAIL_EXTRA_MS).coerceAtLeast(0L), duration - 500)
                }
            }
            val headJob = async(Dispatchers.IO) { decodeOrNull("head") { decoder.decode(nextUrl, 0, HEAD_MS) } }
            val tail = tailJob.await()
            val head = headJob.await()
            val outGrid = tail?.let { BeatAnalyzer.analyze(it.samples, it.sampleRate, it.startMs) }
            val inGrid = head?.let { BeatAnalyzer.analyze(it.samples, it.sampleRate, it.startMs) }
            // A silent run-out isn't worth blending over, nor a silent lead-in worth waiting for.
            val outgoingEnd = tail?.let { snippet ->
                Audibility.endMs(snippet)
                    ?.takeIf { it < Audibility.snippetEndMs(snippet) - RUN_OUT_MIN_MS }
                    ?.let { (it + AUDIBLE_PAD_MS).roundToLong().coerceAtMost(duration) }
            }
            val incomingStart = head?.let { Audibility.startMs(it) }
                ?.let { (it - AUDIBLE_PAD_MS).roundToLong().coerceIn(0L, MAX_INCOMING_START_MS) }
                ?: 0L
            log("analysed in ${System.currentTimeMillis() - started}ms (tail=${tail != null}, head=${head != null})")
            Analysis(outGrid, inGrid, outgoingEnd, incomingStart)
        }

    private suspend fun decodeOrNull(what: String, block: suspend () -> PcmSnippet?): PcmSnippet? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("$what decode failed: $e")
            null
        }

    private class Analysis(
        val outGrid: BeatGrid?,
        val inGrid: BeatGrid?,
        val outgoingEndMs: Long?,
        val incomingStartMs: Long,
    )

    private fun log(message: String) {
        if (DEBUG) println("[Mix] $message")
    }

    companion object {
        const val MAX_CROSSFADE_MS = 12_000
        const val MIX_WINDOW_MS = 8_000 // blend length in Mix mode
        private const val MIN_FADE_MS = 1_000L
        private const val POLL_MS = 200L
        private const val STEP_MS = 30L

        // Scheduling (media ms; scaled by the user's speed where it matters).
        private const val PREPARE_LEAD_MS = 20_000L // crossfade: open the next track this far ahead
        private const val ANALYSIS_LEAD_MS = 60_000L // Mix: analyse this far ahead of the blend
        private const val PLAN_GRACE_MS = 5_000L // earliest beat-matched blend after the analysis starts
        private const val RESEEK_SLACK_MS = 10_000L
        private const val LATE_TOLERANCE_MS = 250L
        private const val RELEASE_WAIT_MS = 2_000L

        // Nothing is time-stretched, so beats are only matched when the tempos already agree.
        private const val MATCH_TOLERANCE = 0.004

        // Mix analysis.
        private const val TAIL_EXTRA_MS = 12_000L // analyse this much of the outgoing track before the blend
        private const val HEAD_MS = 20_000L // analyse the first 20 s of the incoming track
        private const val RUN_OUT_MIN_MS = 1_000.0 // shorter silent run-outs are left alone
        private const val AUDIBLE_PAD_MS = 50.0
        private const val MAX_INCOMING_START_MS = 8_000L

        // Mix bass swap when beats aren't matched (fractions of the blend).
        private const val UNMATCHED_OUT_CUT = 0.3
        private const val UNMATCHED_IN_HOLD = 0.35
        private const val UNMATCHED_IN_OPEN = 0.55

        private val DEBUG = System.getenv("MUSICSM_MIX_DEBUG") != null
    }
}
