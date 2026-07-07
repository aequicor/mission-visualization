package io.aequicor.visualization.engine.frontend.normalize

import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector

/**
 * Deterministic node id assignment. Precedence: explicit `node.id` > lexicon slug
 * hint > latinized camelCase transliteration of the name > role > kind + ordinal.
 * Ids are unique document-wide: generated collisions get a `-2`/`-3`... suffix
 * (warning when colliding with an explicit id); two identical explicit ids are
 * an error.
 */
class SlugGenerator(private val diagnostics: DiagnosticCollector) {
    private val used = mutableSetOf<String>()
    private val explicitIds = mutableSetOf<String>()
    private val kindOrdinals = mutableMapOf<String, Int>()

    fun idFor(
        explicitId: String?,
        slugHint: String = "",
        name: String = "",
        role: String = "",
        kind: String = "node",
        line: Int = 0,
    ): String {
        if (!explicitId.isNullOrBlank()) {
            if (explicitId in explicitIds) {
                diagnostics.error("Duplicate explicit node id \"$explicitId\"", line)
                return unique(explicitId, line, collidesExplicit = false)
            }
            explicitIds += explicitId
            if (explicitId in used) {
                // A generated id claimed this name first; the explicit id still wins its
                // exact spelling — regenerate would break authored references. Suffix the
                // explicit one deterministically and warn.
                diagnostics.warning(
                    "Explicit id \"$explicitId\" collides with a generated id; using suffix",
                    line,
                )
                return unique(explicitId, line, collidesExplicit = false)
            }
            used += explicitId
            return explicitId
        }
        val base = firstNonBlank(
            slugHint,
            latinizeToCamelCase(name),
            latinizeToCamelCase(role),
        ) ?: syntheticBase(kind)
        return unique(base, line, collidesExplicit = base in explicitIds)
    }

    private fun syntheticBase(kind: String): String {
        val ordinal = (kindOrdinals[kind] ?: 0) + 1
        kindOrdinals[kind] = ordinal
        return "$kind$ordinal"
    }

    private fun unique(base: String, line: Int, collidesExplicit: Boolean): String {
        if (base !in used) {
            used += base
            return base
        }
        if (collidesExplicit) {
            diagnostics.warning(
                "Generated id \"$base\" collides with an explicit id; using suffix",
                line,
            )
        }
        var index = 2
        while ("$base-$index" in used) index++
        val id = "$base-$index"
        used += id
        return id
    }

    private fun firstNonBlank(vararg candidates: String): String? =
        candidates.firstOrNull { it.isNotBlank() }
}

private val cyrillicToLatin: Map<Char, String> = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
    'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
    'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
    'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch",
    'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
)

/**
 * Transliterates RU text to Latin and folds it to a camelCase identifier:
 * `Создать миссию` -> `sozdatMissiyu`, `Mission Detail Panel` -> `missionDetailPanel`.
 */
internal fun latinizeToCamelCase(text: String): String {
    val latin = buildString {
        text.forEach { char ->
            val lower = char.lowercaseChar()
            val mapped = cyrillicToLatin[lower]
            when {
                mapped != null -> append(if (char.isUpperCase()) mapped.replaceFirstChar { it.uppercaseChar() } else mapped)
                else -> append(char)
            }
        }
    }
    val words = latin
        .split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotEmpty() }
    if (words.isEmpty()) return ""
    val joined = words
        .mapIndexed { index, word ->
            val lower = word.lowercase()
            if (index == 0) lower else lower.replaceFirstChar { it.uppercaseChar() }
        }
        .joinToString("")
    return if (joined.first().isDigit()) "n$joined" else joined
}
