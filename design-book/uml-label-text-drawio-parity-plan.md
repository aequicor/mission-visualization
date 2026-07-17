# План: текстовки в UML-компонентах — как в draw.io

Ветка `main`, рабочее дерево (не HEAD). Baseline зелёный: `./gradlew :subsystems:diagrams:jvmTest :subsystems:diagrams-slm:jvmTest` → exit 0 **при всех живых дефектах ниже**. Значит это дыры в покрытии, а не регрессии.

> **Предупреждение о доказательствах.** Файл `ZzScratchUseCaseRepro.kt`, на который ссылается часть дознания, **в дереве отсутствует** (`ls subsystems/diagrams-slm/src/jvmTest/.../slm/` → только `SlmDiagramAuditTool.kt`). Все цитаты на него недействительны. Вся арифметика ниже перевыведена независимо.

---

## 1. Диагноз

Жалоба — это **три независимых дефекта**, наложившихся на двух скриншотах, плюс корневая причина, лежащая **вне кода** — в документе авторинга.

### 1.1. Скриншот 2 (выделенный узел выглядит ПУСТЫМ) — главный дефект

В модели **два текстовых стора**:

| стор | где | кто читает |
|---|---|---|
| `DiagramNode.labels: List<DiagramLabel>` | `DiagramNode.kt:96` | 3 kind'а из 17 |
| поля payload'а (`name` / `text` / `title`) | `UmlNodes.kt:97-100` — `data class UmlUseCaseNode(val name: String)` | 14 kind'ов из 17 |

Рендерер для use-case рисует **только** `payload.name`:

```kotlin
// DiagramNodeRenderer.kt:170-173
is UmlUseCaseNode -> {
    drawStyledPath(node.outlinePath(), style, colors, seed)
    drawDiagramLabel(measurer, DiagramLabel(payload.name), bounds.inset(8.0), labelInk)
}
```

`node.labels` читает только `drawFirstLabel`, вызываемый из трёх мест — `:101` (BasicShape), `:106` (Flowchart), `:121` (BpmnTask). Рендерер документирует раскол прямо:

```kotlin
// DiagramNodeRenderer.kt:212
/** First node label centered in [box] (payloads whose name lives in the payload skip this). */
```

А inline-редактор читает и пишет **другой стор**:

```kotlin
// EditorDiagramOverlay.kt:1952-1953
is DiagramTextEditTarget.NodeLabel ->
    graph.nodeById(DiagramNodeId(target.elementId))?.labels?.firstOrNull()?.text.orEmpty()
```

```kotlin
// NodeOps.kt:89-98 — payload не трогается никогда
public fun DiagramGraph.setNodeLabel(id: DiagramNodeId, text: String?): DiagramGraph =
    updateNode(id) { node -> node.copy(labels = …) }
```

**Следствие:** для CNL-авторского use-case `node.labels` пуст → редактор открывается **пустым поверх видимого текста**, напечатанное уходит в никуда (канва продолжает рисовать `payload.name`), а `DiagramCnlWriter.kt:106` покорно пишет в источник **осиротевший `label «…»`**. Тот же баг в инспекторе (`EditorInspectorPane.kt:4794-4801`).

Две отягчающие детали:
- `DiagramCnlRoundTripTest` на испорченном документе **ПРОЙДЁТ** — round-trip fidelity здесь не защита.
- `grep -rln SetDiagramNodeLabel shared/src/commonTest/` → **пусто**. Путь коммита inline-правки не покрыт вообще.

**Форма скриншота 2** («широкий плоский rounded rect с бледными вырожденными дугами сверху и снизу по центру») — не другая фигура и не cylinder. `diagramTextEditRect` возвращает для NodeLabel **полный `node.bounds`** (`EditorDiagramOverlay.kt:1911-1912`), а плита редактора — непрозрачный `Surface(color = colors.raisedSurface, shape = RoundedCornerShape(3.dp))` по этим bounds, рисуемый последним. `ellipsePath(b)` (`NodeOutline.kt:71`) вписан в bounds и касается их ровно в 4 точках: **сверху и снизу касательная горизонтальна** — внешняя половина обводки идёт параллельно краю плиты длинной дугой (видна как плоская дуга); слева и справа касательная вертикальна и сливается с 1.dp accent-бордюром плиты (не видна). Асимметрию «дуги сверху/снизу, но не слева/справа» предсказывает только эта версия.

**Вывод: скриншоты 1 и 2 — один узел в двух состояниях. Пользователь сделал двойной клик и получил пустую плиту. Скриншот 2 — прямая улика бага двух сторов.**

### 1.2. Скриншот 1 (эллипс 930×260 вокруг короткой строки) — корневая причина не в рендерере

**Перенос строк работает.** `DiagramText.kt:126-131` передаёт `maxWidth = box.width`; `ComposeTypographyMeasurer.kt:118` — `softWrap = maxWidth != null`. Лейблу просто нечего переносить (числа перевыведены):

| величина | значение |
|---|---|
| лейбл «Вести контакты собственников и совета дома» | 42 симв. |
| ширина @ Inter 13px, фактор **0.540** (реальный advance кириллицы) | **294.8 px** |
| коробка лейбла `bounds.inset(8.0)` | **914 px** |

294.8 в 914 — переносить нечего.

**Размер узла — обязательный авторский ввод.** `DiagramCnlReader.kt:458` жёстко падает: `"diagram node '$id' is missing its \`<w> by <h>\` size"`. `AutoLayout` размеры не трогает (только translate — `LayoutScopes.kt:238`). Измерения текста в чистом ядре **нет вообще**: `subsystems/diagrams/build.gradle.kts` не объявляет ни одной зависимости.

**То есть 930×260 — литерал, который агент-автор напечатал на глаз, и конвейер пронёс его дословно.** Арифметика подтверждает механизм:

| величина | значение |
|---|---|
| эллипс, вписывающий одну строку 294.8px (`text × √2`) | **417 px** |
| авторский | **930 px** = **×2.23** |
| draw.io stock use case | 140×70 |
| наша палитра (`EditorDiagramOverlay.kt:203`) | 150×70 |

Автор компенсировал **размером** отсутствие hug.

**Настоящая поверхность корневой причины — `SKILLS/SLM-diagrams.md`**, который компилируется в продукт (`shared/build.gradle.kts:25` — `Triple("DIAGRAMS", "SLM diagrams", "SKILLS/SLM-diagrams.md")`) и является **тем самым документом, что агент читает перед тем, как напечатать `930 by 260`**. Сегодня он говорит «Size and position are required» (`:80`) и даёт **ровно один** use-case-образец:

```md
Node use-case submit «Submit mission» 180 by 80 position 260 220   ← SKILLS/SLM-diagrams.md:123
```

180×80 на 13-символьную английскую подпись, **без формулы** и без указаний, как масштабировать на 42-символьную русскую. Экстраполяция на глаз приземляется на 930×260.

