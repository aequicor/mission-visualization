package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.ActionPatch
import io.aequicor.visualization.engine.frontend.blocks.InteractionPatch
import io.aequicor.visualization.engine.frontend.blocks.MotionPatch
import io.aequicor.visualization.engine.frontend.blocks.VariablesPatch
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignEasing
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.DesignMotion
import io.aequicor.visualization.engine.ir.model.DesignTransition
import io.aequicor.visualization.engine.ir.model.EasingKind
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.MotionFrame
import io.aequicor.visualization.engine.ir.model.MotionKeyframes
import io.aequicor.visualization.engine.ir.model.OverlayPosition
import io.aequicor.visualization.engine.ir.model.OverlaySettings
import io.aequicor.visualization.engine.ir.model.TransitionType
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InteractionMotionReaderTest {
    /** Spec interaction example (~line 993-1007). */
    @Test
    fun readsSpecOverlayInteractionExample() {
        val (patch, collector) = readSingle(
            """
            interaction:
              trigger: onClick
              action: openOverlay
              destination: createMissionDialog
              overlay:
                position: center
                closeOnOutsideClick: true
                background: rgba(0,0,0,0.32)
              animation:
                type: smartAnimate
                easing: easeOut
                durationMs: 220
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            InteractionPatch(
                trigger = InteractionTrigger.OnClick,
                actions = listOf(
                    DesignAction.OpenOverlay(
                        destination = "createMissionDialog",
                        overlay = OverlaySettings(
                            position = OverlayPosition.Center,
                            closeOnOutsideClick = true,
                            background = DesignColor(0x51000000).bindable(),
                        ),
                        transition = DesignTransition(
                            type = TransitionType.SmartAnimate,
                            easing = DesignEasing.Named(EasingKind.EaseOut),
                            durationMs = 220.0,
                        ),
                    ),
                ),
            ),
            patch,
        )
    }

    /** Spec prototype variables + actions list example (~line 1036-1055). */
    @Test
    fun readsSpecActionsListExample() {
        val (patches, collector) = readPatches(
            """
            variables:
              prototype:
                selectedMissionId:
                  type: string
                  default: ""
                isCreateDialogOpen:
                  type: boolean
                  default: false
            interaction:
              trigger: onClick
              actions:
                - type: setVariable
                  variable: selectedMissionId
                  value: "{{mission.id}}"
                - type: changeToVariant
                  target: missionCard
                  variant:
                    state: selected
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertTrue(patches[0] is VariablesPatch)
        assertEquals(
            InteractionPatch(
                trigger = InteractionTrigger.OnClick,
                actions = listOf(
                    DesignAction.SetVariable(
                        variable = "selectedMissionId",
                        value = Bindable.DataRef(DesignExpression("mission.id")),
                    ),
                    DesignAction.ChangeToVariant(
                        target = "missionCard",
                        variant = mapOf("state" to "selected"),
                    ),
                ),
            ),
            patches[1],
        )
    }

    /** `action:` shorthand — a single onClick interaction. */
    @Test
    fun readsActionShorthand() {
        val (patch, collector) = readSingle(
            """
            action:
              type: navigate
              to: /missions/new
            """,
        )
        assertTrue(collector.diagnostics.isEmpty())
        assertEquals(ActionPatch(DesignAction.Navigate(to = "/missions/new")), patch)
    }

    /** Spec motion example (~line 1060-1074). */
    @Test
    fun readsSpecMotionExample() {
        val (patch, collector) = readSingle(
            """
            motion:
              ref: motion/loading-pulse.json
              fallback:
                type: keyframes
                durationMs: 900
                loop: true
                frames:
                  - at: 0
                    opacity: 0.4
                  - at: 0.5
                    opacity: 1
                  - at: 1
                    opacity: 0.4
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            MotionPatch(
                DesignMotion(
                    ref = "motion/loading-pulse.json",
                    fallback = MotionKeyframes(
                        durationMs = 900.0,
                        loop = true,
                        frames = listOf(
                            MotionFrame(0.0, mapOf("opacity" to 0.4)),
                            MotionFrame(0.5, mapOf("opacity" to 1.0)),
                            MotionFrame(1.0, mapOf("opacity" to 0.4)),
                        ),
                    ),
                ),
            ),
            patch,
        )
    }

    @Test
    fun springEasingReadsParameters() {
        val (patch, collector) = readSingle(
            """
            interaction:
              trigger: onHover
              action: closeOverlay
              animation:
                type: dissolve
                easing: spring
                stiffness: 200
                damping: 20
                durationMs: 150
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            InteractionPatch(
                trigger = InteractionTrigger.OnHover,
                actions = listOf(
                    DesignAction.CloseOverlay(
                        DesignTransition(
                            type = TransitionType.Dissolve,
                            easing = DesignEasing.Spring(mass = 1.0, stiffness = 200.0, damping = 20.0),
                            durationMs = 150.0,
                        ),
                    ),
                ),
            ),
            patch,
        )
    }

    @Test
    fun unknownActionTypeBecomesUnknownWithWarning() {
        val (patch, collector) = readSingle(
            """
            interaction:
              trigger: onClick
              action: teleport
            """,
        )
        assertEquals(listOf<DesignAction>(DesignAction.Unknown("teleport")), (patch as InteractionPatch).actions)
        assertTrue(collector.diagnostics.any { "teleport" in it.message })
    }
}
