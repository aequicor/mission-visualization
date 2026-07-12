package io.aequicor.visualization.editor.ui.strings

import io.aequicor.visualization.editor.presentation.MotionPreset
import io.aequicor.visualization.editor.presentation.ProtoActionKind
import io.aequicor.visualization.engine.ir.model.EasingKind
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.TransitionDirection
import io.aequicor.visualization.engine.ir.model.TransitionType

/**
 * Copy for the Prototype inspector tab (interactions + motion authoring). Enum-backed selects
 * expose a resolver used on BOTH the option list and the value/matcher so a round-trip select
 * keeps matching after translation. The enums themselves live in language-neutral modules.
 */
interface PrototypeStrings {
    // Empty state and guidance notes.
    val emptySelect: String
    val addScreenFirst: String
    val noInteractions: String

    // Interaction card.
    val delay: String
    val addAction: String

    // Action editor field captions.
    val target: String
    val pickScreen: String
    val motion: String
    val easing: String
    val duration: String

    // Motion section.
    val animateLayer: String
    val preset: String
    val loop: String

    // Footer.
    val playInScene: String

    // Shared unit suffix.
    val ms: String

    // Round-trip resolvers (used on option list AND value/matcher).
    fun trigger(trigger: InteractionTrigger): String
    fun protoAction(kind: ProtoActionKind): String
    fun transitionType(type: TransitionType): String
    fun direction(direction: TransitionDirection): String
    fun easingKind(kind: EasingKind): String
    fun motionPreset(preset: MotionPreset): String

    // Displayed easing values that are not selectable list options.
    val easingSpring: String
    val easingCustom: String

    // Motion-preset fallback shown when no preset matches the current clip.
    val motionCustom: String
}

object PrototypeStringsEn : PrototypeStrings {
    override val emptySelect = "Select an object to add behavior."
    override val addScreenFirst = "Add another screen first, then wire a tap to it."
    override val noInteractions = "No interactions yet. Add On click → Navigate with +, then press Play."

    override val delay = "Delay"
    override val addAction = "+ Add action"

    override val target = "Target"
    override val pickScreen = "Pick a screen"
    override val motion = "Motion"
    override val easing = "Easing"
    override val duration = "Duration"

    override val animateLayer = "Animate this layer"
    override val preset = "Preset"
    override val loop = "Loop"

    override val playInScene = "▶  Play in Scene"

    override val ms = "ms"

    override fun trigger(trigger: InteractionTrigger): String = when (trigger) {
        InteractionTrigger.OnClick -> "On click"
        InteractionTrigger.OnPress -> "On press"
        InteractionTrigger.AfterDelay -> "After delay"
        else -> trigger.name
    }

    override fun protoAction(kind: ProtoActionKind): String = when (kind) {
        ProtoActionKind.Navigate -> "Navigate"
        ProtoActionKind.Back -> "Back"
    }

    override fun transitionType(type: TransitionType): String = when (type) {
        TransitionType.Instant -> "Instant"
        TransitionType.Dissolve -> "Dissolve"
        TransitionType.Push -> "Push"
        TransitionType.SlideIn -> "Slide in"
        TransitionType.SlideOut -> "Slide out"
        TransitionType.MoveIn -> "Move in"
        TransitionType.MoveOut -> "Move out"
        TransitionType.SmartAnimate -> "Smart animate"
    }

    override fun direction(direction: TransitionDirection): String = when (direction) {
        TransitionDirection.Left -> "Left"
        TransitionDirection.Right -> "Right"
        TransitionDirection.Top -> "Top"
        TransitionDirection.Bottom -> "Bottom"
    }

    override fun easingKind(kind: EasingKind): String = when (kind) {
        EasingKind.Linear -> "Linear"
        EasingKind.EaseIn -> "Ease in"
        EasingKind.EaseOut -> "Ease out"
        EasingKind.EaseInOut -> "Ease in out"
        EasingKind.EaseInBack -> "Ease in back"
        EasingKind.EaseOutBack -> "Ease out back"
    }

    override fun motionPreset(preset: MotionPreset): String = when (preset) {
        MotionPreset.FadeIn -> "Fade in"
        MotionPreset.Pop -> "Pop"
        MotionPreset.Float -> "Float"
        MotionPreset.Pulse -> "Pulse"
        MotionPreset.Spin -> "Spin"
    }

    override val easingSpring = "Spring"
    override val easingCustom = "Custom curve"

    override val motionCustom = "Custom"
}

object PrototypeStringsRu : PrototypeStrings {
    override val emptySelect = "Выберите объект, чтобы добавить поведение."
    override val addScreenFirst = "Сначала добавьте ещё один экран, затем привяжите к нему нажатие."
    override val noInteractions = "Пока нет взаимодействий. Добавьте «По клику → Перейти» через +, затем нажмите «Запустить»."

    override val delay = "Задержка"
    override val addAction = "+ Добавить действие"

    override val target = "Цель"
    override val pickScreen = "Выберите экран"
    override val motion = "Анимация"
    override val easing = "Плавность"
    override val duration = "Длительность"

    override val animateLayer = "Анимировать этот слой"
    override val preset = "Пресет"
    override val loop = "Зациклить"

    override val playInScene = "▶  Запустить в сцене"

    override val ms = "мс"

    override fun trigger(trigger: InteractionTrigger): String = when (trigger) {
        InteractionTrigger.OnClick -> "По клику"
        InteractionTrigger.OnPress -> "По нажатию"
        InteractionTrigger.AfterDelay -> "После задержки"
        else -> trigger.name
    }

    override fun protoAction(kind: ProtoActionKind): String = when (kind) {
        ProtoActionKind.Navigate -> "Перейти"
        ProtoActionKind.Back -> "Назад"
    }

    override fun transitionType(type: TransitionType): String = when (type) {
        TransitionType.Instant -> "Мгновенно"
        TransitionType.Dissolve -> "Растворение"
        TransitionType.Push -> "Сдвиг"
        TransitionType.SlideIn -> "Скольжение внутрь"
        TransitionType.SlideOut -> "Скольжение наружу"
        TransitionType.MoveIn -> "Перемещение внутрь"
        TransitionType.MoveOut -> "Перемещение наружу"
        TransitionType.SmartAnimate -> "Умная анимация"
    }

    override fun direction(direction: TransitionDirection): String = when (direction) {
        TransitionDirection.Left -> "Слева"
        TransitionDirection.Right -> "Справа"
        TransitionDirection.Top -> "Сверху"
        TransitionDirection.Bottom -> "Снизу"
    }

    override fun easingKind(kind: EasingKind): String = when (kind) {
        EasingKind.Linear -> "Линейно"
        EasingKind.EaseIn -> "Плавно в начале"
        EasingKind.EaseOut -> "Плавно в конце"
        EasingKind.EaseInOut -> "Плавно с двух сторон"
        EasingKind.EaseInBack -> "С отскоком в начале"
        EasingKind.EaseOutBack -> "С отскоком в конце"
    }

    override fun motionPreset(preset: MotionPreset): String = when (preset) {
        MotionPreset.FadeIn -> "Появление"
        MotionPreset.Pop -> "Pop"
        MotionPreset.Float -> "Парение"
        MotionPreset.Pulse -> "Пульсация"
        MotionPreset.Spin -> "Вращение"
    }

    override val easingSpring = "Пружина"
    override val easingCustom = "Своя кривая"

    override val motionCustom = "Свой"
}