### 1.3. Третий дефект — коробка лейбла эллипса

| для 930×260 | значение |
|---|---|
| текущая коробка `bounds.inset(8.0)` | 914 × 244 |
| вписанный прямоугольник (`w/√2 × h/√2`) | **657.6 × 183.8** |
| перекос | **+39% ширины, +33% высоты** |

При этом мы **клипаем** (`DiagramText.kt:137-143`) → текст, вылезший за кривую, обрезается прямоугольно: хуже и draw.io (который выпускает), и вписывания. Тот же класс для всех кривых kind'ов (`bounds.inset(4.0)` `:101`, `inset(6.0)` `:106`).

Ирония: шов уже есть и уже классифицирует фигуру — `perimeterKind()` (`NodeGeometry.kt:64` → `is UmlUseCaseNode -> DiagramPerimeterKind.ELLIPSE`), и его KDoc (`NodeGeometry.kt:25-27`) прямо говорит *«round shapes attach on the inscribed ellipse»*. **Для текста он просто не используется.**

### 1.4. Чего в жалобе НЕТ, вопреки дознанию

1. **SVG-экспорт и lint — не user-facing.** `grep -rn "diagramToSvg"` / `"lintDiagram"` даёт только тесты и `SlmDiagramAuditTool.kt:53-54`. **Продакшн-вызывающих ноль** — ни из `:shared`, ни из `:webApp`, ни из меню. Пользователь физически не может триггернуть ни неразрывный `<text>`, ни разошедшиеся метрики класса. **Но** `SlmDiagramAuditTool` — единственный глаз verification-цикла, а `svgText` (`DiagramSvgExport.kt:540-556`) не умеет переносить строки → audit-SVG **структурно не способен показать дефект лейбла**. Вот почему vision-цикл, отгрузивший P0–P5 роутинга, это пропустил. Это **блокер верификации, а не дефекта**.
2. **«labelCharWidth = 7.0 недооценивает кириллицу»** — неверно в обе стороны. 7.0 на 13px = фактор **0.538** против реальных **0.540** (промах 0.4%). Пурный **0.6** ПЕРЕоценивает на **11.1%** (294.8 → 327.6px). Реальный риск обратный заявленному.
3. **«tofu»-гипотеза мертва** — Inter и Roboto покрывают весь блок А–я.
4. **«Escape=commit — non-draw.io»** — ошибка дознания. draw.io инвертирует дефолт mxGraph (`mxCellEditor.prototype.escapeCancelsEditing = false`) — **Escape коммитит**. Наш `KDoc EditorDiagramOverlay.kt:1307-1308` уже описывает паритет.

---

## 2. Проектные решения

### 2.1. Что берём у draw.io дословно

| поведение | draw.io | статус у нас |
|---|---|---|
| **Геометрия фигуры авторитетна**; hug opt-in, не автомат | `mxGraph.autoSizeCells = false`, draw.io не переопределяет | принять — не пересчитывать на загрузке |
| **Hug мерит НЕРАЗОРВАННО** → расширяет по ширине | `getPreferredSizeForCell` без `textWidth` → `whiteSpace:nowrap` | принять |
| **Hug пересчитывает от якоря выравнивания**, а не растит от левого-верхнего | `mxGraph.js:5528` | принять |
| **Escape = commit** | `escapeCancelsEditing = false` | **уже есть** (`EditorDiagramOverlay.kt:1307-1308`) — не трогать |
| **Blur = commit** | `invokesStopCellEditing = true` | **сломано** — `:630`, `:660` делают `textEdit = null` без dispatch → чинить |
| Лейбл скрыт во время редактирования | `mxCellEditor`: `visibility = 'hidden'` | плита совпадёт с коробкой лейбла (P2) |

### 2.2. Что улучшаем осознанно (пользователь сказал «как в drawio» — расхождение требует причины)

**1. Клипаем, а не выпускаем текст за фигуру.**
draw.io по умолчанию `overflow=visible` и выпускает текст за контур (foreignObject 1×1 + `translate(-50%,-50%)`). У нас так **нельзя**: роутинг и nudging (`EdgeRouter`, `EdgeNudging`) трактуют `node.bounds` как препятствие и **ничего не знают о вытекшем тексте** — вытекший лейбл будут пересекать рёбра. Плюс в дизайн-инструменте текст, вылезающий из фигуры, читается как поломка. Компенсация — hug + lint-правило.
*Побочно:* текущий CENTER-клип **симметричен** (`DiagramText.kt:134`) — переросший текст теряет и верх, и низ. Худший вариант усечения → top-anchor.

**2. Инскрайбим лейбл эллипса.**
draw.io **не** инскрайбит (`mxShape.getLabelMargins` → null; `mxEllipse` не переопределяет ни `getLabelBounds`, ни `getLabelMargins`) — текст просто пересекает кривую. Но draw.io текст **выпускает**, а мы **клипаем** (расхождение 1). В связке «bbox-коробка + клип» текст обрезается **внутри** фигуры — строго хуже и draw.io, и вписывания.

**3. `hug`, а не `autosize`.**
Проект уже поставляет `width (fixed|hug|fill [N] [min N] [max N])` (`SKILLS/SLM.md:135`), обеспеченный `enum class SizingMode { Fixed, Hug, Fill }` (`DesignLayoutModel.kt:4`). Diagram CNL, изобретающий `autosize` для того же понятия, **раскалывает словарь авторинга**, который агент держит в голове одновременно с SLM.md.

**4. Enter = commit, Shift+Enter = newline.**
draw.io: Enter вставляет перенос, коммит только Ctrl+Enter (`enterStopsCellEditing = false`) — наследие HTML-textarea. Современная конвенция (Figma) — Enter коммитит. Наш редактор уже `singleLine = true` (`:1405`).

### 2.3. Архитектурные решения

**Шов измерения — чистый инжектируемый интерфейс, НЕ Compose-зависимость.**
Прецедент уже в проде и его никто не заметил:

```kotlin
// engine/ir/.../layout/DesignTextMeasurer.kt:7-13
/**
 * Platform text measurement injected into the layout engine so the engine itself
 * stays pure Kotlin. The Compose UI layer provides the real implementation; tests
 * use a deterministic fake.
 */
interface DesignTextMeasurer {
    fun measure(text: ResolvedText, maxWidth: Double? = null): MeasuredText
```

Пурный `ApproximateTextMeasurer` (`:43`), инжект в `DesignLayoutEngine.kt:105`, реальный из Compose-слоя через `SceneRenderer.kt:40-46`. `SizingMode.Hug` — text-driven sizing, **работающий сегодня**. Копируем дословно; `api(projects.subsystems.typography)` в ядро **не добавляем** (легально — модуль чистый KMP — но тащит `RichText`/`TypographyStyle` в чистую модель и всё равно оставляет character-wrap аппроксимацию).

