package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable

/** Thickness of the source-pane overlay scrollbars, in dp (desktop / web / iOS). */
internal const val SourceScrollbarThicknessDp = 12

/**
 * Overlay scrollbars for the source text pane. Desktop, web and iOS draw real draggable
 * scrollbars; Android is a no-op and relies on its built-in scroll indicators, because the
 * `HorizontalScrollbar` / `VerticalScrollbar` / `rememberScrollbarAdapter` APIs are not part of
 * the Android Compose foundation (they exist only on the skiko-backed targets).
 */
@Composable
expect fun BoxScope.SourceScrollbars(verticalScroll: ScrollState, horizontalScroll: ScrollState)
