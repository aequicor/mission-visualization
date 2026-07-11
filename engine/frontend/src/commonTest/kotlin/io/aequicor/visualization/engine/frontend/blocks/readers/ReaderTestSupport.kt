package io.aequicor.visualization.engine.frontend.blocks.readers

/**
 * `§` stands in for `$` so spec snippets can be pasted without Kotlin string
 * interpolation escapes.
 */
internal fun slm(text: String): String = text.trimIndent().replace('§', '$')
