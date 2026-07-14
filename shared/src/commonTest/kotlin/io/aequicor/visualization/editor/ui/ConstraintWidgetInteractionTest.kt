package io.aequicor.visualization.editor.ui

import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConstraintWidgetInteractionTest {

    @Test
    fun horizontalPinsCreateAndReduceThePairedConstraint() {
        assertEquals(
            HorizontalConstraint.LeftRight,
            nextHorizontalConstraint(HorizontalConstraint.Right, ConstraintPinTarget.HorizontalLeft),
        )
        assertEquals(
            HorizontalConstraint.Right,
            nextHorizontalConstraint(HorizontalConstraint.LeftRight, ConstraintPinTarget.HorizontalLeft),
        )
        assertEquals(
            HorizontalConstraint.Left,
            nextHorizontalConstraint(HorizontalConstraint.Left, ConstraintPinTarget.HorizontalLeft),
        )
    }

    @Test
    fun verticalPinsCreateAndReduceThePairedConstraint() {
        assertEquals(
            VerticalConstraint.TopBottom,
            nextVerticalConstraint(VerticalConstraint.Bottom, ConstraintPinTarget.VerticalTop),
        )
        assertEquals(
            VerticalConstraint.Top,
            nextVerticalConstraint(VerticalConstraint.TopBottom, ConstraintPinTarget.VerticalBottom),
        )
        assertEquals(
            VerticalConstraint.Bottom,
            nextVerticalConstraint(VerticalConstraint.Bottom, ConstraintPinTarget.VerticalBottom),
        )
    }

    @Test
    fun centerAndScaleReplaceOnlyTheirOwnAxis() {
        assertEquals(
            HorizontalConstraint.Center,
            nextHorizontalConstraint(HorizontalConstraint.LeftRight, ConstraintPinTarget.HorizontalCenter),
        )
        assertEquals(
            HorizontalConstraint.Scale,
            nextHorizontalConstraint(null, ConstraintPinTarget.HorizontalScale),
        )
        assertEquals(
            VerticalConstraint.Center,
            nextVerticalConstraint(VerticalConstraint.TopBottom, ConstraintPinTarget.VerticalCenter),
        )
        assertEquals(
            VerticalConstraint.Scale,
            nextVerticalConstraint(null, ConstraintPinTarget.VerticalScale),
        )
        assertEquals(
            HorizontalConstraint.Right,
            nextHorizontalConstraint(HorizontalConstraint.Right, ConstraintPinTarget.VerticalTop),
        )
        assertEquals(
            VerticalConstraint.Top,
            nextVerticalConstraint(VerticalConstraint.Top, ConstraintPinTarget.HorizontalLeft),
        )
    }

    @Test
    fun hitAreasKeepBothAxesAndScaleControlsReachable() {
        assertEquals(ConstraintPinTarget.VerticalTop, constraintPinTargetAt(50f, 6f, 100f, 100f))
        assertEquals(ConstraintPinTarget.HorizontalCenter, constraintPinTargetAt(50f, 24f, 100f, 100f))
        assertEquals(ConstraintPinTarget.VerticalCenter, constraintPinTargetAt(24f, 50f, 100f, 100f))
        assertEquals(ConstraintPinTarget.HorizontalRight, constraintPinTargetAt(90f, 50f, 100f, 100f))
        assertEquals(ConstraintPinTarget.HorizontalScale, constraintPinTargetAt(24f, 90f, 100f, 100f))
        assertEquals(ConstraintPinTarget.VerticalScale, constraintPinTargetAt(76f, 90f, 100f, 100f))
        assertNull(constraintPinTargetAt(-1f, 50f, 100f, 100f))
    }
}
