# Mission Editor — implemented scope & gaps

This note describes the interactive visual editor built on top of the SLM → IR →
Compose pipeline. It is the reference for what works, how it is wired, and what is
deliberately left as a follow-up. UX requirements come from `design-book/07..16`.

## Architecture

Two independent state trees, kept strictly apart (design-book ch. 07):

- **Document state** — `DesignEditorState` (`editor/presentation`): the working
  `DesignDocument`, the per-page SLM `sources` + `compiledResults`, multi-selection
  (`selectedNodeIds` + primary `selectedNodeId`), text-editing target, and undo/redo
  stacks. Every user document action is a `DesignEditorIntent` reduced by the pure
  `reduceDesignEditor(state, intent)`.
- **Workspace state** — `EditorWorkspaceState`: panel widths, collapse flags, focus
  mode, active tool, canvas zoom/pan, hovered node, layer collapse set, vector-edit
  target. This is a personal view preference and never touches the document.

The holder `MissionEditorStateHolder` owns both (`designState`, `workspace`) plus the
last computed `artboardLayout` (`LayoutBox`, document coordinates). UI is split into
panes under `editor/ui`: `EditorSourcePane` (layers/screens/markdown),
`EditorCanvasPane` (viewport + all direct manipulation), `EditorInspectorPane`
(context inspector). The renderer `DesignArtboard` (`:engine:backend-compose`) is a
pure renderer given an explicit `CanvasViewport`; all gestures live in the app layer.

### Persistence model (important)

Property edits that SLM can express **write back into the owning `*.layout.md`** via
`SlmPatcher` (recompile keeps the fingerprint chain valid, and the in-memory node is
mirrored in lock-step) and are **saved locally** (browser `localStorage` on web, a file
on desktop, SharedPreferences on Android) with debounced autosave + Save/Reset, restored
on boot. Covered today, all through the `writeBackEdits` helper (`DesignEditorReducer`):

- **Geometry / node contract:** resize, position, constraints, visibility, lock, rename.
- **Layout / appearance scalars:** layout mode, gap, padding; opacity, corner radius; text.
- **Style lists (Tier-2):** fills, strokes, effects — the whole list is re-serialized from
  the IR via `StyleYamlWriter` (`SetFills`/`SetStrokes`/`SetEffects`), preserving `token:`
  refs and `#hex` literals; `YamlPayload.Sequence.replaceWhole` rewrites the existing list.

A non-anchor / in-memory-created node (no source span) falls back to an **in-memory-only**
edit, so the canvas still reflects it. **Typography** and **structural** edits now write back too:

- **Typography (`text:` style):** `SetTextStyle` re-serializes the merged `DesignTextStyle` into the
  node's `text.typography` block via `TypographyYamlWriter` (the faithful inverse of the reader —
  bare px vs `{unit: percent}` maps, `$token` refs for bound size/weight), through the same
  `writeBackEdits` path.
- **Structural (create / delete / duplicate / reorder / reparent) + new screens:** a dedicated
  section emitter (`NodeSectionWriter` renders a fresh heading section with an **explicit minted id**;
  `SectionWriter` deletes / relocates / relevels heading footprints) drives new IR-carrying edits
  `InsertChildSubtree` / `DeleteSection` / `MoveSection`; a reorder persists as an `order:` scalar
  batch over the sibling run; a new screen appends its own `*.layout.md` via `ScreenSourceWriter`. The
  reducer wrapper `withStructuralSource` recompiles the single owning source and **vetoes** the patch
  (keeping the in-memory edit, every source byte-identical) whenever the recompiled node-id set drifts
  from the expected set — or, for reparent, the moved node's parent isn't the new parent — a
  non-corrupting id-preservation net.

