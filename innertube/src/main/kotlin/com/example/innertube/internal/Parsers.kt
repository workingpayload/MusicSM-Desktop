package com.example.innertube.internal

import com.example.innertube.model.YtAlbum
import com.example.innertube.model.YtAlbumRef
import com.example.innertube.model.YtArtist
import com.example.innertube.model.YtArtistRef
import com.example.innertube.model.YtItem
import com.example.innertube.model.YtPage
import com.example.innertube.model.YtPlaylist
import com.example.innertube.model.YtShelf
import com.example.innertube.model.YtSong

/** One page of a long track list, and the token for the page after it (null at the end). */
internal data class TrackPage(val songs: List<YtSong>, val continuation: String?)

/**
 * Turns InnerTube's renderer tree into the module's public models.
 *
 * Two rules run through all of it. Nothing throws — a page that changes shape should cost a
 * missing shelf, not a crashed screen — and identification is driven by the endpoint attached to
 * an item rather than by its position, because YouTube reorders shelves freely but the endpoint
 * on a card is what actually says whether it is an album, an artist or a track.
 */
internal object Parsers {

    private const val PAGE_TYPE_ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
    private const val PAGE_TYPE_ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
    private const val PAGE_TYPE_PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"
    private const val PAGE_TYPE_USER_CHANNEL = "MUSIC_PAGE_TYPE_USER_CHANNEL"
    private const val EXPLICIT_BADGE = "MUSIC_EXPLICIT_BADGE"

    /** Album browse ids are prefixed; playlist browse ids are the playlist id behind a `VL`. */
    private const val ALBUM_ID_PREFIX = "MPREb_"
    private const val PLAYLIST_BROWSE_PREFIX = "VL"

    // ---------------------------------------------------------------- pages

    /** Home and artist pages: shelves live under the first tab of the single-column envelope. */
    fun singleColumnList(response: InnerTubeResponse): SectionListRenderer? =
        response.contents?.singleColumnBrowseResultsRenderer?.tabs
            ?.firstNotNullOfOrNull { it.tabRenderer?.content?.sectionListRenderer }

    fun singleColumnSections(response: InnerTubeResponse): List<SectionContent> =
        singleColumnList(response)?.contents.orEmpty()

    /** The extra shelves returned by a continuation request, rather than a whole page. */
    fun continuationList(response: InnerTubeResponse): SectionListRenderer? =
        response.continuationContents?.sectionListContinuation

    /** The "related" page hangs its section list straight off `contents`. */
    fun directSections(response: InnerTubeResponse): List<SectionContent> =
        response.contents?.sectionListRenderer?.contents.orEmpty()

    /** Album and playlist pages: the track list is in the two-column envelope's second column. */
    fun secondarySections(response: InnerTubeResponse): List<SectionContent> =
        response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
            ?.contents
            .orEmpty()

    /** The detail header of an album or playlist page, in the two-column envelope's first column. */
    fun responsiveHeader(response: InnerTubeResponse): MusicResponsiveHeaderRenderer? =
        response.contents?.twoColumnBrowseResultsRenderer?.tabs
            ?.firstNotNullOfOrNull { it.tabRenderer?.content?.sectionListRenderer }
            ?.contents
            ?.firstNotNullOfOrNull { it.musicResponsiveHeaderRenderer }

    fun searchSections(response: InnerTubeResponse): List<SectionContent> =
        response.contents?.tabbedSearchResultsRenderer?.tabs
            ?.firstNotNullOfOrNull { it.tabRenderer?.content?.sectionListRenderer }
            ?.contents
            .orEmpty()

    /** Every shelf on a page, whichever renderer it happens to use. */
    fun page(sections: List<SectionContent>): YtPage =
        YtPage(sections.mapNotNull(::shelf))

    /** As [page], but also carrying the cursor to the next batch of shelves. */
    fun page(list: SectionListRenderer?): YtPage =
        YtPage(list?.contents.orEmpty().mapNotNull(::shelf), list?.continuationToken)

