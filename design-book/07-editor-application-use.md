# Application uses: рабочее пространство редактора

[← Оглавление](README.md)

Эта глава описывает, как пользователь должен управлять самим приложением:
панелями, canvas, режимами фокуса, навигацией и состояниями рабочего места.

Figma reference:

- [Explore the navigation bar and left sidebar](https://help.figma.com/hc/en-us/articles/360039831974-Explore-the-navigation-bar-and-left-sidebar)
- [Hide or minimize the UI](https://help.figma.com/hc/en-us/articles/41414918021271-Hide-or-minimize-the-UI)
- [Design, prototype, and explore layer properties in the right sidebar](https://help.figma.com/hc/en-us/articles/360039832014-Design-prototype-and-explore-layer-properties-in-the-right-sidebar)
- [Adjust your zoom and view options](https://help.figma.com/hc/en-us/articles/360041065034-Adjust-your-zoom-and-view-options)

## UX-модель Figma

`base`

Figma Design file состоит из нескольких постоянных рабочих зон:

- toolbar для создания и выбора инструментов;
- navigation bar для базовых workflow;
- left sidebar для layers/assets/pages;
- right sidebar для properties/prototype/inspect;
- scrollable canvas для прямой работы с объектами.

С точки зрения UX это важно: пользователь всегда понимает, где создаются
объекты, где они организуются, где они редактируются и где виден результат.

Для Mission Editor аналог:

- `Source` and `Screens` слева;
- `Preview / Canvas` в центре;
- `Inspector` справа;
- floating toolbar для инструментов canvas;
- top/header controls для режима preview, zoom and focus.

## Resize панелей

`base/daily`

В Figma left sidebar можно растягивать: пользователь наводит курсор на правый
край sidebar, видит bidirectional arrow, зажимает и drag-ит границу.

Для Mission Editor это должно стать базовым поведением всех крупных стыков:

- между `Source` и `Preview`;
- между `Preview` и `Inspector`;
- между верхней preview area и нижними toolbars, если они будут docked;
- между списком screens и source editor, если они находятся в одной колонке.

Hover behavior:

- зона resize должна быть шире визуальной линии;
- при наведении курсор меняется на horizontal или vertical resize;
- линия стыка получает hover highlight;
- tooltip опционален, но полезен для первого использования: `Resize panel`;
- hover не должен перехватывать click, если курсор явно находится на объекте
  canvas, а не на стыке.

Drag behavior:

- после mouse down resize захватывается до mouse up;
- линия становится active;
- размеры соседних областей меняются в реальном времени;
- текст внутри панелей не выделяется во время drag;
- поверх приложения может появляться прозрачная drag shield;
- рядом с линией можно показывать текущий размер: `Source 440 px`;
- если достигнут min/max, линия останавливается и может показать limit state.

Release behavior:

- размер применяется сразу после отпускания;
- layout пересчитывается без скачков;
- selection на canvas сохраняется;
- активная вкладка панели не сбрасывается;
- размер сохраняется как пользовательская настройка workspace.

Edge cases:

- Escape во время drag отменяет resize;
- double click по стыку возвращает дефолтный размер;
- если окно стало слишком узким, панели переходят в collapsed/drawer режим;
- если пользователь drag-ит до минимальной ширины, панель можно свернуть.

## Minimize, collapse and hide UI

`base`

Figma различает minimized UI and hidden UI:

- minimize оставляет доступ к canvas, а panels могут появляться по контексту;
- hide UI скрывает navigation, sidebars and toolbar, чтобы максимально
  освободить canvas.

Для Mission Editor нужны три состояния:

- `expanded`: все панели видны;
- `collapsed`: панель свернута, но есть ручка/иконка для возврата;
- `hidden`: панель выключена в focus/presentation mode.

Collapse source:

- левая панель исчезает;
- canvas расширяется;
- на левом краю остается icon button `Show source`;
- текущий screen и source tab сохраняются;
- при возврате scroll position source editor восстанавливается.

Collapse inspector:

- правая панель исчезает;
- selected object остается selected;
- canvas получает больше места;
- на правом краю остается icon button `Show inspector`;
- если выбрать новый объект в collapsed state, inspector не раскрывается
  автоматически, если пользователь явно выбрал focus mode.

Minimized behavior:

- панели могут быть визуально свернуты;
- при выборе объекта правый inspector может временно раскрыться;
- при снятии selection inspector снова минимизируется;
- это удобно для визуальной работы, когда свойства нужны только по контексту.

## Main only / focus mode

`daily`

`Main only` показывает только рабочую область canvas.

Что скрывается:

- Source;
- Screens list;
- Inspector;
- secondary headers;
- full toolbar, если включен presentation-like mode.

Что остается:

- canvas/artboard;
- минимальные zoom controls;
- exit focus button;
- optional floating toolbar в edit focus;
- current screen title, если без него пользователь теряет контекст.

Варианты:

- `Canvas only`: только canvas и выбранный screen;
- `Edit focus`: canvas, selection handles, compact toolbar;
- `Preview focus`: без handles, guides and editor chrome;
- `Compare focus`: несколько screens рядом без source/inspector.

Keyboard:

- Escape выходит из focus mode;
- shortcut toggle включает/выключает focus;
- zoom shortcuts продолжают работать;
- selection shortcuts продолжают работать в `Edit focus`.

Acceptance criteria:

- вход и выход не сбрасывают selection;
- zoom/pan/current screen сохраняются;
- размеры панелей восстанавливаются после выхода;
- focus mode не меняет документ, только workspace state.

## Canvas navigation

`base/daily`

Figma отделяет zoom canvas от scale интерфейса. Пользователь может менять zoom,
fit view and zoom to selection без изменения размеров sidebar UI.

Для Mission Editor:

- zoom percentage должен быть виден рядом с canvas;
- zoom in/out должен работать горячими клавишами и controls;
- fit screen центрирует текущий screen;
- fit selection центрирует выбранный объект;
- reset zoom возвращает 100%;
- pan не должен случайно двигать selected object.

Cursor behavior:

- select tool: обычный pointer на объектах;
- hand/pan tool: hand cursor;
- при зажатом pan: grabbing cursor;
- при drag пустого canvas в select mode можно делать marquee selection, если
  это не конфликтует с pan gesture.

Zoom behavior:

- zoom происходит относительно курсора;
- при zoom out labels and handles должны оставаться читаемыми, но не закрывать
  весь screen;
- при zoom in должны быть видны pixel-level guides;
- scroll wheel and trackpad gestures должны иметь разные thresholds, чтобы
  избежать случайного zoom.

## Workspace persistence

`base`

Пользовательские настройки workspace не должны быть частью screen design.

Сохранять как user preference:

- ширина Source;
- ширина Inspector;
- collapsed panels;
- focus mode default;
- last zoom;
- last selected screen;
- last selected source tab;
- inspector expanded sections.

Не сохранять в design document:

- временный hover;
- active drag;
- transient tooltip;
- focused input field;
- temporary marquee selection.

Почему важно: документ должен описывать экран, а не личную раскладку рабочего
места конкретного пользователя.

## Application uses checklist

- курсор меняется на стыке панелей до mouse down;
- drag стыка меняет размеры плавно;
- resize не выделяет текст;
- Source and Inspector можно свернуть и вернуть;
- focus mode скрывает лишний chrome;
- Escape возвращает из focus/edit states;
- canvas можно zoom, pan, fit screen, fit selection;
- workspace state не загрязняет screen/document model.
