# Мини-книжка: базовые и крутые Figma-фичи

Дата сверки: 2026-07-07. Источники собраны в [sources.md](sources.md).

Эта книжка - Figma-first каталог возможностей. Цель: собрать в одном месте
базовые, ежедневные и продвинутые фичи Figma, чтобы понимать, что умеет
инструмент и какие идеи можно заимствовать для своих продуктов.

## Как читать

Каждая глава отвечает на практические вопросы:

- какая фича есть в Figma;
- для чего она нужна;
- какие основные настройки и параметры у нее есть;
- почему пользователи считают ее базовой или сильной;
- какие смежные фичи стоит изучить рядом.

Фичи помечаются простыми статусами:

- `base` - фундаментальные возможности, без которых трудно работать в Figma;
- `daily` - ежедневные фичи для UI/product design;
- `advanced` - мощные возможности для дизайн-систем, прототипов и handoff;
- `new/cool` - новые или особенно выразительные возможности Figma ecosystem.

## Оглавление

0. [Проверенная карта фич](00-figma-feature-audit.md) - короткий список
   Figma-возможностей с подтверждением по официальным источникам.
1. [Ежедневные Figma-функции](01-daily-figma-features.md) - базовый словарь:
   canvas, frames, layers, components, text, comments, prototypes.
2. [Layout и responsive design](02-layout-model.md) - frames, Auto layout,
   grid, wrap, constraints, guides, resizing, clipping.
3. [Компоненты, variants и дизайн-системы](03-components-and-props.md) -
   components, instances, properties, slots, variables, styles, libraries.
4. [Текст, контент и typography](04-text-and-content.md) - text layers,
   styles, truncation, links, lists, variables, content workflows.
5. [Структура, collaboration и handoff](05-structure-inspector.md) - layers,
   pages, sections, comments, version history, Dev Mode, annotations.
6. [Visual assets, AI и новые продукты](06-visual-assets-handoff.md) -
   vectors, images, Figma Draw, Motion, Make, Sites, Buzz, Slides, AI.
7. [Semantic Layout Markdown с i18n](semantic-layout-markdown-i18n.md) -
   semantic extraction, language-neutral IR and i18n resources.
8. [Аудит полноты SLM относительно Figma-фич](slm-feature-completeness-audit.md) -
   проверка, может ли SLM описать любой Figma screen.
9. [Источники](sources.md) - официальные ссылки Figma.

## Большая карта фич

| Группа | Фичи | Статус |
| --- | --- | --- |
| Canvas basics | Files, pages, canvas, frames, sections, layers, groups | base |
| Layout | Auto layout, vertical/horizontal flow, wrap, grid, padding, gap, alignment, resizing | daily |
| Responsive behavior | Hug contents, fill container, fixed size, min/max, constraints, layout guides | daily/advanced |
| Design systems | Components, instances, variants, component properties, slots, libraries | daily/advanced |
| Tokens | Variables, collections, modes, styles, text styles, color styles, effect styles | advanced |
| Text | Text layers, typography properties, truncation, max lines, links, lists | daily |
| Visual design | Shapes, vector networks, boolean operations, strokes, fills, gradients, effects, masks | daily/advanced |
| Prototyping | Interactions, prototype connections, overlays, smart animate, scroll/overflow, variables | daily/advanced |
| Collaboration | Multiplayer editing, comments, mentions, version history, branches, sharing | daily |
| Handoff | Dev Mode, inspect, measurements, annotations, export settings, code/connectors | advanced |
| AI | Figma agent, First Draft, Make/Edit image, rename layers, search and helper tools | new/cool |
| New products | Figma Make, Sites, Buzz, Slides, Draw, Motion | new/cool |

## Принцип отбора

В книгу попадают не только "самые часто используемые" функции, но и сильные
идеи Figma:

- дизайн как структура, а не набор координат;
- компоненты как понятный API для дизайнеров;
- tokens/variables как источник правды;
- prototypes как быстрый способ проверить поведение;
- comments and Dev Mode как часть handoff;
- AI, Make, Sites, Draw and Motion как расширение canvas beyond static mockups.
