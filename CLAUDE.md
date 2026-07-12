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
- `:subsystems:figures` — векторные фигуры (чистый Kotlin, KMP): геометрия (`PathGeometry`,
  примитивы rect/ellipse/polygon/star/line/arrow, дуги эллипса `ellipseArcGeometry`, SVG-парсер,
  hit-test, `meetFit`), модель `VectorNetwork`/`ShapeType`/`VectorPath`/`BooleanOperationKind`,
  чистые editing-ops (move/handle/mirror/corner/radius/winding), lowering `networkToGeometry`
  (со скруглением углов), `VectorAssetProvider`, чистый boolean-движок
  (`PathBoolean`: `pathBoolean`/`pathBooleanFold`, `PathBooleanOp` union/subtract/intersect/
  exclude), `strokeOutline` (реальные joins/caps + align) для Flatten/Outline и
  `toSvgPathData`. `:engine:ir` зависит через `api`.
- `:subsystems:figures-compose` — Compose-адаптер фигур: `PathGeometry.toComposePath`, stroke
  cap/join, boolean `PathOperation`, мини-превью `FigureShapePreview`/`FigureBooleanPreview`.
  Потребители: `:engine:backend-compose`, `:shared`.
- `:subsystems:anchoring`(+`-compose`) — снаппинг/магнит (см. `MEMORY.md`).
- `:shared` — app shell: `App`, `MissionEditorScreen`, `editor.{presentation,domain,data,ui}`.
  Таргеты: Android, JVM, JS, wasmJs, iOS. Редактор: `editor.presentation` — иммутабельный
  `DesignEditorState` (документ) + `EditorWorkspaceState` (вид, **отдельно** от документа),
  sealed `DesignEditorIntent` и чистый `reduceDesignEditor`; `editor.ui` — панели
  (`EditorSourcePane`/`EditorCanvasPane`/`EditorInspectorPane`). Правки геометрии,
  контракта узла, скаляров layout/appearance и списков стилей (fills/strokes/effects) пишут
  обратно в SLM (`SlmPatcher`, хелпер `writeBackEdits`) и **сохраняются локально** (web —
  `localStorage`, desktop — файл, Android — SharedPreferences; автосейв + Save/Reset,
  restore при старте; `editor.data.DraftRepository`/`KeyValueStore`). Теперь пишут обратно и
  **типографика** (`text:` style через `SetTextStyle`/`TypographyYamlWriter`), и **структурные
  правки** — create/delete/duplicate/reorder/reparent (секционный эмиттер `NodeSectionWriter`/
  `SectionWriter` + `InsertChildSubtree`/`DeleteSection`/`MoveSection`); create-screen добавляет
  новый `*.layout.md` источник (`ScreenSourceWriter`). Стабильность id держит явный id в
  синтезируемой секции + пост-recompile сверка набора id (`withStructuralSource`): при любом
  дрейфе патч откатывается, правка остаётся in-memory (исходник не портится). In-memory-фолбэк
  (не выразимо одним источником): cross-page reparent, reparent глубже ATX-6, узлы без
  heading-якоря (`ir`-splice/prose), instance/media/vector-path субдеревья, multi-page delete.
  Scope и gaps — в `EDITOR.md`.
