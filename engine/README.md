# engine — SLM design engine (frontend · ir · backend-compose)

The `engine/*` Gradle modules are the successor of the `:shared` `designdoc`
pipeline, split so that the document core stays pure Kotlin and Compose appears
only at the very edge:

- `:engine:ir` — the document core (pure Kotlin, KMP):
  - `model` — typed IR (`slm-ir/1.0`): `DesignDocument`, `DesignNode` +
    `DesignNodeKind` (frames, text with i18n content, shapes/vectors, instances,
    media, tables, slots, annotations), sizing/constraints/auto-layout/grid,
    paints/strokes/effects, masks, interactions/transitions, motion, responsive
    variants, handoff/export metadata, `Bindable` (`$var` / `$prop` / `{{data}}`).
  - `serialization` — hand-rolled forward-compatible JSON readers (+ writer,
    `parseDesignNode` for single-node escape hatches); unknown fields are
    ignored, unknown enum values warn and fall back.
  - `resolve` — `DesignResolver(document, ResolveContext)`: variables/modes,
    styles, component instances (libraries, slots, nested overrides), i18n text
    (ICU-lite), data bindings/conditions/repeat, responsive patches, RTL logical
    mapping, and lowering (media → placeholder fill, table → grid) into a
    concrete `ResolvedNode` tree.
  - `layout` — `DesignLayoutEngine`: pure-Kotlin Figma-semantics solver
    (fixed/hug/fill + min/max, auto-layout H/V with wrap/gap `auto`/baseline,
    grid tracks incl. implicit rows, absolute + constraints/anchors, content
    extents, fixed scroll children). Text metrics come only through the injected
    `DesignTextMeasurer`.
  - `validate` — `validateDesignDocument(document, context, options)`: static
    check groups (structure, layout, styles, text/i18n, components, media/assets,
    interactions, responsive, data, handoff/export) plus opt-in resolve/layout
    probes; diagnostics carry `IR-*` codes.
- `:engine:frontend` — the SLM compiler (pure Kotlin, under construction):
  markdown/frontmatter/typed-block parsing, semantic extraction, normalization
  and slug/order resolution that turn `*.layout.md` sources into the IR.
- `:engine:backend-compose` — this renderer: `DesignArtboard` +
  `ComposeDesignTextMeasurer` + the canvas drawing pass. The only engine module
  that depends on Compose.

## Pipeline

```mermaid
flowchart LR
    A["SLM source<br/>*.layout.md"] --> B["compileSlm<br/>:engine:frontend"]
    B --> C["DesignDocument IR<br/>:engine:ir model"]
    C --> D["DesignResolver(ResolveContext)<br/>:engine:ir resolve"]
    D --> E["DesignLayoutEngine<br/>:engine:ir layout"]
    E --> F["DesignArtboard<br/>:engine:backend-compose"]
    C --> G["validateDesignDocument<br/>:engine:ir validate"]
```

JSON documents enter the same pipeline through `:engine:ir` `serialization`
instead of the frontend.

## Layering rules

- `:engine:ir` and `:engine:frontend` are pure Kotlin — no Compose, no platform
  APIs. Anything platform-specific is injected (e.g. `DesignTextMeasurer`).
- Compose lives only in `:engine:backend-compose`; it depends on `:engine:ir`
  and never on the frontend.
- Rendering math that does not need a brush (crop windows, grid slices, hairline
  positions, mask/hit-test selection) is factored into pure functions
  (`RenderGeometry.kt`) and unit-tested headlessly; the `DrawScope` extensions
  stay a thin drawing layer.

## Renderer surface

`DesignArtboard(document, pageId, …)` resolves, lays out, and draws the first
top-level frame of a page with zoom-to-fit, click-to-select (instance internals
collapse to the instance), and a selection overlay.

- `overlayOptions: DesignOverlayOptions` — opt-in editor overlays: ruler guides
  as full-length hairlines, layout grids (columns/rows/grid, ~8% alpha fills
  honoring count/gutter/margin/alignment), and node-level annotations as
  numbered pins. All off by default; overlays never affect layout.
- `onInteraction: ((ResolvedInteraction, LayoutBox) -> Unit)?` — interaction
  preview. When provided and the tapped node (or nearest ancestor) carries an
  `onClick`/`onPress` interaction, the callback fires **instead of** selection
  for that tap; otherwise taps select as usual. With `onInteraction = null` the
  selection behavior is unchanged.

## Rendering approximations

The renderer favors a fast, dependency-free canvas pass; the following are
deliberate approximations:

- Shadows draw without gaussian blur (offset translucent shape; inner shadows as
  a clipped rim); layer/background blur are ignored.
- `cornerSmoothing` (squircle) is not applied; radii clamp to half of min dimension.
- `booleanOperation` draws its children without path boolean ops.
- SVG path arcs (`A`) are unsupported and end the parse gracefully.
- Bindings (`$var`/`$prop`) on rotation, position, size, effect radii and gradient
  stop positions are not resolved; the parser warns and falls back to defaults.
- Per-side strokes collapse multiple paint layers to one color; stroke align is
  not applied to freeform vector outlines.
- The selection overlay for rotated nodes draws the unrotated bounds
  (hit-testing does account for rotation).
- **No bitmap or video decoding.** Image fills stay a flat placeholder; a media
  node adds a notional crop window (projected from the asset's intrinsic size
  per `fillMode` + `focalPoint` — nothing is actually cropped), a subtle focal
  crosshair, and the asset id label. `Tile` draws a checker pattern; `Stretch`
  draws like `Fill` until real assets load. Video adds a play glyph and poster
  label; a `Video` paint arrives lowered to the same image placeholder.
- **Masks are shape clipping, not alpha sampling.** Alpha, vector, and luminance
  masks all clip their targets (explicit `appliesTo` ids, else following
  siblings) to the mask node's geometry — rounded-rect from box + corner radius,
  ellipse for ellipse shapes, parsed vector path when authored, else the
  bounding box. The mask node itself is not painted.
- **Baseline:** `AlignItems.Baseline` uses the real measured first baseline
  (`ComposeDesignTextMeasurer.firstBaseline`); `BaselineAlign.Last` approximates
  as first baseline + (lineCount − 1) × lineHeight.
- **Tables render as hairlines only:** 1px lines on grid track boundaries
  (derived from laid-out cell edges, centered in the gutters) plus a translucent
  tint over the first grid row as the header band. `headerRows` beyond the first
  row and per-cell alignment are not rendered.
- **Interaction preview is a callback only.** No prototype state machine,
  overlay actions, or transition/easing execution — `onInteraction` reports the
  triggering interaction and hit box, nothing more.
- Overlay data is node-level: guides/layout grids/annotations carried on
  resolved nodes are drawn; document-level handoff annotation targets are not.
