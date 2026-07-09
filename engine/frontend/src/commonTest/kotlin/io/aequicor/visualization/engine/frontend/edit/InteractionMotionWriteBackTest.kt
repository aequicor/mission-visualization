package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignEasing
import io.aequicor.visualization.engine.ir.model.DesignInteraction
import io.aequicor.visualization.engine.ir.model.DesignMotion
import io.aequicor.visualization.engine.ir.model.DesignTransition
import io.aequicor.visualization.engine.ir.model.EasingKind
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.MotionFrame
import io.aequicor.visualization.engine.ir.model.MotionKeyframes
import io.aequicor.visualization.engine.ir.model.TransitionDirection
import io.aequicor.visualization.engine.ir.model.TransitionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * P1 write-back: authoring `interaction:` / `motion:` typed blocks from the IR (SetInteractions /
 * SetMotion) round-trips through `compileSlm`, patches surgically, and falls back cleanly on
 * inexpressible cases. Semantic round-trip (emit → compile → equal IR), not byte identity.
 */
class InteractionMotionWriteBackTest {

    private val base = """
        ---
        screen: s
        sourceLocale: en-US
        ---

        # Screen

        node:
          id: root
          name: Screen

        ## Shape: Hero

        node:
          type: shape
          id: hero
          name: Hero
        shape:
          kind: rectangle
        style:
          radius: 8
    """.trimIndent()

    private fun push(to: String) = DesignInteraction(
        trigger = InteractionTrigger.OnClick,
        actions = listOf(
            DesignAction.Navigate(
                to = to,
                transition = DesignTransition(TransitionType.Push, TransitionDirection.Left, DesignEasing.Named(EasingKind.EaseOut), 320.0),
            ),
        ),
    )

    private fun List<DesignInteraction>.stripped() = map { it.copy(sourceMap = null) }

    private fun recompileHero(source: String) = compileForEdit(source).requireDocument().requireNode("hero")

    @Test
    fun createInteractionRoundTrips() {
        val compiled = compileForEdit(base)
        val target = listOf(push("telemetry"))
        val result = applySlmEdit(base, SetInteractions("hero", target), compiled)
        val newSource = result.requireNewSource()

        assertEquals(target, recompileHero(newSource).interactions.stripped())
        assertLosslessOutside(base, newSource, result.appliedRange!!)
        assertTrue(newSource.contains("interaction:"))
    }

    @Test
    fun replaceInteractionLeavesNoStaleKeys() {
        val withOne = applySlmEdit(base, SetInteractions("hero", listOf(push("telemetry"))), compileForEdit(base)).requireNewSource()
        // Replace with a Back action — the shape change must not leave a dangling `to:`/`animation:`.
        val backOnly = DesignInteraction(trigger = InteractionTrigger.OnClick, actions = listOf(DesignAction.Back))
        val replaced = applySlmEdit(withOne, SetInteractions("hero", listOf(backOnly)), compileForEdit(withOne)).requireNewSource()

        assertEquals(listOf(backOnly), recompileHero(replaced).interactions.stripped())
        assertFalse(replaced.contains("telemetry"), "old navigate target must be gone")
        assertFalse(replaced.contains("push"), "old transition must be gone")
    }

    @Test
    fun deleteAllInteractionsRemovesTheBlock() {
        val withOne = applySlmEdit(base, SetInteractions("hero", listOf(push("telemetry"))), compileForEdit(base)).requireNewSource()
        val cleared = applySlmEdit(withOne, SetInteractions("hero", emptyList()), compileForEdit(withOne)).requireNewSource()

        assertTrue(recompileHero(cleared).interactions.isEmpty())
        assertFalse(cleared.contains("interaction:"), "no dangling interaction block")
        // The rest of the node survives.
        assertTrue(cleared.contains("radius: 8"))
    }

    @Test
    fun multipleInteractionsRoundTripAndSecondCanBeEdited() {
        val target = listOf(push("telemetry"), DesignInteraction(trigger = InteractionTrigger.OnPress, actions = listOf(DesignAction.Back)))
        val two = applySlmEdit(base, SetInteractions("hero", target), compileForEdit(base)).requireNewSource()
        assertEquals(target, recompileHero(two).interactions.stripped())

        // Edit only the 2nd of two (whole-set rewrite): change its trigger.
        val edited = target.toMutableList().also { it[1] = it[1].copy(trigger = InteractionTrigger.OnClick) }
        val out = applySlmEdit(two, SetInteractions("hero", edited), compileForEdit(two)).requireNewSource()
        assertEquals(edited, recompileHero(out).interactions.stripped())
    }

