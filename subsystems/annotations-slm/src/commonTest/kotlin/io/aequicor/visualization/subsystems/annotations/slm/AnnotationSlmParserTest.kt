package io.aequicor.visualization.subsystems.annotations.slm

import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnnotationSlmParserTest {

    @Test
    fun parsesThePlanExample() {
        val source = """
            ## issue @node-abc123 +@node-def456 {id=ann-1}
            Контраст текста ниже нормы, поправить фон.
            ![](data:image/png;base64,AAAA)

            ## note @(120,340) {id=ann-2}
            Здесь свободный комментарий, откреплён от узла.
        """.trimIndent()

        val result = AnnotationSlmParser.parse("overview.annotations.md", source)

        assertTrue(result.warnings.isEmpty())
        assertFalse(result.needsRewrite)
        assertEquals("overview.annotations.md", result.layer.screenFileName)
        assertEquals(2, result.layer.annotations.size)

        val issue = result.layer.annotations[0]
        assertEquals("ann-1", issue.id)
        assertEquals(AnnotationKind.Issue, issue.kind)
        assertEquals(AnnotationAnchor.NodeAnchor("node-abc123"), issue.anchor)
        assertEquals(listOf("node-def456"), issue.references)
        assertEquals("Контраст текста ниже нормы, поправить фон.", issue.body.text)
        assertEquals("data:image/png;base64,AAAA", issue.image?.source)
        assertEquals(0.0, issue.image?.width)
        assertEquals(0.0, issue.image?.height)

        val note = result.layer.annotations[1]
        assertEquals("ann-2", note.id)
        assertEquals(AnnotationKind.Note, note.kind)
        assertEquals(AnnotationAnchor.FreePoint(120.0, 340.0), note.anchor)
        assertNull(note.image)
    }

    @Test
    fun parsesNodeAnchorOffsetAndExpandedFlag() {
        val result = AnnotationSlmParser.parse(
            "overview.annotations.md",
            "## issue @node-a(8,-12.5) [expanded] {id=ann-1}\nBody.\n",
        )
        val annotation = result.layer.annotations.single()
        assertEquals(AnnotationAnchor.NodeAnchor("node-a", 8.0, -12.5), annotation.anchor)
        assertTrue(annotation.defaultExpanded)
    }

    @Test
    fun parsesImageDimensionsFromAltText() {
        val result = AnnotationSlmParser.parse(
            "overview.annotations.md",
            "## note @node-a {id=ann-1}\n![320x200.5](data:image/png;base64,AAAA)\n",
        )
        val image = result.layer.annotations.single().image
        assertEquals(320.0, image?.width)
        assertEquals(200.5, image?.height)
    }

    @Test
    fun malformedSectionIsSkippedWithWarningOthersSurvive() {
        val source = """
            ## note @node-a {id=ann-1}
            Fine.

            ## banana @node-b {id=ann-2}
            Unknown kind, skipped.

            ## issue node-missing-at {id=ann-3}
            Bad anchor, skipped.

            ## issue @node-c {id=ann-4}
            Also fine.
        """.trimIndent()

        val result = AnnotationSlmParser.parse("overview.annotations.md", source)

        assertEquals(listOf("ann-1", "ann-4"), result.layer.annotations.map { it.id })
        assertEquals(2, result.warnings.size)
        assertEquals(4, result.warnings[0].line)
        assertEquals(7, result.warnings[1].line)
    }

    @Test
    fun sectionWithoutIdGetsDeterministicSynthesizedIdAndFlagsRewrite() {
        val source = """
            ## note @node-a
            No id marker here.

            ## issue @node-b {id=ann-x}
            Explicit id.
        """.trimIndent()

        val result = AnnotationSlmParser.parse("overview.annotations.md", source)

        assertTrue(result.needsRewrite)
        assertEquals(listOf("ann-1", "ann-x"), result.layer.annotations.map { it.id })
        // Deterministic: parsing again yields the same ids.
        assertEquals(result.layer, AnnotationSlmParser.parse("overview.annotations.md", source).layer)
    }

    @Test
    fun synthesizedIdAvoidsCollisionWithExplicitIds() {
        val source = """
            ## note @node-a {id=ann-2}
            Explicit id squatting the synth slot.

            ## note @node-b
            Needs a synthesized id; ann-2 is taken.
        """.trimIndent()

        val result = AnnotationSlmParser.parse("overview.annotations.md", source)

        assertTrue(result.needsRewrite)
        assertEquals(listOf("ann-2", "ann-2-2"), result.layer.annotations.map { it.id })
    }

    @Test
    fun duplicateExplicitIdIsSkippedWithWarning() {
        val source = """
            ## note @node-a {id=ann-1}
            First wins.

            ## issue @node-b {id=ann-1}
            Duplicate, skipped.
        """.trimIndent()

        val result = AnnotationSlmParser.parse("overview.annotations.md", source)

        assertEquals(1, result.layer.annotations.size)
        assertEquals(AnnotationKind.Note, result.layer.annotations.single().kind)
        assertEquals(1, result.warnings.size)
        assertEquals(4, result.warnings.single().line)
    }

    @Test
    fun preambleBeforeFirstSectionIsIgnored() {
        val source = """
            # Overview — review notes

            Some prose that is not an annotation.

            ## note @node-a {id=ann-1}
            Real annotation.
        """.trimIndent()

        val result = AnnotationSlmParser.parse("overview.annotations.md", source)

        assertTrue(result.warnings.isEmpty())
        assertEquals(listOf("ann-1"), result.layer.annotations.map { it.id })
    }

    @Test
    fun blankAndWhitespaceOnlySourcesYieldEmptyLayers() {
        assertEquals(0, AnnotationSlmParser.parse("a.annotations.md", "").layer.annotations.size)
        assertEquals(0, AnnotationSlmParser.parse("a.annotations.md", "\n\n  \n").layer.annotations.size)
    }

    @Test
    fun parsesAuthorFromAttributeBlock() {
        val result = AnnotationSlmParser.parse(
            "overview.annotations.md",
            "## note @node-a {id=ann-1, author=Jane Doe}\nSigned.\n",
        )
        assertEquals("Jane Doe", result.layer.annotations.single().author)
    }
}
