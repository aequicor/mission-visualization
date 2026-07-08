# Fill: colors, gradients, images and patterns

[← Оглавление](README.md)

Эта глава описывает UX для fills: layer fill, stroke fill, color picker,
multiple fills, opacity, order, gradients, image/video/pattern fills and
variables.

Figma reference:

- [Guide to fills](https://help.figma.com/hc/en-us/articles/360041003694-Guide-to-fills)
- [Update fills using the color picker](https://help.figma.com/hc/en-us/articles/360041003774-Update-fills-using-the-color-picker)
- [Use gradients as a fill or stroke](https://help.figma.com/hc/en-us/articles/34208860210199-Use-gradients-as-a-fill-or-stroke)
- [Use patterns as a fill or stroke](https://help.figma.com/hc/en-us/articles/31616030150167-Use-patterns-as-a-fill-or-stroke)
- [Adjust the properties of an image](https://help.figma.com/hc/en-us/articles/360041098433-Adjust-the-properties-of-an-image)

## UX-модель Figma

`daily`

Figma fill can be applied to frames, shapes, text and other layers. Fill is
edited from the Fill section of the right sidebar through a swatch/color picker.
Objects can have multiple fills, and each fill can have its own type, opacity
and visibility.

Fill types:

- solid;
- gradient;
- pattern;
- image;
- video/animated media where supported.

Mission Editor should support at least solid, gradient and image fills in MVP.

## Fill section anatomy

`base`

Each fill row should include:

- visibility toggle;
- color/media swatch;
- fill type indicator;
- value/name;
- opacity;
- drag handle for reorder;
- minus/delete.

Section controls:

- plus to add fill;
- style/variable picker;
- mixed selection colors;
- expand advanced options.

UX:

- clicking swatch opens color picker;
- clicking opacity edits percentage;
- dragging handle reorders fills;
- hiding fill keeps fill in document;
- deleting fill removes it;
- multiple fills render in defined order.

## Solid color

`base`

Color picker behavior:

- swatch click opens picker;
- user can enter HEX/RGB/HSL/HSB depending on supported model;
- opacity can be changed separately;
- recent colors and document styles are accessible;
- eyedropper can pick from canvas, later advanced;
- Enter applies typed value;
- Escape closes without applying.

Canvas behavior:

- color updates live while user drags picker;
- if preview is temporary, cancel restores previous color;
- selected object remains selected.

## Gradient fills

`daily`

Gradient types:

- linear;
- radial;
- angular;
- diamond.

Inspector/color picker:

- gradient type dropdown;
- list of stops;
- stop color swatches;
- stop position values;
- opacity per stop;
- reverse gradient;
- add/remove stop.

Canvas handles:

- gradient start/end points visible on selected object;
- drag start/end changes direction and scale;
- drag middle handle can move gradient;
- stop handles can be edited from gradient bar;
- holding modifier can constrain angle;
- tooltip/label can show angle or stop position.

UX:

- hover gradient type previews result;
- selecting a stop shows its color in picker;
- variables can be applied to gradient stops;
- deleting last required stop is prevented;
- gradient handles visible only when fill edit mode is active or selected.

## Image fill

`daily`

Figma treats images as fills. Mission Editor should use the same mental model:
the object owns size/bounds, image fill controls how media fits inside.

Modes:

- fill;
- fit;
- crop;
- tile;
- custom position/scale.

Behavior:

- select object with image fill;
- swatch opens image properties;
- crop mode shows crop handles;
- drag handles crop visible area;
- object resize changes image rendering according to mode;
- replacing image preserves object bounds.

UX:

- transparent images show checkerboard if needed;
- missing image shows broken/missing asset state;
- image source visible in resources;
- image fill can be hidden/removed like other fills.

## Pattern fill

`advanced`

Pattern fill references another object on canvas.

Flow:

- choose Pattern in fill picker;
- click/select source object;
- configure tile type;
- configure scale;
- configure spacing;
- configure alignment;
- configure opacity.

UX:

- selected source should be visible/highlighted;
- if source object is deleted, pattern fill shows missing source;
- source changes update pattern preview;
- pattern fill should be marked as advanced if MVP does not implement it.

## Fill and text

`daily`

Text fill changes the text color, not a background behind text.

UX:

- if user wants text background, suggest wrapping text in frame or adding frame
  fill;
- text fill still supports solid/gradient where supported;
- text layer can have stroke/effects separately;
- mixed text selection should show shared text color where possible.

## Fill checklist

- Fill section has add, visibility, swatch, opacity, reorder and remove;
- solid color can be edited precisely;
- gradient supports type, stops and canvas handles;
- image fill has fit/crop behavior;
- hidden fill remains in document;
- multiple fills render predictably;
- style/variable references are visible;
- text fill is clearly different from text background.
