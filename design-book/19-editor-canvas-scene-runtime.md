# Canvas and Scene: интерактивность, переходы и animation runtime

[← Оглавление](README.md)

Эта глава фиксирует системное разделение для Mission Editor:

- `Canvas` - статичная верстка конкретного экрана;
- `Scene` - временная и событийная модель, которая показывает, как экран
  меняется при interaction, переходе, animation, delay или внешнем событии.

Цель: не смешивать редактирование экрана, prototype playback, timeline,
transition rendering and animation control в одном состоянии. Canvas отвечает за
то, как экран выглядит. Scene отвечает за то, как визуальное состояние меняется
во времени.

## Product goal

`advanced`

Пользователь должен уметь:

- редактировать статический экран в `Canvas` mode;
- переключиться в `Scene` mode через отдельную кнопку toolbar;
- нажимать на компоненты как пользователь прототипа, а не выбирать их;
- видеть переходы между экранами, даже если визуально участвуют два screen
  snapshot одновременно;
- контролировать анимацию через timeline: play, pause, seek, restart, slow
  motion;
- видеть, какие events, interactions, actions and variables сработали;
- отлаживать, почему компонент изменился, почему экран перешел или почему
  animation не запустилась.

Главный контракт: Canvas and Scene являются разными projections одного IR, а их
renderers архитектурно разные. Canvas projection строит редактируемый статический
экран для CanvasRenderer. Scene projection строит исполняемый frame поведения во
времени для SceneRenderer. Scene не является частным случаем Canvas и Canvas не
является частным случаем Scene.

## Technical architecture

`advanced`

Source pipeline должен быть единым до уровня IR, а после IR разделяться:

```text
SML/SLM source
  -> IR
  -> Canvas -> CanvasRenderer
  -> Scene  -> SceneRenderer
```

IR хранит authored-модель целиком:

```text
DesignDocument IR
  canvases/screens/pages
  nodes/components/styles/variables
  scene definitions
  interactions/triggers/actions
  transitions
  motion/keyframes
  action sets
```

После IR pipeline разделяется на два независимых projection and renderer path:

```text
Canvas mode:
  IR + CanvasEditorState
    -> CanvasProjection
    -> CanvasRenderModel
    -> CanvasRenderer

Scene mode:
  IR + SceneRuntimeState + TimelineState
    -> SceneProjection
    -> SceneRenderModel
    -> SceneRenderer
```

Это важная архитектурная граница. `CanvasRenderModel` and `SceneRenderModel`
могут переиспользовать одинаковые IR entities, layout algorithms, text
measurement interfaces, paint conversion helpers or primitive draw utilities.
Но renderer orchestration, owner состояния, hit-testing policy, event routing
and frame composition у них разные.

Недопустимый shortcut:

```text
IR -> current screen -> generic renderer
```

Такой путь работает только для статического preview и сразу ломается на
transition, overlay, pause at time, smart animation and multi-layer scene frame.

Правильный путь:

```text
IR
  -> CanvasProjection when user edits static screen
  -> SceneProjection when user plays or edits behavior
```

`current screen` не должен быть глобальным свойством renderer layer. В Canvas
mode текущий экран берется из editor workspace and goes to CanvasRenderer. В
Scene mode текущая позиция берется из scene runtime and goes to SceneRenderer.

## IR responsibilities

`advanced`

IR должен описывать не только статические screens, но и Scene authored model.
Если SML/SLM содержит Scene на 100%, компиляция не должна терять это знание и
перекладывать его в UI layer.

Минимальный conceptual split внутри IR:

```text
DesignDocument
  screens: authored visual screens
  components: reusable visual structures
  variables: design/prototype variables
  scenes: authored behavior graphs
  motionRefs: reusable motion assets
```

`Screen` отвечает за visual authored state:

- root frame;
- node hierarchy;
- layout/style/content;
- component instances;
- static responsive rules.

`Scene` отвечает за authored behavior:

- start screen or start node;
- flow graph;
- triggers;
- action chains;
- transition specs;
- animation clips;
- delay/timer rules;
- references to screens, overlays, nodes and variables.

IR не хранит live runtime values:

- current screen;
- current time;
- pressed node;
- active transition progress;
- current overlay stack;
- history stack;
- debug trace.

Эти значения принадлежат runtime state.

## Two editing modes

`base/advanced`

Toolbar должен явно разделять два режима:

```text
[Canvas] [Scene]
```

В `Canvas` mode:

- click по объекту выбирает объект;
- drag двигает или resize-ит объект;
- inspector редактирует layout, style, text, media, constraints and component
  properties;
- active screen означает экран, который редактируется;
- preview overlays показывают selection, handles, measurements and guides;
- interaction definitions можно видеть или настраивать, но они не исполняются
  как пользовательский сценарий.

В `Scene` mode:

- click по объекту исполняет его interaction;
- hover/press/focus states моделируются как runtime input state;
- active screen означает текущую позицию prototype runtime, not editor
  selection;
- timeline показывает transitions, animation clips and delayed actions;
- inspector фокусируется на behavior: trigger, action chain, transition,
  easing, duration, variables, playback;
- selection handles and edit affordances скрыты или заменены debug overlays.

Это UX-критично: один и тот же click не должен одновременно выбирать кнопку и
нажимать ее как пользователь прототипа.

## Canvas model

`base`

Canvas отвечает на вопрос:

> Как выглядит экран в конкретном authored state?

В Canvas живут:

- screen/frame structure;
- node tree;
- layout, sizing, constraints and auto layout;
- position, rotation, opacity and visual style;
- text, media, vector, fills, strokes, effects;
- components, instances, variants and props;
- static responsive variants;
- editor selection and manipulation overlays.

Canvas не владеет временем. Canvas не должен хранить `currentTimeMs`,
`playing`, `activeTransition` или transient press/hover state. Если animation
визуально двигает компонент, Canvas document не должен менять `position`.

Canvas model не является runtime source для Scene. Scene ссылается на authored
screens из IR by id and строит свои snapshots через SceneProjection. Если Canvas
mode сейчас редактирует Screen B, это не значит, что Scene runtime обязан
находиться на Screen B.

## Canvas projection

`base/advanced`

Canvas projection - это read model для редактирования одного статического
экрана.

Input:

```text
DesignDocument IR
CanvasEditorState
  selectedScreenId
  selectedNodeIds
  viewport
  activeTool
  hover/drag/editing state
```

Output:

```text
CanvasRenderModel
  screen layer
  resolved/layout tree
  selection overlays
  resize/rotate handles
  guides/measurements
  editor hit-test regions
```

Canvas projection отвечает за:

- выбрать screen из IR по `CanvasEditorState.selectedScreenId`;
- resolve variables/modes relevant for static editing;
- посчитать layout;
- добавить editor overlays;
- построить hit-test для selection/manipulation;
- отдать CanvasRenderer статический editable frame.

Canvas projection не исполняет interaction actions. Даже если node содержит
`onClick`, click в Canvas mode остается editor command unless user explicitly
opens Scene mode or interaction authoring UI.

## Scene model

`advanced`

Scene отвечает на вопрос:

> Что происходит с экраном при событиях и с течением времени?

В Scene живут:

- flows and сценарии;
- current runtime screen;
- history stack;
- overlay stack;
- prototype variables;
- event routing;
- interaction execution;
- active input states: hover, pressed, focused, dragging;
- active transitions;
- animation clips and keyframes;
- timeline clock;
- playback state;
- trace/debug log.

Scene не является еще одним статическим экраном. Scene может одновременно
использовать несколько Canvas snapshots:

```text
Scene frame at 140ms:
  Screen A snapshot, x = -80, opacity = 1.0
  Screen B snapshot, x = 320, opacity = 1.0
  Overlay backdrop, opacity = 0.35
  Modal snapshot, y = 24, opacity = 0.8
```

