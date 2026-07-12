package io.aequicor.visualization.editor.ui.strings

import io.aequicor.visualization.editor.presentation.CompactLabel
import io.aequicor.visualization.editor.presentation.DeviceMode
import io.aequicor.visualization.editor.presentation.EditorMode
import io.aequicor.visualization.editor.presentation.EditorTool
import io.aequicor.visualization.editor.presentation.InspectorSection
import io.aequicor.visualization.editor.presentation.InspectorTab
import io.aequicor.visualization.editor.presentation.SourceTab

/**
 * Localized labels for presentation-layer enums (tabs, sections, tools, modes). The enums
 * themselves stay language-neutral — the UI resolves their display text here so the domain
 * never depends on Compose or a locale. Round-trip selects that match a value back by its
 * displayed string keep working because both the option list and the matcher call the same
 * resolver within one composition.
 */
interface LabelStrings {
    fun sourceTab(tab: SourceTab): CompactLabel
    fun inspectorTab(tab: InspectorTab): CompactLabel
    fun inspectorSection(section: InspectorSection): CompactLabel
    fun editorTool(tool: EditorTool): String
    fun editorMode(mode: EditorMode): String
    fun deviceMode(mode: DeviceMode): String

    // Panel rails and focus-mode chrome.
    val sourceRail: String
    val inspectorRail: String
    val exitFocus: String
}

object LabelStringsEn : LabelStrings {
    override fun sourceTab(tab: SourceTab): CompactLabel = when (tab) {
        SourceTab.Markdown -> CompactLabel("Semantic Layout Markdown", "Markdown", "SLM")
        SourceTab.Resources -> CompactLabel("Resources", "Res", "Res")
        SourceTab.Layers -> CompactLabel("Layers", "Layers", "Lyr")
    }

    override fun inspectorTab(tab: InspectorTab): CompactLabel = when (tab) {
        InspectorTab.Design -> CompactLabel("Design", "Design", "Dsgn")
        InspectorTab.Prototype -> CompactLabel("Prototype", "Proto", "Prt")
        InspectorTab.Comments -> CompactLabel("Comments", "Com", "Com")
    }

    override fun inspectorSection(section: InspectorSection): CompactLabel = when (section) {
        InspectorSection.Position -> CompactLabel("Position", "Pos", "Pos")
        InspectorSection.Layout -> CompactLabel("Layout", "Layout", "Lay")
        InspectorSection.Appearance -> CompactLabel("Appearance", "Appear", "App")
        InspectorSection.Fill -> CompactLabel("Fill")
        InspectorSection.Stroke -> CompactLabel("Stroke", "Stroke", "Str")
        InspectorSection.Effects -> CompactLabel("Effects", "FX", "FX")
        InspectorSection.Typography -> CompactLabel("Typography", "Type", "Typ")
        InspectorSection.Constraints -> CompactLabel("Constraints", "Const", "Cnst")
        InspectorSection.Interactions -> CompactLabel("Interactions", "Interact", "Int")
        InspectorSection.Motion -> CompactLabel("Motion", "Motion", "Mot")
    }

    override fun editorTool(tool: EditorTool): String = when (tool) {
        EditorTool.Select -> "Move"
        EditorTool.Frame -> "Frame"
        EditorTool.Rectangle -> "Rectangle"
        EditorTool.Ellipse -> "Ellipse"
        EditorTool.Polygon -> "Polygon"
        EditorTool.Star -> "Star"
        EditorTool.Line -> "Line"
        EditorTool.Arrow -> "Arrow"
        EditorTool.Pen -> "Pen"
        EditorTool.Text -> "Text"
        EditorTool.Comment -> "Comment"
        EditorTool.Link -> "Link"
        EditorTool.Code -> "Code"
    }

    override fun editorMode(mode: EditorMode): String = when (mode) {
        EditorMode.Canvas -> "Canvas"
        EditorMode.Scene -> "Scene"
    }

    override fun deviceMode(mode: DeviceMode): String = when (mode) {
        DeviceMode.Pc -> "PC"
        DeviceMode.Mob -> "MOB"
        DeviceMode.Tab -> "TAB"
    }

    override val sourceRail = "Source"
    override val inspectorRail = "Inspector"
    override val exitFocus = "Exit focus  (Esc)"
}

object LabelStringsRu : LabelStrings {
    override fun sourceTab(tab: SourceTab): CompactLabel = when (tab) {
        SourceTab.Markdown -> CompactLabel("Semantic Layout Markdown", "Markdown", "SLM")
        SourceTab.Resources -> CompactLabel("Ресурсы", "Рес", "Рес")
        SourceTab.Layers -> CompactLabel("Слои", "Слои", "Сл")
    }

    override fun inspectorTab(tab: InspectorTab): CompactLabel = when (tab) {
        InspectorTab.Design -> CompactLabel("Дизайн", "Дизайн", "Дзн")
        InspectorTab.Prototype -> CompactLabel("Прототип", "Прото", "Прт")
        InspectorTab.Comments -> CompactLabel("Комментарии", "Комм", "Ком")
    }

    override fun inspectorSection(section: InspectorSection): CompactLabel = when (section) {
        InspectorSection.Position -> CompactLabel("Позиция", "Поз", "Поз")
        InspectorSection.Layout -> CompactLabel("Раскладка", "Раскл", "Рас")
        InspectorSection.Appearance -> CompactLabel("Внешний вид", "Вид", "Вид")
        InspectorSection.Fill -> CompactLabel("Заливка", "Залив", "Зал")
        InspectorSection.Stroke -> CompactLabel("Обводка", "Обвод", "Обв")
        InspectorSection.Effects -> CompactLabel("Эффекты", "Эфф", "Эфф")
        InspectorSection.Typography -> CompactLabel("Типографика", "Типогр", "Тип")
        InspectorSection.Constraints -> CompactLabel("Привязки", "Прив", "Прв")
        InspectorSection.Interactions -> CompactLabel("Взаимодействия", "Взаим", "Взм")
        InspectorSection.Motion -> CompactLabel("Анимация", "Аним", "Ани")
    }

    override fun editorTool(tool: EditorTool): String = when (tool) {
        EditorTool.Select -> "Перемещение"
        EditorTool.Frame -> "Фрейм"
        EditorTool.Rectangle -> "Прямоугольник"
        EditorTool.Ellipse -> "Эллипс"
        EditorTool.Polygon -> "Многоугольник"
        EditorTool.Star -> "Звезда"
        EditorTool.Line -> "Линия"
        EditorTool.Arrow -> "Стрелка"
        EditorTool.Pen -> "Перо"
        EditorTool.Text -> "Текст"
        EditorTool.Comment -> "Комментарий"
        EditorTool.Link -> "Ссылка"
        EditorTool.Code -> "Код"
    }

    override fun editorMode(mode: EditorMode): String = when (mode) {
        EditorMode.Canvas -> "Холст"
        EditorMode.Scene -> "Сцена"
    }

    override fun deviceMode(mode: DeviceMode): String = when (mode) {
        DeviceMode.Pc -> "ПК"
        DeviceMode.Mob -> "МОБ"
        DeviceMode.Tab -> "ПЛАН"
    }

    override val sourceRail = "Исходник"
    override val inspectorRail = "Инспектор"
    override val exitFocus = "Выйти из фокуса  (Esc)"
}
