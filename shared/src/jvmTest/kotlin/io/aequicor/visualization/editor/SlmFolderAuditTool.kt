package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import java.io.File
import kotlin.system.measureTimeMillis
import kotlin.test.Test

/**
 * Env-gated audit over a whole project folder — the same compile the desktop folder-open runs
 * (`compileMissionDocuments` over every `*.layout.md`), timed per file. No-op without env vars.
 *
 * ```
 * SLM_FOLDER_AUDIT_DIR=/path/to/project ./gradlew :shared:jvmTest --tests "*SlmFolderAuditTool*"
 * ```
 *
 * Exists because a folder that compiles slowly (or hangs) freezes the desktop app on click:
 * the compile runs synchronously on the caller's thread, so the only way to see WHERE the time
 * goes is to run the identical work headless.
 */
class SlmFolderAuditTool {

    @Test
    fun audit() {
        val dir = System.getenv("SLM_FOLDER_AUDIT_DIR") ?: return
        // Recursive, matching JvmFolderSync.refreshSnapshot's Files.walk: subfolder layouts are
        // part of the real snapshot, and a top-level-only audit silently skips them.
        val files = File(dir).walkTopDown()
            .filter { it.isFile && it.name.endsWith(".layout.md") }
            .sortedBy { it.path }
            .toList()

        // Per-file first: a hang points at its file by being the last START printed.
        val sources = files.map { file ->
            println("=== START ${file.relativeTo(File(dir))} (${file.length()} bytes)")
            val source = MissionDocumentSource(file.relativeTo(File(dir)).path.replace(java.io.File.separatorChar, '/'), file.readText())
            val ms = measureTimeMillis {
                val docs = compileMissionDocuments(listOf(source))
                val errors = docs.diagnostics.filter { it.severity == DesignSeverity.Error }
                println("    hasErrors=${docs.hasErrors} errors=${errors.size} diags=${docs.diagnostics.size}")
                errors.take(6).forEach { println("    ERROR ${it.location?.file}:${it.location?.line} ${it.message}") }
            }
            println("=== DONE ${file.relativeTo(File(dir))} in ${ms}ms")
            source
        }

        println("=== START merged compile (the exact desktop folder-open path)")
        val totalMs = measureTimeMillis {
            val docs = compileMissionDocuments(sources)
            println("    merged hasErrors=${docs.hasErrors}")
            docs.diagnostics.filter { it.severity == DesignSeverity.Error }.take(10).forEach {
                println("    ERROR ${it.location?.file}:${it.location?.line} ${it.message}")
            }
        }
        println("=== DONE merged in ${totalMs}ms")
    }

    /**
     * The rest of the desktop folder-ADOPTION path, which the compile-only audit skips:
     * connect → platform snapshot JSON → decode → compile → editor state. A failure at any of
     * these stages leaves the real app stuck on the landing with no dialog (the click "does
     * nothing"), so each stage prints and asserts separately.
     */
    @Test
    fun adoption() {
        val dir = System.getenv("SLM_FOLDER_AUDIT_DIR") ?: return
        io.aequicor.visualization.editor.platform.platformConnectFolderByIdForTest(dir)
        println("=== connect status=${io.aequicor.visualization.editor.platform.folderSyncStatus()} error=${io.aequicor.visualization.editor.platform.folderSyncError()}")
        val json = io.aequicor.visualization.editor.platform.folderSyncSnapshotJson()
        println("=== snapshot json: ${json?.length ?: "null"} chars")
        checkNotNull(json) { "no snapshot json after connect" }
        val snapshot = io.aequicor.visualization.editor.data.decodeProjectSnapshot(json)
        println("=== snapshot decode: ${snapshot?.sources?.size ?: "null"} sources, name=${snapshot?.projectName}")
        checkNotNull(snapshot) { "snapshot json did not decode" }
        val docs = compileMissionDocuments(snapshot.sources)
        println("=== compile hasErrors=${docs.hasErrors}")
        val ms = measureTimeMillis {
            val state = io.aequicor.visualization.editor.presentation.createDesignEditorState(docs)
            println("=== editor state: pages=${state.document?.pages?.size} selected=${state.selectedPageId}")
        }
        println("=== DONE editor state in ${ms}ms")
        io.aequicor.visualization.editor.platform.platformResetFolderSyncForTest()
    }
}
