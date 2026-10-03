package com.example.musicsmd.playback

import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AudioKeepAliveTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Collections.synchronizedList(mutableListOf<String>())
    private var failOpen = false

    private fun keepAlive(lingerMs: Long = 60_000L) = AudioKeepAlive(scope, lingerMs) { device ->
        if (failOpen) {
            log += "fail ${device ?: "default"}"
            null
        } else {
            log += "open ${device ?: "default"}"
            SilentStream { log += "close ${device ?: "default"}" }
        }
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `stays open across back-to-back track changes`() {
        val k = keepAlive()
        k.update(true, null)
        k.update(false, null) // the instant between one track ending and the next loading
        k.update(true, null)
        k.update(true, null)
        eventually { log.isNotEmpty() }
        Thread.sleep(100)
        assertEquals(listOf("open default"), log.toList())
        assertTrue(k.isOpen)
    }

    @Test
    fun `lingers after stopping then lets the device sleep, and reopens on play`() {
        val k = keepAlive(lingerMs = 150L)
        k.update(true, null)
        k.update(false, null)
        eventually { log.contains("close default") }
        assertFalse(k.isOpen)
        k.update(true, null)
        eventually { log.count { it == "open default" } == 2 }
    }

    @Test
    fun `follows the chosen output device`() {
        val k = keepAlive()
        k.update(true, null)
        k.update(true, "Headphones (Speaker)")
        k.update(false, "Headphones (Speaker)")
        k.update(true, "Headphones (Speaker)")
        eventually { log.size >= 3 }
        Thread.sleep(100)
        assertEquals(listOf("open default", "close default", "open Headphones (Speaker)"), log.toList())
    }

    @Test
    fun `a device that won't open doesn't break later requests, and close releases`() {
        failOpen = true
        val k = keepAlive()
        k.update(true, null)
        eventually { log.contains("fail default") }
        failOpen = false
        k.update(false, null)
        k.update(true, "Other")
        eventually { log.contains("open Other") }
        k.close()
        eventually { log.contains("close Other") }
        k.update(true, null)
        Thread.sleep(100)
        assertEquals(listOf("fail default", "open Other", "close Other"), log.toList())
    }

    private fun eventually(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 3_000L
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("Timed out; log=$log")
            Thread.sleep(10)
        }
    }
}
