package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test

class MediaAssetChecksTest {

    @Test
    fun maskAppliesToUnknownIdIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame", "children": [
                { "id": "cutout", "type": "ellipse",
                  "mask": { "type": "alpha", "appliesTo": ["ghost"] } },
                { "id": "photo", "type": "rectangle" }
              ] }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-ASSET-006", DesignSeverity.Error, messagePart = "ghost")
    }

    @Test
    fun maskAppliesToNonSiblingWarns() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame", "children": [
                { "id": "cutout", "type": "ellipse",
                  "mask": { "type": "alpha", "appliesTo": ["faraway"] } },
                { "id": "group", "type": "frame", "children": [
                  { "id": "faraway", "type": "rectangle" }
                ] }
              ] }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-ASSET-007", DesignSeverity.Warning, messagePart = "faraway")
    }

    @Test
    fun maskAppliesToSiblingIsClean() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "root", "type": "frame", "children": [
                { "id": "cutout", "type": "ellipse",
                  "mask": { "type": "alpha", "appliesTo": ["photo"] } },
                { "id": "photo", "type": "rectangle" }
              ] }
            ] } ] }
            """.trimIndent(),
        ).apply {
            assertNone("IR-ASSET-006")
            assertNone("IR-ASSET-007")
        }
    }

    @Test
    fun focalPointOutOfRangeIsAnError() {
        validate(
            """
            {
              "assets": { "img1": { "type": "image", "url": "https://cdn/a.png" } },
              "pages": [ { "id": "p", "children": [
                { "id": "hero", "type": "media",
                  "media": { "assetId": "img1", "focalPoint": { "x": 1.5, "y": 0.5 } } }
              ] } ]
            }
            """.trimIndent(),
        ).assertHas("IR-ASSET-003", DesignSeverity.Error, messagePart = "hero")
    }

    @Test
    fun unknownAssetIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "hero", "type": "media", "media": { "assetId": "missing" } }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-ASSET-001", DesignSeverity.Error, messagePart = "missing")
    }

    @Test
    fun malformedVectorPathIsAnError() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "icon", "type": "vector",
                "paths": [ { "d": "M 0 0 L 10 X" } ] }
            ] } ] }
            """.trimIndent(),
        ).assertHas("IR-ASSET-004", DesignSeverity.Error, messagePart = "icon")
    }

    @Test
    fun wellFormedVectorPathIsClean() {
        validate(
            """
            { "pages": [ { "id": "p", "children": [
              { "id": "icon", "type": "vector",
                "paths": [ { "d": "M0 0 L10 10 C 1 2 3 4 5 6 Z" } ] }
            ] } ] }
            """.trimIndent(),
        ).assertNone("IR-ASSET-004")
    }
}
