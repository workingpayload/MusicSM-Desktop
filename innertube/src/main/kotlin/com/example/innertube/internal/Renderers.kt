package com.example.innertube.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The request envelopes InnerTube expects.
 *
 * Every endpoint takes the same `context` block identifying the calling client, plus a handful of
 * endpoint-specific fields. Nulls are not serialized (see the Json config in the client), so one
 * body type can cover both an initial request and a continuation.
 */

@Serializable
internal data class ClientInfo(
    val clientName: String,
    val clientVersion: String,
    val hl: String,
    val gl: String,
    /**
     * The anonymous session identity YouTube hands out on the first response. Continuations are
     * scoped to a session, so paging silently returns nothing at all without it.
     */
    val visitorData: String? = null,
)

@Serializable
internal data class RequestContext(
    val client: ClientInfo,
)

@Serializable
internal data class BrowseBody(
    val context: RequestContext,
    val browseId: String? = null,
    val params: String? = null,
    val continuation: String? = null,
)

@Serializable
internal data class SearchBody(
    val context: RequestContext,
    val query: String,
    val params: String? = null,
)

@Serializable
internal data class NextBody(
    val context: RequestContext,
    val videoId: String,
    val playlistId: String? = null,
    val isAudioOnly: Boolean = true,
)

@Serializable
internal data class PlayerBody(
    val context: RequestContext,
    val videoId: String,
)

/**
 * The response subset this module reads.
 *
 * Only the fields actually used are declared; the Json parser is configured to ignore everything
 * else, which is most of the payload. That keeps the module resilient — YouTube adds and removes
 * sibling fields constantly, and none of that should break parsing.
 */

