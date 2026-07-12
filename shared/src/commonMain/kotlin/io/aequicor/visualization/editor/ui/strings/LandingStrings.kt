package io.aequicor.visualization.editor.ui.strings

/**
 * Startup landing ("recent projects + Welcome"). Rendered by the web overlay
 * (`window.__mvLanding`), but the copy lives here in the catalog — the state holder passes the
 * active-language strings into the overlay, so there is a single localized source of truth.
 */
interface LandingStrings {
    val heading: String
    val subtitle: String
    val welcomeTitle: String
    val welcomeSubtitle: String
    val recentHeading: String
    val noRecent: String
    val connectFolder: String
    val open: String
    val remove: String
    val localFolder: String
    val reconnectHint: String
    val foldersUnavailable: String
    val foldersUnavailableBrave: String
    val copyFlagAddress: String
    val flagAddressCopied: String
    val recoverTitle: String
    val recoverSubtitle: String
    val dismiss: String
    val language: String
}

object LandingStringsEn : LandingStrings {
    override val heading = "Open a project"
    override val subtitle = "Pick up where you left off, or start with the Welcome tour."
    override val welcomeTitle = "Welcome tour"
    override val welcomeSubtitle = "Explore the editor, vectors and diagrams"
    override val recentHeading = "Recent projects"
    override val noRecent = "No recent folders yet"
    override val connectFolder = "Open a folder…"
    override val open = "Open"
    override val remove = "Remove from list"
    override val localFolder = "Local folder"
    override val reconnectHint = "Click to grant access"
    override val foldersUnavailable = "Live local folders need Chrome or Edge — this browser " +
        "doesn't support the File System Access API. You can still work here and save to disk."
    override val foldersUnavailableBrave = "Brave hides live local folders behind an experimental " +
        "flag. You can still work here and save to disk, or copy the flag address below."
    override val copyFlagAddress = "Copy flag address"
    override val flagAddressCopied = "Copied"
    override val recoverTitle = "Unsaved work"
    override val recoverSubtitle = "Recovered from your browser"
    override val dismiss = "Skip"
    override val language = "Language"
}

object LandingStringsRu : LandingStrings {
    override val heading = "Открыть проект"
    override val subtitle = "Продолжите работу или начните с обзорного тура Welcome."
    override val welcomeTitle = "Обзорный тур"
    override val welcomeSubtitle = "Редактор, векторы и диаграммы"
    override val recentHeading = "Недавние проекты"
    override val noRecent = "Пока нет недавних папок"
    override val connectFolder = "Открыть папку…"
    override val open = "Открыть"
    override val remove = "Убрать из списка"
    override val localFolder = "Локальная папка"
    override val reconnectHint = "Нажмите, чтобы разрешить доступ"
    override val foldersUnavailable = "Живая синхронизация папок работает в Chrome или Edge — " +
        "этот браузер не поддерживает File System Access API. Здесь можно работать и " +
        "сохранить проект на диск."
    override val foldersUnavailableBrave = "Brave прячет живую синхронизацию папок за " +
        "экспериментальным флагом. Здесь можно работать и сохранить проект на диск — или " +
        "скопировать адрес флага ниже."
    override val copyFlagAddress = "Скопировать адрес флага"
    override val flagAddressCopied = "Скопировано"
    override val recoverTitle = "Несохранённая работа"
    override val recoverSubtitle = "Восстановлено из браузера"
    override val dismiss = "Пропустить"
    override val language = "Язык"
}
