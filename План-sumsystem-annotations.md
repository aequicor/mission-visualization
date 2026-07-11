# План — подсистема аннотаций / комментариев

## Цель

Добавить подсистему **аннотаций** к дизайн-документу: пользователь оставляет
комментарий **к компоненту** или **над компонентом** — пояснение либо замечание.
Аннотация может быть **скрыта** (капля-маркер над компонентом) или **раскрыта**
(табличка с текстом), может нести **вложенное изображение**. Замечания (issue)
выделяются жёлтым и **выгружаются как промпт для ИИ-агента**, чтобы он исправил
дизайн.

Подсистема — **три модуля** по образцу `anchoring`/`typography` (чистое ядро +
`-compose` рендерер), плюс отдельный SLM-модуль для авторинга/write-back:

- `:subsystems:annotations` — чистое ядро (KMP, без Compose): модель + операции + экспорт-промпт.
- `:subsystems:annotations-compose` — Compose-рендерер: капля-маркер, раскрытая табличка, оверлей, картинка.
- `:subsystems:annotations-slm` — sidecar-формат: parse / write / хирургический patch (чистый Kotlin).

## Ключевые решения (утверждены)

| Вопрос | Решение |
|---|---|
| Хранилище | **Sidecar-файл** рядом с SLM (`*.annotations.md`, отдельно от дизайн-разметки), но аннотация **ссылается на компоненты** по node id. |
| Привязка | **И то, и то**: по умолчанию к узлу (`NodeAnchor(nodeId, offset)`), допускается открепление в свободную точку (`FreePoint(x,y)`). Плюс доп. ссылки на узлы (`references`). |
| Рисунок | **Вложенное изображение** (data-URI/asset), показывается в раскрытой табличке. Freehand-ink — вне scope v1. |
| Экспорт | **Все варианты охвата**: выбранные / весь экран / весь документ → текстовый промпт (только issue, с контекстом узлов). |

## Два вида аннотаций

- **Note (пояснение)** — нейтральный визуал (chrome/ink токены). Не попадает в промпт-экспорт.
- **Issue (замечание)** — выделено **жёлтым** (`statusWarning`). Попадает в промпт-экспорт.

Смена вида — одним интентом (`SetAnnotationKind`), визуал и участие в экспорте пересчитываются.

## Отображение

- **Скрыта (collapsed)** → капля-маркер над компонентом (позиция = `NodeAnchor` bounds top + offset, либо `FreePoint`).
- **Раскрыта (expanded)** → табличка: текст + опциональная картинка; issue — жёлтая рамка/заголовок.
- Состояние раскрытия — **вид, не документ**: живёт в `EditorWorkspaceState`
  (`expandedAnnotationIds`), не пишется в sidecar (в модели — только `defaultExpanded`).

---

## Модуль 1 — `:subsystems:annotations` (чистое ядро)

Пакет: `io.aequicor.visualization.subsystems.annotations`.

**Модель**
- `Annotation(id, kind, anchor, body, image?, defaultExpanded, references, author?)`
- `AnnotationKind` — `Note` | `Issue` (sealed/enum).
- `AnnotationAnchor` — sealed: `NodeAnchor(nodeId: String, offset: Offset)` | `FreePoint(x, y)`.
- `AnnotationBody` — текст (v1 — plain `String`; задел на `RichText` из typography).
- `AnnotationImage(source: String /* data-URI/asset ref */, width, height)`.
- `AnnotationLayer(screenFileName: String, annotations: List<Annotation>)` — sidecar-модель одного экрана.

**Чистые операции** (в духе `reduceDesignEditor` — pure, возвращают новый layer):
- add / update-text / set-kind / attach-image / detach-image / move (offset|free) /
  attach-to-node / detach-anchor / add-reference / delete.

**Геометрия размещения**
- `annotationBadgePosition(anchor, nodeBounds: Rect?): Point` — куда ставить каплю/якорь таблички.

**Экспорт-промпт** (`AnnotationPromptExporter`)
- `exportIssues(annotations, scope, nodeContext): String` — собирает **только issue** в промпт для ИИ-агента.
- `ExportScope` — `Selected(ids)` | `Screen(fileName)` | `WholeDocument`.
- Контекст узлов ядро **не тянет из IR**: принимает `nodeContext: (nodeId) -> AnnotatedNodeRef?`
  (label/type/bounds/screen), маппинг из `ResolvedNode` — адаптером в `:shared`.
- Формат промпта: заголовок-инструкция + пронумерованные замечания с привязкой к узлу
  (id, имя, тип, экран, bounds) и текстом; наличие картинки помечается.

**Тесты (`commonTest`)**: операции слоя, `annotationBadgePosition` (node vs free),
экспорт (все три scope, только issue, контекст узлов, картинка-флаг).

## Модуль 2 — `:subsystems:annotations-compose` (рендерер)

Пакет: `io.aequicor.visualization.subsystems.annotations.compose`. Зависит от ядра.

- `AnnotationBadge` — капля-маркер (collapsed); issue → `statusWarning`, note → нейтраль. Клик → toggle expand.
- `AnnotationCard` — раскрытая табличка: текст + `AnnotationImage` (декод data-URI), issue-акцент.
- `AnnotationOverlay(annotations, expandedIds, nodeBounds, viewTransform, callbacks, modifier)` —
  рисует все аннотации экрана поверх артборда с учётом pan/zoom; хостится над `DesignArtboard`.
- Цвета/отступы — только токены темы (`LocalEditorColors`), правила dropdown/визуалов из `CLAUDE.md`.
- Rich text — задел на `:subsystems:typography-compose`; v1 — plain text.

**Тесты**: смок-рендер badge/card (issue-акцент), позиционирование по `viewTransform` — по возможности.

