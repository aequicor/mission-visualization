# Frames: экраны, контейнеры и вложенность

[← Оглавление](README.md)

Эта глава описывает UX frames: создание screen/artboard, nested frames, frame
properties, clip content, resize, child selection and frame-level handoff.

Figma reference:

- [Frames in Figma Design](https://help.figma.com/hc/en-us/articles/360041539473-Frames-in-Figma-Design)
- [Parent, child, and sibling relationships](https://help.figma.com/hc/en-us/articles/360039959014-Parent-child-and-sibling-relationships)
- [Adjust alignment, rotation, position, and dimensions](https://help.figma.com/hc/en-us/articles/360039956914-Adjust-alignment-rotation-position-and-dimensions)
- [Prototype scroll and overflow behavior](https://help.figma.com/hc/en-us/articles/360039818734-Prototype-scroll-and-overflow-behavior)

## UX-модель Figma

`base/daily`

Frame в Figma - не просто прямоугольник. Это parent object, который может:

- содержать children;
- быть top-level screen/artboard;
- быть nested container;
- иметь fill, stroke, effects and corner radius;
- clip content;
- иметь layout guides;
- использовать constraints;
- использовать auto layout;
- быть prototype target.

Для Mission Editor screen должен быть частным случаем frame: top-level frame с
device size, background, children, export settings and preview behavior.

## Create frame / screen

`base`

Пользовательские способы создания:

- кнопка `+` в Screens;
- Frame tool на toolbar;
- context menu на canvas;
- duplicate selected screen;
- create frame from selection;
- template/preset picker.

Flow для screen:

- пользователь нажимает `+`;
- выбирает preset: Desktop, Tablet, Mobile, Custom, Blank;
- вводит title;
- задает width/height or orientation;
- нажимает Create;
- новый screen появляется в list and canvas;
- screen selected, inspector показывает frame properties.

Flow для обычного frame:

- пользователь выбирает Frame tool;
- click создает default frame;
- drag на canvas создает frame произвольного размера;
- если frame создан поверх selected screen, он становится child этого screen;
- если создан на пустом canvas, становится top-level frame.

## Frame selection

`base`

Frame selection должен быть различим от child selection.

Canvas:

- selected frame показывает bounds and handles;
- label frame name виден около рамки;
- child hover внутри frame не должен случайно выбирать parent, если пользователь
  явно попал в child;
- click по пустой области внутри frame выбирает frame;
- double click or Enter может перейти к child selection.

Keyboard navigation:

- Enter выбирает child layer;
- Tab переходит к next sibling;
- Shift + Tab к previous sibling;
- Shift + Enter выбирает parent.

Layers:

- frame можно раскрыть;
- children nested inside;
- selected child подсвечивает parent path;
- top-level frames отделяются от nested frames.

## Frame resize

`base/daily`

Frame resize должен работать и через canvas, и через inspector.

Canvas handles:

- hover по side handle показывает horizontal/vertical resize cursor;
- hover по corner показывает diagonal resize cursor;
- drag side меняет одну ось;
- drag corner меняет обе оси;
- Shift temporarily locks aspect ratio when unlocked;
- modifier key can temporarily ignore constraints, если это поддержано;
- dimension badge показывает текущий size.

Inspector:

- X/Y for position;
- W/H for dimensions;
- lock aspect ratio;
- rotation;
- clip content;
- frame preset selector;
- min/max if layout mode supports it.

Constraints impact:

- при resize parent children реагируют по constraints;
- при auto layout children reflow;
- при free mode children сохраняют absolute positions;
- если frame clips content, overflow hidden;
- если clip disabled, overflow виден за bounds.

## Nested frames

`daily`

Nested frames позволяют строить сложные интерфейсы.

UX rules:

- drag frame into another frame делает nested parent/child relationship;
- insertion/containment highlight показывает будущего parent;
- если frame частично пересекает parent, система должна ясно решить:
  вложить или оставить на canvas;
- command `Move into frame` должен быть доступен из context menu/layers;
- command `Remove from frame` выносит child наружу, сохраняя visual position.

Inspector:

- показывает parent frame;
- можно jump to parent;
- constraints are relative to parent;
- export settings доступны для top-level and selected frame.

## Clip content and overflow

`daily`

Clip content должен быть понятным визуально.

Behavior:

- toggle находится в frame inspector;
- enabled: objects outside bounds скрыты в preview/export;
- disabled: overflow виден на canvas;
- при selection hidden overflow object может быть outline-visible, чтобы
  пользователь понял, что объект существует;
- export respects clip content.

Scroll/overflow:

- если screen высотой больше viewport, preview может scroll;
- frame может быть marked scrollable;
- scroll direction: vertical, horizontal, both;
- fixed children внутри scrolling frame должны иметь отдельный UX state.

## Frame appearance

`daily`

Frame имеет те же visual properties, что и многие layers:

- fill;
- stroke;
- corner radius;
- effects;
- opacity/blend;
- background/image/gradient fill;
- layout guides.

UX detail:

- если selected frame has children, changing frame fill не должен менять child
  fills;
- для mixed selection можно показывать Selection colors;
- frame background differs from canvas background.

## Frame checklist

- screen создается как top-level frame;
- frame можно создать click/drag/tool/menu;
- selected frame имеет clear bounds and handles;
- frame resize отражается в inspector W/H;
- children реагируют по layout/constraints;
- nested frames показывают parent-child relationship;
- clip content влияет на preview and export;
- keyboard navigation parent/child работает;
- frame properties не смешиваются с child properties.
