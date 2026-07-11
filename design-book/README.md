# Мини-книжка: базовые и крутые Figma-фичи

Дата сверки: 2026-07-08. Источники собраны в [sources.md](sources.md).

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
7. [Application uses: рабочее пространство редактора](07-editor-application-use.md) -
   панели, canvas, resize стыков, minimize/hide UI, focus mode and zoom.
8. [Layout: поведение layout-редактирования](08-editor-layout.md) -
   auto layout, direction, gap, padding, alignment, resizing and constraints.
9. [Frames: экраны, контейнеры и вложенность](09-editor-frames.md) -
   screen frames, nested frames, clip content, resize and frame properties.
10. [Objects: выбор, перемещение и слои](10-editor-objects.md) -
   hover, selection, move, resize, multi-selection, layers and object creation.
11. [Vector: shape, path and point editing](11-editor-vector.md) -
   shape tools, pen, vector edit mode, points, paths, lasso, cut and paint.
12. [Position: coordinates, dimensions, rotation and order](12-editor-position.md) -
   X/Y, W/H, nudge, scale, rotation, flip, z-order and guides.
13. [Appearance: opacity, effects, blend, radius and styles](13-editor-appearance.md) -
   layer opacity, blend modes, effects, radius, smoothing and style references.
14. [Fill: colors, gradients, images and patterns](14-editor-fill.md) -
   fill rows, color picker, gradients, image fill, patterns and variables.
15. [Stroke: borders, outlines, caps and dashed styles](15-editor-stroke.md) -
   stroke fill, weight, position, individual sides, endpoints, joins and dashes.
16. [Typography: text layers and type controls](16-editor-typography.md) -
   text creation/editing, typography controls, resizing, truncation and styles.
17. [Ресерч: управление в Figma](17-figma-control-research.md) -
   workspace controls, canvas navigation, selection, drag/resize, layers,
   inspector, color, text, vector, prototype and keyboard control patterns.
18. [Прокаченные позиционирование и preview](18-editor-advanced-positioning-preview.md) -
   Figma-like alignment, X/Y drag, constraints, rotation, dimensions, selected
   overlay and Alt measurements.
19. [Canvas and Scene: интерактивность, переходы и animation runtime](19-editor-canvas-scene-runtime.md) -
   pipeline SML/SLM -> IR -> Canvas/Scene, разные renderers, scene timeline,
   event runtime, transitions and animation control.
20. [Semantic Layout Markdown с i18n](semantic-layout-markdown-i18n.md) -
   semantic extraction, language-neutral IR and i18n resources.
21. [Аудит полноты SLM относительно Figma-фич](slm-feature-completeness-audit.md) -
   проверка, может ли SLM описать любой Figma screen.
22. [Формат sidecar-файла аннотаций](annotations-sidecar-format.md) -
   review-слой note/issue: грамматика заголовка, тело/картинка, толерантный
   парсер, round-trip writer и хирургический патчер.
23. [Источники](sources.md) - официальные ссылки Figma.

## Большая карта фич

| Группа | Фичи | Статус |
| --- | --- | --- |
| Canvas basics | Files, pages, canvas, frames, sections, layers, groups | base |
| Application uses | Panels, resizable sidebars, minimized/hidden UI, focus mode, zoom and canvas navigation | base/daily |
| Layout | Auto layout, vertical/horizontal/grid flow, padding, gap, alignment, resizing, constraints | daily |
| Responsive behavior | Hug contents, fill container, fixed size, min/max, constraints, layout guides | daily/advanced |
| Frames | Top-level screens, nested frames, clip content, frame properties, export targets | base/daily |
| Objects | Selection, hover, move, resize, multi-selection, layers, visibility, lock | base/daily |
| Position | X/Y, W/H, nudge, scale, rotation, flip, z-order, guides | base/daily |
| Advanced positioning preview | Inspector alignment, free X/Y drag, constraints, selected overlay, dashed center lines, Alt distance measurement | daily/advanced |
| Canvas/Scene runtime | Static canvas editing, scene mode, timeline, transitions, snapshots, animation sampling and trace | advanced |
| Design systems | Components, instances, variants, component properties, slots, libraries | daily/advanced |
| Tokens | Variables, collections, modes, styles, text styles, color styles, effect styles | advanced |
| Typography | Text layers, typography properties, text resize, truncation, max lines, links, lists | daily |
| Appearance | Opacity, blend modes, shadows, blur, effects, radius, smoothing, styles | daily/advanced |
| Fill | Solid colors, gradients, patterns, images, videos, multiple fills, variables | daily/advanced |
| Stroke | Stroke fills, weight, position, individual sides, caps, joins, dashed styles | daily/advanced |
| Vector | Shapes, vector networks, pen, point/path editing, boolean operations, masks | daily/advanced |
| Prototyping | Interactions, prototype connections, overlays, smart animate, scroll/overflow, variables | daily/advanced |
| Control research | Workspace, canvas, tools, selection, transforms, panels, shortcuts and modes as one interaction system | base/advanced |
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
