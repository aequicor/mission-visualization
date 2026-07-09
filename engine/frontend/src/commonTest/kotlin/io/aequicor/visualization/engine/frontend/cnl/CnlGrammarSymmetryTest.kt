package io.aequicor.visualization.engine.frontend.cnl

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Keeps the two directions of the grammar in lockstep: every canonical spelling the emitter
 * ([CnlGrammar]) produces must be a word the parser ([CnlVocabulary]) understands. If a phase
 * adds an emitter keyword without a parser entry (or vice versa), this fails.
 */
class CnlGrammarSymmetryTest {
    @Test
    fun everyEmitterKeywordIsAParserKeyword() {
        CnlGrammar.descriptors.filter { it.keyword.isNotEmpty() }.forEach { descriptor ->
            // The parser matcher lowercases tokens, so a camelCase emit keyword (e.g. maxLines) parses fine.
            assertTrue(
                descriptor.keyword.lowercase() in CnlVocabulary.propertyKeywords,
                "emitter keyword \"${descriptor.keyword}\" is not in the parser vocabulary",
            )
        }
    }

    @Test
    fun canonicalNounsAreParserNouns() {
        listOf(
            "Rectangle", "Ellipse", "Line", "Star", "Polygon", "Arrow",
            "Text", "Button", "Frame", "Image", "Vector",
        ).forEach { noun ->
            assertTrue(noun.lowercase() in CnlVocabulary.nouns, "canonical noun \"$noun\" is not a parser noun")
        }
    }

    @Test
    fun keywordlessCanonicalWordsAreParsed() {
        listOf("column", "row", "grid").forEach {
            assertTrue(it in CnlVocabulary.directions, "direction word \"$it\" is not parsed")
        }
        listOf("bold", "semibold", "thin").forEach {
            assertTrue(it in CnlVocabulary.fontWeights, "weight word \"$it\" is not parsed")
        }
        listOf("outside", "center").forEach {
            assertTrue(it in CnlVocabulary.strokeAligns, "stroke-align word \"$it\" is not parsed")
        }
    }
}
