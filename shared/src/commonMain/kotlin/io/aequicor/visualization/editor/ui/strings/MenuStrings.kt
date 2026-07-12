package io.aequicor.visualization.editor.ui.strings

/** The project («burger») menu: root actions and the Open / Save / Export / Language panes. */
interface MenuStrings {
    val projectMenu: String
    // Root pane.
    val open: String
    val save: String
    val export: String
    val fullscreen: String
    /** Label of the language row; the current language name is appended after a colon. */
    val language: String
    // Open pane.
    val welcomeProject: String
    val openZipArchive: String
    val openFolder: String
    // Save pane.
    val saveInBrowser: String
    val saveToFolder: String
    val saveAsZip: String
    // Export pane.
    val exportPngScreen: String
    val exportPngComponent: String
    val exportPdfAllScreens: String
    // Language pane.
    val chooseLanguage: String
}

object MenuStringsEn : MenuStrings {
    override val projectMenu = "Project menu"
    override val open = "Open"
    override val save = "Save"
    override val export = "Export"
    override val fullscreen = "Fullscreen (F10)"
    override val language = "Language"
    override val welcomeProject = "Welcome project"
    override val openZipArchive = "Open ZIP archive"
    override val openFolder = "Pick a folder on this PC"
    override val saveInBrowser = "Save work in the browser"
    override val saveToFolder = "Save to a folder on this PC"
    override val saveAsZip = "Save as a ZIP archive"
    override val exportPngScreen = "PNG — whole screen"
    override val exportPngComponent = "PNG — selected component"
    override val exportPdfAllScreens = "PDF — all screens"
    override val chooseLanguage = "Choose language"
}

object MenuStringsRu : MenuStrings {
    override val projectMenu = "Меню проекта"
    override val open = "Открыть"
    override val save = "Сохранить"
    override val export = "Экспортировать"
    override val fullscreen = "Развернуть на весь экран (F10)"
    override val language = "Язык"
    override val welcomeProject = "Welcome-проект"
    override val openZipArchive = "Открыть ZIP архив"
    override val openFolder = "Выбрать папку на ПК"
    override val saveInBrowser = "Сохранить работу в браузере"
    override val saveToFolder = "Сохранить в папку на ПК"
    override val saveAsZip = "Сохранить ZIP архивом"
    override val exportPngScreen = "PNG — весь экран"
    override val exportPngComponent = "PNG — выбранный компонент"
    override val exportPdfAllScreens = "PDF — все экраны"
    override val chooseLanguage = "Выбор языка"
}
