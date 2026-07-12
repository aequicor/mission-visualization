package io.aequicor.visualization.editor.ui.strings

import io.aequicor.visualization.subsystems.annotations.AnnotationKind

/**
 * Canvas-area copy: the center canvas empty state, the floating toolbar and its flyout
 * affordances, the zoom/fit controls, the issue-export action + confirmation popup, and the
 * Scene-stage overlays. Enum labels whose canonical value lives in the presentation module
 * (`EditorMode`, `DeviceMode`, `EditorTool`) are resolved through [LabelStrings]
 * (`strings.labels.*`) so the UI localizes them without re-implementing the mapping here.
 */
interface CanvasStrings {
    // Empty / preview state (also reused by the Scene stage).
    val noPreview: String

    // Floating-toolbar flyout affordances (icon contentDescriptions).
    val shapeTools: String
    val chooseShapeTool: String
    val diagramTools: String
    val chooseDiagramNodeType: String
    val annotationTools: String
    val chooseAnnotationKind: String
    /** Display label of an annotation kind in the toolbar flyout (Note / Issue). */
    fun annotationKind(kind: AnnotationKind): String

    // Zoom controls.
    val fitScreen: String
    val fitSelection: String

    // Export-issues action (toolbar) + its confirmation popup.
    val exportIssues: String
    val scopeSelectedAnnotation: String
    val scopeCurrentScreen: String
    val scopeWholeDocument: String
    val exportPromptTitle: String
    val exportPromptEmpty: String
    val exportPromptCopied: String
    val copyAgain: String
    val close: String

    // Scene stage.
    val trace: String

    // External-image ingestion (drag-drop / paste) affordances.
    val dropImageHere: String
    val ingestUnsupportedType: String
    val ingestReadFailed: String
}

object CanvasStringsEn : CanvasStrings {
    override val noPreview = "No preview"

    override val shapeTools = "Shape tools"
    override val chooseShapeTool = "Choose shape tool"
    override val diagramTools = "Diagram tools"
    override val chooseDiagramNodeType = "Choose diagram node type"
    override val annotationTools = "Annotation tools"
    override val chooseAnnotationKind = "Choose annotation kind"
    override fun annotationKind(kind: AnnotationKind): String = when (kind) {
        AnnotationKind.Note -> "Note"
        AnnotationKind.Issue -> "Issue"
    }

    override val fitScreen = "Fit screen"
    override val fitSelection = "Fit selection"

    override val exportIssues = "Export issues"
    override val scopeSelectedAnnotation = "Selected annotation"
    override val scopeCurrentScreen = "Current screen"
    override val scopeWholeDocument = "Whole document"
    override val exportPromptTitle = "Export issues"
    override val exportPromptEmpty = "The selected scope has no issue annotations — the prompt is empty."
    override val exportPromptCopied =
        "The prompt was copied to the clipboard. If pasting didn't work " +
            "(for example, the page is served over HTTP), select and copy the text manually:"
    override val copyAgain = "Copy again"
    override val close = "Close"

    override val trace = "Trace"

    override val dropImageHere = "Drop image to add it to the project"
    override val ingestUnsupportedType = "That file isn't a supported image (PNG, JPG, SVG, GIF, WebP)."
    override val ingestReadFailed = "Couldn't read that image file."
}

object CanvasStringsRu : CanvasStrings {
    override val noPreview = "Нет предпросмотра"

    override val shapeTools = "Инструменты фигур"
    override val chooseShapeTool = "Выбрать инструмент"
    override val diagramTools = "Инструменты диаграмм"
    override val chooseDiagramNodeType = "Выбрать тип узла"
    override val annotationTools = "Инструменты аннотаций"
    override val chooseAnnotationKind = "Выбрать тип аннотации"
    override fun annotationKind(kind: AnnotationKind): String = when (kind) {
        AnnotationKind.Note -> "Заметка"
        AnnotationKind.Issue -> "Замечание"
    }

    override val fitScreen = "Вписать экран"
    override val fitSelection = "Вписать выделение"

    override val exportIssues = "Выгрузить замечания"
    override val scopeSelectedAnnotation = "Выбранная аннотация"
    override val scopeCurrentScreen = "Текущий экран"
    override val scopeWholeDocument = "Весь документ"
    override val exportPromptTitle = "Экспорт замечаний"
    override val exportPromptEmpty = "В выбранной области нет issue-аннотаций — промпт пуст."
    override val exportPromptCopied =
        "Промпт скопирован в буфер обмена. Если вставка не сработала " +
            "(например, страница открыта по HTTP), выделите и скопируйте текст вручную:"
    override val copyAgain = "Скопировать ещё раз"
    override val close = "Закрыть"

    override val trace = "Трасса"

    override val dropImageHere = "Отпустите изображение, чтобы добавить его в проект"
    override val ingestUnsupportedType = "Это не поддерживаемое изображение (PNG, JPG, SVG, GIF, WebP)."
    override val ingestReadFailed = "Не удалось прочитать файл изображения."
}
