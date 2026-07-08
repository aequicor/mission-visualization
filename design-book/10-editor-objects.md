# Objects: выбор, перемещение и слои

[← Оглавление](README.md)

Эта глава описывает UX для объектов: selection, hover, move, resize,
multi-selection, layers, lock/visibility, context actions and object creation.

Figma reference:

- [Select layers and objects](https://help.figma.com/hc/en-us/articles/360040449873-Select-layers-and-objects)
- [Layers 101: Get started with layers](https://help.figma.com/hc/en-us/articles/26584819173271-Layers-101-Get-started-with-layers)
- [Layers 101: Explore layer types](https://help.figma.com/hc/en-us/articles/26620239826199-Layers-101-Explore-layer-types)
- [Shape tools](https://help.figma.com/hc/en-us/articles/360040450133-Shape-tools)

## UX-модель Figma

`base`

Figma treats everything on canvas as layers/objects: frames, groups, shapes,
text, components, instances, vectors and media. Пользователь может выбрать
объект на canvas или через Layers panel, а right sidebar показывает properties
выбранного layer.

Mission Editor должен сохранить эту связку:

- canvas показывает объект визуально;
- layers показывает объект структурно;
- inspector показывает editable properties;
- source показывает serialized representation.

## Hover and hit testing

`base`

Hover должен помогать пользователю выбрать правильный объект.

Behavior:

- объект под курсором получает subtle outline;
- рядом может появляться layer label;
- hover не меняет selection;
- locked object показывает locked affordance;
- hidden object не участвует в canvas hit testing;
- nested object можно deep-select через double click or modifier click;
- если objects overlap, topmost selectable layer получает priority.

Hit target:

- visible fill/stroke should be clickable;
- thin lines need enlarged hit target;
- empty frame area clickable for frame selection;
- resize handles have priority over move drag;
- text editing area has priority after double click.

## Single selection

`base`

Click selection:

- click object selects it;
- click empty canvas clears selection;
- click frame empty area selects frame;
- double click enters nested object or text edit mode;
- Enter moves down into children when object supports hierarchy;
- Shift + Enter selects parent.

Selection visual:

- blue bounds;
- handles if resizable;
- rotation affordance outside bounds;
- label/name near bounds;
- properties in inspector update immediately;
- layers panel scrolls to selected layer.

## Multi-selection

`daily`

Figma multi-selection is useful for bulk property changes, move, resize,
group/frame/component creation. Mission Editor should support the same idea.

Ways to select:

- Shift click objects;
- drag marquee on empty canvas;
- select layers in Layers panel;
- Select all on current screen;
- select matching objects, later advanced.

Multi-selection visual:

- shared bounding box;
- individual object outlines can be visible on hover;
- inspector shows common properties;
- fields with different values show `Mixed`;
- Selection colors can summarize shared fill/stroke colors.

Actions:

- move together;
- resize together;
- group;
- frame selection;
- align/distribute;
- update shared fill/stroke/text where applicable;
- delete/duplicate/copy.

## Move objects

`daily`

Flow:

- select object;
- mouse down inside bounds, away from handles;
- cursor enters move/grab state;
- drag moves object;
- guides and distance labels appear;
- release commits position.

Rules:

- object movement is relative to parent frame;
- Shift constrains movement to horizontal/vertical axis;
- arrow keys nudge by small step;
- Shift + arrow nudges by big step;
- object cannot move if locked;
- hidden object cannot be moved from canvas;
- if parent clips content, object may disappear when dragged outside bounds.

Smart feedback:

- x/y label;
- distance to nearby objects;
- alignment guide;
- parent boundary guide;
- insertion indicator if object is inside auto layout.

## Resize objects

`daily`

Canvas:

- side handles resize one axis;
- corner handles resize both axes;
- cursor changes before drag;
- dimension badge follows drag;
- min size prevents collapse;
- aspect ratio can be locked or temporarily constrained;
- content reflows for text/layout containers.

Inspector:

- W/H fields;
- lock ratio;
- fixed/hug/fill modes if object is in layout;
- min/max if supported.

Object-specific:

- text box resize changes line wrapping;
- shape resize preserves shape type;
- component instance resize respects component constraints;
- vector resize changes bounds, while vector edit changes points.

## Layers manipulation

`daily`

Layers panel is the structural control surface.

Actions:

- select;
- rename;
- reorder;
- nest into frame/group;
- hide/show;
- lock/unlock;
- duplicate;
- delete;
- search/filter;
- expand/collapse children.

Drag behavior:

- dragged row floats;
- insertion line shows target position;
- possible parent container highlights;
- invalid drop shows unavailable cursor;
- release applies new z/order/parent.

Visibility:

- eye icon toggles visibility;
- hidden layer remains in document;
- hidden layer is not selected from canvas by normal click;
- hidden selected layer can be shown from layers.

Lock:

- locked layer visible but not editable from canvas;
- selecting locked layer from layers is allowed;
- inspector shows locked state;
- unlock available from layers/context/inspector.

## Object creation

`daily`

Creation methods:

- toolbar tools;
- insert menu;
- resources/components panel;
- paste from clipboard;
- duplicate selected;
- context menu;
- command palette.

Tool behavior:

- click creates object with default size;
- drag creates object with custom size;
- Shift constrains proportions for shapes;
- newly created object is selected;
- inspector opens relevant properties;
- layers panel highlights new layer.

Basic object types:

- frame;
- group;
- rectangle;
- ellipse;
- line;
- arrow;
- text;
- vector;
- image/media;
- component/instance;
- comment marker.

## Object checklist

- hover makes target understandable;
- click selects without changing document;
- selection syncs canvas/layers/inspector;
- multi-selection supports shared actions;
- move/resize have clear cursors and labels;
- lock/visibility rules are predictable;
- layers drag shows insertion/parent target;
- object creation selects the new object immediately.
