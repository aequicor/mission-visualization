# Проверенная карта Figma-фич

[← Оглавление](README.md)

Дата сверки: 2026-07-07.

Эта глава фиксирует, что каждая фича из книжки действительно существует в
Figma ecosystem. Источники - официальные Figma Help/Learn страницы и Figma
blog. Если фича находится в beta или относится к новому продукту, это отмечено
в описании.

## Core Figma Design

| Фича | Есть в Figma | Что это такое | Статус |
| --- | --- | --- | --- |
| Files, pages, canvas | Да | Рабочее пространство для дизайн-файлов, страниц и объектов на canvas. | base |
| Frames | Да | Контейнеры для экранов, секций, компонентов, артбордов и preset-размеров. | base |
| Layers panel | Да | Дерево объектов, порядок слоев, nesting, selection, hover highlight. | base |
| Sections | Да | Группировка областей canvas для организации больших файлов. | base |
| Shapes | Да | Rectangles, ellipses, polygons, lines and other basic drawing primitives. | base |
| Text layers | Да | Текстовые слои с typography, resizing, truncation and styling. | base |
| Images/videos as fills | Да | Медиа добавляются как fills к слоям, а не как отдельный image layer type. | base |
| Fills, strokes, effects | Да | Цвета, gradients, images, borders/strokes, shadows, blur and blend modes. | daily |
| Masks | Да | Слои могут ограничивать видимость других слоев. | daily |
| Vector networks | Да | Figma-specific vector paths for shapes, icons and illustrations. | advanced |
| Boolean operations | Да | Union, subtract, intersect and exclude for combining shapes. | daily |

## Layout

| Фича | Есть в Figma | Что это такое | Статус |
| --- | --- | --- | --- |
| Auto layout | Да | Layout на frames, который адаптируется к content changes. | daily |
| Vertical flow | Да | Children идут по y-axis. | daily |
| Horizontal flow | Да | Children идут по x-axis. | daily |
| Wrap | Да | Overflown horizontal Auto layout items переходят на следующую строку. | daily |
| Grid auto layout | Да | Two-dimensional Auto layout with rows, columns, gaps and spans. | advanced |
| Padding | Да | Внутренние отступы parent Auto layout frame. | daily |
| Gap between | Да | Расстояние или distribution между Auto layout children. | daily |
| Alignment | Да | Выравнивание children inside Auto layout frame. | daily |
| Hug contents | Да | Frame подстраивается под content. | daily |
| Fill container | Да | Child занимает доступное место в parent. | daily |
| Fixed size | Да | Размер остается заданным числом. | daily |
| Min/max dimensions | Да | Ограничения минимальной/максимальной ширины и высоты. | advanced |
| Ignore auto layout | Да | Child исключается из Auto layout flow and behaves like absolute-positioned object. | advanced |
| Constraints | Да | Правила resize behavior relative to parent frame. | daily |
| Layout grids/guides | Да | Columns, rows, grid guides and layout guides for alignment. | daily |

## Design systems

| Фича | Есть в Figma | Что это такое | Статус |
| --- | --- | --- | --- |
| Components | Да | Reusable design objects with main component and instances. | daily |
| Instances | Да | Copies linked to main component. | daily |
| Variants | Да | Component set variations such as size, state, color. | daily |
| Component properties | Да | Controls for instance customization. | advanced |
| Boolean property | Да | Toggle visibility of a layer in an instance. | daily |
| Text property | Да | Change text inside instance from component controls. | daily |
| Instance swap property | Да | Swap nested instances. | advanced |
| Variant property | Да | Pick a variant value such as state or size. | daily |
| Slot property | Да | Flexible content area inside a component instance without detach. | advanced |
| Exposed nested instances | Да | Surface nested instance properties at the top-level component. | advanced |
| Libraries | Да | Publish and consume shared components/styles/variables. | advanced |
| Styles | Да | Reusable style definitions such as text, color and effects. | daily |
| Variables | Да | Reusable raw values for color, number, string and boolean. | advanced |
| Variable modes | Да | Context-specific values such as light/dark, locale, density, device. | advanced |

## Prototyping

| Фича | Есть в Figma | Что это такое | Статус |
| --- | --- | --- | --- |
| Prototype connections | Да | Lines between objects/frames with interaction details. | daily |
| Triggers/actions/destination | Да | Core prototype model: when something happens, do an action to a destination. | daily |
| Navigate to | Да | Move to another frame. | daily |
| Open overlay | Да | Show frame as overlay. | daily |
| Swap overlay | Да | Replace one overlay with another. | advanced |
| Smart animate | Да | Animate matching layers between frames. | advanced |
| Scroll and overflow behavior | Да | Prototype scrolling, fixed elements and overflow handling. | advanced |
| Variables in prototypes | Да | Store state and change UI with interactions, expressions and conditionals. | advanced |
| Interactive components | Да | Component variants can have prototype interactions. | advanced |
| Prototype animations | Да | Transitions, easing and spring settings. | daily/advanced |

## Collaboration and handoff

| Фича | Есть в Figma | Что это такое | Статус |
| --- | --- | --- | --- |
| Comments | Да | Feedback pinned to file, prototype, frame, layer or canvas coordinate. | daily |
| Mentions and threads | Да | @mentions, replies and comment workflows. | daily |
| Version history | Да | Browse, restore and share previous versions. | daily |
| Sharing and permissions | Да | Links, access levels and team collaboration. | daily |
| Dev Mode | Да | Developer-focused inspect and navigation mode. | advanced |
| Inspect panel | Да | Measurements, properties, code snippets and assets for implementation. | advanced |
| Export settings | Да | Export selected assets in supported static formats. | daily |
| Measurements and annotations | Да | Developer handoff notes and specs in Dev Mode. | advanced |

## Visual, AI and new products

| Фича | Есть в Figma | Что это такое | Статус |
| --- | --- | --- | --- |
| Figma Draw | Да | Illustration tools, brushes, transforms and textures inside Figma Design. | new/cool |
| Advanced vector editing | Да | Vector edit mode, shape builder, variable width, cut, bend, lasso, paint. | advanced |
| Figma Motion | Да | Motion workspace with presets, keyframes, timeline and reusable animation work. | new/cool |
| Figma AI tools | Да | AI actions for design, images, search and workflow help. | new/cool |
| Figma agent | Да, beta rollout | Conversational AI in design files. | new/cool |
| First Draft | Да | Generate early UI/design directions from a prompt. | new/cool |
| Make/Edit image | Да | Generate or edit images with AI inside Figma products. | new/cool |
| Figma Make | Да | AI prompt-to-app for functional prototypes, web apps and interactive UI. | new/cool |
| Figma Sites | Да, beta | Design, prototype and publish responsive websites. | new/cool |
| Figma Buzz | Да, beta | On-brand static asset creation for marketing/brand teams. | new/cool |
| Figma Slides | Да | Presentation creation with Figma workflows. | new/cool |
| Config 2026 materials | Да | Motion, shaders, code layers, generative plugins and Weave-related canvas workflows. | new/cool |

## Что не делать

- Не называть пользовательские library components встроенными Figma-компонентами.
  Button/Card/Input обычно приходят из design system, UI kit или library.
- Не называть images отдельным layer type: в Figma Design images/videos are
  fills.
- Не смешивать Dev Mode handoff, prototype behavior and design editing в одну
  фичу: это разные режимы работы.
- Не описывать beta/new features как гарантированно доступные всем plans/seats.