**Новые ключи — на узел, НИКОГДА на `DiagramStyle`.**

```kotlin
// DiagramStyle.kt:40-41 — собственный KDoc
/** Visual style shared by nodes and edges. `null` colors mean "theme default" … */
```

Один инспекторский контрол на оба (`EditorInspectorPane.kt:4816-4824`, KDoc *«Shared node/edge style block»*), один `styleGroup` в CNL для обоих (`DiagramCnlWriter.kt:253`). Ключи wrap/overflow/hug на `DiagramStyle` дали бы **каждому ребру** бессмысленный autosize в модели, грамматике и UI.

**Раскол сторов — не баг, а грамматика.** `SKILLS/SLM-diagrams.md:88` («basic shapes have no payload fields; use `label` for the caption») против таблицы `:109` (`actor`, `use-case`, `package` → required `«name»`, repeatable items «none»). Рендерер её честно реализует. Инверсия («рендерер предпочитает `labels`») сломала бы `DiagramCnlDocExamplesTest` и заставила бы писать избыточный `label «…»` в каждое UML-предложение. **Дефект односторонний: неправ только редактор.**

### 2.4. 🔴 Ловушка измерения — самая дорогая в плане

```kotlin
// ComposeTypographyMeasurer.kt:107-110
exactWidth -> {
    val width = ceil(contentMax).toInt().coerceAtLeast(0)
    Constraints(minWidth = width, maxWidth = width)   // min == max
}
// :167
val width = paragraphs.maxOfOrNull { it.x + it.result.size.width.toDouble() } ?: 0.0
```

`drawDiagramLabel` зовёт `layout(..., exactWidth = true)` (`DiagramText.kt:126-131`). При `min == max` `result.size.width` **есть** ширина коробки.

> **`laidOut.measured.width` физически не может вернуть натуральную ширину текста — он ВСЕГДА равен `box.width`.**

Наивный autosize, переиспользующий этот вызов, получит ширину коробки и посчитает **неподвижную точку на мусоре**. Натуральная ширина — только через `measure(rich, maxWidth = null)`. (`measured.height` — осмыслен.)

---

## 3. Фазы

### P0 — Блокеры: текстовая проводка редактора

**Цель.** Двойной клик по любому UML-узлу открывает редактор **с его текстом**, коммит меняет текст на канве, в источник не попадает осиротевший `label «…»`. Смена типа узла не стирает текст.

**Шаги.**
1. `ops/NodeOps.kt`: `public fun DiagramNode.primaryText(): String?` и `public fun DiagramGraph.setNodeText(id, text): DiagramGraph`. Один `when (payload)` на 17 kind'ов: UseCase/Actor/Package/Component/Deployment/Class/State/Activity/Lifeline/Entity → `name`; Note → `text`; Container/Swimlane → `title`; BasicShape/Flowchart/Bpmn → делегируют в `setNodeLabel` (`:89-98`). **`when` без `else`** — новый payload должен ломать компиляцию, а не молча терять текст.
2. `DiagramEditing.kt:124-126` → `it.setNodeText(...)`. Имя интента не трогать (публичный API, 4 ссылки).
3. Seed: `EditorDiagramOverlay.kt:1952-1953` → `?.primaryText().orEmpty()`.
4. Инспектор: `EditorInspectorPane.kt:4795-4796` → `element.primaryText().orEmpty()` — **тем же аксессором**, чтобы канва/инспектор/inline не разошлись в третий раз.
5. `SetDiagramNodePayload` (`DiagramEditing.kt:130-131`): читать `primaryText()` до замены, писать `setNodeText()` после. Сейчас кормится шаблоном палитры (`EditorInspectorPane.kt:4781-4783` → `UmlUseCaseNode(name = "Use case")`) и **молча подменяет пользовательский текст плейсхолдером**.
6. Blur = commit: `:630`, `:660` — вызвать `commit()` перед `textEdit = null`.
7. Guard в CNL: `DiagramCnlReader.kt:424-428` принимает `word == "label"` на любом типе → payload-kind guard + `diagnostics.error(...)`.

**Тесты.**
- `:subsystems:diagrams` — `setNodeText` на UseCase меняет `payload.name`, не трогает `labels`; на BasicShape наоборот; **табличный** round-trip `primaryText()`/`setNodeText()` по всем 17 kind'ам.
- `:shared:jvmTest` — `DiagramEditorReducerTest`: SetDiagramNodeLabel на use-case меняет `payload.name` **и** патчит CNL в head-позицию, а не дописывает `label «…»`. **Сегодня — ноль тестов.**
- `:shared:jvmTest` — SetDiagramNodePayload use-case→class сохраняет текст.
- `:subsystems:diagrams-slm` — `label «orphan»` на типизированном payload'е даёт диагностику.

**Риск.** Средний. `primaryText()` обязан быть исчерпывающим (забытый kind = исходный баг). Guard может зажечься на документах, куда inline-редактор уже записал осиротевший label — это desired (диагностика, не падение); проверить на `project-structure.layout.md`.

---

### P1 — Модель: измеритель + общая раскладка (+ SVG как первый потребитель)

**Цель.** В чистом ядре появляется шов измерения и **одна** функция раскладки лейбла для трёх поверхностей (рендерер, экспортёр, редактор). Audit-SVG начинает переносить строки — **verification-цикл прозревает**.

> **Почему SVG здесь, а не в конце.** У `diagramToSvg` ноль продакшн-вызывающих — это не баг пользователя. Но `SlmDiagramAuditTool.kt:53` — единственный глаз агентского цикла, и без переноса строк он не способен показать дефект лейбла. Обёртка **падает из шва естественно** (экспортёр живёт в `:subsystems:diagrams`), поэтому дешевле сделать её здесь и получить проверяемость P2/P3.

**Шаги.**
1. `text/DiagramTextMeasurer.kt` — калька `DesignTextMeasurer.kt:12`:
   `interface DiagramTextMeasurer { fun measure(text: String, style: DiagramTextStyle, maxWidth: Double? = null): MeasuredDiagramText }`, `MeasuredDiagramText(width, height, lines)`. **Ни одной новой зависимости** у `:subsystems:diagrams`.
