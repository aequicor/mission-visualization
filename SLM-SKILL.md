---
name: compose-slm
description: >-
  Compatibility entry point for authoring, editing, reviewing, or validating Semantic
  Layout Markdown (`*.layout.md`) with English CNL. Use when an existing tool or prompt
  requests the historical `compose-slm` skill; immediately load the canonical
  `SKILLS/SLM.md` guide and any specialist skill it routes to.
---

# Compatibility entry point for SLM

The canonical, self-contained SLM/CNL skill is [SKILLS/SLM.md](SKILLS/SLM.md).
Read it completely before creating or editing a `.layout.md` file.

Then load only the specialist guide required by the task:

- [diagrams](SKILLS/SLM-diagrams.md)
- [vector graphics](SKILLS/SLM-vector-graphics.md)
- [typography](SKILLS/SLM-typography.md)
- [annotation sidecars](SKILLS/SLM-annotations.md)
- [editor write-back](SKILLS/SLM-editor.md)

Do not maintain grammar rules in this compatibility file. `SKILLS/SLM.md` and the
executable `CnlGrammar`/`CnlVocabulary` sources are authoritative.
