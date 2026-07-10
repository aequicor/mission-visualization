package io.aequicor.visualization.subsystems.annotations.slm

import io.aequicor.visualization.subsystems.annotations.Annotation
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationBody
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AnnotationSlmPatcherTest {

    // Deliberately non-canonical spacing/body details in untouched sections — the patcher
    // must preserve their bytes exactly, never re-serialize them.
    private val source = """
        # Overview — review notes

        ## issue @node-a {id=ann-1}
        First issue,  with odd   spacing preserved.

        ## note @(10,20) {id=ann-2}
        Middle note.
        Second line.

        ## issue @node-c(4,-6) [expanded] {id=ann-3}
        Last issue.
    """.trimIndent() + "\n"

    @Test
    fun upsertUpdatesMiddleSectionAndKeepsOtherBytesExact() {
        val updated = Annotation(
            id = "ann-2",
            kind = AnnotationKind.Issue,
            anchor = AnnotationAnchor.FreePoint(99.0, 100.5),
            body = AnnotationBody("Rewritten middle."),
        )

        val patched = AnnotationSlmPatcher.upsertSection(source, updated)

        assertEquals(
            """
            # Overview — review notes

            ## issue @node-a {id=ann-1}
            First issue,  with odd   spacing preserved.

            ## issue @(99,100.5) {id=ann-2}
            Rewritten middle.

            ## issue @node-c(4,-6) [expanded] {id=ann-3}
            Last issue.
            """.trimIndent() + "\n",
            patched,
        )
    }

    @Test
    fun upsertUpdatesLastSectionWithoutTrailingBlankLine() {
        val updated = Annotation(
            id = "ann-3",
            kind = AnnotationKind.Note,
            anchor = AnnotationAnchor.NodeAnchor("node-c"),
            body = AnnotationBody("Downgraded to a note."),
        )

        val patched = AnnotationSlmPatcher.upsertSection(source, updated)

        assertEquals(
            """
            # Overview — review notes

            ## issue @node-a {id=ann-1}
            First issue,  with odd   spacing preserved.

            ## note @(10,20) {id=ann-2}
            Middle note.
            Second line.

            ## note @node-c {id=ann-3}
            Downgraded to a note.
            """.trimIndent() + "\n",
            patched,
        )
    }

    @Test
    fun upsertAppendsUnknownIdAsNewSection() {
        val added = Annotation(
            id = "ann-4",
            kind = AnnotationKind.Issue,
            anchor = AnnotationAnchor.NodeAnchor("node-d"),
            body = AnnotationBody("Brand new."),
        )

        val patched = AnnotationSlmPatcher.upsertSection(source, added)

        assertEquals(source + "\n## issue @node-d {id=ann-4}\nBrand new.\n", patched)
    }

    @Test
    fun upsertIntoEmptySourceWritesJustTheSection() {
        val added = Annotation(
            id = "ann-1",
            kind = AnnotationKind.Note,
            anchor = AnnotationAnchor.FreePoint(1.0, 2.0),
            body = AnnotationBody("Only one."),
        )
        assertEquals("## note @(1,2) {id=ann-1}\nOnly one.\n", AnnotationSlmPatcher.upsertSection("", added))
    }

    @Test
    fun upsertAppendsWithSingleBlankLineWhenSourceLacksTrailingNewline() {
        val added = Annotation(
            id = "ann-2",
            kind = AnnotationKind.Note,
            anchor = AnnotationAnchor.NodeAnchor("node-b"),
        )
        val patched = AnnotationSlmPatcher.upsertSection("## note @node-a {id=ann-1}\nBody.", added)
        assertEquals("## note @node-a {id=ann-1}\nBody.\n\n## note @node-b {id=ann-2}\n", patched)
    }

    @Test
    fun deleteRemovesMiddleSectionAndKeepsOtherBytesExact() {
        val patched = AnnotationSlmPatcher.deleteSection(source, "ann-2")

        assertEquals(
            """
            # Overview — review notes

            ## issue @node-a {id=ann-1}
            First issue,  with odd   spacing preserved.

            ## issue @node-c(4,-6) [expanded] {id=ann-3}
            Last issue.
            """.trimIndent() + "\n",
            patched,
        )
    }

    @Test
    fun deleteRemovesLastSectionAndTrimsDanglingSeparator() {
        val patched = AnnotationSlmPatcher.deleteSection(source, "ann-3")

        assertEquals(
            """
            # Overview — review notes

            ## issue @node-a {id=ann-1}
            First issue,  with odd   spacing preserved.

            ## note @(10,20) {id=ann-2}
            Middle note.
            Second line.
            """.trimIndent() + "\n",
            patched,
        )
    }

    @Test
    fun deleteUnknownIdIsNoOp() {
        assertSame(source, AnnotationSlmPatcher.deleteSection(source, "ann-404"))
    }

    @Test
    fun deleteOnlySectionLeavesPreambleWithSingleTrailingNewline() {
        val single = "# Title\n\n## note @node-a {id=ann-1}\nBody.\n"
        assertEquals("# Title\n", AnnotationSlmPatcher.deleteSection(single, "ann-1"))
    }

    @Test
    fun patchedSourceStillParsesCleanly() {
        var patched = AnnotationSlmPatcher.upsertSection(
            source,
            Annotation(
                id = "ann-4",
                kind = AnnotationKind.Issue,
                anchor = AnnotationAnchor.NodeAnchor("node-d", 1.5, 2.0),
                body = AnnotationBody("Added by patch."),
                references = listOf("node-e"),
            ),
        )
        patched = AnnotationSlmPatcher.deleteSection(patched, "ann-1")

        val result = AnnotationSlmParser.parse("overview.annotations.md", patched)
        assertEquals(listOf("ann-2", "ann-3", "ann-4"), result.layer.annotations.map { it.id })
        assertEquals(0, result.warnings.size)
    }
}
