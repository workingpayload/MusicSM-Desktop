package com.example.musicsmd

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.example.innertube.InnerTube
import com.example.motionart.MotionArtSource
import com.example.musicsmd.motionart.DesktopMotionArtRepository
import com.example.musicsm.data.applemusic.AppleMusicPublicClient
import com.example.musicsm.data.repository.MusicRepositoryImpl
import com.example.musicsm.data.repository.PlaylistImportRepositoryImpl
import com.example.musicsm.data.repository.AppleMusicLyricsProvider
import com.example.musicsm.data.repository.BiniLyricsProvider
import com.example.musicsm.data.repository.KuGouLyricsProvider
import com.example.musicsm.data.repository.LrcLibProvider
import com.example.musicsm.data.repository.LyricsPlusProvider
import com.example.musicsm.data.repository.LyricsRepositoryImpl
import com.example.musicsm.data.repository.SimpMusicLyricsProvider
import com.example.musicsm.data.repository.UnisonLyricsProvider
import com.example.musicsm.data.repository.YouTubeMusicLyricsProvider
import com.example.musicsm.data.source.youtube.NewPipeDownloaderImpl
import com.example.musicsm.data.source.youtube.NewPipeMusicSource
import com.example.musicsm.data.source.youtube.YouTubeMusicSource
import com.example.musicsm.data.source.youtube.signin.SignedInStreamResolver
import com.example.musicsm.data.source.youtube.signin.YouTubeAccountLibrary
import com.example.musicsmd.youtube.BrowserPlayerScript
import com.example.musicsmd.youtube.YouTubeAccount
import com.example.musicsm.data.spotify.SpotifyPublicClient
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.LyricsRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.domain.repository.PlaylistImportRepository
import com.example.musicsm.domain.source.MusicSource
import com.example.musicsmd.downloads.DownloadManager
import com.example.musicsmd.library.FileLibraryRepository
import com.example.musicsmd.local.LocalMusicManager
import com.example.musicsmd.lyrics.DesktopLyricsPreferences
import com.example.musicsmd.playback.CompositeOfflineSource
import com.example.musicsmd.playback.OfflineSource
import com.example.musicsmd.playback.PlaybackListener
import com.example.musicsmd.playback.PlayerController
import com.example.musicsmd.search.SearchHistoryStore
import com.example.musicsmd.settings.SettingsStore
import com.example.musicsmd.stats.FileStatsRepository
import com.example.musicsmd.stats.SkipTracker
import com.example.musicsmd.update.UpdateChecker
import com.example.musicsmd.update.UpdateNotifier
import com.example.musicsm.domain.repository.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe

/**
 * Manual DI root — the desktop app has no Hilt/Dagger, so this plays the role of the mobile
 * app's `DataModule` + `NetworkModule`, wired once at startup in [main].
 */
object AppGraph {
    val okHttpClient: OkHttpClient by lazy { OkHttpClient.Builder().build() }
    val innerTube: InnerTube by lazy { InnerTube() }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val musicSource: MusicSource get() = youTubeMusicSource

    /** The same source, for the signed-in account's home, history and playlists. */
    val youTubeLibrary: YouTubeAccountLibrary get() = youTubeMusicSource

    private val youTubeMusicSource: YouTubeMusicSource by lazy {
        YouTubeMusicSource(
            innerTube = innerTube,
            newPipe = NewPipeMusicSource(),
            signedInStreams = signedInStreams,
            accountInnerTube = InnerTube(auth = { youTubeAccount.cookies() }),
            accountEnabled = ::youTubeAccountDataEnabled,
        )
    }

    /** Signed in, and the user lets the app use the account's home, history and playlists. */
    fun youTubeAccountDataEnabled(): Boolean =
        settingsStore.settings.value.useYouTubeAccountData && youTubeAccount.cookies() != null

    /** The YouTube sign-in used when YouTube won't play anonymously on this network. */
    val youTubeAccount: YouTubeAccount by lazy { YouTubeAccount(client = okHttpClient) }

    private val youTubePlayerScript = lazy { BrowserPlayerScript(okHttpClient) }

