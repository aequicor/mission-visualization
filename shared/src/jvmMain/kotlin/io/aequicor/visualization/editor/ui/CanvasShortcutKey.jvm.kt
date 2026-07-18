package io.aequicor.visualization.editor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.key.key

/**
 * ЙЦУКЕН → QWERTY for the letters the canvas actually binds ([handleCanvasKey]): the
 * Cyrillic character a Russian layout types on that physical key. Extend together with
 * the shortcut map.
 */
private val cyrillicShortcutKeys: Map<Char, Key> = mapOf(
    'я' to Key.Z,
    'с' to Key.C,
    'м' to Key.V,
    'в' to Key.D,
    'ф' to Key.A,
    'п' to Key.G,
    'щ' to Key.O,
    'у' to Key.E,
)

internal actual fun canvasShortcutKey(event: KeyEvent): Key {
    val typed = event.utf16CodePoint.takeIf { it > 0 }?.toChar()?.lowercaseChar()
    return typed?.let { cyrillicShortcutKeys[it] } ?: event.key
}
