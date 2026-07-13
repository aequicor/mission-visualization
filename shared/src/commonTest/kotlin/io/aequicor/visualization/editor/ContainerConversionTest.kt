package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.ContainerKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContainerConversionTest {
    private val source = """
        ---
        screen: conversion
        sourceLocale: en-US
        targetLocales: [en-US]
        ---

        # Conversion

        ## Frame: Panel id panel 300 by 200 position 10 10 clip

        Rectangle id second 40 by 30 position 100 20 color #22C55E
        Rectangle id first 40 by 30 position 20 20 color #2563EB
    """.trimIndent()

    @Test
    fun convertsBothWaysWithSourceWriteBackAndSingleStepHistory() {
        val initial = createDesignEditorState(
            compileMissionDocuments(listOf(MissionDocumentSource("conversion.layout.md", source))),
        )
        assertTrue(initial.diagnostics.none { it.severity == DesignSeverity.Error })

        val toAuto = reduceDesignEditor(
            initial,
            DesignEditorIntent.ConvertContainer(
                nodeId = "panel",
                target = ContainerKind.AutoLayout,
                targetMode = LayoutMode.Horizontal,
                width = 300.0,
                height = 200.0,
                children = listOf(
                    DesignEditorIntent.BakedChildGeometry("second", 100.0, 20.0, 40.0, 30.0),
                    DesignEditorIntent.BakedChildGeometry("first", 20.0, 20.0, 40.0, 30.0),
                ),
            ),
        )
        val auto = assertNotNull(toAuto.document?.nodeById("panel"))
        assertEquals(ContainerKind.AutoLayout, auto.containerKind, toAuto.diagnostics.joinToString { it.message })
        assertEquals(LayoutMode.Horizontal, auto.layout.mode)
        assertEquals(listOf("first", "second"), auto.children.map { it.id })
        auto.children.forEach {
            assertEquals(null, it.position)
            assertFalse(it.layoutChild.absolute)
        }
        assertTrue("AutoLayout:" in toAuto.sources.single().content)
        assertNotEquals(initial.sources, toAuto.sources)
        assertTrue(toAuto.diagnostics.none { it.severity == DesignSeverity.Error }, toAuto.diagnostics.joinToString { it.message })

        val undone = reduceDesignEditor(toAuto, DesignEditorIntent.Undo)
        assertEquals(initial.sources, undone.sources)
        assertEquals(ContainerKind.Frame, undone.document?.nodeById("panel")?.containerKind)
        val redone = reduceDesignEditor(undone, DesignEditorIntent.Redo)
        assertEquals(toAuto.sources, redone.sources)
        assertEquals(ContainerKind.AutoLayout, redone.document?.nodeById("panel")?.containerKind)

        val toFrame = reduceDesignEditor(
            redone,
            DesignEditorIntent.ConvertContainer(
                nodeId = "panel",
                target = ContainerKind.Frame,
                width = 300.0,
                height = 200.0,
                children = listOf(
                    DesignEditorIntent.BakedChildGeometry("first", 12.0, 16.0, 40.0, 30.0),
                    DesignEditorIntent.BakedChildGeometry("second", 64.0, 16.0, 40.0, 30.0),
                ),
            ),
        )
        val frame = assertNotNull(toFrame.document?.nodeById("panel"))
        assertEquals(ContainerKind.Frame, frame.containerKind)
        assertEquals(LayoutMode.None, frame.layout.mode)
        assertTrue(frame.layout.clipsContent)
        assertTrue("Frame:" in toFrame.sources.single().content)
        assertFalse("AutoLayout:" in toFrame.sources.single().content)
        frame.children.zip(listOf(12.0 to 16.0, 64.0 to 16.0)).forEach { (child, expected) ->
            assertEquals(expected.first, child.position?.x?.literalOrNull())
            assertEquals(expected.second, child.position?.y?.literalOrNull())
            assertEquals(SizingMode.Fixed, child.sizing?.horizontal)
            assertEquals(SizingMode.Fixed, child.sizing?.vertical)
            assertEquals(HorizontalConstraint.Left, child.constraints.horizontal)
            assertEquals(VerticalConstraint.Top, child.constraints.vertical)
            assertTrue(child.layoutChild.absolute)
        }
        assertTrue(toFrame.diagnostics.none { it.severity == DesignSeverity.Error }, toFrame.diagnostics.joinToString { it.message })
    }
}
