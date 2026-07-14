package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.presentation.nestedSelectionTargetForTap
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NestedCanvasSelectionTest {
    private val leaf = frame("leaf")
    private val inner = frame("inner", leaf)
    private val nested = frame("nested", inner)
    private val sibling = frame("sibling")
    private val root = frame("root", nested, sibling)
    private val document = DesignDocument(pages = listOf(DesignPage(id = "page", children = listOf(root))))

    @Test
    fun singleTapInsideSelectedNestedContainerKeepsContainerSelected() {
        assertEquals(
            "nested",
            document.nestedSelectionTargetForTap(setOf("nested"), hitId = "leaf", doubleTap = false),
        )
    }

    @Test
    fun doubleTapDrillsExactlyOneLevelTowardDeepestHit() {
        assertEquals(
            "inner",
            document.nestedSelectionTargetForTap(setOf("nested"), hitId = "leaf", doubleTap = true),
        )
    }

    @Test
    fun directChildStillRequiresDoubleTapBeforeItBecomesSelected() {
        assertEquals(
            "nested",
            document.nestedSelectionTargetForTap(setOf("nested"), hitId = "inner", doubleTap = false),
        )
        assertEquals(
            "inner",
            document.nestedSelectionTargetForTap(setOf("nested"), hitId = "inner", doubleTap = true),
        )
    }

    @Test
    fun doubleTapDoesNotDrillIntoALockedIntermediateContainer() {
        // The single-click hit-test refuses locked nodes, so drilling must not reach one either —
        // otherwise a double-tap selects a container the user can never pick directly on canvas,
        // leaving a dead, handle-suppressed selection.
        val lockedInner = inner.copy(locked = true)
        val nestedLock = frame("nestedLock", lockedInner)
        val lockRoot = frame("lockRoot", nestedLock)
        val lockDocument = DesignDocument(pages = listOf(DesignPage(id = "page", children = listOf(lockRoot))))

        assertNull(
            lockDocument.nestedSelectionTargetForTap(setOf("nestedLock"), hitId = "leaf", doubleTap = true),
        )
        // A single tap still keeps the container selected regardless of the locked intermediate.
        assertEquals(
            "nestedLock",
            lockDocument.nestedSelectionTargetForTap(setOf("nestedLock"), hitId = "leaf", doubleTap = false),
        )
    }

    @Test
    fun rootMultiSelectionAndUnrelatedHitsUseNormalCanvasSelection() {
        assertNull(document.nestedSelectionTargetForTap(setOf("root"), hitId = "leaf", doubleTap = false))
        assertNull(document.nestedSelectionTargetForTap(setOf("nested", "sibling"), hitId = "leaf", doubleTap = false))
        assertNull(document.nestedSelectionTargetForTap(setOf("nested"), hitId = "sibling", doubleTap = false))
        assertNull(document.nestedSelectionTargetForTap(setOf("nested"), hitId = "nested", doubleTap = true))
    }

    private fun frame(id: String, vararg children: DesignNode): DesignNode = DesignNode(
        id = id,
        type = "frame",
        kind = DesignNodeKind.Frame,
        children = children.toList(),
    )
}