    private val signedInStreams: SignedInStreamResolver by lazy {
        SignedInStreamResolver(client = okHttpClient, session = youTubeAccount, script = youTubePlayerScript.value)
    }

    /** Closes the hidden browser and any sign-in window. */
    fun closeYouTube() {
        if (youTubePlayerScript.isInitialized()) youTubePlayerScript.value.close()
        youTubeAccount.cancelSignIn()
    }

    val libraryRepository: LibraryRepository by lazy { FileLibraryRepository(scope = appScope) }

    val settingsStore: SettingsStore by lazy { SettingsStore() }

    private val fileStatsRepository: FileStatsRepository by lazy { FileStatsRepository(scope = appScope) }
    /** Clearing listening history also forgets skips, which are listening history too. */
    val statsRepository: StatsRepository by lazy {
        object : StatsRepository by fileStatsRepository {
            override suspend fun clear() {
                fileStatsRepository.clear()
                skipTracker.clear()
            }
        }
    }
    val searchHistoryStore: SearchHistoryStore by lazy { SearchHistoryStore() }

    val musicRepository: MusicRepository by lazy { MusicRepositoryImpl(musicSource) }

    private val motionArtSource = lazy { MotionArtSource() }
    val motionArtRepository: DesktopMotionArtRepository by lazy {
        DesktopMotionArtRepository(lookup = { song, provider ->
            motionArtSource.value.lookup(song.artist, song.title, song.album, provider)
        })
    }

    fun closeMotionArt() {
        if (motionArtSource.isInitialized()) motionArtSource.value.close()
    }

    val playlistImportRepository: PlaylistImportRepository by lazy {
        PlaylistImportRepositoryImpl(
            spotify = SpotifyPublicClient(okHttpClient),
            appleMusic = AppleMusicPublicClient(okHttpClient),
            musicRepository = musicRepository,
            libraryRepository = libraryRepository,
        )
    }

    val downloadManager: DownloadManager by lazy {
        DownloadManager(musicSource = musicSource, client = okHttpClient, settingsStore = settingsStore, scope = appScope)
    }

    val localMusicManager: LocalMusicManager by lazy { LocalMusicManager(settingsStore = settingsStore, scope = appScope) }

    val lyricsRepository: LyricsRepository by lazy {
        LyricsRepositoryImpl(
            preferences = DesktopLyricsPreferences(settingsStore),
            appleMusic = AppleMusicLyricsProvider(okHttpClient),
            biniLyrics = BiniLyricsProvider(okHttpClient),
            lyricsPlus = LyricsPlusProvider(okHttpClient),
            simpMusic = SimpMusicLyricsProvider(okHttpClient),
            lrcLib = LrcLibProvider(okHttpClient),
            kuGou = KuGouLyricsProvider(okHttpClient),
            unison = UnisonLyricsProvider(okHttpClient),
            youTubeMusic = YouTubeMusicLyricsProvider(innerTube),
        )
    }

    /** Downloads / local files that play without the network; features append their sources. */
    val offlineSource: OfflineSource by lazy { CompositeOfflineSource(listOf(downloadManager, localMusicManager)) }

    /** Observers of track changes (e.g. the listening-stats log). */
    val playbackListeners: List<PlaybackListener> by lazy { listOf(fileStatsRepository, skipTracker) }

    /** Songs moved on from early: a negative signal for Home's recommendations. */
    val skipTracker: SkipTracker by lazy { SkipTracker(scope = appScope) }

    val playerController: PlayerController by lazy { PlayerController() }

    /** Announces new MusicSM Desktop releases (mobile's update prompt, without the self-install). */
    val updateNotifier: UpdateNotifier by lazy {
        val checker = UpdateChecker(okHttpClient)
        UpdateNotifier(newerRelease = checker::newerRelease, settingsStore = settingsStore, scope = appScope)
    }

    /** Must run once before any [musicSource] call — mirrors `MusicSmApp.onCreate`. */
    fun init() {
        NewPipe.init(NewPipeDownloaderImpl.create(okHttpClient))
        SingletonImageLoader.setSafe { context: PlatformContext ->
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory()) }
                .build()
        }
    }
}