2. `class ApproximateDiagramTextMeasurer(averageCharWidthFactor: Double = 0.54)`. Два отличия от существующих пурных: **(а)** фактор 0.54, не 0.6 (реальные Inter: кир. 0.540 / лат. 0.521; 0.6 переоценивает на 11.1%); **(б)** перенос **по словам** — `Measure.kt:142-155` и `DesignTextMeasurer.kt:97-110` рвут на любом символе (`if (contentMax != null && lineHasContent && lineWidth + advance > contentMax) flushLine(style)`), Compose рвёт по словам, из-за чего пурный fake **недосчитывает строки**. `:engine:ir`'ский 0.6 **не трогать** — зачернит golden'ы layout-движка.
3. `ComposeDiagramTextMeasurer` в `:subsystems:diagrams-compose` (build уже даёт `api(projects.subsystems.diagrams)` + `api(projects.subsystems.typographyCompose)`, `:38-44`). Паттерн — `SceneRenderer.kt:40-46`.
4. `geometry/NodeLabelBox.kt`: `fun DiagramNode.labelBox(padding: Double): DiagramRect` через `perimeterKind()`: RECTANGLE → `bounds.inset(p)`; ELLIPSE → вписанный `w/√2 × h/√2` по центру + inset; RHOMBUS → вписанный `w/2 × h/2` + inset; OUTLINE → `bounds.inset(p)` (v1-пробел, задокументировать).
5. Обратная функция: `boundsForLabel(kind, textWidth, textHeight, padding): DiagramSize` — **точная инверсия**. ELLIPSE → `(textWidth + 2*padding) * √2`. Пара покрыта property-тестом.
6. Экспортёр: `diagramToSvg(graph, routes, options, measurer: DiagramTextMeasurer = ApproximateDiagramTextMeasurer())` (`DiagramSvgExport.kt:76`). `svgText` (`:540-556`) → `<text>` + `<tspan x dy>` по `measure(...).lines`. `nodeCenterText` (`:215-227`) → на `primaryText()` из P0, убрав labels-first-приоритет, расходившийся с канвой.

**Тесты.**
- `NodeLabelBoxTest`: use-case 930×260, `labelBox(8.0)` → **657.6×183.8** (±0.1), а не 914×244.
- property: `boundsForLabel(kind, labelBox(node, p).size, p) == node.bounds` для ELLIPSE/RHOMBUS/RECTANGLE.
- `ApproximateDiagramTextMeasurerTest`: перенос по словам; 42-симв. кириллица @13px → **294.8px ±2%**.
- `DiagramSvgExportTest`: лейбл с переносом даёт >1 `<tspan>`; ни один не шире коробки.

**Риск.** Средний. Архитектурный — держать `:subsystems:diagrams` без Compose (новый интерфейс не добавляет зависимостей вообще). Числовой — пурный fake и Compose никогда не совпадут точно; SVG и канва разойдутся на единицы px. Приемлемо для audit-глаза; **точные пиксели в golden'ы не закладывать**.

---

### P2 — Рендерер + inline-редактор на общей коробке

**Цель.** Текст не вылезает за кривую и не обрезается прямоугольно внутри неё. Вход в edit-режим **не сдвигает текст**: плита совпадает с коробкой лейбла, а не накрывает фигуру.

**Шаги.**
1. `DiagramNodeRenderer.kt:170-173` (use-case), `:101` (`inset(4.0)`), `:106` (`inset(6.0)`) → `node.labelBox(padding)`. Отступы 4/6/8 становятся аргументом, геометрия — из `perimeterKind()`.
2. `DiagramText.kt:132-136`: при `measured.height > box.height` брать `box.top`, а не `box.top + (box.height - measured.height)/2.0` (`:134`).
3. `diagramTextEditRect` (`EditorDiagramOverlay.kt:1911-1912`) → `node.labelBox(padding)`, **тот же вызов, что рендерер**. Это же убирает «вырезанный эллипс» со скриншота 2.
4. Типографика плиты (`:1404-1410`): сейчас `singleLine = true`, всегда `FontWeight.Normal`, всегда `TextAlign.Center` — тогда как рендерер переносит по `box.width` и использует weight 600/700 (класс `:542`, package `:207`, container `:150`) и Left+TOP для заметок (`:189-197`). Прокинуть per-payload `(weight, align, verticalAlign)` из одного места. Коммит `446750a` («render-matched typography») сопоставил **только размер шрифта и внешний прямоугольник** — отсюда и остался прыжок.
5. Enter = commit / Shift+Enter = newline (снять `singleLine`); Escape/blur уже в P0.
6. Заодно: `.padding(horizontal = 4.dp)` против `.width((widthPx - 8f).toDp())` (`:1380-1388`) — смешение dp и raw px, поле шире слота при density > 1 (wasm обычно 2).

**Тесты.**
- `:subsystems:diagrams-compose` — коробка use-case = `labelBox`, а не `bounds.inset(8.0)`.
- `:subsystems:diagrams` — углы `labelBox` внутри `outlinePath()` (hit-test контура) для ELLIPSE/RHOMBUS.
- `:shared:jvmTest` — `diagramTextEditRect` == коробка рендерера (регрессия на расхождение).
- wasm: вход в edit не сдвигает текст; фигура не пропадает под плитой.

**Риск.** ⚠️ **Средний-высокий — визуальная регрессия по всем диаграммам.** Вписанный прямоугольник эллипса заметно меньше bbox (**×0.707**): лейблы, ранее влезавшие в `bounds.inset(8.0)`, начнут переноситься/клипаться. **P3 обязан идти сразу следом**, иначе тесные авторские эллипсы (палитра 150×70) станут хуже, а не лучше. Прогнать `SlmDiagramAuditTool` по `project-structure.layout.md`, сравнить SVG до/после.

---

### P3 — Hug: размер узла от текста

**Цель.** Узел, созданный редактором или отредактированный inline, подгоняет размер под текст по draw.io-семантике. Авторские документы на загрузке не трогаются.

**Шаги.**
1. Модель — на **узле**: `DiagramNode(..., val sizing: DiagramNodeSizing = DiagramNodeSizing.Fixed)`, `enum class DiagramNodeSizing { Fixed, Hug }`.
2. Op: `public fun DiagramGraph.fitNodeToText(id, measurer: DiagramTextMeasurer, padding: Double): DiagramGraph`. Алгоритм draw.io дословно: (1) `measure(text, style, maxWidth = null)` — **неразорванно**; (2) `boundsForLabel(perimeterKind(), …)` из P1; (3) пересчёт x/y **от якоря выравнивания** (`mxGraph.js:5528`); (4) минимумы per-kind.
3. 🔴 **В комментарии у op'а — запрет на переиспользование `drawDiagramLabel`'s layout** (см. §2.4).
4. Триггеры (opt-in, но **default-on там, где ущерб**): коммит inline-редактора при `sizing == Hug`; создание из палитры (`EditorDiagramOverlay.kt:203` — стартовый размер = результат измерения плейсхолдера); явное «Подогнать под текст». **Не на загрузке.**
5. Унифицировать дефолты — сейчас **три независимых набора**: use-case 150×70 (`EditorDiagramOverlay.kt:203`) / 160×70 (`DiagramTemplates.kt:209`); актор 60×90 / 60×100 (`:203-208`); класс 160×108 / 180×96 (`:54-58`) / 180×(36+20·rows) (`TextParsing.kt:155-162`). Один `DiagramNodeDefaults`. Ориентир draw.io: use case 140×70, class 160×90, actor 30×60.
6. Инвариант класса под интерактивом: `TextParsing.kt:155-157` кодирует «класс высотой в свои компартменты», но `UmlOps.addClassField/addClassMethod` (`:19-36`) добавляют строки через `updateClass` (`:77-84`), **не трогая height** — импортированный класс размерен верно, а после добавления поля строки лезут за коробку.

