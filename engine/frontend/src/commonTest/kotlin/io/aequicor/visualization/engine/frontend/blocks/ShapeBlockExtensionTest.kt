package io.aequicor.visualization.engine.frontend.blocks

import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.subsystems.figures.ShapeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The built-in `shape` patch application exercised through the extension contract. */
class ShapeBlockExtensionTest {

    @Test
    fun applyToNodeConvertsAFrameIntoAShape() {
        val node = DesignNode(id = "n1", type = "frame", kind = DesignNodeKind.Frame)
        val payload = ShapePatch(kind = ShapeType.Polygon, pointCount = 6, width = 40.0, height = 40.0)
        val applied = ShapeBlockExtension.applyToNode(node, payload)
        assertEquals("shape", applied.type)
        val kind = assertIs<DesignNodeKind.Shape>(applied.kind)
        assertEquals(ShapeType.Polygon, kind.shape)
        assertEquals(6, kind.pointCount)
        assertEquals(40.0, applied.size.width)
        assertEquals(40.0, applied.size.height)
    }
}
