package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.orZero
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Adversarial SLM-fidelity probes for the structural + typography write-back: tricky node
 * names (colon, markdown, unicode/RU, empty), unicode text bodies, token colors, nested
 * frames, and byte-preservation of authored prose / comments outside the touched region.
 */
class AdversarialRoundTripTest {

    // A document with an authored HTML comment (i18n key), prose, and two frame anchors.
    private val doc = """
        ---
        screen: s
        sourceLocale: en-US
        ---

        # Screen id root name «Screen»

        <!-- authored comment: do not touch -->
        Some authored prose between sections.

        ## AutoLayout: Panel id panel column

        ## Frame: Sidebar id sidebar
    """.trimIndent() + "\n"

    private fun frame(id: String, name: String, children: List<DesignNode> = emptyList()): DesignNode =
        DesignNode(
            id = id,
            type = "frame",
            kind = DesignNodeKind.Frame,
            name = name,
            children = children,
        )

    private fun rect(id: String, name: String, fills: List<DesignPaint>? = null): DesignNode =
        DesignNode(
            id = id,
            type = "shape",
            kind = DesignNodeKind.Shape(shape = ShapeType.Rectangle),
            name = name,
            sizing = DesignSizing(horizontal = SizingMode.Fixed, vertical = SizingMode.Fixed),
            size = DesignSize(width = 100.0, height = 40.0),
            fills = fills,
        )

    private fun text(id: String, name: String, body: String): DesignNode =
        DesignNode(
            id = id,
            type = "text",
            kind = DesignNodeKind.Text(characters = body.bindable()),
            name = name,
        )

    private fun insertUnderPanel(node: DesignNode): String {
        val compiled = compileForEdit(doc)
        val result = applySlmEdit(doc, InsertChildSubtree("panel", node), compiled)
        return result.requireNewSource()
    }

    private fun recompiled(source: String) = compileForEdit(source).also { r ->
        val errors = r.diagnostics.filter { it.severity == DesignSeverity.Error }
        assertTrue(errors.isEmpty(), "recompile errors: ${errors.joinToString { it.message }}\n---\n$source")
    }

    private fun assertCommentPreserved(new: String) {
        assertTrue("<!-- authored comment: do not touch -->" in new, "authored comment dropped:\n$new")
        assertTrue("Some authored prose between sections." in new, "authored prose dropped:\n$new")
        assertTrue("## Frame: Sidebar" in new, "sibling section dropped:\n$new")
    }

    @Test
    fun nameWithColonRoundTrips() {
        val new = insertUnderPanel(rect("r_colon", "Panel: Main"))
        assertCommentPreserved(new)
        val node = recompiled(new).requireDocument().requireNode("r_colon")
        assertEquals("Panel: Main", node.name, "colon-bearing name must survive intact\n$new")
    }

    @Test
    fun nameWithMarkdownCharsRoundTrips() {
        val new = insertUnderPanel(rect("r_md", "*emphasis* _under_ #hash"))
        assertCommentPreserved(new)
        val node = recompiled(new).requireDocument().requireNode("r_md")
        assertEquals("*emphasis* _under_ #hash", node.name, "markdown-y name must survive verbatim\n$new")
    }

    @Test
    fun unicodeRuNameAndTextRoundTrip() {
        val node = text("t_ru", "Заголовок «Миссия»", "Привет, мир! Строка с \"кавычками\" и emoji 🚀")
        val new = insertUnderPanel(node)
        assertCommentPreserved(new)
        val re = recompiled(new).requireDocument().requireNode("t_ru")
        assertEquals("Заголовок «Миссия»", re.name)
        val kind = assertNotNull(re.kind as? DesignNodeKind.Text)
        val body = kind.content?.defaultText ?: (kind.characters as? Bindable.Value)?.value
        assertEquals("Привет, мир! Строка с \"кавычками\" и emoji 🚀", body, "unicode text body corrupted\n$new")
    }