**Тесты.**
- `FitNodeToTextTest` **на числах репро**: use-case, 42 симв., фактор 0.54/13px, padding 8 → ширина ≈ `(294.8 + 16)·√2 ≈ 439`, **а не 930**. Явно зафиксировать 930 как регрессию.
- hug пересчитывает от центра (центр не сместился).
- `Fixed`-узел `fitNodeToText` не меняет.
- `:shared:jvmTest` — коммит inline на hug-узле меняет текст **и** w/h, пишет оба в CNL.
- `addClassField` растит height по инварианту.

**Риск.** ⚠️ **Высокий.** (1) **Детерминизм:** hug на загрузке сделал бы один `.layout.md` **разным на JVM и wasm** (разные метрики) → hug только в момент правки, `<w> by <h>` остаётся авторитетным кэшем. (2) Унификация дефолтов меняет размеры шаблонов — прогнать golden'ы. (3) `fitNodeToText` требует измеритель в `:shared` — прокинуть `ComposeDiagramTextMeasurer` тем же швом, что артборд, **не создавать внутри редьюсера** (правило инъекции зависимостей).

---

### P4 — Авторинг: CNL round-trip `hug`, SKILLS, инспектор, i18n

**Цель.** `hug` переживает сохранение; агент-автор получает **формулу** вместо угадывания; человек включает hug из инспектора на обоих языках.

**Шаги.**
1. ⚠️ **CNL строгий** — неизвестный ключ это **жёсткая ошибка** (`DiagramCnlReader.kt:454` `else -> return fail("unknown word …")`, `:1018-1019` для style-ключей); пути сохранения неизвестных ключей нет. Reader + writer + документ меняются **атомарно**.
2. Reader: бареворд `hug` на уровне узла → `sizing = Hug`. **`<w> by <h>` остаётся обязательным** и с hug — это последний измеренный результат: (а) детерминизм без измерителя, (б) старые ридеры получают размер, (в) round-trip не зависит от платформенных метрик.
3. Writer: `DiagramCnlWriter.kt:100` — дописать `hug` при `sizing == Hug`. Голова предложения (`quote(payload.name)`, `:149`) уже пишет текст — P0 лишь направил редактор в неё.
4. 🔴 **`SKILLS/SLM-diagrams.md` — главный deliverable фазы.** Он компилируется в продукт (`shared/build.gradle.kts:25`) и именно он заставляет агента печатать `930 by 260`:
   - в общую продукцию (`:76`) и в «Size and position are required» (`:80`) добавить `hug` + правило «для фигур с текстом пиши `hug` и приблизительный размер — редактор уточнит»;
   - **дать формулу**: ширина ≈ `len(text) × fontSize × 0.55`; для эллипса (use-case) `× √2`; для прямоугольника `× 1`;
   - заменить единственный образец (`:123`, `180 by 80`) на **пару** — короткий английский и длинный кириллический, чтобы экстраполяция имела **две точки**;
   - явно запретить `label` на типизированных payload'ах: сейчас `:76` разрешает его всем типам, а `:109` говорит «repeatable items — none» — **документ противоречит сам себе**.
5. Инспектор: контрол sizing (Fixed|Hug) + «Подогнать под текст» рядом с полем label (`EditorInspectorPane.kt:4794-4801`). **Не** в `DiagramStyleControls` (`:4816-4824`) — он shared с рёбрами.
6. i18n: строки в группу `inspector` (`InspectorStrings.kt`, рядом с `label` `:219` и `style` `:220`), **En + Ru**, через `LocalStrings.current.inspector.*`. ⚠️ **Ловушка round-trip-селектов** (CLAUDE.md): если значение матчится назад по отображаемой строке — локализовать **обе** стороны одним резолвером, иначе в RU выбор молча ломается.

**Тесты.**
- `DiagramCnlRoundTripTest`: `hug` переживает read→write→read; `hug` и `<w> by <h>` пишутся оба.
- `DiagramCnlDocExamplesTest` уже требует, чтобы **каждый** пример документа компилировался и канонически round-trip'ился — **правки SKILLS тест-энфорсятся**; новые образцы добавить туда.
- Узел без `hug` → `sizing = Fixed` (обратная совместимость всех документов).
- `:shared:jvmTest` — интент смены sizing патчит источник, а не остаётся in-memory.

**Риск.** Средний. Форвард-несовместимость: документ с `hug` на билде без P4 падает `unknown word`, а не деградирует — свойство строгого CNL; reader и writer релизить одним куском.

---

### P5 — Lint + паритет экспорта

**Цель.** Автоматический глаз проекта начинает ловить **именно тот класс дефектов, который пропустил**.

**Шаги.**
1. `DiagramLintFinding.NodeLabelFit`: (а) текст не влезает в `labelBox()`; (б) фигура значительно больше hug-размера (репро: **930 против 417 = ×2.23**; порог ~×1.6 по площади). Сегодня `lintDiagram` (`:114-128`) гоняет ровно 6 правил (NodeOverlap, EdgeThroughNode, AnchorBunch, EdgeOverlap, CrossingHotspot, LabelOverNode) — **ни одно не сравнивает лейбл узла с его же коробкой**; `labelCharWidth`/`labelHeight` (`:40-43`) обслуживают только чужие узлы под лейблом ребра (`:436-440`).
2. `DiagramLintFinding` — `sealed interface` (`:51`); добавление варианта source-compatible, т.к. единственный потребитель имеет `else -> Unit` (`SlmDiagramAuditTool.kt:77`). Добавить ветку в audit-репорт.
3. Мерить через `DiagramTextMeasurer` (дефолт `ApproximateDiagramTextMeasurer(0.54)`), не через `labelCharWidth = 7.0`. **Заметка:** 7.0 на 13px = фактор 0.538 против реальных 0.540 (промах 0.4%) — константа откалибрована **хорошо**; для рёбер (12px) переоценивает на ~8%. Это **не** «недооценка кириллицы», как утверждало дознание. Без нужды не трогать.
4. Метрики компартментов (не user-facing, но это глаз цикла): класс `DiagramSvgExport.kt:234-235` (`32.0`/`18.0`) против канвы `bounds.height / totalRows` (`DiagramNodeRenderer.kt:507`); ER `:269` (`28.0`) против `TITLE_BAND_HEIGHT = 26.0` (`:43`); lifeline `:331` (`minOf(40.0, bounds.height)`) против общего `umlLifelineHeadHeight` = `minOf(36.0, height * 0.25)` (`UmlNodes.kt:49`) — **экспортёр не зовёт хелпер, существующий ровно ради согласия роутера и рендерера**.
5. Вернуть текст, который канва рисует, а экспорт роняет: стереотипы component/deployment (`drawStereotypedName` `:753`/`:821`), заголовки полос swimlane (`:288-310`), таб package (ветки в `appendNode` нет).

