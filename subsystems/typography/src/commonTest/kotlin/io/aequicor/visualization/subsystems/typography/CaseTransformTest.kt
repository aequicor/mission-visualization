package io.aequicor.visualization.subsystems.typography

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [CaseTransform] coverage: length-changing case mappings (`ß` -> `SS`), the
 * bidirectional offset map, title-case word boundaries and span/link projection.
 */
class CaseTransformTest {

    private val bold = TypographyStyle(fontWeight = 700)

    // --- upper / lower ---

    @Test
    fun upperExpandsSharpSToDoubleS() {
        val transformed = CaseTransform.apply("aßb", TextCasing.Upper)
        assertEquals("ASSB", transformed.text)
        assertEquals(listOf(0, 1, 3, 4), transformed.sourceToTransformed.toList())
    }

    @Test
    fun lowerMapsPerCharacter() {
        val transformed = CaseTransform.apply("AbC", TextCasing.Lower)
        assertEquals("abc", transformed.text)
        assertEquals(listOf(0, 1, 2, 3), transformed.sourceToTransformed.toList())
    }

    // --- title ---

    @Test
    fun titleCapitalizesWordStartsWithoutLoweringRest() {
        assertEquals("Hello WORLD", CaseTransform.apply("hello WORLD", TextCasing.Title).text)
    }

    @Test
    fun titleTreatsDigitsAsWordContinuation() {
        // A digit neither gets capitalized nor starts a new word.
        assertEquals("3d Graphics", CaseTransform.apply("3d graphics", TextCasing.Title).text)
        assertEquals("Abc123def", CaseTransform.apply("abc123def", TextCasing.Title).text)
    }

    @Test
    fun titleTreatsPunctuationAsWordBoundary() {
        assertEquals("Foo-Bar (Baz)", CaseTransform.apply("foo-bar (baz)", TextCasing.Title).text)
    }

    // --- identity casings ---

    @Test
    fun noneAndSmallCapsVariantsAreIdentity() {
        listOf(TextCasing.None, TextCasing.SmallCaps, TextCasing.SmallCapsForced).forEach { casing ->
            val transformed = CaseTransform.apply("Mixed ßtring", casing)
            assertEquals("Mixed ßtring", transformed.text)
            assertEquals((0..12).toList(), transformed.sourceToTransformed.toList())
        }
    }

    // --- offset projection ---

    @Test
    fun toTransformedAndToSourceRoundTripThroughExpansions() {
        val transformed = CaseTransform.apply("aßbß", TextCasing.Upper)
        assertEquals("ASSBSS", transformed.text)
        for (source in 0.."aßbß".length) {
            assertEquals(source, transformed.toSource(transformed.toTransformed(source)))
        }
    }

    @Test
    fun toSourceSnapsInsideExpansionToItsSourceCharacter() {
        val transformed = CaseTransform.apply("aßb", TextCasing.Upper) // map [0, 1, 3, 4]
        assertEquals(1, transformed.toSource(1))
        assertEquals(1, transformed.toSource(2)) // between the two S of the expansion
        assertEquals(2, transformed.toSource(3))
    }

    @Test
    fun offsetProjectionClampsOutOfRangeInput() {
        val transformed = CaseTransform.apply("aßb", TextCasing.Upper)
        assertEquals(0, transformed.toTransformed(-1))
        assertEquals(4, transformed.toTransformed(99))
        assertEquals(0, transformed.toSource(-1))
        assertEquals(3, transformed.toSource(99))
    }

    // --- span / link projection ---

    @Test
    fun projectSpansStretchesRangesOverExpansions() {
        val transformed = CaseTransform.apply("aßb", TextCasing.Upper)
        val spans = listOf(
            StyleSpan(0, 1, bold),
            StyleSpan(1, 2, bold), // exactly the ß -> covers both S
            StyleSpan(2, 3, bold),
        )
        assertEquals(
            listOf(StyleSpan(0, 1, bold), StyleSpan(1, 3, bold), StyleSpan(3, 4, bold)),
            CaseTransform.projectSpans(spans, transformed),
        )
    }

    @Test
    fun projectSpansDropsCollapsedSpans() {
        val transformed = CaseTransform.apply("abc", TextCasing.Upper)
        assertEquals(
            emptyList(),
            CaseTransform.projectSpans(listOf(StyleSpan(1, 1, bold)), transformed),
        )
    }

    @Test
    fun projectLinksProjectsLikeSpans() {
        val transformed = CaseTransform.apply("aßb", TextCasing.Upper)
        assertEquals(
            listOf(LinkSpan(1, 3, url = "https://x")),
            CaseTransform.projectLinks(listOf(LinkSpan(1, 2, url = "https://x")), transformed),
        )
        assertEquals(
            emptyList(),
            CaseTransform.projectLinks(listOf(LinkSpan(2, 2, url = "https://x")), transformed),
        )
    }

    @Test
    fun emptyTextTransformsToEmpty() {
        val transformed = CaseTransform.apply("", TextCasing.Upper)
        assertEquals("", transformed.text)
        assertEquals(0, transformed.toTransformed(0))
        assertEquals(0, transformed.toSource(0))
    }
}
