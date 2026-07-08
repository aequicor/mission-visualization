package io.aequicor.visualization.editor.presentation

/**
 * UI label variants ordered from preferred to most compact.
 *
 * Rendering code chooses the first variant that fits its current width; the last
 * variant is still single-line ellipsized if even it cannot physically fit.
 */
data class CompactLabel(
    val full: String,
    val compact: String = full,
    val short: String = compact,
) {
    val variants: List<String> = listOf(full, compact, short).filter { it.isNotBlank() }.distinct()

    val fallback: String = variants.lastOrNull().orEmpty()
}
