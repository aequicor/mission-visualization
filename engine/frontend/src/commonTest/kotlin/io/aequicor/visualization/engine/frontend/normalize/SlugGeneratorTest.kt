package io.aequicor.visualization.engine.frontend.normalize

import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlugGeneratorTest {
    @Test
    fun explicitIdWins() {
        val generator = SlugGenerator(DiagnosticCollector())
        assertEquals(
            "missionPanel",
            generator.idFor(explicitId = "missionPanel", slugHint = "hint", name = "Имя"),
        )
    }

    @Test
    fun slugHintBeatsName() {
        val generator = SlugGenerator(DiagnosticCollector())
        assertEquals(
            "createMission",
            generator.idFor(explicitId = null, slugHint = "createMission", name = "Создать миссию"),
        )
    }

    @Test
    fun russianNameIsTransliteratedToCamelCase() {
        val generator = SlugGenerator(DiagnosticCollector())
        assertEquals(
            "sozdatMissiyu",
            generator.idFor(explicitId = null, name = "Создать миссию"),
        )
        assertEquals(
            "panelDetaley",
            generator.idFor(explicitId = null, name = "Панель деталей"),
        )
    }

    @Test
    fun englishNameIsCamelCased() {
        val generator = SlugGenerator(DiagnosticCollector())
        assertEquals(
            "missionDetailPanel",
            generator.idFor(explicitId = null, name = "Mission Detail Panel"),
        )
    }

    @Test
    fun roleThenKindOrdinalFallbacks() {
        val generator = SlugGenerator(DiagnosticCollector())
        assertEquals("topbar", generator.idFor(explicitId = null, role = "topbar"))
        assertEquals("text1", generator.idFor(explicitId = null, kind = "text"))
        assertEquals("text2", generator.idFor(explicitId = null, kind = "text"))
        assertEquals("frame1", generator.idFor(explicitId = null, kind = "frame"))
    }

    @Test
    fun generatedCollisionGetsSuffix() {
        val generator = SlugGenerator(DiagnosticCollector())
        assertEquals("filters", generator.idFor(explicitId = null, name = "Filters"))
        assertEquals("filters-2", generator.idFor(explicitId = null, name = "Filters"))
        assertEquals("filters-3", generator.idFor(explicitId = null, name = "Filters"))
    }

    @Test
    fun generatedCollidingWithExplicitIdWarns() {
        val collector = DiagnosticCollector()
        val generator = SlugGenerator(collector)
        assertEquals("filters", generator.idFor(explicitId = "filters"))
        assertEquals("filters-2", generator.idFor(explicitId = null, name = "Filters"))
        assertTrue(collector.diagnostics.any { "collides with an explicit id" in it.message })
    }

    @Test
    fun twoExplicitCollisionsAreAnError() {
        val collector = DiagnosticCollector()
        val generator = SlugGenerator(collector)
        assertEquals("panel", generator.idFor(explicitId = "panel"))
        assertEquals("panel-2", generator.idFor(explicitId = "panel"))
        assertTrue(
            collector.diagnostics.any {
                it.severity == DesignSeverity.Error && "Duplicate explicit node id" in it.message
            },
        )
    }

    @Test
    fun deterministicAcrossRuns() {
        fun run(): List<String> {
            val generator = SlugGenerator(DiagnosticCollector())
            return listOf(
                generator.idFor(explicitId = null, name = "Фильтры"),
                generator.idFor(explicitId = null, name = "Фильтры"),
                generator.idFor(explicitId = null, kind = "group"),
            )
        }
        assertEquals(run(), run())
    }
}
