package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.compileSlm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScreenFrameWriteBackTest {
    @Test
    fun resizesInlineFrameUsedByExistingMissionFiles() {
        val source = """
            ---
            screen: recorderPip
            page: Recording
            frame: { width: 128, height: 296 }
            ---

            # Recorder PiP

            ## Frame: id controls name «Transport Controls» 60 by 155 position 0 31
        """.trimIndent() + "\n"
        val compiled = compileSlm(source)
        val result = applySlmEdit(
            source = source,
            edit = SetScreenFrame("recorderPip", width = 78.7, height = 223.3),
            compiled = compiled,
        )

        val patched = assertNotNull(result.newSource, result.diagnostics.joinToString("\n") { it.message })
        assertTrue("frame: { width: 78.7, height: 223.3 }" in patched, patched)
        assertTrue("page: Recording" in patched, patched)
        val recompiled = compileSlm(patched)
        val root = assertNotNull(recompiled.document?.nodeById("recorderPip"))
        assertEquals(78.7, root.size.width)
        assertEquals(223.3, root.size.height)
    }

    @Test
    fun syntheticScreenRootWritesFrameFrontmatterWithoutTouchingBody() {
        val body = """
            # Recorder PiP

            ## Frame: Transport Controls id controls 128 by 214 position 0 82
        """.trimIndent() + "\n"
        val source = """
            ---
            screen: recorderPip
            sourceLocale: ru-RU
            ---

        """.trimIndent() + body
        val compiled = compileSlm(source)

        val result = applySlmEdit(
            source = source,
            edit = SetScreenFrame("recorderPip", width = 78.7, height = 223.3),
            compiled = compiled,
        )

        val patched = assertNotNull(result.newSource, result.diagnostics.joinToString("\n") { it.message })
        assertTrue("frame:\n  width: 78.7\n  height: 223.3\n---" in patched, patched)
        assertEquals(body, patched.substring(patched.indexOf("# Recorder PiP")), "screen body stays byte-identical")
        val recompiled = compileSlm(patched)
        val document = assertNotNull(recompiled.document)
        val root = assertNotNull(document.nodeById("recorderPip"))
        assertEquals(78.7, root.size.width)
        assertEquals(223.3, root.size.height)
        assertEquals(78.7, document.screen?.frame?.width)
        assertEquals(223.3, document.screen?.frame?.height)
    }

    @Test
    fun existingFrameKeepsPresetHeightAndCommentDuringWidthEdit() {
        val source = "---\r\n" +
            "screen: recorderPip\r\n" +
            "frame:\r\n" +
            "  preset: compact\r\n" +
            "  width: 128  # authored width\r\n" +
            "  height: 296\r\n" +
            "---\r\n\r\n" +
            "# Recorder PiP\r\n"
        val compiled = compileSlm(source)

        val result = applySlmEdit(
            source = source,
            edit = SetScreenFrame("recorderPip", width = 96.5),
            compiled = compiled,
        )

        val patched = assertNotNull(result.newSource, result.diagnostics.joinToString("\n") { it.message })
        assertTrue("  preset: compact\r\n" in patched, patched)
        assertTrue("  width: 96.5  # authored width\r\n" in patched, patched)
        assertTrue("  height: 296\r\n" in patched, patched)
        val recompiled = compileSlm(patched)
        val root = assertNotNull(recompiled.document).nodeById("recorderPip")
        assertEquals(96.5, root?.size?.width)
        assertEquals(296.0, root?.size?.height)
    }

    @Test
    fun cnlScreenRootKeepsHeadingSizeInSyncWithFrontmatter() {
        val source = """
            ---
            screen: recorderPip
            frame:
              width: 128
              height: 296
            ---

            # Recorder PiP id recorderPip name «Recorder PiP» 128 by 296 position 0 0

            ## Rectangle: Child id child 40 by 60 position 8 12 color #FF0000
        """.trimIndent() + "\n"
        val compiled = compileSlm(source)
        val rootBefore = assertNotNull(compiled.document?.nodeById("recorderPip"))
        val childBefore = assertNotNull(compiled.document?.nodeById("child"))

        val result = applySlmEdit(
            source = source,
            edit = SetScreenFrame("recorderPip", width = 160.5, height = 320.25),
            compiled = compiled,
            patchedNode = rootBefore.copy(
                size = rootBefore.size.copy(width = 160.5, height = 320.25),
            ),
        )

        val patched = assertNotNull(result.newSource, result.diagnostics.joinToString("\n") { it.message })
        assertTrue("  width: 160.5\n  height: 320.25" in patched, patched)
        assertTrue("# Recorder PiP id recorderPip name «Recorder PiP» 160.5 by 320.25" in patched, patched)
        val recompiled = compileSlm(patched)
        val root = assertNotNull(recompiled.document?.nodeById("recorderPip"))
        assertEquals(160.5, root.size.width)
        assertEquals(320.25, root.size.height)
        val childAfter = assertNotNull(recompiled.document?.nodeById("child"))
        assertEquals(childBefore.position, childAfter.position, "resizing the frame must not move its child")
        assertEquals(childBefore.size, childAfter.size, "resizing the frame must not scale its child")
        assertEquals(childBefore.constraints, childAfter.constraints, "resizing the frame must not rewrite child constraints")
    }
}