- `:subsystems:*` — извлечённые переиспользуемые подсистемы редактора, каждая парой модулей
  «чистое ядро + `-compose` рендерер» (layering как у anchoring, `engine/README.md`):
  - `:subsystems:anchoring` / `:subsystems:anchoring-compose` — снаппинг/магнит (см. `EDITOR.md`).
  - `:subsystems:typography` / `:subsystems:typography-compose` — вся типографика: чистое ядро
    (`RichText`/`TypographyStyle` — полный Figma-набор: italic, decoration style/color/thickness/
    skip-ink, small caps, super/subscript, leading trim, hanging punctuation, lists, OpenType,
    variable axes; `SpanAlgebra` — **стилизация части строки**; `OffsetHealing`, `CaseTransform`,
    `TypographyMeasurer` c per-line-метриками + selection-geometry) и compose-рендерер
    (`RichTextComposer`/`ComposeTypographyMeasurer` с кэшем / `RichTextPainter` / `FontProvider` +
    `BundledFontProvider` — забандленные Google Fonts: Inter / Source Serif 4 / Roboto /
    JetBrains Mono). `:engine:backend-compose` потребляет `-compose` (адаптер
    `ResolvedText`→`RichText`), редактор (`:shared`) — оба (span-алгебра в редьюсере
    `TextRangeEditing`, шрифты в артборде).
  - `:subsystems:annotations` / `:subsystems:annotations-compose` / `:subsystems:annotations-slm` —
    аннотации/комментарии как отдельный review-слой поверх дизайна: чистое ядро (модель
    `Annotation`/`AnnotationKind` note|issue/`AnnotationAnchor` node|free/`AnnotationLayer`,
    чистые операции слоя, `annotationBadgePosition`, `AnnotationPromptExporter` — промпт для
    ИИ-агента только из issue, scope selected/screen/document, контекст узлов через
    `nodeContext`-лямбду), compose-рендерер (`AnnotationBadge` капля / `AnnotationCard`
    табличка с data-URI-картинкой / `AnnotationOverlay` поверх артборда с pan/zoom;
    issue = `statusWarning`) и sidecar-формат `*.annotations.md` (`AnnotationSlmParser`
    толерантный / `AnnotationSlmWriter` round-trip / `AnnotationSlmPatcher` хирургический
    upsert/delete секции; спека — `design-book/annotations-sidecar-format.md`). Потребитель —
    `:shared` (интенты + `writeBackAnnotations`, оверлей в канве, секция инспектора, экспорт).
- `:androidApp`, `:desktopApp`, `:webApp`, `iosApp` — тонкие обёртки над shared UI.
- `:tools:agent-console` — headless-автоматизация для ИИ-агента (JVM CLI): агент правит CNL
  `*.layout.md` своими инструментами, консоль компилирует/валидирует/рендерит —
  `render` (экран→PNG через off-screen `ImageComposeScene`, реальные шрифты/текст),
  `screens`/`inspect` (JSON: список экранов / дерево с вычисленной геометрией), `validate`
  (IR-* диагностика, exit 2 при error), `export-samples`, `create-screen`. Ядро
  (`AgentSession`/`HeadlessRenderer`/`AgentProject`) UI-агностично — под будущий MCP-сервер.
  См. `tools/agent-console/README.md`.

Документация конвейера — `engine/README.md`; спецификация SLM —
`design-book/semantic-layout-markdown-i18n.md`; редактор — `EDITOR.md`.

## Команды
- Desktop: `./gradlew :desktopApp:run`
- Web (Wasm): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
- Android debug: `./gradlew :androidApp:assembleDebug`
- Тесты (JVM): `./gradlew :shared:jvmTest`
- Тесты движка: `./gradlew :engine:ir:jvmTest` / `./gradlew :engine:frontend:jvmTest`
- Compile-check desktop: `./gradlew :desktopApp:compileKotlin`

## ИИ-тестирование в браузере (webApp/Wasm)
Продукт делается **wasm-first**: браузер (`:webApp`, Compose/WasmJs) — **основная** поверхность
проверки UI, а не desktop-демо. Поэтому UI-правки агент проверяет **сам** в браузере и показывает
результат — не перекладывает ручную проверку на пользователя.

**Порт — не фиксированный, особенно в worktree.** Основной чекаут (или соседний worktree) уже
держит dev-сервер на 8080; параллельно занимать тот же порт нельзя. Правило: поднимать wasm на
**свободном (случайном) порту** и драйвить **фактический** порт webpack, а не «ожидаемый».
`preview_start` берёт свободный порт для прокси (`autoPort: true` в `.claude/launch.json`, name
`webApp`), но webpack всё равно биндится на свой порт и каскадит 8080→8081→8082… — истинный порт
смотреть в `preview_logs`. Готовность: `curl -s -o /dev/null -w '%{http_code}' http://localhost:<port>/`
→ 200. Первая сборка ~30с+ — не навигировать раньше готовности (иначе таб застрянет на
`chrome-error`); осиротевшие webpack-серверы (каскад 808x) — убить и стартовать заново одним чистым
`preview_start`. Dev-сервер — блокирующая continuous-задача: только в фоне (`preview_start` либо
`./gradlew :webApp:wasmJsBrowserDevelopmentRun` через background-bash), не синхронно.

