# Mission Visualization

[![Latest published web version](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Faequicor.github.io%2Fmission-visualization%2Fversion.json&query=%24.version&label=latest%20published%20web%20version&prefix=v&cacheSeconds=300)](https://aequicor.github.io/mission-visualization/)

Mission Visualization is a Kotlin Multiplatform + Compose Multiplatform library and
demo: an agent authors **Semantic Layout Markdown** (`*.layout.md`, RU/EN + i18n),
the frontend compiles it to a language-independent typed IR (`slm-ir/1.0`), a pure
layout engine lays the document out, and a Compose backend renders an interactive
preview. Editor edits patch the SLM source surgically (`SlmPatcher`) or the in-memory
document. Root package: `io.aequicor.visualization`.

> The earlier `.mv.yaml` / `ui_engine` engine and the `:example` module were removed;
> this README describes the current SLM pipeline. See `CLAUDE.md` and
> `engine/README.md` for the authoritative architecture and layering rules.

## Modules

- `:engine:ir` — document core (pure Kotlin, KMP): the typed `slm-ir/1.0` IR —
  model / serialization / resolve / layout / validate.
- `:engine:frontend` — the SLM compiler (pure Kotlin): `*.layout.md` → IR, plus
  `SlmPatcher` for surgical write-back.
- `:engine:backend-compose` — the Compose renderer (`DesignArtboard`); the only engine
  module that depends on Compose.
- `:shared` — app shell: `App`, the Mission Editor, and `editor.{presentation,domain,data,ui}`.
  Targets: Android, JVM (desktop), JS, wasmJs, iOS.
- `:androidApp`, `:desktopApp`, `:webApp`, `iosApp` — thin wrappers around the shared UI.

## Mission Editor

The demo is a working visual editor (Figma-like) over one shared document model:
resizable/collapsible panels and a focus mode; a zoom/pan/fit canvas with
hover/select/marquee, drag-move, handle-resize and shape/text tools; a Layers tree;
and a context inspector for position, layout, appearance, fill, stroke, effects and
typography. Document state and workspace/view state are kept separate; every document
action is a `DesignEditorIntent` reduced by the pure `reduceDesignEditor`.

See **[EDITOR.md](EDITOR.md)** for the implemented scope, wiring, and known gaps.

## Authoring & pipeline

- SLM authoring spec: `design-book/semantic-layout-markdown-i18n.md`.
- Pipeline overview and layering rules: `engine/README.md`.
- UX requirements the editor targets: `design-book/07..16-editor-*.md`.

## Running

- Desktop: `./gradlew :desktopApp:run`
- Web (Wasm): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
- Android debug: `./gradlew :androidApp:assembleDebug`

## Tests

- Editor / shared (JVM): `./gradlew :shared:jvmTest`
- Engine: `./gradlew :engine:ir:jvmTest` · `./gradlew :engine:frontend:jvmTest`
- Desktop compile check: `./gradlew :desktopApp:compileKotlin`
