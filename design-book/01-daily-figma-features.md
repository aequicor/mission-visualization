# Ежедневные Figma-функции

[← Оглавление](README.md)

Эта глава - короткий словарь Figma-фич, которыми обычно пользуются каждый день:
что есть в продукте, зачем это нужно и какие настройки важно помнить.

## Files, pages и canvas

`base`

Figma file - рабочая единица для дизайна. Внутри файла есть pages, а на pages
лежит canvas с frames, sections, components, notes, prototypes and assets.

Ключевые возможности:

- несколько pages в одном file;
- бесконечный canvas;
- перемещение, zoom, pan;
- file browser, teams/projects;
- sharing links and permissions;
- multiplayer editing.

Почему важно: Figma работает не как один static artboard, а как живой workspace
для исследования, дизайна, ревью и handoff.

## Frames

`base/daily`

Frame - базовый контейнер Figma. Через frames делают screens, sections,
components, cards, modals, social posts, website sections and export targets.

Параметры и настройки:

- position and dimensions;
- fill, stroke, effects, corner radius;
- clipping;
- constraints;
- layout grids/guides;
- Auto layout;
- prototype connections;
- export settings;
- frame presets for devices and common formats.

Почему круто: frame объединяет визуальный контейнер, layout container,
prototype target and handoff unit.

## Layers panel

`base/daily`

Layers panel показывает все объекты страницы: frames, groups, sections,
components, instances, text, shapes, vectors and media-backed layers.

Ключевые привычки:

- order in panel explains overlap on canvas;
- nested objects можно раскрывать;
- hover highlights object on canvas;
- click selects layer;
- layer names matter for handoff and smart animate;
- icons help identify layer type.

Почему важно: хороший Figma-файл читается через Layers panel почти как документ.

## Sections

`base/daily`

Sections группируют области canvas. Их удобно использовать для flow maps,
variants exploration, handoff areas, draft/archive zones and presentation-ready
parts of a file.

Типичные применения:

- group multiple related frames;
- separate exploration from final design;
- label stages of a user flow;
- prepare review areas;
- organize large files.

## Groups

`base`

Groups объединяют выбранные layers, но не дают столько layout behavior, сколько
frames. Обычно groups полезны для временной организации, а frames лучше для UI
containers.

Параметры:

- nested layers;
- group bounds from children;
- selection as one unit;
- no independent Auto layout behavior like a frame.

## Shapes

`base/daily`

Shapes - rectangles, lines, ellipses, polygons, stars and other primitives.
Они нужны для backgrounds, dividers, icons, skeletons, decorations and quick
wireframes.

Настройки:

- fill and stroke;
- corner radius and smoothing;
- rotation;
- opacity/blend mode;
- effects;
- constraints and resizing;
- boolean operations when combined.

## Text layers

`base/daily`

Text layers используются для headings, body copy, labels, captions, table
values, links, annotations and prototype content.

Ключевые настройки:

- font family, weight, size;
- line height and letter spacing;
- paragraph spacing;
- horizontal/vertical alignment;
- auto width/auto height/fixed size;
- max lines and truncation;
- decoration, case, lists and OpenType features;
- text styles.

## Images, video and fills

`base/daily`

В Figma Design images and videos are fills, not a separate image layer type.
Пользователь добавляет image на rectangle/frame/shape/text layer, а затем
управляет fill behavior.

Важные настройки:

- image fill;
- crop/fit behavior;
- multiple fills;
- opacity/blend mode;
- masks;
- export settings;
- replacement through fill controls.

## Auto layout

`daily`

Auto layout делает frames responsive to content. Он нужен для buttons, forms,
cards, lists, navigation, dashboards and responsive site sections.

Основные настройки:

- flow: vertical, horizontal, grid;
- padding;
- gap between;
- alignment;
- wrap for horizontal flow;
- resizing: hug contents, fill container, fixed size;
- min/max dimensions;
- ignore auto layout for overlay-like children.

## Components and instances

`daily/advanced`

Components позволяют создать reusable UI part, а instances использовать его в
дизайне без копирования структуры вручную.

Сильные фичи:

- main component;
- instances linked to main;
- overrides;
- variants;
- component properties;
- instance swap;
- slots;
- libraries and publishing.

Важно: Button, Input, Card, Dialog are usually components from a design system
or UI kit, not built-in Figma primitives.

## Variables and styles

`daily/advanced`

Styles and variables помогают держать дизайн системным.

Styles:

- text styles;
- color styles;
- effect styles;
- grid styles.

Variables:

- color, number, string, boolean values;
- collections;
- modes;
- aliases;
- mode switching for themes, density, locale and device contexts;
- prototype state.

## Comments

`daily`

Comments нужны для review and collaboration. Их можно оставлять в design files
and prototypes, pin к frame/layer/canvas coordinate, отвечать в threads and
mention collaborators.

Основные действия:

- enter comment mode;
- place comment pin or region;
- reply/resolve;
- search/filter comments;
- click comment to navigate to its location;
- hide/show comment pins.

## Prototyping

`daily/advanced`

Figma prototypes превращают static frames в clickable flows.

Базовая модель:

- trigger;
- action;
- destination;
- animation.

Частые actions:

- navigate to;
- open overlay;
- swap overlay;
- back/close overlay;
- open link;
- set variable;
- change to variant.

## Dev Mode and export

`advanced`

Dev Mode помогает разработчикам inspect design and assets.

Ключевые возможности:

- inspect selected layers;
- measurements and spacing;
- code output;
- typography and color values;
- export selected assets;
- annotations and measurements;
- compare changes where available;
- plugins and integrations.

## AI and new canvas tools

`new/cool`

Современная Figma включает AI and new products around design:

- Figma agent in design files;
- First Draft;
- Make/Edit image;
- Figma Make for functional prototypes and web apps;
- Figma Sites for responsive websites;
- Figma Buzz for branded marketing assets;
- Figma Draw for expressive illustration;
- Figma Motion for keyframed animation.

Почему это важно: Figma перестает быть только static UI mockup tool and becomes
a broader canvas for design, motion, code, AI and production workflows.