    private fun shelf(section: SectionContent): YtShelf? {
        val carousel = section.musicCarouselShelfRenderer
        if (carousel != null) {
            val title = carousel.header?.musicCarouselShelfBasicHeaderRenderer?.title?.text
            return buildShelf(title, carousel.contents)
        }
        val list = section.musicShelfRenderer ?: section.musicPlaylistShelfRenderer
        if (list != null) return buildShelf(list.title?.text, list.contents)
        val grid = section.gridRenderer
        if (grid != null) return buildShelf(grid.header?.gridHeaderRenderer?.title?.text, grid.items)
        return null
    }

    private fun buildShelf(title: String?, contents: List<ShelfItem>): YtShelf? {
        val items = contents.mapNotNull(::item)
        if (items.isEmpty()) return null
        return YtShelf(title.orEmpty().ifBlank { UNTITLED_SHELF }, items)
    }

    /**
     * The track list of an album or playlist page.
     *
     * [fallbackThumbnail] covers album pages, where the rows carry no artwork of their own because
     * the whole release shares the cover shown in the header.
     */
    fun tracks(sections: List<SectionContent>, fallbackThumbnail: String? = null): List<YtSong> =
        sections
            .mapNotNull { it.musicShelfRenderer ?: it.musicPlaylistShelfRenderer }
            .flatMap { it.contents }
            .mapNotNull { it.musicResponsiveListItemRenderer }
            .mapNotNull { song(it) }
            .map { if (it.thumbnailUrl == null) it.copy(thumbnailUrl = fallbackThumbnail) else it }

    // ---------------------------------------------------------------- items

    fun item(raw: ShelfItem): YtItem? {
        raw.musicResponsiveListItemRenderer?.let { row ->
            // A list row is a track when it has a video id; otherwise it is a browse result, and
            // the endpoint's page type says which kind.
            song(row)?.let { return YtItem.Song(it) }
            val endpoint = row.navigationEndpoint?.browseEndpoint
            return browseItem(
                endpoint = endpoint,
                title = row.flexText(0)?.text,
                subtitleRuns = row.flexColumns.getOrNull(1)
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs.orEmpty(),
                thumbnailUrl = row.thumbnail.bestUrl(),
            )
        }

        val card = raw.musicTwoRowItemRenderer ?: return null
        val title = card.title?.firstText ?: return null
        val thumbnailUrl = card.thumbnailRenderer.bestUrl()
        val subtitleRuns = card.subtitle?.runs.orEmpty()

        // A card that plays a single track carries a watchEndpoint; anything else is a browse
        // destination. Checking the play overlay first matters because the card's own
        // navigationEndpoint on a song points at the watch page too.
        val videoId = card.navigationEndpoint?.watchEndpoint?.videoId
            ?: card.thumbnailOverlay?.playEndpoint?.watchEndpoint?.videoId
        if (videoId != null) {
            return YtItem.Song(
                YtSong(
                    id = videoId,
                    title = title,
                    artists = artistRefs(subtitleRuns),
                    album = albumRef(subtitleRuns),
                    durationMs = subtitleRuns.firstNotNullOfOrNull { parseDuration(it.text) } ?: 0L,
                    thumbnailUrl = thumbnailUrl,
                ),
            )
        }

        // Playlist cards identify themselves by the playlist their play button would start.
        val playlistId = card.thumbnailOverlay?.playEndpoint?.watchPlaylistEndpoint?.playlistId
        return browseItem(
            endpoint = card.navigationEndpoint?.browseEndpoint,
            title = title,
            subtitleRuns = subtitleRuns,
            thumbnailUrl = thumbnailUrl,
            playlistId = playlistId,
        )
    }

