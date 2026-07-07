package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test

class StyleChecksTest {

    @Test
    fun variableWithoutValueForOneModeWarns() {
        validate(
            """
            {
              "variables": { "collections": { "theme": {
                "modes": ["light", "dark"], "defaultMode": "light",
                "vars": { "accent": { "type": "color", "values": { "light": "#1F5FA8" } } }
              } } },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame",
                  "fills": [ { "type": "solid", "color": { "${'$'}var": "accent" } } ] }
              ] } ]
            }
            """.trimIndent(),
        ).assertHas("IR-STYLE-004", DesignSeverity.Warning, messagePart = "dark")
    }

    @Test
    fun variableCoveringAllModesIsClean() {
        validate(
            """
            {
              "variables": { "collections": { "theme": {
                "modes": ["light", "dark"], "defaultMode": "light",
                "vars": { "accent": { "type": "color",
                  "values": { "light": "#1F5FA8", "dark": "#143A66" } } }
              } } },
              "pages": [ { "id": "p", "children": [ { "id": "root", "type": "frame" } ] } ]
            }
            """.trimIndent(),
        ).assertNone("IR-STYLE-004")
    }

    @Test
    fun aliasCycleIsAnError() {
        validate(
            """
            {
              "variables": { "collections": { "theme": {
                "modes": ["light"], "defaultMode": "light",
                "vars": {
                  "a": { "type": "color", "values": { "light": { "${'$'}var": "b" } } },
                  "b": { "type": "color", "values": { "light": { "${'$'}var": "a" } } }
                }
              } } },
              "pages": [ { "id": "p", "children": [ { "id": "root", "type": "frame" } ] } ]
            }
            """.trimIndent(),
        ).assertHas("IR-STYLE-005", DesignSeverity.Error)
    }

    @Test
    fun unknownVariableReferenceIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "fills": [ { "type": "solid", "color": { "${'$'}var": "ghost" } } ] }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-STYLE-003", DesignSeverity.Error, messagePart = "ghost")
    }

    @Test
    fun variableTypeMismatchAtUsageSiteIsAnError() {
        validate(
            """
            {
              "variables": { "collections": { "sizes": {
                "modes": ["default"], "defaultMode": "default",
                "vars": { "spacing": { "type": "number", "values": { "default": 8 } } }
              } } },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame",
                  "fills": [ { "type": "solid", "color": { "${'$'}var": "spacing" } } ] }
              ] } ]
            }
            """.trimIndent(),
        ).assertHas("IR-STYLE-006", DesignSeverity.Error, messagePart = "spacing")
    }

    @Test
    fun unknownStyleIdIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame", "fillStyleId": "nope" }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-STYLE-001", DesignSeverity.Error, messagePart = "nope")
    }
}