Some structural moves are deliberately **not expressible** as a single-source patch and stay
in-memory (canvas reflects them; sources untouched): a **multi-page delete**, a **cross-page reparent**,
a reparent whose post-move heading depth would exceed ATX level 6, any op touching a node with no
addressable heading anchor (an `ir` splice or prose sibling), and **instance / media / vector-path**
subtrees the emitter can't round-trip. One caveat inherited from all write-back tiers: **undo does not
revert sources** — `undo()`/`redo()` swap only the in-memory document, so after an undo the canvas
reverts but the source keeps the last write-back. The working document remains the single source of
truth within a session; samples still load and compile unchanged.

## What works

**Workspace (ch. 07)** — resizable Source/Inspector splitters with a resize cursor on
hover (real AWT cursors on desktop via `expect/actual`), no text selection during
drag, double-click to reset width; collapse/expand of both side panels with a rail to
restore; Main-only focus mode (Esc or the exit button); canvas zoom (buttons +
scroll-to-cursor), pan (Ctrl-drag), fit-screen and fit-selection, 1:1; responsive
stacked layout on narrow viewports. Workspace state is separate from the document.

**Frames (ch. 09)** — each screen is a top-level frame + its own page; new screens via
the Screens `+` preset menu (Desktop/Tablet/Mobile/Square); the Frame tool creates
nested frames inside whatever frame is under the cursor; resize via inspector W/H
(write-back) and canvas corner/side handles; clip-content toggle; frame fill / stroke
/ radius / effects in the inspector.

**Objects (ch. 10)** — hover outline; click / Shift-click / marquee multi-selection;
selection synchronized across Canvas ↔ Layers ↔ Inspector; drag-move and handle-resize
on canvas (one undo entry per drag via `BeginInteraction`/`EndInteraction`
coalescing); lock/visibility/delete/duplicate; Layers tree with expand/collapse,
front-first paint order, per-row reorder and visibility/lock. A press whose top-most hit
is the current selection — or a descendant showing through inside a selected container —
drags the selection instead of re-selecting the nested/behind object under the cursor
(`pressHitBelongsToSelection`; design-book §10 "drag moves object"). An unrelated object
stacked on top is not part of the selection, so it still wins the press (§10 "topmost
selectable layer gets priority"); a nested object is reached by double-click.

**Position (ch. 12)** — inspector X/Y/W/H editing (parent-relative), arrow-key nudge and
Shift+arrow big-nudge, aspect-ratio lock, rotation field, flip, z-order via
reorder/Layers and canvas keyboard shortcuts (`]`/`[` bring to front / send to back,
Cmd/Ctrl+`]`/`[` bring forward / send backward — restacking the **primary** selection, like
the Layers per-row reorder; restacking a whole multi-selection as a block is a follow-up),
undo/redo.

**Advanced positioning preview (ch. 18)** — central anchor lines (dashed at rest, emphasized
solid while dragging) through the selected component's center, extended to its parent frame
edges; an on-canvas rotate affordance above the selection that drags the object's rotation
live (Shift snaps to 15°); selection outline, handles and hit-testing all follow the rotated
geometry, not the old axis-aligned box (rotating only the fill while the frame stayed put was
the previous, invalid behavior); resize-handle cursors change on hover before drag starts and
account for the component's rotation (bucketed into the four cursor orientations Compose
actually has); resizing a rotated component inverse-rotates the drag delta so a corner grows
along the edge the user is dragging; a persistent `W x H` size badge under the selection;
holding `Alt` shows a read-only red measurement overlay (selected/hover outlines, center line,
directional gap or center-distance badges) against the hovered sibling or, by default, the
parent frame — releasing `Alt` clears it without touching selection, geometry or document
state. All of this is overlay-only state in `EditorCanvasPane`/`CanvasGeometry.kt`
(`:shared`), never serialized into the document.