**Тесты.**
- `DiagramLintTest`: use-case 930×260 + 42-симв. лейбл → `NodeLabelFit` (oversized); тот же лейбл в 160×70 → `NodeLabelFit` (overflow); hug-размер (~440×125) → пусто. **Это тест-регрессия на исходный скриншот.**
- `DiagramSvgExportTest`: метрики класса/ER/lifeline совпадают с канвой; стереотип component и заголовки полос присутствуют.
- Все `DiagramTemplates` проходят lint без `NodeLabelFit` (иначе дефолты P3 подобраны неверно).

**Риск.** Низкий для продукта (нулевой охват), средний для тестов: правило может зажечься на шаблонах и `project-structure.layout.md` — это сигнал, но пороги калибровать по реальным документам, чтобы не стать шумом (память проекта: aeration 60→17, «структурный пол» уже наступали).

---

## 4. Проверка

### 4.1. По фазам

| фаза | зелёный критерий |
|---|---|
| P0 | `:subsystems:diagrams:jvmTest` + `:shared:jvmTest`. **Ручной wasm:** двойной клик по use-case → поле **с текстом**; правка → текст меняется **на канве**; в `.layout.md` в head-позиции `«…»`, **никакого `label «…»`**. |
| P1 | `:subsystems:diagrams:jvmTest`. Audit-SVG показывает `<tspan>`'ы. **Компиляция `:subsystems:diagrams` без Compose в classpath — архитектурный gate.** |
| P2 | `:subsystems:diagrams-compose:jvmTest` + `:shared:jvmTest`. **wasm:** текст в пределах кривой; вход в edit не сдвигает текст; фигура не исчезает. |
| P3 | `:subsystems:diagrams:jvmTest` + `:shared:jvmTest`. **wasm:** создать use-case из палитры, напечатать длинную русскую строку → эллипс ~440px, **не 930**. |
| P4 | `:subsystems:diagrams-slm:jvmTest` (round-trip + DocExamples) + `:shared:jvmTest`. **wasm:** переключить язык RU → инспектор переведён, селект sizing **не ломается** (ловушка round-trip). |
| P5 | `:subsystems:diagrams:jvmTest`. Audit-lint печатает `NodeLabelFit` на репро-документе. |

Полный прогон: `./gradlew :subsystems:diagrams:jvmTest :subsystems:diagrams-slm:jvmTest :subsystems:diagrams-compose:jvmTest :shared:jvmTest`

### 4.2. SlmDiagramAuditTool + SVG-маршрут

Env-gated jvmTest, no-op без переменных (`SlmDiagramAuditTool.kt:28-29`), **флага `--rerun` нет** (вопреки памяти проекта):

```bash
SLM_AUDIT_FILE=/path/to/repro.layout.md SLM_AUDIT_OUT=/tmp/audit \
  ./gradlew :subsystems:diagrams-slm:jvmTest --tests "*.SlmDiagramAuditTool"
```

Пишет `<node-id>.svg` (`:53`) и `lint.txt` (`:54`). **До P1 этот маршрут слеп к дефектам лейбла** — `svgText` не переносит строки. После P1 — рабочий глаз: снять SVG **до** правок как baseline, сравнивать после P2/P3, рендерить headless-Chrome для визуального сравнения.

Репро-документ (в дереве его нет — единственный tracked `.layout.md` это `project-structure.layout.md`, use-case-диаграмм и кириллицы скриншота в репозитории нет, документ пользователя **внешний**):

```md
Node use-case uc1 «Вести контакты собственников и совета дома» 930 by 260 position 160 40
Node use-case uc2 «Вести контакты собственников и совета дома» 160 by 70 position 160 380
```

### 4.3. wasm-first браузерная проверка (обязательна — CLAUDE.md)

Продукт **wasm-first**; UI-правки агент проверяет **сам**, не перекладывая на пользователя.

- Порт **не фиксированный**: `preview_start` (name `webApp`, `autoPort: true`), но webpack биндится сам и каскадит 8080→8081→…; **истинный порт — из `preview_logs`**.
- Готовность: `curl -s -o /dev/null -w '%{http_code}' http://localhost:<port>/` → 200. Первая сборка **~30с+** — раньше не навигировать (таб застрянет на `chrome-error`). Осиротевшие webpack-серверы — убить и стартовать **одним чистым** `preview_start`.
- Dev-сервер — блокирующая continuous-задача: **только в фоне**.
- **Гочи Compose-canvas:** редактор — один canvas в shadow-root, DOM-инспекта текста нет → только скриншоты + синтетические жесты. Цепочка `pointermove→pointerdown→pointerup`; координаты skiko — backing-пиксели (`clientX = cssX * devicePixelRatio`); `Modifier.clickable` требует быстрый тап (down→up ~70мс в **одном** вызове), иначе Compose трактует как long-press.
- **Двойной клик** (главный сценарий P0) — две пары down/up в одном вызове с коротким интервалом.

**Чеклист wasm после P3:** создать use-case из палитры → двойной клик → поле **с текстом** (не пустое) → напечатать длинную русскую строку → Enter → (1) текст на канве изменился, (2) эллипс подогнался ~440px, (3) текст внутри кривой, (4) фигура **не исчезла** под плитой, (5) в `.layout.md` head `«…»` + `hug` + новые w/h, **без `label «…»`**.

---

## 5. Вне охвата

1. **Иконка-глиф** перед текстом (скриншот 1) — не воспроизводится: `:170-173` рисует только контур и `payload.name`. «tofu» **мертва** (Inter/Roboto покрывают А–я, проверено разбором cmap format-4 tracked-шрифтов). Остаются: глиф внутри самой строки `payload.name` либо оверлей (бейдж аннотации / иконка блокировки). Нужен живой скриншот или исходный `.layout.md`; **ни на одно решение плана не влияет**.
2. **Расхождение ширины** ~560px (скриншот) против 294.8px (Inter@13px), ×1.9 — вероятно zoom канвы ≠ 1.0. **Не трогать константы padding'а hug, пока не приколочено живым замером.**
3. **Вписанные прямоугольники для OUTLINE** (triangle, hexagon, parallelogram, trapezoid, cylinder, cloud). P1 даёт точные для ELLIPSE/RHOMBUS; OUTLINE — на `bounds.inset(p)` с задокументированным пробелом. Треугольник страдает сильнее прочих.
4. **`labels` как List** — рендерится только `[0]` (`:219`, `DiagramSvgExport.kt:216`, `:1953`), writer пишет все (`:106`). Осмысленным сделала бы placement-модель уровня `DiagramEdgeLabel(label, position, offsetX, offsetY)` для узлов.
5. **Выравнивание одинаковых полос:** container — `AlignHorizontal.Left` (`:150`), swimlane — дефолтный Center (`:252`) при идентичной геометрии.
6. **Тихий DROP строк ER** за коробкой (`:350-351`) — единственный путь переполнения, который не клипает, а **удаляет данные без следа**.
7. **Хардкод шрифтов в SVG** (`Helvetica, Arial` @12) против канвы (Inter @13). Полный паритет требует эмбеддинга шрифта.
8. **Глиф-префиксы**, вбитые в строку (`"${member.visibility.symbol} ${member.text}"` `:550`, `"PK "`/`"FK "` `:352-356`, `"«$stereotype»"` `:528`/`:775`): их ширина попадает внутрь переноса → длинная сигнатура переносится под собственный `+`. draw.io резервирует `spacingLeft`.
---

