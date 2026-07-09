# Mission Visualization — agent rules

## Dropdown / select / combobox
- Все строки dropdown menu / select / combobox и похожих меню выбора должны иметь визуал слева от текста.
- Выбранное значение, если оно отображается в поле, показывает тот же тип визуала слева от текста.
- Действия и объекты используют `EditorIcon` / Material Symbols из `shared/src/commonMain/composeResources/files/editor-icons`; новые иконки добавляются туда же и регистрируются в `EditorIcon`.
- Формы/shape/preset-форматы показываются не generic-иконкой, а маленьким custom preview-компонентом.
- Цветовые варианты показываются реальным цветом значения через swatch/checkerboard, не generic-иконкой.
- Расширять общий `EditorDropdownMenuItem` / `SelectField` / `CompactSelectField`, если это покрывает меню; точечно править вызовы только для контекстных preview.
- Иконка, текст, chevron и hover/active/selected-состояния должны оставаться выровненными без layout shift и переполнения на desktop/mobile.