**Beautiful-anchor snapping (ch. 18 + "beautiful positions")** — while free-moving a
coordinate-positioned selection, `computeAnchors` (`CanvasGeometry.kt`) magnetically snaps each
axis independently to the nearest "beautiful" line within a 6-screen-px radius (constant on
screen: the radius is `/ zoom`ed into document units). Candidates: **edge/center alignment** to
containers and sibling peers (Figma's blue guides, via the retained `snapAxis` core), the
container's **golden-ratio** lines (0.382 / 0.618, amber guide + "φ" badge), its simple
**proportion** lines (thirds 1/3·2/3 and quarters 1/4·3/4 — halves == center, so not repeated;
dashed amber guide + fraction badge), and the **equal-distance family** — all drawn as green
measurement bars with px gap badges, over siblings that overlap the box on the perpendicular
axis (so a real row/column gap exists): *equal spacing* (centre between two flanking siblings),
*equal margin* (centre between a sibling and the container wall on the other side), and *match
gap* (the gap to a neighbour duplicates an existing gap between two other siblings — the matched
reference gap gets its own bar too). Containers are the immediate parent frame *plus its
unrotated ancestors up to the root*, so a nested node can still anchor to an outer/root
container. One winner per axis; ties break by priority (alignment > equal spacing > match gap >
equal margin > golden > proportion). `computeSnap` is retained as the alignment-only subset
(delegates to `computeAnchors`, keeps its regression suite). All overlay-only, never serialized.

**Auto layout boundary (ch. 18)** — `MoveNodes`/canvas drag only ever repositions coordinate-
positioned nodes (`isCoordinatePositioned`); an Auto layout flow child can't be dragged to an
arbitrary position (the layout engine ignores its `position`), so a Move drag on one instead
previews a reorder within its flow parent: a live insertion-line indicator
(`flowInsertionIndex`/`flowInsertionLine` in `CanvasGeometry.kt`) tracks the pointer along the
parent's main axis, and release dispatches `ReparentNode` at the resolved index (one undo
entry). Detaching a flow child into free positioning is a separate, explicit action — the
inspector's Position section shows an "Absolute position inside frame" button for any
non-coordinate-positioned selection, dispatching `SetAbsolutePosition` (sets
`layoutChild.absolute = true` and captures the child's current resolved position so it doesn't
jump). Both behaviors match design-book §18's "Auto layout boundary" note; grid-mode auto
layout parents are out of scope for the reorder preview (1D flow only).

**Appearance / Fill / Stroke (ch. 13–15)** — layer opacity + blend mode + corner radius;
effects stack (drop/inner shadow, layer/background blur) with per-effect visibility
toggle, type switch, remove and shadow X/Y/blur; fill stack with add/remove/toggle/
reorder, solid + linear/radial gradient (stops: add/remove/recolor/reverse), per-fill
opacity separate from layer opacity, image-fill placeholder; stroke add/remove/toggle,
color/opacity/weight, inside/center/outside position, dashed, and caps/endpoints for
lines and arrows. Resize preserves stroke weight (weight is independent of size).
Fill/stroke/gradient-stop swatches resolve variable-bound colors (`{"§var": "..."}`, the
common case in the bundled samples) to the token's actual default-mode value — following
alias chains — instead of a flat black/blue placeholder, so the inspector swatch matches
what the canvas renders via `DesignResolver`.

