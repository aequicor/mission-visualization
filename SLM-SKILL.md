---
name: compose-slm
description: Author, improve, validate, rewrite, or explain a UI screen as Semantic Layout Markdown (SLM) using its controlled natural language (CNL). Use when asked to create, improve, convert, or review a .layout.md / SLM screen — turn a screen description into English-only element sentences that compile to a renderer-independent UI IR with i18n. Self-contained: works standalone with no host project (use the vocabulary below as the contract) and with small or weak models (DeepSeek, Codex, …).
---

# Write SLM screens in CNL (controlled natural language)

CNL is **THE authoring format** for SLM: English-only, **one sentence per node**, at full
IR parity, fully bidirectional — parse (`CnlParser`) + deterministic emit (`CnlEmitter`, driven
by the shared `CnlGrammar` descriptor registry) + surgical write-back (`CnlWriter`). There is
**no author-facing escape hatch**. You are given a **screen description** and must output an
**SLM document**: Markdown whose elements are written as short **sentences** — a noun plus
`keyword value` phrases.

> `Rectangle 120 by 15 color #00B843 radius 15 padding 10 gap 16`
> → a rectangle, 120×15, fill #00B843, corner radius 15, padding 10, gap 16.

Each sentence compiles into typed UI properties; the document tree is markdown heading nesting.
Write it so it reads like an annotated wireframe. **Keywords are English-only.** The only
localized part of a document is the visible text inside `«…»` / `"…"`.

> The YAML typed blocks and `` ```ir `` fences you may see elsewhere are **INTERNAL desugar
> machinery / IR serialization** — `CnlParser` lowers each sentence into the same typed patches
> the block-readers consume. They are **not** an authoring surface. Never hand-write them.

## Working-Layout Contract

When asked to generate SLM, output the SLM artifact itself. Do not wrap it in a chat answer,
explanation, or Markdown code fence. The first non-empty line of the answer must be `---`; the
document must then contain frontmatter, one `#` screen heading, and real CNL nodes. Any prose
before `---`, any outer `` ```markdown `` fence, or any afterword is not SLM and can be parsed as
visible content.

For weak or uncertain models, use this safe subset before attempting advanced features:

1. Create frontmatter, one `#` screen title, and 3-6 visible top-level container headings.
2. Give every container heading a **name first**, then layout phrases — `column`/`row`/`grid`,
   sizing (`width (fill …)` / `height (…)`), and either `padding`/`gap` or an explicit reason it
   has no children.
3. Give every visible text node the `Text` noun, its text in `«…»`/`"…"`, a `key`, typography
   (`size`, `font`, weight), sizing, and `maxLines N` or `truncate N`.
4. Author visible repeated rows as **actual nested heading containers with child sentences**, not
   as instance props. Props are component data, not child nodes.
5. Use only local components defined in the same SLM or libraries already known to the project.
   If a design-system component might not resolve, build the control from `Frame`, `Text`, and
   shape primitives.
6. For code panes, logs, tables, trees, timelines, and dashboards, use explicit row/cell/text
   nodes unless the repository already has a first-class node or renderer for that widget.
7. Only reference component ids that resolve. Never invent widget nouns such as `codeViewer`,
   `chartWidget`, `treeView`, or `dataGrid` — the noun set is fixed (see the nouns table).

Before returning an SLM document, scan it for these hard failures:

| Failure | Why it breaks | Safer authoring |
| --- | --- | --- |
| Text before frontmatter, or an outer `` ```markdown `` fence | The parser may treat the answer as Markdown content, not SLM source. | Output the raw `.layout.md` only. |
| A node written over more than one line | One sentence = one node; a wrapped line parses as two nodes or as prose. | Keep every element on a single line. |
| A container heading that starts with a property word (`## column …`) | The heading splitter reads the first token as the name. | Name it first: `## Panel column gap 16`. |
| `characters «literal»` on a text node | `characters` is only for bindings; literal copy is the `«…»` text literal. | Put visible copy in `«…»`; use `characters $var` only for a bound value. |
| `Instance of ds/Card` with no available `ds` component | Unknown instances render as placeholders or fail validation. | Define a local component or use primitive nodes. |
| A bound flex grid track written as bare `track $colfr` | A bare `$id`/`$prop.x` ref is always a **fixed** track (`$colfr` = fixed ref to var `colfr`). | For a bound **flex** weight use the braced form `track ${col}fr` / `track ${prop.col}fr`. |
| A `$var`/`{{expr}}` focal point on a **fill** paint's `focus (x y)` | Edge #2: fill-paint focal binding is emitted as literal `0` (binding lost). | Use a literal focus, or put the media on an `Image` node's `media (… focus …)` where binds round-trip. |
| `height (fill …)` regions without clipping | Content can overlap or escape its panel. | Add `clip` and `overflow (y auto)` with clear sizing. |
| `absolute` children in auto layout without intent | They can overlap flow content. | Prefer normal flow; use `absolute` + `anchor (…)` / `position X Y` deliberately. |

## Context Intake

When working inside a repository, the **source of truth for CNL syntax** is the grammar itself —
inspect it before composing:

- `engine/frontend/src/commonMain/.../cnl/CnlGrammar.kt` — the `descriptors` registry (each
  `Descriptor(kind, keyword, order, render)` drives BOTH the parser keyword and the emitter) plus
  every phrase renderer. This is the single authoritative catalog.
- `engine/frontend/src/commonMain/.../cnl/CnlVocabulary.kt` — the noun / keyword / enum tables.
- `design-book/semantic-layout-markdown-i18n.md` — the SLM spec, including the CNL Phrase Reference.
- `shared/src/commonMain/.../editor/data/*Slm.kt` — large production CNL examples.

The `engine/frontend/.../blocks/readers` are **internal desugar** (the typed patches CNL lowers
into) — not a syntax you author against. Prefer the CNL grammar/tests over prose when they
conflict. If no local implementation exists, use this skill as the contract and state assumptions.

## Authoring Workflow

1. Define the screen contract: screen id, page, source locale, target locales, platform, theme,
   density, frame size, data inputs, empty/loading/error states, primary actions, and navigation.
2. Build the information architecture first: screen root, top bar, navigation, main content,
   panels, repeated lists/cards, dialogs, overlays, and status regions.
3. Apply design judgment before writing sentences:
   - Make the primary workflow visible and reduce competing calls to action.
   - Use meaningful frames for layout; avoid generic groups when spacing, clipping, or responsive
     behavior matters.
   - Prefer auto layout — `column`/`row`/`grid`, `padding`, `gap`, fill/hug/fixed sizing, min/max,
     and grid tracks — over manual `position X Y` nudging.
   - Use `absolute` + `position`/`anchor` only for intentionally freeform mockups, imported
     compositions, overlays, or canvas-like screens.
   - Use design tokens (`$…`), style refs (`styles (…)`), and component instances for repeated UI.
   - Include real copy, generated i18n `key`s, empty/error/loading states, and truncation/wrapping
     policy (`maxLines`/`truncate`) for bounded text.
   - Use standard controls: `Button`, inputs, tabs, menus, segmented controls, toggles, sliders,
     tables, cards, badges, and icon buttons where appropriate.
   - Keep cards for repeated items, modals, and framed tools; do not turn every section into a card.
   - Avoid one-note palettes; check contrast, density, spacing rhythm, and responsive behavior.
   - Use logical directions where locale/RTL matters (padding/anchor use logical
     `inlineStart`/`inlineEnd`/`blockStart`/`blockEnd`).
4. Write CNL in stable order: frontmatter, screen heading, root-level variables/styles sections,
   then containers and their child sentences. Keep ids stable, descriptive, and language-neutral.
5. Validate with the project compiler/tests when available. Fix diagnostics instead of hiding
   ambiguity in prose.

## Minimal Working Template

When the user did not ask for advanced component systems, start from this shape and expand it. It
is intentionally boring because it compiles predictably.

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

