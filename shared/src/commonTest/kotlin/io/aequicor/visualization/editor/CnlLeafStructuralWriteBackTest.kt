package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.parentNodeOf
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** End-to-end reducer coverage for leaf components authored as CNL sentence lines. */
class CnlLeafStructuralWriteBackTest {
    private val source = """
        ---
        screen: cnl-leaves
        sourceLocale: en-US
        ---

        # CNL leaves

        ## Frame: Left id left 300 by 500 position 0 0

        Rectangle id leaf 40 by 40 position 10 20 rotation -18 color #FF0000

        ## Frame: Right id right 300 by 500 position 400 0

        Rectangle id existing 40 by 40 position 10 20 color #00FF00
    """.trimIndent() + "\n"

    private fun freshState() = createDesignEditorState(
        compileMissionDocuments(listOf(MissionDocumentSource("cnl-leaves.layout.md", source))),
    )

    @Test
    fun deletesCnlLeafAndPersistsTheRemoval() {
        val before = freshState()

        val next = reduceDesignEditor(before, DesignEditorIntent.DeleteNodes(setOf("leaf")))

        assertNull(next.document?.nodeById("leaf"), next.diagnostics.joinToString { it.message })
        assertNotEquals(before.sources, next.sources)
        assertTrue("Rectangle id leaf " !in next.sources.single().content)
        assertTrue(next.diagnostics.none { it.severity == DesignSeverity.Error }, next.diagnostics.joinToString { it.message })
    }

    @Test
    fun reparentsCnlLeafAndPersistsItsDropGeometry() {
        val before = freshState()

        val next = reduceDesignEditor(
            before,
            DesignEditorIntent.ReparentNode(
                nodeId = "leaf",
                newParentId = "right",
                position = DesignPoint(24.0, 36.0),
                size = DesignSize(46.0, 44.0),
                rotation = 12.0,
            ),
        )

        val document = assertNotNull(next.document, next.diagnostics.joinToString { it.message })
        val node = assertNotNull(document.nodeById("leaf"))
        assertEquals("right", document.parentNodeOf("leaf")?.id, next.diagnostics.joinToString { it.message })
        assertEquals(24.0, node.position?.x?.literalOrNull())
        assertEquals(36.0, node.position?.y?.literalOrNull())
        assertEquals(46.0, node.size.width)
        assertEquals(44.0, node.size.height)
        assertEquals(12.0, node.rotation)
        assertNotEquals(before.sources, next.sources)
        assertTrue(next.diagnostics.none { it.severity == DesignSeverity.Error }, next.diagnostics.joinToString { it.message })

        val recompiled = assertNotNull(compileMissionDocuments(next.sources).document)
        assertEquals("right", recompiled.parentNodeOf("leaf")?.id)
        assertEquals(listOf("existing", "leaf"), recompiled.nodeById("right")?.children?.map { it.id })
        assertEquals(24.0, recompiled.nodeById("leaf")?.position?.x?.literalOrNull())
        assertEquals(36.0, recompiled.nodeById("leaf")?.position?.y?.literalOrNull())
        assertEquals(12.0, recompiled.nodeById("leaf")?.rotation)
    }

    @Test
    fun reparentsCnlLeafAfterHeadingSiblingWithoutSnappingBack() {
        val nestedSource = """
            ---
            screen: reparent-after-heading
            sourceLocale: en-US
            ---

            # Reparent after heading

            ## Frame: Workspace id workspace

            ### Frame: Panel id panel

            #### Frame: Source id source 368 by 100 position 0 500

            Button id moving «Move me» 368 by 40 position 0 0 onClick setVariable (moved) to (true)

            #### Frame: Target id target 368 by 484 position 0 0

            ##### Frame: Existing Row id existing_row 352 by 88 position 8 8

            Rectangle id existing_thumbnail 124 by 70 position 8 9 color #00FF00
        """.trimIndent() + "\n"
        val before = createDesignEditorState(
            compileMissionDocuments(listOf(MissionDocumentSource("reparent-after-heading.layout.md", nestedSource))),
        )

        val next = reduceDesignEditor(
            before,
            DesignEditorIntent.ReparentNode(
                nodeId = "moving",
                newParentId = "target",
                position = DesignPoint(0.0, 120.0),
                size = DesignSize(368.0, 40.0),
            ),
        )

        val document = assertNotNull(next.document, next.diagnostics.joinToString { it.message })
        assertEquals("target", document.parentNodeOf("moving")?.id)
        assertEquals(120.0, document.nodeById("moving")?.position?.y?.literalOrNull())
        assertTrue(next.diagnostics.none { it.severity == DesignSeverity.Error }, next.diagnostics.joinToString { it.message })
        assertTrue("##### Button:" in next.sources.single().content, next.sources.single().content)

        val recompiled = assertNotNull(compileMissionDocuments(next.sources).document)
        assertEquals("target", recompiled.parentNodeOf("moving")?.id)
        assertEquals(listOf("existing_row", "moving"), recompiled.nodeById("target")?.children?.map { it.id })
    }

    @Test
    fun reparentsHeadingCardWithCnlChildrenIntoSiblingAtMaximumDepth() {
        val nestedSource = """
            ---
            screen: nested-cards
            sourceLocale: en-US
            ---

            # Nested cards

            ## Frame: Workspace id workspace

            ### Frame: Panel id panel

            #### Frame: List id list

            ##### Frame: Row 01 id row_01 352 by 88 position 8 8

            Rectangle id row_01_thumbnail 124 by 70 position 8 9 color #FF0000

            ##### Frame: Row 02 id row_02 352 by 88 position 8 104

            Rectangle id row_02_thumbnail 124 by 70 position 8 9 color #00FF00
        """.trimIndent() + "\n"
        val before = createDesignEditorState(
            compileMissionDocuments(listOf(MissionDocumentSource("nested-cards.layout.md", nestedSource))),
        )

        val next = reduceDesignEditor(
            before,
            DesignEditorIntent.ReparentNode(
                nodeId = "row_01",
                newParentId = "row_02",
                position = DesignPoint(16.0, 24.0),
                size = DesignSize(352.0, 88.0),
            ),
        )

        val document = assertNotNull(next.document, next.diagnostics.joinToString { it.message })
        assertEquals("row_02", document.parentNodeOf("row_01")?.id, next.diagnostics.joinToString { it.message })
        assertEquals(16.0, document.nodeById("row_01")?.position?.x?.literalOrNull())
        assertNotEquals(before.sources, next.sources)
        assertTrue(next.diagnostics.none { it.severity == DesignSeverity.Error }, next.diagnostics.joinToString { it.message })

        val recompiled = assertNotNull(compileMissionDocuments(next.sources).document)
        assertEquals("row_02", recompiled.parentNodeOf("row_01")?.id)
        assertEquals(listOf("row_02_thumbnail", "row_01"), recompiled.nodeById("row_02")?.children?.map { it.id })
        assertEquals(listOf("row_01_thumbnail"), recompiled.nodeById("row_01")?.children?.map { it.id })
    }
}
