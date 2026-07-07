package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test

class ResponsiveChecksTest {

    @Test
    fun unknownBreakpointIdInSelectorIsAnError() {
        validate(
            """
            {
              "breakpoints": [ { "id": "sm", "maxWidth": 599 }, { "id": "md", "minWidth": 600 } ],
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame",
                  "responsive": [ { "when": { "breakpoint": "xxl" }, "set": { "opacity": 0.5 } } ] }
              ] } ]
            }
            """.trimIndent(),
        ).assertHas("IR-RESP-001", DesignSeverity.Error, messagePart = "xxl")
    }

    @Test
    fun declaredBreakpointSelectorIsClean() {
        validate(
            """
            {
              "breakpoints": [ { "id": "sm", "maxWidth": 599 }, { "id": "md", "minWidth": 600 } ],
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame",
                  "responsive": [ { "when": { "breakpoint": "md" }, "set": { "opacity": 0.5 } } ] }
              ] } ]
            }
            """.trimIndent(),
        ).assertNone("IR-RESP-001")
    }

    @Test
    fun equalSpecificityVariantsPatchingSameGroupIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "responsive": [
                  { "when": { "theme": "dark" }, "set": { "opacity": 0.5 } },
                  { "when": { "brand": "acme" }, "set": { "opacity": 0.7 } }
                ] }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-RESP-005", DesignSeverity.Error, messagePart = "opacity")
    }

    @Test
    fun mutuallyExclusiveVariantsAreClean() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "responsive": [
                  { "when": { "theme": "dark" }, "set": { "opacity": 0.5 } },
                  { "when": { "theme": "light" }, "set": { "opacity": 0.7 } }
                ] }
            ] } ] }
            """.trimIndent(),
        ).assertNone("IR-RESP-005")
    }

    @Test
    fun differentGroupsAtEqualSpecificityAreClean() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame",
                "responsive": [
                  { "when": { "theme": "dark" }, "set": { "opacity": 0.5 } },
                  { "when": { "brand": "acme" }, "set": { "visible": false } }
                ] }
            ] } ] }
            """.trimIndent(),
        ).assertNone("IR-RESP-005")
    }

    @Test
    fun overlappingBreakpointsWarn() {
        validate(
            """
            {
              "breakpoints": [
                { "id": "sm", "minWidth": 0, "maxWidth": 700 },
                { "id": "md", "minWidth": 600, "maxWidth": 1000 }
              ],
              "pages": [ { "id": "p", "children": [ { "id": "root", "type": "frame" } ] } ]
            }
            """.trimIndent(),
        ).assertHas("IR-RESP-006", DesignSeverity.Warning)
    }
}
