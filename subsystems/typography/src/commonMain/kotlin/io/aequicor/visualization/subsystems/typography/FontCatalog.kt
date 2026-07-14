package io.aequicor.visualization.subsystems.typography

/** A concrete face request: family plus weight/italic/variable axes. */
data class FontDescriptor(
    val family: String,
    val weight: Int = TypographyDefaults.FONT_WEIGHT,
    val italic: Boolean = false,
    val axes: Map<String, Double> = emptyMap(),
)

/** A named style within a family, Figma-style ("Regular", "Bold Italic", ...). */
data class NamedFontStyle(
    val name: String,
    val weight: Int,
    val italic: Boolean = false,
)

object FontStyles {

    private val weightNames = listOf(
        100 to "Thin",
        200 to "Extra Light",
        300 to "Light",
        400 to "Regular",
        500 to "Medium",
        600 to "Semi Bold",
        700 to "Bold",
        800 to "Extra Bold",
        900 to "Black",
    )

    /** The standard 18 named styles (9 weights x roman/italic). */
    val STANDARD: List<NamedFontStyle> = weightNames.flatMap { (weight, name) ->
        listOf(
            NamedFontStyle(name, weight, italic = false),
            NamedFontStyle(if (weight == 400) "Italic" else "$name Italic", weight, italic = true),
        )
    }

    fun nameFor(weight: Int, italic: Boolean): String {
        val base = weightNames.minByOrNull { (w, _) -> kotlin.math.abs(w - weight) }?.second ?: "Regular"
        return when {
            !italic -> base
            base == "Regular" -> "Italic"
            else -> "$base Italic"
        }
    }

    /** Parses a named style back to (weight, italic); unknown names -> Regular. */
    fun parse(name: String): NamedFontStyle {
        val normalized = name.trim()
        val italic = normalized.endsWith("Italic", ignoreCase = true)
        // Strip by length: the suffix was matched case-insensitively above.
        val weightPart = if (italic) normalized.dropLast("Italic".length).trim() else normalized
        val normalizedWeightPart = weightPart.filterNot { it.isWhitespace() || it == '-' || it == '_' }
        val weight = weightNames.firstOrNull { (_, n) ->
            n.filterNot(Char::isWhitespace).equals(normalizedWeightPart, ignoreCase = true)
        }?.first
            ?: if (weightPart.isEmpty() && italic) 400 else 400
        return NamedFontStyle(nameFor(weight, italic), weight, italic)
    }

    /**
     * CSS-like matching: prefer the requested italic flavor, then the closest weight
     * (below-or-equal wins ties for weights <= 500, above-or-equal for > 500).
     */
    fun closest(available: List<NamedFontStyle>, weight: Int, italic: Boolean): NamedFontStyle? {
        if (available.isEmpty()) return null
        val pool = available.filter { it.italic == italic }.ifEmpty { available }
        return pool.minByOrNull { candidate ->
            val distance = kotlin.math.abs(candidate.weight - weight)
            val directionPenalty = when {
                weight <= 500 && candidate.weight > weight -> 1
                weight > 500 && candidate.weight < weight -> 1
                else -> 0
            }
            distance * 2 + directionPenalty
        }
    }
}
