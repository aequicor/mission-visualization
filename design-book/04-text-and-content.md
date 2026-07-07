# Текст, контент и typography

[← Оглавление](README.md)

Text in Figma is not only words on the canvas. It is typography, layout,
content modeling, truncation, links, lists, styles, variables and handoff.

## Text layers

`base/daily`

Text layer is the basic object for written content.

Use cases:

- headings;
- body copy;
- labels;
- captions;
- buttons;
- table cells;
- annotations;
- prototype copy;
- links.

Basic operations:

- create text with Text tool;
- edit content inline;
- resize text box;
- apply typography;
- apply text style;
- convert text to vector paths where needed;
- inspect text in Dev Mode.

## Typography properties

`daily`

Figma exposes text-specific properties in the right sidebar.

Important settings:

| Property | Для чего нужна |
| --- | --- |
| Font family | Typeface selection |
| Font weight/style | Regular, medium, bold, italic and other family styles |
| Font size | Text scale |
| Line height | Vertical rhythm between lines |
| Letter spacing | Tracking between characters |
| Horizontal alignment | Left, center, right, justify |
| Vertical alignment | Top, middle, bottom inside fixed text box |
| Paragraph spacing | Space between paragraphs |
| Indentation | Paragraph and list formatting |
| Decoration | Underline, strikethrough |
| Letter case | Uppercase, lowercase, capitalize, small caps |
| Lists | Bulleted and numbered lists |
| OpenType features | Font-specific advanced typography |
| Variable font settings | Weight, width, optical size, slant where available |

## Text resizing

`daily`

Text boxes can resize differently depending on the intended layout.

Common modes:

- auto width;
- auto height;
- fixed size;
- hug behavior inside Auto layout;
- fill behavior inside Auto layout.

Why it matters:

- fixed text boxes can overflow;
- auto height supports dynamic body text;
- button labels often need max lines or truncation;
- table cells need predictable bounds.

## Truncation and max lines

`daily`

Figma supports truncating text and setting maximum line count.

Use cases:

- one-line button labels;
- card title capped at two lines;
- table cell ellipsis;
- feed/list previews;
- navigation labels.

What to decide:

- max number of lines;
- whether overflowing text truncates;
- whether text wraps;
- whether layout should grow instead.

## Text styles

`daily/advanced`

Text styles let teams reuse typography choices.

Common style roles:

- Display;
- H1/H2/H3;
- Body;
- Label;
- Caption;
- Code/mono;
- Link;
- Button.

Text styles can include many typography properties, but not all properties.
For example, color/fill and resizing behavior are not part of text styles in
the same way as font and spacing properties.

## Rich text inside one layer

`daily`

A single text layer can contain mixed styling, such as bold phrase, underlined
link or different emphasis in a paragraph.

Useful for:

- inline links;
- legal copy;
- help text;
- product descriptions;
- marketing content.

Tradeoff: too much mixed styling can make systematic typography harder.

## Links in text

`daily`

Figma supports links in text layers. They can point to external URLs, frames,
pages, files and prototypes.

Use cases:

- documentation links;
- prototype navigation;
- source/reference notes;
- production-like link states.

## Lists

`daily`

Text can include bulleted and numbered lists.

Use cases:

- onboarding copy;
- feature lists;
- release notes;
- instructions;
- documentation blocks.

Important settings:

- list type;
- indentation;
- paragraph spacing;
- hanging lists behavior.

## Content and component properties

`advanced`

Text properties in components make content editable from instance controls.

Examples:

- Button label;
- Card title;
- Empty state body;
- Input label;
- Badge text;
- Menu item label.

This is one of the strongest Figma design-system ideas: content can be exposed
as a named control rather than requiring users to deep-select a nested text
layer.

## String variables

`advanced`

String variables can store reusable text values and work with modes.

Use cases:

- locale-specific copy;
- repeated product names;
- status labels;
- prototype state messages;
- shared labels across frames.

## Text in prototypes

`advanced`

Text can become dynamic in prototypes through variables and interactions.

Examples:

- changing label based on state;
- form-like prototype values;
- counter text;
- localized prototype preview;
- conditional content.

## Dev Mode text handoff

`advanced`

Dev Mode helps inspect text properties and content.

Useful details:

- font family;
- size;
- weight;
- line height;
- letter spacing;
- color;
- copyable content;
- code output depending on target.

## Text QA checklist

- Does the text wrap as intended?
- Are long labels safe?
- Are max lines/truncation intentional?
- Are text styles reused?
- Are link styles visible?
- Are localization and longer strings considered?
- Is text readable in light/dark modes?
- Are text layer names meaningful for handoff?

## Источники

- [Explore text properties](https://help.figma.com/hc/en-us/articles/360039956634-Explore-text-properties)
- [Create and apply text styles](https://help.figma.com/hc/en-us/articles/360039957034-Create-and-apply-text-styles)
- [Add links to text](https://help.figma.com/hc/en-us/articles/360045942953-Add-links-to-text)
- [Overview of variables, collections, and modes](https://help.figma.com/hc/en-us/articles/14506821864087-Overview-of-variables-collections-and-modes)
- [Use variables in prototypes](https://help.figma.com/hc/en-us/articles/14506587589399-Use-variables-in-prototypes)
