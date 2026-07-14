package io.aequicor.visualization.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlmSkillBundleTest {
    @Test
    fun allIncludesBaseAndEverySpecialistInCanonicalOrder() {
        val bundle = getSlmSkillBundle()

        assertEquals(
            listOf("slm", "diagrams", "vector_graphics", "typography", "annotations", "editor"),
            bundle.includedSkills,
        )
        assertEquals(
            listOf("slm", "slm-diagrams", "slm-vector-graphics", "slm-typography", "slm-annotations", "slm-editor"),
            bundle.files.map { it.name },
        )
        assertTrue(bundle.files.all { it.markdown.startsWith("---") })
        assertTrue("## Included skill: SLM" in bundle.markdown)
        assertTrue("## Included skill: SLM editor" in bundle.markdown)
    }

    @Test
    fun focusedBundleIncludesBaseAndOnlyRequestedSpecialist() {
        val bundle = getSlmSkillBundle("vector-graphics")

        assertEquals(listOf("slm", "vector_graphics"), bundle.includedSkills)
        assertEquals(listOf("slm", "slm-vector-graphics"), bundle.files.map { it.name })
        assertTrue("## Included skill: SLM vector graphics" in bundle.markdown)
        assertFalse("## Included skill: SLM diagrams" in bundle.markdown)
    }

    @Test
    fun baseSkillKeepsEditableFramesAndExplainsRealCentering() {
        val base = getSlmSkillBundle("slm").files.single().markdown

        assertTrue("Use a free `Frame` by default" in base)
        assertTrue("## Frame-first geometry and alignment" in base)
        assertTrue("do **not** calculate the" in base)
        assertTrue("initial position and do **not** move" in base)
        assertTrue("`text-align` and `text-valign` align glyphs inside" in base)
        assertTrue("render the full screen, inspect the PNG at 100%" in base)
    }

    @Test
    fun rejectsUnknownSkill() {
        assertFailsWith<IllegalArgumentException> { getSlmSkillBundle("unknown") }
    }
}
