package io.aequicor.visualization.editor.ui

import android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW
import android.view.PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW
import android.view.PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW
import android.view.PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW
import androidx.compose.ui.input.pointer.PointerIcon

actual fun horizontalResizeCursor(): PointerIcon = PointerIcon(TYPE_HORIZONTAL_DOUBLE_ARROW)

actual fun verticalResizeCursor(): PointerIcon = PointerIcon(TYPE_VERTICAL_DOUBLE_ARROW)

actual fun diagonalResizeCursor(topLeftToBottomRight: Boolean): PointerIcon =
    PointerIcon(if (topLeftToBottomRight) TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW else TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW)
