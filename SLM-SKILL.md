---
name: semantic-layout-markdown
description: Author, improve, validate, rewrite, or explain Semantic Layout Markdown (*.layout.md, SLM) and its CNL controlled-natural-language layer — Markdown whose elements are one-line sentences that compile to a language-neutral UI IR with i18n. Use for a screen intent, wireframe, UI screen, design-system instance tree, prototype flow, or any renderer-independent screen spec, and for reviewing or fixing existing SLM, CNL, UI IR, i18n, or Figma-like screen specs. Works standalone with no project, and with small or weak models.
---

# Semantic Layout Markdown (SLM + CNL)

Describe a UI screen as Markdown. Each visible element is **one short sentence**
(a noun plus `keyword value` phrases) or a typed YAML block. The compiler turns
it into a **language-neutral IR** plus i18n resources, so the language of the
description never leaks into the rendered UI.

This skill is self-contained: use the vocabulary below when no host project is
available. When a host project ships its own SLM compiler, prefer that.

## Purpose

Create, improve, review, and validate SLM/CNL screen documents. Pick the
smallest authoring form that makes each property survive to IR — CNL sentences
for common visuals, typed blocks for anything exact.

## Working Contract

- Generating SLM: the reply **is** the `.layout.md`. The first character must be
  `---`. No text before it. No surrounding ``` fence. No explanation after it.
- The document must have frontmatter, exactly one `#` screen heading, and real
  nested nodes.
- Explaining, reviewing, or auditing: answer in normal prose instead.
- Editing an existing file: change the file; do not paste a new document into
  chat.

**Safe recipe (follow exactly when unsure):**

1. Open with `---`, a `screen:` id, `sourceLocale:`, `targetLocales:`, and a
   `frame:` size, then `---`.
2. Add one `# Screen Title`.
3. Make each section a `## Heading` container with a direction word
   (`column`/`row`), plus `gap`/`padding` and a `color`.
4. Put each visible element on its own line, starting with a noun.
5. Wrap visible text in `«…»` or `"…"`. Everything else is numbers, `#hex`, or
   `$token`.
6. If a sentence can't express something, add a typed YAML block under that line.

## Context Intake

- If a host project implements SLM, inspect its parser, tests, and CNL
  vocabulary, and prefer them. Executable behavior beats prose docs on conflict.
- If there is no project, or no SLM implementation, use the grammar and contract
  in **this skill** as the source of truth, and state any assumption.
- Do not depend on specific file paths or build commands; projects differ.

## Authoring Reasoning

1. **Fix the screen contract.** screen id, page, source locale, target locales,
   platform, theme, density, frame size, data inputs, empty/loading/error
   states, primary actions, navigation destinations.
2. **Build information architecture.** root/shell, topbar/sidebar/main, panels,
   repeated lists/cards, tables, dialogs, overlays, status regions.
3. **Choose precision.** CNL sentences for simple, common elements; typed blocks
   for layout/style/text/component/media/vector/interaction/responsive/handoff/
   export; the `ir` escape hatch only for exact *supported* IR.
4. **Lay out flow-first.** Use direction, padding, gap, fill/hug/fixed, min/max,
   and overflow before absolute positioning.
5. **Handle text and i18n.** Visible text is localizable; ids, tokens, routes,
   bindings, component refs, and action names are not translated.
6. **Add interactions and states** only when they belong to the requested
   screen.
7. **Check representation.** Every visible thing is a real node, never hidden
   inside opaque props.

## CNL Guidance

One line = one element. Start with a noun; add `keyword value` phrases in any
order; put visible text in `«…»` or `"…"`.

**Nouns** (line start): `rectangle` · `ellipse`/`circle` · `line` · `star` ·
`polygon` · `arrow` · `text` · `button` · `frame` · `group` · `image` · `icon` ·
`instance`.

**Property phrases** (value = number, `#hex`, `$token`, or an enum word):

