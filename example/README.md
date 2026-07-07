# Пример использования Mission Visualization

Папка `example` показывает два способа проверить проект:

- открыть готовый Markdown-файл `mission-control-onboarding.md` и вставить его в левую панель основного приложения;
- запустить отдельный consumer-модуль `:example`, который подключает `shared` как зависимость и рендерит тот же сценарий.

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

- Компоненты на превью экрана кликабельны: выбор подсвечивает компонент и обновляет инспектор.
- Шаги сценария переключают экран и компонент.
- Поле `Comment` внутри превью формы принимает ввод и остаётся выбранным компонентом.
- Инспектор показывает все замечания по всем экранам и компонентам.
- Клик по замечанию переводит на экран и компонент, к которым оно относится.
- Одна кнопка `Prompt` генерирует общий prompt по всем замечаниям.

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
