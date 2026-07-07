# Аудит полноты SLM относительно Figma-фич

[← Оглавление](README.md)

Дата проверки: 2026-07-07.

Задача аудита: проверить, достаточно ли текущего `Semantic Layout Markdown`
(`SLM`), чтобы описать любой экран из Figma, если SLM должен соединять
естественное описание экрана с жесткой декларативной Figma-подобной структурой.

## Короткий вывод

Текущий SLM не является feature-complete для "любого экрана из Figma".

Он хорошо покрывает semantic authoring: экран, секции, базовую иерархию,
естественные инструкции, bindings, repeat, conditions, navigation actions,
i18n extraction and language-neutral IR. Этого достаточно для многих обычных
product UI screens на уровне намерения.

Но для полного описания Figma screen сейчас не хватает формальной схемы для
layout details, visual appearance, typography, component instances, variants,
component properties, media fills, vectors, masks, prototype interactions,
responsive behavior and handoff metadata. Многие из этих возможностей в SLM
только упомянуты как будущие поля IR, но не описаны как authoring syntax,
canonical schema or validation contract.

## Граница полноты

Для SLM нужно явно различать три уровня:

| Уровень | Что должен покрывать | Статус текущего SLM |
| --- | --- | --- |
| `screen-intent complete` | Смысл экрана: sections, content, actions, data bindings, simple layout intent. | Почти покрыто |
| `figma-screen complete` | Видимый frame/screen: точное дерево layers, layout, style, typography, components, media, interactions. | Не покрыто |
| `figma-file complete` | Весь Figma file/workspace: pages, comments, permissions, versions, libraries, Dev Mode workflows, AI/product workflows. | Не должно быть основной целью SLM |

Практичная цель для SLM: `figma-screen complete`, а не `figma-file complete`.
SLM должен уметь описать финальный экран и его поведение, но не обязан
моделировать историю версий, multiplayer editing, billing/plan rollout или
внутренние workflow Figma products.

## Что уже покрыто

| Возможность | Где есть в SLM | Оценка |
| --- | --- | --- |
| Screen metadata | Frontmatter: `screen`, `sourceLocale`, `targetLocales`, `density`, `platform`. | Хорошо |
| Hierarchy | Markdown headings `#`, `##`, `###`, lists. | Хорошо для semantic tree |
| Natural-language extraction | Locale-specific semantic lexicon. | Хорошо как authoring layer |
| Bindings | `{{variable.path}}`. | Хорошо для data-bound UI |
| Repeat | `{{item in collection}}`. | Хорошо |
| Conditions | `Если {{condition}}:`. | Хорошо |
| Actions/navigation | Markdown links and action extraction. | Частично: есть navigate, нет полной prototype model |
| Media/table/callout | Markdown image, table, blockquote mapping. | Частично |
| i18n | Resource generation, keys, pluralization, formatting, RTL checks. | Хорошо |
| Canonical IR boundary | Renderer получает JSON IR, а не Markdown. | Хорошо |
| Validation | Diagnostics for ambiguity, bindings, actions, layout, i18n. | Хорошо как принцип, схемы не хватает |
| Basic layout vocabulary | В `architecture.md`: `column`, `row`, `grid`, `stack`, `padding`, `gap`, `align`, `hug`, `fill`, `fixed`, `min/max`, `wrap`, `columns`, `spans`. | Частично |

## Матрица покрытия Figma-фич

| Группа Figma-фич | Текущее покрытие в SLM | Пробел для "любой экран из Figma" |
| --- | --- | --- |
| Files, pages, canvas, sections | Есть screen metadata и markdown hierarchy. | Нет модели page/section/canvas placement for multi-screen maps. Для одиночного экрана это не критично, для flows - нужно. |
| Frames, groups, layers | Headings/lists могут стать nested nodes. | Нет строгих node types для `frame`, `group`, `section`, `component`, `instance`, `shape`, `vector`, `text`; нет visibility, lock, z-order/layer order, absolute coordinates. |
| Auto layout | Есть базовый vocabulary: row/column/grid, padding, gap, align, hug/fill/fixed. | Не хватает per-side padding, row/column gap, distribution, baseline alignment, per-axis sizing, min/max per axis, grid tracks, cell placement, spans, constraints, ignore auto layout, clip content, scroll/overflow, fixed elements. |
| Responsive behavior | Есть `density`, `platform`, text expansion checks and logical directions. | Нет breakpoints, layout variants per breakpoint, device presets, layout grids/guides, container/query-like rules, mode-specific layout overrides. |
| Components and instances | Есть `type`, `role`, `variant`, `states` as IR concepts. | Нет `componentRef`, `libraryRef`, instance overrides, variant property values, boolean/text/instance-swap/slot properties, exposed nested instances, reset/detach semantics. |
| Styles, variables, tokens | Упомянуты design tokens and i18n resources. | Нет схемы variables collections/modes/aliases, style refs for color/text/effect/grid, bindings of variables to visual properties, theme/density/platform modes. |
| Text layers | Есть content extraction, i18n keys, formatting intent. | Нет typography schema: font family/weight/size, line height, letter spacing, paragraph spacing, alignment, resizing, max lines, truncation, rich text spans, inline links, list indentation, OpenType/variable font settings. |
| Visual design | Markdown может описать намерение, image maps to media node. | Нет fills/strokes/effects schema: solid/image/video/gradient fills, multiple fills, stroke weight/position/dash/caps/joins, shadows, blur, opacity, blend mode, corner radius/smoothing. |
| Shapes and vectors | Нет явной модели, кроме generic semantic nodes. | Нет shape primitives, vector path/network representation, boolean operations, masks, icon/vector asset references. |
| Images and media | Markdown image maps to media node. | Нет fill behavior: crop, fit/fill, focal point, replaceable asset, opacity, blend modes, masks, video-specific settings. |
| Prototyping | Links can become navigation actions. | Нет triggers, action types, destination model, overlays, swap overlay, back/close, open link, set variable, change to variant, interactive components, smart animate, transition/easing/spring settings. |
| Motion | Не покрыто. | Для screen completeness нужна хотя бы reference/model for animations and micro-interactions; full Figma Motion authoring can stay out of scope. |
| Comments, annotations, Dev Mode | `sourceMap` есть; comments mentioned in architecture mental model. | Нет formal annotations/handoff notes/measurements/export settings. Version history and permissions should stay out of SLM scope. |
| Export assets | Не покрыто, кроме renderer boundary. | Нет export format/scale/suffix and exportable asset markers. |
| AI, Make, Sites, Buzz, Slides | Не покрыто. | Это workflows/products, не screen primitives. Их outputs should map to components, assets, prototypes, sites or documents, но сами workflow не должны быть обязательной частью SLM. |