Именно это снимает ограничение "активен только один экран". Логически runtime
может иметь один current screen, но визуально SceneRenderer во время transition
получает несколько layers.

## Scene projection

`advanced`

Scene projection - это read model для проигрывания и редактирования поведения.

Input:

```text
DesignDocument IR
SceneRuntimeState
  sceneId
  currentLocation
  variables
  overlays
  history
  inputState
TimelineState
  currentTimeMs
  playbackState
  activeAnimations
```

Output:

```text
SceneRenderModel
  visual layers
  sampled animation overrides
  event hit-test regions
  timeline markers
  debug overlays
```

Scene projection отвечает за:

- выбрать Scene definition из IR;
- определить logical current location runtime-а;
- построить нужные screen/overlay snapshots from IR;
- применить runtime variables and input state;
- sample active transitions and keyframes at `currentTimeMs`;
- составить visual layers текущего кадра;
- построить hit-test по фактически видимым scene layers.

Scene projection не использует `CanvasEditorState.selectedScreenId` как source
of truth. Он может использовать selected screen только как стартовую подсказку
при запуске Scene mode, если пользователь не выбрал flow.

## Current screen resolution

`advanced`

В системе должно быть несколько разных "current screen":

```text
CanvasEditorState.selectedScreenId
  screen currently edited on Canvas

SceneRuntimeState.currentLocation
  logical screen/node/overlay where Scene runtime is now

Transition.fromSnapshot.screenId
  screen visible as outgoing layer

Transition.toSnapshot.screenId
  screen visible as incoming layer
```

Ни CanvasRenderer, ни SceneRenderer не должны читать глобальный
`currentScreenId`. Каждый renderer получает готовую модель from its projection.

Canvas mode render:

```text
CanvasEditorState.selectedScreenId = Screen B
CanvasProjection(IR, editorState)
  -> CanvasRenderModel(Screen B + editor overlays)
```

Scene mode stable render:

```text
SceneRuntimeState.currentLocation = Screen A
SceneProjection(IR, runtimeState, timeline)
  -> SceneRenderModel([Screen A snapshot])
```

Scene mode transition render:

```text
SceneRuntimeState.activeTransition = Screen A -> Screen C at 42%
SceneProjection(IR, runtimeState, timeline)
  -> SceneRenderModel([
       outgoing Screen A snapshot with sampled overrides,
       incoming Screen C snapshot with sampled overrides
     ])
```

Так текущий экран влияет на rendering only through projection. Renderer path
остается исполнителем готовой модели, а не владельцем навигации.

## Scene definition vs runtime

`advanced`

Нужно различать authored behavior and live playback state.

`Scene definition` - то, что автор настроил:

- flow starts at screen A;
- button click navigates to screen B;
- transition uses push, 300 ms, ease-out;
- modal opens after delay;
- component variant changes on press;
- keyframe animation loops on status indicator.

`Scene runtime` - то, что происходит сейчас:

- current screen is A;
- pointer is pressed on button X;
- variable `selectedTab` equals `details`;
- transition A -> B is at 42%;
- overlay stack contains modal Y;
- playback is paused at 180 ms.

Authoring state сериализуется как часть design/prototype model. Runtime state
должен быть transient и не должен попадать в document undo stack.

## Transition model

`advanced`

Переход между экранами не должен быть просто сменой `activeScreen`.
Transition требует две визуальные картины:

```text
from scene: что пользователь видел до события
to scene: что должно быть после события
transition: как перейти from -> to
progress: где timeline сейчас
```

Правильный порядок:

1. Scene runtime получает событие, например click.
2. Runtime находит interaction and action chain.
3. Runtime просит SceneProjection построить `from` snapshot from current
   runtime location.
4. Runtime применяет логическое действие к копии prototype state.
5. Runtime просит SceneProjection построить `to` snapshot from target runtime
   state.
6. Timeline создает transition instance.
7. SceneProjection каждый кадр sample-ит transition and builds
   `SceneRenderModel`.
