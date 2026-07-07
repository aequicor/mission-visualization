# Пример использования Mission Visualization

Папка `example` показывает два способа проверить проект:

- открыть готовый YAML-файл `mission-control-onboarding.mv.yaml` и вставить его в левую панель основного приложения;
- запустить отдельный consumer-модуль `:example`, который подключает `shared` как зависимость и рендерит свой standalone `.mv.yaml` документ.

## Запуск

Desktop:

```bash
./gradlew :example:run
```

Web/Wasm distribution:

```bash
./gradlew :example:wasmJsBrowserDevelopmentExecutableDistribution
```

Web/Wasm dev server:

```bash
./gradlew :example:wasmJsBrowserDevelopmentRun
```

## Что проверять в демо

- YAML грузится напрямую без Markdown fenced-блока.
- Компоненты на превью экрана кликабельны: выбор подсвечивает node через Canvas overlay и обновляет инспектор.
- Шаги сценария переключают экран и node.
- Поле `Comment` внутри превью формы принимает ввод и остаётся выбранным node.
- Инспектор показывает замечания по экранам и node.
- Клик по замечанию переводит на target, к которому оно относится.
- Кнопка `Prompt` генерирует prompt по выбранному IR target и связанным comments.

## Подключение как зависимости

В этом репозитории пример подключает библиотеку локально:

```kotlin
implementation(projects.shared)
```

После публикации библиотеки это место можно заменить на координаты артефакта,
например:

```kotlin
implementation("io.aequicor:mission-visualization:<version>")
```
