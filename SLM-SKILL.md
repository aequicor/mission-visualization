---
name: compose-slm
description: Generate a UI screen as Semantic Layout Markdown (SLM) using its controlled natural language (CNL). Use when asked to create, improve, or convert a .layout.md / SLM screen — turn a screen description into natural-language element sentences (RU or EN) that compile to a renderer-independent UI IR. Works with any model (DeepSeek, Codex, …).
---

# Write SLM screens in natural language (CNL)

You are given a **screen description** and must output an **SLM document**: Markdown whose
elements are written as short **sentences** — a noun plus `keyword value` phrases — not YAML.

> **Прямоугольник 120 на 15 цвет #00B843 радиус 15 паддинги 10 отступ 16**
> → a rectangle, 120×15, fill #00B843, corner radius 15, padding 10, gap 16.

The compiler turns each sentence into typed UI properties. Write it so it reads like an
annotated wireframe. Keywords work in **Russian or English** — pick one and be consistent.

## Working-Layout Contract

When asked to generate SLM, output the SLM artifact itself. Do not wrap it in a
chat answer, explanation, or Markdown code fence. The first non-empty line of
the answer must be `---`; the document must then contain frontmatter, one `#`
screen heading, and real SLM nodes. Any prose before `---`, any outer
`` ```markdown `` fence, or any afterword is not SLM and can be parsed as
visible content.

For weak or uncertain models, use this safe subset before attempting advanced
features:

1. Create frontmatter, one `#` screen title, and 3-6 visible top-level sections.
2. Give every container a `node`, `layout.mode`, `layout.sizing`, and either
   `padding`/`gap` or an explicit reason why it has no children.
3. Give every visible text layer `node.type: text`, `text.key`,
   `text.defaultText`, typography, resizing, and max-lines or truncation.
4. Author visible repeated rows as actual nested nodes or Markdown list items.
   Do not hide visible structure inside `props.children` and expect it to
   render.
5. Use only local components defined in the same SLM or libraries already known
   to the project. If a design-system component might not resolve, build the
   control from `frame`, `text`, and `shape` primitives.
6. For code panes, logs, tables, trees, timelines, and dashboards, use explicit
   rows/cells/text nodes unless the repository already has a first-class node or
   renderer for that exact widget.
7. Use `ir` only for exact supported IR node JSON. Never invent widget types
   such as `codeViewer`, `chartWidget`, `treeView`, or `dataGrid` unless the IR
   reader and renderer in the repository already support them.

Before returning an SLM document, scan it for these hard failures:

