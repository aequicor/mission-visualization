package io.aequicor.visualization.engine.ir.model

/** A prototyping interaction: one trigger firing an ordered list of actions. */
data class DesignInteraction(
    val trigger: InteractionTrigger,
    /** Key name for [InteractionTrigger.OnKey]. */
    val key: String = "",
    /** Delay for [InteractionTrigger.AfterDelay]. */
    val delayMs: Double? = null,
    /** Variable id for [InteractionTrigger.OnVariableChange]. */
    val variable: String = "",
    val actions: List<DesignAction>,
    val sourceMap: SourceLocation? = null,
)

enum class InteractionTrigger {
    OnClick, OnHover, OnPress, OnDrag, OnKey, AfterDelay, WhileHovering, WhilePressed, OnVariableChange,
}

sealed interface DesignAction {
    data class Navigate(
        val to: String,
        val transition: DesignTransition? = null,
    ) : DesignAction

    data class OpenOverlay(
        val destination: String,
        val overlay: OverlaySettings = OverlaySettings(),
        val transition: DesignTransition? = null,
    ) : DesignAction

    data class SwapOverlay(
        val destination: String,
        val transition: DesignTransition? = null,
    ) : DesignAction

    data class CloseOverlay(
        val transition: DesignTransition? = null,
    ) : DesignAction

    data object Back : DesignAction

    data class OpenLink(val url: String) : DesignAction

    data class SetVariable(
        val variable: String,
        val value: Bindable<String>,
    ) : DesignAction

    data class ChangeToVariant(
        val target: String,
        val variant: Map<String, String>,
    ) : DesignAction

    data class ScrollTo(
        val target: String,
        val animated: Boolean = true,
    ) : DesignAction

    data class RunActionSet(val actionSetId: String) : DesignAction

    /** Forward compatibility: unknown action types are preserved, never fail the parse. */
    data class Unknown(val rawType: String) : DesignAction
}

data class OverlaySettings(
    val position: OverlayPosition = OverlayPosition.Center,
    val offset: DesignPoint? = null,
    val closeOnOutsideClick: Boolean = true,
    val background: Bindable<DesignColor>? = null,
)

enum class OverlayPosition {
    Center, TopLeft, TopCenter, TopRight, BottomLeft, BottomCenter, BottomRight, Manual,
}

data class DesignTransition(
    val type: TransitionType = TransitionType.Instant,
    val direction: TransitionDirection = TransitionDirection.Left,
    val easing: DesignEasing = DesignEasing.Named(EasingKind.EaseOut),
    val durationMs: Double = 300.0,
)

enum class TransitionType { Instant, Dissolve, SmartAnimate, MoveIn, MoveOut, Push, SlideIn, SlideOut }

enum class TransitionDirection { Left, Right, Top, Bottom }

sealed interface DesignEasing {
    data class Named(val kind: EasingKind) : DesignEasing

    data class CubicBezier(
        val x1: Double,
        val y1: Double,
        val x2: Double,
        val y2: Double,
    ) : DesignEasing

    data class Spring(
        val mass: Double = 1.0,
        val stiffness: Double = 100.0,
        val damping: Double = 15.0,
    ) : DesignEasing
}

enum class EasingKind { Linear, EaseIn, EaseOut, EaseInOut, EaseInBack, EaseOutBack }
