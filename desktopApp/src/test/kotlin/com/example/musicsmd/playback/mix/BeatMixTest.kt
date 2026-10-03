package com.example.musicsmd.playback.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Ported from the mobile app's BeatMixTest (the analysis, planning and curve parts). */
class BeatMixTest {

    private val sr = 44_100

    /** Kick on every beat (louder on the one), hats on the off-beats, a little noise. */
    private fun track(bpm: Double, seconds: Double, firstBeatMs: Double, seed: Int = 1): FloatArray {
        val rnd = Random(seed)
        val n = (seconds * sr).toInt()
        val out = FloatArray(n) { (rnd.nextFloat() - 0.5f) * 0.02f }
        val period = 60.0 / bpm
        var beat = 0
        var t = firstBeatMs / 1000.0
        while (t < seconds) {
            val amp = if (beat % 4 == 0) 0.9 else 0.5
            val start = (t * sr).toInt()
            for (i in 0 until (0.12 * sr).toInt()) {
                val idx = start + i
                if (idx >= n) break
                val tt = i.toDouble() / sr
                out[idx] += (amp * exp(-tt * 30) * sin(2 * PI * (60 + 90 * exp(-tt * 40)) * tt)).toFloat()
            }
            val hat = ((t + period / 2) * sr).toInt()
            var last = 0f
            for (i in 0 until (0.03 * sr).toInt()) {
                val idx = hat + i
                if (idx >= n) break
                // Differenced noise: bright like a real hat, with next to no low end.
                val w = rnd.nextFloat() - 0.5f
                out[idx] += ((w - last) * 0.3f * exp(-i / (0.005 * sr))).toFloat()
                last = w
            }
            beat++
            t += period
        }
        return out
    }

    private fun phaseErrorMs(grid: BeatGrid, trueFirstBeatMs: Double, periodMs: Double = grid.periodMs): Double {
        val d = ((grid.anchorMs - trueFirstBeatMs) % periodMs + periodMs) % periodMs
        return minOf(d, periodMs - d)
    }

    @Test
    fun detectsTempoAndPhase() {
        for ((bpm, offset) in listOf(124.0 to 310.0, 96.0 to 50.0, 140.0 to 0.0, 174.0 to 200.0)) {
            val grid = BeatAnalyzer.analyze(track(bpm, 20.0, offset), sr)
            assertNotNull("no grid at $bpm", grid)
            grid!!
            // Half/double time is an acceptable reading (the planner treats them as compatible).
            val octave = listOf(0.5, 1.0, 2.0).minBy { abs(grid.bpm - bpm * it) }
            assertEquals("bpm at $bpm", bpm * octave, grid.bpm, bpm * octave * 0.004)
            val err = phaseErrorMs(grid, offset, 60_000.0 / bpm)
            assertTrue("phase at $bpm: $err", err < 20.0)
            assertTrue("confidence at $bpm: ${grid.confidence}", grid.confidence >= TransitionPlanner.MIN_CONFIDENCE)
        }
    }

    @Test
    fun findsTheAccentedDownbeat() {
        val grid = BeatAnalyzer.analyze(track(120.0, 20.0, 250.0), sr)!!
        val first = grid.downbeatAtOrAfter(0.0)
        assertTrue("downbeat $first ($grid)", abs(first - 250.0) < 20.0)
    }

    @Test
    fun startOffsetShiftsTheGrid() {
        val grid = BeatAnalyzer.analyze(track(120.0, 15.0, 100.0), sr, startMs = 60_000.0)!!
        assertTrue("$grid", phaseErrorMs(grid, 60_100.0) < 20.0)
    }

    @Test
    fun noiseHasLowConfidence() {
        for (seed in 1..5) {
            val rnd = Random(seed)
            val noise = FloatArray(sr * 20) { (rnd.nextFloat() - 0.5f) * 0.5f }
            val grid = BeatAnalyzer.analyze(noise, sr)
            assertTrue("noise $grid", grid == null || grid.confidence < TransitionPlanner.MIN_CONFIDENCE)

            // Transients at random intervals: busy, but no pulse.
            val hits = FloatArray(sr * 20) { (rnd.nextFloat() - 0.5f) * 0.02f }
            var t = 0
            while (t < hits.size) {
                for (i in 0 until 3000) {
                    if (t + i < hits.size) hits[t + i] += ((rnd.nextFloat() - 0.5f) * exp(-i / 400.0)).toFloat()
                }
                t += rnd.nextInt(sr / 8, sr)
            }
            val h = BeatAnalyzer.analyze(hits, sr)
            assertTrue("hits $h", h == null || h.confidence < TransitionPlanner.MIN_CONFIDENCE)
        }
    }

    @Test
    fun plannerAlignsDownbeatsAndStretchesOutgoing() {
        val out = BeatGrid(periodMs = 60_000.0 / 126, anchorMs = 170_123.0, downbeatPhase = 0, confidence = 0.8f)
        val inc = BeatGrid(periodMs = 60_000.0 / 124, anchorMs = 400.0, downbeatPhase = 0, confidence = 0.8f)
        val duration = 200_000L
        val plan = TransitionPlanner.plan(duration, 10_000, 150_000, out, inc)
        assertTrue(plan.beatMatched)
        assertEquals(out.periodMs / inc.periodMs, plan.outgoingRate.toDouble(), 1e-4)
        // The incoming first downbeat (400 ms in) lands on an outgoing downbeat.
        val outgoingAtIncomingDownbeat = plan.handoffAtMs + 400.0 * plan.outgoingRate
        val fromBar = ((outgoingAtIncomingDownbeat - out.anchorMs) % (4 * out.periodMs))
        assertTrue(minOf(fromBar, 4 * out.periodMs - fromBar) < 2.0)
        // The outgoing tail lasts for the whole blend and the fade is whole bars.
        assertTrue(plan.handoffAtMs + plan.fadeMs * plan.outgoingRate <= duration + 1)
        val bars = plan.fadeMs / (4 * inc.periodMs)
        assertEquals(Math.round(bars).toDouble(), bars, 0.01)
        // Bass swap on an incoming downbeat.
        val swapBars = (plan.bassSwapMs - 400.0) / (4 * inc.periodMs)
        assertEquals(Math.round(swapBars).toDouble(), swapBars, 0.01)
    }

