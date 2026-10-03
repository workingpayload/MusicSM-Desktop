package com.example.innertube

import com.example.innertube.internal.InnerTubeResponse
import com.example.innertube.internal.Parsers
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryParsersTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `reads the account's playlists from the library grid`() {
        val response = json.decodeFromString<InnerTubeResponse>(LIBRARY_PLAYLISTS)
        val playlists = Parsers.page(Parsers.singleColumnList(response)).playlists
        assertEquals(listOf("LM" to "Liked Music", "PLmine" to "Road trip"), playlists.map { it.id to it.title })
        assertEquals("https://i/liked.png", playlists.first().thumbnailUrl)
    }

    @Test
    fun `reads history shelves as dated song lists`() {
        val response = json.decodeFromString<InnerTubeResponse>(HISTORY)
        val page = Parsers.page(Parsers.singleColumnList(response))
        assertEquals(listOf("Today"), page.shelves.map { it.title })
        assertEquals(listOf("vid1"), page.songs.map { it.id })
        assertEquals("Artist One", page.songs.single().artistLine)
    }

    @Test
    fun `signs requests the way the web client does`() {
        val expected = java.security.MessageDigest.getInstance("SHA-1")
            .digest("1700000000 abc https://music.youtube.com".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals("SAPISIDHASH 1700000000_$expected", sapisidHash("abc", "https://music.youtube.com", 1_700_000_000))
    }
}

private const val LIBRARY_PLAYLISTS = """
{"contents": {"singleColumnBrowseResultsRenderer": {"tabs": [{"tabRenderer": {"content": {"sectionListRenderer": {"contents": [
  {"gridRenderer": {"items": [
    {"musicTwoRowItemRenderer": {"title": {"runs": [{"text": "New playlist"}]},
      "navigationEndpoint": {"createPlaylistEndpoint": {}}}},
    {"musicTwoRowItemRenderer": {
      "thumbnailRenderer": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [{"url": "https://i/liked.png"}]}}},
      "title": {"runs": [{"text": "Liked Music"}]},
      "navigationEndpoint": {"browseEndpoint": {"browseId": "VLLM", "browseEndpointContextSupportedConfigs": {
        "browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_PLAYLIST"}}}}}},
    {"musicTwoRowItemRenderer": {
      "title": {"runs": [{"text": "Road trip"}]},
      "navigationEndpoint": {"browseEndpoint": {"browseId": "VLPLmine", "browseEndpointContextSupportedConfigs": {
        "browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_PLAYLIST"}}}}}}
  ]}}
]}}}}]}}}
"""

private const val HISTORY = """
{"contents": {"singleColumnBrowseResultsRenderer": {"tabs": [{"tabRenderer": {"content": {"sectionListRenderer": {"contents": [
  {"musicShelfRenderer": {"title": {"runs": [{"text": "Today"}]}, "contents": [
    {"musicResponsiveListItemRenderer": {
      "playlistItemData": {"videoId": "vid1"},
      "flexColumns": [
        {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Song One"}]}}},
        {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Artist One",
          "navigationEndpoint": {"browseEndpoint": {"browseId": "UCartist1", "browseEndpointContextSupportedConfigs": {
            "browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_ARTIST"}}}}}]}}}
      ]}}
  ]}}
]}}}}]}}}
"""
