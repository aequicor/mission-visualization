# Структура, collaboration и handoff

[← Оглавление](README.md)

Figma-файл ценен не только тем, что находится на canvas, но и тем, как он
организован, обсуждается и передается дальше. Эта глава собирает фичи для
structure, selection, review, versioning and developer handoff.

## Pages

`base`

Pages разделяют части файла.

Типичные pages:

- Cover;
- Exploration;
- Flows;
- Components;
- Design system;
- Handoff;
- Archive;
- Playground.

Good practice:

- keep final flows separate from messy exploration;
- name pages clearly;
- avoid one endless unstructured canvas;
- preserve archive/history without polluting handoff.

## Sections

`daily`

Sections group frames and canvas areas.

Use cases:

- organize user flow;
- mark review-ready work;
- create phases such as Draft, Ready, Archive;
- group platform variants;
- create stakeholder-friendly review areas.

## Layers panel

`base/daily`

Layers panel is the structural map of a design file.

What it shows:

- layer type icon;
- layer name;
- nesting;
- order;
- components/instances;
- frames/groups/sections;
- visibility and lock state;
- selection and hover highlight.

Why it matters:

- layer order explains overlap;
- naming improves handoff;
- nested selection is easier;
- smart animate depends on matching layers;
- developers inspect structure in Dev Mode.

## Layer order and z-order

`base`

Layers higher in the Layers panel appear above layers below them on canvas.

Use cases:

- text over image;
- overlay over card;
- badge over avatar;
- modal over background;
- annotation pins.

Rule of thumb: if overlap matters, layer order should be obvious.

## Selection and deep selection

`daily`

Figma supports selecting objects from canvas or Layers panel, including nested
objects.

Selection tools:

- click on canvas;
- double-click/Enter to go into nested layers;
- deep select with modifier key;
- Select layer menu;
- Layers panel selection;
- multi-select with Shift or modifier keys;
- select matching layers.

Why it is powerful: complex nested UI remains editable without detaching or
flattening everything.

## Naming layers

`daily`

Layer names are not decoration. They affect search, handoff, smart animate,
component readability and team communication.

Good naming:

- `Header / Desktop`;
- `Button / Primary / Default`;
- `Card / Pricing`;
- `Icon / Search`;
- `Table row / Selected`;
- `Modal / Confirm delete`.

Bad naming:

- `Rectangle 432`;
- `Frame 189`;
- `Group 12`;
- `Copy of Copy`.

## Comments

`daily`

Comments make review possible inside the file or prototype.

Capabilities:

- add comments to files and prototypes;
- pin to a canvas location;
- pin to a frame or layer;
- select a region;
- @mention collaborators or groups;
- reply in threads;
- resolve discussions;
- view comments in sidebar;
- sort and filter comments;
- hide/show comment pins.

Use cases:

- design critique;
- stakeholder feedback;
- copy review;
- accessibility notes;
- implementation questions;
- QA on prototypes.

## Version history

`daily/advanced`

Version history lets teams inspect and restore earlier file states.

Use cases:

- recover accidentally deleted work;
- compare earlier iterations;
- preserve milestone checkpoints;
- share a past version;
- audit what changed over time.

What to remember:

- restoring is non-destructive;
- comments have their own behavior across versions;
- named versions are easier to understand later.

## Branching and file workflows

`advanced`

Depending on plan/workspace setup, teams can use branching-like workflows or
controlled file organization to isolate risky changes.

Use cases:

- experiment with design-system changes;
- propose component updates;
- separate WIP from production library;
- review changes before publishing.

## Sharing and permissions

`daily`

Figma files are collaborative. Sharing is part of the design workflow.

Common controls:

- can view;
- can comment;
- can edit;
- team/project access;
- public or restricted links;
- prototype sharing;
- presentation view.

## Dev Mode

`advanced`

Dev Mode is a developer-focused interface for inspecting and navigating designs.

Core uses:

- inspect selected layer;
- measure spacing;
- view properties;
- copy code-like values;
- export assets;
- inspect typography;
- view annotations;
- compare changes where available;
- run Dev Mode plugins.

Why it matters: Dev Mode turns design file structure into implementation
information.

## Measurements and annotations

`advanced`

Annotations and measurements let designers communicate context, specs and
implementation intent.

Use cases:

- explain responsive behavior;
- mark component states;
- document edge cases;
- clarify content behavior;
- call out accessibility requirements;
- leave implementation notes.

## Export settings

`daily/advanced`

Export settings define which assets should be exported and in what format.

Common formats:

- PNG;
- JPG;
- SVG;
- PDF.

Useful settings:

- export scale;
- suffix;
- format;
- color profile where available;
- selected layer/frame export;
- multiple export configurations.

## Developer handoff checklist

- Frames and components are named clearly.
- Layers are organized and not random.
- Components use variants/properties instead of detached copies.
- Responsive behavior is visible through Auto layout/constraints.
- Comments and annotations explain open questions.
- Exportable assets have export settings.
- Text and colors use styles/variables.
- Prototype flow demonstrates interaction.
- Dev Mode inspection has enough structure to be useful.

## Источники

- [Layers 101: Get started with layers](https://help.figma.com/hc/en-us/articles/26584819173271-Layers-101-Get-started-with-layers)
- [Layers 101: Explore layer types](https://help.figma.com/hc/en-us/articles/26620239826199-Layers-101-Explore-layer-types)
- [Select layers and objects](https://help.figma.com/hc/en-us/articles/360040449873-Select-layers-and-objects)
- [Parent, child, and sibling relationships](https://help.figma.com/hc/en-us/articles/360039959014-Parent-child-and-sibling-relationships)
- [Rename Layers](https://help.figma.com/hc/en-us/articles/360039958934-Rename-Layers)
- [Add comments to files](https://help.figma.com/hc/en-us/articles/360041068574-Add-comments-to-files)
- [Move or edit comments](https://help.figma.com/hc/en-us/articles/360041547853-Move-or-edit-comments)
- [View a file's version history](https://help.figma.com/hc/en-us/articles/360038006754-View-a-file-s-version-history)
- [Guide to Dev Mode](https://help.figma.com/hc/en-us/articles/15023124644247-Guide-to-Dev-Mode)
- [Guide to inspecting](https://help.figma.com/hc/en-us/articles/22012921621015-Guide-to-inspecting)
- [Add measurements and annotate designs](https://help.figma.com/hc/en-us/articles/20774752502935-Add-measurements-and-annotate-designs)
- [Export static designs from Figma](https://help.figma.com/hc/en-us/articles/360040028114-Export-static-designs-from-Figma)
