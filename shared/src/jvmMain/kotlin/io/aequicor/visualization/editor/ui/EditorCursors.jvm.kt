package io.aequicor.visualization.editor.ui

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

actual fun horizontalResizeCursor(): PointerIcon = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))

actual fun verticalResizeCursor(): PointerIcon = PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR))

actual fun diagonalResizeCursor(topLeftToBottomRight: Boolean): PointerIcon =
    PointerIcon(Cursor(if (topLeftToBottomRight) Cursor.NW_RESIZE_CURSOR else Cursor.NE_RESIZE_CURSOR))
