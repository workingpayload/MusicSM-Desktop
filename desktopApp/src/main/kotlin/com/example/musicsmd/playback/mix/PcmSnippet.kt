package com.example.musicsmd.playback.mix

/** Mono PCM in [-1, 1] for a slice of a track; [startMs] is the media time of the first sample. */
class PcmSnippet(val samples: FloatArray, val sampleRate: Int, val startMs: Double)
