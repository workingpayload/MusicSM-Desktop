package com.example.musicsmd.lyrics

import com.example.musicsm.data.repository.LyricsPreferences
import com.example.musicsm.domain.model.LyricsSource
import com.example.musicsmd.settings.SettingsStore

class DesktopLyricsPreferences(
    private val settingsStore: SettingsStore,
) : LyricsPreferences {
    override val lyricsSourceOrderNow: List<LyricsSource>
        get() {
            val saved = settingsStore.current.lyricsSourceOrder
                .mapNotNull { LyricsSource.fromName(it.trim()) }
                .distinct()
            return saved + LyricsSource.entries.filter { it !in saved }
        }

    override val disabledLyricsSourcesNow: Set<LyricsSource>
        get() = settingsStore.current.disabledLyricsSources
            .mapNotNull { LyricsSource.fromName(it.trim()) }
            .toSet()

    override val preferWordSyncedLyricsNow: Boolean
        get() = settingsStore.current.preferWordSyncedLyrics
}