    @Test
    fun plannerTreatsDoubleTimeAsCompatible() {
        val out = BeatGrid(60_000.0 / 70, 0.0, 0, 0.8f)
        val inc = BeatGrid(60_000.0 / 140, 0.0, 0, 0.8f)
        val plan = TransitionPlanner.plan(240_000, 10_000, 0, out, inc)
        assertTrue(plan.beatMatched)
        assertEquals(1.0, plan.outgoingRate.toDouble(), 1e-3)
    }

    @Test
    fun plannerFallsBackWhenTemposClashOrUnsure() {
        val out = BeatGrid(60_000.0 / 100, 0.0, 0, 0.8f)
        val inc = BeatGrid(60_000.0 / 128, 0.0, 0, 0.8f)
        assertFalse(TransitionPlanner.plan(200_000, 10_000, 0, out, inc).beatMatched)
        assertFalse(TransitionPlanner.plan(200_000, 10_000, 0, out.copy(confidence = 0.05f), inc).beatMatched)
        assertFalse(TransitionPlanner.plan(200_000, 10_000, 0, null, inc).beatMatched)
        val fallback = TransitionPlanner.plan(200_000, 10_000, 0, null, null)
        assertEquals(194_000L, fallback.handoffAtMs)
        assertEquals(6_000L, fallback.fadeMs)
    }

    @Test
    fun plannerSkipsSilentRunOutAndLeadIn() {
        // Timed blend: ends where the outgoing audio does; the next track starts past its silence.
        val fallback = TransitionPlanner.plan(
            200_000, 8_000, 0, null, null, outgoingEndMs = 185_000, incomingStartMs = 1_200,
        )
        assertFalse(fallback.beatMatched)
        assertEquals(179_000L, fallback.handoffAtMs)
        assertEquals(1_200L, fallback.incomingStartMs)

        // Matched: the first incoming downbeat after the lead-in lands on an outgoing downbeat,
        // and the tail is done before the run-out.
        val out = BeatGrid(60_000.0 / 120, 0.0, 0, 0.8f)
        val inc = BeatGrid(60_000.0 / 120, 300.0, 0, 0.8f)
        val plan = TransitionPlanner.plan(
            200_000, 8_000, 100_000, out, inc, outgoingEndMs = 185_000, incomingStartMs = 1_200,
        )
        assertTrue(plan.beatMatched)
        assertEquals(1_200L, plan.incomingStartMs)
        assertTrue(plan.handoffAtMs + plan.fadeMs * plan.outgoingRate <= 185_001)
        val lead = inc.downbeatAtOrAfter(1_200.0) - 1_200.0
        val fromBar = (plan.handoffAtMs + lead * plan.outgoingRate) % (4 * out.periodMs)
        assertTrue("off by $fromBar", minOf(fromBar, 4 * out.periodMs - fromBar) < 2.0)
    }

    @Test
    fun fadeOutIsKeyedToMediaTime() {
        for (shape in FadeShape.entries) {
            val out = MixCurves.fadeOut(180_000.0, 8_000.0, shape)
            assertEquals(1.0, out.gainAt(179_999.0), 0.0)
            assertEquals(MixCurves.fadeOut(0.5, shape), out.gainAt(184_000.0), 1e-9)
            assertEquals(0.0, out.gainAt(188_000.0), 0.0)
        }
    }

    @Test
    fun blendIsEqualPowerAndMixClearsOutFaster() {
        val fadeIn = MixCurves.fadeIn(1_000.0, 8_000.0)
        assertEquals(0.0, fadeIn.gainAt(999.0), 0.0)
        assertEquals(1.0, fadeIn.gainAt(9_000.0), 0.0)
        for (u in listOf(0.1, 0.25, 0.5, 0.75, 0.9)) {
            val incoming = MixCurves.fadeIn(u)
            val outgoing = MixCurves.fadeOut(u, FadeShape.EQUAL_POWER)
            assertEquals(1.0, incoming * incoming + outgoing * outgoing, 1e-9)
            assertTrue(MixCurves.fadeOut(u, FadeShape.MIX) < outgoing)
        }
    }

    @Test
    fun audibilityFindsWhereContentStartsAndEnds() {
        // From 10 s into a track: 1 s of noise floor, 3 s of tone, 2 s of noise floor.
        val rnd = Random(3)
        val samples = FloatArray(6 * sr) { i ->
            val t = i.toDouble() / sr
            if (t >= 1.0 && t < 4.0) (0.5 * sin(2 * PI * 440 * t)).toFloat() else (rnd.nextFloat() - 0.5f) * 1e-4f
        }
        val snippet = PcmSnippet(samples, sr, 10_000.0)
        assertEquals(11_000.0, Audibility.startMs(snippet)!!, 50.0)
        assertEquals(14_000.0, Audibility.endMs(snippet)!!, 50.0)
        assertEquals(16_000.0, Audibility.snippetEndMs(snippet), 1e-6)
        assertNull(Audibility.startMs(PcmSnippet(FloatArray(sr), sr, 0.0)))
    }
}
