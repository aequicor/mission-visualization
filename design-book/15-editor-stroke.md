# Stroke: borders, outlines, caps and dashed styles

[← Оглавление](README.md)

Эта глава описывает UX для strokes: add stroke, stroke fill, weight, position,
individual sides, endpoint caps, joins, dashed/dotted/custom strokes and
stroke scaling.

Figma reference:

- [Apply and adjust stroke properties](https://help.figma.com/hc/en-us/articles/360049283914-Apply-and-adjust-stroke-properties)
- [Guide to fills](https://help.figma.com/hc/en-us/articles/360041003694-Guide-to-fills)
- [Use gradients as a fill or stroke](https://help.figma.com/hc/en-us/articles/34208860210199-Use-gradients-as-a-fill-or-stroke)
- [Export formats and settings for static designs](https://help.figma.com/hc/en-us/articles/13402894554519-Export-formats-and-settings-for-static-designs)

## UX-модель Figma

`daily`

Stroke is a visual representation of a layer/vector path outline. In Figma,
strokes can be applied to shapes, frames, vector networks, boolean operations,
lines, arrows, images and text. Stroke has its own fill, position, weight,
style and endpoint behavior.

Mission Editor should treat stroke as a first-class appearance property, not as
a simple border string.

## Add stroke

`base`

Flow:

- select object;
- click plus in Stroke section;
- default stroke appears;
- canvas updates immediately;
- stroke row becomes editable.

Stroke row:

- visibility toggle;
- swatch;
- opacity;
- weight;
- position;
- advanced settings;
- remove button.

UX:

- if layer type does not support stroke, section hidden or disabled with reason;
- adding stroke is undoable;
- hidden stroke still remains in document;
- multiple stroke fills are possible if supported.

## Stroke fill

`daily`

Stroke fill can be solid, gradient, image/pattern where supported. The stroke's
fill controls color/media, while stroke properties control geometry.

Controls:

- swatch opens color picker;
- opacity field;
- style/variable picker;
- add stroke fill;
- hide/show stroke fill;
- remove stroke fill.

Important UX:

- multiple fills share same stroke weight/position/style;
- fill order matters visually;
- selection colors should include visible stroke fills;
- fill-specific blend mode belongs to color picker, not top-level stroke row.

## Stroke position

`daily`

Position options:

- inside;
- center;
- outside.

UX:

- dropdown previews each option on hover;
- selected option applies on click;
- unsupported layer types disable unsupported positions;
- lines default to center;
- shapes often default to inside;
- export to SVG may simplify inside/outside stroke, so export warnings should
  exist where relevant.

## Weight and dimensions

`daily`

Stroke weight is edited in pixels.

Behavior:

- weight field supports numeric input;
- scrub changes value interactively;
- arrow keys adjust field when focused;
- stroke weight usually not included in object dimensions;
- auto layout can optionally include stroke in layout size if supported.

Resize vs scale:

- resize object keeps stroke weight;
- scale tool scales stroke weight with object;
- UI must make this distinction visible.

## Individual strokes

`daily`

For rectangles, frames, components and instances, individual side strokes are
useful for UI borders.

Controls:

- all;
- top;
- bottom;
- left;
- right;
- custom.

UX:

- custom mode shows four fields;
- setting side weight to 0 removes that side;
- canvas preview highlights selected side;
- side controls are hidden for shapes where side concept does not apply.

Use cases:

- table row dividers;
- card left status bar;
- section underline;
- border on only three sides;
- separators.

## Caps and endpoints

`daily/advanced`

Open paths, lines and arrows need endpoint controls.

Options:

- none;
- round;
- square;
- line arrow;
- triangle arrow;
- reverse triangle;
- diamond.

UX:

- simple two-point line exposes start/end controls directly;
- complex vector network may require vector edit mode;
- hover previews cap/tip;
- independent endpoint editing should be possible in vector edit mode;
- arrows use stroke settings rather than separate shape logic.

## Stroke style

`daily/advanced`

Advanced stroke styles:

- basic;
- dashed;
- dotted;
- custom dash pattern;
- brush;
- dynamic stroke;
- width profiles.

Dashed UX:

- advanced settings menu;
- choose dashed;
- fields for dash and gap;
- cap setting for dash;
- canvas preview updates live.

Dotted UX:

- center stroke position;
- dash length 1;
- round cap;
- gap controls density.

Custom:

- pattern field accepts dash/gap sequence;
- invalid sequence shows validation;
- preview shows resulting pattern.

## Joins and miter

`advanced`

Join options:

- miter;
- bevel;
- round.

UX:

- join controls visible for paths where joins matter;
- miter angle field appears with miter;
- point-level join editing requires vector edit mode;
- hover previews join result.

## Stroke checklist

- Stroke can be added from selected object;
- stroke row has visibility, swatch, opacity, weight and remove;
- position supports inside/center/outside with preview;
- weight is separate from object dimensions;
- individual sides support common UI border cases;
- endpoints work for lines/open paths;
- dashed/dotted/custom styles are editable;
- resize vs scale stroke behavior is explicit;
- export caveats for SVG strokes are documented.
