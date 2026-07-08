package io.aequicor.visualization.editor.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.fromKeyword

@OptIn(ExperimentalComposeUiApi::class)
actual fun horizontalResizeCursor(): PointerIcon = PointerIcon.fromKeyword("ew-resize")

@OptIn(ExperimentalComposeUiApi::class)
actual fun verticalResizeCursor(): PointerIcon = PointerIcon.fromKeyword("ns-resize")

@OptIn(ExperimentalComposeUiApi::class)
actual fun diagonalResizeCursor(topLeftToBottomRight: Boolean): PointerIcon =
    PointerIcon.fromKeyword(if (topLeftToBottomRight) "nwse-resize" else "nesw-resize")
