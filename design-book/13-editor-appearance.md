# Appearance: opacity, effects, blend, radius and styles

[← Оглавление](README.md)

Эта глава описывает UX для общих visual properties: opacity, blend modes,
effects, shadows, blur, corner radius, smoothing, styles and mixed selection.

Figma reference:

- [Design, prototype, and explore layer properties in the right sidebar](https://help.figma.com/hc/en-us/articles/360039832014-Design-prototype-and-explore-layer-properties-in-the-right-sidebar)
- [Apply effects to layers](https://help.figma.com/hc/en-us/articles/360041488473-Apply-effects-to-layers)
- [Apply blend modes to layers, fills, and effects](https://help.figma.com/hc/en-us/articles/360040667874-Apply-blend-modes-to-layers-fills-and-effects)
- [Adjust corner radius and smoothing](https://help.figma.com/hc/en-us/articles/360050986854-Adjust-corner-radius-and-smoothing)
- [Create color, text, effect, and layout guide styles](https://help.figma.com/hc/en-us/articles/360038746534-Create-color-text-effect-and-layout-guide-styles)

## UX-модель Figma

`daily`

Appearance properties live mostly in the right sidebar and depend on selected
layer type. Пользователь выбирает object on canvas/layers, затем видит только
relevant properties: layout, colors, typography, effects, export.

Mission Editor должен повторить этот принцип:

- no selection: canvas/page settings;
- screen selected: screen/frame appearance;
- object selected: object appearance;
- text selected: typography plus text fill/stroke/effects;
- multi-selection: shared properties and mixed states.

## Opacity

`base/daily`

Opacity should be fast to understand and edit.

Controls:

- percentage field;
- slider;
- scrub on label/field;
- show/hide icon for property where relevant.

Behavior:

- opacity affects selected layer as a whole;
- fill opacity is separate from layer opacity;
- stroke opacity is separate from stroke visibility;
- effects may have their own opacity/color;
- mixed selection shows `Mixed`.

UX:

- slider drag updates canvas live;
- typing exact value applies on Enter;
- invalid values are rejected or clamped with clear feedback;
- 0% object may still be selected through layers, but should be hard to select
  from canvas unless bounds are visible by selection.

## Blend modes

`daily/advanced`

Figma allows blend mode on:

- whole layer;
- individual fill/stroke fill;
- certain effects.

Mission Editor:

- `Appearance` section controls layer blend mode;
- Fill/Stroke popover controls fill-specific blend mode;
- Effect settings control effect-specific blend mode;
- hover over blend options can preview on canvas.

UX rule:

- blend mode dropdown should show current mode;
- preview should be temporary until click;
- Escape closes dropdown without applying;
- if a style/variable prevents direct blend edit, UI explains why.

## Effects

`daily/advanced`

Effect types:

- drop shadow;
- inner shadow;
- layer blur;
- background blur;
- noise/texture/shader later if supported.

Flow:

- select layer;
- click plus in Effects;
- default effect appears;
- dropdown switches effect type;
- settings icon opens detail panel;
- user edits X/Y/blur/spread/color/opacity;
- canvas updates live.

Shadow settings:

- X;
- Y;
- blur;
- spread;
- color/fill;
- opacity;
- blend mode.

Blur settings:

- blur type;
- amount;
- progressive direction if supported;
- performance warning for heavy blur when needed.

UX:

- each effect row has visibility toggle;
- effect can be duplicated/copied;
- effect can be reordered if stacking is supported;
- delete/minus removes effect;
- effects should be available only for supported layer types.

## Corner radius and smoothing

`daily`

Controls:

- single radius field;
- independent corner fields;
- lock/unlock corners;
- corner smoothing if supported;
- canvas corner handles for rectangles/frames.

Canvas behavior:

- rectangle selected shows corner handles inside corners;
- drag corner handle changes radius;
- if corners linked, all corners change;
- if a single corner selected in vector edit mode, only that point changes;
- radius label can appear during drag.

Inspector:

- radius field supports numeric typing;
- scrub control changes value;
- invalid negative values are rejected;
- maximum radius clamps to shape geometry.

## Styles and variables

`advanced`

Appearance properties often come from styles/variables.

UX:

- style icon near property;
- click opens style/variable picker;
- applied style shows name;
- detach action is explicit;
- local override is visible;
- hover style can show description;
- style creation from selected object is possible.

Properties that can become styles:

- color/fill style;
- text style;
- effect style;
- layout guide style.

Mission Editor should separate:

- raw property value;
- token/style reference;
- overridden local value.

## Mixed selection appearance

`daily`

When many objects are selected:

- common properties show actual value;
- different properties show `Mixed`;
- selection colors summarize colors across selected layers;
- applying a new fill/stroke updates all relevant objects;
- unsupported objects are skipped with visible explanation.

UX:

- user should not need to inspect every layer manually;
- bulk updates must be undoable;
- hidden fills/effects should not unexpectedly participate unless shown.

## Appearance checklist

- inspector changes by selected layer type;
- opacity is separate for layer/fill/stroke/effect;
- blend modes can preview before applying;
- effects have add/settings/visibility/delete controls;
- radius works in inspector and canvas;
- styles/variables are visible as references, not hidden values;
- mixed selection states are explicit;
- unsupported appearance controls are disabled with reason.
