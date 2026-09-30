package com.example.musicsmd

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.example.innertube.InnerTube
import com.example.musicsm.data.source.youtube.NewPipeDownloaderImpl
import com.example.musicsm.data.source.youtube.NewPipeMusicSource
import com.example.musicsm.data.source.youtube.YouTubeMusicSource
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.source.MusicSource
import com.example.musicsmd.library.FileLibraryRepository
import com.example.musicsmd.playback.PlayerController
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val musicSource: MusicSource by lazy {
        YouTubeMusicSource(innerTube = InnerTube(), newPipe = NewPipeMusicSource())
    }

    val libraryRepository: LibraryRepository by lazy { FileLibraryRepository(scope = appScope) }

    val playerController: PlayerController by lazy { PlayerController() }

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

