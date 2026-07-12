package io.aequicor.visualization.editor.ui.strings

/** Generic words and actions reused across several UI areas. */
interface CommonStrings {
    val back: String
    val done: String
    val edit: String
    val add: String
    val remove: String
    val delete: String
    val hide: String
    val show: String
    val none: String
    val auto: String
    val mixed: String
    val default: String
    val collapseSection: String
    val expandSection: String
    val openOptions: String
    val on: String
    val off: String
}

object CommonStringsEn : CommonStrings {
    override val back = "Back"
    override val done = "Done"
    override val edit = "Edit"
    override val add = "Add"
    override val remove = "Remove"
    override val delete = "Delete"
    override val hide = "Hide"
    override val show = "Show"
    override val none = "None"
    override val auto = "Auto"
    override val mixed = "Mixed"
    override val default = "Default"
    override val collapseSection = "Collapse section"
    override val expandSection = "Expand section"
    override val openOptions = "Open options"
    override val on = "on"
    override val off = "off"
}

object CommonStringsRu : CommonStrings {
    override val back = "Назад"
    override val done = "Готово"
    override val edit = "Изменить"
    override val add = "Добавить"
    override val remove = "Удалить"
    override val delete = "Удалить"
    override val hide = "Скрыть"
    override val show = "Показать"
    override val none = "Нет"
    override val auto = "Авто"
    override val mixed = "Разное"
    override val default = "По умолчанию"
    override val collapseSection = "Свернуть секцию"
    override val expandSection = "Развернуть секцию"
    override val openOptions = "Открыть список"
    override val on = "вкл"
    override val off = "выкл"
}
