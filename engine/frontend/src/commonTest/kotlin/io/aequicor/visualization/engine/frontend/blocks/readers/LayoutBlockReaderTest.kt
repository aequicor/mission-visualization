package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.LayoutPatch
import io.aequicor.visualization.engine.frontend.blocks.NodePositionMode
import io.aequicor.visualization.engine.frontend.blocks.SizingPatch
import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.BaselineAlign
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.GridPlacement
import io.aequicor.visualization.engine.ir.model.GridTrack
import io.aequicor.visualization.engine.ir.model.GuideLine
import io.aequicor.visualization.engine.ir.model.GuideOrientation
import io.aequicor.visualization.engine.ir.model.JustifyContent
import io.aequicor.visualization.engine.ir.model.LayoutGridAlignment
import io.aequicor.visualization.engine.ir.model.LayoutGridDefinition
import io.aequicor.visualization.engine.ir.model.LayoutGridType
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.OverflowMode
import io.aequicor.visualization.engine.ir.model.ScrollOverflow
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LayoutBlockReaderTest {
    /** Spec "Layout Schema" example (~line 444-478). */
    @Test
    fun readsSpecAutoLayoutExample() {
        val (patch, collector) = readSingle(
            """
            layout:
              mode: column
              padding:
                top: §space.5
                right: §space.6
                bottom: §space.5
                left: §space.6
              gap:
                row: §space.4
                column: §space.2
              align:
                inline: stretch
                block: start
                baseline: first
              distribution: packed
              wrap: false
              sizing:
                width:
                  type: fill
                  min: 320
                  max: 520
                height:
                  type: hug
              clipContent: true
              overflow:
                x: hidden
                y: auto
              scroll:
                direction: vertical
                fixedChildren:
                  - missionPanelHeader
            """,
        )
        assertEquals(
            LayoutPatch(
                mode = LayoutMode.Vertical,
                paddingBlockStart = Bindable.VarRef("space.5"),
                paddingInlineEnd = Bindable.VarRef("space.6"),
                paddingBlockEnd = Bindable.VarRef("space.5"),
                paddingInlineStart = Bindable.VarRef("space.6"),
                rowGap = Bindable.VarRef("space.4"),
                columnGap = Bindable.VarRef("space.2"),
                alignInline = AlignItems.Stretch,
                alignBlock = AlignItems.Start,
                baseline = BaselineAlign.First,
                distribution = JustifyContent.Start,
                wrap = false,
                sizingWidth = SizingPatch(mode = SizingMode.Fill, min = 320.0, max = 520.0),
                sizingHeight = SizingPatch(mode = SizingMode.Hug),
                clipContent = true,
                overflowX = OverflowMode.Hidden,
                overflowY = OverflowMode.Auto,
                scrollDirection = ScrollOverflow.Vertical,
                scrollFixedChildren = listOf("missionPanelHeader"),
            ),
            patch,
        )
        // Physical left/right are accepted with import-compat hints.
        val hints = collector.diagnostics.filter { "normalized to logical" in it.message }
        assertEquals(2, hints.size, collector.diagnostics.joinToString { it.message })
        assertEquals(collector.diagnostics.size, hints.size)
    }

    /** Spec grid layout example (~line 482-498). */
    @Test
    fun readsSpecGridExample() {
        val (patch, collector) = readSingle(
            """
            layout:
              mode: grid
              columns:
                count: 12
                track: 1fr
                gap: §space.4
              rows:
                auto: true
                min: 96
                gap: §space.4
              placement:
                column: 1
                columnSpan: 8
                row: 1
                rowSpan: 2
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        val layout = assertIs<LayoutPatch>(patch)
        assertEquals(LayoutMode.Grid, layout.mode)
        assertEquals(List(12) { GridTrack.Flex(1.0) }, layout.gridColumns)
        assertEquals(Bindable.VarRef("space.4"), layout.columnGap)
        assertEquals(Bindable.VarRef("space.4"), layout.rowGap)
        assertEquals(null, layout.gridRows)
        assertEquals(GridTrack.Flex(1.0), layout.implicitRows)
        assertEquals(96.0, layout.implicitRowMin)
        assertEquals(
            GridPlacement(column = 1, row = 1, columnSpan = 8, rowSpan = 2),
            layout.placement,
        )
    }

    /** Spec absolute / ignored auto layout child example (~line 502-519). */
    @Test
    fun readsSpecAbsoluteChildExample() {
        val (patch, collector) = readSingle(
            """
            layout:
              ignoreAutoLayout: true
              position:
                mode: absolute
                inlineEnd: 4
                blockStart: 4
              sizing:
                width:
                  type: fixed
                  value: 8
                height:
                  type: fixed
                  value: 8
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            LayoutPatch(
                ignoreAutoLayout = true,
                positionMode = NodePositionMode.Absolute,
                anchorInlineEnd = 4.0.bindable(),
                anchorBlockStart = 4.0.bindable(),
                sizingWidth = SizingPatch(mode = SizingMode.Fixed, value = 8.0),
                sizingHeight = SizingPatch(mode = SizingMode.Fixed, value = 8.0),
            ),
            patch,
        )
    }

    /** Spec guides + grids example (~line 571-582). */
    @Test
    fun readsSpecGuidesAndGridsExample() {
        val (patch, collector) = readSingle(
            """
            layout:
              guides:
                - orientation: vertical
                  position: 72
              grids:
                - type: columns
                  count: 12
                  gutter: 24
                  margin: 72
                  alignment: stretch
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            LayoutPatch(
                guides = listOf(GuideLine(GuideOrientation.Vertical, 72.0)),
                grids = listOf(
                    LayoutGridDefinition(
                        type = LayoutGridType.Columns,
                        count = 12,
                        gutter = 24.0,
                        margin = 72.0,
                        alignment = LayoutGridAlignment.Stretch,
                    ),
                ),
            ),
            patch,
        )
    }

    /** Spec variable binding example (~line 739-742). */
    @Test
    fun readsGapVariableBinding() {
        val (patch, collector) = readSingle(
            """
            layout:
              gap:
                variable: space.4
            """,
        )
        assertTrue(collector.diagnostics.isEmpty())
        assertEquals(LayoutPatch(gap = DesignGap.Fixed(Bindable.VarRef("space.4"))), patch)
    }

    @Test
    fun gapAutoAndScalarShorthands() {
        val (auto, _) = readSingle("layout:\n  gap: auto")
        assertEquals(LayoutPatch(gap = DesignGap.Auto), auto)
        val (fixed, _) = readSingle("layout:\n  gap: 12")
        assertEquals(LayoutPatch(gap = DesignGap.Fixed(12.0.bindable())), fixed)
        val (token, _) = readSingle("layout:\n  gap: §space.3")
        assertEquals(LayoutPatch(gap = DesignGap.Fixed(Bindable.VarRef("space.3"))), token)
    }

    @Test
    fun sizingScalarShorthand() {
        val (patch, _) = readSingle(
            """
            layout:
              sizing:
                width: fill
                height: hug
            """,
        )
        assertEquals(
            LayoutPatch(
                sizingWidth = SizingPatch(mode = SizingMode.Fill),
                sizingHeight = SizingPatch(mode = SizingMode.Hug),
            ),
            patch,
        )
    }

    @Test
    fun unknownModeWarnsAndStaysNull() {
        val (patch, collector) = readSingle("layout:\n  mode: diagonal")
        assertEquals(LayoutPatch(), patch)
        assertTrue(
            collector.diagnostics.any {
                it.severity == DesignSeverity.Warning && "diagonal" in it.message
            },
        )
    }

    @Test
    fun logicalPaddingShorthandsInlineAndBlock() {
        val (patch, collector) = readSingle(
            """
            layout:
              padding:
                inline: §space.4
                block: 8
            """,
        )
        assertTrue(collector.diagnostics.isEmpty())
        assertEquals(
            LayoutPatch(
                paddingBlockStart = 8.0.bindable(),
                paddingInlineEnd = Bindable.VarRef("space.4"),
                paddingBlockEnd = 8.0.bindable(),
                paddingInlineStart = Bindable.VarRef("space.4"),
            ),
            patch,
        )
    }
}
