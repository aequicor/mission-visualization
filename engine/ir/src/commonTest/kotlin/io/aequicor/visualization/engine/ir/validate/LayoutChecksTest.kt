package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test

class LayoutChecksTest {

    @Test
    fun frameWithFlowLayoutIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "legacy", "type": "frame", "containerKind": "frame", "layout": { "mode": "vertical", "gap": 8 } }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-LAYOUT-009", DesignSeverity.Error, messagePart = "legacy")
    }

    @Test
    fun legacyFrameWithFlowButNoContainerKindInfersAutoLayout() {
        // JSON that predates the containerKind field (or omits it) is read as AutoLayout when its
        // layout has a flow mode, so it is not flagged as a Frame/flow contradiction.
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "legacy", "type": "frame", "layout": { "mode": "vertical", "gap": 8 } }
            ] } ] }
            """.trimIndent(),
        ).assertNone("IR-LAYOUT-009")
    }

    @Test
    fun autoLayoutWithoutDirectionIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "stack", "type": "frame", "containerKind": "autoLayout", "layout": { "mode": "none" } }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-LAYOUT-010", DesignSeverity.Error, messagePart = "stack")
    }

    @Test
    fun matchingContainerKindsAreValid() {
        val diagnostics = validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "free", "type": "frame", "containerKind": "frame", "layout": { "mode": "none" } },
              { "id": "stack", "type": "frame", "containerKind": "autoLayout", "layout": { "mode": "horizontal", "gap": 8 } }
            ] } ] }
            """.trimIndent(),
        )
        diagnostics.assertNone("IR-LAYOUT-009")
        diagnostics.assertNone("IR-LAYOUT-010")
    }

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

    @Test
    fun centerConstraintWithoutInitialPositionWarns() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame", "size": { "width": 200, "height": 100 },
                "children": [
                  { "id": "card", "type": "frame", "size": { "width": 80, "height": 40 },
                    "constraints": { "horizontal": "center", "vertical": "center" } }
                ]
              }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-LAYOUT-011", DesignSeverity.Warning, messagePart = "do not create one")
    }

    @Test
    fun centerConstraintWithCalculatedPositionIsClean() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame", "size": { "width": 200, "height": 100 },
                "children": [
                  { "id": "card", "type": "frame", "size": { "width": 80, "height": 40 },
                    "position": { "x": 60, "y": 30 },
                    "constraints": { "horizontal": "center", "vertical": "center" } }
                ]
              }
            ] } ] }
            """.trimIndent(),
        ).assertNone("IR-LAYOUT-011")
    }

    @Test
    fun constraintsOnInFlowChildWithPositionAreClean() {
        // Figma legitimately lets an in-flow Auto Layout child carry a non-default resize
        // constraint (it applies once the child is detached/absolutized). As long as the
        // child has an authored position to preserve, IR-LAYOUT-011 must stay silent.
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame", "containerKind": "autoLayout",
                "size": { "width": 200, "height": 100 }, "layout": { "mode": "vertical" },
                "children": [
                  { "id": "card", "type": "frame", "size": { "width": 80, "height": 40 },
                    "position": { "x": 60, "y": 30 },
                    "constraints": { "horizontal": "right", "vertical": "bottom" } }
                ]
              }
            ] } ] }
            """.trimIndent(),
        ).assertNone("IR-LAYOUT-011")
    }
}
