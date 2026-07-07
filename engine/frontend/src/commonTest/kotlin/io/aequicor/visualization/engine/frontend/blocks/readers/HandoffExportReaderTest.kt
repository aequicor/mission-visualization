package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.ExportPatch
import io.aequicor.visualization.engine.frontend.blocks.HandoffPatch
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.CodeHints
import io.aequicor.visualization.engine.ir.model.DesignAnnotation
import io.aequicor.visualization.engine.ir.model.DesignHandoff
import io.aequicor.visualization.engine.ir.model.DesignMeasurement
import io.aequicor.visualization.engine.ir.model.ExportFormat
import io.aequicor.visualization.engine.ir.model.ExportSetting
import io.aequicor.visualization.engine.ir.model.MeasureAxis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandoffExportReaderTest {
    /** Spec handoff example (~line 1080-1095). */
    @Test
    fun readsSpecHandoffExample() {
        val (patch, collector) = readSingle(
            """
            handoff:
              annotations:
                - id: note-loading
                  target: missionGrid
                  text: Показывать skeleton после 250 ms ожидания.
                  audience: engineering
              measurements:
                - from: missionGrid
                  to: missionPanel
                  axis: inline
                  value: §space.6
              code:
                framework: react
                componentHint: MissionDashboard
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            HandoffPatch(
                DesignHandoff(
                    annotations = listOf(
                        DesignAnnotation(
                            id = "note-loading",
                            target = "missionGrid",
                            text = "Показывать skeleton после 250 ms ожидания.",
                            audience = "engineering",
                        ),
                    ),
                    measurements = listOf(
                        DesignMeasurement(
                            from = "missionGrid",
                            to = "missionPanel",
                            axis = MeasureAxis.Inline,
                            value = Bindable.VarRef("space.6"),
                        ),
                    ),
                    code = CodeHints(framework = "react", componentHint = "MissionDashboard"),
                ),
            ),
            patch,
        )
    }

    /** Spec export example (~line 1099-1108). */
    @Test
    fun readsSpecExportExample() {
        val (patch, collector) = readSingle(
            """
            export:
              enabled: true
              settings:
                - format: png
                  scale: 2
                  suffix: "@2x"
                - format: svg
                  suffix: ""
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            ExportPatch(
                enabled = true,
                settings = listOf(
                    ExportSetting(format = ExportFormat.Png, scale = 2.0, suffix = "@2x"),
                    ExportSetting(format = ExportFormat.Svg, scale = 1.0, suffix = ""),
                ),
            ),
            patch,
        )
    }

    @Test
    fun unknownFormatIsDroppedWithWarning() {
        val (patch, collector) = readSingle(
            """
            export:
              settings:
                - format: webp
            """,
        )
        assertEquals(ExportPatch(settings = emptyList()), patch)
        assertTrue(collector.diagnostics.any { "webp" in it.message })
    }
}
