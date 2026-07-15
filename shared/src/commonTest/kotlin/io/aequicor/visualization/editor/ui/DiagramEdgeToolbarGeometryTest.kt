package io.aequicor.visualization.editor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiagramEdgeToolbarGeometryTest {

    @Test
    fun anchorUsesArcLengthMidpointOfBentRoute() {
        val anchor = diagramEdgeToolbarAnchor(
            listOf(
                DiagramPoint(0.0, 0.0),
                DiagramPoint(100.0, 0.0),
                DiagramPoint(100.0, 300.0),
            ),
        )

        assertEquals(DiagramPoint(100.0, 100.0), anchor)
        assertNull(diagramEdgeToolbarAnchor(listOf(DiagramPoint(1.0, 1.0))))
    }

    @Test
    fun offsetPlacesToolbarLeftOfRouteAndVerticallyCentered() {
        val offset = diagramEdgeToolbarOffset(
            anchor = Offset(300f, 200f),
            viewportSize = Size(800f, 600f),
            toolbarSize = Size(106f, 36f),
        )

        assertEquals(Offset(182f, 182f), offset)
    }

    @Test
    fun offsetClampsToolbarInsideEveryViewportEdge() {
        val topLeft = diagramEdgeToolbarOffset(
            anchor = Offset(20f, 10f),
            viewportSize = Size(800f, 600f),
            toolbarSize = Size(106f, 36f),
        )
        val bottomRight = diagramEdgeToolbarOffset(
            anchor = Offset(900f, 590f),
            viewportSize = Size(800f, 600f),
            toolbarSize = Size(106f, 36f),
        )
        val narrowViewport = diagramEdgeToolbarOffset(
            anchor = Offset(40f, 40f),
            viewportSize = Size(80f, 50f),
            toolbarSize = Size(106f, 36f),
        )

        assertEquals(Offset(8f, 8f), topLeft)
        assertEquals(Offset(686f, 556f), bottomRight)
        assertEquals(Offset(8f, 8f), narrowViewport)
    }
}
