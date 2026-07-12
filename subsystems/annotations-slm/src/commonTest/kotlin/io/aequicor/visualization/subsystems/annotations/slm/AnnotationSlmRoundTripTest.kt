package io.aequicor.visualization.subsystems.annotations.slm

import io.aequicor.visualization.subsystems.annotations.Annotation
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationBody
import io.aequicor.visualization.subsystems.annotations.AnnotationImage
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import io.aequicor.visualization.subsystems.annotations.AnnotationLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Round-trip contract for the examples and rules published in `SKILLS/SLM-annotations.md`. */
class AnnotationSlmRoundTripTest {

    private fun roundTrip(layer: AnnotationLayer): AnnotationSlmParseResult =
        AnnotationSlmParser.parse(layer.screenFileName, AnnotationSlmWriter.write(layer))

    private fun assertStable(layer: AnnotationLayer) {
        val result = roundTrip(layer)
        assertEquals(layer, result.layer)
        assertTrue(result.warnings.isEmpty(), "unexpected warnings: ${result.warnings}")
        assertFalse(result.needsRewrite, "written files carry explicit ids, no rewrite needed")
    }

    @Test
    fun emptyLayerRoundTrips() {
        assertStable(AnnotationLayer("overview.annotations.md"))
    }

    @Test
    fun nodeAnchorWithoutOffsetRoundTrips() {
        assertStable(
            AnnotationLayer(
                "overview.annotations.md",
                listOf(
                    Annotation(
                        id = "ann-1",
                        kind = AnnotationKind.Note,
                        anchor = AnnotationAnchor.NodeAnchor("node-abc123"),
                        body = AnnotationBody("Simple note."),
                    ),
                ),
            ),
        )
    }

    @Test
    fun nodeAnchorWithOffsetRoundTrips() {
        assertStable(
            AnnotationLayer(
                "overview.annotations.md",
                listOf(
                    Annotation(
                        id = "ann-1",
                        kind = AnnotationKind.Issue,
                        anchor = AnnotationAnchor.NodeAnchor("node-abc123", offsetX = 8.0, offsetY = -12.5),
                        body = AnnotationBody("Offset badge."),
                    ),
                ),
            ),
        )
    }

    @Test
    fun freePointAnchorRoundTrips() {
        assertStable(
            AnnotationLayer(
                "overview.annotations.md",
                listOf(
                    Annotation(
                        id = "ann-2",
                        kind = AnnotationKind.Note,
                        anchor = AnnotationAnchor.FreePoint(120.0, 340.25),
                        body = AnnotationBody("Free-floating comment."),
                    ),
                ),
            ),
        )
    }

