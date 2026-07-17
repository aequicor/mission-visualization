package io.aequicor.visualization.editor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent

/**
 * The [Key] a canvas shortcut should match for [event], layout-independent.
 *
 * Compose's [KeyEvent.key] comes from the platform key CODE, and on desktop JVMs a
 * non-latin layout (ЙЦУКЕН) reports letter keys as undefined/Cyrillic codes — so
 * `key == Key.Z` never matches and every letter shortcut (undo, duplicate, group,
 * select-all) silently dies the moment the user's layout is Russian. The JVM actual
 * maps the typed character back to the physical latin key for the letters the canvas
 * binds; other platforms report layout-independent codes already and pass through.
 */
internal expect fun canvasShortcutKey(event: KeyEvent): Key
