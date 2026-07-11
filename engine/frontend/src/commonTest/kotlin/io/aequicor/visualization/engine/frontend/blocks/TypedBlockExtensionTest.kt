package io.aequicor.visualization.engine.frontend.blocks

import io.aequicor.visualization.engine.frontend.SlmCompileOptions
import io.aequicor.visualization.engine.frontend.blocks.readers.BlockReading
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlScalar
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Test payload of the custom `badge:` block. */
private data class BadgePayload(
    val label: String = "",
    val level: Int = 0,
)

/** Minimal well-behaved extension: map block with `label` + `level`. */
private object BadgeBlockExtension : TypedBlockExtension<BadgePayload> {
    override val kind: String = "badge"

    override fun read(value: YamlValue, reading: BlockReading): BadgePayload? {
        val map = value as? YamlMap ?: run {
            reading.error("`badge` must be a map", value)
            return null
        }
        return BadgePayload(
            label = (map.entries["label"] as? YamlScalar)?.value as? String ?: "",
            level = ((map.entries["level"] as? YamlScalar)?.value as? Double)?.toInt() ?: 0,
        )
    }

    override fun validate(payload: BadgePayload, reading: BlockReading) {
        if (payload.level < 0) {
            reading.diagnostics.warning("`badge.level` must be >= 0", blockPath = reading.blockPath)
        }
    }

    override fun applyToNode(node: DesignNode, payload: BadgePayload): DesignNode =
        node.copy(role = "badge-${payload.label}-${payload.level}")

    override fun write(payload: BadgePayload): String = buildString {
        append("badge:")
        append("\n  label: ${payload.label}")
        append("\n  level: ${payload.level}")
    }
}

private fun extensionWithKind(key: String): TypedBlockExtension<BadgePayload> =
    object : TypedBlockExtension<BadgePayload> {
        override val kind: String = key
        override fun read(value: YamlValue, reading: BlockReading): BadgePayload? = BadgePayload()
        override fun applyToNode(node: DesignNode, payload: BadgePayload): DesignNode = node
        override fun write(payload: BadgePayload): String = "$key:"
    }

private val badgeDocument = """
    ---
    screen: badgeScreen
    ---

    # Badge Screen

    ## Card
    node: { id: card }
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
    fun registeredExtensionBlockIsParsedAndAppliedToTheAnchorNode() {
        val result = compileSlm(
            badgeDocument,
            SlmCompileOptions(extensions = SlmExtensionRegistry.of(BadgeBlockExtension)),
        )
        val document = assertNotNull(result.document)
        val card = assertNotNull(findNode(document.pages.single().children.single(), "card"))
        assertEquals("badge-hot-2", card.role)
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            "Unexpected errors: ${result.diagnostics}",
        )
    }

    @Test
    fun unregisteredKeyKeepsLegacyBehaviorAndStaysProse() {
        val result = compileSlm(badgeDocument)
        val document = assertNotNull(result.document)
        val card = assertNotNull(findNode(document.pages.single().children.single(), "card"))
        assertTrue(card.role.isEmpty() || !card.role.startsWith("badge-"))
        assertTrue(result.diagnostics.none { it.severity == DesignSeverity.Error })
    }

    @Test
    fun validateReportsDiagnosticsWithoutDroppingThePayload() {
        val negative = badgeDocument.replace("level: 2", "level: -3")
        val result = compileSlm(
            negative,
            SlmCompileOptions(extensions = SlmExtensionRegistry.of(BadgeBlockExtension)),
        )
        val document = assertNotNull(result.document)
        val card = assertNotNull(findNode(document.pages.single().children.single(), "card"))
        assertEquals("badge-hot--3", card.role)
        assertTrue(result.diagnostics.any { it.message.contains("badge.level") })
    }

    @Test
    fun registryRejectsBuiltInReservedKeys() {
        assertFailsWith<IllegalArgumentException> {
            SlmExtensionRegistry.of(extensionWithKind("shape"))
        }
        assertFailsWith<IllegalArgumentException> {
            SlmExtensionRegistry.of(extensionWithKind("ir"))
        }
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
    fun writerOutputRoundTripsThroughTheCompiler() {
        val payload = BadgePayload(label = "fresh", level = 5)
        val block = BadgeBlockExtension.write(payload)
        val document = listOf(
            "---",
            "screen: badgeScreen",
            "---",
            "",
            "# Badge Screen",
            "",
            "## Card",
            "node: { id: card }",
            block,
        ).joinToString("\n")
        val result = compileSlm(
            document,
            SlmCompileOptions(extensions = SlmExtensionRegistry.of(BadgeBlockExtension)),
        )
        val compiled = assertNotNull(result.document)
        val card = assertNotNull(findNode(compiled.pages.single().children.single(), "card"))
        assertEquals("badge-fresh-5", card.role)
    }

    @Test
    fun extensionPatchExposesKindAndWriteBlock() {
        val patch = ExtensionPatch(BadgeBlockExtension, BadgePayload("x", 1))
        assertEquals("badge", patch.kind)
        assertEquals("badge:\n  label: x\n  level: 1", patch.writeBlock())
    }
}
