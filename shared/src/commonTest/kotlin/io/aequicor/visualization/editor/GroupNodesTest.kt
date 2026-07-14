package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.NewObjectKind
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.parentNodeOf
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.orZero
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GroupNodesTest {

    @Test
    fun siblingSelectionBecomesOneTransparentPositionedGroup() {
        var state = createDesignEditorState(missionDemoDocuments())
        val root = assertNotNull(state.document?.pageById(state.selectedPageId)?.children?.firstOrNull()).id
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.CreateObject(NewObjectKind.Rectangle, root, 20.0, 30.0, 80.0, 50.0),
        )
        val first = state.selectedNodeId
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.CreateObject(NewObjectKind.Rectangle, root, 140.0, 100.0, 40.0, 60.0),
        )
        val second = state.selectedNodeId
        val beforeGroup = assertNotNull(state.document)

        state = reduceDesignEditor(state, DesignEditorIntent.GroupNodes(setOf(first, second)))

        val groupId = state.selectedNodeId
        val group = assertNotNull(state.document?.nodeById(groupId))
        assertEquals(setOf(groupId), state.selectedNodeIds)
        assertEquals("group", group.type, state.diagnostics.toString())
        assertEquals("Group", group.name)
        assertEquals(DesignNodeKind.Frame, group.kind)
        assertEquals(null, group.fills, "groups are transparent")
        assertEquals(root, state.document?.parentNodeOf(groupId)?.id)
        assertEquals(listOf(first, second), group.children.map { it.id })
        assertEquals(20.0, group.position?.x?.orZero)
        assertEquals(30.0, group.position?.y?.orZero)
        assertEquals(160.0, group.size.width)
        assertEquals(130.0, group.size.height)
        assertEquals(0.0, group.children[0].position?.x?.orZero)
        assertEquals(0.0, group.children[0].position?.y?.orZero)
        assertEquals(120.0, group.children[1].position?.x?.orZero)
        assertEquals(70.0, group.children[1].position?.y?.orZero)
        assertFalse(state.diagnostics.any { it.severity == DesignSeverity.Error }, state.diagnostics.toString())
        assertFalse(state.sources.none { groupId in it.content }, "group id is persisted to SLM")

        state = reduceDesignEditor(state, DesignEditorIntent.Undo)
        assertEquals(beforeGroup, state.document)
    }

    @Test
    fun nodesFromDifferentParentsAreNotGrouped() {
        var state = createDesignEditorState(missionDemoDocuments())
        val document = assertNotNull(state.document)
        val root = assertNotNull(document.pageById(state.selectedPageId)?.children?.firstOrNull()).id
        val nested = assertNotNull(document.nodeById(root)?.allDescendants()?.firstOrNull { it.children.isNotEmpty() })
        val nestedChild = nested.children.first().id
        val rootChild = assertNotNull(document.nodeById(root)?.children?.firstOrNull { it.id != nested.id }).id
        val before = state.document

        state = reduceDesignEditor(state, DesignEditorIntent.GroupNodes(setOf(nestedChild, rootChild)))

        assertEquals(before, state.document)
    }

    @Test
    fun cnlSentenceComponentsArePersistedInsideARealGroupFrame() {
        val source = """
            ---
            screen: grouping-cnl
            sourceLocale: en-US
            ---

            # Grouping CNL

            ## Frame: Workspace id workspace 1200 by 800 position 0 0

            ### Frame: Review id review 1100 by 700 position 20 20

            #### Frame: List id list 1040 by 640 position 20 20

            ##### Frame: Preview id preview 1000 by 600 position 20 20

            Rectangle id background 1000 by 600 position 0 0 color #112233
            Rectangle id card_left 300 by 180 position 120 140 color #224466
            Rectangle id card_right 300 by 180 position 440 140 color #335577
            Rectangle id footer 620 by 160 position 120 340 color #446688
            Text id caption position 20 560 text "Preview"
        """.trimIndent() + "\n"
        val before = createDesignEditorState(
            compileMissionDocuments(listOf(MissionDocumentSource("grouping-cnl.layout.md", source))),
        )

        val next = reduceDesignEditor(
            before,
            DesignEditorIntent.GroupNodes(setOf("card_left", "card_right", "footer")),
        )

        val group = assertNotNull(next.document?.nodeById(next.selectedNodeId), next.diagnostics.toString())
        assertEquals("group", group.type)
        assertEquals(DesignNodeKind.Frame, group.kind)
        assertEquals(listOf("card_left", "card_right", "footer"), group.children.map { it.id })
        assertEquals("preview", next.document?.parentNodeOf(group.id)?.id)
        assertEquals(group.id, next.document?.nodeById("preview")?.children?.lastOrNull()?.id)
        assertTrue(group.id in next.sources.single().content)
        assertFalse(next.diagnostics.any { it.severity == DesignSeverity.Error }, next.diagnostics.toString())
    }
}
