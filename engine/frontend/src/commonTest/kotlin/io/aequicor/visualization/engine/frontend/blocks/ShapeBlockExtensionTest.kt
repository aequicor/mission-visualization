package io.aequicor.visualization.engine.frontend.blocks

import io.aequicor.visualization.engine.frontend.blocks.readers.readSingle
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.subsystems.figures.ShapeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The migrated built-in `shape:` block exercised through the extension contract. */
class ShapeBlockExtensionTest {

    @Test
    fun writeRoundTripsThroughTheReader() {
        val patch = ShapePatch(
            kind = ShapeType.Star,
            width = 120.0,
            height = 80.5,
            pointCount = 5,
            innerRadius = 0.6,
            arcStartDeg = 10.0,
            arcSweepDeg = 270.0,
        )
        val block = ShapeBlockExtension.write(patch)
        assertTrue(block.startsWith("shape:"), block)
        val (read, collector) = readSingle(block)
        assertEquals(patch, read)
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.toString())
    }

    @Test
    fun writeOmitsUnauthoredFields() {
        assertEquals(
            "shape:\n  kind: ellipse",
            ShapeBlockExtension.write(ShapePatch(kind = ShapeType.Ellipse)),
        )
    }

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
