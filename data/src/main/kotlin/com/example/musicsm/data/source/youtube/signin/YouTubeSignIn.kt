package com.example.musicsm.data.source.youtube.signin

import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.PlayableStream
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.Song
import java.io.IOException

/** The cookies of a signed-in YouTube session, which the app keeps after the user signs in. */
interface YouTubeSessionStore {
    /** youtube.com cookies by name, or null while signed out. */
    fun cookies(): Map<String, String>?

    /** YouTube no longer accepts the stored session (signed out elsewhere, expired): forget it. */
    fun onSessionRejected()
}

/**
 * Runs YouTube's web-player JavaScript, which a signed-in player response needs before it plays:
 * stream URLs come with a scrambled signature and `n` parameter, and googlevideo only serves the
 * whole file to a request carrying a proof-of-origin (PO) token.
 */
interface YouTubePlayerScript {
    /** The `signatureTimestamp` a player request must quote for [playerUrl]'s scrambling. */
    suspend fun signatureTimestamp(playerUrl: String): Int

    /** Unscrambles [signatures] and [nParameters] with [playerUrl]'s functions. */
    suspend fun solve(playerUrl: String, signatures: List<String>, nParameters: List<String>): PlayerScriptSolutions

    /** A PO token bound to [videoId], for its googlevideo URLs. */
    suspend fun mintPoToken(videoId: String): String
}

data class PlayerScriptSolutions(
    val signatures: Map<String, String>,
    val nParameters: Map<String, String>,
)

/** Plays YouTube songs through a signed-in session; see [SignedInStreamResolver]. */
interface SignedInStreams {
    val isSignedIn: Boolean

    suspend fun resolveStream(videoId: String): PlayableStream
}

/**
 * The signed-in YouTube Music account's own data: its personal home, history and playlists.
 * Every call returns empty while [isAvailable] is false (signed out, or the user turned it off).
 */
interface YouTubeAccountLibrary {
    val isAvailable: Boolean

    /** The account's personal home shelves (Quick picks, Listen again, mixes…). */
    suspend fun accountHome(): HomeFeed

    suspend fun moreAccountHome(continuation: String): HomeFeed

    /** Recently played on YouTube, newest first. */
    suspend fun accountHistory(limit: Int): List<Song>

    /** The account's playlists, "Liked Music" included, without their tracks. */
    suspend fun accountPlaylists(): List<Playlist>
}

/**
 * YouTube refuses anonymous playback from this network ("Sign in to confirm you're not a bot")
 * and there is no usable signed-in session to fall back on.
 */
class YouTubeSignInRequiredException(cause: Throwable? = null) : IOException(MESSAGE, cause) {
    companion object {
        const val MESSAGE =
            "YouTube wants you to sign in before it plays music on this network. " +
                "Sign in under Settings → YouTube account."
    }
}
