package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test

class StructureChecksTest {

    @Test
    fun duplicateNodeIdIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame", "children": [
                { "id": "dup", "type": "rectangle" },
                { "id": "dup", "type": "rectangle" }
              ] }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-STRUCT-003", DesignSeverity.Error, messagePart = "dup")
    }

    @Test
    fun uniqueIdsProduceNoStructureDiagnostics() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame", "children": [
                { "id": "a", "type": "rectangle" },
                { "id": "b", "type": "rectangle" }
              ] }
            ] } ] }
            """.trimIndent(),
        ).assertNone("IR-STRUCT-003")
    }

    @Test
    fun unsupportedSchemaVersionIsAnError() {
        validate(
            """
            { "schemaVersion": "slm-ir/2.0",
              "pages": [ { "id": "p", "children": [ { "id": "root", "type": "frame" } ] } ] }
            """.trimIndent(),
        ).assertHas("IR-STRUCT-001", DesignSeverity.Error, messagePart = "slm-ir/2.0")
    }

    @Test
    fun duplicateExplicitSiblingOrderWarns() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame", "children": [
                { "id": "a", "type": "rectangle", "order": 1 },
                { "id": "b", "type": "rectangle", "order": 1 }
              ] }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-STRUCT-005", DesignSeverity.Warning)
    }
}
