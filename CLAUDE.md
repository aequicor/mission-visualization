# Mission Visualization — руководство для Claude

Kotlin Multiplatform + Compose Multiplatform библиотека и демо: агент пишет строгий standalone
`.mv.yaml`, движок парсит и валидирует его в типизированный IR и рендерит Compose-превью с Canvas-оверлеями
(выделение, комментарии, сценарии). Корневой пакет — `io.aequicor.visualization`.

## Модули
- `:shared` — переиспользуемое ядро KMP: парсер, IR, валидатор, reducer, Compose-рендерер (`ui_engine`).
  Таргеты: Android, JVM, JS, wasmJs, iOS.
- `:desktopApp`, `:webApp` — основные демо (`MissionEditorApp`), компилируют общий `appPresentation`.
- `:androidApp`, `iosApp` — тонкие обёртки над shared UI.
- `:example` — модуль-потребитель + готовые `.mv.yaml`.
- `appPresentation/`, `design/` — общие srcDir/ассеты (не Gradle-модули).

## Команды
- Desktop: `./gradlew :desktopApp:run`
- Web (Wasm): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
- Example desktop: `./gradlew :example:run`
- Android debug: `./gradlew :androidApp:assembleDebug`
- Тесты (JVM): `./gradlew :shared:jvmTest`
- Compile-check desktop: `./gradlew :desktopApp:compileKotlin`

## Архитектура (целевая Clean Architecture)
Зависимости направлены внутрь к `domain`: `ui → presentation → domain ← data`. `domain` и `data` —
чистый Kotlin без Compose; Compose только в `presentation`/`ui`. Текущий MVI-конвейер `ui_engine`
(чистый `reduceUiVisualization`, `@Stable` holder'ы, строгие слои) — фундамент, который эволюционирует
к repositories + use cases, DI, `StateFlow` и Decompose-навигации.

Полные правила (слои, repositories, use cases, DI/Koin, модули api/impl, Decompose, Ktor, миграция):

@.claude/rules/architecture.md

## Стандарты кода
Kotlin conventions (именование, иммутабельность, sealed-состояния), корутины (main-safety, инъекция
dispatcher'ов, `StateFlow`, `runTest`), Compose (stateless + hoisting, `Modifier`-параметр, slot-API,
цвета/отступы только через токены). Полные правила:

@.claude/rules/code-style.md

## Визуальный язык (лазуритовый)
Стиль проекта — «лазуритовый»: глубокий синий ляпис-лазури с тёплыми акцентами-«вкраплениями».
**Канон — токены `UiRenderTokens.kt`** (`shared/src/commonMain/kotlin/io/aequicor/visualization/ui_engine/compose_render_engine/`),
их и используем, а не сырые hex:

| Токен | Hex | Роль |
|---|---|---|
| `LapisBlue` | `#1F5FA8` | primary, основной синий |
| `DeepLapis` | `#143A66` | глубокие/нажатые поверхности, тёмный акцент |
| `SignalTeal` | `#2BB8A8` | успех / сигнальный акцент |
| `CoralNote` | `#E97155` | danger / маркеры комментариев (тёплый «пирит») |
| warning | `#C77800` | предупреждения — янтарь, тёплое вкрапление |
| `Ink` | `#172033` | текст, высокий контраст |
| `Cloud` / `Mist` | `#F6FAFF` / `#EAF1F8` | светлые поверхности |

Правила:
- Цвета берём из токенов; `toneSurface`/`toneStroke` держим согласованными с палитрой.
- Тему превью (`UiVisualizationTheme` в `compose_ui/UiVisualization.kt`) — в той же гамме.
- Высокий контраст: `Ink` на `Cloud`/`Mist`. Тёплые акценты (`CoralNote`, янтарь) — точечно, как
  вкрапления пирита в лазурите, не как фон.
- Расширение палитры/темы — отдельной задачей; значения новых токенов добавлять в эту таблицу.

## Плагин ECC
Проект подключает [ECC](https://github.com/affaan-m/everything-claude-code) (`ecc@ecc`) в
`.claude/settings.json` (project scope). Разовая активация в интерактивном терминале:
`/plugin marketplace add affaan-m/everything-claude-code` → `/plugin install ecc@ecc` → `/reload-plugins`
(или `claude plugin install ecc@ecc --scope project`). ECC большой (67 агентов, 277 скиллов) — при
избыточном контексте `/plugin disable ecc@ecc`.

## Как здесь работать
- Соблюдать правило зависимостей и layering (`ui_engine/README.md`): ядро без Compose.
- Новый код — сразу по целевым правилам (repositories/use cases, токены, stateless-composable).
- Менять поведение — прогонять `./gradlew :shared:jvmTest`; UI-изменения — проверять в desktop/web демо.
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
