package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test

class InteractionChecksTest {

    @Test
    fun overlayDestinationMissingIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "button", "type": "frame",
                "interactions": [ { "trigger": "onClick",
                  "action": { "type": "openOverlay", "destination": "ghost_dialog" } } ] }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-PROTO-002", DesignSeverity.Error, messagePart = "ghost_dialog")
    }

    @Test
    fun existingOverlayDestinationIsClean() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "button", "type": "frame",
                "interactions": [ { "trigger": "onClick",
                  "action": { "type": "openOverlay", "destination": "dialog" } } ] },
              { "id": "dialog", "type": "frame" }
            ] } ] }
            """.trimIndent(),
        ).assertNone("IR-PROTO-002")
    }

    @Test
    fun setVariableTypeMismatchIsAnError() {
        validate(
            """
            {
              "prototypeVariables": { "count": { "type": "number", "default": 0 } },
              "pages": [ { "id": "p", "children": [
                { "id": "button", "type": "frame",
                  "interactions": [ { "trigger": "onClick",
                    "action": { "type": "setVariable", "variable": "count", "value": "not-a-number" } } ] }
              ] } ]
            }
            """.trimIndent(),
        ).assertHas("IR-PROTO-005", DesignSeverity.Error, messagePart = "count")
    }

    @Test
    fun setVariableUndeclaredIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "button", "type": "frame",
                "interactions": [ { "trigger": "onClick",
                  "action": { "type": "setVariable", "variable": "ghost", "value": "1" } } ] }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-PROTO-004", DesignSeverity.Error, messagePart = "ghost")
    }

    @Test
    fun motionRefWithoutFallbackIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "spinner", "type": "frame", "motion": { "ref": "spin" } }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-PROTO-008", DesignSeverity.Error, messagePart = "spin")
    }

    @Test
    fun motionRefWithFallbackOrRegistryEntryIsClean() {
        validate(
            """
            {
              "motionRefs": { "spin": "asset_spin" },
              "pages": [ { "id": "p", "children": [
                { "id": "a", "type": "frame", "motion": { "ref": "spin" } },
                { "id": "b", "type": "frame", "motion": { "ref": "bounce",
                  "fallback": { "durationMs": 300, "frames": [ { "at": 0, "properties": { "opacity": 0 } } ] } } }
              ] } ]
            }
            """.trimIndent(),
        ).assertNone("IR-PROTO-008")
    }

    @Test
    fun backWithoutAnyOverlayWarns() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "button", "type": "frame",
                "interactions": [ { "trigger": "onClick", "action": { "type": "closeOverlay" } } ] }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-PROTO-003", DesignSeverity.Warning)
    }
}
