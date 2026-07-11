package io.aequicor.visualization.subsystems.annotations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AnnotationOperationsTest {

    private fun note(
        id: String = "a1",
        anchor: AnnotationAnchor = AnnotationAnchor.NodeAnchor("node-1", 4.0, -8.0),
        text: String = "hello",
    ): Annotation = Annotation(id = id, kind = AnnotationKind.Note, anchor = anchor, body = AnnotationBody(text))

    private fun layer(vararg annotations: Annotation): AnnotationLayer =
        AnnotationLayer(screenFileName = "overview.layout.md", annotations = annotations.toList())

    @Test
    fun addAppendsAnnotation() {
        val updated = layer(note("a1")).addAnnotation(note("a2", text = "second"))
        assertEquals(listOf("a1", "a2"), updated.annotations.map { it.id })
        assertEquals("second", updated.annotations.last().body.text)
    }

    @Test
    fun addReplacesExistingIdInPlace() {
        val updated = layer(note("a1"), note("a2"))
            .addAnnotation(note("a1", text = "replaced"))
        assertEquals(listOf("a1", "a2"), updated.annotations.map { it.id })
        assertEquals("replaced", updated.annotations.first().body.text)
    }

    @Test
    fun updateTextReplacesBody() {
        val updated = layer(note("a1")).updateAnnotationText("a1", "new text")
        assertEquals(AnnotationBody("new text"), updated.annotations.single().body)
    }

    @Test
    fun updateTextUnknownIdIsNoOp() {
        val original = layer(note("a1"))
        assertSame(original, original.updateAnnotationText("missing", "x"))
    }

    @Test
    fun updateTextNormalizesBlankLineFraming() {
        val updated = layer(note("a1")).updateAnnotationText("a1", "\nBody.\n\n")
        assertEquals(AnnotationBody("Body."), updated.annotations.single().body)
    }

    @Test
    fun updateTextWhitespaceOnlyBecomesEmpty() {
        val updated = layer(note("a1")).updateAnnotationText("a1", " \n\t\n")
        assertEquals(AnnotationBody(""), updated.annotations.single().body)
    }

    @Test
    fun updateTextKeepsInternalBlankLinesAndWhitespace() {
        val updated = layer(note("a1")).updateAnnotationText("a1", "First.\n\nSecond  spaced.")
        assertEquals(AnnotationBody("First.\n\nSecond  spaced."), updated.annotations.single().body)
    }

    @Test
    fun setKindSwitchesNoteToIssue() {
        val updated = layer(note("a1")).setAnnotationKind("a1", AnnotationKind.Issue)
        assertEquals(AnnotationKind.Issue, updated.annotations.single().kind)
    }

    @Test
    fun attachImageSetsImage() {
        val image = AnnotationImage("data:image/png;base64,AAA", 120.0, 80.0)
        val updated = layer(note("a1")).attachAnnotationImage("a1", image)
        assertEquals(image, updated.annotations.single().image)
    }

    @Test
    fun detachImageClearsImage() {
        val image = AnnotationImage("data:image/png;base64,AAA", 120.0, 80.0)
        val updated = layer(note("a1"))
            .attachAnnotationImage("a1", image)
            .detachAnnotationImage("a1")
        assertNull(updated.annotations.single().image)
    }

    @Test
    fun moveNodeAnchoredAnnotationChangesOffset() {
        val updated = layer(note("a1")).moveAnnotation("a1", 10.0, 20.0)
        assertEquals(
            AnnotationAnchor.NodeAnchor("node-1", 10.0, 20.0),
            updated.annotations.single().anchor,
        )
    }

    @Test
    fun moveFreePointAnnotationChangesPoint() {
        val updated = layer(note("a1", anchor = AnnotationAnchor.FreePoint(1.0, 2.0)))
            .moveAnnotation("a1", 30.0, 40.0)
        assertEquals(AnnotationAnchor.FreePoint(30.0, 40.0), updated.annotations.single().anchor)
    }

    @Test
    fun attachToNodeRepinsFreePoint() {
        val updated = layer(note("a1", anchor = AnnotationAnchor.FreePoint(1.0, 2.0)))
            .attachAnnotationToNode("a1", "node-9", offsetX = 3.0, offsetY = 4.0)
        assertEquals(
            AnnotationAnchor.NodeAnchor("node-9", 3.0, 4.0),
            updated.annotations.single().anchor,
        )
    }

    @Test
    fun detachAnchorConvertsNodeAnchorToFreePointAtResolvedPosition() {
        val updated = layer(note("a1"))
            .detachAnnotationAnchor("a1", AnnotationPoint(150.0, 60.0))
        assertEquals(AnnotationAnchor.FreePoint(150.0, 60.0), updated.annotations.single().anchor)
    }

    @Test
    fun detachAnchorOnFreePointIsNoOp() {
        val original = layer(note("a1", anchor = AnnotationAnchor.FreePoint(5.0, 6.0)))
        assertSame(original, original.detachAnnotationAnchor("a1", AnnotationPoint(0.0, 0.0)))
    }

    @Test
    fun moveFoldsNegativeZeroOffsets() {
        val updated = layer(note("a1")).moveAnnotation("a1", -0.0, 5.0)
        assertEquals(AnnotationAnchor.NodeAnchor("node-1", 0.0, 5.0), updated.annotations.single().anchor)
    }

    @Test
    fun attachToNodeFoldsNegativeZeroOffsets() {
        val updated = layer(note("a1")).attachAnnotationToNode("a1", "node-2", offsetX = -0.0, offsetY = -0.0)
        assertEquals(AnnotationAnchor.NodeAnchor("node-2", 0.0, 0.0), updated.annotations.single().anchor)
    }

    @Test
    fun attachToNodeDropsNewAnchorNodeFromReferences() {
        val updated = layer(note("a1").copy(references = listOf("node-9", "node-3")))
            .attachAnnotationToNode("a1", "node-9")
        val annotation = updated.annotations.single()
        assertEquals(AnnotationAnchor.NodeAnchor("node-9"), annotation.anchor)
        assertEquals(listOf("node-3"), annotation.references)
    }

    @Test
    fun detachFromDeletedNodesFreezesResolvedPositions() {
        val original = layer(
            note("a1"),
            note("a2", anchor = AnnotationAnchor.NodeAnchor("node-2", 1.0, 2.0)),
            note("a3", anchor = AnnotationAnchor.FreePoint(9.0, 9.0)),
        )
        val updated = original.detachAnnotationsFromNodes(setOf("node-1")) { annotation ->
            AnnotationPoint(500.0, 800.0).takeIf { annotation.id == "a1" }
        }
        assertEquals(AnnotationAnchor.FreePoint(500.0, 800.0), updated.annotations[0].anchor)
        assertEquals(AnnotationAnchor.NodeAnchor("node-2", 1.0, 2.0), updated.annotations[1].anchor)
        assertEquals(AnnotationAnchor.FreePoint(9.0, 9.0), updated.annotations[2].anchor)
    }

    @Test
    fun detachFromNodesWithUnresolvedPositionKeepsNodeAnchor() {
        val original = layer(note("a1"))
        assertSame(original, original.detachAnnotationsFromNodes(setOf("node-1")) { null })
    }

    @Test
    fun detachFromNodesWithEmptySetIsNoOp() {
        val original = layer(note("a1"))
        assertSame(original, original.detachAnnotationsFromNodes(emptySet()) { AnnotationPoint(0.0, 0.0) })
    }

    @Test
    fun addReferenceAppendsAndDeduplicates() {
        val updated = layer(note("a1"))
            .addAnnotationReference("a1", "node-2")
            .addAnnotationReference("a1", "node-3")
            .addAnnotationReference("a1", "node-2")
        assertEquals(listOf("node-2", "node-3"), updated.annotations.single().references)
    }

    @Test
    fun addReferenceOfTheAnchorsOwnNodeIsNoOp() {
        val original = layer(note("a1"))
        assertSame(original, original.addAnnotationReference("a1", "node-1"))
    }

    @Test
    fun removeReferenceDropsOnlyTheGivenId() {
        val updated = layer(note("a1").copy(references = listOf("node-2", "node-3")))
            .removeAnnotationReference("a1", "node-2")
        assertEquals(listOf("node-3"), updated.annotations.single().references)
    }

    @Test
    fun deleteRemovesAnnotation() {
        val updated = layer(note("a1"), note("a2")).deleteAnnotation("a1")
        assertEquals(listOf("a2"), updated.annotations.map { it.id })
    }

    @Test
    fun deleteUnknownIdIsNoOp() {
        val original = layer(note("a1"))
        assertSame(original, original.deleteAnnotation("missing"))
    }

    @Test
    fun operationsDoNotMutateOriginalLayer() {
        val original = layer(note("a1"))
        original.updateAnnotationText("a1", "changed")
        original.deleteAnnotation("a1")
        assertEquals("hello", original.annotations.single().body.text)
        assertTrue(original.annotations.size == 1)
    }
}
