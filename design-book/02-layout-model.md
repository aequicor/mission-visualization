# Layout и responsive design

[← Оглавление](README.md)

Figma layout-фичи нужны, чтобы UI вел себя предсказуемо при изменении контента,
размеров экрана и структуры. Эта глава собирает базовые и продвинутые layout
возможности Figma.

## Frames as layout containers

`base`

Frame может быть просто контейнером, а может стать responsive layout container,
если включить Auto layout or constraints.

Параметры frame:

- width and height;
- x/y position on canvas;
- rotation;
- fill/stroke/effects;
- corner radius;
- clip content;
- layout grids;
- constraints;
- Auto layout;
- export settings;
- prototype settings.

## Auto layout

`daily`

Auto layout раскладывает children inside frame automatically. Когда меняется
text, добавляется item or меняется размер parent, layout пересчитывается.

Основные настройки:

- flow: vertical, horizontal, grid;
- padding;
- gap between;
- alignment;
- resizing behavior;
- wrap where available;
- min/max dimensions;
- child order;
- ignore auto layout.

Типичные use cases:

- buttons that resize with label;
- forms with stable spacing;
- lists with adding/removing items;
- cards and panels;
- navigation bars;
- dashboards;
- responsive website sections.

## Vertical flow

`daily`

Vertical Auto layout places children along the y-axis.

Параметры:

- top-to-bottom order;
- vertical gap;
- horizontal alignment;
- vertical padding;
- width/height resizing;
- child fill/hug behavior.

Где использовать:

- forms;
- side panels;
- settings pages;
- cards with title/body/actions;
- timelines;
- mobile screens.

## Horizontal flow

`daily`

Horizontal Auto layout places children along the x-axis.

Параметры:

- left-to-right order;
- horizontal gap;
- vertical alignment;
- horizontal padding;
- fill/hug/fixed children;
- wrap for overflow.

Где использовать:

- buttons with icon and label;
- toolbars;
- nav bars;
- tab rows;
- card actions;
- table rows;
- metadata chips.

## Wrap

`daily`

Wrap pushes overflowing horizontal Auto layout children to the next line.

Параметры:

- horizontal flow must be enabled;
- gap between items;
- row gap;
- parent width;
- item sizing.

Где использовать:

- tag groups;
- filter chips;
- responsive action groups;
- icon grids that should wrap before becoming cramped.

## Grid auto layout

`advanced`

Grid Auto layout gives two-dimensional control: columns, rows, tracks, gaps and
cell spans. It is stronger than nested rows/columns for dashboards and galleries.

Параметры:

- number of columns;
- number of rows or auto rows;
- column and row track sizing;
- row/column gaps;
- cell placement;
- row span and column span;
- resizing behavior for tracks and cells.

Где использовать:

- dashboards;
- KPI card grids;
- bento layouts;
- pricing tables;
- galleries;
- complex responsive sections.

## Padding and gap

`daily`

Padding controls space between parent edges and children. Gap controls distance
between children.

Настройки:

- uniform padding;
- per-side padding;
- vertical/horizontal padding;
- fixed numeric gap;
- auto gap/distribution in horizontal and vertical flows;
- separate row/column gap in grid contexts.

Почему важно: padding and gap replace manual nudging and make spacing editable
as a system.

## Alignment

`daily`

Alignment controls how children sit inside an Auto layout frame.

Параметры:

- left/center/right;
- top/middle/bottom;
- stretch;
- distribution such as packed/space between;
- baseline alignment where relevant.

Use cases:

- center button labels;
- align icons with text;
- stretch cards to equal widths;
- space nav items across a bar.

## Resizing behavior

`daily`

Figma uses explicit resizing behavior instead of guessing.

Основные варианты:

- Hug contents: frame grows/shrinks around children.
- Fill container: child stretches to available parent space.
- Fixed width/height: selected dimension remains fixed.
- Min width/height: lower bound.
- Max width/height: upper bound.

Зачем:

- button grows with label;
- input fills form width;
- card keeps minimum readable width;
- hero section has max content width;
- table cells avoid overlap.

## Ignore auto layout

`advanced`

Ignore auto layout removes a child from the Auto layout flow while keeping it
inside the parent frame.

Где полезно:

- close button in dialog corner;
- badge over avatar;
- notification dot;
- decorative mark;
- annotation or comment-like marker;
- overlay content.

Что помнить:

- ignored object no longer participates in sibling spacing;
- it behaves closer to absolute positioning;
- constraints can matter for resizing;
- overuse makes files harder to maintain.

## Constraints

`daily`

Constraints define how a layer responds when its parent frame resizes.

Параметры:

- left/right;
- top/bottom;
- center;
- scale;
- combinations such as left and right.

Где использовать:

- fixed nav inside resizable frame;
- bottom sheet;
- pinned footer;
- responsive image/card placement;
- ignored Auto layout children.

## Layout grids and guides

`daily`

Layout grids and guides help align elements consistently.

Types and settings:

- grid;
- columns;
- rows;
- count;
- gutter;
- margin;
- stretch/center alignment;
- layout guide lines.

Use cases:

- desktop column system;
- mobile margins;
- consistent marketing sections;
- visual rhythm across screens;
- developer handoff.

## Clipping and overflow

`daily/advanced`

Frames can clip content. Prototypes can also handle scroll and overflow.

Параметры:

- clip content;
- scroll behavior in prototype;
- fixed elements while scrolling;
- overflow content beyond frame;
- nested scroll areas.

Где использовать:

- mobile screen previews;
- side panels;
- long lists;
- sticky nav;
- carousel/galleries.

## Practical layout checklist

- Use frames for meaningful containers.
- Prefer Auto layout for UI structure.
- Use groups sparingly for temporary organization.
- Use padding/gap instead of manual spacing.
- Use hug/fill/fixed intentionally.
- Use min/max for responsive safety.
- Use grid for real two-dimensional layouts.
- Use constraints for resize behavior outside regular Auto layout.
- Use ignore auto layout only for intentional overlays.
- Name frames and layers so layout is readable in Layers panel.

## Источники

- [Guide to auto layout](https://help.figma.com/hc/en-us/articles/360040451373-Guide-to-auto-layout)
- [Use the grid auto layout flow](https://help.figma.com/hc/en-us/articles/31289469907863-Use-the-grid-auto-layout-flow)
- [Apply constraints to define how layers resize](https://help.figma.com/hc/en-us/articles/360039957734-Apply-constraints-to-define-how-layers-resize)
- [Create layout guides](https://help.figma.com/hc/en-us/articles/360040450513-Create-layout-guides)
- [Prototype scroll and overflow behavior](https://help.figma.com/hc/en-us/articles/360039818734-Prototype-scroll-and-overflow-behavior)