    @Test
    fun referencesRoundTrip() {
        assertStable(
            AnnotationLayer(
                "overview.annotations.md",
                listOf(
                    Annotation(
                        id = "ann-1",
                        kind = AnnotationKind.Issue,
                        anchor = AnnotationAnchor.NodeAnchor("node-abc123"),
                        body = AnnotationBody("Applies to two more nodes."),
                        references = listOf("node-def456", "node-ghi789"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun multilineBodyWithInternalBlankLineRoundTrips() {
        assertStable(
            AnnotationLayer(
                "overview.annotations.md",
                listOf(
                    Annotation(
                        id = "ann-1",
                        kind = AnnotationKind.Note,
                        anchor = AnnotationAnchor.NodeAnchor("node-a"),
                        body = AnnotationBody("First paragraph.\n\nSecond paragraph\nwith a wrapped line."),
                    ),
                ),
            ),
        )
    }

    @Test
    fun emptyBodyRoundTrips() {
        assertStable(
            AnnotationLayer(
                "overview.annotations.md",
                listOf(
                    Annotation(
                        id = "ann-1",
                        kind = AnnotationKind.Issue,
                        anchor = AnnotationAnchor.NodeAnchor("node-a"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun imageDataUriRoundTrips() {
        assertStable(
            AnnotationLayer(
                "overview.annotations.md",
                listOf(
                    Annotation(
                        id = "ann-1",
                        kind = AnnotationKind.Issue,
                        anchor = AnnotationAnchor.NodeAnchor("node-a"),
                        body = AnnotationBody("See the mock."),
                        image = AnnotationImage("data:image/png;base64,iVBORw0KGgo=", width = 320.0, height = 200.5),
                    ),
                ),
            ),
        )
    }

    @Test
    fun imageWithoutBodyRoundTrips() {
        assertStable(
            AnnotationLayer(
                "overview.annotations.md",
                listOf(
                    Annotation(
                        id = "ann-1",
                        kind = AnnotationKind.Note,
                        anchor = AnnotationAnchor.FreePoint(0.0, 0.0),
                        image = AnnotationImage("data:image/png;base64,AAAA", width = 64.0, height = 64.0),
                    ),
                ),
            ),
        )
    }

    @Test
    fun expandedFlagRoundTrips() {
        assertStable(
            AnnotationLayer(
                "overview.annotations.md",
                listOf(
                    Annotation(
                        id = "ann-1",
                        kind = AnnotationKind.Issue,
                        anchor = AnnotationAnchor.NodeAnchor("node-a"),
                        body = AnnotationBody("Open by default."),
                        defaultExpanded = true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun authorRoundTrips() {
        assertStable(
            AnnotationLayer(
                "overview.annotations.md",
                listOf(
                    Annotation(
                        id = "ann-1",
                        kind = AnnotationKind.Note,
                        anchor = AnnotationAnchor.NodeAnchor("node-a"),
                        body = AnnotationBody("Signed."),
                        author = "Jane Doe",
                    ),
                ),
            ),
        )
    }

    @Test
    fun everyFieldAtOnceAcrossMultipleSectionsRoundTrips() {
        assertStable(
            AnnotationLayer(
                "telemetry.annotations.md",
                listOf(
                    Annotation(
                        id = "ann-1",
                        kind = AnnotationKind.Issue,
                        anchor = AnnotationAnchor.NodeAnchor("node-abc123", offsetX = -4.0, offsetY = 16.0),
                        body = AnnotationBody("Contrast is below the norm.\nFix the background."),
                        image = AnnotationImage("data:image/png;base64,iVBORw0KGgo=", width = 120.0, height = 80.0),
                        defaultExpanded = true,
                        references = listOf("node-def456"),
                        author = "Reviewer",
                    ),
                    Annotation(
                        id = "ann-2",
                        kind = AnnotationKind.Note,
                        anchor = AnnotationAnchor.FreePoint(120.0, 340.0),
                        body = AnnotationBody("Free-floating comment, detached from a node."),
                    ),
                ),
            ),
        )
    }

    @Test
    fun bodyLineStartingWithHeaderPrefixRoundTrips() {
        assertStable(
            AnnotationLayer(
                "overview.annotations.md",
                listOf(
                    Annotation(
                        id = "ann-1",
                        kind = AnnotationKind.Note,
                        anchor = AnnotationAnchor.NodeAnchor("node-a"),
                        body = AnnotationBody("Intro\n## Header in body\nTail"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun bodyLineShapedLikeMarkdownImageRoundTrips() {
        assertStable(
            AnnotationLayer(
                "overview.annotations.md",
                listOf(
                    Annotation(
                        id = "ann-1",
                        kind = AnnotationKind.Note,
                        anchor = AnnotationAnchor.NodeAnchor("node-a"),
                        body = AnnotationBody("See ref:\n![screenshot](assets/shot.png)"),
                        image = null,
                    ),
                ),
            ),
        )
    }

    @Test
    fun bodyWithLeadingBackslashBeforeStructuralLineRoundTrips() {
        assertStable(
            AnnotationLayer(
                "overview.annotations.md",
                listOf(
                    Annotation(
                        id = "ann-1",
                        kind = AnnotationKind.Note,
                        anchor = AnnotationAnchor.NodeAnchor("node-a"),
                        body = AnnotationBody("\\## already escaped\n\\\\## doubly escaped\n\\plain backslash"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun imageShapedBodyLineCoexistsWithRealImage() {
        assertStable(
            AnnotationLayer(
                "overview.annotations.md",
                listOf(
                    Annotation(
                        id = "ann-1",
                        kind = AnnotationKind.Issue,
                        anchor = AnnotationAnchor.NodeAnchor("node-a"),
                        body = AnnotationBody("Compare with:\n![old](assets/old.png)"),
                        image = AnnotationImage("data:image/png;base64,AAAA", width = 64.0, height = 64.0),
                    ),
                ),
            ),
        )
    }

    @Test
    fun nodeIdWithSpacesAndParensRoundTrips() {
        assertStable(
            AnnotationLayer(
                "overview.annotations.md",
                listOf(
                    Annotation(
                        id = "ann-1",
                        kind = AnnotationKind.Issue,
                        anchor = AnnotationAnchor.NodeAnchor("hero (main)", offsetX = 8.0, offsetY = -12.0),
                        body = AnnotationBody("Weird but legal explicit SLM id."),
                        references = listOf("other node", "plain-node"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun nonBmpNodeIdRoundTrips() {
        assertStable(
            AnnotationLayer(
                "overview.annotations.md",
                listOf(
                    Annotation(
                        id = "ann-1",
                        kind = AnnotationKind.Note,
                        anchor = AnnotationAnchor.NodeAnchor("🚀-hero"),
                        body = AnnotationBody("Emoji id."),
                    ),
                ),
            ),
        )
    }

    @Test
    fun nodeIdWithQuotesAndBackslashesRoundTrips() {
        assertStable(
            AnnotationLayer(
                "overview.annotations.md",
                listOf(
                    Annotation(
                        id = "ann-1",
                        kind = AnnotationKind.Note,
                        anchor = AnnotationAnchor.NodeAnchor("""he said "hi" \ done"""),
                        body = AnnotationBody("Escapes inside quotes."),
                    ),
                ),
            ),
        )
    }

    @Test
    fun writerCanonicalizesBodyBlankLineFraming() {
        fun layerWithBody(text: String) = AnnotationLayer(
            "overview.annotations.md",
            listOf(
                Annotation(
                    id = "ann-1",
                    kind = AnnotationKind.Note,
                    anchor = AnnotationAnchor.NodeAnchor("node-a"),
                    body = AnnotationBody(text),
                ),
            ),
        )

        // Framing blank lines are section separators in the format; the writer folds
        // them away, so write ∘ parse ∘ write is stable (no phantom diffs on reload).
        assertEquals(AnnotationSlmWriter.write(layerWithBody("Body.")), AnnotationSlmWriter.write(layerWithBody("Body.\n")))
        assertEquals(AnnotationSlmWriter.write(layerWithBody("")), AnnotationSlmWriter.write(layerWithBody(" \n ")))
        assertEquals("Body.", roundTrip(layerWithBody("\n\nBody.\n")).layer.annotations.single().body.text)
        assertStable(roundTrip(layerWithBody("Body.\n\n")).layer)
    }

    @Test
    fun writerEmitsCanonicalFormat() {
        val layer = AnnotationLayer(
            "overview.annotations.md",
            listOf(
                Annotation(
                    id = "ann-1",
                    kind = AnnotationKind.Issue,
                    anchor = AnnotationAnchor.NodeAnchor("node-abc123"),
                    body = AnnotationBody("Contrast too low."),
                    references = listOf("node-def456"),
                ),
                Annotation(
                    id = "ann-2",
                    kind = AnnotationKind.Note,
                    anchor = AnnotationAnchor.FreePoint(120.0, 340.0),
                    body = AnnotationBody("Free comment."),
                ),
            ),
        )
        assertEquals(
            """
            ## issue @node-abc123 +@node-def456 {id=ann-1}
            Contrast too low.

            ## note @(120,340) {id=ann-2}
            Free comment.
            """.trimIndent() + "\n",
            AnnotationSlmWriter.write(layer),
        )
    }

    @Test
    fun integralCoordinatesAreWrittenWithoutTrailingZero() {
        val annotation = Annotation(
            id = "ann-1",
            kind = AnnotationKind.Note,
            anchor = AnnotationAnchor.NodeAnchor("node-a", offsetX = 8.0, offsetY = -12.0),
        )
        assertEquals("## note @node-a(8,-12) {id=ann-1}\n", AnnotationSlmWriter.renderSection(annotation))
    }
}
