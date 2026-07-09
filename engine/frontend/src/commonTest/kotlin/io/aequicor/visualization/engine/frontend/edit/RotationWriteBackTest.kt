package io.aequicor.visualization.engine.frontend.edit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Round-trip coverage for [SetNodeRotation]: writes `node: position: rotation` and recompiles to the new angle. */
class RotationWriteBackTest {

    private val withPosition = """
        ---
        screen: demo
        sourceLocale: en-US
        ---

        # Screen

        node:
          id: screen_root
          type: frame

        ## Card

        node:
          type: frame
          id: card
          position:
            x: 10
            y: 20
        layout:
          mode: none
    """.trimIndent() + "\n"

    private val withoutPosition = """
        ---
        screen: demo
        sourceLocale: en-US
        ---

        # Screen

        node:
          id: screen_root
          type: frame

        ## Card

        node:
          type: frame
          id: card
        layout:
          mode: none
    """.trimIndent() + "\n"

    @Test
    fun rotationMergesIntoExistingPositionAndRoundTrips() {
        val compiled = compileForEdit(withPosition)
        val result = applySlmEdit(withPosition, SetNodeRotation("card", 45.0), compiled)
        val new = result.requireNewSource()
        val card = compileForEdit(new).requireDocument().requireNode("card")
        assertEquals(45.0, card.rotation)
        // x/y are untouched by the rotation write.
        assertEquals(10.0, card.position?.x)
        assertEquals(20.0, card.position?.y)
        assertLosslessOutside(withPosition, new, assertNotNull(result.appliedRange))
    }

    @Test
    fun rotationCreatesPositionWhenAbsentAndRoundTrips() {
        val compiled = compileForEdit(withoutPosition)
        val result = applySlmEdit(withoutPosition, SetNodeRotation("card", 90.0), compiled)
        val new = result.requireNewSource()
        assertEquals(90.0, compileForEdit(new).requireDocument().requireNode("card").rotation)
    }
}
