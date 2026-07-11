package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.semantics.SpecRuDocument
import io.aequicor.visualization.engine.ir.model.SizingMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnaddressableNodeTest {
    @Test
    fun paragraphSegmentNodeHasNoAnchor() {
        val compiled = compileForEdit(SpecRuDocument)
        // The topbar button is synthesized from a paragraph segment, not an anchor.
        val result = applySlmEdit(
            SpecRuDocument,
            SetSizing("createMission", width = SizingSpec(SizingMode.Fixed, value = 200.0)),
            compiled,
        )
        assertFalse(result.isApplied)
        val message = result.diagnostics.single().message
        assertTrue("no addressable source anchor" in message, message)
        assertTrue("promote" in message, message)
    }

    @Test
    fun ignoredIrFenceNodeIsUnaddressable() {
        // ```ir is no longer an authoring surface: the fence is ignored, so no node is
        // produced and any edit targeting its would-be id degrades to the generic message.
        val doc = """
            ---
            screen: s
            sourceLocale: en-US
            ---

            # Screen

            ## Alerts

            ```ir
            {
              "type": "vector",
              "id": "customAlertIcon",
              "name": "Custom alert icon",
              "pathRef": "assets/icons/custom-alert.svg"
            }
            ```
        """.trimIndent() + "\n"
        val compiled = compileForEdit(doc)
        val result = applySlmEdit(
            doc,
            SetSizing("customAlertIcon", width = SizingSpec(SizingMode.Fixed, value = 24.0)),
            compiled,
        )
        assertFalse(result.isApplied)
        val message = result.diagnostics.single().message
        assertTrue("no addressable source anchor" in message, message)
        assertTrue("promote" in message, message)
    }
}
