package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.domain.LoadDesignDocumentUseCase
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.engine.ir.layout.ApproximateTextMeasurer
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.projection.ScreenComposer
import io.aequicor.visualization.engine.scene.runtime.PointerKind
import io.aequicor.visualization.engine.scene.runtime.SceneEntry
import io.aequicor.visualization.engine.scene.runtime.SceneEvent
import io.aequicor.visualization.engine.scene.runtime.SceneRuntime
import io.aequicor.visualization.engine.scene.runtime.SequentialIdGenerator
import io.aequicor.visualization.engine.scene.runtime.TraceEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The bundled Welcome tour plays in the Scene runtime: each screen's root `afterDelay`
 * timer navigates to the next screen with an animated transition, closing the loop
 * welcomeEditor → welcomeVectors → welcomeUml → welcomeEditor, and the Next button
 * navigates on click.
 */
class WelcomeTourSceneTest {

    private fun runtime(): SceneRuntime {
        val document = assertNotNull(
            createDesignEditorState(LoadDesignDocumentUseCase(DefaultDesignDocumentRepository())()).document,
        )
        val composer = ScreenComposer(document, DesignLayoutEngine(ApproximateTextMeasurer()))
        return SceneRuntime(document, composer, SequentialIdGenerator())
    }

    @Test
    fun afterDelayTourWalksAllThreeScreensAndLoops() {
        val runtime = runtime()
        var session = runtime.start(SceneEntry.Screen("welcomeEditor")).session
        val visited = mutableListOf<String>()
        // Step in sub-delay increments for ~40s of scene time: enough for three 9s hops
        // plus their 500-600ms transitions.
        repeat(80) {
            session = runtime.advance(session, 500.0).session
            val screen = session.runtime.location.screenId
            if (visited.lastOrNull() != screen) visited += screen
        }
        assertEquals(
            listOf("welcomeEditor", "welcomeVectors", "welcomeUml", "welcomeEditor", "welcomeVectors"),
            visited.take(5),
            "tour loops through the three Welcome screens; visited: $visited",
        )
    }

    @Test
    fun nextButtonNavigatesOnClick() {
        val runtime = runtime()
        val start = runtime.start(SceneEntry.Screen("welcomeEditor")).session
        val clicked = runtime.dispatch(start, SceneEvent.Pointer(PointerKind.Click, "wel_nav_next"))
        assertTrue(
            clicked.trace.any { it is TraceEntry.TransitionStarted },
            "Next click starts a transition; trace: ${clicked.trace}",
        )
        val arrived = runtime.advance(clicked.session, 600.0).session
        assertEquals("welcomeVectors", arrived.runtime.location.screenId)
    }
}
