package io.aequicor.visualization.engine.frontend.i18n

import io.aequicor.visualization.engine.frontend.ast.KeyHint
import io.aequicor.visualization.engine.frontend.expr.SlmExpression
import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan

/**
 * One localizable text collected during normalization. The i18n generator
 * (stage 7.8) turns entries into resource keys and locale bundles.
 */
data class TextEntry(
    val keyHint: KeyHint,
    val explicitKey: String?,
    val defaultText: String,
    val params: Map<String, SlmExpression>,
    val span: SlmSourceSpan,
    val nodeId: String,
)
