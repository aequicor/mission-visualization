package io.aequicor.visualization.editor.ui.strings

/**
 * Startup landing ("recent projects + Welcome"). Rendered as the web startup screen
 * (`window.__mvLanding`), but the copy lives here in the catalog — the state holder passes the
 * active-language strings into the screen, so there is a single localized source of truth.
 */
interface LandingStrings {
    val heading: String
    val subtitle: String
    val welcomeTitle: String
    val welcomeSubtitle: String
    val recentHeading: String
    val noRecent: String
    val connectFolder: String
    val projectChoiceTitle: String
    val createBrowserProject: String
    val openDiskProject: String
    val open: String
    val remove: String
    val localFolder: String
    val reconnectHint: String
    val openFailedTitle: String
    val openFailedAccess: String
    val openFailedRetry: String
    val openFailedDismiss: String
    val foldersUnavailable: String
    val foldersUnavailableBrave: String
    val copyFlagAddress: String
    val flagAddressCopied: String
    val recoverTitle: String
    val recoverSubtitle: String
    val language: String
    val folderUnavailableError: String
    val folderContainsProjectError: String
    val projectCreateError: String
}

object LandingStringsEn : LandingStrings {
    override val heading = "Open a project"
    override val subtitle = "Pick up where you left off, or start with the Welcome tour."
    override val welcomeTitle = "Welcome tour"
    override val welcomeSubtitle = "Explore the editor, vectors and diagrams"
    override val recentHeading = "Recent projects"
    override val noRecent = "No recent folders yet"
    override val connectFolder = "Open project"
    override val projectChoiceTitle = "Open a project"
    override val createBrowserProject = "Create new in browser"
    override val openDiskProject = "Open project on disk"
    override val open = "Open"
    override val remove = "Remove from list"
    override val localFolder = "Local folder"
    override val reconnectHint = "Click to grant access"
    override val openFailedTitle = "Could not open the project"
    override val openFailedAccess =
        "The browser did not grant access to this folder. It may have been moved or renamed, " +
            "or the permission prompt was dismissed. Try again and allow access."
    override val openFailedRetry = "Try again"
    override val openFailedDismiss = "Close"
    override val foldersUnavailable = "Live local folders need Chrome or Edge — this browser " +
        "doesn't support the File System Access API. You can still work here and save to disk."
    override val foldersUnavailableBrave = "Brave hides live local folders behind an experimental " +
        "flag. You can still work here and save to disk, or copy the flag address below."
    override val copyFlagAddress = "Copy flag address"
    override val flagAddressCopied = "Copied"
    override val recoverTitle = "Browser project"
    override val recoverSubtitle = "Saved only in this browser"
    override val language = "Language"
    override val folderUnavailableError = "The project folder is unavailable."
    override val folderContainsProjectError = "This folder already contains a project. Open it instead or choose another folder."
    override val projectCreateError = "The project could not be created in this folder."
}

object LandingStringsRu : LandingStrings {
    override val heading = "Открыть проект"
    override val subtitle = "Продолжите работу или начните с обзорного тура Welcome."
    override val welcomeTitle = "Обзорный тур"
    override val welcomeSubtitle = "Редактор, векторы и диаграммы"
    override val recentHeading = "Недавние проекты"
    override val noRecent = "Пока нет недавних папок"
    override val connectFolder = "Открыть проект"
    override val projectChoiceTitle = "Открыть проект"
    override val createBrowserProject = "Создать новый в браузере"
    override val openDiskProject = "Открыть проект на диске"
    override val open = "Открыть"
    override val remove = "Убрать из списка"
    override val localFolder = "Локальная папка"
    override val reconnectHint = "Нажмите, чтобы разрешить доступ"
    override val openFailedTitle = "Не удалось открыть проект"
    override val openFailedAccess =
        "Браузер не дал доступ к этой папке. Возможно, её переместили или переименовали, " +
            "либо запрос доступа был закрыт. Попробуйте ещё раз и разрешите доступ."
    override val openFailedRetry = "Попробовать снова"
    override val openFailedDismiss = "Закрыть"
    override val foldersUnavailable = "Живая синхронизация папок работает в Chrome или Edge — " +
        "этот браузер не поддерживает File System Access API. Здесь можно работать и " +
        "сохранить проект на диск."
    override val foldersUnavailableBrave = "Brave прячет живую синхронизацию папок за " +
        "экспериментальным флагом. Здесь можно работать и сохранить проект на диск — или " +
        "скопировать адрес флага ниже."
    override val copyFlagAddress = "Скопировать адрес флага"
    override val flagAddressCopied = "Скопировано"
    override val recoverTitle = "Проект в браузере"
    override val recoverSubtitle = "Хранится только в этом браузере"
    override val language = "Язык"
    override val folderUnavailableError = "Папка проекта недоступна."
    override val folderContainsProjectError = "В этой папке уже есть проект. Откройте его или выберите другую папку."
    override val projectCreateError = "Не удалось создать проект в этой папке."
}
