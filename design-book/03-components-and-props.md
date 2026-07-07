# Компоненты, variants и дизайн-системы

[← Оглавление](README.md)

Figma сильна не только canvas editing, но и design-system workflow. Components,
instances, variants, properties, slots, styles, variables and libraries делают
UI повторяемым, управляемым and scalable.

## Components

`daily`

Component - reusable design object. Main component задает исходную структуру,
instances используют эту структуру в дизайне.

Что можно делать:

- create component from selected layers;
- create main component;
- place instances;
- override instance content and properties;
- reset overrides;
- detach instance when связь больше не нужна;
- publish component to library;
- update instances when main component changes.

Когда использовать:

- buttons;
- inputs;
- cards;
- icons;
- nav items;
- modals;
- table rows;
- repeated marketing blocks.

Важно: Figma не имеет built-in Button/Card/Input как HTML controls. Обычно это
components from your team library, UI kit or community file.

## Instances

`daily`

Instance - linked copy of a main component. Она наследует structure, но может
иметь overrides.

Типы overrides:

- text content;
- visibility via boolean property;
- nested instance swap;
- variant value;
- style changes where allowed;
- slot content;
- size/layout changes allowed by component setup.

Почему важно: instances keep design consistent while allowing local changes.

## Variants

`daily/advanced`

Variants group similar components into a component set. Instead of separate
Button/Primary, Button/Secondary, Button/Disabled files, you can expose
properties such as `type`, `state`, `size`, `color`.

Параметры:

- property names;
- property values;
- default variant;
- component set;
- naming structure;
- unique combination for each variant;
- variant interactions for interactive components.

Typical variant axes:

- state: default, hover, pressed, disabled, loading;
- size: small, medium, large;
- type: primary, secondary, tertiary;
- tone: neutral, success, warning, danger;
- platform: desktop, mobile;
- theme: light, dark.

Совет: variants are powerful, but a huge variant matrix becomes hard to manage.
Use component properties and slots where they are simpler.

## Component properties

`advanced`

Component properties define what an instance user can change from the right
sidebar without digging into internal layers.

Figma property types:

| Property type | Для чего нужна | Typical use |
| --- | --- | --- |
| Boolean property | Toggle layer visibility | Show/hide icon, badge, helper text |
| Text property | Change text string | Button label, card title, empty state copy |
| Instance swap property | Swap nested instance | Change icon, avatar, illustration, nested button |
| Variant property | Choose variant value | Size, state, color, tone |
| Slot property | Add/rearrange content inside instance | Card body, modal content, repeating list items |

Крутая идея: component properties turn visual components into understandable
APIs for designers.

## Boolean properties

`daily`

Boolean property lets instance users hide/show a nested layer.

Use cases:

- button with optional icon;
- input with optional helper/error text;
- card with optional badge;
- list item with optional avatar;
- table row with optional action menu.

Параметры:

- property name;
- default value;
- connected layer visibility;
- optional variable binding.

## Text properties

`daily`

Text property exposes editable copy from nested text layers.

Use cases:

- button label;
- input label;
- empty state title;
- card metadata;
- notification text.

Параметры:

- property name;
- default string;
- connected text layer;
- optional string variable.

## Instance swap properties

`advanced`

Instance swap property lets users replace a nested component instance with
another preferred instance.

Use cases:

- change button icon;
- swap avatar;
- choose illustration;
- switch nested CTA;
- select product logo.

Параметры:

- nested instance;
- preferred values;
- default instance;
- library availability.

## Slot properties

`advanced/newer`

Slot is a flexible area inside a main component. It allows instance users to
add, resize and arrange content without detaching the component.

Great use cases:

- card with variable body;
- modal with custom content;
- task list with variable items;
- tab content;
- dashboard panel;
- marketing block with flexible media/content.

Параметры:

- slot frame/layer;
- allowed content;
- preferred instances;
- min/max number of layers;
- nested layout behavior.

Why it is cool: slots reduce detach and variant explosion.

## Exposed nested instances

`advanced`

Figma can expose nested instance properties at the top-level instance. This
helps designers change nested parts without deep-selecting internal layers.

Use cases:

- social card with nested avatar/button/icon;
- modal with nested action buttons;
- form field composed from subcomponents;
- navigation item with nested icon and badge.

## Styles

`daily/advanced`

Styles are reusable sets of design properties. They are useful when a single
named choice should apply across many layers.

Common style types:

- text styles;
- color styles;
- effect styles;
- layout grid styles.

Text styles can include typography settings such as font family, weight, size,
line height, letter spacing and several text details, but not every text layer
property belongs in a text style.

## Variables

`advanced`

Variables are reusable raw values. They can power themes, design tokens and
prototype logic.

Variable types:

- color;
- number;
- string;
- boolean.

Core concepts:

- collection;
- group;
- mode;
- alias;
- value per mode;
- variable binding to design properties.

Use cases:

- light/dark mode;
- density modes;
- mobile/desktop spacing;
- locale-dependent strings;
- design tokens;
- prototype state.

## Variable modes

`advanced`

Modes let the same variable have different values in different contexts.

Examples:

- `theme/light` and `theme/dark`;
- `density/comfortable` and `density/compact`;
- `platform/ios`, `platform/android`, `platform/web`;
- `locale/en`, `locale/de`, `locale/ja`;
- `brand/default`, `brand/campaign`.

Why it is powerful: one design can switch context without duplicating every
frame.

## Libraries

`advanced`

Libraries let teams publish and reuse components, styles and variables across
files.

Important workflows:

- publish main components;
- publish style/variable updates;
- review library changes;
- accept updates in consuming files;
- organize assets with names and descriptions;
- document usage and accessibility guidance.

## Design system hygiene

`advanced`

Good Figma systems are easy to use because they are named and structured well.

Checklist:

- Components have clear names.
- Variant properties use consistent axes.
- Boolean/text/swap properties have human-readable names.
- Slots are used for flexible content, not every tiny option.
- Variables have semantic names.
- Styles are not duplicated randomly.
- Components include documentation links where helpful.
- Layer names support Dev Mode and handoff.

## Источники

- [Explore component properties](https://help.figma.com/hc/en-us/articles/5579474826519-Explore-component-properties)
- [Create and use variants](https://help.figma.com/hc/en-us/articles/360056440594-Create-and-use-variants)
- [Use slots to build flexible components in Figma](https://help.figma.com/hc/en-us/articles/38231200344599-Use-slots-to-build-flexible-components-in-Figma)
- [Overview of variables, collections, and modes](https://help.figma.com/hc/en-us/articles/14506821864087-Overview-of-variables-collections-and-modes)
- [Guide to variables in Figma](https://help.figma.com/hc/en-us/articles/15339657135383-Guide-to-variables-in-Figma)
- [Modes for variables](https://help.figma.com/hc/en-us/articles/15343816063383-Modes-for-variables)
- [The difference between variables and styles](https://help.figma.com/hc/en-us/articles/15871097384471-The-difference-between-variables-and-styles)
