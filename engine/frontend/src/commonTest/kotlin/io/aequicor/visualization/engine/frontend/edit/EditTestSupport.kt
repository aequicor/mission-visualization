package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.SlmCompileOptions
import io.aequicor.visualization.engine.frontend.SlmCompileResult
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal val EditTestOptions = SlmCompileOptions(fileName = "test.layout.md")

internal fun compileForEdit(source: String): SlmCompileResult = compileSlm(source, EditTestOptions)

internal fun SlmCompileResult.requireDocument(): DesignDocument = assertNotNull(document)

internal fun DesignDocument.requireNode(id: String): DesignNode =
    assertNotNull(nodeById(id), "node \"$id\" not found")

/** Id of the text node whose default text is exactly [text]. */
internal fun SlmCompileResult.textNodeId(text: String): String = assertNotNull(
    requireDocument().pages.flatMap { it.allNodes() }.firstOrNull { node ->
        (node.kind as? DesignNodeKind.Text)?.content?.defaultText == text
    },
    "text node \"$text\" not found",
).id

internal fun SlmEditResult.requireNewSource(): String {
    assertTrue(isApplied, diagnostics.joinToString { it.message })
    return assertNotNull(newSource)
}

/** Asserts every byte outside [range] (new coordinates) is identical. */
internal fun assertLosslessOutside(old: String, new: String, range: SlmTextRange) {
    assertEquals(
        old.substring(0, range.startOffset),
        new.substring(0, range.startOffset),
        "prefix before appliedRange differs",
    )
    val tailLength = new.length - range.endOffset
    assertEquals(
        old.substring(old.length - tailLength),
        new.substring(range.endOffset),
        "suffix after appliedRange differs",
    )
}