**Typography (ch. 16 + typography subsystem)** — the whole text stack lives in
`:subsystems:typography` (pure model/algebra/measure) + `:subsystems:typography-compose`
(render/fonts); the editor consumes both. Text tool: click creates auto-width text, drag
a fixed-width box; double-click enters an inline editing overlay: a transparent,
metric-matched `BasicTextField` owns text input/IME/selection (its own caret and selection
highlight are hidden), while the **caret and selection are drawn from the node's real text
layout** (`textEditGeometry` → `LaidOutRichText.caretRect`/`selectionRects`, mapped
document→screen through the viewport), so they follow wrapping, mixed sizes, alignment and
per-range styling exactly. The overlay reports its caret/selection into
`EditorWorkspaceState.textSelection`.
The inspector Typography panel mirrors Figma: font family (rows previewed in their own
face), style (weight + italic, "Mixed" across a selection), size, line-height (Auto/px/%),
letter-spacing, H+V alignment, and a "type settings" popover for decoration
(kind/style/color/thickness/skip-ink), case (incl. small caps), super/subscript, lists,
paragraph spacing/indent, leading trim, hanging punctuation and OpenType/variable-axis
toggles. **Per-range styling (изменение стиля части строки):** with a non-collapsed
selection the same controls dispatch `UpdateTypographyRange`/`SetTextRangeFills`/`SetTextLink`
instead of the node-level `UpdateTypography`; the reducer splits/merges the node's
`styleRanges` via `TextRangeEditing` (the IR-native sibling of the subsystem's `SpanAlgebra`)
and `OffsetHealing` keeps offsets sane across content edits. Text fill drives glyph color
(node fill list, incl. gradient text via a brush).

**Typography write-back** — node-level typography → `text.typography` (`SetTextStyle`);
character ranges → `text.spans` (`SetTextSpans`, range/typography/fills/link/shared-style-ref);
auto-resize → `text.resizing`; truncation → `text.maxLines`/`overflow`. Span write-back is gated
to nodes whose rendered string equals the authored `defaultText` (no ICU params), so `[start,end)`
offsets line up; otherwise the edit stays **in-memory** (canvas reflects it, source untouched).
`YamlPathWriter` now supports **key removal** (`YamlPayload.Remove`), so clearing truncation drops
the authored `maxLines`/`overflow` and clearing a decoration color drops `decorationColor` (both
persist to source instead of falling back in-memory). Known render best-effort: skip-ink
approximates descender boxes (no glyph intercepts); hanging punctuation uses a negative first-line
indent; per-range line-height is paragraph-granular (Compose can't vary it per span within one
line — the tallest overlapping run wins); small caps needs a font with `smcp` (the bundled
families qualify).

**Runtime hyperlinks** — a tap on a text node's link range takes precedence over selection/
interaction: the subsystem's `LaidOutRichText.linkRects()`/`linkAt()` hit-test the point,
`backend-compose.linkAtPoint` adapts it for a `LayoutBox`, and `DesignArtboard`/`SceneRenderer`
expose `onLinkClick`. The Scene stage opens an external `url` via `platformOpenUrl` (expect/actual
across jvm/js/wasmJs/ios; Android is a no-op — no Context is wired) and navigates an internal
`nodeTarget` via `SceneEntry.Screen`. Shared text-style refs on a range now resolve
(`TextStyleRange.styleRef`, base < shared-ref < inline) and instances can override a target's
spans/links (`InstanceOverride.styleRanges`/`links`). The inspector's Typography section carries a
**"Text style" dropdown** (`SetTextRangeStyleRef` → `TextRangeEditing.applyStyleRef` → `text.spans`)
that applies a document text style to the active selection (or the whole node); it lists the
document's `DesignStyle.Text` entries and renders nothing when there are none.

**Deferred typography follow-ups** — genuine tensions, not just unbuilt UI:
- **Debounced SLM recompile while typing** (currently per-keystroke). It *conflicts* with the
  geometry-drawn caret, which needs the in-memory document to update every keystroke (else the caret
  lags the text), and a correct debounce must source-sync span offsets (live in-memory span healing
  desyncs from the still-stale source spans) or it corrupts authored spans. Autosave already debounces
  the expensive localStorage writes; only the (cheap, for these docs) recompile runs per keystroke.
- **Lazy per-family font loading.** Compose-resource `Font()` is composable-only and fetches lazily
  *when a family is first rendered*; the eager +3.8 MB comes from the picker previews, so targeting it
  safely needs browser network profiling rather than a blind edit.
- **Instance per-range overrides authoring UI** (engine model `InstanceOverride.styleRanges/links`
  exists) and **CNL for the remaining typography fields** (italic/decoration done).

**Vector (ch. 11)** — Rectangle/Ellipse/Line/Arrow shape tools; a vector-edit mode
(double-click a vector shape) that renders the path's on-path anchors and lets you
select and drag them (`MoveVectorPoint` over absolute `M/L/C/Q` commands). The model
and reducer are structured so lasso/bend/cut/paint/pen can be added as new tools
without rewriting the path layer.

## Tests

`shared/src/commonTest/.../editor`:
- `DesignEditorReducerWriteBackTest` — SLM source write-back (existing, still green).
- `DesignEditorReducerCommandsTest` — selection, move/nudge, visibility/lock, create
  object/screen, delete/duplicate/reorder/reparent, fills/strokes/effects, typography,
  undo/redo, drag coalescing; the structural/typography cases also assert the owning
  source was rewritten (others byte-identical, source-undo captured).
- `Tier1WriteBackTest` / `Tier2WriteBackTest` — property (rename/visible/lock/opacity/
  radius/layout) and style-list (fills/strokes/effects) source write-back.
- `TypographyWriteBackReducerTest` — `text:` typography written into the owning source
  (px/percent line-height, align tokens, field-by-field merge).
- `StructuralWriteBackTest` — create/delete/duplicate/reorder/reparent source write-back
  plus the in-memory fallbacks (multi-page delete, ir-splice reorder, cross-page / depth-
  overflow reparent, instance duplicate, id-preservation veto).
- `CreateScreenSourceTest` — a new screen appends its own `*.layout.md`, others byte-identical.
- `VectorPathEditingTest` — SVG anchor parse + translate.
- `CanvasGeometryTest` — rotated corner/handle geometry, rotation-aware resize cursor
  bucketing, move-drag center anchor lines, Alt-measurement gap math, and beautiful-anchor
  snapping (golden ratio, thirds/quarters, equal spacing / equal margin / match gap, priority
  tie-breaks) (design-book ch. 18).

Run: `./gradlew :shared:jvmTest` (engine: `:engine:ir:jvmTest`, `:engine:frontend:jvmTest`).

## Known gaps / follow-ups

- **Write-back coverage.** Geometry, node-contract, layout/appearance scalars, style lists
  (fills/strokes/effects), **typography** (`text:` style) and **structural** edits
  (create/delete/duplicate/reorder/reparent, plus new screens as their own `*.layout.md`) all
  patch `*.layout.md` and persist locally. Structural write-back mints an explicit id,
  synthesizes / removes / relevels heading sections and is guarded by a post-recompile
  id-preservation veto (`withStructuralSource`). Remaining **in-memory-only fallbacks**
  (non-corrupting, sources untouched): multi-page delete, cross-page reparent, reparent past
  ATX depth 6, any op on a node without an addressable heading anchor (`ir` splice / prose
  sibling), and instance/media/vector-path subtrees.
- **Undo does not revert sources.** `undo()`/`redo()` swap only the in-memory document; the SLM
  sources keep the last write-back, so after an undo the canvas reverts but the source retains the
  create/delete/move. The working document stays the session source of truth. Reverting sources on
  undo is a cross-cutting follow-up (pre-existing, shared by all write-back tiers).
- **Mixed values.** The inspector on a multi-selection shows the primary node's values
  rather than a per-field `Mixed` placeholder.
- **Flip.** Only mirrors the *arrangement* of a multi-selection (positions); a single
  primitive's geometry isn't mirrored because the IR has no scale/flip transform field.
- **Layers drag-and-drop.** Reparent/reorder exist as intents and reorder is exposed as
  per-row up/down; tree drag-and-drop with an insertion line is not wired.
- **Image fill.** Creates a placeholder `Image` paint; no asset picker / decoding
  (the renderer draws image fills as placeholders by design).
- **Vector advanced.** Pen, lasso, bend (bezier handles), cut, boolean ops and region
  paint are not implemented; only anchor move over simple absolute paths.
- **Export image.** Not implemented (was the last planned slice; not an acceptance item).
- **Scale tool.** Resize is implemented; a separate proportional Scale tool is a
  follow-up (design-book notes them as distinct modes).
