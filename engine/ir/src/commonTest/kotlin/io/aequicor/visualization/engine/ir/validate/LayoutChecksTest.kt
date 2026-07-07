package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test

class LayoutChecksTest {

    @Test
    fun fillChildInFreeLayoutWithoutConstraintsWarns() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame", "size": { "width": 200, "height": 100 },
                "layout": { "mode": "none" },
                "children": [
                  { "id": "banner", "type": "rectangle",
                    "sizing": { "horizontal": "fill" }, "size": { "height": 20 } }
                ] }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-LAYOUT-004", DesignSeverity.Warning, messagePart = "banner")
    }

    @Test
    fun fillChildWithStretchingConstraintsIsFine() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame", "size": { "width": 200, "height": 100 },
                "layout": { "mode": "none" },
                "children": [
                  { "id": "banner", "type": "rectangle",
                    "sizing": { "horizontal": "fill" }, "size": { "height": 20 },
                    "constraints": { "horizontal": "leftRight" } }
                ] }
            ] } ] }
            """.trimIndent(),
        ).assertNone("IR-LAYOUT-004")
    }

    @Test
    fun gridSpanOutOfBoundsIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame", "size": { "width": 200, "height": 100 },
                "layout": { "mode": "grid",
                            "columns": [ { "type": "flex", "value": 1 }, { "type": "flex", "value": 1 } ] },
                "children": [
                  { "id": "wide", "type": "rectangle",
                    "gridPlacement": { "column": 2, "row": 1, "columnSpan": 2 } }
                ] }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-LAYOUT-003", DesignSeverity.Error, messagePart = "wide")
    }

    @Test
    fun fixedChildrenMustBeDirectChildren() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame", "size": { "width": 200, "height": 100 },
                "layout": { "mode": "vertical" },
                "scroll": { "overflow": "vertical", "fixedChildren": ["ghost"] },
                "children": [ { "id": "row", "type": "rectangle", "size": { "height": 20 } } ] }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-LAYOUT-008", DesignSeverity.Error, messagePart = "ghost")
    }

    @Test
    fun negativeGapIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame", "size": { "width": 200, "height": 100 },
                "layout": { "mode": "vertical", "gap": -4 },
                "children": [ { "id": "row", "type": "rectangle", "size": { "height": 20 } } ] }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-LAYOUT-001", DesignSeverity.Error, messagePart = "gap")
    }
}
