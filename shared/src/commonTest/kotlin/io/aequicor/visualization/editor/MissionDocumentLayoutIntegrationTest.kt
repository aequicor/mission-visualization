package io.aequicor.visualization.editor

import io.aequicor.visualization.designdoc.data.MissionDesignJson
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MissionDocumentLayoutIntegrationTest {

    private val engine = DesignLayoutEngine()

    @Test
    fun missionDocumentLaysOutCleanlyAtAuthoredAndDeviceWidths() {
        val result = parseDesignDocument(MissionDesignJson)
        val success = assertIs<DesignParseResult.Success>(result)
        assertTrue(
            success.diagnostics.none { it.severity == DesignSeverity.Error },
            "mission doc parse errors: ${success.diagnostics}",
        )
        val resolver = DesignResolver(success.document)

        success.document.pages.forEach { page ->
            val resolved = assertNotNull(resolver.resolvePage(page).firstOrNull(), "page ${page.id} has a root frame")
            val box = engine.layout(resolved)
            assertEquals(1440.0, box.width, "page ${page.id} authored width")
            assertEquals(1024.0, box.height, "page ${page.id} authored height")

            val mobile = engine.layout(resolved, 375.0, 812.0)
            assertEquals(375.0, mobile.width)
            mobile.allBoxes().forEach { child ->
                assertTrue(child.width >= 0.0 && child.height >= 0.0, "negative size in ${child.node.id}")
            }
        }
        assertTrue(
            resolver.diagnostics.isEmpty(),
            "mission doc resolve diagnostics: ${resolver.diagnostics}",
        )

        val overview = success.document.pages.first()
        val overviewBox = engine.layout(assertNotNull(resolver.resolvePage(overview).firstOrNull()))
        val tiles = listOf("tile_1", "tile_2", "tile_3").map { id ->
            assertNotNull(overviewBox.findBySourceId(id), "missing $id")
        }
        assertEquals(tiles[0].width, tiles[1].width, "tiles share the row equally")
        assertEquals(tiles[1].width, tiles[2].width)
        assertEquals(150.0, tiles[0].height, "tile height comes from the component variant")
    }
}