## 6. E2E workflow исполнения

Исполнительный контракт для сессии, которая делает P0–P5. Порядок жёсткий, гейты обязательные.

### 6.0. Инварианты всей работы

- **Ветка.** Работать в `feature/uml-label-drawio-parity` от текущего `main`. В `main` не коммитить.
- **Чужие правки в дереве.** На старте дерево содержит **незакоммиченную работу по роутингу рёбер**
  (`EdgeRouter.kt`, `EdgeNudging.kt`, `RoutedEdge.kt`, `Arrowheads.kt`, `DiagramSvgExport.kt`,
  `DiagramEdgeRenderer.kt`, `EditorDiagramOverlay.kt`, + 2 новых теста роутинга). Она **не твоя**:
  не откатывать, не «чинить», не включать в свои коммиты. `DiagramSvgExport.kt` и
  `EditorDiagramOverlay.kt` правит и P1/P2 — коммитить **только свои хунки** (`git add -p`).
  Если её закоммитили/убрали до старта — просто продолжай.
- **Один коммит на фазу**, сообщение — что и почему, с `Co-Authored-By`.
- **Гейт фазы = зелёные тесты фазы (§4.1) + свои новые тесты.** Красный гейт останавливает работу:
  чинить, а не идти дальше. Тест, который не падал бы на текущем баге, — бесполезен: **сначала
  убедиться, что новый тест КРАСНЫЙ на старом коде**, потом чинить.
- **Не расширять охват.** §5 «Вне охвата» — не трогать. Найденное записывать в конец этого файла
  в «Находки исполнения», а не чинить попутно.
- **Субагенты в worktree получают базу от `main`/родителя, а НЕ твой неотправленный HEAD.**
  Если делегируешь в `isolation: "worktree"` — первым шагом `git reset --hard <твой-HEAD-sha>` +
  grep-самопроверка, иначе агент работает против устаревшего кода.

### 6.1. Стадии и гейты

| # | стадия | выход | гейт |
|---|---|---|---|
| 0 | **Ground** | прочитать этот файл целиком + `CLAUDE.md` + `.claude/rules/*`; ветка; baseline `./gradlew :subsystems:diagrams:jvmTest :subsystems:diagrams-slm:jvmTest` | exit 0 (ожидаемо зелёный **при живых дефектах** — это дыры покрытия) |
| 1 | **P0** блокеры проводки | `nodeText()`/`setNodeText()`, blur=commit, CNL-guard | тесты P0 + **wasm-чек §4.3**: двойной клик по use-case → поле **с текстом**, правка видна на канве |
| 2 | **P1** измеритель + коробка + SVG | шов, `labelBox`/`boundsForLabel`, `<tspan>` | тесты P1 + **архитектурный gate: `:subsystems:diagrams` компилируется без Compose в classpath** + baseline-SVG снят (§4.2) |
| 3 | **P2+P3 вместе** | коробка в рендерере/редакторе + hug | тесты P2 и P3 + **wasm-чек полный чеклист §4.3** |
| 4 | **P4** авторинг | CNL `hug`, `SKILLS/SLM-diagrams.md`, инспектор, i18n | тесты P4 + **wasm-чек RU**: инспектор переведён, селект sizing не ломается |
| 5 | **P5** lint + паритет | `NodeLabelFit`, метрики экспорта | тесты P5 + audit-lint печатает `NodeLabelFit` на репро-документе |
| 6 | **Финал** | полный прогон + review + отчёт | `./gradlew :subsystems:diagrams:jvmTest :subsystems:diagrams-slm:jvmTest :subsystems:diagrams-compose:jvmTest :shared:jvmTest` |

> **P2 и P3 — одна стадия, не две.** P2 в одиночку сужает коробку эллипса в 1.41 раза и делает
> тесные авторские эллипсы **хуже**, чем сейчас. Между ними нет пригодного к показу состояния:
> коммита два, гейт один — общий, после P3.

### 6.2. Стадия 3 — обязательное сравнение до/после

P2+P3 — единственная стадия с реальным риском визуальной регрессии **по всем существующим
диаграммам**. Поэтому:

1. **До** правок: `SlmDiagramAuditTool` (§4.2) по `project-structure.layout.md` + всем
   `DiagramTemplates` → сохранить SVG как baseline.
2. **После**: тот же прогон, отрендерить обе версии headless-Chrome, **сравнить глазами**.
3. Любая фигура, где текст стал вылезать/клипаться сильнее прежнего, — **дефект P3**
   (дефолты подобраны неверно), а не «приемлемая цена P2».

### 6.3. Финальный отчёт (стадия 6)

Не «сделано». Конкретно: репро-числа **до и после** (930 → ~440); что показал wasm-чек
(скриншоты); какие решения §2 подтвердились на практике, а какие пришлось пересмотреть **и почему**;
что осталось из §5 и что дописано в «Находки исполнения». Провалы называть провалами
с выводом теста.

### 6.4. Когда останавливаться и спрашивать

- Решение из §2 на практике **не работает** (а не «неудобно») — остановиться, изложить, спросить.
- Гейт красный **по причине вне охвата** (чужая правка роутинга, инфраструктура) — спросить.
- P4 требует правки `SKILLS/SLM-diagrams.md` — это **продуктовый контракт для ИИ-авторов**:
  показать diff в отчёте явно, не растворять в фазе.
- Всё остальное — решать самому по §2 и не спрашивать.

---

## 7. Находки исполнения

Заполнено сессией, отгрузившей P0–P5 (ветка `feature/uml-label-drawio-parity`, 5 коммитов).

### 7.1. Ошибки самого плана (найдены исполнением)

1. **§P0 шаг 1 перечисляет 16 kind'ов, а их 17.** Пропущен `TableNode`
   (`model/TableNode.kt:10`). У него нет node-level подписи вообще — текст живёт в
   `cells[].label`. Решение: `primaryText()` → `null`, `setNodeText()` → no-op, покрыто тестом
   `tableHasNoNodeLevelCaptionAndIsLeftAlone`. Исчерпывающий `when` без `else` (как требовал
   план) поймал бы это на компиляции — но только если знать про 17-й kind.
