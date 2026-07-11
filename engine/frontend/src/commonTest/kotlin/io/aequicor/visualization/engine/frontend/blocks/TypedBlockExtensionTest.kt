package io.aequicor.visualization.engine.frontend.blocks

import io.aequicor.visualization.engine.frontend.SlmCompileOptions
import io.aequicor.visualization.engine.frontend.blocks.readers.BlockReading
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Test payload of the custom `badge` extension. */
private data class BadgePayload(
    val label: String = "",
    val level: Int = 0,
)

/** Minimal well-behaved extension: pure payload application plus a validate hook. */
private object BadgeBlockExtension : TypedBlockExtension<BadgePayload> {
    override val kind: String = "badge"

    override fun validate(payload: BadgePayload, reading: BlockReading) {
        if (payload.level < 0) {
            reading.warning("`badge.level` must be >= 0")
        }
    }

    override fun applyToNode(node: DesignNode, payload: BadgePayload): DesignNode =
        node.copy(role = "badge-${payload.label}-${payload.level}")
}

private fun extensionWithKind(key: String): TypedBlockExtension<BadgePayload> =
    object : TypedBlockExtension<BadgePayload> {
        override val kind: String = key
        override fun applyToNode(node: DesignNode, payload: BadgePayload): DesignNode = node
    }

/** A document still spelling the retired raw-YAML block form for a registered extension key. */
private val rawYamlBadgeDocument = """
    ---
    screen: badgeScreen
    ---

    # Badge Screen

    ## Frame: id card name «Card»

    badge:
      label: hot
      level: 2
""".trimIndent()

private fun findNode(root: DesignNode, id: String): DesignNode? {
    if (root.id == id) return root
    return root.children.firstNotNullOfOrNull { findNode(it, id) }
}

class TypedBlockExtensionTest {

    @Test
    fun rawYamlExtensionBlockWarnsAndStaysProse() {
        val result = compileSlm(
            rawYamlBadgeDocument,
            SlmCompileOptions(extensions = SlmExtensionRegistry.of(BadgeBlockExtension)),
        )
        val document = assertNotNull(result.document)
        val card = assertNotNull(findNode(document.pages.single().children.single(), "card"))
        assertTrue(
            !card.role.startsWith("badge-"),
            "raw YAML block must not reach applyToNode; role=${card.role}",
        )
        assertTrue(
            result.diagnostics.any {
                it.severity == DesignSeverity.Warning &&
                    "Raw YAML typed blocks are no longer supported" in it.message &&
                    "`badge:`" in it.message
            },
            "expected the deprecation warning, got: ${result.diagnostics}",
        )
        assertTrue(result.diagnostics.none { it.severity == DesignSeverity.Error })
    }

    @Test
    fun registryRejectsBuiltInReservedKeys() {
        assertFailsWith<IllegalArgumentException> {
            SlmExtensionRegistry.of(extensionWithKind("shape"))
        }
        assertFailsWith<IllegalArgumentException> {
            SlmExtensionRegistry.of(extensionWithKind("node"))
        }
        // `ir` is no longer a reserved key: fenced blocks are generically warn-and-ignored.
        assertEquals(setOf("ir"), SlmExtensionRegistry.of(extensionWithKind("ir")).kinds)
    }

    @Test
    fun registryRejectsDuplicateKinds() {
        assertFailsWith<IllegalArgumentException> {
            SlmExtensionRegistry.of(BadgeBlockExtension, extensionWithKind("badge"))
        }
    }

    @Test
    fun registryRejectsMalformedKeys() {
        assertFailsWith<IllegalArgumentException> { SlmExtensionRegistry.of(extensionWithKind("")) }
        assertFailsWith<IllegalArgumentException> {
            SlmExtensionRegistry.of(extensionWithKind("1bad"))
        }
        assertFailsWith<IllegalArgumentException> {
            SlmExtensionRegistry.of(extensionWithKind("has space"))
        }
    }

    @Test
    fun emptyRegistryIsSharedAndEmpty() {
        assertTrue(SlmExtensionRegistry.Empty.isEmpty)
        assertNull(SlmExtensionRegistry.Empty.find("badge"))
        assertTrue(SlmExtensionRegistry.of(emptyList()).isEmpty)
    }

    @Test
    fun extensionPatchExposesKindAndAppliesThePayload() {
        val patch = ExtensionPatch(BadgeBlockExtension, BadgePayload("x", 1))
        assertEquals("badge", patch.kind)
        val node = DesignNode(id = "n1", type = "frame", kind = DesignNodeKind.Frame)
        assertEquals("badge-x-1", patch.applyTo(node).role)
    }

    @Test
    fun validateReportsThroughTheBlockReadingContext() {
        val collector = DiagnosticCollector("test.layout.md")
        BadgeBlockExtension.validate(BadgePayload("x", -3), BlockReading(collector, "badge"))
        assertTrue(collector.diagnostics.any { "badge.level" in it.message })
    }
}
