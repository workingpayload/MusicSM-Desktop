package com.example.innertube

import com.example.innertube.internal.InnerTubeResponse
import com.example.innertube.internal.NextResponse
import com.example.innertube.internal.Parsers
import com.example.innertube.internal.PlayerResponse
import com.example.innertube.model.YtItem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser coverage for the response shapes InnerTube actually returns.
 *
 * The fixtures are hand-written and trimmed to the fields the parsers read, with the unrelated
 * siblings YouTube ships left out. They are structured exactly like the real payloads, including
 * the three different top-level envelopes, because getting those confused is the failure mode that
 * would silently empty a whole screen.
 */
class ParsersTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parse(raw: String) = json.decodeFromString<InnerTubeResponse>(raw)

    // ------------------------------------------------------------------ duration

    @Test
    fun `parses minutes and seconds`() {
        assertEquals(276_000L, Parsers.parseDuration("4:36"))
    }

    @Test
    fun `parses hours`() {
        assertEquals(3_731_000L, Parsers.parseDuration("1:02:11"))
    }

    @Test
    fun `rejects text that is not a duration`() {
        assertNull(Parsers.parseDuration("Album"))
        assertNull(Parsers.parseDuration("2013"))
        assertNull(Parsers.parseDuration(""))
        assertNull(Parsers.parseDuration(null))
        assertNull(Parsers.parseDuration("1:2:3:4"))
        assertNull(Parsers.parseDuration("a:b"))
    }

    // ------------------------------------------------------------------ home

    @Test
    fun `reads carousel shelves from the single column envelope`() {
        val page = Parsers.page(Parsers.singleColumnSections(parse(HOME)))

        assertEquals(1, page.shelves.size)
        assertEquals("Listen again", page.shelves[0].title)
        assertEquals(2, page.shelves[0].items.size)

        val album = page.albums.single()
        assertEquals("MPREb_fixture01", album.id)
        assertEquals("Night Drive", album.title)
        assertEquals("Neon Fields", album.artists.single().name)
        assertEquals("2021", album.year)
        assertEquals("https://i.example/big.jpg", album.thumbnailUrl)

        val playlist = page.playlists.single()
        assertEquals("RDCLAK5fixture", playlist.id)
        assertEquals("Chill Mix", playlist.title)
    }

    @Test
    fun `skips shelves with no parsable items`() {
        val page = Parsers.page(Parsers.singleColumnSections(parse(HOME_WITH_JUNK_SHELF)))
        assertEquals(listOf("Listen again"), page.shelves.map { it.title })
    }

    // ------------------------------------------------------------------ search

    @Test
    fun `reads songs from the tabbed search envelope`() {
        val items = Parsers.page(Parsers.searchSections(parse(SEARCH)))
            .shelves
            .flatMap { it.items }

        val song = (items.single() as YtItem.Song).song
        assertEquals("vid00000001", song.id)
        assertEquals("Midnight Signal", song.title)
        assertEquals(215_000L, song.durationMs)
        assertTrue(song.explicit)
        assertEquals("https://i.example/song-big.jpg", song.thumbnailUrl)
        assertEquals(listOf("Neon Fields"), song.artists.map { it.name })
        assertEquals("UCfixtureArtist", song.artists.single().id)
        assertEquals("Night Drive", song.album?.title)
        assertEquals("MPREb_fixture01", song.album?.id)
    }

    @Test
    fun `keeps unlinked credits and drops separators and metadata`() {
        val items = Parsers.page(Parsers.searchSections(parse(SEARCH_PLAIN_TEXT_CREDITS)))
            .shelves
            .flatMap { it.items }
        val song = (items.single() as YtItem.Song).song

        assertEquals(listOf("Neon Fields", "Aurora Kane"), song.artists.map { it.name })
        assertTrue(song.artists.all { it.id == null })
        assertEquals(215_000L, song.durationMs)
    }

    @Test
    fun `does not mistake a view count for a performer`() {
        val items = Parsers.page(Parsers.searchSections(parse(SEARCH_VIDEO_RESULT)))
            .shelves
            .flatMap { it.items }
        val song = (items.single() as YtItem.Song).song

        assertEquals(listOf("Neon Fields"), song.artists.map { it.name })
    }

    @Test
    fun `credits a fan upload to its channel and never to a date`() {
        val songs = Parsers.page(Parsers.searchSections(parse(SEARCH_FAN_UPLOADS)))
            .shelves
            .flatMap { it.items }
            .map { (it as YtItem.Song).song }

        assertEquals(listOf("Leak Archive"), songs[0].artists.map { it.name })
        assertNull(songs[0].artists.single().id)
        assertEquals(167_000L, songs[0].durationMs)
        // A Short shows its upload date where a length would be.
        assertEquals(emptyList<String>(), songs[1].artists.map { it.name })
    }

    @Test
    fun `reads the follower count past the type label on an artist card`() {
        val items = Parsers.page(Parsers.searchSections(parse(SEARCH_ARTIST_RESULT)))
            .shelves
            .flatMap { it.items }
        val artist = (items.single() as YtItem.Artist).artist

        assertEquals("Neon Fields", artist.name)
        assertEquals("3.2M subscribers", artist.subscribers)
    }

    // ------------------------------------------------------------------ album

    @Test
    fun `reads an album from the two column envelope`() {
        val album = Parsers.album("MPREb_fixture01", parse(ALBUM))

        assertNotNull(album)
        requireNotNull(album)
        assertEquals("Night Drive", album.title)
        assertEquals("Neon Fields", album.artists.single().name)
        assertEquals("2021", album.year)
        assertEquals(2, album.songs.size)

        val first = album.songs[0]
        assertEquals("vid00000001", first.id)
        assertEquals("Midnight Signal", first.title)
        // Duration sits in a fixed column on album pages, not in the credit line.
        assertEquals(215_000L, first.durationMs)
        // Album rows carry no art of their own, so the release cover stands in.
        assertEquals("https://i.example/cover-big.jpg", first.thumbnailUrl)
    }

    @Test
    fun `reads a playlist shelf and keeps per track artwork`() {
        val playlist = Parsers.playlist("RDCLAK5fixture", parse(PLAYLIST))

        assertNotNull(playlist)
        requireNotNull(playlist)
        assertEquals("Chill Mix", playlist.title)
        assertEquals(1, playlist.songs.size)
        assertEquals("https://i.example/track-big.jpg", playlist.songs[0].thumbnailUrl)
    }

    @Test
    fun `finds the token for a playlist's next page`() {
        assertNull(Parsers.playlistContinuation(parse(PLAYLIST)))
        assertEquals("CONT_TOKEN_1", Parsers.playlistContinuation(parse(PLAYLIST_WITH_MORE)))
    }

    @Test
    fun `reads a further page of a playlist`() {
        val page = Parsers.playlistPage(parse(PLAYLIST_CONTINUATION), fallbackThumbnail = "https://i.example/cover.jpg")

        assertEquals(listOf("vid00000004"), page.songs.map { it.id })
        assertEquals("https://i.example/cover.jpg", page.songs.single().thumbnailUrl)
        assertEquals("CONT_TOKEN_2", page.continuation)
        assertNull(Parsers.playlistPage(parse("{}")).continuation)
    }

    // ------------------------------------------------------------------ artist

    @Test
    fun `reads an artist header and its shelves`() {
        val artist = Parsers.artist("UCfixtureArtist", parse(ARTIST))

        assertEquals("Neon Fields", artist.name)
        assertEquals("1.2M subscribers", artist.subscribers)
        assertEquals("https://i.example/artist-big.jpg", artist.thumbnailUrl)
        // The video of the same song, on the "Videos" shelf, is not listed again.
        assertEquals(listOf("vid00000001"), artist.topSongs.map { it.id })
        assertEquals(listOf("Night Drive"), artist.albums.map { it.title })
        assertEquals("VLOLAK5uy_fixtureSongs", Parsers.artistSongsBrowseId(parse(ARTIST)))
    }

    @Test
    fun `an artist page without a song list falls back to its videos`() {
        val artist = Parsers.artist("UCfixtureArtist", parse(ARTIST_VIDEOS_ONLY))

        assertEquals(listOf("vid00000009"), artist.topSongs.map { it.id })
        assertNull(Parsers.artistSongsBrowseId(parse(ARTIST_VIDEOS_ONLY)))
    }

    // ------------------------------------------------------------------ related

    @Test
    fun `finds the related browse id among the watch tabs`() {
        val next = json.decodeFromString<NextResponse>(NEXT)
        assertEquals("MPTRfixture", Parsers.relatedBrowseId(next))
    }

    @Test
    fun `reads an age-restricted video's details from the player endpoint`() {
        val song = Parsers.videoDetails("qpgTC9MDx1o", json.decodeFromString<PlayerResponse>(PLAYER))!!

        assertEquals("Animals", song.title)
        assertEquals("Kara's Flowers", song.artistLine)
        assertEquals(280_000L, song.durationMs)
        assertEquals("https://i.example/big.jpg", song.thumbnailUrl)
        assertNull(Parsers.videoDetails("qpgTC9MDx1o", json.decodeFromString<PlayerResponse>("{}")))
    }

    @Test
    fun `returns no related id when the tab is absent`() {
        val next = json.decodeFromString<NextResponse>(NEXT_WITHOUT_RELATED)
        assertNull(Parsers.relatedBrowseId(next))
    }

    @Test
    fun `reads shelves hung directly off contents`() {
        val page = Parsers.page(Parsers.directSections(parse(RELATED)))
        assertEquals(listOf("You might also like"), page.shelves.map { it.title })
        assertEquals(listOf("Midnight Signal"), page.songs.map { it.title })
    }

    // ------------------------------------------------------------------ resilience

    @Test
    fun `an empty response yields an empty page rather than failing`() {
        val page = Parsers.page(Parsers.singleColumnSections(parse("{}")))
        assertTrue(page.shelves.isEmpty())
        assertNull(Parsers.album("MPREb_x", parse("{}")))
        assertNull(Parsers.playlist("VLx", parse("{}")))
    }

    @Test
    fun `unknown fields in the payload are ignored`() {
        val page = Parsers.page(Parsers.singleColumnSections(parse(HOME_WITH_UNKNOWN_FIELDS)))
        assertEquals(1, page.shelves.size)
        assertEquals(1, page.albums.size)
    }
}