## App Shell column width (fill) height (fill) clip overflow (y auto) color $color.surface

Text «Sample Screen» key sample.title font «Inter» bold size 24 line-height 32 width (fill) height (hug) maxLines 1
```

The heading is the container (a `Frame`), named `App Shell`, with its layout/style phrases
following the name. Each child element is one sentence on its own line.

## Core Source Shape

Use frontmatter for screen-level defaults only. Everything else is CNL: heading containers and
one-sentence nodes.

Do not include the surrounding chat response in the source. A valid SLM file is the content
between frontmatter and the last authored sentence, not a fenced code sample inside another
Markdown document.

```md
---
screen: <stableId>
sourceLocale: en-US
targetLocales: [en-US]
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
- **Frontmatter** (between `---`) sets screen id, locales and the artboard size. Keep it on few
  lines using `{ ... }` and `[ ... ]` as shown.
- **`#` H1** = the screen title.
- **`##` / `###` headings = containers** (frames/sections). A heading may end with container
  properties (layout/size/style) — see below. Deeper headings (`###`) nest inside shallower ones.
- **Each element is ONE line** — a sentence. One line, one element. Never wrap an element across
  lines.
- Blank line between a container heading and its children, and between containers.

## Elements: noun + properties

Start a line with an element noun, then add property phrases **in any order** (the compiler
canonicalizes the order on emit). The noun set is fixed:

| Noun (and aliases) | Element |
|---|---|
| `Rectangle` / `rect` | rectangle shape |
| `Ellipse` / `circle` | ellipse shape |
| `Line` | line |
| `Star` | star |
| `Polygon` | polygon |
| `Arrow` | arrow |
| `Text` / `label` | text layer |
| `Button` | text node with `role=button` |
| `Frame` / `group` / `container` | frame / group |
| `Image` | image / media |
| `Icon` / `vector` | vector / icon |
| `Instance` | component instance |
| `section` | semantic region (compiles to a frame) |
| `screen` | root authored frame |

A text node's visible copy comes right after the noun in `«…»` or `"…"`:
`Text «Active missions» size 20 bold`.

## Property phrases

Values are numbers, `#hex` colors, `$token` refs, `{{expr}}` bindings, or enum words. Visible text
goes in `«…»` or `"…"`. Phrases can appear in any order; below they are grouped by feature. This is
the practical subset — the exhaustive catalog is the **CNL Phrase Reference** in
`design-book/semantic-layout-markdown-i18n.md`.

### Geometry & position

| Meaning | Phrase | Example |
|---|---|---|
| size | `<w> by <h>` (also `x` / `×` / `*`) | `120 by 15` |
| width / height | `width N` / `height N` | `width 320` |
| sized axis | `width (fixed\|hug\|fill N min N max N)` | `width (fill 320 min 240 max 720)` |
| free position | `position X Y` | `position 72 96` |
| visibility | `visible no` / `visible $var` / `visible {{expr}}` | `visible no` |
| lock | `locked yes` | `locked yes` |
| rotation | `rotation N` | `rotation 30` |
| leave auto-layout | `absolute` | `absolute` |
| anchor insets | `anchor (inlineStart N inlineEnd N blockStart N blockEnd N)` | `anchor (inlineStart 24 blockStart 96)` |

### Layout (containers)

| Meaning | Phrase | Example |
|---|---|---|
| flow direction | `column` / `row` / `grid` | `column` |
| wrap | `wrap` | `wrap` |
| gap | `gap N` / `gap auto` / `gap (row N column N)` | `gap 16` |
| distribution | `distribute center\|end\|space-between` | `distribute space-between` |
| padding | `padding N` / `padding V H` / `padding T R B L` | `padding 24` · `padding 12 24` |
| clip | `clip` | `clip` |
| child alignment | `align (block\|inline <start\|center\|end\|baseline\|stretch>)` | `align (inline stretch)` |
| overflow | `overflow (x hidden\|auto y hidden\|auto)` | `overflow (y auto)` |
| scroll | `scroll (direction horizontal\|vertical\|both sticky)` | `scroll (direction vertical)` |
| align in parent | `align center\|bottom\|right` | `align center` |
| constraints | `constraints (horizontal <h> vertical <v>)` | `constraints (horizontal left-right vertical top)` |

