package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.presentation.EditorNodeFactory
import io.aequicor.visualization.editor.presentation.NewObjectKind
import io.aequicor.visualization.engine.ir.model.ContainerKind
import io.aequicor.visualization.engine.ir.model.GridTrack
import io.aequicor.visualization.engine.ir.model.LayoutMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ContainerPresetFactoryTest {
    @Test
    fun createsFreeFrameAndAllAutoLayoutPresets() {
        val frame = EditorNodeFactory.newObject(null, NewObjectKind.Frame, 10.0, 20.0, 240.0, 160.0)
        assertEquals(ContainerKind.Frame, frame.containerKind)
        assertEquals(LayoutMode.None, frame.layout.mode)

        val vertical = EditorNodeFactory.newObject(null, NewObjectKind.AutoLayoutVertical, 10.0, 20.0, 240.0, 160.0)
        assertEquals(ContainerKind.AutoLayout, vertical.containerKind)
        assertEquals(LayoutMode.Vertical, vertical.layout.mode)

        val horizontal = EditorNodeFactory.newObject(null, NewObjectKind.AutoLayoutHorizontal, 10.0, 20.0, 240.0, 160.0)
        assertEquals(ContainerKind.AutoLayout, horizontal.containerKind)
        assertEquals(LayoutMode.Horizontal, horizontal.layout.mode)

        val grid = EditorNodeFactory.newObject(null, NewObjectKind.AutoLayoutGrid, 10.0, 20.0, 240.0, 160.0)
        assertEquals(ContainerKind.AutoLayout, grid.containerKind)
        assertEquals(LayoutMode.Grid, grid.layout.mode)
        assertEquals(2, grid.layout.columns.size)
        grid.layout.columns.forEach { assertIs<GridTrack.Flex>(it) }
        assertEquals(GridTrack.Hug, grid.layout.implicitRows)
        assertTrue(grid.layoutChild.absolute)
    }
}