// ---------------------------------------------------------------------- fixtures

private const val THUMBS =
    """"thumbnails":[{"url":"https://i.example/small.jpg","width":60,"height":60},""" +
        """{"url":"https://i.example/big.jpg","width":544,"height":544}]"""

private const val ALBUM_CARD = """
{
  "musicTwoRowItemRenderer": {
    "title": {"runs": [{"text": "Night Drive",
      "navigationEndpoint": {"browseEndpoint": {"browseId": "MPREb_fixture01"}}}]},
    "subtitle": {"runs": [
      {"text": "Album"}, {"text": " \u2022 "},
      {"text": "Neon Fields", "navigationEndpoint": {"browseEndpoint": {
        "browseId": "UCfixtureArtist",
        "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {
          "pageType": "MUSIC_PAGE_TYPE_ARTIST"}}}}},
      {"text": " \u2022 "}, {"text": "2021"}]},
    "thumbnailRenderer": {"musicThumbnailRenderer": {"thumbnail": {$THUMBS}}},
    "navigationEndpoint": {"browseEndpoint": {
      "browseId": "MPREb_fixture01",
      "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {
        "pageType": "MUSIC_PAGE_TYPE_ALBUM"}}}}
  }
}
"""

private const val PLAYLIST_CARD = """
{
  "musicTwoRowItemRenderer": {
    "title": {"runs": [{"text": "Chill Mix"}]},
    "subtitle": {"runs": [{"text": "YouTube Music"}]},
    "thumbnailRenderer": {"musicThumbnailRenderer": {"thumbnail": {$THUMBS}}},
    "navigationEndpoint": {"browseEndpoint": {
      "browseId": "VLRDCLAK5fixture",
      "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {
        "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"}}}},
    "thumbnailOverlay": {"musicItemThumbnailOverlayRenderer": {"content": {
      "musicPlayButtonRenderer": {"playNavigationEndpoint": {
        "watchPlaylistEndpoint": {"playlistId": "RDCLAK5fixture"}}}}}}
  }
}
"""

