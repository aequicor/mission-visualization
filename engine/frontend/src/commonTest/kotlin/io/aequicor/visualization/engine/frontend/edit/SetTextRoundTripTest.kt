package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.SlmLocale
import io.aequicor.visualization.engine.frontend.semantics.SpecRuDocument
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class SetTextRoundTripTest {
    @Test
    fun setTextWritesDefaultTextAndRegeneratesTheBundleUnderTheSameKey() {
        val compiled = compileForEdit(SpecRuDocument)
        val result = applySlmEdit(SpecRuDocument, SetText("name", "Метка: {missionName}"), compiled)
        val new = result.requireNewSource()
        assertEquals(
            SpecRuDocument.replace(
                "  - Название: {{mission.name}}\n",
                "  - Название: {{mission.name}}\n    text:\n      defaultText: \"Метка: {missionName}\"\n",
            ),
            new,
        )
        assertLosslessOutside(SpecRuDocument, new, assertNotNull(result.appliedRange))

        val recompiled = compileForEdit(new)
        val name = recompiled.requireDocument().requireNode("name")
        val kind = assertIs<DesignNodeKind.Text>(name.kind)
        assertEquals("Метка: {missionName}", kind.content?.defaultText)
        // The generated key is untouched; only the default text changed.
        assertEquals("missionDashboard.missions.card.name", kind.content?.key)
        assertEquals(
            "Метка: {missionName}",
            assertNotNull(recompiled.resources[SlmLocale("ru-RU")])["missionDashboard.missions.card.name"],
        )
    }
}