8. SceneRenderer получает готовый `SceneRenderModel`.
9. После завершения transition runtime фиксирует новый stable state.

Во время transition SceneRenderer может рисовать `from` and `to` одновременно.
После завершения остается только stable scene.

## Snapshots

`advanced`

Snapshot - это зафиксированная визуальная картина на момент запуска перехода.
Он нужен, потому что исходный экран может перестать быть текущим, но должен
оставаться видимым, пока transition не закончился.

Snapshot должен включать:

- resolved node tree;
- layout boxes;
- визуальные свойства;
- relevant runtime variables;
- overlay state;
- scroll state where applicable.

Snapshot не должен зависеть от дальнейших live edits Canvas mode. Если
пользователь редактирует документ, active Scene playback должен либо
перестроиться controlled образом, либо быть остановлен с понятным reset.

## Render contracts

`advanced`

Canvas and Scene должны иметь разные верхнеуровневые render contracts.

Canvas contract:

```text
CanvasRenderModel
  screenId
  root layer
  editor overlays
  editor hit-test map
  viewport hints
```

Scene contract:

```text
SceneRenderModel
  layers:
    - screen snapshot
    - outgoing screen snapshot
    - incoming screen snapshot
    - overlay backdrop
    - overlay snapshot
  visual overrides:
    - opacity
    - translation
    - scale
    - rotation
    - clip
    - z-order during transition
  debug overlays:
    - triggered node
    - active hit target
    - timeline markers
```

CanvasRenderer and SceneRenderer являются разными architecture paths.
Они могут share small implementation utilities:

```text
shared utilities
  text measurement adapters
  paint conversion helpers
  vector path parsers
  primitive shape drawing helpers
```

Но не должно быть обязательного generic renderer, который принимает
`currentScreenId` and guesses the mode. CanvasRenderer consumes
`CanvasRenderModel`; SceneRenderer consumes `SceneRenderModel`.

SceneRenderer не должен знать, что такое `Navigate`, `SetVariable`,
`OpenOverlay` или `RunActionSet`. Это уже исполнено SceneRuntime and
SceneProjection. SceneRenderer должен знать только, какие scene layers надо
нарисовать в текущем кадре и какие visual overrides применить.

CanvasRenderer не должен знать Scene timeline, transition progress or action
chain. Он рисует static editable frame plus editor overlays.

## Animation control

`advanced`

Animation должна управляться timeline/controller, not ad hoc UI animation.
Каждая animation должна уметь быть sampled:

```text
sample(timeMs) -> visual overrides
```

Timeline должен поддерживать:

- play;
- pause;
- restart;
- seek/scrub;
- playback speed;
- frame inspection;
- completion callbacks;
- cancellation;
- deterministic replay.

Это делает возможными:

- pause на середине transition;
- slow motion для проверки easing;
- scrubber для component state animation;
- debug frame at exact time;
- automated checks for expected visual state.

Compose или другой UI toolkit может быть clock/render host, но не должен быть
единственным владельцем логики animation state.

## Animation types

`advanced`

Scene должна различать несколько классов animation.

`Transition animation`:

- screen A -> screen B;
- open overlay;
- close overlay;
- swap overlay;
- push, slide, move in/out, dissolve.

`Property animation`:

- opacity;
- x/y translation;
- scale;
- rotation;
- size;
- color;
- corner radius.

`Component state animation`:

- button default -> hover -> pressed;
- tab inactive -> active;
- input idle -> focused -> error;
- card collapsed -> expanded;
- loading -> success.

`Smart animation`:

- сравнение two snapshots;
- matching nodes by stable ids;
- interpolation of bounds and visual properties;
- unmatched old nodes fade/move out;
- unmatched new nodes fade/move in.

Smart animation не должна быть базовой моделью всех переходов. Это отдельный
advanced transition type поверх общей snapshot/timeline architecture.

## Event routing

`advanced`

Scene mode должен явно определить, кто получает input.

В стабильном состоянии:

