# План — подсистема диаграмм (`:subsystems:diagrams`)

Draw.io-подобный редактор диаграмм: узлы-таблицы, удобные коннекторы-стрелки, описания и
**полный набор UML**. Подсистема — по канону проекта (пара «чистое ядро + `-compose` рендерер»),
но с **тремя** модулями: чистое ядро, Compose-рендерер и SLM-биндинг.

## Решения (согласовано с пользователем)

| Вопрос | Решение |
|---|---|
| Отношение к `:subsystems:figures` | **Автономно** — своя геометрия узлов/линий, без зависимости от figures. |
| Персистентность / авторинг | **SLM write-back** (канон). Требует сделать **SLM внешне-расширяемым** (регистрация typed-block'ов из подсистем, а не хардкод в `TypedBlockKind`) — это **отдельная задача-предусловие**. |
| Набор UML v1 | Копирование основного функционала draw.io + **полный UML**: Class (+все связи), Sequence, State, Activity, Use Case, Component, Deployment. |
| Коннекторы | **Ортогональные (Manhattan) + порты** — стрелка приклеена к узлу и следует за ним; плюс **авто-раскладка** графа (layered/tree) для генерации. |

## Функции draw.io (изучено) и их место в v1

Разбор функционала draw.io/diagrams.net по областям, с отметкой охвата в нашей подсистеме:
**✅ v1** — входит; **⛔ вне** — не наша задача (коллаборация/облако/импорт чужих форматов).
Всё, что раньше откладывалось в v2, по решению пользователя **поднято в v1**. Источники — в конце файла.

### Узлы, фигуры, контейнеры
- **Библиотеки фигур** (50+ паков: UML/UML 2.5, flowchart, ER, BPMN, network, cloud…). У нас —
  ✅ v1 наш **UML-набор** + базовые узлы (rect/rounded/ellipse/text) + расширенные паки
  (flowchart/ER/BPMN-примитивы).
- **Таблицы** (строки/колонки/ячейки, как отдельный тип узла) — ✅ v1.
- **Контейнеры / swimlanes / cross-functional** (узел-контейнер, дочерние узлы внутри,
  drag-in/out, ресайз тянет содержимое) — ✅ v1.
- **Custom shape library** (пользовательские фигуры/группы) — ⛔ вне (у нас фиксированный
  UML-словарь; расширяемость — через SLM-реестр, не через UI-библиотеки).
- **Группировка, слои (layers), z-order** — ✅ v1 (z-order + группы + слои).

### Коннекторы (ядро «удобных стрелок»)
- **Типы соединения:** *floating* (скользит по периметру, кратчайший путь) и *fixed*
  (прикреплён к точке-порту); допускается floating на одном конце и fixed на другом — ✅ v1
  (порты + плавающее примыкание к стороне).
- **Точки соединения (connection points):** предопределённые порты на сторонах узла;
  индикаторы floating (`·`) / fixed (`×`); кастомные (произвольные пользовательские) точки — ✅ v1.
- **Waypoints:** синие грабы, перетаскивание секции меняет маршрут, авто-добавление/удаление
  точек — ✅ v1 (ручные waypoint'ы поверх авто-роутинга).
- **Стили роутинга:** straight / **orthogonal** / simple / isometric / curved / entity-relation —
  ✅ v1 все шесть (базис — straight + **orthogonal** Manhattan, obstacle-aware).
- **Углы линии:** sharp / rounded / curved — ✅ v1 (все три).
- **Наконечники (arrowheads):** большой список стрелок + **UML-символы** (◇ aggregation,
  ◆ composition, △ generalization, ⇢ dependency/realization, ● и др.), размер и отступ конца,
  None — ✅ v1 (полный набор).
- **Режим Connection:** line / link (двойная) / arrow (фигура-стрелка) — ✅ v1 (все три).
- **Паттерн:** solid / dashed / dotted, цвет/толщина/непрозрачность — ✅ v1.
- **Line jumps** (пересечения: arc/gap/sharp/overlap) — ✅ v1.
- **Метки на коннекторе:** до трёх (source/middle/target), таскаются грабом, свопаются при
  реверсе — ✅ v1.
- **Реверс / flip коннектора** — ✅ v1 (реверс со свопом меток).
- **Жесты рисования:** hover по узлу → directional-стрелки → тянуть новую связь; drag-секции →
  переразводка; alt-drag → фиксированная точка; clone-and-connect — ✅ v1 (полный набор).
- **Анимация потока по связи** (source→target) — ✅ v1.

### Раскладка и холст
- **Grid / snap / guides** (сетка, привязка, направляющие) — ✅ v1 (переиспользуем
  `:subsystems:anchoring` для снаппинга/магнита узлов и портов).
- **Авто-раскладка** (tree/org, horizontal/vertical flow) — ✅ v1 (layered + tree, «arrange»).
- **Outline / навигация / зум-пан** — ✅ v1 (базовый пан/зум канвы редактора).

### Текст и стилизация
- **Метки/описания узлов и рёбер, rich-text** — ✅ v1 через `:subsystems:typography` (описания —
  явное требование пользователя).
- **Markdown в метках** — ✅ v1.
- **Стили (заливка/обводка/тень/скетч-стиль)** — ✅ v1 (заливка/обводка + sketch-стиль + тени).

### Ввод/вывод и генерация
- **Text-to-diagram** (Mermaid / PlantUML) — ✅ v1 (парсер → модель + авто-раскладка Фазы 7).
- **SVG-экспорт узлов** (figures-подобный `toSvgPathData`) — ✅ v1.
- **Импорт/экспорт `.drawio`/VSDX/Lucid, PNG/PDF-экспорт** — ⛔ вне (наш формат — SLM).
- **Шаблоны диаграмм** — ✅ v1.

### Явно вне подсистемы (⛔)
Реалтайм-коллаборация, облачные хранилища (Drive/Dropbox), комментарии/ревизии, плагины UI,
десктоп-приложение — это уровень приложения/сервиса, не подсистемы диаграмм.

## Модульная структура

Три модуля, package-корень `io.aequicor.visualization.subsystems.diagrams[.*]`, таргеты как у
остальных подсистем (android/jvm/js/wasmJs/ios). **Ключевой принцип** (по образцу `figures`):
ядро — чистый leaf-модуль, а `:engine:ir` **зависит от ядра**, а не наоборот. Два адаптера
разнесены по ответственности — **compose = рендер, slm = парсинг/сериализация** — и никогда не
зависят друг от друга:

```
   parse / write-back                         render
   ─────────────────                          ──────
:engine:frontend ──▶ :subsystems:diagrams-slm        :engine:backend-compose
                            │                                │
                            ├────────────▼───────────────────┤
                            │     :subsystems:diagrams        │   ◀── :subsystems:diagrams-compose
                            │      (чистое ядро, KMP,         │            │
                            │       без Compose, без IR)      │            └── typography-compose
                            ▼                                 ▼
                        :engine:ir ─── api(:subsystems:diagrams) ── встраивает DiagramGraph в IR-узел
```

Направление зависимостей (все — внутрь, к чистому ядру):
- `:engine:ir` → `api(:subsystems:diagrams)` — как `api(:subsystems:figures)`: узел типа `diagram`
  несёт модель `DiagramGraph` как типизированную нагрузку (аналог shape/vector).
- `:subsystems:diagrams-compose` → `api(:subsystems:diagrams)` + compose + `typography-compose`.
- `:subsystems:diagrams-slm` → `:subsystems:diagrams` + `:engine:frontend` + `:engine:ir`.
- `:engine:backend-compose` → `implementation(:subsystems:diagrams-compose)` (рендер из IR).
  **backend-compose НЕ знает про diagrams-slm** — парсер не тянется в путь рендера.

- **`:subsystems:diagrams`** — чистое ядро (Kotlin, KMP, **без Compose и без IR**), единственный
  источник истины по модели и геометрии:
  - Модель графа: `DiagramGraph`, `DiagramNode` (id, geometry, тип, `ports`), `DiagramEdge`
    (source/target port, тип связи, метки), `DiagramLabel`, `DiagramStyle`.
  - Модель узлов: таблицы (`TableNode`: строки/колонки/ячейки), UML-shape'ы (класс с секциями
    полей/методов и видимостью, lifeline, state, activity-node, actor, component, node/deployment).
  - Геометрия: bbox узлов, якоря портов на сторонах, **ортогональный роутинг** коннекторов
    (obstacle-aware Manhattan, точки поворота), геометрия наконечников стрелок по UML-нотации
    (association / aggregation ◇ / composition ◆ / generalization △ / dependency ⇢ / realization).
  - Хит-тест (узел / ребро / порт / ручка-метка), editing-ops (move/resize узла, reconnect ребра,
    add/remove port, edit label) — чистые функции над иммутабельной моделью.
  - **Авто-раскладка**: layered (Sugiyama-lite) для class/component/deployment, tree для
    state/activity, — детерминированная, тестируемая.
  - Экспорт геометрии в нейтральный path-формат для рендерера (аналог figures `PathGeometry`).

- **`:subsystems:diagrams-compose`** — **только рендер**: `DiagramCanvas`/`DiagramNodeView`/
  `EdgeView` (path → Compose Path, cap/join, наконечники), рендер таблиц и UML-шейпов, метки
  через `:subsystems:typography-compose`, оверлеи выделения/портов/ручек. Ни SLM, ни write-back
  тут нет. Потребители: `:engine:backend-compose` (рендер из IR) и `:shared` (редактор-канва).

- **`:subsystems:diagrams-slm`** — **только парсинг/сериализация**: SLM-текст ↔ `DiagramGraph`
  (reader) и **write-back** модели в `*.layout.md` (эмиттер + `SlmPatcher`-интеграция, по образцу
  `NetworkYamlWriter`/`NodeSectionWriter`/`TypographyYamlWriter`). Регистрируется в реестре
  расширений `:engine:frontend` (см. «Предусловие»). Ни строки Compose. Стабильность id — как в
  существующем write-back (явные id + пост-recompile сверка, откат при дрейфе).

### Почему так (ответ на «compose для рендера, slm для парсинга»)
- **Ответственности не пересекаются и не тянут лишнего.** Рендер-путь (`backend-compose →
  diagrams-compose`) не зависит от парсера; парс-путь (`frontend → diagrams-slm`) не зависит от
  Compose. Собрать headless-рендер или headless-парсинг можно независимо.
- **Одна модель, два адаптера.** `DiagramGraph` живёт в чистом ядре; и compose, и slm, и `:engine:ir`
  ссылаются на неё — нет дублирования типов и конвертеров «модель↔модель».
- **IR зависит от ядра, а не ядро от IR** — точь-в-точь как `figures` (`:engine:ir` →
  `api(:subsystems:figures)`). Поэтому ядро остаётся чистым leaf'ом, а диаграмма встраивается в
  документ как типизированная нагрузка узла (как shape/vector), и общий `DesignArtboard` рендерит
  её единообразно.
- **Регистрация без обратной зависимости.** `:engine:frontend` объявляет интерфейс реестра;
  `diagrams-slm` его реализует; связывает их composition root (`:shared`/DI) — так frontend не
  зависит от diagrams-slm, правило зависимостей не нарушается.

## Предусловие: расширяемый SLM

Сейчас typed-block'и SLM — закрытый `enum TypedBlockKind` (node/shape/vector/…) в
`:engine:frontend`. Чтобы диаграммы жили в SLM без хардкода в ядре фронтенда, нужен
**реестр расширений**: подсистема регистрирует ключ блока (`diagram:`), reader (текст→модель),
writer (модель→текст), валидатор. Это отдельная задача (Фаза 0). До неё — временно опереться на
существующий `ir`-fence escape hatch (`IrEscapeHatch`) для прототипа рендера.

## Фазы

**Фаза 0 — SLM extensibility (предусловие).**
Реестр typed-block-расширений во `:engine:frontend` (регистрация kind + reader + writer +
validate), миграция минимум одного существующего block'а на реестр для валидации API. Тесты
frontend зелёные.

**Фаза 1 — Ядро модели + геометрия (`:subsystems:diagrams`).**
`DiagramGraph`/`Node`/`Edge`/`Port`, bbox/якоря, прямые коннекторы, наконечники UML. Юнит-тесты
геометрии и хит-теста. Без Compose.

**Фаза 2 — Compose-рендер (`:subsystems:diagrams-compose`).**
`DiagramCanvas`, рендер узла-прямоугольника + таблицы, ребра-стрелки, метки через typography.
Мини-превью для инспектора. Визуальный чек в web/wasm.

**Фаза 3 — Коннекторы: полный набор.**
Все шесть стилей роутинга (straight/orthogonal/simple/isometric/curved/entity-relation, базис —
obstacle-aware Manhattan), порты на сторонах + произвольные точки, «липкость» стрелки к узлу,
ручные waypoint'ы, режимы line/link/arrow, line-jumps (arc/gap/sharp), паттерны, до 3 меток,
реверс со свопом. Тесты роутинга (детерминированные пути).

**Фаза 4 — UML-набор.**
Class (секции + видимость + все 6 связей) → Sequence (lifelines/activation/messages/fragments) →
State + Activity → Use Case → Component/Deployment. Каждый тип — своя модель узла + рендер +
парити-скриншот. Инкрементально, тип за типом.

**Фаза 5 — Таблицы, контейнеры/swimlanes, редактирование.**
Полноценные таблицы (add/remove строк/колонок, edit + merge ячеек), контейнеры/swimlanes
(вложенность, drag-in/out, ресайз тянет содержимое), группы/слои/z-order, интенты редактора
(create/delete/move/connect/relabel/group/reorder), интеграция в `reduceDesignEditor` (`:shared`),
инспектор-панель.

**Фаза 6 — SLM write-back (`:subsystems:diagrams-slm`).**
Reader + writer диаграммы в `*.layout.md` через реестр из Фазы 0; локальное сохранение
(автосейв/Save/Reset) как у остального редактора; сверка id и анти-коррупция (откат в in-memory
при дрейфе). Golden-тесты round-trip.

**Фаза 7 — Авто-раскладка + генерация.**
Layered/tree раскладка, «arrange» в UI, детерминизм под тесты; text-to-diagram (Mermaid/PlantUML
парсер → модель → раскладка); SVG-экспорт узлов (`toSvgPathData`); шаблоны диаграмм;
стили (sketch/тени) и markdown в метках.

## Интеграция и границы

- Редактор (`:shared`, `editor.presentation`): новые `DesignEditorIntent`-ы диаграммы →
  чистый reducer; write-back через `writeBackEdits` + новый эмиттер; workspace/document split
  сохраняется.
- `:engine:backend-compose` (`DesignArtboard`) потребляет `:subsystems:diagrams-compose` для
  рендера диаграмм-узлов внутри общего документа.
- Цвета/отступы — только токены темы (`LocalEditorColors`); ядро — без Compose; dropdown/select
  в инспекторе — с левым визуалом (custom preview форм узлов), по конвенции проекта.
- Тесты: ядро — юнит (геометрия/роутинг/раскладка/хит-тест), slm — golden round-trip,
  reducer — write-back тесты; UI — wasm-first визуальная проверка.

## Открытые вопросы (уточнить по ходу)

- Грамматика SLM для диаграммы: YAML-блок `diagram:` (узлы/рёбра/типы) — детальная схема в Фазе 6.
- Нужен ли CNL-авторинг диаграмм (сейчас — нет, как и у figures) — отложено.
- Объём v1 расширен (весь ранее-v2 функционал в scope) — это большой объём; при риске сроков
  порядок Фаз 3–7 позволяет резать по краю (line-jumps, isometric-роутинг, Mermaid-импорт —
  первые кандидаты на отсечение), не ломая ядро.

## Источники (draw.io)

- [Work with connectors — draw.io docs](https://www.drawio.com/docs/manual/connectors/)
- [Connector styles — draw.io docs](https://www.drawio.com/docs/manual/styles/connector-styles/)
- [Fixed vs floating connectors — draw.io docs](https://www.drawio.com/docs/manual/connectors/connector-fixed-vs-floating/)
- [Waypoint shape — draw.io docs](https://www.drawio.com/docs/manual/shapes/waypoint-shape/)
- [Diagram types — draw.io docs](https://www.drawio.com/docs/diagram-types/)
- [UML diagrams intro — draw.io docs](https://www.drawio.com/docs/diagram-types/uml/)
- [UML class diagrams — draw.io docs](https://www.drawio.com/docs/diagram-types/uml/class-diagrams/)