## Критичные блокеры

1. Нет полного декларативного node/property schema.
   Semantic extraction может угадать `topbar` или `primary button`, но для
   полноты нужен список допустимых node types and properties.

2. Layout model недостаточно точный.
   Для Figma screen нужны constraints, absolute/ignored auto layout children,
   per-axis sizing, grid tracks, clipping, overflow and scroll behavior.

3. Visual appearance почти не формализован.
   Без fills, strokes, effects, opacity, blend modes, radius, masks and media
   fill settings нельзя восстановить большинство реальных экранов.

4. Typography не является complete.
   SLM хорошо работает с content/i18n, но не фиксирует text layer properties,
   rich text, truncation, links and list formatting.

5. Component instance API отсутствует.
   Figma design-system screens часто состоят из instances with overrides.
   Нужно явно описывать component refs, variants, component properties and slots.

6. Prototype/interactions сведены к navigation links.
   Для экранов с overlays, interactive components, state variables and smart
   animate текущего action model недостаточно.

7. Asset/vector model не определен.
   Нужны asset references для сложных изображений/иконок и explicit primitives
   для простых shapes/vectors.

## Минимальный контракт полноты

SLM можно считать `figma-screen complete`, когда для каждого финального Figma
screen/frame он может выразить:

- stable ids, names and source map;
- visible node tree with layer order;
- node types: screen, frame, group, section, component, instance, text, shape,
  vector, media, table, slot;
- Auto layout, grid, constraints, absolute positioning, clipping and overflow;
- sizing: hug, fill, fixed, min/max per axis;
- visual style: fills, strokes, effects, opacity, blend, radius;
- typography and rich text behavior;
- component refs, variants, properties, slots and overrides;
- variables, styles, tokens and modes;
- media/vector/mask/export asset references;
- interactions, states, prototype variables and animations;
- responsive variants for platform, density, theme, locale and breakpoints;
- validation diagnostics for every unsupported or ambiguous feature.

Ключевой принцип: natural language может быть удобным входом, но полнота должна
опираться на strict IR schema and explicit override syntax.

## Рекомендуемые расширения SLM

1. Ввести явные typed attribute blocks.

```md
## CTA Card
node: frame
layout:
  mode: row
  padding:
    block: $space.4
    inline: $space.6
  gap: $space.3
  align:
    inline: space-between
    block: center
  sizing:
    width: fill
    height: hug
style:
  fills:
    - token: color.surface
  radius: $radius.md
  effects:
    - style: shadow.card
```

2. Добавить component/instance syntax.

```md
### Button: Создать миссию
component:
  ref: ds/Button
  variant:
    type: primary
    size: md
  props:
    label: Создать миссию
    iconLeading: ds/Icon/Plus
    loading: false
action:
  type: navigate
  to: /missions/new
```

3. Добавить visual and text style syntax.

```md
### Text: Mission Control
text:
  key: missionDashboard.title
  defaultText: Mission Control
  style: typography.heading.lg
  maxLines: 1
  overflow: truncate
```

4. Добавить prototype interaction syntax.

```md
interaction:
  trigger: onClick
  action: openOverlay
  destination: createMissionDialog
  animation:
    type: smartAnimate
    easing: easeOut
    durationMs: 220
```

5. Добавить asset reference layer вместо попытки описывать все сложные vectors
   прозой.

```md
media:
  asset: assets/mission-map.png
  fillMode: crop
  focalPoint: center
  alt: Карта активных миссий
```

6. Оставить escape hatch для точного IR.

````md
```ir
{
  "type": "vector",
  "name": "Custom icon",
  "pathRef": "assets/icons/custom-alert.svg"
}
```
````

Escape hatch нужен не вместо SLM, а для редких случаев, где semantic extractor
не должен угадывать.

## Итог

SLM в текущем виде - хороший foundation for semantic UI authoring with i18n,
но он не полный для "любого экрана из Figma".

Чтобы достичь полноты, нужно не расширять natural-language словарь бесконечно,
а формализовать Figma-like IR schema и дать SLM два режима:

- semantic shorthand для частых UI patterns;
- explicit declarative blocks для точного описания Figma properties.

После этого SLM сможет быть не просто описанием намерения, а полноценным
authoring layer для воспроизводимых Figma-like screens.