2. **🔴 §1.3 арифметика вписанного прямоугольника смешивает padded и unpadded.** Таблица
   сравнивает `bounds.inset(8.0)` = 914×244 (с padding) с «вписанным» 657.6×183.8 (БЕЗ
   padding), и §P1 переносит эти числа в ожидание `labelBox(8.0)`. По формуле самого плана
   («вписанный + inset») `labelBox(8.0)` для 930×260 = **641.6×167.8**, а 657.6×183.8 —
   это `labelBox(0.0)`. Проверка: §P3 ожидает `(294.8 + 16)·√2 ≈ 439`, что согласуется
   именно с inscribe-then-inset. Реализована формула, тест пиннит оба числа.
3. **§2.4 сформулирована шире, чем верна.** `exactWidth` игнорируется при `maxWidth = null`
   (`ComposeTypographyMeasurer.kt:104-112`: `contentMax == null -> Constraints()`), так что
   `measured.width` — ширина коробки **только на call-site'ах с непустым maxWidth**
   (`drawDiagramLabel` — именно такой). Практический вывод плана верен и подтверждён; ловушка
   реальна ровно там, где он и указал.
4. **§P5 «порог ~×1.6 по площади» несовместим с «930 против 417 = ×2.23»** — ×2.23 это
   отношение ШИРИН, а не площадей. Реальный репро — ×12 по площади относительно hug-размера.
   Подробнее в 7.2.
5. **§4.2 репро-документ не компилируется как есть** — нужен frontmatter + `#` заголовок +
   `## Diagram:` контейнер. Рабочая версия — в 7.4.

### 7.2. Калибровка `NodeLabelFit` (правило чуть не стало шумом)

Первая версия правила (площадь узла / площадь hug-размера, порог 2.5) зажглась на **обычном
шаблоне** `state-machine`: `'idle' 140x48 vs 44x32 needed`. Причина структурная: короткая
подпись хагается почти в ноль, поэтому «подпись против коробки» осуждает любую нормальную
фигуру, стоящую в своём дефолтном размере. Это ровно тот режим отказа, о котором предупреждает
память проекта (aeration 60→17, «структурный пол»).

Починка: базой считается **максимум из hug-размера и дефолтного штампа вида**
(`DiagramNodeDefaults.defaultSizeFor`), порог 4.0 по площади. Тогда репро = ×12 (ловится),
рекомендованный самим SKILLS размер `450 by 120` = ×2.7 (чисто), 140×48 state = <1 (чисто).
**Все `DiagramTemplates` проходят lint без `NodeLabelFit`** — это и есть проверка, что дефолты
P3 подобраны верно.

### 7.2b. Что дал и чего НЕ дал wasm-чек (честно)

**Подтверждено вживую** (Chrome, dev-сервер 8080, экран Architecture, узел `mod_diagrams`):
- **P0, главный дефект — исправлен.** Двойной клик открывает поле **с текстом**: скрытый
  IME-элемент Compose держит `"diagrams"` с выделением `[0, 8]`. До P0 поле было пустым
  (редактор читал `node.labels`, канва рисовала `payload.name`). Проверено на двух сборках.
- **P0/P4 инспектор** — поле Label показывает ту же подпись (раньше пустое).
- **P2 плита = коробка лейбла** — плита теперь узкая полоса **внутри** узла (контур и ручки
  видны вокруг), а не слэб во весь `node.bounds`. Это и есть «пустая плита с дугами» со
  скриншота 2.
- **P2 `singleLine = false`** — Compose переключился с `<input>` на `<textarea>` (Shift+Enter
  реально может вставить перенос).
- **Регрессии P2 нет**: вся диаграмма (21 узел) рисуется корректно, фигуры не пропали,
  подписи не обрезаны.

**НЕ подтверждено вживую: коммит правки.** Ни Enter, ни blur не удалось довести до
Compose-поля через харнесс:
- `computer key` до Compose не доходит (известная запись в памяти проекта);
- синтетический `KeyboardEvent` на canvas не попадает в `onPreviewKeyEvent` поля, когда
  DOM-фокус на IME-`textarea`;
- клик «мимо» ушёл в обработчик **главной канвы**, а не оверлея.

Путь коммита закрыт JVM-тестами (интент → `setNodeText` → write-back, включая проверку
«никакого `label «…»`»), но end-to-end в браузере он **не проверен** — это провал проверки,
а не подтверждение.

⚠️ **Гипотеза о реальном пробеле в blur=commit** (стоит проверить руками): commit-хук
вызывается только из press-обработчика **оверлея**. Нажатие, которое обрабатывает
`EditorCanvasPane` (клик далеко за пределами диаграммы → `diagramEditNodeId = ""`), размонтирует
оверлей, `onDispose` снимает хук — и черновик теряется молча. draw.io в этом случае коммитит.

### 7.3. Находки вне охвата (НЕ чинились)

1. **Третья несогласованная текстовая константа.** План называет `labelCharWidth = 7.0`
   (`DiagramLint.kt:41`), но такая же аппроксимация зашита и в hit-test:
   `DiagramHitTest.kt:191` и `:373` — `label.text.length * 3.5` (= 7.0/2) с `halfHeight 8.0`
   против `labelHeight 16.0` у линта. То есть hit-box лейбла ребра и lint-оценка того же
   лейбла могут разойтись. Не трогалось (охват — узлы, не рёбра).
2. **`DiagramTemplates` не переведены на `DiagramNodeDefaults`.** P3 шаг 5 унифицировал
   палитру (она держала свои 150×70 против 160×70 у шаблонов), но сами шаблоны продолжают
   печатать литералы. Риск расхождения снижен (единственный источник существует), но не снят.
3. **Пакет/контейнер/swimlane не переведены на `labelBox`** — они рисуют подпись в полосе
   (`band.inset(6.0)`) или в теле под табом, а не в коробке узла. План этого и не требовал
   (назывались `:101`, `:106`, `:170-173`), но шов теперь есть и для них.

### 7.4. Репро-документ (компилируется)

```md
---
screen: reproScreen
page: Repro
---

# Repro id frame_root name «Root»

## Diagram: Repro id repro

Node use-case uc1 «Вести контакты собственников и совета дома» 930 by 260 position 160 40
Node use-case uc2 «Вести контакты собственников и совета дома» 160 by 70 position 160 380
Node actor a1 «Собственник» 60 by 90 position 40 380
Edge e1 from a1 to uc2
```

После P5 `SlmDiagramAuditTool` печатает на нём ровно два дефекта со скриншотов:

```
warning: 'uc1' is much larger than its label needs (930x260 vs 439x70 needed)
warning: label of 'uc2' does not fit its shape (needs 65px of 33px)
```
