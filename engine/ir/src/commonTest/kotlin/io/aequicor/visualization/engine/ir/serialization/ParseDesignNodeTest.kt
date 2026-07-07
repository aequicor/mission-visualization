package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.SourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 5.2 single-node entry point for the ```ir escape hatch. */
class ParseDesignNodeTest {

    @Test
    fun parsesSingleNodeAndSeedsSourceMap() {
        val result = parseDesignNode(
            """{ "id": "badge", "type": "text", "characters": "Hi" }""",
            pointerBase = "/blocks/3",
            file = "dashboard.slm.md",
            line = 42,
        )

        val success = assertIs<DesignNodeParseResult.Success>(result)
        assertEquals(0, success.diagnostics.size, "expected no diagnostics: ${success.diagnostics}")
        assertEquals("badge", success.node.id)
        assertIs<DesignNodeKind.Text>(success.node.kind)
        assertEquals(
            SourceLocation(pointer = "/blocks/3", file = "dashboard.slm.md", line = 42),
            success.node.sourceMap,
        )
        assertEquals(success.node, result.nodeOrNull())
    }

    @Test
    fun authoredSourceMapWinsOverSeed() {
        val result = parseDesignNode(
            """{ "id": "n", "type": "frame", "sourceMap": { "file": "authored.md", "line": 7 } }""",
            pointerBase = "/blocks/0",
            file = "seed.md",
            line = 99,
        )

        val success = assertIs<DesignNodeParseResult.Success>(result)
        assertEquals(
            SourceLocation(pointer = "/blocks/0", file = "authored.md", line = 7),
            success.node.sourceMap,
        )
    }

    @Test
    fun noSeedLeavesSourceMapEmpty() {
        val result = parseDesignNode("""{ "id": "n", "type": "frame" }""")
        assertNull(assertIs<DesignNodeParseResult.Success>(result).node.sourceMap)
    }

    @Test
    fun malformedJsonFails() {
        val result = parseDesignNode("{ this is not json", file = "broken.md", line = 3)
        val failure = assertIs<DesignNodeParseResult.Failure>(result)
        assertTrue(
            failure.diagnostics.any { it.severity == DesignSeverity.Error && "Malformed JSON" in it.message },
        )
        assertNull(result.nodeOrNull())
    }

    @Test
    fun nonObjectRootFails() {
        val result = parseDesignNode("[1, 2, 3]")
        val failure = assertIs<DesignNodeParseResult.Failure>(result)
        assertTrue(
            failure.diagnostics.any { it.severity == DesignSeverity.Error && "must be an object" in it.message },
        )
    }
}
