# Архитектура: целевая Clean Architecture (KMP)

Документ описывает **целевую** архитектуру. Текущий код уже во многом ей следует (модули
`engine/*`, слои `editor.{domain,data,presentation}`); что осталось — см. раздел «Миграция».
**Новый код пишем сразу по целевым правилам.**

## Правило зависимостей

Зависимости направлены **только внутрь**, к `domain`:

```
ui  ──▶  presentation  ──▶  domain  ◀──  data
```

- `domain` не знает ни о `data`, ни о `presentation`, ни о Compose/фреймворках.
- `data` и `presentation` зависят от `domain` (его интерфейсов и моделей), но **не друг от друга**.
- `ui` зависит только от `presentation` (+ design-токенов).
- **Compose — только в `presentation`/`ui`.** Ядро (`domain`, `data`) — чистый Kotlin. Слоение
  закреплено границами Gradle-модулей: `frontend → ir ← backend-compose`, приложение (`:shared`)
  сверху; `:engine:ir` и `:engine:frontend` свободны от Compose, Compose появляется только в
  `:engine:backend-compose` и app-слое (см. `engine/README.md`).

## Слои

### domain — чистый Kotlin, без Compose и фреймворков
- **Модели** предметной области (entities/IR): `DesignDocument`, `DesignNode`, `ResolvedNode`
  (пакет `engine.ir.model` / `engine.ir.resolve`); в `:shared` — `editor.domain` (`MissionDocuments`).
- **Контракты repository** — интерфейсы (реализации живут в `data`): `DesignDocumentRepository`.
- **Use cases** — однозадачные интеракторы, инкапсулируют бизнес-правило: `LoadDesignDocumentUseCase`.
- **Доменные сервисы / чистая логика**: компиляция SLM (`engine.frontend`), сериализация/резолв/
  лейаут/валидация (`engine.ir.serialization`, `engine.ir.resolve`, `engine.ir.layout`,
  `engine.ir.validate`) и чистый reducer `reduceDesignEditor` — логика без побочных эффектов.

### data — реализации доступа к данным
- **Реализации repository** над data sources.
- **Data sources**: локальные/удалённые. Сейчас — `editor.data` (`DefaultDesignDocumentRepository`
  над встроенными SLM-исходниками `SlmSource`).
- **DTO** — модели сериализации/транспорта, отдельные от доменных.
- **Mappers** DTO ↔ domain.
- **Сеть**: Ktor-клиент — когда появится (ниже). Секреты — не в коде.

### presentation — состояние экрана, без Compose-виджетов
- **Holder'ы/компоненты состояния**: сейчас `MissionEditorStateHolder`
  (`@Stable` + `mutableStateOf`). Цель — Decompose-компоненты (см. «Навигация»).
- **UI-модели** (view state), отдельные от доменных: `MissionEditorViewState` — хороший пример.
- **Маппинг** domain → UI-модель; вызовы use cases; диспетчеризация intent'ов
  (`DesignEditorIntent`) в reducer `reduceDesignEditor`.
- Наружу отдаёт **иммутабельный state** (цель — `StateFlow`), принимает intent'ы.

### ui — чистый Compose-рендер
- Stateless-композейблы, никакой бизнес-логики. Сейчас: `MissionEditorScreen` +
  `engine.backend.compose` (`DesignArtboard`). Цвета — только через токены темы
  (`editor.ui.theme.EditorTheme` / `LocalEditorColors`).

## Repositories
- Интерфейс — в `domain`, реализация — в `data`. Уже так:
  ```kotlin
  // domain (editor.domain)
  interface DesignDocumentRepository {
      fun missionDocumentSources(): List<MissionDocumentSource>
  }
  // data (editor.data)
  class DefaultDesignDocumentRepository : DesignDocumentRepository { /* ... */ }
  ```

