---
name: compose-slm
description: Compose, revise, and validate Semantic Layout Markdown (SLM) screen documents. Use when Codex is asked to create, improve, audit, convert, or explain .layout.md or SLM for product UI screens, Figma-like frames, wireframes, prototypes, design-system instance trees, responsive layouts, i18n-ready UI, or renderer-independent screen specs.
---

# Compose SLM

## Purpose

Use this skill to turn product UI intent into Semantic Layout Markdown: a Markdown-first screen authoring format that compiles into strict, language-neutral UI IR plus i18n resources.

Default to an expert design pass, not a schema dump. The SLM should describe a usable screen with clear hierarchy, layout behavior, states, content, components, visual treatment, and interactions.

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
   - Use `ir` fenced blocks only as an escape hatch for unsupported imported details.
5. Write SLM in stable order: frontmatter, screen heading, root-level variables, then sections/components. Keep ids stable, descriptive, and language-neutral.
6. Validate with the project compiler/tests when available. Fix diagnostics instead of hiding ambiguity in prose.

## Core Source Shape

Use frontmatter for screen-level defaults only. Put exact node properties in typed blocks next to the heading, paragraph, list item, image, table, or blockquote they describe.

```md
---
screen: missionDashboard
page: Operations
sourceLocale: en-US
targetLocales:
  - en-US
  - ru-RU
density: compact
platform: web
theme: light
frame:
  preset: desktop-1440
  width: 1440
  height: 1024
breakpoints:
  - id: desktop
    minWidth: 1024
  - id: mobile
    maxWidth: 767
libraries:
  - id: ds
    source: "@company/design-system"
---

# Mission Dashboard

Top bar: title Mission Control, on the right primary button [Create mission](/missions/new).

## Active Missions
node:
  type: frame
  id: activeMissions
layout:
  mode: grid
  columns:
    count: 3
    track: 1fr
    gap: $space.4
  rows:
    auto: true
    min: 160
  sizing:
    width: fill
    height: hug
style:
  fills:
    - token: color.surface
```

Use `$token.name` in real SLM. In Kotlin raw-string fixtures in this repository, the helper `missionSlm` uses the section-sign placeholder (`U+00A7`) before token names and converts it to `$`.

## Document Structure

Use frontmatter for document and screen defaults:

| Field | Purpose |
| --- | --- |
| `screen` | Stable screen id; required. |
| `page` | Figma-like page/grouping name. |
| `sourceLocale`, `targetLocales` | Authoring locale and generated resource locales. |
| `density`, `platform`, `theme` | Default mode values. |
| `frame` | Screen preset and fixed viewport dimensions. |
| `canvas` | Canvas section and placement for multi-screen maps. |
| `flow` | Prototype entry/flow metadata. |
| `breakpoints` | Responsive selector definitions. |
| `libraries` | Design-system/library refs used by instances. |

Use Markdown hierarchy for the authored tree:

- `#` is the screen title and root screen anchor.
- `##` and `###` create major and nested semantic sections or node anchors.
- Lists are groups, repeated content, collections, or nested content.
- Blockquotes are callouts, empty states, warnings, or similar semantic regions.
- Links are navigation/actions unless an explicit `action` or `interaction` block overrides them.

Typed blocks bind to the nearest previous heading, list item, image, table, blockquote, or explicit node marker at the same structural level. Screen-level blocks such as `variables` can appear before any anchor and bind to the screen/document context.

Markdown order is the default layer order. Use `node.order` only when exact sibling ordering must override source order.

## Typed Blocks

Reserved top-level typed block keys are:

`node`, `layout`, `style`, `text`, `component`, `props`, `overrides`, `media`, `shape`, `vector`, `mask`, `action`, `interaction`, `motion`, `responsive`, `variables`, `handoff`, `export`.

Typed blocks start only when the reserved key appears at the correct block column, or inside a fenced YAML block whose first key is reserved. Misspelled or wrong-case keys stay prose and should be fixed. Use fenced `ir` code blocks only for exact IR escape hatches; unfenced `ir:` is invalid.

### Node

Use `node` for node identity, type, role, order, visibility, lock, position, and constraints.

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

### Components

Use `node.type: instance` with `component.ref` for design-system usage. Use `component.name`, `variants`, and `properties` for definitions.

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

For repository work, run the narrowest meaningful tests after creating or editing SLM examples or syntax:

```powershell
.\gradlew.bat :engine:frontend:jvmTest --tests "*Slm*"
.\gradlew.bat :engine:scene:jvmTest
```

If the project exposes a compile helper, also compile the exact SLM source and inspect diagnostics. A good SLM output has no error diagnostics and passes these checks:

- Parse/frontmatter: required `screen`, valid YAML maps, known frontmatter keys, closed fences.
- Structure: unique stable ids, valid node types, no cycles, deterministic layer order, source maps preserved.
- Layout: valid modes, sizing, constraints, anchors, grid tracks, placement, overflow, scroll, and no conflicting absolute/auto-layout rules.
- Style/text: resolvable tokens/styles/variables, valid colors, gradients, effects, typography, i18n keys/resources, and bounded text with max-lines, wrap, or truncation policy.
- Components/assets: component and library refs resolve; variants, props, slots, and overrides target existing definitions; media assets, focal points, vector paths, and masks are valid.
- Scene/prototype: triggers and actions are supported; navigation, overlay, scroll, variable, and action-set targets resolve; transitions, easing, motion refs, and fallback keyframes are valid.
- Responsive/handoff/export: selectors use known dimensions, variants are not ambiguous, annotations/measurements target existing nodes, and export formats/settings are supported.
- Canvas/Scene boundary: authored SLM includes behavior definitions but no live runtime playback state.

When validation fails, fix the source structure or typed blocks. Do not paper over parser errors by moving required properties into natural-language prose.