- pointer event hit-test-ится по topmost visible scene layer;
- найденный node получает trigger;
- ближайший ancestor с подходящим interaction может принять событие;
- event execution может изменить variables, overlays, current screen or
  timeline.

Во время transition MVP behavior:

- input blocked until transition completes.

Это простое правило снимает большинство edge cases. Позже можно добавить:

- target-screen input;
- topmost-layer input;
- interruptible transitions;
- gesture-controlled transitions.

Любое расширение input policy должно быть явным свойством Scene runtime, а не
случайным поведением SceneRenderer.

## Runtime state boundaries

`base/advanced`

Нужно держать разные состояния раздельно:

```text
Document State
  authored screens, components, styles, interactions

Canvas Editor State
  selected node, selected canvas screen, tool, hover, drag, inspector panels

Scene Definition State
  flows, triggers, actions, transitions, clips

Scene Runtime State
  current screen, variables, overlays, history, input capture

Timeline State
  current time, playing/paused, active animations

Canvas Projection State
  resolved static screen, editor overlays, selection hit-test

Scene Projection State
  snapshots, sampled layers, timeline overrides, event hit-test

CanvasRenderer State
  static editable frame drawing, editor overlays, editor hit-test affordances

SceneRenderer State
  scene layer drawing, sampled visual overrides, scene hit-test affordances

Debug State
  trace, active action, warnings, timeline markers
```

Особенно важно: `selectedPageId` in Canvas editor не должен быть тем же самым,
что `currentScreen` in Scene runtime. Пользователь может редактировать Screen B,
пока Scene runtime paused на transition from Screen A to Screen C, если продукт
разрешает такой workflow.

Projection state должен быть пересоздаваемым from IR + owner state. Он не должен
становиться третьим источником правды.

## Debug and trace

`advanced`

Для Scene mode debug является частью core UX, not optional polish.

Пользователь должен видеть:

- какое событие пришло;
- какой node был hit target;
- какой trigger выбран;
- какие actions выполнились;
- какие variables изменились;
- какой transition запущен;
- какие animation clips активны;
- почему событие проигнорировано;
- где timeline находится сейчас.

Trace должен быть детерминированным и пригодным для replay. Если два запуска
сценария из одного initial state дают разные results без внешнего события, это
bug in runtime model.

## Mode switch behavior

`base`

Переключение `Canvas -> Scene`:

- сохраняет Canvas selection and viewport;
- запускает Scene runtime from selected flow or selected screen;
- скрывает edit handles;
- включает event-routing behavior;
- показывает timeline controls;
- не создает undo entry.

Переключение `Scene -> Canvas`:

- останавливает active playback or pauses it according to workspace policy;
- возвращает edit handles and inspector;
- восстанавливает selection and canvas tools;
- не применяет runtime variables to document;
- не меняет authored layout.

Если Scene playback был paused на середине transition, Canvas mode не должен
показывать "полукадр" как редактируемый документ. Полукадр принадлежит Scene
runtime, not Canvas.

## Acceptance checklist

- toolbar имеет явный переключатель `Canvas / Scene`;
- SML/SLM компилирует static screens and Scene definitions в IR без потери behavior;
- после IR pipeline разделяется на CanvasProjection and SceneProjection;
- CanvasProjection and SceneProjection are siblings, not parent/child modes;
- в Canvas mode click selects, в Scene mode click executes interaction;
- Canvas document не хранит current time or active animation state;
- Scene runtime не мутирует authored layout during playback;
- CanvasRenderer and SceneRenderer не читают глобальный `currentScreenId`;
- transition строится из `from` and `to` snapshots;
- SceneRenderer может рисовать несколько scene layers in one frame;
- Canvas selected screen, Scene current location, transition from/to snapshots
  are separate concepts;
- timeline поддерживает play, pause, restart and scrub;
- animation sampling returns visual overrides, not document edits;
- input during transition follows explicit policy;
- Scene runtime state не попадает в editor undo stack;
- debug trace показывает event, hit target, actions, variables and transition;
- switching modes preserves editor workspace state and does not mutate document.