private const val SONG_ROW = """
{
  "musicResponsiveListItemRenderer": {
    "playlistItemData": {"videoId": "vid00000001"},
    "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [
      {"url": "https://i.example/song-small.jpg", "width": 60, "height": 60},
      {"url": "https://i.example/song-big.jpg", "width": 226, "height": 226}]}}},
    "badges": [{"musicInlineBadgeRenderer": {"icon": {"iconType": "MUSIC_EXPLICIT_BADGE"}}}],
    "flexColumns": [
      {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
        {"text": "Midnight Signal",
         "navigationEndpoint": {"watchEndpoint": {"videoId": "vid00000001"}}}]}}},
      {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
        {"text": "Song"}, {"text": " \u2022 "},
        {"text": "Neon Fields", "navigationEndpoint": {"browseEndpoint": {
          "browseId": "UCfixtureArtist",
          "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {
            "pageType": "MUSIC_PAGE_TYPE_ARTIST"}}}}},
        {"text": " \u2022 "},
        {"text": "Night Drive", "navigationEndpoint": {"browseEndpoint": {
          "browseId": "MPREb_fixture01",
          "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {
            "pageType": "MUSIC_PAGE_TYPE_ALBUM"}}}}},
        {"text": " \u2022 "}, {"text": "3:35"}]}}}
    ]
  }
}
"""

