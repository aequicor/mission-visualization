package io.aequicor.visualization.subsystems.annotations.slm

import io.aequicor.visualization.subsystems.annotations.Annotation
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationBody
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnnotationLayoutCommentsTest {

    private val comment = Annotation(
        id = "ann-1",
        kind = AnnotationKind.Note,
        anchor = AnnotationAnchor.NodeAnchor("hero"),
        body = AnnotationBody("Keep 8px -- and literal %2D.\nDo not close --> this block."),
    )

    @Test
    fun commentsLiveAfterFrontmatterAndRoundTripUnsafeHtmlText() {
        val source = "---\nschema: slm/1.0\n---\n\n# Screen\n"

        val written = AnnotationLayoutComments.upsert(source, comment)

        assertTrue(written.indexOf(AnnotationLayoutComments.StartMarker) > written.indexOf("schema:"))
        assertTrue(written.indexOf(AnnotationLayoutComments.StartMarker) < written.indexOf("# Screen"))
        assertFalse("Do not close --> this block." in written, "HTML terminator is encoded in storage")
        assertEquals(comment, AnnotationLayoutComments.parse("screen.layout.md", written).layer.annotations.single())
    }

    @Test
    fun deletingTheLastCommentRestoresTheOriginalLayoutBytes() {
        val source = "---\nschema: slm/1.0\n---\n\n# Screen\n"
        val written = AnnotationLayoutComments.upsert(source, comment)

        assertEquals(source, AnnotationLayoutComments.delete(written, comment.id))
    }
}
