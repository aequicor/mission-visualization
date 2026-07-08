# Mission Visualization — руководство для Claude

Kotlin Multiplatform + Compose Multiplatform библиотека и демо: агент пишет Semantic Layout
Markdown (`*.layout.md`, авторинг на RU/EN + i18n), `engine/frontend` компилирует его в
языконезависимый типизированный IR `slm-ir/1.0` (`engine/ir`: model/serialization/resolve/layout/
validate), чистый layout-движок раскладывает документ, `engine/backend-compose` рендерит
Compose-превью с оверлеями (выделение, инспектор). Write-back: правки редактора хирургически
патчат SLM-исходник (`SlmPatcher`). Корневой пакет — `io.aequicor.visualization`.

## Модули
- `:engine:ir` — ядро документа (чистый Kotlin, KMP): типизированный IR `slm-ir/1.0` —
  model / serialization / resolve / layout / validate.
- `:engine:frontend` — SLM-компилятор (чистый Kotlin): `*.layout.md` → IR; `SlmPatcher` для write-back.
- `:engine:backend-compose` — Compose-рендерер (`DesignArtboard`); единственный engine-модуль с Compose.
- `:shared` — app shell: `App`, `MissionEditorScreen`, `editor.{presentation,domain,data,ui}`.
  Таргеты: Android, JVM, JS, wasmJs, iOS. Редактор: `editor.presentation` — иммутабельный
  `DesignEditorState` (документ) + `EditorWorkspaceState` (вид, **отдельно** от документа),
  sealed `DesignEditorIntent` и чистый `reduceDesignEditor`; `editor.ui` — панели
  (`EditorSourcePane`/`EditorCanvasPane`/`EditorInspectorPane`). Только `ResizeNode` пишет
  обратно в SLM-исходник (`SlmPatcher`); прочие правки — in-memory (патчер не умеет
  вставлять/удалять/двигать узлы). Реализованный scope и gaps редактора — в `EDITOR.md`.
- `:androidApp`, `:desktopApp`, `:webApp`, `iosApp` — тонкие обёртки над shared UI.

Документация конвейера — `engine/README.md`; спецификация SLM —
`design-book/semantic-layout-markdown-i18n.md`; редактор — `EDITOR.md`.

## Команды
- Desktop: `./gradlew :desktopApp:run`
- Web (Wasm): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
- Android debug: `./gradlew :androidApp:assembleDebug`
- Тесты (JVM): `./gradlew :shared:jvmTest`
- Тесты движка: `./gradlew :engine:ir:jvmTest` / `./gradlew :engine:frontend:jvmTest`
- Compile-check desktop: `./gradlew :desktopApp:compileKotlin`

## Архитектура (целевая Clean Architecture)
Зависимости направлены внутрь к `domain`: `ui → presentation → domain ← data`. `domain` и `data` —
чистый Kotlin без Compose; Compose только в `presentation`/`ui`. Слоение закреплено границами
Gradle-модулей: `frontend → ir ← backend-compose`, приложение (`:shared`) сверху; в `:shared`
редактор разложен по `editor.{domain,data,presentation}` (repository + use case, чистый
`reduceDesignEditor`); дальше — DI/Koin, `StateFlow` и Decompose-навигация.

Полные правила (слои, repositories, use cases, DI/Koin, модули api/impl, Decompose, Ktor, миграция):

@.claude/rules/architecture.md

## Стандарты кода
Kotlin conventions (именование, иммутабельность, sealed-состояния), корутины (main-safety, инъекция
dispatcher'ов, `StateFlow`, `runTest`), Compose (stateless + hoisting, `Modifier`-параметр, slot-API,
цвета/отступы только через токены). Полные правила:

@.claude/rules/code-style.md

## Визуальный язык
Канон палитры живёт в коде — `EditorColors` в
`shared/src/commonMain/kotlin/io/aequicor/visualization/editor/ui/theme/EditorTheme.kt`
(источник истины по значениям; hex-таблицу здесь не дублируем). Роли, сгруппированные по назначению:
- **accent** — `accent`, `accentContainer`, `onAccentContainer`;
- **chrome** — `chrome`, `chromeGradientStart`/`chromeGradientEnd`, `shellStroke`;
- **поверхности** — `surfaceVariant`, `paneSurface`, `raisedSurface`, `gutterSurface`,
  `statusBarSurface`, `activeLineSurface`;