private const val HOME = """
{"contents": {"singleColumnBrowseResultsRenderer": {"tabs": [{"tabRenderer": {"content": {
  "sectionListRenderer": {"contents": [
    {"musicCarouselShelfRenderer": {
      "header": {"musicCarouselShelfBasicHeaderRenderer": {
        "title": {"runs": [{"text": "Listen again"}]}}},
      "contents": [$ALBUM_CARD, $PLAYLIST_CARD]}}
  ]}}}}]}}}
"""

private const val HOME_WITH_JUNK_SHELF = """
{"contents": {"singleColumnBrowseResultsRenderer": {"tabs": [{"tabRenderer": {"content": {
  "sectionListRenderer": {"contents": [
    {"musicTastebuilderShelfRenderer": {"thumbnail": {}}},
    {"musicDescriptionShelfRenderer": {"description": {"runs": [{"text": "About"}]}}},
    {"musicCarouselShelfRenderer": {
      "header": {"musicCarouselShelfBasicHeaderRenderer": {
        "title": {"runs": [{"text": "Listen again"}]}}},
      "contents": [$ALBUM_CARD]}},
    {"musicCarouselShelfRenderer": {
      "header": {"musicCarouselShelfBasicHeaderRenderer": {
        "title": {"runs": [{"text": "Empty"}]}}},
      "contents": []}}
  ]}}}}]}}}
"""

private const val HOME_WITH_UNKNOWN_FIELDS = """
{"responseContext": {"visitorData": "abc", "maxAgeSeconds": 300},
 "trackingParams": "xyz",
 "contents": {"singleColumnBrowseResultsRenderer": {"tabs": [{"tabRenderer": {
   "selected": true, "trackingParams": "t", "content": {
   "sectionListRenderer": {"trackingParams": "s", "continuations": [{"nextContinuationData": {
     "continuation": "cont-token"}}], "contents": [
     {"musicCarouselShelfRenderer": {
       "numItemsPerColumn": 1,
       "header": {"musicCarouselShelfBasicHeaderRenderer": {
         "title": {"runs": [{"text": "Listen again"}]}, "trackingParams": "h"}},
       "contents": [$ALBUM_CARD]}}
   ]}}}}]}}}
"""

private const val SEARCH = """
{"contents": {"tabbedSearchResultsRenderer": {"tabs": [{"tabRenderer": {"content": {
  "sectionListRenderer": {"contents": [
    {"musicShelfRenderer": {"title": {"runs": [{"text": "Songs"}]},
      "contents": [$SONG_ROW]}}
  ]}}}}]}}}
"""

