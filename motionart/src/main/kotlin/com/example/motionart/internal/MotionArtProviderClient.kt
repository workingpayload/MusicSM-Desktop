package com.example.motionart.internal

import com.example.motionart.MotionArt
import kotlinx.coroutines.CancellationException

/** What the orchestrator knows about a track when it goes looking for a cover video. */
internal data class TrackQuery(
    val artist: String,
    val title: String,
    val album: String?,
)

/**
 * One catalogue or manifest that may hold a motion cover.
 *
 * A miss or logged transport failure means "nothing here", so the orchestrator moves on to the
 * next source. Coroutine cancellation is not a miss and must propagate.
 */
internal interface MotionArtProviderClient {
    suspend fun lookup(query: TrackQuery): MotionArt?
}

internal suspend fun <T> providerRequest(provider: String, request: suspend () -> T): T? =
    try {
        request()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        System.err.println("Animated artwork $provider request failed: ${error.javaClass.simpleName}")
        null
    }