| Write | Means |
|---|---|
| `120 by 40` | size, width by height |
| `width 320` / `height 48` | one axis |
| `color #2563EB` / `color $color.accent` | fill |
| `stroke #CBD5E1 1 inside` | stroke color, weight, side |
| `radius 12` | corner radius |
| `rotate 30` | rotation in degrees |
| `opacity 0.8` | opacity, 0–1 |
| `padding 16` (or `12 24`, or four numbers) | padding |
| `gap 16` | spacing between children |
| `column` / `row` / `grid` / `free` | container direction |
| `align center` (top/bottom/left/right/center) | placement in parent |
| `position 72 96` | free X Y placement |
| `size 20` | font size (on text) |
| `bold` / `semibold` / `thin` | font weight (on text) |
| `«text»` or `"text"` | the visible, localized text |

Container headings take the same phrases **after the name**:

```md
## Mission card column gap 12 padding 16 color #FFFFFF radius 12

Text «Active missions» size 20 bold color #0F172A
Button «Open» color #2563EB
```

Keep to this keyword set — it is fixed and English by default. Do not invent
keyword or noun words; an unknown word is dropped or left as prose. Only the
quoted text is translated. When a sentence gets ambiguous, switch that element
to a typed block.

## Typed Blocks Guidance

A typed block is YAML placed **directly under** the element line or heading it
describes. Keys must be spelled exactly and indented under the block key. A
typed block overrides a CNL sentence or prose for the same property. Categories:

- **node** — type, id, name, order, visibility, position, constraints.
- **layout** — direction, sizing, padding, gap, grid, clipping, overflow, scroll.
- **style** — fills, strokes, radius, opacity, effects, tokens/style refs.
- **text** — i18n key, default text, typography, wrapping/truncation/max-lines.
- **component / props / overrides** — instances, variants, slots, bindings.
- **media / shape / vector / mask** — images/video, primitives, paths, masks.
- **action / interaction / motion** — behavior and prototype.
- **responsive / variables / handoff / export** — modes, prototype variables,
  notes, export assets (`variables` and `handoff` are document-level).
- **ir** — rare exact escape hatch, fenced only.

Precedence, highest first:

```text
explicit typed block  >  frontmatter defaults  >  CNL/prose extraction  >  renderer defaults
```

Use a typed block whenever ambiguity, precision loss, or a lost property would
matter. Keep block and sentence consistent — never let them contradict.

```md
### Text: Count
node: { type: text, id: missionCount }
text:
  key: sample.count
  defaultText: "12 in progress"
  maxLines: 1
  overflow: truncate
```

## Prototype, Motion And Scene

SLM authors *behavior*, not live runtime state. Keep two layers apart:

- **Canvas** — the static screen: node tree, layout, style, text, assets,
  responsive variants.
- **Scene** — the runtime/prototype layer over IR: navigation, overlays,
  transitions, prototype variables, action sets, motion playback.

SLM may set Scene **inputs**: `interaction`, `action`, `variables.prototype`,
`motion`, `flow`, overlay destinations, transition parameters.

SLM must **not** serialize live runtime state: the animation clock or playback
time; hover/pressed/focused/dragging; the current overlay stack as a fact;
transition progress; sampled animation values; editor selection or handles.

For animation, describe the trigger, action, destination, and transition intent.
Use duration/easing/spring/keyframes only when they are part of the design. Use
`motion.ref` for complex specs and inline `fallback` keyframes only when useful;
a `motion.ref` must resolve or carry a fallback, or it fails. Do not invent
motion features the target lacks. If behavior matters, express it in a typed
block, not prose.

```md
### Create Mission Button
interaction:
  trigger: onClick
  action: openOverlay
  destination: createMissionDialog
  animation:
    type: smartAnimate
    easing: easeOut
    durationMs: 220
```

## Design Quality Principles

- Prefer meaningful frames over generic groups when layout matters.
- Use cards only for repeated items, modals, and framed tools.
- Give bounded text a wrap / truncate / max-lines policy.
- Avoid unresolved design-system refs.
- Avoid imaginary widgets the compiler/renderer does not support.
- Prefer primitives when component-library availability is uncertain.
- Never rely on accidental overlap for layout.
- Use absolute positioning only with intent, bounds, and constraints.
- Use tokens / style refs when available; raw values only when appropriate.
- Author visible repeated rows as actual nodes, not opaque props.
- Data, component props, and bindings are not automatically rendered children.