private const val SEARCH_PLAIN_TEXT_CREDITS = """
{"contents": {"tabbedSearchResultsRenderer": {"tabs": [{"tabRenderer": {"content": {
  "sectionListRenderer": {"contents": [
    {"musicShelfRenderer": {"title": {"runs": [{"text": "Songs"}]}, "contents": [
      {"musicResponsiveListItemRenderer": {
        "playlistItemData": {"videoId": "vid00000002"},
        "flexColumns": [
          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
            {"text": "Midnight Signal"}]}}},
          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
            {"text": "Neon Fields, Aurora Kane"}, {"text": " \u2022 "}, {"text": "2021"},
            {"text": " \u2022 "}, {"text": "3:35"}]}}}
        ]}}
    ]}}
  ]}}}}]}}}
"""

private const val SEARCH_VIDEO_RESULT = """
{"contents": {"tabbedSearchResultsRenderer": {"tabs": [{"tabRenderer": {"content": {
  "sectionListRenderer": {"contents": [
    {"musicShelfRenderer": {"title": {"runs": [{"text": "Videos"}]}, "contents": [
      {"musicResponsiveListItemRenderer": {
        "playlistItemData": {"videoId": "vid00000004"},
        "flexColumns": [
          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
            {"text": "Midnight Signal (Live)"}]}}},
          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
            {"text": "Neon Fields"}, {"text": " \u2022 "}, {"text": "1M views"},
            {"text": " \u2022 "}, {"text": "3:35"}]}}}
        ]}}
    ]}}
  ]}}}}]}}}
"""

private const val SEARCH_FAN_UPLOADS = """
{"contents": {"tabbedSearchResultsRenderer": {"tabs": [{"tabRenderer": {"content": {
  "sectionListRenderer": {"contents": [
    {"musicShelfRenderer": {"title": {"runs": [{"text": "Videos"}]}, "contents": [
      {"musicResponsiveListItemRenderer": {
        "playlistItemData": {"videoId": "vid00000005"},
        "flexColumns": [
          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
            {"text": "Neon Fields - Night Drive (Unreleased)"}]}}},
          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
            {"text": "Leak Archive", "navigationEndpoint": {"browseEndpoint": {
              "browseId": "UCfixtureUploader",
              "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {
                "pageType": "MUSIC_PAGE_TYPE_USER_CHANNEL"}}}}},
            {"text": " \u2022 "}, {"text": "12K views"}, {"text": " \u2022 "}, {"text": "2:47"}]}}}
        ]}},
      {"musicResponsiveListItemRenderer": {
        "playlistItemData": {"videoId": "vid00000006"},
        "flexColumns": [
          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
            {"text": "Night Drive snippet #shorts"}]}}},
          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
            {"text": "Aug 4"}]}}}
        ]}}
    ]}}
  ]}}}}]}}}
"""

private const val SEARCH_ARTIST_RESULT = """
{"contents": {"tabbedSearchResultsRenderer": {"tabs": [{"tabRenderer": {"content": {
  "sectionListRenderer": {"contents": [
    {"musicShelfRenderer": {"title": {"runs": [{"text": "Artists"}]}, "contents": [
      {"musicResponsiveListItemRenderer": {
        "navigationEndpoint": {"browseEndpoint": {
          "browseId": "UCfixtureArtist",
          "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {
            "pageType": "MUSIC_PAGE_TYPE_ARTIST"}}}},
        "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [
          {"url": "https://i.example/artist-big.jpg", "width": 120, "height": 120}]}}},
        "flexColumns": [
          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
            {"text": "Neon Fields"}]}}},
          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
            {"text": "Artist"}, {"text": " \u2022 "}, {"text": "3.2M subscribers"}]}}}
        ]}}
    ]}}
  ]}}}}]}}}
"""

private const val ALBUM_TRACK_ROW = """
{
  "musicResponsiveListItemRenderer": {
    "playlistItemData": {"videoId": "vid00000001"},
    "flexColumns": [
      {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
        {"text": "Midnight Signal",
         "navigationEndpoint": {"watchEndpoint": {"videoId": "vid00000001"}}}]}}},
      {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
        {"text": "Neon Fields", "navigationEndpoint": {"browseEndpoint": {
          "browseId": "UCfixtureArtist",
          "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {
            "pageType": "MUSIC_PAGE_TYPE_ARTIST"}}}}}]}}}
    ],
    "fixedColumns": [
      {"musicResponsiveListItemFixedColumnRenderer": {"text": {"runs": [{"text": "3:35"}]}}}
    ]
  }
}
"""

