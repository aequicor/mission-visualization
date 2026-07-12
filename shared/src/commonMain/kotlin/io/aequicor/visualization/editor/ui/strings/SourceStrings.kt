package io.aequicor.visualization.editor.ui.strings

import io.aequicor.visualization.editor.presentation.CompactLabel
import io.aequicor.visualization.editor.presentation.ScreenPreset

/** Left column: SLM source viewer, Layers tree and the Screens panel. */
interface SourceStrings {
    val noSlmSource: String
    val diagnostics: String
    val errors: String
    val warnings: String
    val copyDiagnostics: String
    val emptyScreen: String
    val lockLayer: String
    val unlockLayer: String
    val hideLayer: String
    val showLayer: String
    val bringForward: String
    val sendBackward: String
    val screens: CompactLabel
    val createScreen: String
    fun screenPreset(preset: ScreenPreset): String
    val resourcesEmptyTitle: String
    val resourcesEmptyHint: String
    val resourceMissing: String
    fun resourceUsage(count: Int): String
}

object SourceStringsEn : SourceStrings {
    override val noSlmSource = "This screen was created in the editor and has no SLM source yet."
    override val diagnostics = "Diagnostics"
    override val errors = "Errors"
    override val warnings = "Warnings"
    override val copyDiagnostics = "Copy diagnostics"
    override val emptyScreen = "Empty screen"
    override val lockLayer = "Lock layer"
    override val unlockLayer = "Unlock layer"
    override val hideLayer = "Hide layer"
    override val showLayer = "Show layer"
    override val bringForward = "Bring forward"
    override val sendBackward = "Send backward"
    override val screens = CompactLabel("Screens", "Scr", "Scr")
    override val createScreen = "Create screen"
    override fun screenPreset(preset: ScreenPreset): String = when (preset) {
        ScreenPreset.Desktop -> "Desktop"
        ScreenPreset.Tablet -> "Tablet"
        ScreenPreset.Mobile -> "Mobile"
        ScreenPreset.Square -> "Square"
    }
    override val resourcesEmptyTitle = "No images yet"
    override val resourcesEmptyHint = "Drop or paste an image onto the canvas to add it to res/."
    override val resourceMissing = "Missing bytes"
    override fun resourceUsage(count: Int): String = if (count == 1) "1 use" else "$count uses"
}

object SourceStringsRu : SourceStrings {
    override val noSlmSource = "Этот экран создан в редакторе и пока не имеет SLM-исходника."
    override val diagnostics = "Диагностика"
    override val errors = "Ошибки"
    override val warnings = "Предупреждения"
    override val copyDiagnostics = "Скопировать диагностику"
    override val emptyScreen = "Пустой экран"
    override val lockLayer = "Заблокировать слой"
    override val unlockLayer = "Разблокировать слой"
    override val hideLayer = "Скрыть слой"
    override val showLayer = "Показать слой"
    override val bringForward = "На передний план"
    override val sendBackward = "На задний план"
    override val screens = CompactLabel("Экраны", "Экр", "Экр")
    override val createScreen = "Создать экран"
    override fun screenPreset(preset: ScreenPreset): String = when (preset) {
        ScreenPreset.Desktop -> "Десктоп"
        ScreenPreset.Tablet -> "Планшет"
        ScreenPreset.Mobile -> "Мобильный"
        ScreenPreset.Square -> "Квадрат"
    }
    override val resourcesEmptyTitle = "Пока нет изображений"
    override val resourcesEmptyHint = "Перетащите или вставьте изображение на холст — оно попадёт в res/."
    override val resourceMissing = "Нет байтов"
    override fun resourceUsage(count: Int): String = when {
        count % 10 == 1 && count % 100 != 11 -> "$count использование"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "$count использования"
        else -> "$count использований"
    }
}
