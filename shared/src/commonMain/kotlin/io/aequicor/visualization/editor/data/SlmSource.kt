package io.aequicor.visualization.editor.data

/**
 * Prepares an embedded SLM document literal: trims the raw-string margin and turns
 * the `§` placeholder into the SLM token sigil `$` (which Kotlin raw strings cannot
 * carry in front of an identifier without noisy `${'$'}` escapes).
 */
internal fun missionSlm(source: String): String = source.trimIndent().replace('§', '$')
