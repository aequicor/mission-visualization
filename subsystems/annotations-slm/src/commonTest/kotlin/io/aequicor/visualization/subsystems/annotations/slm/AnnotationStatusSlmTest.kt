package io.aequicor.visualization.subsystems.annotations.slm

import io.aequicor.visualization.subsystems.annotations.Annotation
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import io.aequicor.visualization.subsystems.annotations.AnnotationLayer
import io.aequicor.visualization.subsystems.annotations.AnnotationStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class AnnotationStatusSlmTest {

    @Test
    fun nonDefaultIssueStatusRoundTripsAndLegacyIssueDefaultsToOpen() {
        val issue = Annotation(
            id = "ann-1",
            kind = AnnotationKind.Issue,
            anchor = AnnotationAnchor.FreePoint(1.0, 2.0),
            status = AnnotationStatus.InReview,
        )
        val text = AnnotationSlmWriter.write(AnnotationLayer("screen.layout.md", listOf(issue)))

        assertEquals("## issue @(1,2) {id=ann-1, status=in-review}\n", text)
        assertEquals(issue, AnnotationSlmParser.parse("screen.layout.md", text).layer.annotations.single())
        assertEquals(
            AnnotationStatus.Open,
            AnnotationSlmParser.parse("screen.layout.md", "## issue @(1,2) {id=legacy}\n")
                .layer.annotations.single().status,
        )
    }
}
