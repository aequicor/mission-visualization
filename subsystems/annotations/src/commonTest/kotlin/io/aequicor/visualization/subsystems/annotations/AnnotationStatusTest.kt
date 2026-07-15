package io.aequicor.visualization.subsystems.annotations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AnnotationStatusTest {

    private fun annotation(kind: AnnotationKind, id: String = "ann-1") = Annotation(
        id = id,
        kind = kind,
        anchor = AnnotationAnchor.FreePoint(1.0, 2.0),
        body = AnnotationBody("Fix it"),
    )

    @Test
    fun issueStatusChangesWhileCommentIgnoresStatus() {
        val issueLayer = AnnotationLayer("screen.layout.md", listOf(annotation(AnnotationKind.Issue)))
        assertEquals(
            AnnotationStatus.InReview,
            issueLayer.setAnnotationStatus("ann-1", AnnotationStatus.InReview).annotations.single().status,
        )

        val commentLayer = AnnotationLayer("screen.layout.md", listOf(annotation(AnnotationKind.Note)))
        assertSame(commentLayer, commentLayer.setAnnotationStatus("ann-1", AnnotationStatus.Closed))
    }

    @Test
    fun promptIncludesOpenAndReviewIssuesButSkipsClosedOnes() {
        val layer = AnnotationLayer(
            "screen.layout.md",
            listOf(
                annotation(AnnotationKind.Issue, "open"),
                annotation(AnnotationKind.Issue, "review").copy(status = AnnotationStatus.InReview),
                annotation(AnnotationKind.Issue, "closed").copy(status = AnnotationStatus.Closed),
            ),
        )

        val prompt = AnnotationPromptExporter.exportIssues(listOf(layer), ExportScope.WholeDocument) { null }

        assertTrue("Status: open" in prompt)
        assertTrue("Status: in review" in prompt)
        assertTrue("3. Screen:" !in prompt)
        assertTrue("closed" !in prompt)
    }
}
