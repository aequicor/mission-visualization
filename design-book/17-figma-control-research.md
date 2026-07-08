# Ресерч: управление в Figma

[← Оглавление](README.md)

Дата сверки: 2026-07-08.

Этот документ собирает research по теме "управление в Figma": как пользователь
управляет интерфейсом, canvas, объектами, слоями, свойствами, цветом, текстом,
векторами, прототипами и вспомогательными режимами. Под "управлением" здесь
понимаются UX controls and interaction contracts внутри Figma Design, а не
администрирование организаций или billing.

Основные источники - официальные Figma Help/Learn страницы. Дополнительные
публичные источники по shortcuts рассматриваются как справочные, но не как
источник продуктовой истины.

## Карта источников

`research map`

Главные официальные источники:

- [Explore the navigation bar and left sidebar](https://help.figma.com/hc/en-us/articles/360039831974-Explore-the-navigation-bar-and-left-sidebar)
- [Explore design files](https://help.figma.com/hc/en-us/articles/15297425105303-Explore-design-files)
- [Design, prototype, and explore layer properties in the right sidebar](https://help.figma.com/hc/en-us/articles/360039832014-Design-prototype-and-explore-layer-properties-in-the-right-sidebar)
- [Hide or minimize the UI](https://help.figma.com/hc/en-us/articles/41414918021271-Hide-or-minimize-the-UI)
- [Adjust your zoom and view options](https://help.figma.com/hc/en-us/articles/360041065034-Adjust-your-zoom-and-view-options)
- [Use Figma products with a keyboard](https://help.figma.com/hc/en-us/articles/360040328653-Use-Figma-products-with-a-keyboard)
- [Explore FigJam files](https://help.figma.com/hc/en-us/articles/15300412458647-Explore-FigJam-files)
- [Pan and zoom in FigJam](https://help.figma.com/hc/en-us/articles/1500004414582-Pan-and-zoom-in-FigJam)
- [Select layers and objects](https://help.figma.com/hc/en-us/articles/360040449873-Select-layers-and-objects)
- [Adjust alignment, rotation, position, and dimensions](https://help.figma.com/hc/en-us/articles/360039956914-Adjust-alignment-rotation-position-and-dimensions)
- [Guide to auto layout](https://help.figma.com/hc/en-us/articles/360040451373-Guide-to-auto-layout)
- [Frames in Figma Design](https://help.figma.com/hc/en-us/articles/360041539473-Frames-in-Figma-Design)
- [Guide to fills](https://help.figma.com/hc/en-us/articles/360041003694-Guide-to-fills)
- [Update fills using the color picker](https://help.figma.com/hc/en-us/articles/360041003774-Update-fills-using-the-color-picker)
- [Apply and adjust stroke properties](https://help.figma.com/hc/en-us/articles/360049283914-Apply-and-adjust-stroke-properties)
- [Guide to text in Figma Design](https://help.figma.com/hc/en-us/articles/360039956434-Guide-to-text-in-Figma-Design)
- [Explore text properties](https://help.figma.com/hc/en-us/articles/360039956634-Explore-text-properties)
- [Edit vector layers](https://help.figma.com/hc/en-us/articles/360039957634-Edit-vector-layers)
- [Vector networks](https://help.figma.com/hc/en-us/articles/360040450213-Vector-networks)

Дополнительные источники:

- [Lock and unlock layers](https://help.figma.com/hc/en-us/articles/360041596573-Lock-and-unlock-layers)
- [Toggle visibility to hide layers](https://help.figma.com/hc/en-us/articles/360041112614-Toggle-visibility-to-hide-layers)
- [Arrange layers with Smart selection](https://help.figma.com/hc/en-us/articles/360040450233-Arrange-layers-with-Smart-selection)
- [Copy and paste objects](https://help.figma.com/hc/en-us/articles/4409078832791-Copy-and-paste-objects)
- [Adjust text dimensions and resizing](https://help.figma.com/hc/en-us/articles/27378154668951-Adjust-text-dimensions-and-resizing)
- [Scale layers while maintaining proportions](https://help.figma.com/hc/en-us/articles/360040451453-Scale-layers-while-maintaining-proportions)
- [Prototype scroll and overflow behavior](https://help.figma.com/hc/en-us/articles/360039818734-Prototype-scroll-and-overflow-behavior)
- [Add images and videos to designs](https://help.figma.com/hc/en-us/articles/360040028034-Add-images-and-videos-to-designs)
- [Crop an image](https://help.figma.com/hc/en-us/articles/360040675194-Crop-an-image)

Community/behavior reports used only as supporting evidence for desktop mouse
expectations:

- [Figma Forum: Pan and zoom gesture on middle mouse click](https://forum.figma.com/suggest-a-feature-11/pan-and-zoom-gesture-on-middle-mouse-click-34952)
- [Figma Forum: Move around with middle click?](https://forum.figma.com/ask-the-community-7/move-around-with-middle-click-windows-33263)
- [Figma Forum: Right click drag to pan](https://forum.figma.com/suggest-a-feature-11/right-click-drag-to-pan-16264)

## Уровни управления

`base`

Figma не сводит управление к одному toolbar. В редакторе есть несколько уровней
control surface:

| Уровень | Что управляет | Где находится |
| --- | --- | --- |
| Workspace controls | UI chrome, panels, zoom, view options | navigation bar, sidebars, view menu |
| Tool controls | create/select/move/hand/text/frame/shape/pen | toolbar, shortcuts |
| Canvas controls | direct manipulation, marquee, drag, resize, guides | canvas |
| Layer controls | hierarchy, nesting, visibility, lock, order | left sidebar / Layers panel |
| Property controls | position, layout, fills, strokes, text, effects | right sidebar |
| Context controls | right-click actions, select layer menu, paste here | canvas/layers context menu |
| Keyboard controls | navigation, selection, object creation, shortcuts | keyboard |
| Mode controls | design/prototype/dev, vector edit, crop, text edit | mode-specific UI |
| Collaboration controls | comments, cursor chat, multiplayer cursors | canvas, comments panel |

Практический вывод для Mission Editor: нельзя реализовать Figma-like управление
только через inspector или только через canvas. Сильная модель требует
синхронизации canvas, layers, inspector, source and keyboard.

## Управление интерфейсом файла

`base`

По официальной модели Figma Design file имеет пять интерактивных областей:
toolbar, navigation bar, left panel, right panel and scrollable canvas. Эти зоны
разделяют задачи:

- toolbar: инструменты создания и выбора;
- navigation/left area: pages, layers, assets, components, plugins, variables;
- canvas: прямое редактирование;
- right sidebar: properties/prototype/inspect/export;
- top/navigation controls: file, view, mode and collaboration actions.

UX principles:

- пользователь всегда видит, где создавать, где выбирать, где редактировать;
- selection на canvas меняет right sidebar;
- layer selection в left sidebar выбирает тот же объект на canvas;
- view options отдельно от document model;
- navigation/workspace controls не должны менять сам design.

Mission Editor contract:

- `Source/Screens` слева, `Canvas` центр, `Inspector` справа;
- panel state относится к workspace, а не к документу;
- right inspector должен всегда объяснять текущую selection;
- скрытие UI не должно сбрасывать selection, zoom or screen.

## Hide, minimize and focus

`base/daily`

Figma поддерживает hide/minimize UI, чтобы освободить canvas. Это не изменение
дизайна, а изменение рабочего окружения.

Control states:

- full UI: toolbar, navigation, sidebars and canvas visible;
- minimized UI: меньше chrome, canvas важнее, panels могут возвращаться по
  контексту;
- hidden UI: интерфейсный chrome скрыт, пользователь фокусируется на canvas.

Expected behavior:

- UI chrome can disappear without changing the file;
- selection remains available;
- zoom and pan remain stable;
- keyboard shortcuts still matter;
- exit affordance must be discoverable.

Mission Editor application:

- `Main only` режим должен скрывать Source/Inspector/tool clutter;
- возвращение из focus должно восстановить размеры панелей;
- focus mode должен быть reversible and non-destructive.

## Canvas navigation

`base/daily`

Canvas navigation в Figma поддерживает несколько входов:

- zoom menu and zoom percentage;
- keyboard zoom;
- trackpad pinch;
- mouse wheel with modifiers;
- hand/pan interaction;
- arrow-key pan when nothing is selected;
- view options such as pixel grid, layout guides and multiplayer cursors.

Important behavior:

- zoom меняет canvas scale, но не размер UI panels;
- pan перемещает viewport, но не objects;
- zoom/view options are workspace state;
- keyboard navigation доступна как accessibility route;
- pan/zoom должны работать даже когда активен инструмент creation, если жест
  явно навигационный.

Contract for implementation:

- pointer coordinates must convert through zoom and pan;
- `fit selection`, `fit screen`, `100%`, `zoom in`, `zoom out` are separate
  commands;
- drag-to-pan must not accidentally move selected objects;
- view options toggles should not mutate document structure.

## Viewport pan controls

`base/daily`

Это одна из главных missing-фич для Figma-like editor. Пользователь должен
легко перемещаться по бесконечному canvas без риска сдвинуть объект.

Официальная справка Figma для design files описывает pan через `Space` +
click-drag and trackpad two-finger pan. Figma Help также описывает keyboard pan
стрелками, когда ничего не выбрано. В FigJam справке отдельно описан Hand tool
and optional right-click drag to pan preference. В Figma Forum пользователи
обсуждают middle mouse button hold as expected canvas pan behavior; это
desktop-графический паттерн, который стоит поддержать в Mission Editor даже
если официальный Figma Help формулирует его не как главный documented route.

Pan entry points:

- hold `Space` + left mouse drag;
- Hand tool active + left mouse drag;
- middle mouse button/wheel button hold + drag;
- right-click drag to pan, если включена соответствующая preference;
- trackpad two-finger pan;
- arrow-key pan when nothing is selected;
- Shift + arrow-key pan with larger step.

Middle mouse pan contract:

- pointer down with middle button starts viewport pan immediately;
- middle button pan has higher priority than object hover/selection;
- middle button pan must not select, move, resize or edit objects under cursor;
- cursor changes to hand/grabbing state;
- pointer capture continues until middle button release;
- canvas viewport moves opposite pointer delta, like grabbing the canvas;
- document coordinates and object geometry do not change;
- inspector values do not change;
- selection remains as it was before pan;
- if user starts over text, middle pan must not enter text editing;
- if user starts over resize handle, middle pan still pans viewport, not resize;
- on Linux/browser environments where middle click may be reserved for paste,
  implementation should either prevent default inside canvas or expose a
  fallback preference and document the limitation.

Space + drag pan contract:

- pressing Space temporarily switches to pan while held;
- active creation/selection tool is restored when Space is released;
- Space + drag does not select or move objects;
- if focus is inside a text input, Space should type space instead of pan;
- if focus is canvas/editor surface, Space can activate temporary pan.

Hand tool contract:

- selecting Hand tool makes left mouse drag pan;
- object hover outlines should be suppressed or visually de-emphasized in Hand
  mode;
- clicking objects in Hand mode should not select them;
- Escape or selecting Move returns to normal selection mode.

Right-click drag contract:

- short right-click without movement opens context menu;
- right-click with drag threshold pans, if preference is enabled;
- context menu should not open after a successful right-drag pan;
- this must be configurable because users also expect right-click menus.

Trackpad and wheel contract:

- two-finger trackpad pan moves viewport;
- wheel scroll pans vertically by default unless zoom modifier is held;
- Shift + wheel pans horizontally where platform conventions support it;
- Ctrl/Cmd + wheel zooms around cursor;
- zoom and pan must share one viewport transform.

QA cases:

- middle mouse drag over empty canvas pans;
- middle mouse drag over selected object pans, object geometry unchanged;
- middle mouse drag over resize handle pans, resize not triggered;
- Space + drag pans and restores previous tool;
- Hand tool drag pans and does not select objects;
- after pan, selection remains and inspector values are unchanged;
- all pointer-to-document math still works after pan.

## Toolbar and tool activation

`base`

Figma tools are mode switches. Важные инструменты:

- Move/Select;
- Hand/Pan;
- Frame;
- Shape tools;
- Pen/vector;
- Text;
- Comment;
- Scale;
- Resources/components/actions depending on UI version.

UX rules:

- active tool has visible active state;
- cursor changes according to tool;
- Escape commonly exits the current operation/mode;
- V/Move-like selection mode is the safe default;
- creation tools create on click/drag and then usually select the created layer;
- tool mode should not remain ambiguous after creation.

Mission Editor contract:

- every tool has explicit mode in editor state;
- each mode defines allowed pointer actions;
- object body drag in Select mode means move, not resize;
- resize handles have priority over move;
- text edit mode and vector edit mode are separate from object mode.

## Selection management

`base/daily`

Figma selection can happen from canvas or Layers panel. Public docs describe:

- click to select;
- marquee selection by dragging over objects;
- modifier marquee for nested layers;
- Shift click to add/remove from selection;
- right-click Select layer menu for overlapping/nested/locked layers;
- hidden layers are not selected through normal visible canvas flows;
- locked layers are not selected through regular left-click selection, but can
  be selected through layer menu/panel.

Selection states:

- no selection;
- single layer selection;
- multi-selection;
- nested selection;
- locked selection;
- hidden selected through layers/outline mode;
- text editing selection;
- vector point/path selection.

Required behavior:

- hover target does not equal selection;
- click selection should be deterministic by z-order/hit testing;
- selected layer shows bounds;
- multi-selection shows combined bounds;
- inspector shows selected properties;
- layers panel highlights the same layer.

Mission Editor contract:

- canvas, layers, inspector and source must never disagree about selected target;
- hidden layers should not be hit-tested by normal canvas clicks;
- locked layers can be structurally selected but not moved/resized;
- multi-selection needs mixed values in inspector.

## Marquee and bounding-box selection

`base/daily`

На скриншотах показан именно этот класс управления: пользователь протягивает
selection rectangle / marquee over canvas, и Figma-like editor должен выбирать
объекты по пересечению с рамкой, а не воспринимать это как move или resize.

Marquee entry rules:

- starts from empty canvas in Move/Select mode;
- starts only after drag threshold, so click on empty canvas can just clear
  selection;
- should not start if pointer down began on object body;
- should not start if pointer down began on resize/rotate/vector/crop handle;
- should not start during Space/middle/Hand pan;
- modifier can include nested/deep objects where supported.

Marquee visual:

- translucent selection rectangle;
- visible blue outline;
- rectangle follows pointer in any direction;
- origin corner remains fixed while opposite corner follows cursor;
- if dragged left/up, x/y of marquee normalize but visual direction stays
  correct;
- objects inside/intersecting marquee receive preview outlines before commit;
- dimensions or count badge can be shown, but should not cover target objects.

Selection commit:

- on pointer up, objects intersecting marquee become selected;
- if Shift is held, objects are added to/removes from current selection
  depending on selection policy;
- if no objects intersect and no modifier is held, selection clears;
- locked objects are not selected through normal marquee;
- hidden objects are not selected through normal marquee;
- nested/deep selection requires explicit modifier, not default.

Bounding box for selection:

- single selection shows object bounds and handles;
- multi-selection shows combined bounds;
- selected frame/screen can show dimension badge;
- handles are hit targets, not only visible pixels;
- bounding box must be rendered in viewport coordinates but computed from
  document geometry through current zoom/pan;
- selected out-of-viewport object should not produce broken handles.

Mission Editor contract:

- marquee selection changes selection model only, not document geometry;
- marquee must be canceled by Escape;
- marquee must not create undo entry unless selection history is intentionally
  undoable;
- object selection from marquee must sync canvas, layers and inspector.

QA cases:

- drag from empty canvas left-to-right selects intersecting objects;
- drag from empty canvas right-to-left behaves consistently with documented
  policy;
- drag beginning on object body moves object, not marquee;
- drag beginning on handle resizes object, not marquee;
- middle mouse drag never creates marquee;
- selected objects match highlighted layers.

## Selection bounds and resize handles

`base/daily`

Selection is not just a colored outline. It is the primary control surface for
transforming objects.

Expected elements:

- hover outline for potential target;
- selected bounds outline;
- corner handles;
- side handles;
- rotation affordance where supported;
- dimension badge during resize;
- x/y or distance labels during move;
- parent/frame outline when useful;
- disabled/locked visual state when selected layer cannot be edited.

Handle priority:

- handle hit target wins over object body;
- rotation handle wins over move where present;
- vector point handle wins inside vector edit mode;
- crop handle wins inside crop mode;
- gradient handle wins inside gradient edit mode;
- object body wins over marquee only when hit testing finds selectable object;
- pan gestures win over all object operations.

Hit target requirements:

- visible handle can be small, but hit target must be comfortable;
- high zoom should not make handles absurdly large;
- low zoom should keep handles usable;
- hit targets should be based on viewport pixels, not document units;
- cursor changes before pointer down.

QA cases:

- hover side handle changes cursor;
- hover corner handle changes cursor;
- body drag does not accidentally resize;
- handle drag does not accidentally move;
- at 50%, 100%, 200% zoom hit targets remain usable.

## Drag, move and reorder

`daily`

Figma uses direct manipulation for object movement:

- select object;
- drag object body to move;
- guides and distance labels help align;
- arrow keys nudge selected objects;
- layer order controls z-depth;
- smart selection can move, duplicate, resize and reorder items within a group
  of selected layers.

Important distinction:

- drag inside object body means move;
- drag on handle means resize/transform;
- drag in layers panel means reorder/reparent;
- drag with copy modifier can duplicate;
- drag in auto layout often means reorder/insertion, not free position.

Mission Editor contract:

- pointer down must resolve an operation: move, resize, marquee, pan, text edit,
  vector edit, or no-op;
- operation should be captured until pointer up;
- drag threshold prevents accidental movement on click;
- movement updates real document geometry;
- layers reorder updates z-order and render order.

## Object body drag contract

`base/daily`

Это второй критический missing-контракт. В Figma-like редакторе объект должен
двигаться, когда пользователь хватает его видимую область, а не только
специальный handle. Если пользователь не может просто взять компонент и
перетащить его, editor ощущается сломанным.

Pointer resolution:

- pointer down on selected object's body prepares move operation;
- pointer down on unselected selectable object's body selects it and prepares
  move after drag threshold;
- pointer down on empty canvas prepares marquee;
- pointer down on handle prepares that handle operation;
- pointer down with middle button prepares viewport pan;
- pointer down while Space is held prepares viewport pan;
- pointer down in text edit mode edits text, unless user exits edit mode first.

Move lifecycle:

- pointer down stores start pointer, start geometry and selected ids;
- movement below threshold remains click/selection;
- movement above threshold enters drag move;
- pointer capture remains active until pointer up/cancel;
- selected objects render live at preview position;
- inspector may update live or on commit, but must match after commit;
- pointer up commits document geometry;
- Escape cancels preview and restores start geometry;
- undo restores previous geometry.

Move geometry:

- delta is calculated in document coordinates, not screen pixels;
- delta accounts for zoom and pan;
- moving one object changes its x/y relative to parent;
- moving multi-selection applies same delta to all selected objects;
- moving object inside parent frame keeps parent-relative coordinates;
- moving across frames requires explicit reparent/drop behavior, not accidental
  coordinate corruption;
- locked objects cannot move;
- hidden objects cannot be moved through canvas.

Visual feedback:

- cursor changes to moving/grabbing after drag starts;
- selected bounds move with object;
- distance labels show relation to nearby objects/parent;
- alignment guides appear;
- if object cannot move, show locked/disabled state instead of silently doing
  nothing.

Conflict rules:

- body drag must not start text selection;
- body drag must not start panel resize;
- body drag must not start marquee;
- body drag must not mutate width/height;
- drag on child inside component/frame selects according to nested selection
  policy before move.

QA cases:

- grab selected rectangle body and move it;
- grab unselected rectangle body and move it in one drag;
- grab text object body outside edit mode and move it;
- grab component body and move it;
- grab locked object body: no move, clear lock feedback;
- drag at 50%, 100%, 200% zoom: same logical delta;
- inspector X/Y equals final canvas position.

## Resize, scale, rotation and flip

`daily`

Figma separates several geometry operations:

- resize from bounds handles;
- scale tool for proportional scaling;
- rotation by rotation handle/field;
- flip horizontal/vertical;
- alignment controls;
- W/H and X/Y fields in right sidebar.

Resize behavior:

- left/right bounds adjust width;
- top/bottom bounds adjust height;
- corner adjusts width and height;
- W/H fields edit dimensions numerically;
- aspect ratio can be locked/unlocked;
- modifier keys temporarily lock or unlock aspect ratio;
- constraints/auto layout can affect children when parent resizes.

Scale behavior:

- scale tool proportionally resizes layer and nested properties;
- strokes and blurs scale with scale tool;
- scale ignores constraints to preserve proportional result.

Rotation/flip behavior:

- rotation can be direct or numeric;
- flip is command-based;
- multi-selection transforms around shared bounds.

Mission Editor contract:

- drag right edge grows/shrinks right side without moving x;
- drag left edge changes x and width while right side remains fixed;
- drag bottom changes height without changing y;
- drag top changes y and height while bottom remains fixed;
- scale and resize must not be silently conflated;
- inspector values must match canvas after transformation.

## Position and z-order

`daily`

Figma controls position with canvas movement and numeric right-sidebar fields.
Layer depth is represented by order in the Layers panel: top layers render in
front, lower layers render behind.

Controls:

- X;
- Y;
- W;
- H;
- rotation;
- layer order;
- bring forward/backward;
- move into/out of frame/group;
- align/distribute.

UX requirements:

- coordinates are relative to parent frame/context;
- layers can be nested;
- z-order cannot be solved by a disconnected number if hierarchy matters;
- moving between parents changes coordinate basis and must preserve visual
  position where possible.

Mission Editor contract:

- geometry model needs parent-relative coordinates;
- renderer must respect layer order;
- reparenting must explicitly transform coordinates;
- inspector and layers panel should expose order/depth affordances.

## Frames and parent-child control

`base/daily`

Frames are one of Figma's main control primitives. They can be screens,
containers, components, masks/clipping boundaries, auto layout parents and
prototype targets.

Frame control includes:

- create frame with frame tool or preset;
- resize frame;
- nest layers inside frame;
- control clipping/overflow;
- apply layout guides;
- apply auto layout;
- use constraints on child layers;
- set fills/strokes/effects on frame itself.

Parent-child behavior:

- child coordinates are relative to parent;
- moving a parent moves children visually;
- resizing a parent can affect children through constraints/auto layout;
- locking a parent can lock children;
- hiding a parent hides children.

Mission Editor contract:

- screen is a top-level frame;
- nested frame is a normal parent node;
- frame clipping must affect preview/export;
- frame resize must respect child layout/constraints.

## Auto layout and responsive controls

`daily/advanced`

Figma auto layout is a control system over child placement, not just a visual
style. It provides:

- Add auto layout from right sidebar or shortcut;
- horizontal flow;
- vertical flow;
- grid flow;
- gap/spacing;
- padding;
- alignment;
- wrapping/grid tracks;
- fixed/hug/fill resizing;
- min/max constraints in newer layout contexts;
- child reorder through canvas/layers.

Control implications:

- users manipulate layout via both inspector and on-canvas handles;
- drag inside auto layout often changes order;
- spacing can be changed numerically or through smart handles;
- child free movement may be disabled unless absolute positioning is enabled;
- layout reflow must be predictable.

Mission Editor contract:

- free positioning and auto layout should be separate modes;
- gap/padding/alignment must update layout live;
- moving a child inside auto layout should show insertion line;
- constraints apply to free/absolute children, not auto layout children without
  clear explanation.

## Smart selection and spacing controls

`advanced`

Figma Smart selection adds special on-canvas controls for a selected group of
layers. The docs describe pink handles, spacing adjustment and tooltip distance
feedback.

Controls:

- select multiple objects;
- smart handles appear;
- hover between layers shows spacing handle;
- drag handle changes spacing;
- tooltip shows pixel distance;
- marked items can be resized, moved, duplicated or deleted inside selection.

Why it matters:

- Figma treats spacing as a first-class editable object;
- users can edit layout rhythm without manually setting each coordinate;
- visual handles reduce reliance on numeric fields.

Mission Editor contract:

- MVP can start with distance labels/guides;
- advanced version should include smart spacing handles;
- group spacing changes should be undoable and model-backed.

## Layers panel controls

`base/daily`

Layers panel controls:

- selection;
- nesting expand/collapse;
- layer type icon;
- layer name;
- order/z-index;
- visibility;
- lock;
- grouping/framing context;
- components/instances indication.

Figma layer rules:

- top layer in panel appears above lower layers;
- hidden layers are greyed/inactive and not visible on canvas;
- locked layers show padlock and cannot be edited on canvas;
- parent lock affects children;
- visibility can be toggled from Layers panel or shortcut;
- lock/unlock can be done from canvas context menu, Layers panel or shortcut.

Mission Editor contract:

- every renderable object must have a layer row;
- layer visibility and lock must be model fields;
- layer reorder must update canvas render order;
- layer selection must sync inspector and canvas.

## Right sidebar / property controls

`base/daily`

The right sidebar is the property control surface. It adapts to selection and
mode.

Common right-sidebar sections:

- layer name/type;
- position/dimensions/alignment;
- layout/auto layout;
- constraints/resizing;
- fill;
- stroke;
- effects;
- text properties;
- component properties;
- prototype interactions;
- export.

UX rules:

- no selection shows page/canvas properties;
- selected layer shows relevant controls;
- unsupported controls are hidden or disabled;
- multi-selection shows mixed values;
- right sidebar should not expose fake values disconnected from selection.

Mission Editor contract:

- every visible editable field must update document model;
- field edit should support Enter/apply and Escape/cancel;
- canvas manipulations must update inspector values;
- inspector changes must update canvas.

## Fill and color picker controls

`daily`

Figma Fill controls use a swatch in the right sidebar. Clicking the swatch opens
the color picker. The color picker configures layer fill or stroke fill.

Supported fill families:

- solid;
- gradient;
- pattern;
- image;
- video.

Figma color picker controls include:

- fill type selector;
- color selector for hue/saturation/opacity/value-like adjustments;
- HEX/RGB/HSL/HSB input modes;
- opacity;
- blend mode;
- gradient stops;
- styles/variables/libraries picker;
- image/video controls depending on fill type.

Important behavior:

- layer fill and stroke fill are different contexts;
- fill opacity is not the same as layer opacity;
- text fill changes glyph color, not background;
- multiple fills can exist and each has its own properties;
- selection colors expose swatches for mixed selections.

Mission Editor contract:

- color selector must include 2D saturation/brightness field, hue slider and
  opacity slider;
- active context must be clear: fill, stroke, text fill, gradient stop, effect;
- invalid HEX should not corrupt current color;
- gradient stop selection should reuse the same color selector.

## Stroke controls

`daily`

Figma Stroke section controls:

- add stroke;
- stroke fill;
- multiple stroke fills;
- visibility toggle;
- style/variable picker;
- remove fill;
- stroke position: inside, outside, center;
- weight;
- individual strokes/sides for supported shapes;
- caps/endpoints;
- joins;
- dash pattern;
- custom stroke styles.

Important behavior:

- lines default to center stroke;
- most shapes default to inside stroke;
- stroke position preview can appear on hover in dropdown;
- stroke fills share the same position/weight/style properties;
- resize and scale have different impact on strokes.

Mission Editor contract:

- stroke is not just a border string;
- stroke color uses the same color picker but separate context;
- line/arrow endpoints are stroke-level controls;
- resize should keep stroke weight unless using explicit scale.

## Appearance controls

`daily/advanced`

Figma appearance includes:

- opacity;
- blend mode;
- effects;
- shadows;
- blur;
- corner radius;
- corner smoothing;
- styles;
- variables.

Control patterns:

- effect rows can be added, configured, toggled and removed;
- blend modes can apply to layers, fills and effects;
- corner radius can be one value or per-corner;
- canvas handles may exist for shape-specific appearance, such as arc handles;
- styles/variables are references, not just raw values.

Mission Editor contract:

- layer opacity separate from fill/stroke/effect opacity;
- effect rows should have visibility and delete controls;
- radius can be edited through inspector and eventually canvas handles;
- style/token references must be visible and detachable.

## Text controls

`daily`

Figma text management is both canvas-based and property-based.

Creation:

- click with Text tool creates Auto width text;
- click-drag creates Fixed size text;
- content is edited directly on canvas.

Text resizing:

- Auto width grows horizontally with content;
- Auto height wraps and grows vertically;
- Fixed size keeps dimensions and can overflow/wrap;
- text resizing properties live in the right sidebar.

Typography controls:

- font family;
- style/weight;
- size;
- line height;
- letter spacing;
- paragraph spacing;
- horizontal alignment;
- vertical alignment for fixed-size text;
- lists;
- links;
- OpenType features;
- text styles;
- variables.

Mission Editor contract:

- text edit mode is separate from object selection/move;
- clicking text in edit mode edits content, dragging selected text object moves
  the layer;
- resizing text box changes wrapping;
- text fill is glyph color, not background.

## Vector and shape controls

`daily/advanced`

Figma vector control includes regular shape tools and vector edit mode.

Shape controls:

- rectangle;
- ellipse;
- line;
- arrow;
- polygon;
- star;
- arc/ring handles for ellipses;
- shape-specific handles.

Vector controls:

- Pen tool;
- points;
- paths;
- bezier handles;
- bend tool;
- lasso select;
- cut;
- paint closed regions;
- vector networks that can branch rather than only closed paths.

UX rules:

- object mode manipulates vector bounds;
- vector edit mode manipulates points/paths;
- selected points have a different visual state than selected object;
- Escape exits vector edit mode;
- stroke endpoints/caps/joins connect vector editing with Stroke controls.

Mission Editor contract:

- do not conflate shape resize with vector point editing;
- line endpoints are editable but still part of object/vector geometry;
- vector edit mode needs its own selection model.

## Image/video controls

`daily`

Figma treats images and videos as fills or media layers. Image controls are
accessed through the fill swatch/color picker and image properties.

Controls:

- add image/video from toolbar or color picker;
- upload/place assets;
- replace image/video fill;
- crop mode;
- image fill modes;
- drag crop handles;
- asset preview;
- checker placeholder before choosing file;
- missing/replace states.

Mission Editor contract:

- media should come from explicit resources;
- no random logos or generated placeholders unless user explicitly requests;
- missing asset state should be visible;
- replacing media should preserve geometry and fill mode where possible.

## Prototype and scroll controls

`daily/advanced`

Figma prototype controls are another management layer:

- create interactions;
- connect frames;
- configure action;
- overlays;
- scroll/overflow behavior;
- fixed position while scrolling;
- smart animate;
- prototype view.

Relevant for editor management:

- prototype handles/links are on-canvas controls;
- interaction settings live in right sidebar;
- scroll/overflow interacts with frame size and clip content;
- fixed-position elements have separate behavior inside scrolling frames.

Mission Editor contract:

- scroll/overflow should be frame-level behavior;
- interaction/prototype mode should be separate from design edit mode;
- connection handles must not conflict with resize handles.

## Comments, cursor chat and collaboration controls

`daily`

Figma collaboration controls include:

- multiplayer cursors;
- comments pinned to canvas/frame/layer/prototype coordinate;
- comment threads;
- cursor chat;
- mentions;
- notifications;
- version history.

Control patterns:

- comments are mode-based;
- comment pin is an on-canvas object;
- cursor chat follows cursor;
- user can pan/zoom without exiting cursor chat;
- collaboration overlays should not mutate design geometry.

Mission Editor contract:

- comments must be separate from design nodes;
- comment mode should not accidentally select/move layers;
- comment pins need coordinate mapping through zoom/pan;
- collaboration overlays should be toggleable.

## Keyboard and accessibility controls

`base/advanced`

Figma Help documents keyboard access for:

- pan canvas with arrow keys when nothing is selected;
- faster pan with Shift;
- zoom with Command/Ctrl plus/minus;
- create objects through keyboard workflows;
- keyboard box selection;
- Escape to exit keyboard selection;
- keyboard layout selection for non-US layouts.

Common shortcut families:

- tool activation;
- selection;
- nudge;
- duplicate;
- copy/paste;
- group/ungroup;
- lock/unlock;
- hide/show;
- zoom/view;
- text formatting;
- vector editing.

Mission Editor contract:

- keyboard interactions should mirror pointer interactions;
- nudge must update real geometry;
- shortcuts must be discoverable;
- keyboard layout differences should not hardcode only US assumptions if the app
  grows beyond MVP.

## Context menus and action menus

`daily`

Figma uses context menus for local actions:

- right-click canvas;
- right-click object;
- right-click layer row;
- Select layer menu for overlapping/locked/nested objects;
- paste here;
- lock/unlock;
- hide/show;
- copy/paste properties;
- plugins/actions menu.

UX principle:

- context menus expose local operations near the user's focus;
- unavailable actions should be disabled, not silently ignored;
- context actions should respect current selection and mode.

Mission Editor contract:

- object context menu should include duplicate/delete/lock/hide/order/export;
- canvas context menu should include paste, create frame, add comment, fit;
- layer context menu should include rename, reveal, lock, hide, reorder;
- every menu action must go through the same command/reducer path as toolbar.

## AI and agent controls

`new/cool`

Figma has AI/agent flows in design files. Public docs describe:

- on-canvas prompt box;
- persistent sidebar chat;
- keyboard access to agent;
- layer-aware prompt context;
- AI feature availability depending on rollout/admin settings.

Relevance:

- agent UI is another control layer;
- agent can act on selection;
- on-canvas AI prompt must not conflict with design object editing;
- AI actions need visible preview/confirmation when they mutate design.

Mission Editor contract:

- AI actions should target selected screen/layer explicitly;
- prompt output should create model-backed changes, not detached UI;
- destructive AI changes need preview, diff or undo.

## Обобщенные production contracts

`implementation guidance`

Для Figma-like управления в Mission Editor нужны следующие системные контракты:

1. One selection model
   Canvas, layers, inspector, source and comments point to one selected target.

2. One coordinate pipeline
   Pointer screen coordinates convert through viewport zoom/pan into document
   coordinates before changing geometry.

3. Operation lifecycle
   Hover, pointer down, drag, pointer capture, commit/cancel and undo entry are
   explicit for every interaction.

4. Handles have priority
   Resize/vector/crop/gradient handles win over body move; body move wins over
   marquee only after a valid object hit.

5. Workspace state is separate
   Zoom, panel size, hidden UI, view options and focus mode do not belong inside
   the design document.

6. Inspector is model-backed
   No visible editable field should be disconnected from document state.

7. Assets are deterministic
   Logos/images/icons come from resources or show missing asset, never from
   random placeholders.

8. Color context is explicit
   Fill, stroke, text fill, gradient stop and effect color must not overwrite
   each other.

9. Modes are explicit
   Design, prototype, text edit, vector edit, crop, comment and AI prompt modes
   have separate hit-testing and keyboard behavior.

10. Undo is universal
    Every geometry/style/content/layer change has an undoable command.

## Полная чек-карта управления

`qa checklist`

Workspace:

- resize sidebars;
- hide/minimize UI;
- focus canvas;
- restore layout;
- zoom/pan/fit;
- middle mouse / wheel-button drag pans viewport;
- Space + drag pans viewport;
- Hand tool drag pans viewport;
- toggle view options.

Tools:

- select;
- hand;
- frame;
- shape;
- pen;
- text;
- comment;
- scale.

Selection:

- hover outline;
- click select;
- marquee;
- marquee starts only from empty canvas;
- marquee does not start during middle mouse or Space pan;
- selection bounds and handles appear after selection;
- nested select;
- shift multi-select;
- select from layers;
- select locked via menu/panel;
- hidden layer handling.

Geometry:

- move;
- body drag moves selected/unselected object after threshold;
- body drag never changes width/height;
- nudge;
- resize sides/corners;
- handles have priority over body drag;
- scale;
- rotate;
- flip;
- align;
- distribute;
- z-order.

Structure:

- create frame;
- nest/reparent;
- group/ungroup;
- layer reorder;
- lock/unlock;
- hide/show;
- duplicate/delete;
- copy/paste/paste here.

Layout:

- add auto layout;
- switch direction;
- grid flow;
- gap;
- padding;
- alignment;
- fixed/hug/fill;
- constraints;
- clip/overflow.

Appearance:

- fill;
- color picker;
- gradient;
- image/video fill;
- stroke;
- stroke position;
- dash/cap/join;
- opacity;
- blend;
- effects;
- radius.

Content:

- create text;
- edit text;
- text resize;
- typography fields;
- links/lists;
- convert text to vector;
- image crop/replace.

Advanced:

- vector edit mode;
- point/path edit;
- smart selection handles;
- prototype connections;
- comments/cursor chat;
- AI prompt/sidebar.

## Что брать в Mission Editor MVP

`scope`

Минимальный Figma-like control scope:

- panel resize/collapse/focus;
- zoom/pan/fit;
- select/move/resize with correct handles;
- X/Y/W/H inspector sync;
- layers selection/order/visibility/lock;
- frame/screen create and resize;
- text create/edit/resize;
- shape tools;
- fill/stroke with real color selector;
- undo/redo;
- deterministic resources for images/logos.

Не переносить в MVP без необходимости:

- full vector networks;
- smart selection spacing handles;
- full plugin/shader controls;
- complete prototype editor;
- full AI agent mutation flow;
- advanced variable/style systems.

## Риски неправильной реализации

`pitfalls`

- Сделать UI-поля, которые не меняют model.
- Игнорировать zoom/pan in pointer math.
- Не реализовать middle mouse / wheel-button drag для viewport pan.
- Смешать pan canvas and move object.
- Считать marquee selection второстепенной, хотя это базовое выделение.
- Запускать marquee при drag от object body вместо move.
- Не описать selection bounds/handles как отдельный control surface.
- Перепутать resize left/top and right/bottom behavior.
- Сделать drag только с handles, но не с object body.
- Смешать fill opacity and layer opacity.
- Смешать fill and stroke color contexts.
- Рисовать случайные логотипы вместо resource-backed assets.
- Сделать hidden layers selectable by normal click.
- Не различать locked selection and locked editing.
- Смешать text edit mode with object move mode.
- Смешать vector bounds resize with point editing.
- Хранить workspace state inside document.

## Итог

Figma-like управление - это не набор отдельных кнопок. Это система, где
selection, coordinate transforms, direct manipulation, sidebars, shortcuts,
modes and document state работают как один контур. Для Mission Editor главный
продуктовый критерий: пользователь должен видеть один и тот же объект в canvas,
layers, inspector and source, управлять им мышью/клавиатурой/полями и получать
одинаково предсказуемый результат.
