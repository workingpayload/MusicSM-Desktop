package com.example.musicsm.data.repository

import com.example.musicsm.domain.model.LyricsSource

/**
 * Small JVM-facing counterpart of the mobile AppPreferences lyrics settings used by the
 * repository. Desktop implements this over its JSON SettingsStore.
 */
interface LyricsPreferences {
    val lyricsSourceOrderNow: List<LyricsSource>
    val disabledLyricsSourcesNow: Set<LyricsSource>
    val preferWordSyncedLyricsNow: Boolean
}

