# Архитектура: целевая Clean Architecture (KMP)

Документ описывает **целевую** архитектуру. Текущий код (MVI-конвейер `ui_engine`) эволюционирует
к ней постепенно — см. раздел «Миграция». **Новый код пишем сразу по целевым правилам.**

## Правило зависимостей

Зависимости направлены **только внутрь**, к `domain`:

```
ui  ──▶  presentation  ──▶  domain  ◀──  data
```

- `domain` не знает ни о `data`, ни о `presentation`, ни о Compose/фреймворках.
- `data` и `presentation` зависят от `domain` (его интерфейсов и моделей), но **не друг от друга**.
- `ui` зависит только от `presentation` (+ design-токенов).
- **Compose — только в `presentation`/`ui`.** Ядро (`domain`, `data`) — чистый Kotlin. Это уже частично
  соблюдается: `parser`/`ui_document_ir`/`validator`/`runtime_state` свободны от Compose
  (см. `shared/src/commonMain/kotlin/io/aequicor/visualization/ui_engine/README.md`).

## Слои

### domain — чистый Kotlin, без Compose и фреймворков
- **Модели** предметной области (entities/IR): `UiDocument`, `UiScreen`, `UiNode`, `UiValue`
  (сейчас — пакет `ui_document_ir`).
- **Контракты repository** — интерфейсы (реализации живут в `data`).
- **Use cases** — однозадачные интеракторы, инкапсулируют бизнес-правило.
- **Доменные сервисы / чистая логика**: парсинг и reducer (`parser`, `reduceUiVisualization`,
  логика `runtime_state`) — доменная логика без побочных эффектов.

### data — реализации доступа к данным
- **Реализации repository** над data sources.
- **Data sources**: локальные/удалённые. Сейчас — `mv_yaml_source` (встроенный образец).
- **DTO** — модели сериализации/транспорта, отдельные от доменных.
- **Mappers** DTO ↔ domain.
- **Сеть**: Ktor-клиент — когда появится (ниже). Секреты — не в коде.

### presentation — состояние экрана, без Compose-виджетов
- **Holder'ы/компоненты состояния**: сейчас `UiVisualizationStateHolder`, `MissionEditorStateHolder`
  (`@Stable` + `mutableStateOf`). Цель — Decompose-компоненты (см. «Навигация»).
- **UI-модели** (view state), отдельные от доменных: `MissionEditorViewState` — хороший пример.
- **Маппинг** domain → UI-модель; вызовы use cases; диспетчеризация intent'ов (`UiCommand`) в reducer.
- Наружу отдаёт **иммутабельный state** (цель — `StateFlow`), принимает intent'ы.

### ui — чистый Compose-рендер
- Stateless-композейблы, никакой бизнес-логики. Сейчас: `compose_render_engine`, `compose_ui`,
  `components.*`, `canvas_overlays`. Цвета/отступы — только через токены (`UiRenderTokens`, `spacingDp`).

## Repositories
- Интерфейс — в `domain`, реализация — в `data`.
- Пример цели: обернуть свободную функцию `loadUiDocument` в контракт:
  ```kotlin
  // domain
  interface UiDocumentRepository {
      fun load(source: String): UiLoadResult
  }
  // data
  class DefaultUiDocumentRepository(/* data sources */) : UiDocumentRepository { /* ... */ }
  ```

## Use cases
- Один класс — одно действие; имя-глагол; вызывается из presentation.
- Кандидаты из текущей логики: `LoadUiDocumentUseCase` (обёртка `loadUiDocument`),
  `ValidateDocumentUseCase` (`validateUiDocument`), `GeneratePromptUseCase` (генерация промпта из `runtime_state`).
  ```kotlin
  class LoadUiDocumentUseCase(private val repo: UiDocumentRepository) {
      operator fun invoke(source: String): UiLoadResult = repo.load(source)
  }
  ```

## Модели и мапперы
- У каждого слоя — свои модели: **DTO** (data) / **domain** (IR) / **UI-модель** (presentation).
- Никаких «сквозных» моделей через все слои. Между слоями — явные `toDomain()` / `toUi()` мапперы.

## DI
- Рекомендуется **Koin** (KMP-friendly, дружит с Decompose) вместо `object`-синглтонов
  (`DefaultUiRenderEngine` и т.п.).
- Единый composition root; зависимости и `CoroutineDispatcher` — **внедряются**, не создаются внутри логики.
- Допустимо ручное DI, но через централизованную фабрику, не разрозненные синглтоны.

## Модульная структура (KMP)
- Сейчас всё в `:shared`. Цель по мере роста:
  - `:core` (или `:core:domain`, `:core:data`) — доменные и data-примитивы.
  - Feature-модули с разделением **api / impl** (публичный контракт vs реализация).
  - Приложения (`:androidApp`, `:desktopApp`, `:webApp`, `iosApp`) — тонкие.
  - `appPresentation` (сейчас srcDir) — оформить полноценным Gradle-модулем.
- Typesafe project accessors (`projects.shared`) — уже включены, использовать их.

## Навигация — Decompose
- Экраны/области — **компоненты** с `ComponentContext`; навигация через child stack/slot/pages;
  lifecycle-aware; сохранение состояния и retained instances.
- UI — pluggable Compose поверх компонентов, без логики навигации в composable.
- Цель: навигацию MissionEditor (экраны Overview/Telemetry/Event Log; панели инструментов и инспектора)
  перевести с ручного `MissionEditorViewState` на Decompose child-навигацию.

## Ktor (когда появится сеть/бэкенд)
- **Клиент** (в `data`): `HttpClient` + `ContentNegotiation` (kotlinx.serialization); auth-плагин
  (Bearer/API key) **только по HTTPS**; таймауты; ошибки транспорта → доменные ошибки; секреты не в коде.
- **Сервер** (если появится модуль бэкенда): auth-плагины, защищённые маршруты, principals; для API-key —
  HTTPS, ротация, rate limiting, expiration, без логирования секретов.

## Тестируемость
- `domain` тестируется тривиально (чистые функции): уже есть `UiParserTest`, `UiValidationTest`,
  `UiReducerTest`, `UiRendererRegistryTest`.
- Use cases — unit-тесты; repository — с фейковыми data source; presentation — `runTest` + test dispatchers
  (`kotlinx-coroutines-test`).

## Миграция: текущее → цель

| Сейчас | Цель |
|---|---|
| Свободная `loadUiDocument` | `UiDocumentRepository` (интерфейс в domain) + use case |
| `object`-синглтоны (`DefaultUiRenderEngine`) | DI (Koin) / composition root |
| `@Stable` holder + `mutableStateOf` | Decompose-компонент, наружу `StateFlow` |
| Ручная навигация в `MissionEditorViewState` | Decompose child stack/slot |
| Всё в `:shared` | `:core` + feature-модули (api/impl) |
| snake_case пакеты (`ui_engine`) | lowercase без подчёркиваний |
| Синхронный парсинг на вызывающем потоке | инъекция dispatcher + main-safety при росте |

**Приоритет:** repositories + use cases → DI/Koin → StateFlow + инъекция dispatchers → Decompose →
дробление модулей → выравнивание имён пакетов → Ktor при появлении сети.

## Чек-лист для нового кода / PR
- [ ] Зависимости направлены внутрь к `domain`; в `domain`/`data` нет Compose.
- [ ] Доступ к данным — через repository-интерфейс; бизнес-действие — через use case.
- [ ] Модели слоёв разделены; между слоями — явные мапперы.
- [ ] Presentation отдаёт иммутабельный state, принимает intent'ы.
- [ ] Есть тесты на новую доменную логику / use case.