    @Test
    fun springEasingRoundTrips() {
        val spring = DesignInteraction(
            trigger = InteractionTrigger.OnClick,
            actions = listOf(
                DesignAction.Navigate(
                    to = "telemetry",
                    transition = DesignTransition(TransitionType.Dissolve, TransitionDirection.Left, DesignEasing.Spring(mass = 1.0, stiffness = 120.0, damping = 14.0), 400.0),
                ),
            ),
        )
        val out = applySlmEdit(base, SetInteractions("hero", listOf(spring)), compileForEdit(base)).requireNewSource()
        assertEquals(listOf(spring), recompileHero(out).interactions.stripped())
    }

    @Test
    fun cubicBezierEasingFallsBackInMemory() {
        val bezier = DesignInteraction(
            trigger = InteractionTrigger.OnClick,
            actions = listOf(
                DesignAction.Navigate(
                    to = "telemetry",
                    transition = DesignTransition(TransitionType.Push, TransitionDirection.Left, DesignEasing.CubicBezier(0.42, 0.0, 0.58, 1.0), 300.0),
                ),
            ),
        )
        val result = applySlmEdit(base, SetInteractions("hero", listOf(bezier)), compileForEdit(base))
        assertNull(result.newSource, "cubic-bezier easing is inexpressible → no source patch")
        assertTrue(result.diagnostics.isNotEmpty(), "a diagnostic explains the fallback")
    }

    @Test
    fun interspersedSiblingBlocksSurviveInteractionRewrite() {
        // Author node: / interaction: / style: order, then rewrite the interaction.
        val withOne = applySlmEdit(base, SetInteractions("hero", listOf(push("telemetry"))), compileForEdit(base)).requireNewSource()
        val out = applySlmEdit(withOne, SetInteractions("hero", listOf(push("eventLog"))), compileForEdit(withOne)).requireNewSource()
        val hero = recompileHero(out)
        assertEquals("eventLog", (hero.interactions.single().actions.single() as DesignAction.Navigate).to)
        assertTrue(out.contains("radius: 8"), "sibling style block untouched")
        assertTrue(out.contains("kind: rectangle"), "sibling shape block untouched")
    }

    @Test
    fun motionCreateReplaceDelete() {
        val pulse = DesignMotion(
            fallback = MotionKeyframes(
                durationMs = 900.0,
                loop = true,
                frames = listOf(
                    MotionFrame(0.0, mapOf("opacity" to 0.4)),
                    MotionFrame(0.5, mapOf("opacity" to 1.0)),
                    MotionFrame(1.0, mapOf("opacity" to 0.4)),
                ),
            ),
        )
        val created = applySlmEdit(base, SetMotion("hero", pulse), compileForEdit(base)).requireNewSource()
        assertEquals(pulse, recompileHero(created).motion)

        // Replace with a shorter fade (loop off) — whole `motion:` entry rewritten.
        val fade = DesignMotion(fallback = MotionKeyframes(300.0, loop = false, frames = listOf(MotionFrame(0.0, mapOf("opacity" to 0.0)), MotionFrame(1.0, mapOf("opacity" to 1.0)))))
        val replaced = applySlmEdit(created, SetMotion("hero", fade), compileForEdit(created)).requireNewSource()
        assertEquals(fade, recompileHero(replaced).motion)
        assertFalse(replaced.contains("loop: true"), "old loop flag gone")

        val removed = applySlmEdit(replaced, SetMotion("hero", null), compileForEdit(replaced)).requireNewSource()
        assertNull(recompileHero(removed).motion)
        assertFalse(removed.contains("motion:"), "motion block deleted")
    }

    @Test
    fun interactionEmitterRejectsInexpressibleActions() {
        assertNull(InteractionYamlWriter.interaction(DesignInteraction(trigger = InteractionTrigger.OnClick, actions = listOf(DesignAction.Unknown("weird")))))
        assertTrue(InteractionYamlWriter.interaction(push("x")) != null)
    }
}