## Модуль 3 — `:subsystems:annotations-slm` (sidecar-формат + write-back)

Пакет: `io.aequicor.visualization.subsystems.annotations.slm`. Зависит от ядра. Чистый Kotlin.

**Формат `*.annotations.md`** (markdown, в духе SLM; одна секция = одна аннотация):
```
## issue @node-abc123 +@node-def456
Контраст текста ниже нормы, поправить фон.
![](data:image/png;base64,...)

## note @(120,340)
Здесь свободный комментарий, откреплён от узла.
```
- Заголовок: `issue`/`note` + якорь (`@nodeId` или `@(x,y)`) + доп.ссылки (`+@id`).
- Тело — текст; `![](data-uri)` — вложенная картинка.

**Компоненты**
- `AnnotationSlmParser`: текст → `AnnotationLayer` (толерантность к мусору — не роняет весь файл).
- `AnnotationSlmWriter`: `AnnotationLayer` → текст (round-trip-стабильный).
- `AnnotationSlmPatcher`: **хирургический** patch одной секции (add/update/delete),
  по образцу `SlmPatcher`/`NodeSectionWriter` — не переписывает весь файл при точечной правке.

**Тесты**: round-trip parse↔write, patcher add/update/delete, оба вида якоря, доп.ссылки,
картинка-embed, устойчивость к битой секции.

---

## Интеграция в `:shared` (редактор)

**Хранилище / персист**
- Sidecar-исходники несём как обычные `MissionDocumentSource` с именем `*.annotations.md`.
  Компиляция экрана фильтрует их из SLM-компайла и роутит в `annotations-slm`.
- `AnnotationRepository` (domain-интерфейс) + default-impl (data) поверх sidecar.
- Персист — через существующий `DraftRepository`/`KeyValueStore` (localStorage/файл/SharedPreferences):
  sidecar-файлы едут в том же `WorkspaceDraft` списке → автосейв/Save/Reset/restore «бесплатно».

**Состояние**
- `DesignEditorState` — сторона документа: `annotationLayers: Map<screenFileName, AnnotationLayer>`.
- `EditorWorkspaceState` — сторона вида: `expandedAnnotationIds`, `selectedAnnotationId`, `annotationToolActive`.

**Интенты** (`DesignEditorIntent` — новые ветки; write-back в sidecar через `annotations-slm`):
- `AddAnnotation(anchor, kind)`, `SetAnnotationText`, `SetAnnotationKind`,
  `AttachAnnotationImage`/`DetachAnnotationImage`, `MoveAnnotation`,
  `AttachAnnotationToNode`/`DetachAnnotationAnchor`, `AddAnnotationReference`, `DeleteAnnotation`.
- Вид (workspace): `ToggleAnnotationExpanded`, `SelectAnnotation`, `SetAnnotationTool`.
- `reduceDesignEditor`: правки слоя → новый `AnnotationLayer` → **write-back в sidecar**
  (хелпер `writeBackAnnotations` через `AnnotationSlmPatcher`); toggle/selection → workspace.

**Экспорт**
- `ExportIssuesPromptUseCase(scope)` — строит промпт из issue + контекст узлов
  (`ResolvedNode` → `AnnotatedNodeRef`), возвращает текст для копирования/выгрузки.
- UI: кнопка «Выгрузить замечания» с меню scope (выбранные / экран / документ) → clipboard/download.

**UI (`editor.ui`)**
- `EditorCanvasPane`: `AnnotationOverlay` поверх `DesignArtboard`; bounds узлов — из resolved-layout, transform — из вида.
- `EditorInspectorPane`: `AnnotationSection` для выбранной аннотации — переключатель вида (note/issue),
  текст, attach/detach картинки, открепление якоря, удаление. Dropdown/визуалы — по правилам `CLAUDE.md`.
- Toolbar: инструмент «добавить аннотацию» (note/issue), действие экспорта.

**Тесты (`:shared`)**: reducer add/edit/delete/kind/move/attach, write-back в sidecar,
draft round-trip с аннотациями, `ExportIssuesPromptUseCase` (scope + контекст узлов).

---

## Фазы

- **Ph0 — каркас**: 3 модуля + `build.gradle.kts` (шаблон typography), `settings.gradle.kts`,
  typesafe accessors; заглушки пакетов.
- **Ph1 — ядро**: модель + операции + `AnnotationPromptExporter` + тесты.
- **Ph2 — SLM**: формат `*.annotations.md`, parser/writer/patcher + тесты round-trip.
- **Ph3 — compose**: badge / card / overlay / картинка + смок-тесты.
- **Ph4 — shared wiring**: состояние (doc/view split), интенты, reducer, write-back, персист (draft).
- **Ph5 — UI**: canvas-оверлей, инспектор-секция, toolbar, экспорт-действие (clipboard/download).
- **Ph6 — проверка**: `:shared:jvmTest` зелёный; **wasm-first** визуальный чек в браузере
  (капля/табличка/жёлтый issue/картинка/экспорт); обновить `CLAUDE.md`, `EDITOR.md`, `MEMORY.md`,
  добавить спеку формата в `design-book/`.

## Инварианты / ограничения v1

- Аннотации — **отдельный review-слой**, не мешаются с дизайн-SLM (sidecar).
- Привязка живёт по node id; при перекладке узла капля двигается за узлом, при удалении узла —
  аннотация становится «висячей» (помечается, не теряется; при экспорте — контекст «узел удалён»).
- Стабильность id аннотаций — явный id в секции sidecar (как в структурных правках SLM).
- Freehand-ink рисование, кросс-экранные привязки, RichText в теле — вне scope v1 (задел оставлен).
- Все цвета — токены темы; жёлтый issue = `statusWarning`.
