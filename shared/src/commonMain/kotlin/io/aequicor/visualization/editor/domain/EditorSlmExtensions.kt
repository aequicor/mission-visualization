package io.aequicor.visualization.editor.domain

import io.aequicor.visualization.engine.frontend.SlmCompileOptions
import io.aequicor.visualization.engine.frontend.blocks.SlmExtensionRegistry
import io.aequicor.visualization.subsystems.diagrams.slm.DiagramSlmExtension

/**
 * Composition root of the editor's SLM typed-block extensions. Every place the editor
 * compiles SLM (initial load, draft restore, source editing, write-back recompiles)
 * must pass this registry, or extension blocks (`diagram:`) silently degrade to prose.
 * New subsystem extensions join here (one merged registry, per the extensibility plan).
 */
val EditorSlmExtensions: SlmExtensionRegistry = SlmExtensionRegistry.of(
    DiagramSlmExtension,
)

/** The editor's canonical [SlmCompileOptions]: file name + the merged extension registry. */
fun editorSlmCompileOptions(fileName: String): SlmCompileOptions =
    SlmCompileOptions(fileName = fileName, extensions = EditorSlmExtensions)