## Boundary

Model the final screen: the visible node tree; layout, style, typography; i18n
text; components and instances; variables and modes; assets/media/vectors/masks;
interactions and motion references; responsive variants; handoff/export metadata.

Do not model: version history; multiplayer state; permissions/billing; AI prompt
history; editor hover/selection/drag state; live runtime playback state; sampled
Scene frame state.

## Minimal Shape

Simplest valid screen (pure CNL). Real output has **no** outer ``` fence:

```md
---
screen: statusChips
sourceLocale: en-US
targetLocales: [en-US]
frame: { width: 1440, height: 1024 }
---

# Status Chips

## Chips row gap 8 padding 8

Rectangle 80 by 24 color #DCFCE7 radius 12
Text "Nominal" size 12 color #166534
```

Same shape with one typed block for exact text behavior:

```md
---
screen: sampleScreen
sourceLocale: en-US
targetLocales: [en-US]
frame: { preset: desktop-1440, width: 1440, height: 1024 }
---

# Sample Screen

## Panel column gap 16 padding 24 color #FFFFFF radius 12

Text «Active missions» size 20 bold color #0F172A

### Text: Count
node: { type: text, id: missionCount }
text:
  key: sample.count
  defaultText: "12 in progress"
  maxLines: 1
  overflow: truncate
```

## Escape Hatches And Failure Handling

- CNL is for common visual grammar.
- Typed blocks are for exactness.
- `ir` is only for exact supported IR (fenced), never for invented types.
- Turn an unsupported or ambiguous feature into a note or diagnostic — never
  guess it into prose.
- When a component, library, token, or asset may not exist, build from
  primitives (`frame`, `text`, `shape`, `image`) or state the assumption.

**Common mistakes to avoid:**

- Text before `---`, or wrapping the whole answer in a ``` fence.
- Two elements on one line, or an element line not starting with a known noun.
- A bare number with no keyword — attach it (`radius 12`, not `12`).
- Invented noun/property/widget words.
- `{{...}}` inside a typed block's `defaultText` (that is literal text there) —
  put dynamic values in Markdown text lines instead.
- Referencing a component/token/asset you cannot confirm exists.

## Validation / Self-check

Before returning, confirm:

- [ ] No prose before frontmatter in raw SLM output.
- [ ] No outer ``` fence when returning raw SLM.
- [ ] Required frontmatter exists (`screen`, `sourceLocale`).
- [ ] Exactly one `#` screen heading.
- [ ] Stable, unique ids where needed.
- [ ] Every visible thing is a real node.
- [ ] CNL lines are one element per line.
- [ ] No invented CNL keywords.
- [ ] Typed-block keys are valid and correctly indented.
- [ ] Typed blocks do not silently conflict with CNL.
- [ ] No unresolved component / library / token / asset refs.
- [ ] No `{{...}}` inside literal `text.defaultText` unless braces must show.
- [ ] Bounded text has a wrap / truncate / max-lines policy.
- [ ] Layout has explicit sizing / overflow where needed.
- [ ] Absolute positioning has intent and constraints.
- [ ] Actions and destinations resolve.
- [ ] Authored prototype behavior is present when required.
- [ ] Scene-related SLM holds definitions, not live runtime state.
- [ ] Interactions have valid triggers, actions, destinations, overlay targets.
- [ ] Motion refs or fallback keyframes resolve.
- [ ] Transitions do not depend on sampled runtime values.
- [ ] Static screen data is not mixed with editor/runtime-only state.
- [ ] Responsive variants are unambiguous.
- [ ] `ir` is used only for exact supported IR.
- [ ] Compile with the project's tooling when it exists.

If a check fails, fix the source structure or typed blocks. Do not hide errors
by moving required properties into natural-language prose.