| Failure | Why it breaks | Safer authoring |
| --- | --- | --- |
| Text before frontmatter or an outer `` ```markdown `` fence | The parser may treat the answer as Markdown content, not SLM source. | Output the raw `.layout.md` only. |
| `text.defaultText: "{{name}}"` | `defaultText` is literal text in typed blocks. | Use Markdown text such as `Name: {{name}}`, or render concrete sample data. |
| `props.children:` for visible rows | Props are data; they are not automatically child nodes. | Author rows as nested headings/list items, or define a real slot/repeat mechanism. |
| `component.ref: ds/Button` with no available `ds` library | Unknown instances render as placeholders or fail validation. | Define a local component or use primitive nodes. |
| `ir` with unknown `type` | Unknown IR nodes render as fallback and lose layout semantics. | Use supported node types or model the UI with frames/text/shapes. |
| Large `height: fill` regions with `clipContent: false` | Content can overlap or escape its panel. | Use bounded frames with `overflow.y: auto` and clear sizing. |
| Absolute children inside auto layout without intent | They can overlap flow content. | Prefer normal flow; if absolute is needed, set `ignoreAutoLayout`/anchors and bounds deliberately. |

## Context Intake

When working inside a repository, inspect local SLM sources before composing:

- `design-book/semantic-layout-markdown-i18n.md` for the target SLM contract.
- `design-book/slm-feature-completeness-audit.md` for the figma-screen completeness checklist.
- `engine/frontend/src/commonMain/.../blocks` and `engine/frontend/src/commonTest/.../blocks/readers` for the actually parsed typed-block syntax.
- `shared/src/commonMain/.../editor/data/*Slm.kt` for large production examples.

Prefer executable parser/tests over prose documentation when they conflict. If no local SLM implementation exists, use this skill as the contract and state any assumptions.

## Authoring Workflow

1. Define the screen contract: screen id, page, source locale, target locales, platform, theme, density, frame size, data inputs, empty/loading/error states, primary actions, and navigation destinations.
2. Build the information architecture first: screen root, top bar, navigation, main content, panels, repeated lists/cards, dialogs, overlays, and status regions.
3. Apply design judgment before writing blocks:
   - Make the primary workflow visible and reduce competing calls to action.
   - Use meaningful frames for layout; avoid generic groups when spacing, clipping, or responsive behavior matters.
   - Prefer auto layout, padding, gap, fill/hug/fixed sizing, min/max, and grid tracks over manual nudging.
   - Use fixed absolute positioning only for intentionally freeform mockups, imported compositions, overlays, or canvas-like screens.
   - Use design tokens, styles, and component instances for repeated UI.
   - Include real copy, generated i18n keys, empty/error/loading states, and truncation/wrapping policy for bounded text.
   - Use standard controls: buttons, inputs, tabs, menus, segmented controls, toggles, sliders, tables, cards, badges, and icon buttons where appropriate.
   - Keep cards for repeated items, modals, and framed tools; do not turn every section into a card.
   - Avoid one-note palettes; check contrast, density, spacing rhythm, and responsive behavior.
   - Use logical directions (`inlineStart`, `inlineEnd`, `blockStart`, `blockEnd`) where locale or RTL behavior matters.
4. Choose the precision level:
   - Use semantic shorthand for common product UI intent.
   - Add typed blocks for any visible or behavioral property that must survive compilation.
   - Use `ir` fenced blocks only as an escape hatch for exact supported IR, not
     as a place to invent application widgets.
5. Write SLM in stable order: frontmatter, screen heading, root-level variables, then sections/components. Keep ids stable, descriptive, and language-neutral.
6. Validate with the project compiler/tests when available. Fix diagnostics instead of hiding ambiguity in prose.

## Minimal Working Template

When the user did not ask for advanced component systems, start from this shape
and expand it. It is intentionally boring because it compiles predictably.

```md
---
screen: sampleScreen
page: Product
sourceLocale: en-US
targetLocales:
  - en-US
theme: light
density: compact
platform: web
frame:
  preset: desktop-1440
  width: 1440
  height: 1024
---

# Sample Screen

variables:
  collections:
    - id: theme
      modes: [light]
      variables:
        color.surface:
          type: color
          values:
            light: "#FFFFFF"
        color.text:
          type: color
          values:
            light: "#111827"

## App Shell
node:
  type: frame
  id: appShell
layout:
  mode: column
  sizing:
    width: fill
    height: fill
  clipContent: true
  overflow:
    x: hidden
    y: auto
style:
  fills:
    - token: color.surface

### Text: Title
node:
  type: text
  id: screenTitle
text:
  key: sample.title
  defaultText: "Sample Screen"
  typography:
    fontFamily: Inter
    fontWeight: 700
    fontSize: 24
    lineHeight: 32
  resizing:
    width: fill
    height: hug
  maxLines: 1
  overflow: truncate
```

## Core Source Shape

Use frontmatter for screen-level defaults only. Put exact node properties in typed blocks next to the heading, paragraph, list item, image, table, or blockquote they describe.

Do not include the surrounding chat response in the source. A valid SLM file is
the content between frontmatter and the final authored block, not a fenced code
sample inside another Markdown document.

```md
---
screen: <stableId>
sourceLocale: ru-RU
targetLocales: [ru-RU, en-US]
frame: { preset: desktop-1440, width: 1440, height: 1024 }
---

# <Screen name>

## <Container name> <container properties>

<element sentence>
<element sentence>

## <Another container> <container properties>

<element sentence>
```

Rules of the shape:
- **Frontmatter** (between `---`) sets screen id, locales and the artboard size. Keep it on
  few lines using `{ ... }` and `[ ... ]` as shown.
- **`#` H1** = the screen title.
- **`##` / `###` headings = containers** (frames/sections). A heading may end with container
  properties (layout/size/style) — see below. Deeper headings (`###`) nest inside shallower ones.
- **Each element is ONE line** — a sentence. One line, one element. Never wrap an element
  across lines.
- Blank line between a container heading and its children, and between containers.

## Elements: noun + properties

Start a line with an element noun, then add property phrases in any order.

| Noun (RU) | Noun (EN) | Element |
|---|---|---|
| прямоугольник | rectangle / rect | rectangle shape |
| эллипс, круг | ellipse, circle | ellipse shape |
| линия | line | line |
| звезда | star | star |
| многоугольник | polygon | polygon |
| стрелка | arrow | arrow |
| текст, надпись | text, label | text |
| кнопка | button | button (labeled control) |
| контейнер, фрейм, группа | frame, group, container | frame / group |
| изображение, картинка | image | image / media |
| иконка | icon | vector / icon |

## Property phrases

Values are numbers, `#hex` colors, `$token` refs, or enum words. Text goes in `«…»` or `"…"`.

| Meaning | Russian | English | Example |
|---|---|---|---|
| size | `<w> на <h>` | `<w> by <h>` / `<w> x <h>` | `120 на 15` |
| width / height | `ширина N` / `высота N` | `width N` / `height N` | `ширина 320` |
| fill color | `цвет <c>` / `заливка <c>` | `color <c>` / `fill <c>` | `цвет #00B843` |
| stroke | `обводка <c> [w] [inside\|outside\|center]` | `stroke <c> …` | `обводка #CBD5E1 1 inside` |
| corner radius | `радиус N` / `скругление N` | `radius N` | `радиус 12` |
| rotation | `N градусов` / `поворот N` | `rotate N` | `30 градусов` |
| padding | `паддинги N` (1/2/4 numbers) | `padding N` | `паддинги 16` · `паддинги 12 24` |
| gap (spacing) | `отступ N` / `промежуток N` | `gap N` | `отступ 16` |
| direction | `колонка` · `строка` · `сетка` · `свободно` | `column` · `row` · `grid` · `free` | `колонка` |
| align in parent | `родительский контейнер вверх\|вниз\|влево\|вправо\|центр` | `align top\|bottom\|left\|right\|center` | `родительский контейнер вверх` |
| position (free) | `позиция X Y` / `координаты X Y` | `position X Y` / `at X Y` | `позиция 72 96` |
| opacity | `прозрачность N` | `opacity N` | `прозрачность 0.8` |
| font size (text) | `размер N` | `size N` | `размер 20` |
| bold / italic (text) | `жирный` / `курсив` | `bold` / `italic` | `жирный` |

Container headings take the SAME layout/style phrases after the name:

```md
## Панель миссий колонка отступ 16 паддинги 24 цвет #FFFFFF радиус 12
```
→ a frame named "Панель миссий", vertical layout, gap 16, padding 24, white fill, radius 12.

## Colors, tokens, units

- Colors: `#RRGGBB`, `#RGB`, `#RRGGBBAA` — **quotes are NOT required** (`цвет #1E293B` is fine).
- Design tokens (theme variables): `$color.accent`, `$space.4` — a leading `$`, no quotes.
- Numbers are plain (`16`, `0.8`, `320`). No units, no `px`.
- Text content is the only localized part; put it in `«…»` (RU) or `"…"`. Everything else
  (numbers, colors, tokens, enum words) is language-neutral and never translated.

## Two complete examples (both compile clean)

Mission card (Russian):

```md
---
screen: missionCard
sourceLocale: ru-RU
targetLocales: [ru-RU, en-US]
frame: { preset: desktop-1440, width: 1440, height: 1024 }
---

# Mission Card

## Карточка миссии колонка отступ 12 паддинги 16 цвет #FFFFFF радиус 12

Текст «Активные миссии» размер 20 жирный цвет #0F172A
Текст «12 в работе» размер 14 цвет #64748B
Прямоугольник 320 на 4 цвет #2563EB радиус 2
Кнопка «Открыть» цвет #2563EB
```

Status chips (English):

```md
---
screen: statusChips
sourceLocale: en-US
targetLocales: [en-US]
frame: { preset: desktop-1440, width: 1440, height: 1024 }
---

# Status Chips

## Chips row gap 8 padding 8

Rectangle 80 by 24 color #DCFCE7 radius 12
Rectangle 80 by 24 color #FEE2E2 radius 12
Text "Nominal" size 12 color #166534
```

## DO / DON'T (tuned for smaller models)

DO:
- One element per line; keep each line a single sentence.
- Start every element line with a known noun; start containers with `##`/`###`.
- Put visible text in `«…»` / `"…"`; put a name before any container properties.
- Use bare `#hex` for colors; use one keyword language (RU or EN) throughout a screen.

DON'T:
- Don't wrap an element onto a second line, and don't indent with tabs.
- Don't invent property words — only those in the tables above are recognized.
- Don't put numbers with no keyword (a bare `42` is an error — attach it, e.g. `радиус 42`).
- Don't start a container heading with a property word (name it first: `## Панель колонка`).

## Error catalog (self-check)

If the compiler warns `[CNL:<id>] … Как исправить: …`, fix per the id:

| id | Meaning | Fix |
|---|---|---|
| `unknown-keyword` | a word isn't a known property | check spelling; use a word from the tables |
| `missing-value` | keyword has no value | add the value, e.g. `цвет #00B843` |
| `bad-color` | value after `цвет`/`color` isn't a color | use `#hex` or `$token` |
| `bad-number` | value should be a number | e.g. `радиус 15`, `отступ 16` |
| `incomplete-size` | size needs two numbers via `на`/`by` | `120 на 15` |
| `unterminated-text` | quotes not closed | close `«…»` or `"…"` |
| `bad-direction` | not a valid direction | `вверх\|вниз\|влево\|вправо\|центр` |
| `stray-number` | a number not attached to a property | attach it, e.g. `размер 120 на 15` |

## Escape hatch (rarely needed)

CNL covers the common visual grammar. For anything it doesn't express (gradients, effects,
component instances, vector paths, interactions, motion), fall back to explicit YAML typed
blocks under the element/heading (they coexist with CNL and take precedence). The full typed
contract is `design-book/semantic-layout-markdown-i18n.md`; the completeness checklist is
`design-book/slm-feature-completeness-audit.md`.

Markdown order is the default layer order. Use `node.order` only when exact sibling ordering must override source order.

## Typed Blocks

Reserved top-level typed block keys are:

`node`, `layout`, `style`, `text`, `component`, `props`, `overrides`, `media`, `shape`, `vector`, `mask`, `action`, `interaction`, `motion`, `responsive`, `variables`, `handoff`, `export`.

Typed blocks start only when the reserved key appears at the correct block column, or inside a fenced YAML block whose first key is reserved. Misspelled or wrong-case keys stay prose and should be fixed. Use fenced `ir` code blocks only for exact IR escape hatches; unfenced `ir:` is invalid.

### Node

Use `node` for node identity, type, role, order, visibility, lock, position, and constraints. Prefer normal flow layout. Use `position.mode: absolute` only when a node intentionally leaves auto layout, and make its size and parent clipping/overflow explicit.

```md
## Mission Panel
node:
  type: frame
  id: missionPanel
  name: Mission Panel
  role: details
  visible: true
  locked: false
  order: 20
  position:
    mode: absolute
    x: 24
    y: 96
  constraints:
    horizontal: left-right
    vertical: top
```

### Node Taxonomy

| Node | Use for | Authoring guidance |
| --- | --- | --- |
| `screen` | Root authored frame from frontmatter. | Usually produced from `screen` and `frame`, not hand-authored for every section. |
| `frame` | Layout container with sizing, clipping, style, and children. | Prefer for UI containers that affect spacing, responsive behavior, or hit testing. |
| `section` | Semantic region that usually compiles to a frame. | Use for topbar, sidebar, main, filters, empty state, or logical screen regions. |
| `group` | Structural grouping without layout responsibility. | Avoid when the group needs padding, gap, clipping, constraints, or responsive rules. |
| `component` | Reusable component definition. | Include `component.name`, variants, properties, slots, and a root subtree. |
| `instance` | Design-system or local component usage. | Include `component.ref`, variants, props, and overrides. |
| `slot` | Component slot target or slot fill. | Use with component properties and instance `overrides.slots`. |
| `text` | Localized text layer. | Include key/default text, typography, resizing, spans, links, lists, and truncation. |
| `shape` | Rectangle, ellipse, line, polygon, star, arrow. | Use for simple primitives and pair with `style`. |
| `vector` | Icons, SVG refs, inline paths, vector networks. | Prefer `iconRef` or `pathRef`; use inline paths only when the SLM owns the shape. |
| `media` | Image/video-backed layer. | Include asset, kind, fill mode, focal point, alt text, and video settings when needed. |
| `table` | Structured tabular data display. | Use when rows/columns have semantic table behavior rather than decorative grid layout. |
| `annotation` | Handoff/debug note or measurement target. | Prefer `handoff` for engineering notes; use annotation nodes only when visible in the screen model. |
| `booleanOperation` | IR-level vector composition. | Prefer `vector.boolean`; use exact `ir` only for imported compatibility cases. |
| `slice` | Export/compatibility slice. | Prefer `export` settings on the relevant node. |
| `unknown` | Forward-compatible imported node. | Avoid in authored SLM; preserve only when importing unsupported IR. |

### Layout

Use `layout` for flow, sizing, grid, padding, gap, alignment, wrapping, clipping, overflow, scroll, absolute placement, guides, and grids.

Accepted common values include:

- `mode`: `none`, `row`, `horizontal`, `column`, `vertical`, `grid`.
- `sizing`: `fixed`, `hug`, `fill`; use object form when value/min/max are needed.
- `align`: `start`, `center`, `end`, `baseline`, `stretch`.
- `distribution`: `packed`, `start`, `center`, `end`, `space-between`.
- `overflow`: `visible`, `hidden`, `auto`.
- constraints: `left`, `right`, `center`, `left-right`, `scale`, `top`, `bottom`, `top-bottom`.

```md
layout:
  mode: column
  padding:
    inline: $space.4
    block: $space.3
  gap:
    row: $space.3
    column: $space.2
  align:
    inline: stretch
    block: start
  sizing:
    width:
      type: fill
      min: 320
      max: 720
    height:
      type: hug
  clipContent: true
  overflow:
    x: hidden
    y: auto
```

### Visual Style

Use `style` for tokens, fills, strokes, effects, opacity, blend, radius, and style refs. Prefer tokens/style refs over raw colors unless the source asset owns the exact color.

```md
style:
  fillStyle: color.surface.default
  textStyle: typography.heading.lg
  radius:
    variable: radius.card
  fills:
    - token: color.surface
  strokes:
    - token: color.border.subtle
      weight: 1
      position: inside
  effects:
    - style: shadow.card
```

### Text And I18n

Use `text` for content, i18n key, typography, resizing, rich text spans, list settings, max lines, and truncation. Text strings are localized; ids, tokens, routes, component refs, expressions, and action names are not.

`text.defaultText` is literal fallback copy. Do not put raw mustache bindings
there unless the braces should be visible to the user. For dynamic copy, prefer
Markdown text with `{{expr}}` so the semantic extractor creates i18n params, or
use supported component props/data bindings that the resolver can evaluate.

```md
### Text: Mission Count
node:
  type: text
  id: missionCount
text:
  key: missionDashboard.stats.count
  defaultText: "{count} active missions"
  typography:
    fontFamily: Inter
    fontWeight: 700
    fontSize: 24
    lineHeight: 32
  resizing:
    width: fill
    height: hug
  maxLines: 1
  overflow: truncate
```

Use bindings as `{{mission.name}}`, repeats as `{{mission in missions}}`, and conditions as `If {{missions.length == 0}}:`.

Safe dynamic text examples:

```md
### Text: Mission Name
Mission: {{mission.name}}
node:
  type: text
  id: missionName
text:
  key: mission.name
  typography:
    fontSize: 14
    lineHeight: 20
  resizing:
    width: fill
    height: hug
  maxLines: 1
  overflow: truncate
```

Unsafe dynamic text example:

```md
text:
  key: mission.name
  defaultText: "{{mission.name}}" # This is literal text in typed blocks.
```

### Components

Use `node.type: instance` with `component.ref` for design-system usage. Use `component.name`, `variants`, and `properties` for definitions.

An instance ref is only safe when the component exists. A local component must be
defined in the same SLM with a visible child tree. A design-system ref must match
an available library. If either condition is uncertain, author primitive
frames/text/shapes instead.

```md
### Button: Create Mission
node:
  type: instance
  id: createMissionButton
component:
  ref: ds/Button
  libraryRef: ds
  variant:
    type: primary
    size: md
  props:
    label:
      type: text
      value: Create mission
      i18nKey: missionDashboard.actions.createMission
    iconLeading:
      type: instanceSwap
      value: ds/Icon/Plus
action:
  type: navigate
  to: /missions/new
```

Use `props` and `overrides` for instance data bindings, slot fills, and nested instance variants.

Props are not layout. A prop array such as `children: [...]` is just data unless
the component definition explicitly consumes it through supported slots/repeats.
When in doubt, unroll visible rows in the SLM tree:

```md
### File Row: Auth Service
node:
  type: frame
  id: fileAuthService
layout:
  mode: row
  sizing:
    width: fill
    height: hug
  gap: 8

#### Text: File Name
node:
  type: text
  id: fileAuthServiceName
text:
  key: files.authService.name
  defaultText: "AuthService.ts"
  typography:
    fontSize: 13
    lineHeight: 20
  resizing:
    width: fill
    height: hug
  maxLines: 1
  overflow: truncate
```

### Media, Shapes, Vectors, Masks

Use `media` for image/video-backed layers, `shape` for primitives, `vector` for icons/path refs/inline paths, and `mask` for alpha/vector/luminance masks.

```md
### Mission Map
node:
  type: media
  id: missionMap
media:
  asset: assets/mission-map.png
  kind: image
  fillMode: crop
  focalPoint:
    x: 0.48
    y: 0.42
  alt:
    key: missionDashboard.map.alt
    defaultText: Active mission map
```

Prefer asset refs for complex visuals instead of trying to describe detailed artwork in prose.

For unsupported rich widgets, model the visible result with supported primitives.
For example, a code review pane should be a scrollable frame containing line
rows, line-number text, code text, diff backgrounds, and comment frames. Do not
emit `ir` with `type: codeViewer` unless the repository has that IR node and a
renderer for it.

### Interactions, Motion, Responsive, Handoff

Use Markdown links for simple navigation; use `interaction` for prototype behavior and multiple actions.

```md
interaction:
  trigger: onClick
  action: openOverlay
  destination: createMissionDialog
  overlay:
    position: center
    closeOnOutsideClick: true
    background: rgba(0,0,0,0.32)
  animation:
    type: smartAnimate
    easing: easeOut
    durationMs: 220
```

Use `variables.prototype` for prototype state, `motion` for animation refs or fallback keyframes, `responsive.variants` for breakpoint/theme/density/locale/state overrides, `handoff` for engineering annotations, and `export` for asset export settings.

## Canvas And Scene Contract

SLM authors a `DesignDocument` IR. After IR, rendering splits into sibling paths:

```text
SLM -> DesignDocument IR -> CanvasProjection -> CanvasRenderModel
SLM -> DesignDocument IR -> SceneProjection  -> SceneRenderModel
```

Canvas is the authored static screen state:

- node tree, layout, constraints, sizing, clipping, and scroll settings;
- style, text, media, vectors, shapes, components, instances, and responsive variants;
- editor overlays such as selection, hover, resize handles, guides, and measurements.

Scene is authored behavior plus runtime playback:

- authored interactions, triggers, actions, prototype variables, transitions, overlays, and motion;
- runtime location, variables, input state, snapshots, transition phase, timeline, and debug trace.

SLM should author the behavior inputs (`interaction`, `action`, `variables.prototype`, `motion`, `flow`) but must not serialize live runtime state:

- `currentTimeMs`, playback `playing`/`paused`, or active animation clock;
- active transition instance, transition progress, or sampled visual overrides;
- runtime `history`, current `overlayStack`, hover/pressed/focused/dragging state;
- Scene debug trace, hit target, triggered action list, or timeline markers.

A stable Scene frame has one screen layer plus any overlays. A transition frame may render outgoing and incoming screen snapshots at the same time, plus overlay/backdrop layers. The renderer receives layers and sampled overrides from SceneProjection; it must not infer a global `currentScreenId` from SLM.

## Validation

When working in the repository, compile and check for zero error diagnostics:

```bash
./gradlew :engine:frontend:jvmTest --tests "*Cnl*"
./gradlew :engine:frontend:jvmTest
```

A good SLM output parses with no error diagnostics: stable `screen` id, one element per line,
known nouns/keywords, closed quotes, colors as `#hex`/`$token`, and a clear container hierarchy.

If the project exposes a compile helper, also compile the exact SLM source and inspect diagnostics. A good SLM output has no error diagnostics and passes these checks:

- Parse/frontmatter: required `screen`, valid YAML maps, known frontmatter keys, closed fences.
- Structure: unique stable ids, valid node types, no cycles, deterministic layer order, source maps preserved.
- Layout: valid modes, sizing, constraints, anchors, grid tracks, placement, overflow, scroll, and no conflicting absolute/auto-layout rules.
- Style/text: resolvable tokens/styles/variables, valid colors, gradients, effects, typography, i18n keys/resources, and bounded text with max-lines, wrap, or truncation policy.
- Components/assets: component and library refs resolve; variants, props, slots, and overrides target existing definitions; media assets, focal points, vector paths, and masks are valid.
- Scene/prototype: triggers and actions are supported; navigation, overlay, scroll, variable, and action-set targets resolve; transitions, easing, motion refs, and fallback keyframes are valid.
- Responsive/handoff/export: selectors use known dimensions, variants are not ambiguous, annotations/measurements target existing nodes, and export formats/settings are supported.
- Canvas/Scene boundary: authored SLM includes behavior definitions but no live runtime playback state.
- Generator hygiene: no outer Markdown fences, no explanatory prose in the
  source, no unresolved imaginary widgets, no `{{...}}` inside literal
  `text.defaultText`, and no visible UI represented only as opaque props.

When validation fails, fix the source structure or typed blocks. Do not paper over parser errors by moving required properties into natural-language prose.
