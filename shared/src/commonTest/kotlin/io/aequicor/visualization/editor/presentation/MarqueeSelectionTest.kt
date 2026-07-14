package io.aequicor.visualization.editor.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarqueeSelectionTest {

    @Test
    fun marqueeRectNormalizesDraggingInAnyDirection() {
        val rect = DocumentRect.fromCorners(100.0, 120.0, 20.0, 40.0)

        assertEquals(DocumentRect(left = 20.0, top = 40.0, right = 100.0, bottom = 120.0), rect)
    }

    @Test
    fun selectsOnlyVisibleUnlockedIntersectingBounds() {
        val marquee = DocumentRect.fromCorners(0.0, 0.0, 100.0, 100.0)
        val result = marqueeSelection(
            marquee,
            listOf(
                SelectableBounds("inside", DocumentRect.fromCorners(10.0, 10.0, 20.0, 20.0)),
                SelectableBounds("overlap", DocumentRect.fromCorners(90.0, 90.0, 120.0, 120.0)),
                SelectableBounds("locked", DocumentRect.fromCorners(10.0, 10.0, 20.0, 20.0), locked = true),
                SelectableBounds("hidden", DocumentRect.fromCorners(10.0, 10.0, 20.0, 20.0), visible = false),
                SelectableBounds("outside", DocumentRect.fromCorners(120.0, 120.0, 140.0, 140.0)),
            ),
        )

        assertEquals(setOf("inside", "overlap"), result)
    }

    @Test
    fun edgeTouchWithoutAreaDoesNotSelect() {
        val marquee = DocumentRect.fromCorners(0.0, 0.0, 100.0, 100.0)

        assertTrue(
            marqueeSelection(
                marquee,
                listOf(SelectableBounds("touching", DocumentRect.fromCorners(100.0, 40.0, 120.0, 60.0))),
            ).isEmpty(),
        )
    }

    @Test
    fun excludedRootContainerNeverJoinsMarqueeSelection() {
        val marquee = DocumentRect.fromCorners(20.0, 20.0, 80.0, 80.0)

        val result = marqueeSelection(
            marquee,
            listOf(
                SelectableBounds("root", DocumentRect.fromCorners(0.0, 0.0, 500.0, 500.0)),
                SelectableBounds("first", DocumentRect.fromCorners(30.0, 30.0, 40.0, 40.0)),
                SelectableBounds("second", DocumentRect.fromCorners(60.0, 60.0, 70.0, 70.0)),
            ),
            excludedIds = setOf("root"),
        )

        assertEquals(setOf("first", "second"), result)
    }

    @Test
    fun marqueeInsideComponentDoesNotSelectEnclosingLayers() {
        val marquee = DocumentRect.fromCorners(40.0, 40.0, 80.0, 80.0)

        val result = marqueeSelection(
            marquee,
            listOf(
                SelectableBounds("workspace", DocumentRect.fromCorners(0.0, 0.0, 500.0, 500.0)),
                SelectableBounds("panel-background", DocumentRect.fromCorners(20.0, 20.0, 120.0, 120.0)),
                SelectableBounds("inside", DocumentRect.fromCorners(50.0, 50.0, 65.0, 65.0)),
                SelectableBounds("crossed-edge", DocumentRect.fromCorners(70.0, 50.0, 90.0, 65.0)),
            ),
        )

        assertEquals(setOf("inside", "crossed-edge"), result)
    }

    @Test
    fun marqueeEqualToComponentBoundsCanStillSelectIt() {
        val bounds = DocumentRect.fromCorners(20.0, 20.0, 120.0, 120.0)

        assertEquals(
            setOf("component"),
            marqueeSelection(bounds, listOf(SelectableBounds("component", bounds))),
        )
    }

    @Test
    fun wholeContainerSelectionIncludesContainerAndImplementationLayers() {
        val marquee = DocumentRect.fromCorners(12.0, 12.0, 108.0, 108.0)
        val candidates = listOf(
            SelectableBounds(
                "list",
                DocumentRect.fromCorners(10.0, 10.0, 110.0, 110.0),
                parentId = "root",
                container = true,
            ),
            SelectableBounds(
                "row-1",
                DocumentRect.fromCorners(12.0, 12.0, 108.0, 52.0),
                parentId = "list",
                container = true,
            ),
            SelectableBounds(
                "row-1-label",
                DocumentRect.fromCorners(20.0, 20.0, 50.0, 30.0),
                parentId = "row-1",
            ),
            SelectableBounds(
                "row-1-control",
                DocumentRect.fromCorners(80.0, 20.0, 100.0, 40.0),
                parentId = "row-1",
            ),
            SelectableBounds(
                "row-2",
                DocumentRect.fromCorners(12.0, 60.0, 108.0, 108.0),
                parentId = "list",
                container = true,
            ),
            SelectableBounds(
                "row-2-label",
                DocumentRect.fromCorners(20.0, 70.0, 50.0, 80.0),
                parentId = "row-2",
            ),
            SelectableBounds(
                "row-2-control",
                DocumentRect.fromCorners(80.0, 70.0, 100.0, 90.0),
                parentId = "row-2",
            ),
        )
        val hits = marqueeSelection(marquee, candidates)

        assertEquals(
            setOf(
                "list",
                "row-1",
                "row-1-label",
                "row-1-control",
                "row-2",
                "row-2-label",
                "row-2-control",
            ),
            includeFullyCoveredContainers(marquee, candidates, hits),
        )
    }

    @Test
    fun partialSiblingSelectionDoesNotAddParentContainer() {
        val marquee = DocumentRect.fromCorners(10.0, 10.0, 90.0, 45.0)
        val candidates = listOf(
            SelectableBounds(
                "list",
                DocumentRect.fromCorners(0.0, 0.0, 100.0, 100.0),
                container = true,
            ),
            SelectableBounds("first", DocumentRect.fromCorners(10.0, 10.0, 40.0, 40.0), parentId = "list"),
            SelectableBounds("second", DocumentRect.fromCorners(60.0, 10.0, 90.0, 40.0), parentId = "list"),
            SelectableBounds("third", DocumentRect.fromCorners(10.0, 60.0, 40.0, 90.0), parentId = "list"),
        )
        val hits = marqueeSelection(marquee, candidates)

        assertEquals(
            setOf("first", "second"),
            includeFullyCoveredContainers(marquee, candidates, hits),
        )
    }
}