@Serializable
internal data class Thumbnail(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
internal data class Thumbnails(
    val thumbnails: List<Thumbnail> = emptyList(),
)

@Serializable
internal data class MusicThumbnailRenderer(
    val thumbnail: Thumbnails? = null,
)

@Serializable
internal data class ThumbnailRenderer(
    val musicThumbnailRenderer: MusicThumbnailRenderer? = null,
)

@Serializable
internal data class BrowseEndpointMusicConfig(
    val pageType: String? = null,
)

@Serializable
internal data class BrowseEndpointConfigs(
    val browseEndpointContextMusicConfig: BrowseEndpointMusicConfig? = null,
)

@Serializable
internal data class BrowseEndpoint(
    val browseId: String? = null,
    val params: String? = null,
    val browseEndpointContextSupportedConfigs: BrowseEndpointConfigs? = null,
) {
    val pageType: String?
        get() = browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
}

@Serializable
internal data class WatchEndpoint(
    val videoId: String? = null,
    val playlistId: String? = null,
)

@Serializable
internal data class WatchPlaylistEndpoint(
    val playlistId: String? = null,
    val params: String? = null,
)

@Serializable
internal data class NavigationEndpoint(
    val watchEndpoint: WatchEndpoint? = null,
    val browseEndpoint: BrowseEndpoint? = null,
    val watchPlaylistEndpoint: WatchPlaylistEndpoint? = null,
)

@Serializable
internal data class Run(
    val text: String = "",
    val navigationEndpoint: NavigationEndpoint? = null,
)

@Serializable
internal data class Runs(
    val runs: List<Run> = emptyList(),
) {
    val text: String get() = runs.joinToString("") { it.text }
    val firstText: String? get() = runs.firstOrNull()?.text
}

@Serializable
internal data class MusicPlayButtonRenderer(
    val playNavigationEndpoint: NavigationEndpoint? = null,
)

@Serializable
internal data class ThumbnailOverlayContent(
    val musicPlayButtonRenderer: MusicPlayButtonRenderer? = null,
)

@Serializable
internal data class MusicItemThumbnailOverlayRenderer(
    val content: ThumbnailOverlayContent? = null,
)

@Serializable
internal data class ThumbnailOverlay(
    val musicItemThumbnailOverlayRenderer: MusicItemThumbnailOverlayRenderer? = null,
) {
    val playEndpoint: NavigationEndpoint?
        get() = musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer
            ?.playNavigationEndpoint
}

/** The grid-style card used on carousels: albums, playlists, artists and video-ish songs. */
@Serializable
internal data class MusicTwoRowItemRenderer(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val navigationEndpoint: NavigationEndpoint? = null,
    val thumbnailRenderer: ThumbnailRenderer? = null,
    val thumbnailOverlay: ThumbnailOverlay? = null,
)

@Serializable
internal data class FlexColumnRenderer(
    val text: Runs? = null,
)

@Serializable
internal data class FlexColumn(
    val musicResponsiveListItemFlexColumnRenderer: FlexColumnRenderer? = null,
)

@Serializable
internal data class FixedColumnRenderer(
    val text: Runs? = null,
)

@Serializable
internal data class FixedColumn(
    val musicResponsiveListItemFixedColumnRenderer: FixedColumnRenderer? = null,
)

@Serializable
internal data class PlaylistItemData(
    val videoId: String? = null,
)

@Serializable
internal data class BadgeRenderer(
    val icon: BadgeIcon? = null,
)

@Serializable
internal data class BadgeIcon(
    val iconType: String? = null,
)

@Serializable
internal data class Badge(
    val musicInlineBadgeRenderer: BadgeRenderer? = null,
)

/** The list-style row used for tracks: search song results, album/playlist track lists. */
@Serializable
internal data class MusicResponsiveListItemRenderer(
    val flexColumns: List<FlexColumn> = emptyList(),
    val fixedColumns: List<FixedColumn> = emptyList(),
    val thumbnail: ThumbnailRenderer? = null,
    val playlistItemData: PlaylistItemData? = null,
    val overlay: ThumbnailOverlay? = null,
    val badges: List<Badge> = emptyList(),
    val navigationEndpoint: NavigationEndpoint? = null,
)

@Serializable
internal data class ShelfItem(
    val musicTwoRowItemRenderer: MusicTwoRowItemRenderer? = null,
    val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
    /** Last item of a long list: where its next page is. */
    val continuationItemRenderer: ContinuationItemRenderer? = null,
)

@Serializable
internal data class ContinuationCommand(
    val token: String? = null,
)

@Serializable
internal data class ContinuationEndpoint(
    val continuationCommand: ContinuationCommand? = null,
)

@Serializable
internal data class ContinuationItemRenderer(
    val continuationEndpoint: ContinuationEndpoint? = null,
) {
    val token: String? get() = continuationEndpoint?.continuationCommand?.token
}

/** A further page of a list, as a continuation request returns it. */
@Serializable
internal data class AppendContinuationItemsAction(
    val continuationItems: List<ShelfItem> = emptyList(),
)

@Serializable
internal data class ResponseReceivedAction(
    val appendContinuationItemsAction: AppendContinuationItemsAction? = null,
)

@Serializable
internal data class CarouselHeaderRenderer(
    val title: Runs? = null,
)

@Serializable
internal data class CarouselHeader(
    val musicCarouselShelfBasicHeaderRenderer: CarouselHeaderRenderer? = null,
)

@Serializable
internal data class MusicCarouselShelfRenderer(
    val header: CarouselHeader? = null,
    val contents: List<ShelfItem> = emptyList(),
)

@Serializable
internal data class MusicShelfRenderer(
    val title: Runs? = null,
    val contents: List<ShelfItem> = emptyList(),
    /** "Show all": on an artist page, the artist's full song list. */
    val bottomEndpoint: NavigationEndpoint? = null,
)

@Serializable
internal data class MusicResponsiveHeaderRenderer(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val straplineTextOne: Runs? = null,
    val thumbnail: ThumbnailRendererWrapper? = null,
)

@Serializable
internal data class ThumbnailRendererWrapper(
    val musicThumbnailRenderer: MusicThumbnailRenderer? = null,
)

@Serializable
internal data class MusicImmersiveHeaderRenderer(
    val title: Runs? = null,
    val description: Runs? = null,
    val subscriptionButton: SubscriptionButton? = null,
    val thumbnail: ThumbnailRenderer? = null,
)

@Serializable
internal data class SubscriptionButton(
    val subscribeButtonRenderer: SubscribeButtonRenderer? = null,
)

@Serializable
internal data class SubscribeButtonRenderer(
    @SerialName("subscriberCountText") val subscriberCountText: Runs? = null,
)

@Serializable
internal data class PageHeader(
    val musicImmersiveHeaderRenderer: MusicImmersiveHeaderRenderer? = null,
)

/** A row of a `sectionListRenderer`, which may be any of several shelf renderers. */
@Serializable
internal data class SectionContent(
    val musicCarouselShelfRenderer: MusicCarouselShelfRenderer? = null,
    val musicShelfRenderer: MusicShelfRenderer? = null,
    val musicPlaylistShelfRenderer: MusicShelfRenderer? = null,
    val musicResponsiveHeaderRenderer: MusicResponsiveHeaderRenderer? = null,
    val musicDescriptionShelfRenderer: MusicDescriptionShelfRenderer? = null,
)

/** The plain-text block the "Lyrics" tab renders: the words, plus a "Source: …" footer. */
@Serializable
internal data class MusicDescriptionShelfRenderer(
    val description: Runs? = null,
    val footer: Runs? = null,
)

@Serializable
internal data class NextContinuationData(
    val continuation: String? = null,
)

@Serializable
internal data class ContinuationHolder(
    val nextContinuationData: NextContinuationData? = null,
)

@Serializable
internal data class SectionListRenderer(
    val contents: List<SectionContent> = emptyList(),
    val continuations: List<ContinuationHolder> = emptyList(),
) {
    val continuationToken: String?
        get() = continuations.firstNotNullOfOrNull { it.nextContinuationData?.continuation }
}

@Serializable
internal data class TabContent(
    val sectionListRenderer: SectionListRenderer? = null,
)

@Serializable
internal data class TabRenderer(
    val title: String? = null,
    val content: TabContent? = null,
    val endpoint: NavigationEndpoint? = null,
)

@Serializable
internal data class Tab(
    val tabRenderer: TabRenderer? = null,
)

@Serializable
internal data class SingleColumnBrowseResultsRenderer(
    val tabs: List<Tab> = emptyList(),
)

@Serializable
internal data class SecondaryContents(
    val sectionListRenderer: SectionListRenderer? = null,
)

@Serializable
internal data class TwoColumnBrowseResultsRenderer(
    val tabs: List<Tab> = emptyList(),
    val secondaryContents: SecondaryContents? = null,
)

@Serializable
internal data class TabbedSearchResultsRenderer(
    val tabs: List<Tab> = emptyList(),
)

/**
 * The top-level `contents` object.
 *
 * Which of these is populated depends entirely on the page: home and artist pages use the
 * single-column envelope, album and playlist pages the two-column one, search its own tabbed
 * wrapper, and the "related" page hangs a section list directly off `contents`.
 */
@Serializable
internal data class ResponseContents(
    val singleColumnBrowseResultsRenderer: SingleColumnBrowseResultsRenderer? = null,
    val twoColumnBrowseResultsRenderer: TwoColumnBrowseResultsRenderer? = null,
    val tabbedSearchResultsRenderer: TabbedSearchResultsRenderer? = null,
    val sectionListRenderer: SectionListRenderer? = null,
)

@Serializable
internal data class ResponseContext(
    val visitorData: String? = null,
)

/** Implemented by every response type so the session identity can be picked up from any call. */
internal interface SessionCarrier {
    val responseContext: ResponseContext?
}

/** The extra shelves a continuation request returns, appended to a page already on screen. */
@Serializable
internal data class ContinuationContents(
    val sectionListContinuation: SectionListRenderer? = null,
)

@Serializable
internal data class InnerTubeResponse(
    val contents: ResponseContents? = null,
    val header: PageHeader? = null,
    val continuationContents: ContinuationContents? = null,
    val onResponseReceivedActions: List<ResponseReceivedAction> = emptyList(),
    override val responseContext: ResponseContext? = null,
) : SessionCarrier

@Serializable
internal data class PlaylistPanelVideoRenderer(
    val title: Runs? = null,
    val videoId: String? = null,
)

@Serializable
internal data class WatchNextTab(
    val tabRenderer: TabRenderer? = null,
)

@Serializable
internal data class WatchNextTabbedResultsRenderer(
    val tabs: List<WatchNextTab> = emptyList(),
)

@Serializable
internal data class TabbedRenderer(
    val watchNextTabbedResultsRenderer: WatchNextTabbedResultsRenderer? = null,
)

@Serializable
internal data class SingleColumnMusicWatchNextResultsRenderer(
    val tabbedRenderer: TabbedRenderer? = null,
)

@Serializable
internal data class NextContents(
    val singleColumnMusicWatchNextResultsRenderer:
    SingleColumnMusicWatchNextResultsRenderer? = null,
)

@Serializable
internal data class NextResponse(
    val contents: NextContents? = null,
    override val responseContext: ResponseContext? = null,
) : SessionCarrier

/** The player endpoint's answer; only the video's details are read (streams come from NewPipe). */
@Serializable
internal data class PlayerResponse(
    val videoDetails: VideoDetails? = null,
    override val responseContext: ResponseContext? = null,
) : SessionCarrier

@Serializable
internal data class VideoDetails(
    val videoId: String? = null,
    val title: String? = null,
    /** The uploading channel's name, which for most music is the artist. */
    val author: String? = null,
    val lengthSeconds: String? = null,
    val thumbnail: Thumbnails? = null,
)