    private fun browseItem(
        endpoint: BrowseEndpoint?,
        title: String?,
        subtitleRuns: List<Run>,
        thumbnailUrl: String?,
        playlistId: String? = null,
    ): YtItem? {
        if (title.isNullOrBlank()) return null
        val browseId = endpoint?.browseId

        // Prefer the declared page type; fall back to the id's own shape, because a few shelves
        // ship cards without the context config attached.
        val type = endpoint?.pageType
            ?: when {
                browseId?.startsWith(ALBUM_ID_PREFIX) == true -> PAGE_TYPE_ALBUM
                browseId?.startsWith(PLAYLIST_BROWSE_PREFIX) == true -> PAGE_TYPE_PLAYLIST
                playlistId != null -> PAGE_TYPE_PLAYLIST
                else -> null
            }

        return when (type) {
            PAGE_TYPE_ALBUM -> YtItem.Album(
                YtAlbum(
                    id = browseId ?: return null,
                    title = title,
                    artists = artistRefs(subtitleRuns),
                    year = subtitleRuns.lastOrNull { it.text.isYear() }?.text,
                    thumbnailUrl = thumbnailUrl,
                ),
            )

            PAGE_TYPE_ARTIST -> YtItem.Artist(
                YtArtist(
                    id = browseId ?: return null,
                    name = title,
                    thumbnailUrl = thumbnailUrl,
                    subscribers = subscriberText(subtitleRuns),
                ),
            )

            PAGE_TYPE_PLAYLIST -> YtItem.Playlist(
                YtPlaylist(
                    id = playlistId ?: browseId?.removePrefix(PLAYLIST_BROWSE_PREFIX)
                        ?: return null,
                    title = title,
                    thumbnailUrl = thumbnailUrl,
                ),
            )

            else -> null
        }
    }

    /** A list row as a track, or null when the row is not a track at all. */
    fun song(row: MusicResponsiveListItemRenderer): YtSong? {
        val videoId = row.playlistItemData?.videoId
            ?: row.overlay?.playEndpoint?.watchEndpoint?.videoId
            ?: row.flexText(0)?.runs?.firstNotNullOfOrNull {
                it.navigationEndpoint?.watchEndpoint?.videoId
            }
            ?: return null
        val title = row.flexText(0)?.firstText?.takeIf { it.isNotBlank() } ?: return null

        val credits = row.flexColumns.getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            .orEmpty()

        // Album pages put the duration in a fixed column; search results inline it in the
        // credit line instead.
        val duration = row.fixedColumns
            .firstNotNullOfOrNull { it.musicResponsiveListItemFixedColumnRenderer?.text?.text }
            ?.let(::parseDuration)
            ?: credits.firstNotNullOfOrNull { parseDuration(it.text) }
            ?: 0L

        return YtSong(
            id = videoId,
            title = title,
            artists = artistRefs(credits),
            album = albumRef(credits),
            durationMs = duration,
            thumbnailUrl = row.thumbnail.bestUrl(),
            explicit = row.badges.any {
                it.musicInlineBadgeRenderer?.icon?.iconType == EXPLICIT_BADGE
            },
        )
    }

    // ---------------------------------------------------------------- headers

    fun album(id: String, response: InnerTubeResponse): YtAlbum? {
        val header = responsiveHeader(response) ?: return null
        val title = header.title?.text?.takeIf { it.isNotBlank() } ?: return null
        val cover = header.thumbnail?.musicThumbnailRenderer?.thumbnail?.best()
        return YtAlbum(
            id = id,
            title = title,
            artists = header.straplineTextOne?.runs?.let(::artistRefs)
                ?: header.subtitle?.runs?.let(::artistRefs).orEmpty(),
            year = header.subtitle?.runs?.lastOrNull { it.text.isYear() }?.text,
            thumbnailUrl = cover,
            songs = tracks(secondarySections(response), fallbackThumbnail = cover),
        )
    }

    fun playlist(id: String, response: InnerTubeResponse): YtPlaylist? {
        val header = responsiveHeader(response) ?: return null
        val title = header.title?.text?.takeIf { it.isNotBlank() } ?: return null
        val cover = header.thumbnail?.musicThumbnailRenderer?.thumbnail?.best()
        return YtPlaylist(
            id = id,
            title = title,
            thumbnailUrl = cover,
            songs = tracks(secondarySections(response), fallbackThumbnail = cover),
        )
    }

    /** Token for the next page of a playlist's tracks; null when the first page is all of it. */
    fun playlistContinuation(response: InnerTubeResponse): String? =
        secondarySections(response)
            .mapNotNull { it.musicShelfRenderer ?: it.musicPlaylistShelfRenderer }
            .flatMap { it.contents }
            .firstNotNullOfOrNull { it.continuationItemRenderer?.token }

