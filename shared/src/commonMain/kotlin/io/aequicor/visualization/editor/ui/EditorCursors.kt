package io.aequicor.visualization.editor.ui

import androidx.compose.ui.input.pointer.PointerIcon

/**
 * Platform resize cursors for panel splitters and canvas resize handles. Compose
 * Multiplatform's common `PointerIcon` set has no resize variant, so desktop supplies
 * real AWT resize cursors while other targets fall back to a distinct hover icon.
 */
expect fun horizontalResizeCursor(): PointerIcon

expect fun verticalResizeCursor(): PointerIcon

/** Diagonal (corner) resize cursors; direction is the drag axis of the corner. */
expect fun diagonalResizeCursor(topLeftToBottomRight: Boolean): PointerIcon
