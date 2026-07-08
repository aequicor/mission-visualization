package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.semantics.SpecRuDocument
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.SizingMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Semantic interplay (design J.D): no special casing. Inserted explicit blocks win
 * by precedence; only a genuine contradiction with a lexicon-asserted property
 * fires the prose-drift warning.
 */
class SemanticRoundTripTest {
    private val conflictMarker = "overrides semantic extraction"

    @Test
    fun resizeRoundTripsWithZeroConflictWarnings() {
        val compiled = compileForEdit(SpecRuDocument)
        val edited = applySlmEdit(
            SpecRuDocument,
            SetSizing("topbar", width = SizingSpec(SizingMode.Fixed, value = 320.0)),
            compiled,
        )
        val recompiled = compileForEdit(edited.requireNewSource())
        val topbar = recompiled.requireDocument().requireNode("topbar")
        assertEquals(SizingMode.Fixed, topbar.sizing?.horizontal)
        assertEquals(320.0, topbar.size.width)
        // Lexicon rules never assert sizing, so the explicit block conflicts with nothing.
        assertEquals(0, recompiled.diagnostics.count { conflictMarker in it.message })
        // The lexicon-derived layout still applies untouched.
        assertEquals(LayoutMode.Horizontal, topbar.layout.mode)
    }

    @Test
    fun flippingLexiconAssertedModeFiresExactlyOneProseDriftWarning() {
        val compiled = compileForEdit(SpecRuDocument)
        val edited = applySlmEdit(
            SpecRuDocument,
            SetLayoutProperty("topbar", LayoutProp.Mode, YamlScalarValue.Str("column")),
            compiled,
        )
        val recompiled = compileForEdit(edited.requireNewSource())
        val topbar = recompiled.requireDocument().requireNode("topbar")
        // Explicit wins over the lexicon's `layout.mode: row`...
        assertEquals(LayoutMode.Vertical, topbar.layout.mode)
        // ...and the contradiction with the prose is reported exactly once.
        assertEquals(1, recompiled.diagnostics.count { conflictMarker in it.message })
    }
}
