# Typography: text layers and type controls

[← Оглавление](README.md)

Эта глава описывает UX для text and typography: text creation, editing mode,
font controls, line height, letter spacing, alignment, text resizing, truncation,
lists, OpenType features, variable fonts and text styles.

Figma reference:

- [Guide to text in Figma Design](https://help.figma.com/hc/en-us/articles/360039956434-Guide-to-text-in-Figma-Design)
- [Explore text properties](https://help.figma.com/hc/en-us/articles/360039956634-Explore-text-properties)
- [Create and apply text styles](https://help.figma.com/hc/en-us/articles/360039957034-Create-and-apply-text-styles)
- [Convert text to vector paths](https://help.figma.com/hc/en-us/articles/360047239073-Convert-text-to-vector-paths)

## UX-модель Figma

`daily`

Text layer in Figma is both a canvas object and editable content. Typography
properties live in the right sidebar, while text content is edited directly on
canvas.

Mission Editor should preserve that split:

- canvas for writing/editing text;
- inspector for typography properties;
- source/document for durable content;
- i18n resources for translated copy later.

## Create text

`base`

Tool behavior:

- select Text tool;
- click canvas creates auto-width text;
- drag canvas creates fixed-width text box;
- newly created text enters edit mode;
- placeholder text is selected and ready to replace;
- Escape exits text edit mode.

UX:

- cursor changes to text cursor over text insertion area;
- after creation, inspector shows Typography;
- text layer appears in Layers panel;
- if created inside screen/frame, it becomes child of that frame.

## Edit text content

`base`

Entering edit mode:

- double click text layer;
- press Enter when text selected;
- create a new text layer;
- context action `Edit text`.

Editing behavior:

- caret visible;
- text selection works inside layer;
- keyboard shortcuts behave as text editing while caret active;
- click outside commits and exits;
- Escape exits without losing layer selection;
- undo separates content edits from object transforms.

Important distinction:

- Cmd/Ctrl+A inside text edit selects text;
- Cmd/Ctrl+A outside text edit selects objects on canvas/screen.

## Typography section

`daily`

Common controls:

- text style picker;
- font family;
- font weight/style;
- font size;
- line height;
- letter spacing;
- horizontal alignment;
- vertical alignment;
- more/type settings button.

UX:

- controls appear only when text layer or text selection is active;
- multi-selection shows common values or `Mixed`;
- hover preview can show type setting where useful;
- invalid font shows missing font warning;
- variable font exposes variable axes in advanced panel.

## Type settings panel

`advanced`

Detailed settings:

- underline/strikethrough;
- letter case;
- vertical trim;
- numbered/bulleted lists;
- paragraph spacing;
- truncation;
- maximum number of lines;
- paragraph indentation;
- hanging quotes/lists;
- number styles;
- OpenType features;
- variable font axes.

UX:

- type settings opens as focused panel/popover;
- settings are grouped into Basics, Details, Variable where applicable;
- preview area shows effect of hovered property;
- changes update canvas live;
- Escape closes panel;
- unsupported font features disabled or hidden.

## Text dimensions and resizing

`daily`

Text has content and bounds.

Modes:

- auto width;
- auto height;
- fixed size;
- fill container in auto layout;
- hug content in auto layout.

Canvas:

- resize width changes wrapping;
- auto height grows with content;
- fixed height may show overflow/truncation indicator;
- side handles adjust text box width;
- bottom handle can adjust height when fixed;
- double click handle can switch to fit/hug if implemented.

Inspector:

- W/H fields;
- resizing mode;
- max lines/truncate;
- overflow indication.

## Text fill, stroke and effects

`daily`

Text can have visual properties too.

Rules:

- text fill changes glyph color;
- text stroke outlines glyphs;
- effects apply to text layer;
- background behind text requires frame/container fill;
- converting text to vector paths is destructive for text editability.

UX:

- if user asks for text background, UI can suggest `Wrap in frame`;
- `Convert to vector` must warn that content and typography will no longer be
  editable as text;
- text style should not hide local visual overrides.

## Text on path

`advanced`

Figma supports text following vector paths.

Mission Editor can treat this as advanced:

- select Text on path tool;
- hover vector path until text-on-path cursor appears;
- click to add text on the path;
- blue handle controls start position;
- action to flip orientation;
- path and text relationship remains visible in layers.

## Text styles and variables

`advanced`

Text styles are reusable typography presets.

UX:

- style picker in Typography section;
- applied style name visible;
- create style from selected text;
- update style from current selection;
- detach style;
- local overrides highlighted;
- style description shown on hover.

For i18n-oriented Mission Editor:

- content key should be separate from text style;
- text layer can show localized preview;
- overflow/truncation should be tested per locale;
- text style remains language-neutral.

## Typography checklist

- click creates auto-width text;
- drag creates fixed-width text box;
- double click enters text edit mode;
- Escape exits edit mode while preserving selection;
- typography controls cover font, weight, size, line height, spacing and align;
- advanced type settings include lists/truncation/OpenType where supported;
- text resize changes wrapping predictably;
- text fill is distinct from text background;
- text style references and overrides are visible;
- destructive text-to-vector conversion is explicit.
