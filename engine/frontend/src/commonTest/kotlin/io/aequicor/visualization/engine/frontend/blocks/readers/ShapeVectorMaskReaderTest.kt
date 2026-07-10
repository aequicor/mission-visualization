package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.BooleanOpPatch
import io.aequicor.visualization.engine.frontend.blocks.MaskPatch
import io.aequicor.visualization.engine.frontend.blocks.ShapePatch
import io.aequicor.visualization.engine.frontend.blocks.StylePatch
import io.aequicor.visualization.engine.frontend.blocks.VectorPatch
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.subsystems.figures.BooleanOperationKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.subsystems.figures.DesignViewBox
import io.aequicor.visualization.engine.ir.model.MaskType
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.subsystems.figures.VectorPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShapeVectorMaskReaderTest {
    /** Spec shape example (~line 882-893). */
    @Test
    fun readsSpecShapeExample() {
        val (patches, collector) = readPatches(
            """
            shape:
              kind: ellipse
              width: 10
              height: 10
            style:
              fills:
                - token: color.status.success
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(ShapePatch(kind = ShapeType.Ellipse, width = 10.0, height = 10.0), patches[0])
        assertEquals(
            StylePatch(fills = listOf(DesignPaint.Solid(Bindable.VarRef("color.status.success")))),
            patches[1],
        )
    }

    @Test
    fun readsEllipseArcFields() {
        val (patches, collector) = readPatches(
            """
            shape:
              kind: ellipse
              width: 40
              height: 40
              arcStart: -90
              arcSweep: 270
              innerRadius: 0.5
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            ShapePatch(
                kind = ShapeType.Ellipse,
                width = 40.0,
                height = 40.0,
                arcStartDeg = -90.0,
                arcSweepDeg = 270.0,
                innerRadius = 0.5,
            ),
            patches[0],
        )
    }

    /** Spec vector asset-ref example (~line 897-908). */
    @Test
    fun readsSpecVectorRefExample() {
        val (patch, collector) = readSingle(
            """
            vector:
              iconRef: ds/Icon/Alert
              pathRef: assets/icons/alert.svg
              viewBox: [0, 0, 24, 24]
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            VectorPatch(
                iconRef = "ds/Icon/Alert",
                pathRef = "assets/icons/alert.svg",
                viewBox = DesignViewBox(0.0, 0.0, 24.0, 24.0),
            ),
            patch,
        )
    }

    /** Spec inline vector data example (~line 912-922). */
    @Test
    fun readsSpecInlineVectorExample() {
        val (patch, collector) = readSingle(
            """
            vector:
              paths:
                - d: "M12 2L22 20H2L12 2Z"
                  windingRule: nonzero
              boolean:
                op: union
                children:
                  - triangleBase
                  - alertCutout
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            VectorPatch(
                paths = listOf(VectorPath(windingRule = "nonzero", d = "M12 2L22 20H2L12 2Z")),
                boolean = BooleanOpPatch(
                    op = BooleanOperationKind.Union,
                    children = listOf("triangleBase", "alertCutout"),
                ),
            ),
            patch,
        )
    }

    /** Spec mask example (~line 926-932). */
    @Test
    fun readsSpecMaskExample() {
        val (patch, collector) = readSingle(
            """
            mask:
              type: alpha
              source: avatarMask
              appliesTo:
                - avatarImage
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            MaskPatch(
                type = MaskType.Alpha,
                source = "avatarMask",
                appliesTo = listOf("avatarImage"),
            ),
            patch,
        )
    }
}
