# Mission Visualization

Mission Visualization is a Kotlin Multiplatform + Compose Multiplatform library
and demo application for visualizing agent-authored UI documents. Agents write a
strict standalone `.mv.yaml` document, the library parses and validates it into a
typed IR, and the app renders a Compose preview with Canvas overlays for
selection, comments, and scenario flow.

## Current Shape

- `shared` contains the reusable KMP parser, IR model, validator, reducer, and
  Compose renderer.
- `desktopApp` and `webApp` are the primary MVP demos.
- `example` is a consumer module plus ready-to-paste `.mv.yaml` examples.
- `androidApp` and `iosApp` remain thin launch wrappers around the shared UI.
- Future integrations should call the same `UiCommand` and `loadUiDocument`
  APIs that the UI uses today.

## Engine Packages

The UI engine is split by pipeline stage:

- `ui_engine.mv_yaml_source`: bundled `.mv.yaml` sample source.
- `ui_engine.parser`: standalone YAML parser and syntax diagnostics.
- `ui_engine.ui_document_ir`: `UiDocument` IR, diagnostics, and helpers.
- `ui_engine.validator`: semantic validation and document loading pipeline.
- `ui_engine.runtime_state`: commands, state transitions, selection, comments,
  input state, and prompt generation.
- `ui_engine.compose_render_engine`: Compose render engine, renderer registry,
  shared render contracts, and preview wiring.
- `ui_engine.compose_ui`: application shell around source editor, preview, and
  inspector panes.
- `ui_engine.canvas_overlays`: overlay drawing for selection, comments, and
  scenario links.
- `ui_engine.components.<component>`: one isolated renderer provider per UI
  component type.

## UI Document Contract

Each document is standalone YAML:

```yaml
version: 1
title: Example UI
screens:
  - id: dashboard
    title: Dashboard
    layout:
      type: column
      padding: lg
      gap: md
    children:
      - id: primary-action
        type: button
        style:
          variant: primary
        props:
          text: Review scenario
        action:
          type: navigate
          target: review
  - id: review
    title: Review
    children:
      - id: review-card
        type: card
        props:
          title: Generated prompt
          body: Review the generated design patch.
scenarios:
  - id: review-flow
    title: Review flow
    steps:
      - screenId: dashboard
        nodeId: primary-action
        action: Select the primary action.
```

The YAML subset supports maps, lists, strings, numbers, booleans, and null. It
intentionally does not support anchors, aliases, tags, or multiline scalars.
The schema artifact lives at `shared/src/commonMain/resources/schema/ui-document.schema.json`.

## Running

- Desktop: `./gradlew :desktopApp:run`
- Web Wasm: `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
- Example Desktop: `./gradlew :example:run`
- Example Web Wasm: `./gradlew :example:wasmJsBrowserDevelopmentRun`
- Android: `./gradlew :androidApp:assembleDebug`

## Tests

- Shared JVM tests: `./gradlew :shared:jvmTest`
- Desktop compile check: `./gradlew :desktopApp:compileKotlin`
- Web distribution check:
  `./gradlew :webApp:wasmJsBrowserDevelopmentExecutableDistribution`
