---
name: compose-slm
description: Generate a UI screen as Semantic Layout Markdown (SLM) using its controlled natural language (CNL). Use when asked to create, improve, or convert a .layout.md / SLM screen — turn a screen description into natural-language element sentences (RU or EN) that compile to a renderer-independent UI IR. Works with any model (DeepSeek, Codex, …).
---

# Write SLM screens in natural language (CNL)

You are given a **screen description** and must output an **SLM document**: Markdown whose
elements are written as short **sentences** — a noun plus `keyword value` phrases — not YAML.

> **Прямоугольник 120 на 15 цвет #00B843 радиус 15 паддинги 10 отступ 16**
> → a rectangle, 120×15, fill #00B843, corner radius 15, padding 10, gap 16.

The compiler turns each sentence into typed UI properties. Write it so it reads like an
annotated wireframe. Keywords work in **Russian or English** — pick one and be consistent.

## The shape of a screen (copy this skeleton)

```md
---
screen: <stableId>
sourceLocale: ru-RU
targetLocales: [ru-RU, en-US]
frame: { preset: desktop-1440, width: 1440, height: 1024 }
---

# <Screen name>

## <Container name> <container properties>

<element sentence>
<element sentence>

## <Another container> <container properties>

<element sentence>
```

Rules of the shape:
- **Frontmatter** (between `---`) sets screen id, locales and the artboard size. Keep it on
  few lines using `{ ... }` and `[ ... ]` as shown.
- **`#` H1** = the screen title.
- **`##` / `###` headings = containers** (frames/sections). A heading may end with container
  properties (layout/size/style) — see below. Deeper headings (`###`) nest inside shallower ones.
- **Each element is ONE line** — a sentence. One line, one element. Never wrap an element
  across lines.
- Blank line between a container heading and its children, and between containers.

## Elements: noun + properties

Start a line with an element noun, then add property phrases in any order.

| Noun (RU) | Noun (EN) | Element |
|---|---|---|
| прямоугольник | rectangle / rect | rectangle shape |
| эллипс, круг | ellipse, circle | ellipse shape |
| линия | line | line |
| звезда | star | star |
| многоугольник | polygon | polygon |
| стрелка | arrow | arrow |
| текст, надпись | text, label | text |
| кнопка | button | button (labeled control) |
| контейнер, фрейм, группа | frame, group, container | frame / group |
| изображение, картинка | image | image / media |
| иконка | icon | vector / icon |

## Property phrases

Values are numbers, `#hex` colors, `$token` refs, or enum words. Text goes in `«…»` or `"…"`.

| Meaning | Russian | English | Example |
|---|---|---|---|
| size | `<w> на <h>` | `<w> by <h>` / `<w> x <h>` | `120 на 15` |
| width / height | `ширина N` / `высота N` | `width N` / `height N` | `ширина 320` |
| fill color | `цвет <c>` / `заливка <c>` | `color <c>` / `fill <c>` | `цвет #00B843` |
| stroke | `обводка <c> [w] [inside\|outside\|center]` | `stroke <c> …` | `обводка #CBD5E1 1 inside` |
| corner radius | `радиус N` / `скругление N` | `radius N` | `радиус 12` |
| rotation | `N градусов` / `поворот N` | `rotate N` | `30 градусов` |
| padding | `паддинги N` (1/2/4 numbers) | `padding N` | `паддинги 16` · `паддинги 12 24` |
| gap (spacing) | `отступ N` / `промежуток N` | `gap N` | `отступ 16` |
| direction | `колонка` · `строка` · `сетка` · `свободно` | `column` · `row` · `grid` · `free` | `колонка` |
| align in parent | `родительский контейнер вверх\|вниз\|влево\|вправо\|центр` | `align top\|bottom\|left\|right\|center` | `родительский контейнер вверх` |
| position (free) | `позиция X Y` / `координаты X Y` | `position X Y` / `at X Y` | `позиция 72 96` |
| opacity | `прозрачность N` | `opacity N` | `прозрачность 0.8` |
| font size (text) | `размер N` | `size N` | `размер 20` |
| bold / italic (text) | `жирный` / `курсив` | `bold` / `italic` | `жирный` |