    @Test
    fun textWithNewlineRoundTrips() {
        val new = insertUnderPanel(text("t_nl", "Multi", "line one\nline two\twith tab"))
        assertCommentPreserved(new)
        val re = recompiled(new).requireDocument().requireNode("t_nl")
        val kind = assertNotNull(re.kind as? DesignNodeKind.Text)
        val body = kind.content?.defaultText ?: (kind.characters as? Bindable.Value)?.value
        assertEquals("line one\nline two\twith tab", body, "newline/tab in text corrupted\n$new")
    }

    @Test
    fun emptyNameRoundTrips() {
        val new = insertUnderPanel(rect("r_empty", ""))
        assertCommentPreserved(new)
        val node = recompiled(new).requireDocument().requireNode("r_empty")
        assertEquals("", node.name, "empty name should stay empty\n$new")
    }

    @Test
    fun tokenAndHexFillsInNestedFrameRoundTrip() {
        val child = rect(
            "r_fill",
            "Filled",
            fills = listOf(
                DesignPaint.Solid(color = DesignColor(0xFF00FF00L).bindable()),
                DesignPaint.Solid(color = Bindable.VarRef("color.accent")),
            ),
        )
        val subtree = frame("wrap", "Wrapper", children = listOf(child))
        val new = insertUnderPanel(subtree)
        assertCommentPreserved(new)
        val re = recompiled(new).requireDocument()
        assertTrue(re.requireNode("wrap").children.any { it.id == "r_fill" })
        val fills = assertNotNull(re.requireNode("r_fill").fills)
        assertEquals(2, fills.size)
        assertEquals(DesignColor(0xFF00FF00L), ((fills[0] as DesignPaint.Solid).color as Bindable.Value).value)
        assertEquals("color.accent", ((fills[1] as DesignPaint.Solid).color as Bindable.VarRef).id)
    }

    @Test
    fun nameThatIsAReservedYamlWordRoundTrips() {
        // "true"/"null" as names would be re-read as bool/null if written unquoted.
        val new = insertUnderPanel(rect("r_true", "true"))
        assertCommentPreserved(new)
        assertEquals("true", recompiled(new).requireDocument().requireNode("r_true").name)
    }

    @Test
    fun nameThatLooksNumericRoundTrips() {
        val new = insertUnderPanel(rect("r_num", "123.45"))
        assertCommentPreserved(new)
        assertEquals("123.45", recompiled(new).requireDocument().requireNode("r_num").name)
    }

    @Test
    fun absoluteGeometryOrderAndConstraintsRoundTrip() {
        val node = DesignNode(
            id = "r_geo",
            type = "shape",
            kind = DesignNodeKind.Shape(shape = ShapeType.Ellipse),
            name = "Geo",
            order = 30,
            position = io.aequicor.visualization.engine.ir.model.DesignPoint(x = 12.5, y = 40.0),
            layoutChild = io.aequicor.visualization.engine.ir.model.DesignLayoutChild(absolute = true),
            constraints = io.aequicor.visualization.engine.ir.model.DesignConstraints(
                horizontal = io.aequicor.visualization.engine.ir.model.HorizontalConstraint.Right,
                vertical = io.aequicor.visualization.engine.ir.model.VerticalConstraint.Bottom,
            ),
            sizing = DesignSizing(horizontal = SizingMode.Fixed, vertical = SizingMode.Fixed),
            size = DesignSize(width = 50.0, height = 50.0),
        )
        val new = insertUnderPanel(node)
        assertCommentPreserved(new)
        val re = recompiled(new).requireDocument().requireNode("r_geo")
        // CNL authors sibling order positionally (no explicit `order`), so the inserted node —
        // panel's sole child — resolves to the first positional slot rather than its authored 30.
        assertEquals(10, re.order, "positional order\n$new")
        assertTrue(re.layoutChild.absolute, "absolute lost\n$new")
        assertEquals(12.5, re.position?.x?.orZero)
        assertEquals(40.0, re.position?.y?.orZero)
        assertEquals(io.aequicor.visualization.engine.ir.model.HorizontalConstraint.Right, re.constraints.horizontal)
        assertEquals(io.aequicor.visualization.engine.ir.model.VerticalConstraint.Bottom, re.constraints.vertical)
        assertEquals(ShapeType.Ellipse, (re.kind as DesignNodeKind.Shape).shape)
    }

