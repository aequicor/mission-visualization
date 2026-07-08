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

`ResizeNode` is the one intent that writes **back into the SLM source**: it patches
the owning `*.layout.md` via `SlmPatcher`, recompiles it (keeping the fingerprint
chain valid) and also updates the in-memory node. Every **other** edit — create,
delete, duplicate, reorder, reparent, move, fills/strokes/effects, typography,
layout, appearance — mutates the **in-memory working document only**.

Why: `SlmPatcher` is a surgical scalar patcher by design; it *cannot insert, delete
or move nodes* (that would break the stable-id contract), and it only addresses a
fixed set of property paths. So structural and most property edits cannot round-trip
to source with the current write-back layer. The working document is therefore the
single source of truth for the session; the samples still load and compile unchanged
(backwards compatibility preserved). Extending write-back is a follow-up (below).

## What works

**Workspace (ch. 07)** — resizable Source/Inspector splitters with a resize cursor on
hover (real AWT cursors on desktop via `expect/actual`), no text selection during
drag, double-click to reset width; collapse/expand of both side panels with a rail to
restore; Main-only focus mode (Esc or the exit button); canvas zoom (buttons +
scroll-to-cursor), pan (Hand tool), fit-screen and fit-selection, 1:1; responsive
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
front-first paint order, per-row reorder and visibility/lock.

**Position (ch. 12)** — inspector X/Y/W/H editing (parent-relative), arrow-key nudge and
Shift+arrow big-nudge, aspect-ratio lock, rotation field, flip, z-order via
reorder/Layers, undo/redo.

**Appearance / Fill / Stroke (ch. 13–15)** — layer opacity + blend mode + corner radius;
effects stack (drop/inner shadow, layer/background blur) with per-effect visibility
toggle, type switch, remove and shadow X/Y/blur; fill stack with add/remove/toggle/
reorder, solid + linear/radial gradient (stops: add/remove/recolor/reverse), per-fill
opacity separate from layer opacity, image-fill placeholder; stroke add/remove/toggle,
color/opacity/weight, inside/center/outside position, dashed, and caps/endpoints for
lines and arrows. Resize preserves stroke weight (weight is independent of size).

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
  undo/redo, drag coalescing.
- `VectorPathEditingTest` — SVG anchor parse + translate.

Run: `./gradlew :shared:jvmTest` (engine: `:engine:ir:jvmTest`, `:engine:frontend:jvmTest`).

## Known gaps / follow-ups

- **Write-back coverage.** Only `ResizeNode` patches `*.layout.md`. Structural and most
  property edits are in-memory only; in-memory-created screens have no SLM source.
  Closing this needs `SlmPatcher` insert/delete/move-node support (or an IR→SLM
  emitter) — a substantial, separate task.
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
- **Canvas rotation handle.** Rotation is inspector-only; no on-canvas rotate affordance.
- **Export image.** Not implemented (was the last planned slice; not an acceptance item).
- **Scale tool.** Resize is implemented; a separate proportional Scale tool is a
  follow-up (design-book notes them as distinct modes).
