package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.decodeEditorStateSnapshot
import io.aequicor.visualization.editor.data.encodeEditorStateSnapshot
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals

class ProjectEditorStateSnapshotTest {
    @Test
    fun visualEditReopensFromSlmWithoutEditorStateSnapshot() {
        val initial = createDesignEditorState(missionDemoDocuments())
        val edited = reduceDesignEditor(
            initial,
            DesignEditorIntent.UpdateOpacity("win_bg", 0.73),
        )

        assertNotEquals(initial.sources, edited.sources)
        val restored = assertNotNull(compileMissionDocuments(edited.sources).document)

        assertEquals(0.73, restored.nodeById("win_bg")?.opacity?.literalOrNull())
    }

    @Test
    fun snapshotIsIgnoredWhenSlmSourcesChangedExternally() {
        val state = createDesignEditorState(missionDemoDocuments())
        val document = assertNotNull(state.document)
        val encoded = encodeEditorStateSnapshot(state.sources, document)
        val changed = state.sources.mapIndexed { index, source ->
            if (index == 0) source.copy(content = source.content + "\n<!-- external -->\n") else source
        }

        assertEquals(null, decodeEditorStateSnapshot(encoded, changed))
    }
}