- **обводки** — `panelStroke`, `softStroke`, `divider`;
- **текст** — `ink`, `mutedInk`, `codeInk`, `statusBarInk`, `subtleInk`, `gutterInk`, `controlInk`;
- **выделение** — `selectionFill`, `selectionStroke`, `thumbnailSelectedStroke`;
- **канва превью** — `canvasDot`, `badgeSurface`, `badgeStroke`, `anchorDot`,
  `thumbnailBlock`, `thumbnailBar`;
- **статусы** — `statusPositive`, `statusWarning`, `statusDanger`.

Правила:
- Цвета — только через токены темы (`LocalEditorColors.current`), не сырые hex.
- Высокий контраст: `ink` на светлых поверхностях (`paneSurface`, `raisedSurface`).
- Тёплые акценты (`statusWarning`, `statusDanger`) — точечно, не как фон.
- Расширение палитры — новыми ролями в `EditorColors` (отдельной задачей), не hex в компонентах.
- Лазуритовая палитра (`UiRenderTokens`) удалённого `ui_engine` умерла вместе с ним.

## Плагин ECC
Проект подключает [ECC](https://github.com/affaan-m/everything-claude-code) (`ecc@ecc`) в
`.claude/settings.json` (project scope). Разовая активация в интерактивном терминале:
`/plugin marketplace add affaan-m/everything-claude-code` → `/plugin install ecc@ecc` → `/reload-plugins`
(или `claude plugin install ecc@ecc --scope project`). ECC большой (67 агентов, 277 скиллов) — при
избыточном контексте `/plugin disable ecc@ecc`.

## Как здесь работать
- Соблюдать правило зависимостей и layering (`engine/README.md`): ядро (`:engine:ir`,
  `:engine:frontend`) без Compose.
- Авторинг SLM-документов — по спецификации `design-book/semantic-layout-markdown-i18n.md`.
- Новый код — сразу по целевым правилам (repositories/use cases, токены темы, stateless-composable).
- Менять поведение — прогонять `./gradlew :shared:jvmTest` (движок — `:engine:ir:jvmTest`,
  `:engine:frontend:jvmTest`); UI-изменения — проверять в desktop/web демо.
- Держать этот файл и `.claude/rules/*` в актуальном состоянии при смене конвенций.

## Код-конвенции (на основе best-practices)
Дистиллировано из best-practices по темам ниже; развёрнутые правила — в
`@.claude/rules/architecture.md` и `@.claude/rules/code-style.md`.

**Clean Architecture (Android)**
- Слои presentation → domain ← data; зависимости направлены только внутрь, к domain.
- Repository-интерфейсы — в domain, реализации — в data.
- Модели раздельны по слоям (DTO / domain / UI); между слоями — явные мапперы, без «сквозных» моделей.
- Бизнес-правила — в use cases; presentation и data их не содержат.
- domain — чистый Kotlin, без Android/Compose/фреймворков.

**KMP + Compose Multiplatform**
- Общий код в commonMain; платформенное — через expect/actual, по минимуму.
- Рост дробить на feature-модули с разделением api/impl; приложения тонкие.
- UI — переиспользуемые stateless-компоненты, единые тема и токены, состояние поднято (hoisting).
- Состояние экрана иммутабельно, поток однонаправленный (MVI): State + Intent + чистый reducer.

**Kotlin**
- Именование: PascalCase — типы, camelCase — функции/свойства, UPPER_SNAKE — константы;
  пакеты строчными без подчёркиваний.
- По умолчанию `val`; данные — immutable `data class`; состояния/intent'ы/результаты — `sealed`-иерархии.
- Expression body для коротких функций; явная видимость публичного API; trailing commas.

**Coroutines**
- Main-safety: тяжёлое — через `withContext` с инъектированным dispatcher'ом.
- Dispatcher'ы внедряются, а не берутся из `Dispatchers.*` внутри логики.
- Structured concurrency; без `GlobalScope`; `CancellationException` не глотать.
- Наружу — `StateFlow`/`Flow` иммутабельного состояния; тесты — `runTest` + `TestDispatcher`.

**Ktor (когда появится сеть)**
- `HttpClient` в data: ContentNegotiation (kotlinx.serialization), таймауты, транспортные ошибки → доменные.
- Auth только по HTTPS (Bearer/API key); секреты не в коде; для API-key — ротация, rate limiting, expiration.

**Decompose (навигация)**
- Экран/область — компонент с `ComponentContext`; навигация через child stack/slot/pages.
- Lifecycle-aware, сохранение состояния и retained instances; UI — pluggable Compose поверх компонента.
- Навигационной логики в composable нет.
