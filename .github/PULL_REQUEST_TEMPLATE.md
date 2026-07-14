<!--
Thanks for contributing! Please fill in the sections below and check the boxes
that apply. See CONTRIBUTING.md and .claude/rules/architecture.md for the full rules.
-->

## Summary

<!-- What does this PR change, and why? -->

## Related issues

<!-- e.g. Closes #123 -->

## Affected targets

<!-- Check all that apply -->

- [ ] Android
- [ ] JVM (desktop)
- [ ] JS
- [ ] wasmJs (web)
- [ ] iOS

## Checklist

- [ ] Dependencies point inward to `domain`; no Compose in `domain`/`data`.
- [ ] Data access goes through a repository interface; business actions through a use case.
- [ ] Layer models are separate, with explicit mappers between layers.
- [ ] Presentation exposes immutable state and accepts intents (MVI).
- [ ] Colors/spacing use theme tokens (`LocalEditorColors`), not raw hex.
- [ ] New user-facing chrome strings go through the string catalog (`editor.ui.strings`).
- [ ] Added/updated tests for new domain logic / use cases.
- [ ] `./gradlew jvmTest` passes and `./gradlew :desktopApp:compileKotlin` succeeds.

## Verification

<!-- How did you verify the change? For UI, prefer the wasm/web surface (see CLAUDE.md). -->
