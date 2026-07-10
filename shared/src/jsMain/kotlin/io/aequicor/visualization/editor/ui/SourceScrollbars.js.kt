package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun BoxScope.SourceScrollbars(verticalScroll: ScrollState, horizontalScroll: ScrollState) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(verticalScroll),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(bottom = SourceScrollbarThicknessDp.dp),
    )
    HorizontalScrollbar(
        adapter = rememberScrollbarAdapter(horizontalScroll),
        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 46.dp, end = SourceScrollbarThicknessDp.dp),
    )
}
