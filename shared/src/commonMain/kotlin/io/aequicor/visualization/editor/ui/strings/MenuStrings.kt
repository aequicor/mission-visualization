package io.aequicor.visualization.editor.ui.strings

/** The project («burger») menu and its nested action panes. */
interface MenuStrings {
    val projectMenu: String
    // Root pane.
    val open: String
    val save: String
    val export: String
    val agentFile: String
    val fullscreen: String
    /** Label of the language row; the current language name is appended after a colon. */
    val language: String
    // Open pane.
    val welcomeProject: String
    val openZipArchive: String
    val openFolder: String
    // Live local-folder sync ("browser IDE" mode).
    val connectFolder: String
    val folderDisconnect: String
    val folderWatching: String
    val folderExternalError: String
    val folderConflict: String
    val folderRestoreEdit: String
    val folderDismiss: String
    fun folderReconnect(name: String): String
    // Save pane.
    val saveInBrowser: String
    val saveToFolder: String
    val saveAsZip: String
    // Browser-only project banner.
    val browserOnlyNotice: String
    val saveToDisk: String
    // Export pane.
    val exportPngScreen: String
    val exportPngComponent: String
    val exportPdfAllScreens: String
    // Agent file panes.
    val agentSkillsTitle: String
    val agentBaseSkill: String
    val agentDiagramsSkill: String
    val agentVectorGraphicsSkill: String
    val agentTypographySkill: String
    val agentAnnotationsSkill: String
    val agentEditorSkill: String
    val agentNext: String
    val agentOutputTitle: String
    val downloadAgentsFile: String
    val downloadClaudeFile: String
    val copyAgentFile: String
    // Language pane.
    val chooseLanguage: String
}

object MenuStringsEn : MenuStrings {
    override val projectMenu = "Project menu"
    override val open = "Open"
    override val save = "Save"
    override val export = "Export"
    override val agentFile = "AGENTS.md"
    override val fullscreen = "Fullscreen (F10)"
    override val language = "Language"
    override val welcomeProject = "Welcome project"
    override val openZipArchive = "Open ZIP archive"
    override val openFolder = "Pick a folder on this PC"
    override val connectFolder = "Connect folder (live sync)"
    override val folderDisconnect = "Disconnect folder"
    override val folderWatching = "Live"
    override val folderExternalError = "External file has errors — showing the last good version"
    override val folderConflict = "External change loaded — your unsaved edit was kept"
    override val folderRestoreEdit = "Restore my edit"
    override val folderDismiss = "Dismiss"
    override fun folderReconnect(name: String) = "Reconnect “" + name + "”"
    override val saveInBrowser = "Save work in the browser"
    override val saveToFolder = "Save to a folder on this PC"
    override val saveAsZip = "Save as a ZIP archive"
    override val browserOnlyNotice = "Changes are saved in your browser only."
    override val saveToDisk = "Save to disk"
    override val exportPngScreen = "PNG — whole screen"
    override val exportPngComponent = "PNG — selected component"
    override val exportPdfAllScreens = "PDF — all screens"
    override val agentSkillsTitle = "Include SLM subsystems"
    override val agentBaseSkill = "SLM (required)"
    override val agentDiagramsSkill = "Diagrams and UML"
    override val agentVectorGraphicsSkill = "Vector graphics"
    override val agentTypographySkill = "Typography"
    override val agentAnnotationsSkill = "Annotations"
    override val agentEditorSkill = "Editor"
    override val agentNext = "Next"
    override val agentOutputTitle = "Create agent file"
    override val downloadAgentsFile = "Download AGENTS.md"
    override val downloadClaudeFile = "Download CLAUDE.md"
    override val copyAgentFile = "Copy to clipboard"
    override val chooseLanguage = "Choose language"
}

object MenuStringsRu : MenuStrings {
    override val projectMenu = "Меню проекта"
    override val open = "Открыть"
    override val save = "Сохранить"
    override val export = "Экспортировать"
    override val agentFile = "AGENTS.md"
    override val fullscreen = "Развернуть на весь экран (F10)"
    override val language = "Язык"
    override val welcomeProject = "Welcome-проект"
    override val openZipArchive = "Открыть ZIP архив"
    override val openFolder = "Выбрать папку на ПК"
    override val connectFolder = "Подключить папку (живая синхронизация)"
    override val folderDisconnect = "Отключить папку"
    override val folderWatching = "Live"
    override val folderExternalError = "Во внешнем файле ошибки — показана последняя рабочая версия"
    override val folderConflict = "Загружено внешнее изменение — ваша несохранённая правка сохранена"
    override val folderRestoreEdit = "Вернуть мою правку"
    override val folderDismiss = "Скрыть"
    override fun folderReconnect(name: String) = "Переподключить «" + name + "»"
    override val saveInBrowser = "Сохранить работу в браузере"
    override val saveToFolder = "Сохранить в папку на ПК"
    override val saveAsZip = "Сохранить ZIP архивом"
    override val browserOnlyNotice = "Изменения сохраняются только в браузере."
    override val saveToDisk = "Сохранить на диск"
    override val exportPngScreen = "PNG — весь экран"
    override val exportPngComponent = "PNG — выбранный компонент"
    override val exportPdfAllScreens = "PDF — все экраны"
    override val agentSkillsTitle = "Добавить подсистемы SLM"
    override val agentBaseSkill = "SLM (обязательно)"
    override val agentDiagramsSkill = "Диаграммы и UML"
    override val agentVectorGraphicsSkill = "Векторная графика"
    override val agentTypographySkill = "Типографика"
    override val agentAnnotationsSkill = "Аннотации"
    override val agentEditorSkill = "Редактор"
    override val agentNext = "Далее"
    override val agentOutputTitle = "Создать агентский файл"
    override val downloadAgentsFile = "Скачать AGENTS.md"
    override val downloadClaudeFile = "Скачать CLAUDE.md"
    override val copyAgentFile = "Скопировать в буфер обмена"
    override val chooseLanguage = "Выбор языка"
}
