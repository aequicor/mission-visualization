package io.aequicor.visualization.ui_engine.compose_render_engine

import io.aequicor.visualization.ui_engine.compose_render_engine.registeredUiRendererTypes
import io.aequicor.visualization.ui_engine.ui_document_ir.KnownUiNodeTypes
import kotlin.test.Test
import kotlin.test.assertTrue

class UiRendererRegistryTest {
    @Test
    fun everyKnownRichAppKitNodeHasRegisteredRenderer() {
        val missing = KnownUiNodeTypes - registeredUiRendererTypes()

        assertTrue(missing.isEmpty(), "Missing renderers for: $missing")
    }

    @Test
    fun unknownNodeTypesRemainOutsideRegistryForFallbackRendering() {
        assertTrue("customWidget" !in registeredUiRendererTypes())
    }
}