Container headings take the SAME layout/style phrases after the name:

```md
## Панель миссий колонка отступ 16 паддинги 24 цвет #FFFFFF радиус 12
```
→ a frame named "Панель миссий", vertical layout, gap 16, padding 24, white fill, radius 12.

## Colors, tokens, units

- Colors: `#RRGGBB`, `#RGB`, `#RRGGBBAA` — **quotes are NOT required** (`цвет #1E293B` is fine).
- Design tokens (theme variables): `$color.accent`, `$space.4` — a leading `$`, no quotes.
- Numbers are plain (`16`, `0.8`, `320`). No units, no `px`.
- Text content is the only localized part; put it in `«…»` (RU) or `"…"`. Everything else
  (numbers, colors, tokens, enum words) is language-neutral and never translated.

## Two complete examples (both compile clean)

Mission card (Russian):

```md
---
screen: missionCard
sourceLocale: ru-RU
targetLocales: [ru-RU, en-US]
frame: { preset: desktop-1440, width: 1440, height: 1024 }
---

# Mission Card

## Карточка миссии колонка отступ 12 паддинги 16 цвет #FFFFFF радиус 12

Текст «Активные миссии» размер 20 жирный цвет #0F172A
Текст «12 в работе» размер 14 цвет #64748B
Прямоугольник 320 на 4 цвет #2563EB радиус 2
Кнопка «Открыть» цвет #2563EB
```

Status chips (English):

```md
---
screen: statusChips
sourceLocale: en-US
targetLocales: [en-US]
frame: { preset: desktop-1440, width: 1440, height: 1024 }
---

# Status Chips

## Chips row gap 8 padding 8

Rectangle 80 by 24 color #DCFCE7 radius 12
Rectangle 80 by 24 color #FEE2E2 radius 12
Text "Nominal" size 12 color #166534
```

## DO / DON'T (tuned for smaller models)

DO:
- One element per line; keep each line a single sentence.
- Start every element line with a known noun; start containers with `##`/`###`.
- Put visible text in `«…»` / `"…"`; put a name before any container properties.
- Use bare `#hex` for colors; use one keyword language (RU or EN) throughout a screen.

DON'T:
- Don't wrap an element onto a second line, and don't indent with tabs.
- Don't invent property words — only those in the tables above are recognized.
- Don't put numbers with no keyword (a bare `42` is an error — attach it, e.g. `радиус 42`).
- Don't start a container heading with a property word (name it first: `## Панель колонка`).

## Error catalog (self-check)

If the compiler warns `[CNL:<id>] … Как исправить: …`, fix per the id:

| id | Meaning | Fix |
|---|---|---|
| `unknown-keyword` | a word isn't a known property | check spelling; use a word from the tables |
| `missing-value` | keyword has no value | add the value, e.g. `цвет #00B843` |
| `bad-color` | value after `цвет`/`color` isn't a color | use `#hex` or `$token` |
| `bad-number` | value should be a number | e.g. `радиус 15`, `отступ 16` |
| `incomplete-size` | size needs two numbers via `на`/`by` | `120 на 15` |
| `unterminated-text` | quotes not closed | close `«…»` or `"…"` |
| `bad-direction` | not a valid direction | `вверх\|вниз\|влево\|вправо\|центр` |
| `stray-number` | a number not attached to a property | attach it, e.g. `размер 120 на 15` |

## Escape hatch (rarely needed)

CNL covers the common visual grammar. For anything it doesn't express (gradients, effects,
component instances, vector paths, interactions, motion), fall back to explicit YAML typed
blocks under the element/heading (they coexist with CNL and take precedence). The full typed
contract is `design-book/semantic-layout-markdown-i18n.md`; the completeness checklist is
`design-book/slm-feature-completeness-audit.md`.

## Validation

When working in the repository, compile and check for zero error diagnostics:

```bash
./gradlew :engine:frontend:jvmTest --tests "*Cnl*"
./gradlew :engine:frontend:jvmTest
```

A good SLM output parses with no error diagnostics: stable `screen` id, one element per line,
known nouns/keywords, closed quotes, colors as `#hex`/`$token`, and a clear container hierarchy.
