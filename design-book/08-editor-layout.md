# Layout: поведение layout-редактирования

[← Оглавление](README.md)

Эта глава описывает UX для layout-свойств: auto layout, направление, gap,
padding, alignment, resizing, constraints, grid flow and responsive behavior.

Figma reference:

- [Guide to auto layout](https://help.figma.com/hc/en-us/articles/360040451373-Guide-to-auto-layout)
- [Use the horizontal and vertical flows in auto layout](https://help.figma.com/hc/en-us/articles/31289464393751-Use-the-horizontal-and-vertical-flows-in-auto-layout)
- [Use the grid auto layout flow](https://help.figma.com/hc/en-us/articles/31289469907863-Use-the-grid-auto-layout-flow)
- [Apply constraints to define how layers resize](https://help.figma.com/hc/en-us/articles/360039957734-Apply-constraints-to-define-how-layers-resize)

## UX-модель Figma

`daily`

В Figma auto layout применяется к frames. Пользователь выбирает layers/frame и
добавляет auto layout через right sidebar или shortcut. После этого children
располагаются не вручную, а по правилам parent frame:

- vertical flow;
- horizontal flow;
- grid flow;
- spacing/gap;
- padding;
- alignment;
- wrapping/grid tracks;
- resizing behavior parent and children.

Ключевая UX-идея: layout становится видимым контрактом. Пользователь видит, что
объект не просто стоит в координатах, а подчиняется правилам container.

## Add layout behavior

`base/daily`

Mission Editor должен позволять включать layout behavior на frame/container.

Flow:

- пользователь выбирает frame/container;
- inspector показывает секцию `Layout`;
- если layout выключен, видна primary action `Add layout`;
- после включения появляются controls direction/gap/padding/alignment;
- children сразу перестраиваются;
- canvas показывает layout guides/spacing indicators.

Варианты layout:

- `Free`: absolute positioning внутри parent;
- `Vertical`: children идут сверху вниз;
- `Horizontal`: children идут слева направо;
- `Grid`: children раскладываются по rows/columns;
- `Stack`: children могут накладываться, но parent остается frame.

UX detail:

- переключение layout mode должно быть undoable;
- перед разрушительным reflow можно показать preview или keep positions option;
- если container содержит вручную расставленные объекты, включение layout должно
  объяснить, что позиции будут интерпретированы как порядок children.

## Direction and flow controls

`daily`

Direction должен быть быстрым segmented control:

- vertical icon;
- horizontal icon;
- grid icon;
- stack/free icon.

При hover:

- canvas preview может временно показывать новый flow;
- tooltip называет поведение: `Vertical layout`;
- current direction подсвечен.

При click:

- layout меняется сразу;
- children reflow;
- inspector сохраняет selection на parent;
- spacing labels обновляются.

Grid behavior:

- пользователь задает columns/rows/tracks;
- можно выбрать fixed или auto columns;
- gap разделяется на row gap and column gap;
- если children больше, чем visible tracks, grid добавляет rows;
- drag child внутри grid показывает insertion cell.

## Gap and padding manipulation

`daily`

Gap and padding должны редактироваться и числом, и прямым drag.

Inspector:

- gap field;
- horizontal/vertical gap fields for grid;
- padding all;
- independent padding top/right/bottom/left;
- lock/unlock padding sides;
- scrub controls на числах.

Canvas:

- при выборе auto layout frame появляются spacing indicators;
- hover по gap indicator подсвечивает расстояние;
- drag gap handle меняет spacing;
- padding handles показываются внутри frame edges;
- drag padding handle меняет соответствующую сторону;
- если стороны locked, меняются все sides together.

Keyboard:

- arrow в numeric field меняет на 1;
- Shift + arrow меняет на 10;
- Escape отменяет field edit;
- Enter применяет.

Acceptance:

- изменение gap/padding видно сразу;
- labels не перекрывают content;
- для mixed selection показывается `Mixed`;
- invalid values не применяются.

## Alignment

`daily`

Alignment отвечает за расположение children внутри parent frame.

Controls:

- start/center/end/stretch for cross axis;
- packed/space between for main axis;
- top/middle/bottom for vertical cases;
- left/center/right for horizontal cases.

UX behavior:

- icons должны показывать реальное направление текущего layout;
- при hover можно preview-ить alignment на canvas;
- click применяет alignment;
- если child имеет fixed absolute mode, он не участвует в alignment and UI
  должен объяснить исключение.

## Resizing behavior

`daily`

В Figma resizing позволяет parent and children реагировать на изменение
контента. Mission Editor должен иметь похожие пользовательские режимы:

- `Fixed`: размер задан вручную;
- `Hug contents`: parent подстраивается под children/content;
- `Fill container`: child занимает доступное место в parent;
- `Min/Max`: ограничители размера.

Parent controls:

- width mode;
- height mode;
- min width/height;
- max width/height;
- clip content;
- include stroke in layout, если stroke поддержан.

Child controls:

- fixed width/height;
- fill remaining space;
- hug content;
- align self override;
- layout order.

Canvas behavior:

- resize parent вызывает reflow children;
- resize child в auto layout может менять sibling positions;
- если child fill container, его ручной resize может переключить режим или
  показать предупреждение;
- modifier key может временно игнорировать constraints/resizing.

## Constraints

`daily/advanced`

Constraints нужны для free/absolute children внутри frame.

User-facing controls:

- left;
- right;
- left and right;
- center;
- scale;
- top;
- bottom;
- top and bottom;
- vertical center.

UX:

- controls показываются как visual pin grid;
- выбранные anchors подсвечены;
- при resize parent canvas показывает, как object останется anchored;
- constraints не должны смешиваться с auto layout без явного объяснения;
- если layer находится в auto layout, constraints section может быть hidden or
  disabled with reason.

## Layout layers and order

`daily`

Порядок children в auto layout должен быть управляемым:

- drag object на canvas меняет order;
- insertion line показывает новое место;
- layers panel отражает тот же порядок;
- order field может быть доступен в inspector для точной правки;
- keyboard shortcuts move layer before/after siblings.

Important detail:

- drag внутри auto layout не должен превращаться в free movement без явного
  режима;
- если пользователь хочет absolute child, нужна команда `Remove from layout`
  или `Absolute position inside frame`.

## Layout checklist

- layout включается на frame/container;
- direction переключается vertical/horizontal/grid/free;
- gap and padding редактируются через inspector and canvas;
- alignment controls соответствуют текущему direction;
- resizing modes работают отдельно для parent and child;
- constraints доступны там, где есть free positioning;
- drag children показывает insertion line;
- layout changes undoable;
- canvas, layers and inspector остаются синхронизированы.