## Use cases
- Один класс — одно действие; имя-глагол; вызывается из presentation. Уже так:
  ```kotlin
  class LoadDesignDocumentUseCase(private val repository: DesignDocumentRepository) {
      operator fun invoke(): MissionDocuments { /* compileSlm + merge */ }
  }
  ```
- Кандидаты на следующие use cases: `ValidateDesignDocumentUseCase` (`validateDesignDocument`),
  `PatchSlmSourceUseCase` (write-back через `SlmPatcher`).

## Модели и мапперы
- У каждого слоя — свои модели: **DTO** (data) / **domain** (IR) / **UI-модель** (presentation).
- Никаких «сквозных» моделей через все слои. Между слоями — явные `toDomain()` / `toUi()` мапперы.

## DI
- Рекомендуется **Koin** (KMP-friendly, дружит с Decompose) вместо ручной сборки зависимостей
  на месте (сейчас `MissionEditorApp` сам создаёт
  `LoadDesignDocumentUseCase(DefaultDesignDocumentRepository())`).
- Единый composition root; зависимости и `CoroutineDispatcher` — **внедряются**, не создаются внутри логики.
- Допустимо ручное DI, но через централизованную фабрику, не разрозненные синглтоны.

## Модульная структура (KMP)
- Ядро уже выделено: `:engine:ir` / `:engine:frontend` / `:engine:backend-compose`
  (`engine/README.md`); `:shared` — app shell; приложения (`:androidApp`, `:desktopApp`,
  `:webApp`, `iosApp`) — тонкие.
- Дальше по мере роста: feature-модули с разделением **api / impl** (публичный контракт vs
  реализация).
- Typesafe project accessors (`projects.engine.ir` и т.п.) — уже включены, использовать их.

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
- `domain` тестируется тривиально (чистые функции): уже есть тесты IR и компилятора
  (`:engine:ir`, `:engine:frontend` — golden/smoke/validate) и редактора
  (`DesignEditorReducerWriteBackTest`, `MissionDocumentLayoutIntegrationTest` в `:shared`).
- Use cases — unit-тесты; repository — с фейковыми data source; presentation — `runTest` + test dispatchers
  (`kotlinx-coroutines-test`).

## Миграция: текущее → цель

Сделано:

| Было | Стало |
|---|---|
| Легаси `ui_engine` (`.mv.yaml`) + `:example` | Удалены; конвейер `engine/*` (SLM → IR → Compose) |
| Свободные функции загрузки документа | `DesignDocumentRepository` + `LoadDesignDocumentUseCase` (`editor.domain`/`editor.data`) |
| Всё в `:shared`, srcDir `appPresentation` | Модули `:engine:ir` / `:engine:frontend` / `:engine:backend-compose`; `appPresentation` упразднён |
| snake_case пакеты (`ui_engine`, `mv_yaml_source`) | Удалены вместе с носителями; новые пакеты без подчёркиваний |

Осталось:

| Сейчас | Цель |
|---|---|
| Ручная сборка зависимостей в `MissionEditorApp` | DI (Koin) / composition root |
| `@Stable` holder + `mutableStateOf` | Decompose-компонент, наружу `StateFlow` |
| Ручная навигация в `MissionEditorViewState` | Decompose child stack/slot |
| Синхронная компиляция SLM на вызывающем потоке | инъекция dispatcher + main-safety при росте |
| Без сети | Ktor-клиент при появлении бэкенда |

**Приоритет:** DI/Koin → StateFlow + инъекция dispatchers → Decompose → Ktor при появлении сети.

## Чек-лист для нового кода / PR
- [ ] Зависимости направлены внутрь к `domain`; в `domain`/`data` нет Compose.
- [ ] Доступ к данным — через repository-интерфейс; бизнес-действие — через use case.
- [ ] Модели слоёв разделены; между слоями — явные мапперы.
- [ ] Presentation отдаёт иммутабельный state, принимает intent'ы.
- [ ] Есть тесты на новую доменную логику / use case.
