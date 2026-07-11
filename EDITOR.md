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

Property edits that the source can express **write back into the owning `*.layout.md`**
(recompile keeps the fingerprint chain valid, and the in-memory node is mirrored in
lock-step) and are **saved locally** (browser `localStorage` on web, a file on desktop,
SharedPreferences on Android) with debounced autosave + Save/Reset, restored on boot. The
writer depends on how the node is authored: a **CNL-owned** node (the 4 demos are CNL-only)
routes through `CnlWriter` (see "CNL write-back" below); a legacy YAML-authored source is
patched by `SlmPatcher` and the YAML writers named below. Covered today, all through the
`writeBackEdits` helper (`DesignEditorReducer`):

- **Geometry / node contract:** resize, position, constraints, visibility, lock, rename.
- **Layout / appearance scalars:** layout mode, gap, padding; opacity, corner radius; text.
- **Style lists (Tier-2):** fills, strokes, effects — for CNL-owned nodes (the 4 demos, all
  CNL-only) the whole stack re-emits as `color`/`stroke`/`effect` phrases via `CnlWriter` /
  `CnlEmitter` (see "CNL write-back" below). For a legacy YAML-authored source the list is
  re-serialized from the IR via `StyleYamlWriter` (`SetFills`/`SetStrokes`/`SetEffects`),
  preserving `token:` refs and `#hex` literals; `YamlPayload.Sequence.replaceWhole` rewrites the
  existing list. `StyleYamlWriter` is the YAML-source-only path (removed once no YAML sources remain).

A non-anchor / in-memory-created node (no source span) falls back to an **in-memory-only**
edit, so the canvas still reflects it. **Typography** and **structural** edits now write back too:

- **Typography (`text:` style):** `SetTextStyle` merges the `DesignTextStyle`; for a CNL-owned node
  it writes back through `CnlWriter.textStylePlan` as tier-1/tier-2 typography phrases
  (`size`/`font`/`weight`/`line-height`/`tracking`/`paragraph-spacing`/`text-align`/`text-valign`).
  For a legacy YAML source it re-serializes into the node's `text.typography` block via
  `TypographyYamlWriter` (the faithful inverse of the reader — bare px vs `{unit: percent}` maps,
  `$token` refs for bound size/weight), through the same `writeBackEdits` path.
  `TypographyYamlWriter` is the YAML-source-only path.
- **Structural (create / delete / duplicate / reorder / reparent) + new screens:** for CNL-owned
  sources the CNL analogue is `CnlEmitter.emitStableSubtree` / `emitStableHeadingLine` — a fresh
  heading + one-sentence-per-node subtree carrying an **explicit minted id** for id-stable inserts.
  A dedicated section emitter drives new IR-carrying edits `InsertChildSubtree` / `DeleteSection` /
  `MoveSection` (`NodeSectionWriter` renders a fresh heading section with an **explicit minted id**;
  `SectionWriter` deletes / relocates / relevels heading footprints — the YAML-source emitters);
  a reorder persists as an `order:` scalar
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

#### CNL write-back (the demos' path)

The 4 bundled demos are **CNL-only** (English-only, one sentence per node, at full IR parity), so a
CNL-authored node routes its write-back through `CnlWriter` rather than the YAML writers above. A node
is CNL-owned when its source span belongs to the CNL front-slice (`SlmEditIndex.cnlOwners`). `CnlWriter`
patches the owning sentence in three tiers, cheapest first:

- **Tier-1 — value span-replace** (`CnlWriter.surgicalPlan`): replace a single value token's span in
  place (e.g. `opacity 0.6` → `opacity 0.8`, a `#hex`, a `position X Y` coordinate) without touching
  the rest of the sentence.
- **Tier-2 — phrase-append**: the property has no phrase yet, so append the missing phrase at the end
  of the sentence (e.g. adding `radius 8`, a `bold`, or a `line-height 140%` phrase). Phrase order in
  the emitted sentence is canonical (the `CnlGrammar` descriptor `order` field), but an appended phrase
  is still parsed correctly regardless of position.
- **Tier-3 — whole-sentence re-emit** (`CnlWriter.reemitPlan`): when neither surgical tier fits,
  `CnlEmitter.emitSentence` (or `emitStableHeadingLine` for a container heading) regenerates the entire
  sentence / heading line deterministically from the patched node, in canonical phrase order.

A CNL-owned node **never falls back to a YAML typed block** — the typed blocks are internal desugar
machinery, not an authoring surface. If even tier-3 is unavailable (no patched node to re-emit from),
the edit stays in-memory only (`WritePlan.Failed`), and the canvas still reflects it.

Structural CNL edits use the same `CnlEmitter` stable-subtree path noted above
(`emitStableSubtree` / `emitStableHeadingLine`) with an **explicit minted `id`** so node identity
survives recompile — the CNL analogue of `NodeSectionWriter`; new screens still append a fresh
`*.layout.md` (CNL body) via `ScreenSourceWriter`.

**Anti-corruption fidelity veto.** Every CNL write-back tier is guarded exactly like the structural
`withStructuralSource` net: the reducer recompiles the single owning source after the patch and, if the
recompiled node diverges from the intended node (id-set drift, or the patched node not matching the
intended fields), **vetoes** the write — the source is left byte-identical and the edit is kept
in-memory. So a CNL source is never corrupted by an edit it can't faithfully round-trip.

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

**Typography (ch. 16)** — Text tool: click creates auto-width text, drag creates a
fixed-width box; double-click enters an inline editing overlay (Esc exits); inspector
controls for size, weight, line-height, letter-spacing and horizontal alignment; text
fill drives glyph color (it is the node's fill list, not a background).

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
  radius/layout) and style-list (fills/strokes/effects) source write-back (the YAML-source path).
- CNL write-back lives in `:engine:frontend`: `CnlWriteBackTest` (+ `CnlWriter`) covers the
  tier-1 span-replace / tier-2 phrase-append / tier-3 whole-sentence re-emit path and the
  fidelity veto (`./gradlew :engine:frontend:jvmTest`).
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
  patch `*.layout.md` and persist locally. The 4 demos are **CNL-only**, so those nodes are
  owned by `CnlWriter` (`SlmEditIndex.cnlOwners`) with the tier-1 span-replace / tier-2
  phrase-append / tier-3 whole-sentence re-emit model plus the anti-corruption fidelity veto —
  a CNL node never falls back to a YAML typed block. Structural write-back mints an explicit id,
  synthesizes / removes / relevels heading sections (CNL: `CnlEmitter` stable-subtree re-emit)
  and is guarded by a post-recompile id-preservation veto (`withStructuralSource`, which likewise
  guards CNL structural re-emit). Remaining **in-memory-only fallbacks**
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
