package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test

class HandoffExportChecksTest {

    @Test
    fun measurementTargetMissingIsAnError() {
        validate(
            """
            {
              "handoff": { "measurements": [ { "from": "hero", "to": "ghost", "axis": "inline" } ] },
              "pages": [ { "id": "p", "children": [ { "id": "hero", "type": "frame" } ] } ]
            }
            """.trimIndent(),
        ).assertHas("IR-HANDOFF-002", DesignSeverity.Error, messagePart = "ghost")
    }

    @Test
    fun measurementToItselfIsAnError() {
        validate(
            """
            {
              "handoff": { "measurements": [ { "from": "hero", "to": "hero", "axis": "inline" } ] },
              "pages": [ { "id": "p", "children": [ { "id": "hero", "type": "frame" } ] } ]
            }
            """.trimIndent(),
        ).assertHas("IR-HANDOFF-003", DesignSeverity.Error)
    }

    @Test
    fun annotationTargetMissingIsAnError() {
        validate(
            """
            {
              "handoff": { "annotations": [ { "id": "n1", "target": "ghost", "text": "note" } ] },
              "pages": [ { "id": "p", "children": [ { "id": "hero", "type": "frame" } ] } ]
            }
            """.trimIndent(),
        ).assertHas("IR-HANDOFF-001", DesignSeverity.Error, messagePart = "ghost")
    }

    @Test
    fun exportScaleZeroIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "hero", "type": "frame",
                "exportSettings": [ { "format": "png", "scale": 0 } ] }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-HANDOFF-004", DesignSeverity.Error, messagePart = "hero")
    }

    @Test
    fun unsafeExportSuffixWarns() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "hero", "type": "frame",
                "exportSettings": [ { "format": "png", "scale": 2, "suffix": "a/b" } ] }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-HANDOFF-005", DesignSeverity.Warning, messagePart = "a/b")
    }

    @Test
    fun validExportSettingsAreClean() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "hero", "type": "frame",
                "exportSettings": [ { "format": "png", "scale": 2, "suffix": "@2x" } ] }
            ] } ] }
            """.trimIndent(),
        ).apply {
            assertNone("IR-HANDOFF-004")
            assertNone("IR-HANDOFF-005")
        }
    }
}