    private val docWithChildren = """
        ---
        screen: s
        sourceLocale: en-US
        ---

        # Screen id root name «Screen»

        ## AutoLayout: Panel id panel column

        ### Shape: A id child_a

        ### Shape: B id child_b
    """.trimIndent() + "\n"

    @Test
    fun insertAfterSiblingPlacesBetweenChildren() {
        val compiled = compileForEdit(docWithChildren)
        assertNoErrorsLocal(compiled, docWithChildren)
        val result = applySlmEdit(
            docWithChildren,
            InsertChildSubtree("panel", rect("child_mid", "Mid"), afterSiblingId = "child_a"),
            compiled,
        )
        val new = result.requireNewSource()
        val re = recompiled(new).requireDocument().requireNode("panel")
        val ids = re.children.map { it.id }
        assertEquals(listOf("child_a", "child_mid", "child_b"), ids, "midpoint insertion order wrong: $ids\n$new")
    }

    @Test
    fun componentTypedNodeRoundTripsAsInTreeFrame() {
        // Regression for the fixed defect: a node whose free-form `type` is "component" is a live
        // in-tree Frame (its kind comes from `node.type` via the merge, not the heading). If the
        // section writer emitted a `### Component:` heading the extractor would read it as a
        // component *definition* marker and lift the node out of the page tree. NodeSectionWriter
        // now falls back to the neutral `Node:` prefix for component-family types, so the node
        // stays in-tree and round-trips: same id, type and parent.
        val node = DesignNode(
            id = "c_node",
            type = "component",
            kind = DesignNodeKind.Frame,
            name = "Widget",
        )
        val new = insertUnderPanel(node)
        assertTrue("### Component: Widget" !in new, "must not emit a component-definition heading:\n$new")
        assertTrue("### Frame:" in new && "name «Widget»" in new, "expected an in-tree Frame heading:\n$new")
        val re = recompiled(new).requireDocument()
        val reNode = assertNotNull(re.nodeById("c_node"), "component-typed node must survive in the page tree\n$new")
        // In a page body every Frame-kind node is an in-tree frame; the free-form "component" type
        // normalizes to "frame" (definitions are lifted out and emitted separately, never re-lifted).
        assertEquals("frame", reNode.type, "in-tree component-typed frame normalizes to the frame type\n$new")
        assertTrue(reNode.kind is DesignNodeKind.Frame, "component-typed node stays a Frame kind\n$new")
        assertTrue(re.requireNode("panel").children.any { it.id == "c_node" }, "node must be a child of panel\n$new")
    }

    private fun assertNoErrorsLocal(r: io.aequicor.visualization.engine.frontend.SlmCompileResult, src: String) {
        val errors = r.diagnostics.filter { it.severity == DesignSeverity.Error }
        assertTrue(errors.isEmpty(), "setup errors: ${errors.joinToString { it.message }}\n$src")
    }

    @Test
    fun deleteLeavesAuthoredCommentAndSiblingsByteIntact() {
        val compiled = compileForEdit(doc)
        val result = applySlmEdit(doc, DeleteSection("panel"), compiled)
        val new = result.requireNewSource()
        assertCommentPreserved(new)
        assertTrue("## Frame: Panel" !in new, "deleted section still present\n$new")
        val re = recompiled(new).requireDocument()
        assertTrue(re.nodeById("panel") == null, "panel still compiles after delete")
        assertNotNull(re.nodeById("sidebar"))
    }
}