private const val ALBUM = """
{"contents": {"twoColumnBrowseResultsRenderer": {
  "tabs": [{"tabRenderer": {"content": {"sectionListRenderer": {"contents": [
    {"musicResponsiveHeaderRenderer": {
      "title": {"runs": [{"text": "Night Drive"}]},
      "straplineTextOne": {"runs": [{"text": "Neon Fields",
        "navigationEndpoint": {"browseEndpoint": {
          "browseId": "UCfixtureArtist",
          "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {
            "pageType": "MUSIC_PAGE_TYPE_ARTIST"}}}}}]},
      "subtitle": {"runs": [{"text": "Album"}, {"text": " \u2022 "}, {"text": "2021"}]},
      "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [
        {"url": "https://i.example/cover-small.jpg", "width": 60, "height": 60},
        {"url": "https://i.example/cover-big.jpg", "width": 544, "height": 544}]}}}
    }}
  ]}}}}],
  "secondaryContents": {"sectionListRenderer": {"contents": [
    {"musicShelfRenderer": {"contents": [$ALBUM_TRACK_ROW,
      {"musicResponsiveListItemRenderer": {
        "playlistItemData": {"videoId": "vid00000002"},
        "flexColumns": [
          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
            {"text": "Afterglow"}]}}}
        ],
        "fixedColumns": [
          {"musicResponsiveListItemFixedColumnRenderer": {"text": {"runs": [{"text": "4:02"}]}}}
        ]}}
    ]}}
  ]}}
}}}
"""

private const val PLAYLIST = """
{"contents": {"twoColumnBrowseResultsRenderer": {
  "tabs": [{"tabRenderer": {"content": {"sectionListRenderer": {"contents": [
    {"musicResponsiveHeaderRenderer": {
      "title": {"runs": [{"text": "Chill Mix"}]},
      "subtitle": {"runs": [{"text": "Playlist"}, {"text": " \u2022 "},
        {"text": "YouTube Music"}]},
      "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [
        {"url": "https://i.example/mix-big.jpg", "width": 544, "height": 544}]}}}
    }}
  ]}}}}],
  "secondaryContents": {"sectionListRenderer": {"contents": [
    {"musicPlaylistShelfRenderer": {"contents": [
      {"musicResponsiveListItemRenderer": {
        "playlistItemData": {"videoId": "vid00000003"},
        "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [
          {"url": "https://i.example/track-big.jpg", "width": 226, "height": 226}]}}},
        "flexColumns": [
          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
            {"text": "Low Tide"}]}}}
        ],
        "fixedColumns": [
          {"musicResponsiveListItemFixedColumnRenderer": {"text": {"runs": [{"text": "2:58"}]}}}
        ]}}
    ]}}
  ]}}
}}}
"""

private const val PLAYER = """
{"playabilityStatus": {"status": "LOGIN_REQUIRED", "reason": "Sign in to confirm your age"},
 "videoDetails": {"videoId": "qpgTC9MDx1o", "title": "Animals", "author": "Kara's Flowers",
   "lengthSeconds": "280", "thumbnail": {$THUMBS}}}
"""

private const val PLAYLIST_WITH_MORE = """
{"contents": {"twoColumnBrowseResultsRenderer": {
  "secondaryContents": {"sectionListRenderer": {"contents": [
    {"musicPlaylistShelfRenderer": {"contents": [
      $SONG_ROW,
      {"continuationItemRenderer": {"continuationEndpoint": {"continuationCommand": {"token": "CONT_TOKEN_1"}}}}
    ]}}
  ]}}
}}}
"""

private const val PLAYLIST_CONTINUATION = """
{"onResponseReceivedActions": [{"appendContinuationItemsAction": {"continuationItems": [
  {"musicResponsiveListItemRenderer": {
    "playlistItemData": {"videoId": "vid00000004"},
    "flexColumns": [
      {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Undertow"}]}}}
    ]}},
  {"continuationItemRenderer": {"continuationEndpoint": {"continuationCommand": {"token": "CONT_TOKEN_2"}}}}
]}}]}
"""

