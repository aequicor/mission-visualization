package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test

class ComponentChecksTest {

    @Test
    fun unknownLibraryRefIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "card", "type": "instance", "componentId": "ds/Card", "libraryRef": "lib-x" }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-COMP-002", DesignSeverity.Error, messagePart = "lib-x")
    }

    @Test
    fun unknownComponentIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "card", "type": "instance", "componentId": "ghost" }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-COMP-001", DesignSeverity.Error, messagePart = "ghost")
    }

    @Test
    fun slotContentOverMaxItemsIsAnError() {
        validate(
            """
            {
              "components": { "list": {
                "name": "List",
                "properties": { "items": { "type": "slot", "maxItems": 1 } },
                "root": { "id": "list_root", "type": "frame", "children": [
                  { "id": "list_slot", "type": "slot", "slotName": "items" }
                ] }
              } },
              "pages": [ { "id": "p", "children": [
                { "id": "menu", "type": "instance", "componentId": "list",
                  "props": { "items": [
                    { "id": "i1", "type": "rectangle" },
                    { "id": "i2", "type": "rectangle" }
                  ] } }
              ] } ]
            }
            """.trimIndent(),
        ).assertHas("IR-COMP-006", DesignSeverity.Error, messagePart = "items")
    }

    @Test
    fun overridePathMissingIsAnError() {
        validate(
            """
            {
              "components": { "card": {
                "name": "Card",
                "root": { "id": "card_root", "type": "frame", "children": [
                  { "id": "card_title", "type": "text", "characters": "Title" }
                ] }
              } },
              "pages": [ { "id": "p", "children": [
                { "id": "hero", "type": "instance", "componentId": "card",
                  "overrides": [ { "target": ["missing_node"], "characters": "Hi" } ] }
              ] } ]
            }
            """.trimIndent(),
        ).assertHas("IR-COMP-008", DesignSeverity.Error, messagePart = "missing_node")
    }

    @Test
    fun validOverridePathIsClean() {
        validate(
            """
            {
              "components": { "card": {
                "name": "Card",
                "root": { "id": "card_root", "type": "frame", "children": [
                  { "id": "card_title", "type": "text", "characters": "Title" }
                ] }
              } },
              "pages": [ { "id": "p", "children": [
                { "id": "hero", "type": "instance", "componentId": "card",
                  "overrides": [ { "target": ["card_title"], "characters": "Hi" } ] }
              ] } ]
            }
            """.trimIndent(),
        ).assertNone("IR-COMP-008")
    }

    @Test
    fun propTypeMismatchIsAnError() {
        validate(
            """
            {
              "components": { "card": {
                "name": "Card",
                "properties": { "count": { "type": "number", "default": 1 } },
                "root": { "id": "card_root", "type": "frame" }
              } },
              "pages": [ { "id": "p", "children": [
                { "id": "hero", "type": "instance", "componentId": "card",
                  "props": { "count": true } }
              ] } ]
            }
            """.trimIndent(),
        ).assertHas("IR-COMP-004", DesignSeverity.Error, messagePart = "count")
    }

    @Test
    fun componentRecursionCycleIsAnError() {
        validate(
            """
            {
              "components": {
                "a": { "name": "A", "root": { "id": "a_root", "type": "frame", "children": [
                  { "id": "a_inner", "type": "instance", "componentId": "b" } ] } },
                "b": { "name": "B", "root": { "id": "b_root", "type": "frame", "children": [
                  { "id": "b_inner", "type": "instance", "componentId": "a" } ] } }
              },
              "pages": [ { "id": "p", "children": [ { "id": "root", "type": "frame" } ] } ]
            }
            """.trimIndent(),
        ).assertHas("IR-COMP-011", DesignSeverity.Error)
    }
}