### Grid

| Meaning | Phrase | Example |
|---|---|---|
| columns | `columns (count N track <track>)` or `columns (tracks (<track> …))` | `columns (count 3 track 1fr)` |
| rows | `rows (…)` | `rows (auto track 80)` |
| cell placement | `place (column N row N columnSpan N rowSpan N)` | `place (column 1 row 0 columnSpan 2)` |

Track word: `N` (fixed px), `Nfr` (flex), `hug`. A **bound** flex weight uses the braced form
`${id}fr` / `${prop.x}fr`; a bare `$id`/`$prop.x` is always a **fixed** ref (even when its id ends
in `fr`), and `{{expr}}fr` binds a flex weight.

### Fills

Each paint in the stack is its own phrase: solid paints use `color`; gradient/image/video paints
use their own keyword (`gradient` / `image` / `video`), each opening a `( … )` group. `color ( … )`
is **only** a solid-with-props form — a `gradient`/`image` nested inside `color ( … )` is silently
dropped, so use the bare keyword.

| Form | Example |
|---|---|
| solid | `color #00B843` · `color $color.accent` · `color {{theme.brand}}` |
| solid with props | `color (#00B843 opacity 0.8 blend multiply)` |
| gradient | `gradient (linear from (0 0) to (0 1) stops (#2563EB at 0) (#0F172A at 1))` |
| image fill | `image (asset «hero» crop focus (0.5 0.5))` |

### Strokes

| Form | Example |
|---|---|
| flat solid | `stroke #CBD5E1 1 outside` (inside + weight 1 are defaults, omit them) |
| record | `stroke (color #CBD5E1 weight 2 align outside dash (4 4) cap round join round)` |
| per-side weight | `stroke (color #CBD5E1 weight-per-side (0 1 0 1))` |

### Effects

One `effect ( … )` per effect. `blur`/`spread`/`radius`/`offset` accept `$var`/`{{expr}}`.

| Form | Example |
|---|---|
| drop shadow | `effect (dropShadow color #0F172A offset (0 2) blur 8 spread 0)` |
| inner shadow | `effect (innerShadow color #000000 offset (0 1) blur 2)` |
| layer blur | `effect (layerBlur 12)` |
| background blur | `effect (backgroundBlur 20)` |

### Corners, opacity, blend

| Meaning | Phrase | Example |
|---|---|---|
| corner radius | `radius N` / `radius (tl tr br bl)` | `radius 12` · `radius (12 12 0 0)` |
| corner smoothing | `smoothing N` | `smoothing 0.6` |
| opacity | `opacity N` / `opacity $var\|{{expr}}` | `opacity 0.8` |
| blend | `blend <mode>` | `blend multiply` |

### Style refs & node name

| Meaning | Phrase | Example |
|---|---|---|
| shared styles | `styles (fill <id> stroke <id> text <id> effect <id> grid <id>)` | `styles (fill color.surface text typography.heading)` |
| layer name | `name «…»` | `name «Mission panel»` |

### Typography (Text nodes)

| Meaning | Phrase | Example |
|---|---|---|
| i18n key | `key <k>` | `key mission.title` |
| font size | `size N` | `size 20` |
| weight | `bold` / `semibold` / `thin` / `weight N` | `bold` |
| font family | `font «family»` | `font «Inter»` |
| line height | `line-height <N\|N%>` | `line-height 32` |
| letter spacing | `tracking <N\|N%>` | `tracking 2%` |
| paragraph spacing | `paragraph-spacing N` | `paragraph-spacing 8` |
| text align | `text-align left\|center\|right\|justified` | `text-align center` |
| vertical align | `text-valign top\|center\|bottom` | `text-valign center` |
| case | `case upper\|lower\|title` | `case upper` |
| decoration | `decoration underline\|strikethrough` | `decoration underline` |
| open-type features | `features ((tag on\|off) …)` | `features ((liga on))` |
| variable-font axes | `axes ((tag N) …)` | `axes ((wght 600))` |
| text style ref | `text-style $<id>` | `text-style $typography.body` |
| bound content | `characters $var\|{{expr}}` | `characters {{mission.name}}` |
| autosize | `autosize height\|both` | `autosize height` |
| ellipsis truncation | `truncate N` | `truncate 2` |
| max lines (no ellipsis) | `maxLines N` | `maxLines 1` |
| list | `list (bullet\|ordered\|none [indent N])` | `list (bullet indent 16)` |