    /** A further page of a playlist's tracks, from a continuation request. */
    fun playlistPage(response: InnerTubeResponse, fallbackThumbnail: String? = null): TrackPage {
        val items = response.onResponseReceivedActions
            .flatMap { it.appendContinuationItemsAction?.continuationItems.orEmpty() }
        val songs = items
            .mapNotNull { it.musicResponsiveListItemRenderer }
            .mapNotNull { song(it) }
            .map { if (it.thumbnailUrl == null) it.copy(thumbnailUrl = fallbackThumbnail) else it }
        return TrackPage(songs, items.firstNotNullOfOrNull { it.continuationItemRenderer?.token })
    }

    fun artist(id: String, response: InnerTubeResponse): YtArtist {
        val header = response.header?.musicImmersiveHeaderRenderer
        val sections = singleColumnSections(response)
        val page = page(sections)
        // The songs are the "Top songs" list. The "Videos" and "Live performances" carousels are
        // music videos - mostly of those same songs, under other video ids - so they only count
        // on a page that has no song list at all.
        val songs = tracks(sections).ifEmpty { page.songs }
        return YtArtist(
            id = id,
            name = header?.title?.text.orEmpty(),
            thumbnailUrl = header?.thumbnail.bestUrl(),
            subscribers = header?.subscriptionButton?.subscribeButtonRenderer
                ?.subscriberCountText?.text,
            topSongs = songs.distinctBy { it.id },
            albums = page.albums.distinctBy { it.id },
        )
    }

    /** Browse id of an artist's full song list, behind the "Top songs" shelf's "Show all". */
    fun artistSongsBrowseId(response: InnerTubeResponse): String? {
        val shelf = singleColumnSections(response).firstNotNullOfOrNull { it.musicShelfRenderer } ?: return null
        return (shelf.bottomEndpoint ?: shelf.title?.runs?.firstOrNull()?.navigationEndpoint)
            ?.browseEndpoint?.browseId
    }

    /** The `MPTR…` browse id of a track's "Related" tab, if the next response offers one. */
    fun relatedBrowseId(response: NextResponse): String? =
        watchTabBrowseIds(response).firstOrNull { it.startsWith(RELATED_ID_PREFIX) }

    /** A video's title, channel (as its artist) and length, from the player endpoint. */
    fun videoDetails(videoId: String, response: PlayerResponse): YtSong? {
        val details = response.videoDetails ?: return null
        val title = details.title?.takeIf { it.isNotBlank() } ?: return null
        return YtSong(
            id = details.videoId ?: videoId,
            title = title,
            artists = listOfNotNull(details.author?.takeIf { it.isNotBlank() }?.let { YtArtistRef(it) }),
            durationMs = (details.lengthSeconds?.toLongOrNull() ?: 0L) * 1000,
            thumbnailUrl = details.thumbnail.best(),
        )
    }

    /** The `MPLY…` browse id of a track's "Lyrics" tab; absent when YouTube Music has none. */
    fun lyricsBrowseId(response: NextResponse): String? =
        watchTabBrowseIds(response).firstOrNull { it.startsWith(LYRICS_ID_PREFIX) }

    /** The plain lyrics text of a browsed "Lyrics" tab, or null when it is empty. */
    fun lyricsText(response: InnerTubeResponse): String? =
        (response.contents?.sectionListRenderer?.contents
            ?: response.contents?.singleColumnBrowseResultsRenderer?.tabs
                ?.firstNotNullOfOrNull { it.tabRenderer?.content?.sectionListRenderer }?.contents)
            ?.firstNotNullOfOrNull { it.musicDescriptionShelfRenderer?.description?.text }
            ?.takeIf { it.isNotBlank() }

    private fun watchTabBrowseIds(response: NextResponse): List<String> =
        response.contents?.singleColumnMusicWatchNextResultsRenderer?.tabbedRenderer
            ?.watchNextTabbedResultsRenderer?.tabs
            ?.mapNotNull { it.tabRenderer?.endpoint?.browseEndpoint?.browseId }
            .orEmpty()

    private const val RELATED_ID_PREFIX = "MPTR"
    private const val LYRICS_ID_PREFIX = "MPLY"
    private const val UNTITLED_SHELF = "More"
    private const val SUBSCRIBER_HINT = "subscriber"

    /** Requests are sent with `hl=en`, so these labels come back in English. */
    private val AUDIENCE_HINTS = listOf("view", "play")

