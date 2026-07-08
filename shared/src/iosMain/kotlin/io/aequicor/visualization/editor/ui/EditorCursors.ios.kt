package io.aequicor.visualization.editor.ui

import androidx.compose.ui.input.pointer.PointerIcon

actual fun horizontalResizeCursor(): PointerIcon = PointerIcon.Crosshair

actual fun verticalResizeCursor(): PointerIcon = PointerIcon.Crosshair

actual fun diagonalResizeCursor(topLeftToBottomRight: Boolean): PointerIcon = PointerIcon.Crosshair
