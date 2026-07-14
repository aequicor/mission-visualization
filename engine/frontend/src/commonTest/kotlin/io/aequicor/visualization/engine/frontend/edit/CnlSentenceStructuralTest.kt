package io.aequicor.visualization.engine.frontend.edit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Structural write-back for leaf nodes authored as one CNL sentence instead of a heading. */
class CnlSentenceStructuralTest {
    private val source = """
        ---
        screen: cnl-leaves
        sourceLocale: en-US
        ---

        # CNL leaves

        ## Frame: Left id left 300 by 500 position 0 0

        Rectangle id leaf 40 by 40 position 10 20 color #FF0000

        ## Frame: Right id right 300 by 500 position 400 0

        Rectangle id existing 40 by 40 position 10 20 color #00FF00
    """.trimIndent() + "\n"

    @Test
    fun deletesCnlSentenceNode() {
        val compiled = compileForEdit(source)

        val newSource = applySlmEdit(source, DeleteSection("leaf"), compiled).requireNewSource()

        assertTrue("Rectangle id leaf " !in newSource, newSource)
        val document = compileForEdit(newSource).requireDocument()
        assertNull(document.nodeById("leaf"))
        assertEquals(listOf("existing"), document.requireNode("right").children.map { it.id })
    }

    @Test
    fun movesHeadingAfterCnlSentenceLandsPastWholeBodyWithoutCapturingSiblings() {
        // `right` has two body sentences; moving a heading section to sit "after existing" must not
        // splice the heading between them (which would capture `existing2` as its child on
        // recompile). It lands after the whole leaf body instead, leaving `existing2` a child of
        // `right`.
        val twoBodySource = """
            ---
            screen: capture-guard
            sourceLocale: en-US
            ---

            # Capture guard

            ## Frame: Left id left 300 by 500 position 0 0

            ### Frame: Movable id movable 100 by 100 position 0 0

            Rectangle id movable_child 20 by 20 position 0 0 color #0000FF

            ## Frame: Right id right 300 by 500 position 400 0

            Rectangle id existing 40 by 40 position 10 20 color #00FF00

            Rectangle id existing2 40 by 40 position 10 80 color #00FFFF
        """.trimIndent() + "\n"
        val compiled = compileForEdit(twoBodySource)

        val newSource = applySlmEdit(
            twoBodySource,
            MoveSection("movable", "right", afterSiblingId = "existing"),
            compiled,
        ).requireNewSource()

        val document = compileForEdit(newSource).requireDocument()
        assertEquals(listOf("existing", "existing2", "movable"), document.requireNode("right").children.map { it.id })
        assertEquals(listOf("movable_child"), document.requireNode("movable").children.map { it.id })
    }

    @Test
    fun movesCnlSentenceAfterCnlSentenceSibling() {
        val compiled = compileForEdit(source)

        val newSource = applySlmEdit(
            source,
            MoveSection("leaf", "right", afterSiblingId = "existing"),
            compiled,
        ).requireNewSource()

        assertTrue(newSource.indexOf("Rectangle id leaf ") > newSource.indexOf("Rectangle id existing "), newSource)
        val document = compileForEdit(newSource).requireDocument()
        assertTrue(document.requireNode("left").children.isEmpty())
        assertEquals(listOf("existing", "leaf"), document.requireNode("right").children.map { it.id })
    }

    @Test
    fun movesHeadingContainerAfterCnlSentenceAtMaximumDepth() {
        val deepSource = """
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
        val compiled = compileForEdit(deepSource)

        val newSource = applySlmEdit(
            deepSource,
            MoveSection("row_01", "row_02", afterSiblingId = "row_02_thumbnail"),
            compiled,
        ).requireNewSource()

        assertTrue("\n###### Frame: Row 01 id row_01 " in newSource, newSource)
        val document = compileForEdit(newSource).requireDocument()
        assertEquals(listOf("row_02_thumbnail", "row_01"), document.requireNode("row_02").children.map { it.id })
        assertEquals(listOf("row_01_thumbnail"), document.requireNode("row_01").children.map { it.id })
    }
}
