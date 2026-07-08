# Vector: shape, path and point editing

[← Оглавление](README.md)

Эта глава описывает UX для vector work: pen tool, vector edit mode, points,
paths, bezier handles, cut, lasso, paint regions, caps and variable width.

Figma reference:

- [Vector networks](https://help.figma.com/hc/en-us/articles/360040450213-Vector-networks)
- [Edit vector layers](https://help.figma.com/hc/en-us/articles/360039957634-Edit-vector-layers)
- [Shape tools](https://help.figma.com/hc/en-us/articles/360040450133-Shape-tools)
- [Boolean operations](https://help.figma.com/hc/en-us/articles/360039957534-Boolean-operations)

## UX-модель Figma

`advanced`

Figma vector networks differ from simple closed paths: users can create points
and paths that branch, edit points directly, bend straight segments, cut paths,
lasso points, add fills to closed regions and customize stroke endpoints.

Mission Editor does not need full illustrator-level vector editing in MVP, but
it needs a clear UX boundary:

- shape object editing;
- vector object editing;
- icon/SVG asset import;
- simple path manipulation;
- advanced vector mode later.

## Shape tools

`daily`

Base shapes are the entry point before full vector editing.

Tools:

- Rectangle;
- Ellipse;
- Line;
- Arrow;
- Polygon;
- Star;
- Pen.

Creation behavior:

- select tool from toolbar;
- click-drag on canvas;
- dimension label appears while dragging;
- Shift constrains proportions/angle where applicable;
- release creates object;
- object is selected;
- inspector shows shape-specific properties.

Shape-specific handles:

- rectangle has corner radius handles;
- ellipse can expose arc/ring handles later;
- star has count/ratio/radius handles;
- line/arrow expose endpoints;
- polygon can enter edit mode for points.

## Vector edit mode

`advanced`

Entering vector edit mode:

- double click vector layer;
- press Enter when vector is selected;
- choose `Edit vector` from context menu.

Visible state:

- points become visible;
- paths become editable;
- selected points use stronger highlight;
- secondary toolbar switches to vector tools;
- inspector shows point/path/stroke properties;
- normal object handles are replaced by point/path handles.

Exiting:

- Escape exits edit mode;
- click outside can exit if no active operation;
- selection returns to vector layer;
- undo steps preserve point-level edits.

## Pen tool

`advanced`

Behavior:

- select Pen or press shortcut;
- first click creates first point;
- subsequent clicks create segments;
- click-drag creates curved segment with bezier handles;
- hover existing start/end point shows close-path cursor;
- click closes path;
- Escape leaves path open and exits current drawing.

UX feedback:

- cursor indicates add point, close path, continue path;
- preview segment follows cursor;
- point labels are not needed, but selected point must be clear;
- snapping to existing points/guides can be toggled or temporarily ignored.

## Point and path editing

`advanced`

Point operations:

- select point;
- drag point;
- delete point;
- add point on path;
- select multiple points;
- move selected points together;
- transform multiple points with bounding box.

Path operations:

- select path segment;
- drag segment;
- bend segment;
- cut segment;
- split path;
- join points;
- close path.

Lasso:

- lasso tool draws freeform selection region;
- points inside region become selected;
- selected points can be moved/deleted/transformed;
- lasso should not accidentally move the whole object.

## Bezier and bend tool

`advanced`

Figma uses Bend tool and bezier handles for curves.

Mission Editor behavior:

- select Bend tool in vector mode;
- hover path or point;
- click adds bezier handles;
- drag handle changes curve;
- handle mirroring setting controls relation between handles:
  `No mirror`, `Mirror angle`, `Mirror angle and length`;
- Shift can select both handles or constrain handle movement.

UX:

- handles should be thin but easy to grab;
- active handle highlighted;
- curve preview updates live;
- invalid handle states should not create broken paths.

## Cut and boolean operations

`advanced`

Cut tool:

- available only in vector edit mode;
- click point/path to split;
- drag through paths to divide shape;
- separated portion can become its own layer;
- cut action is undoable.

Boolean operations:

- union;
- subtract;
- intersect;
- exclude.

UX:

- boolean group remains editable;
- child layers remain selectable inside group;
- fill/stroke behavior should be explained because boolean result can differ
  from individual child appearances;
- `Flatten` is a destructive conversion and needs clear affordance.

## Paint closed regions

`advanced`

Figma can fill closed regions inside vector networks.

Mission Editor behavior:

- Paint tool appears in vector mode;
- hover closed region shows striped preview;
- cursor indicates add/remove fill;
- click toggles fill for region;
- inspector shows fill for selected region;
- multiple closed regions can have independent fills.

## Vector stroke endpoints

`daily/advanced`

Open vector paths need endpoint controls.

Controls:

- none;
- round;
- square;
- line arrow;
- triangle arrow;
- reverse triangle;
- diamond;
- independent start/end endpoint where supported.

UX:

- for simple two-point path, endpoints can be shown directly in Stroke section;
- for complex vector network, endpoint editing may require vector edit mode;
- hover endpoint control previews result on canvas.

## Vector checklist

- shape creation has drag dimensions and handles;
- vector edit mode is visually distinct from object mode;
- Pen tool supports add point, curve, close path, Escape;
- points/paths can be selected and moved;
- Bend tool exposes bezier handles;
- Cut and lasso are separate tools, not hidden gestures;
- closed region paint has hover preview;
- endpoint/cap controls are discoverable;
- destructive operations are clearly marked.
