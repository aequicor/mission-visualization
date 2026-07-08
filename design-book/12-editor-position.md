# Position: coordinates, dimensions, rotation and order

[← Оглавление](README.md)

Эта глава описывает UX для position and geometry: X/Y, W/H, nudge, rotation,
flip, z-order, constraints, bounding boxes and precise numeric editing.

Figma reference:

- [Adjust alignment, rotation, position, and dimensions](https://help.figma.com/hc/en-us/articles/360039956914-Adjust-alignment-rotation-position-and-dimensions)
- [Scale layers while maintaining proportions](https://help.figma.com/hc/en-us/articles/360040451453-Scale-layers-while-maintaining-proportions)
- [Apply constraints to define how layers resize](https://help.figma.com/hc/en-us/articles/360039957734-Apply-constraints-to-define-how-layers-resize)
- [Create layout guides](https://help.figma.com/hc/en-us/articles/360040450513-Create-layout-guides)

## UX-модель Figma

`base/daily`

Position в Figma управляется двумя путями:

- direct manipulation на canvas;
- numeric fields in right sidebar.

Пользователь может двигать, resize-ить, rotate-ить and flip-ить layers, а для
точных значений использовать X/Y/W/H and rotation fields. Z axis не имеет
отдельного numeric field: depth управляется order in Layers panel.

## X and Y coordinates

`base`

Inspector:

- X field;
- Y field;
- values relative to parent frame;
- supports direct typing;
- supports mathematical expressions;
- Enter applies;
- Escape cancels field edit.

Canvas:

- dragging selected object changes X/Y;
- position label can show current X/Y during drag;
- guides show alignment to parent and sibling objects;
- if object is rotated, coordinates should still be based on stable bounds rule.

UX detail:

- if parent changes, X/Y recalculated relative to new parent;
- if multiple objects selected, inspector can show mixed or common origin;
- if layer is locked, fields disabled with lock reason.

## Nudge

`daily`

Nudge is keyboard-based precise movement.

Behavior:

- arrow keys move selection by small nudge;
- Shift + arrow moves by big nudge;
- default small/big values can be 1 and 10;
- nudge respects current selection and parent;
- locked objects do not move;
- auto layout children may reorder or move according to layout rules instead of
  free coordinates.

Mission Editor should expose:

- small nudge setting;
- big nudge setting;
- optional per-document grid/snap step;
- command palette actions: `Nudge left`, `Nudge right`, etc.

## Dimensions W and H

`base`

Canvas:

- selected object shows bounding box;
- dimension badge appears during resize;
- side handles change one axis;
- corner handles change both axes;
- cursor changes before drag;
- min size prevents accidental collapse.

Inspector:

- W field;
- H field;
- lock aspect ratio;
- fixed/hug/fill mode near dimensions;
- min/max if layout supports it.

Lock aspect ratio:

- lock icon connects W and H;
- editing W updates H;
- editing H updates W;
- Shift can temporarily constrain ratio on canvas;
- if ratio locked and min/max set, paired dimension should adjust consistently.

## Scale vs resize

`daily`

Figma distinguishes resize from scale:

- resize changes object bounds;
- scale proportionally resizes object and its nested properties, including
  strokes/effects where applicable.

Mission Editor UX:

- normal handles perform resize;
- Scale tool performs proportional scale;
- scale shows percentage;
- nested text/stroke/effects scaling should be explicit;
- user should not accidentally scale when they meant resize.

## Rotation

`daily`

Canvas:

- hover just outside a corner/bounds shows rotate cursor;
- drag clockwise/counterclockwise changes angle;
- Shift snaps rotation to increments;
- rotation label shows degree;
- center of selection is default origin.

Inspector:

- rotation field;
- degree unit;
- numeric typing;
- reset to 0 action;
- optional origin control in advanced mode.

UX detail:

- rotated objects still show understandable bounds;
- effects may not rotate the same way as object geometry, so export/handoff
  should be checked;
- multi-selection rotates around shared center.

## Flip

`daily`

Flip actions:

- flip horizontal;
- flip vertical.

Access:

- right-click/context menu;
- keyboard shortcut;
- inspector transform menu.

Behavior:

- flips selected layer around its center;
- multi-selection flips around shared selection bounds;
- text should remain editable but visual result must be predictable;
- vector points flip with the object.

## Z-order and layer depth

`base`

Figma depth is controlled by layer order.

Mission Editor:

- top layer in Layers panel appears in front;
- bottom layer appears behind;
- bring forward/backward commands change order;
- drag in layers changes z-order;
- objects inside different parents cannot reorder across hierarchy without
  explicit reparenting.

Commands:

- bring forward;
- send backward;
- bring to front;
- send to back;
- move into frame;
- remove from frame.

## Position checklist

- X/Y fields update selected object;
- numeric fields support undo and cancel;
- arrow nudge works predictably;
- W/H are visible in inspector and during resize;
- aspect ratio lock is clear and temporary override exists;
- resize and scale are separate modes;
- rotate cursor appears before drag;
- z-order is represented through Layers panel;
- position values are relative to parent frame.
