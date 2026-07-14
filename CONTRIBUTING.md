# Contributing to Mission Visualization

Thanks for your interest in contributing! This is a Kotlin Multiplatform + Compose
Multiplatform project. Please read this guide before opening a pull request.

## Prerequisites

- JDK 17 (Temurin recommended)
- The Gradle wrapper is committed — always use `./gradlew`, never a system Gradle
- Android builds additionally need the Android SDK

## Build & run

| Target | Command |
| --- | --- |
| Desktop (JVM) | `./gradlew :desktopApp:run` |
| Web (Wasm) | `./gradlew :webApp:wasmJsBrowserDevelopmentRun` |
| Android (debug) | `./gradlew :androidApp:assembleDebug` |
| iOS | open `iosApp` in Xcode |

The product is **wasm-first**: the browser (`:webApp`, Compose/WasmJs) is the primary
surface for verifying UI changes. See `CLAUDE.md` for the details of the dev-server workflow.

## Tests

Run before every PR:

```bash
./gradlew jvmTest                 # all modules (engine, subsystems, shared)
./gradlew :desktopApp:compileKotlin
```

Focused runs:

```bash
./gradlew :engine:ir:jvmTest
./gradlew :engine:frontend:jvmTest
./gradlew :shared:jvmTest
```

CI runs the same `jvmTest` + desktop compile on every pull request.

## Architecture & code style

New code must follow the project's target architecture and conventions:

- **Dependency rule** — dependencies point inward to `domain`: `ui → presentation → domain ← data`.
  `domain` and `data` are pure Kotlin with no Compose. Full rules: `.claude/rules/architecture.md`.
- **Data access** goes through a repository interface; a business action goes through a use case.
- **State** is immutable; UI is stateless with hoisted state (MVI: `State` + sealed `Intent` +
  pure reducer). Colors/spacing come from theme tokens, never raw hex.
- **Kotlin & Compose conventions**: `.claude/rules/code-style.md`.
- **Authoring SLM documents** (`*.layout.md`): `design-book/semantic-layout-markdown-i18n.md`
  and `SKILLS/SLM.md`.
- **Pipeline & layering** (`engine/*`): `engine/README.md`.

## Pull requests

- Branch off `main`; keep PRs focused.
- Ensure `./gradlew jvmTest` passes and the app still compiles.
- Fill in the PR template checklist (dependency direction, layered models, immutable state,
  tests for new domain logic).
- Localize any new user-facing chrome string through the string catalog (`editor.ui.strings`);
  document content stays language-neutral. See the localization section in `CLAUDE.md`.

## License

By contributing, you agree that your contributions are licensed under the
[Apache License, Version 2.0](LICENSE).
