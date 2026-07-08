# Прокаченные позиционирование и preview

[← Оглавление](README.md)

Эта глава описывает отдельный Figma-like блок для Mission Editor: точное
позиционирование component, constraints, transform controls and preview
overlays. Фокус не на Auto layout flow, а на ручном управлении объектом внутри
frame/container и на визуальной обратной связи на canvas.

Figma reference:

- [Adjust alignment, rotation, position, and dimensions](https://help.figma.com/hc/en-us/articles/360039956914-Adjust-alignment-rotation-position-and-dimensions)
- [Apply constraints to define how layers resize](https://help.figma.com/hc/en-us/articles/360039957734-Apply-constraints-to-define-how-layers-resize)
- [Create layout guides](https://help.figma.com/hc/en-us/articles/360040450513-Create-layout-guides)

## Product goal

`base/daily`

Пользователь должен уметь:

- выбрать component на preview;
- выровнять его относительно parent frame;
- задать точные `X/Y/W/H`;
- переместить component мышью через зажатие ЛКМ;
- закрепить поведение component при resize parent через constraints;
- повернуть или отзеркалить component;
- видеть selected state, center lines and dimensions на preview;
- при зажатом `Alt` видеть расстояния до parent или hovered component.

Главный контракт: inspector, canvas preview and document model всегда говорят
об одном и том же объекте. Любое действие мышью обновляет inspector, любое
изменение в inspector сразу обновляет preview.

## Inspector structure

`base`

Right inspector для выбранного component должен показывать блоки в таком
порядке:

- `Alignment`;
- `Position`;
- `Constraints`;
- `Rotation`;
- `Layout / Dimensions`.

Если объект нельзя редактировать:

- locked component disables fields with lock reason;
- hidden component can be selected from layers/source, but preview controls are
  disabled;
- multi-selection shows common values or `Mixed`;
- unsupported controls are hidden, not shown as fake inactive data.

All numeric values:

- measured in px;
- relative to parent frame;
- support decimal precision;
- are undoable after commit;
- use `Enter` to apply and `Escape` to cancel while editing a field.

## Alignment

`daily`

Alignment controls - это быстрые команды, а не persistent layout rules. Они
один раз меняют `X/Y` selected component относительно parent frame или общей
bounding box multi-selection.

Horizontal alignment:

- `Left`: ставит левую грань selection на левую внутреннюю границу parent;
- `Horizontal center`: совмещает center X selection с center X parent;
- `Right`: ставит правую грань selection на правую внутреннюю границу parent.

Vertical alignment:

- `Top`: ставит верхнюю грань selection на верхнюю внутреннюю границу parent;
- `Vertical center`: совмещает center Y selection с center Y parent;
- `Bottom`: ставит нижнюю грань selection на нижнюю внутреннюю границу parent.

Комбинации:

- `Left` + `Top` размещает объект в левом верхнем углу;
- `Horizontal center` + `Vertical center` центрирует объект в parent;
- `Right` + `Bottom` размещает объект в правом нижнем углу;
- остальные комбинации работают через последовательное применение двух осей.

UX details:

- buttons use compact icons, not text labels;
- hover показывает tooltip and optional ghost preview;
- click applies immediately;
- command changes only `X/Y`;
- command does not change `W/H`, rotation, flip or constraints;
- if selection has no single parent, controls are disabled with reason.

## Free positioning and mouse movement

`base`

Position fields:

- `X`: distance from parent left edge to selection left bound;
- `Y`: distance from parent top edge to selection top bound.

Canvas drag:

- user presses ЛКМ on selected component body;
- drag starts after small movement threshold;
- selected component follows pointer in document coordinates;
- `X/Y` fields update live;
- preview shows current position/distance feedback;
- central anchor lines are shown while the component is moving;
- mouse release commits one undoable move operation;
- `Escape` during drag cancels and returns object to original position.

Central anchor lines during movement:

- this is a critical feature, not an optional polish item;
- while dragging, preview draws the active center axis of the moving component;
- horizontal movement/alignment feedback shows a horizontal line through
  component center Y, extending to parent frame edges;
- vertical movement/alignment feedback shows a vertical line through component
  center X, extending to parent frame edges;
- the line uses the active measurement/accent color and stays readable over the
  frame;
- small anchor markers can be drawn at parent edges and at the component center
  crossing;
- the lines update every pointer move before commit;
- the lines are preview-only overlay state and never mutate document geometry.

Modifiers:

- `Shift` locks movement to the dominant axis;
- arrow keys nudge by 1 px;
- `Shift + arrow` nudges by 10 px;
- snap can align to parent center, parent edges, sibling edges and sibling
  centers if snap is enabled.

Auto layout boundary:

- free positioning applies to free/absolute children;
- Auto layout children should reorder or show insertion line during drag;
- switching an Auto layout child to absolute positioning requires explicit user
  action such as `Absolute position inside frame`.

## Constraints

`daily/advanced`

Constraints define how component reacts when parent frame changes size. They
are persistent rules stored on child component, not a one-time alignment action.

Horizontal constraints:

| Constraint | Behavior when parent width changes |
| --- | --- |
| `Left` | Keep left offset, keep width |
| `Right` | Keep right offset, keep width |
| `Left + Right` | Keep both offsets, stretch width |
| `Center` | Keep center offset relative to parent center |
| `Scale` | Scale X and width proportionally |

Vertical constraints:

| Constraint | Behavior when parent height changes |
| --- | --- |
| `Top` | Keep top offset, keep height |
| `Bottom` | Keep bottom offset, keep height |
| `Top + Bottom` | Keep both offsets, stretch height |
| `Center` | Keep center offset relative to parent center |
| `Scale` | Scale Y and height proportionally |

Inspector:

- horizontal dropdown;
- vertical dropdown;
- mini preview with parent rectangle, child rectangle and active anchors;
- active anchors use accent color;
- hover over dropdown option updates mini preview temporarily;
- click changes constraints and creates undoable command.

Preview behavior:

- when user resizes parent, child updates live according to constraints;
- stretched children show updated `W/H` badge;
- center constrained children keep visual center relation;
- scale constrained children scale position and size proportionally;
- disabled state appears for Auto layout children if constraints do not apply.

## Rotation and mirroring

`daily`

Rotation:

- stored as degrees;
- `0deg` means no rotation;
- inspector field accepts typed values;
- canvas rotate affordance appears outside selected bounds;
- drag rotate affordance updates angle live;
- `Shift` snaps rotation to fixed increments, for example 15 degrees;
- multi-selection rotates around shared selection center.

Rotation scope:

- rotation applies to the whole component, not only to its filled rectangle;
- the component frame/bounds, fill, stroke, children and any content attached to
  the component rotate together around the same transform origin;
- selection outline, handles and rotate affordance must follow the rotated
  component geometry;
- hit testing must use the rotated bounds/handles, not the old unrotated frame;
- this is a critical feature: rotating only the fill while the component frame
  remains in the old position is an invalid state;
- inspector `X/Y/W/H` remain based on the documented bounds rule, but preview
  must make the rotated visual frame explicit.

Mirroring:

- `Flip horizontal` mirrors selection around vertical axis of its bounds center;
- `Flip vertical` mirrors selection around horizontal axis of its bounds center;
- multi-selection flips around shared bounding box;
- `W/H` remain positive readable values;
- text remains editable after flip.

## Component dimensions

`base`

Dimensions fields:

- `W`: selected component width;
- `H`: selected component height;
- aspect-ratio lock preserves current ratio;
- min size protects component from accidental collapse;
- invalid values are rejected without changing model.

Canvas resize:

- side handles resize one axis;
- corner handles resize both axes;
- cursor changes when pointer hovers a resize handle;
- side handles use horizontal or vertical resize cursors according to the active
  edge;
- corner handles use diagonal resize cursors according to the active corner;
- cursor orientation should respect component rotation where supported;
- `Shift` preserves aspect ratio during corner resize;
- dimension badge shows `W x H` while resizing;
- resize respects constraints only when parent is resized; direct component
  resize edits component dimensions.

## Selected preview overlay

`base/daily`

Selected component must be visible as an editable object, not just as a colored
shape.

Overlay elements:

- blue bounds outline;
- corner handles;
- side handles;
- rotation affordance when supported;
- optional radius handles for rounded rectangles;
- `W x H` size badge under selection;
- dashed horizontal center line through selected component;
- dashed vertical center line through selected component.

Center-line rules:

- horizontal center line extends to parent frame edges;
- vertical center line extends to parent frame edges;
- lines render in overlay layer;
- lines never mutate document;
- lines follow selected component while dragging or resizing;
- during active drag, center anchor lines are emphasized because they are the
  main alignment feedback for moving the component;
- labels and badges avoid handles and should not block pointer actions.

Visual priority:

- selected outline wins over hover outline;
- handles win over body drag in hit testing;
- hovering a resize handle changes cursor before pointer down, so the user can
  predict whether drag will resize or move;
- rotation affordance wins over move when pointer is outside corner;
- overlay must remain readable under zoom/pan.

## Alt measurement preview

`daily`

Holding `Alt` activates measurement preview. This mode is for inspection: it
must not change selection, geometry or inspector values.

Entry:

- user holds `Alt`;
- selected component remains selected;
- hover target is resolved under cursor;
- hover target can be sibling component, parent frame or screen frame;
- if no hover target is found, target defaults to parent frame.

Visual state:

- selected component has normal blue selection plus red measurement outline;
- hovered component/frame has red outline;
- red measurement lines connect selected component and target;
- red dashed center lines show center-to-center relation where useful;
- red distance badges show px values.

Distance badges:

- top gap;
- right gap;
- bottom gap;
- left gap;
- optional center X distance;
- optional center Y distance.

Measurement rules:

- values are measured in document px, independent of zoom;
- bounds are transformed visual bounds;
- if objects overlap on one axis, show center distance or overlap indicator
  instead of misleading negative gap;
- if selected and hovered objects have different parents, convert both bounds
  into common coordinate space before measuring;
- badges are placed close to measurement lines but avoid selection handles.

Exit:

- releasing `Alt` hides red measurement overlay immediately;
- no undo entry is created;
- selection remains unchanged;
- inspector values remain unchanged.

## Data model hints

`implementation guidance`

Minimum fields needed on a renderable component:

```yaml
position:
  x: 120
  y: 92
size:
  width: 446.91
  height: 345.94
constraints:
  horizontal: left | right | left-right | center | scale
  vertical: top | bottom | top-bottom | center | scale
transform:
  rotation: 0
  flipHorizontal: false
  flipVertical: false
```

Preview-only state should not be serialized into document:

```yaml
previewState:
  hoveredNodeId: transient
  isAltMeasurementActive: transient
  activeDrag: transient
  activeResize: transient
  activeRotate: transient
```

## Acceptance checklist

- alignment buttons support left/center/right and top/center/bottom;
- `X/Y` fields and ЛКМ drag stay synchronized;
- drag creates one undoable move operation;
- central anchor lines are visible during component movement and update live;
- constraints support all horizontal and vertical Figma-like modes;
- parent resize applies constraints live;
- rotation field and rotate handle stay synchronized;
- rotation transforms the component frame, fill, stroke, children and attached
  content together;
- rotated selection outline, handles and hit testing follow the rotated
  geometry;
- flip horizontal/vertical works without negative dimensions;
- `W/H` fields and resize handles stay synchronized;
- resize handles change cursor on hover before drag starts;
- selected preview shows blue bounds, handles, size badge and dashed center
  lines;
- `Alt` measurement mode shows red selected/hover outlines, center lines and px
  distance badges;
- releasing `Alt` clears measurement overlay without mutating document;
- all overlay math works under zoom/pan.
