package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.NodePatch
import io.aequicor.visualization.engine.frontend.blocks.NodePositionMode
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NodeBlockReaderTest {
    @Test
    fun scalarShorthandBecomesTypeOnlyPatch() {
        val (patch, collector) = readSingle("node: frame")
        assertEquals(NodePatch(type = "frame"), patch)
        assertTrue(collector.diagnostics.isEmpty())
    }

    @Test
    fun screenGroupSectionStayAsTypeStrings() {
        listOf("screen", "group", "section").forEach { type ->
            val (patch, _) = readSingle("node: $type")
            assertEquals(NodePatch(type = type), patch)
        }
    }

    @Test
    fun readsFullNodeContract() {
        val (patch, collector) = readSingle(
            """
            node:
              type: frame
              id: missionPanel
              name: Mission Panel
              role: card
              visible: true
              locked: false
              order: 10
              position:
                mode: absolute
                x: 4
                y: 8
                rotation: 45
              constraints:
                horizontal: left-right
                vertical: top
            """,
        )
        assertEquals(
            NodePatch(
                type = "frame",
                id = "missionPanel",
                name = "Mission Panel",
                role = "card",
                visible = true.bindable(),
                locked = false,
                order = 10,
                positionMode = NodePositionMode.Absolute,
                x = 4.0,
                y = 8.0,
                rotation = 45.0,
                constraintsHorizontal = HorizontalConstraint.LeftRight,
                constraintsVertical = VerticalConstraint.Top,
            ),
            patch,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
    }

    @Test
    fun unknownConstraintProducesWarningAndNull() {
        val (patch, collector) = readSingle(
            """
            node:
              constraints:
                horizontal: sideways
            """,
        )
        assertEquals(NodePatch(), patch)
        assertTrue(collector.diagnostics.any { "sideways" in it.message })
    }

    @Test
    fun positionArrayShorthand() {
        val (patch, collector) = readSingle(
            """
            node:
              id: card
              position: [72, 96]
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(NodePatch(id = "card", x = 72.0, y = 96.0), patch)
    }

    @Test
    fun specAbsoluteChildNodeBlock() {
        val (patch, _) = readSingle(
            """
            node:
              type: shape
              id: notificationDot
            """,
        )
        assertEquals(NodePatch(type = "shape", id = "notificationDot"), patch)
    }
}
