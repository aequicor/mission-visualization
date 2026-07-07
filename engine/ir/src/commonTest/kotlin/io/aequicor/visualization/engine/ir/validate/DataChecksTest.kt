package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DataValue
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.resolve.ResolveContext
import kotlin.test.Test

class DataChecksTest {

    @Test
    fun malformedExpressionIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "badge", "type": "frame",
                "visible": { "${'$'}data": "mission..done" } }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-DATA-001", DesignSeverity.Error, messagePart = "badge")
    }

    @Test
    fun wellFormedExpressionsAreClean() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "list", "type": "frame",
                "repeat": { "item": "mission", "in": "missions" },
                "condition": "{{mission.active == true}}",
                "children": [
                  { "id": "name", "type": "text", "characters": { "${'$'}data": "mission.name" } }
                ] }
            ] } ] }
            """.trimIndent(),
        ).assertNone("IR-DATA-001")
    }

    @Test
    fun repeatShadowingOuterBindingWarns() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "outer", "type": "frame",
                "repeat": { "item": "row", "in": "rows" },
                "children": [
                  { "id": "inner", "type": "frame",
                    "repeat": { "item": "row", "in": "columns" } }
                ] }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-DATA-002", DesignSeverity.Warning, messagePart = "row")
    }

    @Test
    fun unresolvedPathAgainstProvidedDataWarns() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "name", "type": "text", "characters": { "${'$'}data": "user.nickname" } }
            ] } ] }
            """.trimIndent(),
            context = ResolveContext(
                data = mapOf("user" to DataValue.MapValue(mapOf("name" to DataValue.Str("Ada")))),
            ),
        ).assertHas("IR-DATA-003", DesignSeverity.Warning, messagePart = "nickname")
    }

    @Test
    fun repeatCollectionMustBeAList() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "list", "type": "frame",
                "repeat": { "item": "row", "in": "rows" } }
            ] } ] }
            """.trimIndent(),
            context = ResolveContext(data = mapOf("rows" to DataValue.Num(3.0))),
        ).assertHas("IR-DATA-004", DesignSeverity.Error, messagePart = "rows")
    }
}
