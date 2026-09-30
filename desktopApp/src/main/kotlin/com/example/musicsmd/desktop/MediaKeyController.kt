package com.example.musicsmd.desktop

import com.example.musicsmd.player.AppViewModel
import com.example.musicsmd.settings.SettingsStore
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeHookException
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Registers hardware media keys through JNativeHook when the setting is enabled. */
class MediaKeyController(
    private val settingsStore: SettingsStore,
    private val viewModel: AppViewModel,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var registered = false
    private var loggedFailure = false

    private val listener = object : NativeKeyListener {
        override fun nativeKeyPressed(event: NativeKeyEvent) {
            when (event.keyCode) {
                NativeKeyEvent.VC_MEDIA_PLAY -> viewModel.togglePlayPause()
                NativeKeyEvent.VC_MEDIA_NEXT -> viewModel.playNextInQueue()
                NativeKeyEvent.VC_MEDIA_PREVIOUS -> viewModel.playPreviousInQueue()
                NativeKeyEvent.VC_MEDIA_STOP -> viewModel.pause()
            }
        }
    }

    fun start() {
        silenceLogger()
        scope.launch {
            settingsStore.settings.map { it.mediaKeys }.distinctUntilChanged().collect { enabled ->
                if (enabled) register() else unregister()
            }
        }
    }

    fun dispose() {
        unregister()
        scope.cancel()
    }

    private fun register() {
        if (registered) return
        runCatching {
            if (!GlobalScreen.isNativeHookRegistered()) GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeKeyListener(listener)
            registered = true
        }.onFailure { error ->
            if (!loggedFailure) {
                loggedFailure = true
                System.err.println("Media keys unavailable: ${error.message}")
            }
        }
    }

    private fun unregister() {
        if (!registered) return
        runCatching { GlobalScreen.removeNativeKeyListener(listener) }
        runCatching { if (GlobalScreen.isNativeHookRegistered()) GlobalScreen.unregisterNativeHook() }
        registered = false
    }

    private fun silenceLogger() {
        val logger = Logger.getLogger(GlobalScreen::class.java.packageName)
        logger.level = Level.OFF
        logger.useParentHandlers = false
    }
}
