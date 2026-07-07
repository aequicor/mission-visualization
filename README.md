# Mission Visualization

Mission Visualization is a Kotlin Multiplatform + Compose Multiplatform library
and demo application for visualizing screens and user scenarios from
agent-authored Markdown. It is intended to work without Figma: agents describe a
strict `mission-visualization` YAML block, and the app renders polished mockups,
lets reviewers select components, attach comments, and generate deterministic
design-edit prompts.

## Current Shape

- `shared` contains the reusable KMP library, parser, domain model, reducer, and
  Compose UI.
- `desktopApp` and `webApp` are the primary MVP demos.
- `example` is a consumer module plus ready-to-paste Markdown examples.
- `androidApp` and `iosApp` remain thin launch wrappers around the shared UI.
- The future MCP integration should call the same `VisualizationCommand` API
  that the UI uses today.

## Markdown Contract

Each document must contain exactly one fenced block:

````markdown
```mission-visualization
version: 1
title: Example Mission
screens:
  - id: dashboard
    title: Dashboard
    components:
      - id: primary-action
        type: button
        text: Review scenario
scenarios:
  - id: review
    title: Review flow
    steps:
      - screenId: dashboard
        componentId: primary-action
        action: Select the primary action.
```
````

The MVP YAML subset supports maps, lists, strings, numbers, booleans, and null.
It intentionally does not support anchors, aliases, tags, or multiline scalars.

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
