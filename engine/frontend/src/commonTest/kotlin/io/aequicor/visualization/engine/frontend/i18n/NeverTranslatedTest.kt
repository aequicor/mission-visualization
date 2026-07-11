package io.aequicor.visualization.engine.frontend.i18n

import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.frontend.semantics.SpecRuDocument
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The spec's never-translate list: expressions, routes, tokens, ids, refs,
 * asset paths and action/trigger names must never appear as resource VALUES
 * keyed for translation.
 */
class NeverTranslatedTest {
    @Test
    fun technicalPartsNeverBecomeResourceValues() {
        val source = SpecRuDocument + """

![Схема](assets/icons/alert.svg)

## Технический блок column gap ${'$'}space.4 color ${'$'}color.surface
"""
        val result = compileSlm(source)
        val values = result.resources.values.flatMap { it.values }
        assertTrue(values.isNotEmpty())

        val forbidden = listOf(
            "{{missions.length == 0}}",
            "{{mission in missions}}",
            "{{mission.status}}",
            "{{",
            "/missions/new",
            "\$space.4",
            "missionDashboard",
            "createMission",
            "ds/Button",
            "color.surface",
            "typography.heading.lg",
            "assets/icons/alert.svg",
            "selectedMissionId",
            "onClick",
            "navigate",
        )
        forbidden.forEach { token ->
            assertTrue(
                values.none { it.contains(token) },
                "Resource values must not contain \"$token\": ${values.filter { it.contains(token) }}",
            )
        }
    }

    @Test
    fun expressionsSurfaceOnlyAsIcuParams() {
        val result = compileSlm(SpecRuDocument)
        val values = result.resources.values.flatMap { it.values }
        // Bindings are placeholders, never raw expressions.
        assertTrue(values.any { "{missionName}" in it })
        assertTrue(values.none { "mission.name" in it })
    }
}