    private val DATE = Regex(
        """(?i)(?:(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.? \d{1,2}(?:, \d{4})?|\d+ (?:second|minute|hour|day|week|month|year)s? ago)""",
    )

    // ---------------------------------------------------------------- helpers

    /** Credits that link to an artist page. Plain-text credits are kept without an id. */
    private fun artistRefs(runs: List<Run>): List<YtArtistRef> {
        val linked = runs
            .filter { it.navigationEndpoint?.browseEndpoint?.pageType == PAGE_TYPE_ARTIST }
            .mapNotNull { run ->
                run.navigationEndpoint?.browseEndpoint?.browseId?.let {
                    YtArtistRef(run.text.trim(), it)
                }
            }
        if (linked.isNotEmpty()) return linked

        // A fan upload (an unreleased track, a cover) is credited to the channel that posted it.
        // That isn't an artist page, so it's kept as a name only.
        val uploaders = runs
            .filter { it.navigationEndpoint?.browseEndpoint?.pageType == PAGE_TYPE_USER_CHANNEL }
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
        if (uploaders.isNotEmpty()) return uploaders.map { YtArtistRef(it) }

        // No links at all: YouTube rendered the credit as flat text, so split it on its own
        // separators and keep the parts that are not metadata.
        return runs
            .filter { it.navigationEndpoint == null }
            .map { it.text }
            .flatMap { it.split(",", "&") }
            .map { it.trim() }
            .filter {
                it.isNotEmpty() && !it.isSeparator() && !it.isYear() && !it.isDate() &&
                    !it.isAudienceCount() && parseDuration(it) == null
            }
            .map { YtArtistRef(it) }
    }

    private fun albumRef(runs: List<Run>): YtAlbumRef? =
        runs.firstOrNull { it.navigationEndpoint?.browseEndpoint?.pageType == PAGE_TYPE_ALBUM }
            ?.let { YtAlbumRef(it.text.trim(), it.navigationEndpoint?.browseEndpoint?.browseId) }

    /**
     * The audience size from an artist card's subtitle.
     *
     * A search result's subtitle leads with the literal word "Artist" before the count, so the
     * first run is never the answer — taking it would print "Artist" where a follower count
     * belongs.
     */
    private fun subscriberText(runs: List<Run>): String? {
        val texts = runs.map { it.text.trim() }.filter { it.isNotEmpty() && !it.isSeparator() }
        return texts.firstOrNull { it.contains(SUBSCRIBER_HINT, ignoreCase = true) }
            ?: texts.firstOrNull { it.first().isDigit() }
    }

    /** `"4:36"` or `"1:02:11"` to milliseconds, or null when the text is not a duration. */
    fun parseDuration(text: String?): Long? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty() || ':' !in trimmed) return null
        val parts = trimmed.split(':')
        if (parts.size !in 2..3) return null
        var total = 0L
        for (part in parts) {
            val value = part.toIntOrNull() ?: return null
            if (value < 0) return null
            total = total * 60 + value
        }
        return total * 1000
    }

    private fun String.isSeparator(): Boolean =
        isBlank() || all { it == '•' || it == '·' || it.isWhitespace() }

    private fun String.isYear(): Boolean =
        length == 4 && all { it.isDigit() } && toInt() in 1900..2999

    /** An upload date ("Aug 4", "Aug 4, 2024", "3 days ago"), which Shorts show instead of a length. */
    private fun String.isDate(): Boolean = DATE.matches(this)

    /**
     * Play and view counts, which sit in the same credit line as artist names on video results
     * and would otherwise be read as a performer called "1M views".
     */
    private fun String.isAudienceCount(): Boolean =
        first().isDigit() && AUDIENCE_HINTS.any { contains(it, ignoreCase = true) }

    /** YouTube lists thumbnails smallest-first, so the last one is the best available. */
    private fun Thumbnails?.best(): String? = this?.thumbnails?.lastOrNull()?.url

    private fun ThumbnailRenderer?.bestUrl(): String? =
        this?.musicThumbnailRenderer?.thumbnail.best()

    private fun MusicResponsiveListItemRenderer.flexText(index: Int): Runs? =
        flexColumns.getOrNull(index)?.musicResponsiveListItemFlexColumnRenderer?.text
}
