package com.example.musicsmd.desktop

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.Collections
import javax.swing.SwingUtilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test

/**
 * Real AWT key events through a real Compose window: a text field doesn't consume the key-down of
 * Space or arrows, so window-level shortcuts must check [isTextEntry] or typing pauses the music.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
class KeyboardShortcutsTest {
    @Test
    fun `keys typed into a text field are recognised as text entry, others are shortcuts`() {
        assumeFalse("Needs a display", GraphicsEnvironment.isHeadless())
        val unconsumed = Collections.synchronizedList(mutableListOf<Pair<Int, Boolean>>())
        val text = mutableStateOf("")
        val clearFocus = mutableStateOf(false)
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow()
            window.setSize(420, 160)
            window.setContent(onKeyEvent = { e ->
                if (e.type == KeyEventType.KeyDown) {
                    val awt = checkNotNull(e.awtEventOrNull)
                    unconsumed += awt.keyCode to e.isTextEntry
                }
                false
            }) {
                val focus = remember { FocusRequester() }
                val focusManager = LocalFocusManager.current
                TextField(text.value, { text.value = it }, Modifier.fillMaxWidth().focusRequester(focus))
                LaunchedEffect(Unit) { focus.requestFocus() }
                LaunchedEffect(clearFocus.value) { if (clearFocus.value) focusManager.clearFocus() }
            }
            window.isVisible = true
        }
        try {
            Thread.sleep(1_500)
            fun keyTargets(c: Component): List<Component> =
                (if (c.keyListeners.isNotEmpty()) listOf(c) else emptyList()) +
                    ((c as? Container)?.components?.flatMap { keyTargets(it) } ?: emptyList())
            val target = keyTargets(window).first()
            fun press(code: Int, ch: Char = KeyEvent.CHAR_UNDEFINED, modifiers: Int = 0) {
                SwingUtilities.invokeAndWait {
                    val now = System.currentTimeMillis()
                    target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_PRESSED, now, modifiers, code, ch))
                    if (ch != KeyEvent.CHAR_UNDEFINED) {
                        target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_TYPED, now, modifiers, KeyEvent.VK_UNDEFINED, ch))
                    }
                    target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_RELEASED, now, modifiers, code, ch))
                }
                Thread.sleep(120)
            }

            press(KeyEvent.VK_A, 'a'); press(KeyEvent.VK_SPACE, ' '); press(KeyEvent.VK_B, 'b')
            press(KeyEvent.VK_LEFT, modifiers = InputEvent.SHIFT_DOWN_MASK)
            press(KeyEvent.VK_LEFT, modifiers = InputEvent.CTRL_DOWN_MASK)
            Thread.sleep(300)
            assertEquals("a b", text.value)
            val whileTyping = unconsumed.toList()
            assertTrue("Space must reach the window while typing: $whileTyping", whileTyping.any { it.first == KeyEvent.VK_SPACE })
            assertTrue("Everything reaching the window while typing is text entry: $whileTyping", whileTyping.all { it.second })

            unconsumed.clear()
            SwingUtilities.invokeAndWait { clearFocus.value = true }
            Thread.sleep(500)
            press(KeyEvent.VK_SPACE, ' ')
            press(KeyEvent.VK_RIGHT, modifiers = InputEvent.CTRL_DOWN_MASK)
            Thread.sleep(300)
            assertEquals("a b", text.value)
            val outsideText = unconsumed.toList()
            assertEquals(listOf(KeyEvent.VK_SPACE to false, KeyEvent.VK_RIGHT to false), outsideText)
        } finally {
            SwingUtilities.invokeAndWait { window.dispose() }
        }
    }
}
