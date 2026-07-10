package io.aequicor.visualization.engine.frontend.blocks

import io.aequicor.visualization.engine.frontend.blocks.readers.BlockReading
import io.aequicor.visualization.engine.frontend.blocks.readers.readActionBlock
import io.aequicor.visualization.engine.frontend.blocks.readers.readComponentBlock
import io.aequicor.visualization.engine.frontend.blocks.readers.readExportBlock
import io.aequicor.visualization.engine.frontend.blocks.readers.readHandoffBlock
import io.aequicor.visualization.engine.frontend.blocks.readers.readInteractionBlock
import io.aequicor.visualization.engine.frontend.blocks.readers.readLayoutBlock
import io.aequicor.visualization.engine.frontend.blocks.readers.readMaskBlock
import io.aequicor.visualization.engine.frontend.blocks.readers.readMediaBlock
import io.aequicor.visualization.engine.frontend.blocks.readers.readMotionBlock
import io.aequicor.visualization.engine.frontend.blocks.readers.readNodeBlock
import io.aequicor.visualization.engine.frontend.blocks.readers.readOverridesBlock
import io.aequicor.visualization.engine.frontend.blocks.readers.readPropsBlock
import io.aequicor.visualization.engine.frontend.blocks.readers.readResponsiveBlock
import io.aequicor.visualization.engine.frontend.blocks.readers.readShapeBlock
import io.aequicor.visualization.engine.frontend.blocks.readers.readStyleBlock
import io.aequicor.visualization.engine.frontend.blocks.readers.readStylesBlock
import io.aequicor.visualization.engine.frontend.blocks.readers.readTextBlock
import io.aequicor.visualization.engine.frontend.blocks.readers.readVariablesBlock
import io.aequicor.visualization.engine.frontend.blocks.readers.readVectorBlock
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.markdown.TypedEntry

/**
 * Converts one [TypedEntry] of a typed attribute block into a partial-IR [TypedPatch],
 * dispatching by [TypedBlockKind]. Per-property diagnostics are reported at
 * `file:line#<kind>`. Returns null when the entry is unreadable at the top level.
 */
object TypedBlockReader {
    fun read(entry: TypedEntry, diagnostics: DiagnosticCollector): TypedPatch? {
        val reading = BlockReading(diagnostics, entry.kind.key)
        return when (entry.kind) {
            TypedBlockKind.Node -> readNodeBlock(entry.value, reading)
            TypedBlockKind.Layout -> readLayoutBlock(entry.value, reading)
            TypedBlockKind.Style -> readStyleBlock(entry.value, reading)
            TypedBlockKind.Text -> readTextBlock(entry.value, reading)
            TypedBlockKind.Component -> readComponentBlock(entry.value, reading)
            TypedBlockKind.Props -> readPropsBlock(entry.value, reading)
            TypedBlockKind.Overrides -> readOverridesBlock(entry.value, reading)
            TypedBlockKind.Media -> readMediaBlock(entry.value, reading)
            TypedBlockKind.Shape -> readShapeBlock(entry.value, reading)
            TypedBlockKind.Vector -> readVectorBlock(entry.value, reading)
            TypedBlockKind.Mask -> readMaskBlock(entry.value, reading)
            TypedBlockKind.Action -> readActionBlock(entry.value, reading)
            TypedBlockKind.Interaction -> readInteractionBlock(entry.value, reading)
            TypedBlockKind.Motion -> readMotionBlock(entry.value, reading)
            TypedBlockKind.Responsive -> readResponsiveBlock(entry.value, reading)
            TypedBlockKind.Variables -> readVariablesBlock(entry.value, reading)
            TypedBlockKind.Styles -> readStylesBlock(entry.value, reading)
            TypedBlockKind.Handoff -> readHandoffBlock(entry.value, reading)
            TypedBlockKind.Export -> readExportBlock(entry.value, reading)
        }
    }
}
