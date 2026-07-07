package io.aequicor.visualization.engine.frontend.semantics

import io.aequicor.visualization.engine.frontend.SlmLocale
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignI18n
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.PropValue
import io.aequicor.visualization.engine.ir.model.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Design-section-D parity contract: compiling the spec's RU example and its
 * faithful EN twin must produce EQUAL documents once locale-derived surface
 * (resources, default texts, default locales, prose-derived names) is
 * normalized away — and identical generated i18n KEYS.
 */
class RuEnParityTest {
    @Test
    fun ruAndEnDocumentsAreStructurallyEqual() {
        val ru = compileSlm(SpecRuDocument)
        val en = compileSlm(SpecEnDocument)
        assertTrue(ru.diagnostics.none { it.severity == DesignSeverity.Error })
        assertTrue(en.diagnostics.none { it.severity == DesignSeverity.Error })

        val ruDocument = assertNotNull(ru.document).normalizedForParity()
        val enDocument = assertNotNull(en.document).normalizedForParity()
        assertEquals(ruDocument, enDocument)
    }

    @Test
    fun generatedI18nKeysAreIdentical() {
        val ru = compileSlm(SpecRuDocument)
        val en = compileSlm(SpecEnDocument)
        val ruKeys = assertNotNull(ru.resources[SlmLocale("ru-RU")]).keys
        val enKeys = assertNotNull(en.resources[SlmLocale("en-US")]).keys
        assertEquals(ruKeys, enKeys)
        assertTrue("missionDashboard.actions.createMission" in ruKeys)
        assertTrue("missionDashboard.actions.open" in ruKeys)
        assertTrue("missionDashboard.empty.title" in ruKeys)
        assertTrue("missionDashboard.missions.card.name" in ruKeys)
        assertTrue("missionDashboard.missions.card.status" in ruKeys)
    }

    // --- locale-derived surface normalization ---

    private fun DesignDocument.normalizedForParity(): DesignDocument = copy(
        name = "",
        i18n = DesignI18n(),
        screen = screen?.copy(name = ""),
        pages = pages.map { page -> page.copy(children = page.children.map { it.scrub() }) },
        components = components.mapValues { (_, component) ->
            component.copy(name = "", root = component.root.scrub())
        },
    )

    private fun DesignNode.scrub(): DesignNode = copy(
        name = "",
        kind = kind.scrub(),
        children = children.map { it.scrub() },
    )

    private fun DesignNodeKind.scrub(): DesignNodeKind = when (this) {
        is DesignNodeKind.Text -> copy(content = content?.scrub())
        is DesignNodeKind.Instance -> copy(
            props = props.mapValues { (_, value) ->
                if (value is PropValue.Content) PropValue.Content(value.content.scrub()) else value
            },
        )
        is DesignNodeKind.Media -> copy(media = media.copy(alt = media.alt?.scrub()))
        else -> this
    }

    private fun TextContent.scrub(): TextContent = copy(defaultLocale = "", defaultText = "")
}