private const val VIDEO_CARD = """
{
  "musicTwoRowItemRenderer": {
    "title": {"runs": [{"text": "Midnight Signal (Official Video)"}]},
    "subtitle": {"runs": [{"text": "Neon Fields"}, {"text": " \u2022 "}, {"text": "2M views"}]},
    "thumbnailRenderer": {"musicThumbnailRenderer": {"thumbnail": {$THUMBS}}},
    "navigationEndpoint": {"watchEndpoint": {"videoId": "vid00000009"}}
  }
}
"""

private const val ARTIST = """
{
  "header": {"musicImmersiveHeaderRenderer": {
    "title": {"runs": [{"text": "Neon Fields"}]},
    "subscriptionButton": {"subscribeButtonRenderer": {
      "subscriberCountText": {"runs": [{"text": "1.2M subscribers"}]}}},
    "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [
      {"url": "https://i.example/artist-small.jpg", "width": 60, "height": 60},
      {"url": "https://i.example/artist-big.jpg", "width": 1200, "height": 600}]}}}
  }},
  "contents": {"singleColumnBrowseResultsRenderer": {"tabs": [{"tabRenderer": {"content": {
    "sectionListRenderer": {"contents": [
      {"musicShelfRenderer": {"title": {"runs": [{"text": "Songs"}]},
        "contents": [$SONG_ROW],
        "bottomEndpoint": {"browseEndpoint": {"browseId": "VLOLAK5uy_fixtureSongs"}}}},
      {"musicCarouselShelfRenderer": {
        "header": {"musicCarouselShelfBasicHeaderRenderer": {
          "title": {"runs": [{"text": "Albums"}]}}},
        "contents": [$ALBUM_CARD]}},
      {"musicCarouselShelfRenderer": {
        "header": {"musicCarouselShelfBasicHeaderRenderer": {
          "title": {"runs": [{"text": "Videos"}]}}},
        "contents": [$VIDEO_CARD]}}
    ]}}}}]}}
}
"""

private const val ARTIST_VIDEOS_ONLY = """
{
  "header": {"musicImmersiveHeaderRenderer": {"title": {"runs": [{"text": "Neon Fields"}]}}},
  "contents": {"singleColumnBrowseResultsRenderer": {"tabs": [{"tabRenderer": {"content": {
    "sectionListRenderer": {"contents": [
      {"musicCarouselShelfRenderer": {
        "header": {"musicCarouselShelfBasicHeaderRenderer": {
          "title": {"runs": [{"text": "Videos"}]}}},
        "contents": [$VIDEO_CARD, $VIDEO_CARD]}}
    ]}}}}]}}
}
"""

private const val NEXT = """
{"contents": {"singleColumnMusicWatchNextResultsRenderer": {"tabbedRenderer": {
  "watchNextTabbedResultsRenderer": {"tabs": [
    {"tabRenderer": {"title": "Up next", "content": {}}},
    {"tabRenderer": {"title": "Lyrics",
      "endpoint": {"browseEndpoint": {"browseId": "MPLYfixture"}}}},
    {"tabRenderer": {"title": "Related",
      "endpoint": {"browseEndpoint": {"browseId": "MPTRfixture"}}}}
  ]}}}}}
"""

private const val NEXT_WITHOUT_RELATED = """
{"contents": {"singleColumnMusicWatchNextResultsRenderer": {"tabbedRenderer": {
  "watchNextTabbedResultsRenderer": {"tabs": [
    {"tabRenderer": {"title": "Up next", "content": {}}}
  ]}}}}}
"""

private const val RELATED = """
{"contents": {"sectionListRenderer": {"contents": [
  {"musicDescriptionShelfRenderer": {"description": {"runs": [{"text": "About this song"}]}}},
  {"musicCarouselShelfRenderer": {
    "header": {"musicCarouselShelfBasicHeaderRenderer": {
      "title": {"runs": [{"text": "You might also like"}]}}},
    "contents": [$SONG_ROW]}}
]}}}
"""
