package io.aequicor.visualization.editor.ui.strings

/**
 * The diagram overlay ([io.aequicor.visualization.editor.ui.EditorDiagramOverlay]) renders onto the
 * canvas and carries no chrome copy of its own — the diagram *inspector* strings live in
 * [InspectorStrings]. This area is intentionally empty; kept so [Strings] stays symmetric and a future
 * diagram-overlay label has an obvious home.
 */
interface DiagramStrings

object DiagramStringsEn : DiagramStrings

object DiagramStringsRu : DiagramStrings
