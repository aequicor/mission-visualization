package io.aequicor.visualization.editor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Canvas letter shortcuts must survive a Russian layout: the JVM reports Cyrillic
 * letter keys with undefined/Cyrillic key codes, so `event.key` never equals `Key.Z`
 * and undo/duplicate/group/select-all silently die. [canvasShortcutKey] maps the typed
 * character back to the physical latin key.
 */
class CanvasShortcutKeyTest {

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private fun event(key: Key, typed: Char): KeyEvent =
        KeyEvent(key = key, type = KeyEventType.KeyDown, codePoint = typed.code)

    @Test
    fun cyrillicLettersMapToTheirPhysicalLatinKeys() {
        assertEquals(Key.Z, canvasShortcutKey(event(Key.Unknown, 'я')))
        assertEquals(Key.Z, canvasShortcutKey(event(Key.Unknown, 'Я')))
        assertEquals(Key.D, canvasShortcutKey(event(Key.Unknown, 'в')))
        assertEquals(Key.A, canvasShortcutKey(event(Key.Unknown, 'ф')))
        assertEquals(Key.G, canvasShortcutKey(event(Key.Unknown, 'п')))
        assertEquals(Key.O, canvasShortcutKey(event(Key.Unknown, 'щ')))
        assertEquals(Key.E, canvasShortcutKey(event(Key.Unknown, 'у')))
    }

    @Test
    fun latinAndNonLetterKeysPassThrough() {
        assertEquals(Key.Z, canvasShortcutKey(event(Key.Z, 'z')))
        assertEquals(Key.Backspace, canvasShortcutKey(event(Key.Backspace, Char(0))))
        // A Cyrillic letter with no shortcut on its physical key stays whatever the
        // platform reported — no accidental remapping.
        assertEquals(Key.Unknown, canvasShortcutKey(event(Key.Unknown, 'ы')))
    }
}
