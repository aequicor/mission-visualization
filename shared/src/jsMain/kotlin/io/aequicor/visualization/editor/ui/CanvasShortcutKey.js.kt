package io.aequicor.visualization.editor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key

internal actual fun canvasShortcutKey(event: KeyEvent): Key = event.key