### Rich text: spans & links

| Meaning | Phrase | Example |
|---|---|---|
| styled range | `span (range (a b) style <ref>)` | `span (range (0 3) style typography.link)` |
| link (url) | `link (range (a b) url «…»)` | `link (range (0 5) url «https://…»)` |
| link (node) | `link (range (a b) to <nodeId>)` | `link (range (0 5) to helpScreen)` |

Spans emit in authored order (load-bearing for overlap precedence). Links are re-ordered by
`(start, end)` on round-trip (edge #1) — do not rely on authored link order being preserved.

### Instances & overrides

| Meaning | Phrase | Example |
|---|---|---|
| component id | `Instance of <id>` | `Instance of ds/Card` |
| library | `library <id>` | `library ds` |
| variant | `variant (axis value …)` | `variant (tone warning size md)` |
| props | `props (name value …)` | `props (label «Open» disabled false)` |
| detach / reset | `detach` / `reset` | `detach` |
| slot fill | `slot <name> (<node sentence>)` | `slot actions (Button «Open» color #2563EB)` |
| property override | `override <target> ( … )` | `override header/title (color #111 bold)` |
| nested variant/props | `nested <target> (variant (…) props (…))` | `nested statusBadge (variant (tone warning))` |

`props` values: `name «text»`, `name true/false`, `name N`, `name {{expr}}`, `name (swap <id>)`,
`name (text «…» key <k>)`.

### Media / shapes / vectors / masks

| Meaning | Phrase | Example |
|---|---|---|
| media (Image node) | `media (asset <id> fit\|crop\|tile\|stretch focus center\|(x y) alt «…» poster <id> autoplay loop)` | `media (asset «map» crop focus (0.48 0.42) alt «Mission map»)` |
| polygon/star points | `points N` | `points 6` |
| star inner radius | `inner N` | `inner 0.5` |
| icon ref | `icon <ref>` | `icon nav/plus` |
| svg ref | `svg <ref>` | `svg logo/main` |
| inline path | `path «d» [evenodd]` | `path «M0 0 L10 0 L10 10 Z»` |
| boolean op | `boolean union\|subtract\|intersect\|exclude` | `boolean subtract` |
| mask | `mask alpha\|vector\|luminance [clips (ids)] [from <id>]` | `mask alpha from avatarMask clips (avatarImage)` |

For `boolean`, the operands are the **nested child subtree** — there is no operand id-list phrase.
For media focal points, bindings round-trip on `media (…)` (Image node) but **not** on a fill
paint's `focus` (edge #2).

### Interactions & motion

| Trigger | Example |
|---|---|
| `onClick` / `onPress` / `onHover` / `onDrag` | `onClick navigate (missions/new)` |
| `onKey (key)` | `onKey (Enter) navigate (submit)` |
| `afterDelay (ms)` | `afterDelay (2000) navigate (next)` |

| Action | Example |
|---|---|
| navigate | `navigate (missions/new)` |
| navigate + transition | `navigate (detail) animate (type smartAnimate easing easeOut duration 220)` |
| open overlay | `openOverlay (createDialog) overlay (position center closeOnOutside false)` |
| close overlay | `closeOverlay` |
| back | `back` |
| open link | `openLink («https://…»)` |
| set variable | `setVariable (theme) to (dark)` |
| change variant | `changeToVariant (chip) variant (tone active)` |
| scroll to | `scrollTo (section2)` |

Motion: `motion duration N loop frames (at opacity x y scale rotation) …`. Use a **named** easing
(`easeOut`, `spring …`); a cubic-bezier easing is not expressible and forces the node off the CNL
path (falls to internal IR splice).

### Bindings across families

`$var` (variable ref), `{{expr}}` (data/expression binding), and `$prop.x` (component prop ref)
are accepted on `opacity`, `visible`, `characters`, effect `blur`/`spread`/`radius`/`offset`, grid
`count`/`track`, corner `radius`, and media `asset`/`poster`/`focus`. A bound **flex** track takes
the braced form `${id}fr` (a bare `$id` grid track is always fixed). One caveat:
- A **fill** paint's `focus (x y)` binding is lost (emitted as literal `0`) — edge #2.

## Colors, tokens, units

- Colors: `#RRGGBB`, `#RGB`, `#RRGGBBAA` — **quotes are NOT required** (`color #1E293B` is fine).
- Design tokens (theme variables): `$color.accent`, `$space.4` — a leading `$`, no quotes.
- Data bindings: `{{mission.name}}` — double braces; the extractor creates i18n params.
- Numbers are plain (`16`, `0.8`, `320`). No units, no `px`.
- Text content is the only localized part; put it in `«…»` or `"…"`. Everything else (numbers,
  colors, tokens, enum words, bindings) is language-neutral and never translated.

## Worked examples (all compile clean)

Mission card:

```md
---
screen: missionCard
sourceLocale: en-US
targetLocales: [en-US]
frame: { preset: desktop-1440, width: 1440, height: 1024 }
---

# Mission Card

## Mission card column gap 12 padding 16 color #FFFFFF radius 12

Text «Active missions» size 20 bold color #0F172A
Text «12 in progress» size 14 color #64748B
Rectangle 320 by 4 color #2563EB radius 2
Button «Open» color #2563EB onClick navigate (missions/12)
```

Status chips (grid + gradient + effect):

```md
---
screen: statusChips
sourceLocale: en-US
targetLocales: [en-US]
frame: { preset: desktop-1440, width: 1440, height: 1024 }
---

# Status Chips

## Chips grid columns (count 3 track 1fr) gap 8 padding 8

Rectangle 80 by 24 color #DCFCE7 radius 12 effect (dropShadow color #0F172A offset (0 1) blur 3)
Rectangle 80 by 24 gradient (linear stops (#FEE2E2 at 0) (#FECACA at 1)) radius 12
Text «Nominal» key chips.nominal size 12 color #166534 text-align center
```

Rich text, instance overrides, and bindings:

```md
---
screen: missionDetail
sourceLocale: en-US
targetLocales: [en-US]
frame: { preset: desktop-1440, width: 1440, height: 1024 }
---

# Mission Detail

## Header column gap 8 padding 16 color #FFFFFF

Text «Read the mission brief» key header.brief size 14 span (range (5 12) style typography.link) link (range (5 12) to briefScreen)
Text «Progress» key header.progress size 12 opacity {{mission.progress}}
Instance of ds/Card library ds variant (tone warning) override header/title (color #111 bold) slot actions (Button «Open» color #2563EB) nested statusBadge (variant (tone warning))
Rectangle 320 by 4 color #E2E8F0 radius 2 stroke (color #CBD5E1 weight-per-side (0 1 0 1))
Image 96 by 96 media (asset «avatar» crop focus (0.5 0.5)) mask alpha from avatarMask clips (avatarImage)
```

## DO / DON'T

DO:
- One element per line; keep each line a single sentence.
- Start every element line with a known noun; start containers with `##`/`###` and **name first**.
- Put visible text in `«…»` / `"…"`; use `characters $var`/`{{expr}}` only for bound content.
- Use bare `#hex` for colors, `$token` for theme variables, `{{expr}}` for data bindings.
- Use the braced `${id}fr` form for a **bound** flex grid track; use `Image`-node `media (… focus …)` for bound focal points.

DON'T:
- Don't wrap an element onto a second line, and don't indent with tabs.
- Don't invent property words or nouns — only those in the tables above are recognized.
- Don't put numbers with no keyword (a bare `42` is an error — attach it, e.g. `radius 42`).
- Don't start a container heading with a property word (name it first: `## Panel column`).
- Don't hand-write YAML typed blocks or `` ```ir `` fences — they are internal desugar, not authoring.

## Error catalog (self-check)

If the compiler warns `[CNL:<id>] …`, fix per the id:

| id | Meaning | Fix |
|---|---|---|
| `unknown-keyword` | a word isn't a known property | check spelling; use a word from the tables |
| `missing-value` | keyword has no value | add the value, e.g. `color #00B843` |
| `bad-color` | value after `color`/`fill` isn't a color | use `#hex` or `$token` |
| `bad-number` | value should be a number | e.g. `radius 15`, `gap 16` |
| `incomplete-size` | size needs two numbers via `by`/`x` | `120 by 15` |
| `unterminated-text` | quotes not closed | close `«…»` or `"…"` |
| `bad-direction` | not a valid alignment | `center\|bottom\|right` or `align (block …)` |
| `stray-number` | a number not attached to a property | attach it, e.g. `size 120 by 15` |

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

CNL authors the behavior inputs — interaction triggers (`onClick`, `afterDelay (…)`, …), actions
(`navigate`, `openOverlay`, `setVariable`, …), `motion`, and prototype variables (document-level
`# Prototype Variables`) — but must not serialize live runtime state:

- `currentTimeMs`, playback `playing`/`paused`, or active animation clock;
- active transition instance, transition progress, or sampled visual overrides;
- runtime `history`, current `overlayStack`, hover/pressed/focused/dragging state;
- Scene debug trace, hit target, triggered action list, or timeline markers.

A stable Scene frame has one screen layer plus any overlays. A transition frame may render outgoing
and incoming screen snapshots at the same time, plus overlay/backdrop layers. The renderer receives
layers and sampled overrides from SceneProjection; it must not infer a global `currentScreenId`
from SLM.

## Validation

When working in the repository, compile and check for zero error diagnostics:

```bash
./gradlew :engine:frontend:jvmTest --tests "*Cnl*"
./gradlew :engine:frontend:jvmTest
```

A good SLM output parses with no error diagnostics: stable `screen` id, one sentence per node,
known nouns/keywords, closed quotes, colors as `#hex`/`$token`, and a clear container hierarchy.

If the project exposes a compile helper, also compile the exact SLM source and inspect diagnostics.
A good SLM output has no error diagnostics and passes these checks:

- Parse/frontmatter: required `screen`, valid YAML maps, known frontmatter keys, closed fences.
- Structure: unique stable ids, valid nouns, no cycles, deterministic layer order, source maps
  preserved.
- Layout: valid modes, sizing, constraints, anchors, grid tracks, placement, overflow, scroll, and
  no conflicting `absolute`/auto-layout rules.
- Style/text: resolvable tokens/styles/variables, valid colors, gradients, effects, typography,
  i18n keys/resources, and bounded text with `maxLines`/`truncate` policy.
- Components/assets: component and library refs resolve; variants, props, slots, and overrides
  target existing definitions; media assets, focal points, vector paths, and masks are valid.
- Scene/prototype: triggers and actions are supported; navigation, overlay, scroll, variable, and
  action-set targets resolve; transitions, easing, motion refs, and fallback keyframes are valid.
- Responsive/handoff/export: selectors use known dimensions, variants are not ambiguous,
  annotations/measurements target existing nodes, and export formats/settings are supported.
- Canvas/Scene boundary: authored SLM includes behavior definitions but no live runtime state.
- Generator hygiene: no outer Markdown fences, no explanatory prose in the source, no invented
  nouns/keywords, no `characters «literal»`, and no hand-written YAML typed blocks or `` ```ir ``.

When validation fails, fix the CNL sentence. Do not paper over parser errors by moving required
properties into free-form prose or by hand-writing internal typed blocks.