**Взаимодействие — на выбор:**
- **Внутренний браузер** (`preview_*`): скриншоты, логи, консоль, сеть, ресайз (в т.ч. dark mode).
- **chrome-plugin** (`claude-in-chrome` MCP; тулы деференные — грузить через ToolSearch; нужен
  подключённый Chrome с расширением): навигировать на реальный `http://localhost:<port>/`.

**Гочи Compose-canvas (для обоих путей):** весь редактор — один canvas в shadow-root, DOM-инспекта
текста нет — только скриншоты + синтетические жесты. Кликать цепочкой PointerEvent
`pointermove→pointerdown→pointerup` по canvas; координаты skiko — backing-пиксели
(`clientX = cssX * devicePixelRatio`); `Modifier.clickable` требует быстрый тап (down→up ~70мс в
ОДНОМ вызове), иначе Compose трактует как long-press и onClick не срабатывает.

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

### Dropdown / select / combobox
- Все строки dropdown menu / select / combobox и похожих меню выбора должны иметь визуал слева от текста.
- Выбранное значение, если оно отображается в поле, показывает тот же тип визуала слева от текста.
- Действия и объекты используют `EditorIcon` / Material Symbols из `composeResources/files/editor-icons`; новые иконки добавляются туда же и регистрируются в `EditorIcon`.
- Формы/shape/preset-форматы показываются не generic-иконкой, а маленьким custom preview-компонентом.
- Цветовые варианты показываются реальным цветом значения через swatch/checkerboard, не generic-иконкой.
- Расширять общий `EditorDropdownMenuItem` / `SelectField` / `CompactSelectField`, если это покрывает меню; точечно править вызовы только для контекстных preview.
- Иконка, текст, chevron и hover/active/selected-состояния должны оставаться выровненными без layout shift и переполнения на desktop/mobile.

## Локализация (RU/EN)
Весь **chrome** приложения локализован через каталог строк (пакет `editor.ui.strings`), устроенный
как параллель `EditorColors`/`LocalEditorColors`:
- `Strings` — корневой интерфейс из групп по областям UI (`common`, `menu`, `labels`, `source`,
  `inspector`, `prototype`, `canvas`, `colorPicker`; `diagram` намеренно пуст — строки инспектора
  диаграмм живут в `inspector`). Каждая группа — свой файл `strings/<Area>Strings.kt` с
  `interface`/`…En`/`…Ru`. Язык переключается через `LocalStrings` (`staticCompositionLocalOf`),
  провайдится в `MissionEditorApp` по `state.language`.
- **Правило:** любую новую пользовательскую строку chrome класть в соответствующую группу каталога и
  читать `LocalStrings.current.<area>.<key>` (не хардкодить литерал). Общие слова — в `common`;
  подписи presentation-энумов (`SourceTab`/`InspectorSection`/`EditorTool`/…) — резолверами в `labels`.
- **Только chrome.** Контент документа, имена узлов/экранов, SLM-исходники — **язык-нейтральны**
  (`domain`/`data` не зависят от Compose и локали). Дефолт — English; выбор хранится в
  `LanguagePreference` (KeyValueStore, ключ `app.language`), меню «бургер → Язык».
- **Ловушка round-trip select'ов:** где значение матчится обратно по отображаемой строке
  (`options=…map{ resolver(it) }` + `firstOrNull{ resolver(it)==label }`), локализовать **обе**
  стороны одним резолвером, иначе выбор молча ломается в RU.
- Новые иконки меню (`language`/`check`/`fullscreen` и т.п.) — SVG в `composeResources/files/editor-icons`
  + запись в `EditorIcon` (как обычные иконки).

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
  `:engine:frontend:jvmTest`); UI-изменения — проверять в первую очередь в web/wasm (продукт
  wasm-first, см. «ИИ-тестирование в браузере»), desktop-демо — по необходимости.
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
