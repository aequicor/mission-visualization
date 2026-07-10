package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable

/** Android relies on its built-in scroll indicators; no overlay scrollbars are drawn. */
@Composable
actual fun BoxScope.SourceScrollbars(verticalScroll: ScrollState, horizontalScroll: ScrollState) = Unit
