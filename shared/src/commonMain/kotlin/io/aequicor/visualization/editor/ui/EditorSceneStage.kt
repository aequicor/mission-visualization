package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.MissionEditorStateHolder
import io.aequicor.visualization.editor.presentation.SceneController
import io.aequicor.visualization.editor.presentation.sceneEntryOf
import io.aequicor.visualization.editor.ui.strings.LocalStrings
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.backend.compose.CanvasViewport
import io.aequicor.visualization.engine.backend.compose.SceneRenderer
import io.aequicor.visualization.engine.backend.compose.rememberDesignTextMeasurer
import io.aequicor.visualization.subsystems.typography.compose.rememberBundledFontProvider
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.projection.ScreenComposer
import io.aequicor.visualization.engine.scene.projection.SceneProjection
import io.aequicor.visualization.editor.platform.platformOpenUrl
import io.aequicor.visualization.engine.scene.runtime.PlaybackState
import io.aequicor.visualization.engine.scene.runtime.SceneEntry
import io.aequicor.visualization.engine.scene.runtime.SceneRuntime
import io.aequicor.visualization.engine.scene.runtime.SequentialIdGenerator
import io.aequicor.visualization.engine.scene.runtime.TraceEntry

/** Upper bound on one frame's delta (≈4 frames at 60 fps) so a throttled clock can't jump animations. */
private const val MaxFrameDeltaMs: Double = 64.0

/**
 * Scene-mode canvas: builds a [SceneController] over the pure runtime, drives it from the Compose
 * frame clock, renders the scene under the shared [viewport], and shows timeline transport + a live
 * debug trace. Selection/edit handles are not drawn here (Scene mode hides them). Leaving Scene mode
 * removes this composable, which cancels the clock loop and drops the runtime — playback stops with
 * no effect on the document.
 */
@Composable
fun SceneStage(state: MissionEditorStateHolder, viewport: CanvasViewport, modifier: Modifier = Modifier) {
    val design = state.designState
    val document = design.document
    val ws = state.workspace
    val fontProvider = rememberBundledFontProvider()
    val measurer = rememberDesignTextMeasurer(fontProvider)

    if (document == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(LocalStrings.current.canvas.noPreview, color = LocalEditorColors.current.mutedInk)
        }
        return
    }

    val controller = remember(document, ws.deviceMode, measurer) {
        val engine = DesignLayoutEngine(measurer)
        val composer = ScreenComposer(document, engine)
        val runtime = SceneRuntime(
            document = document,
            composer = composer,
            idGen = SequentialIdGenerator(),
            deviceWidth = ws.deviceMode.width,
            deviceHeight = ws.deviceMode.height,
        )
        SceneController(runtime, SceneProjection(composer))
    }

    // Start the scene from the selected flow/screen when this controller comes into being.
    LaunchedEffect(controller) { controller.start(sceneEntryOf(design)) }

    // Compose frame clock: feed real deltas to the pure runtime. Cancels when Scene mode leaves.
    LaunchedEffect(controller) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    // Clamp the per-frame delta: a backgrounded tab or dropped frames make
                    // withFrameNanos resume with a multi-second gap, which would teleport a
                    // transition/motion past its end in a single step. Cap it to a few frames.
                    val deltaMs = ((now - last) / 1_000_000.0).coerceIn(0.0, MaxFrameDeltaMs)
                    controller.onFrame(deltaMs)
                }
                last = now
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        val model = controller.renderModel
        if (model != null) {
            SceneRenderer(
                renderModel = model,
                viewport = viewport,
                modifier = Modifier.fillMaxSize(),
                fontProvider = fontProvider,
                onLinkClick = { link ->
                    // External URL opens in the browser; an internal node target navigates the scene.
                    if (link.url.isNotBlank()) {
                        platformOpenUrl(link.url)
                    } else if (link.nodeTarget.isNotBlank()) {
                        controller.start(SceneEntry.Screen(link.nodeTarget))
                    }
                },
                onEvent = controller::onEvent,
            )
        }
        SceneTracePanel(
            trace = controller.trace,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        )
        SceneTimelineBar(
            controller = controller,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun SceneTimelineBar(controller: SceneController, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    Surface(
        modifier = modifier.widthIn(max = 620.dp),
        shape = RoundedCornerShape(10.dp),
        color = colors.raisedSurface,
        border = BorderStroke(1.dp, colors.panelStroke),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val playing = controller.playback == PlaybackState.Playing
            TimelineButton(if (playing) "❚❚" else "▶") { if (playing) controller.pause() else controller.play() }
            TimelineButton("⟳") { controller.restart() }

            val duration = controller.durationMs
            val value = if (duration > 0.0) (controller.currentTimeMs % duration).toFloat() else 0f
            Slider(
                value = value.coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                onValueChange = { controller.seek(it.toDouble()) },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                enabled = duration > 0.0,
                modifier = Modifier.widthIn(min = 180.dp, max = 300.dp),
            )

            SpeedControl(controller.speed) { controller.setSpeed(it) }
        }
    }
}

@Composable
private fun SpeedControl(speed: Double, onSpeed: (Double) -> Unit) {
    val colors = LocalEditorColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(1.0, 0.5, 0.25).forEach { option ->
            val active = kotlin.math.abs(speed - option) < 1e-6
            Box(
                modifier = Modifier
                    .background(if (active) colors.accent else colors.controlSurface, RoundedCornerShape(6.dp))
                    .clickable { onSpeed(option) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (option == 1.0) "1×" else "${option}×",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) androidx.compose.ui.graphics.Color.White else colors.ink,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TimelineButton(label: String, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(colors.accent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SceneTracePanel(trace: List<TraceEntry>, modifier: Modifier = Modifier) {
    if (trace.isEmpty()) return
    val colors = LocalEditorColors.current
    Surface(
        modifier = modifier.widthIn(max = 320.dp),
        shape = RoundedCornerShape(10.dp),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.panelStroke),
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(LocalStrings.current.canvas.trace, style = MaterialTheme.typography.labelMedium, color = colors.mutedInk, fontWeight = FontWeight.Bold)
            Column(
                Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                trace.takeLast(24).forEach { entry ->
                    Text(
                        traceLine(entry),
                        style = MaterialTheme.typography.labelSmall,
                        color = traceColor(entry, colors.ink, colors.statusWarning, colors.statusPositive),
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

private fun traceLine(entry: TraceEntry): String {
    val t = "${entry.timeMs.toInt()}ms"
    return when (entry) {
        is TraceEntry.EventReceived -> "$t · event ${entry.event}"
        is TraceEntry.HitTarget -> "$t · hit ${entry.nodeId}"
        is TraceEntry.TriggerMatched -> "$t · trigger ${entry.trigger} @${entry.nodeId}"
        is TraceEntry.ActionExecuted -> "$t · action ${entry.action::class.simpleName}"
        is TraceEntry.VariableSet -> "$t · set ${entry.name} = ${entry.value}"
        is TraceEntry.TransitionStarted -> "$t · → ${entry.from}→${entry.to} (${entry.type})"
        is TraceEntry.TransitionEnded -> "$t · arrived ${entry.screenId}"
        is TraceEntry.Ignored -> "$t · ignored: ${entry.reason}"
    }
}

private fun traceColor(
    entry: TraceEntry,
    ink: androidx.compose.ui.graphics.Color,
    warning: androidx.compose.ui.graphics.Color,
    positive: androidx.compose.ui.graphics.Color,
): androidx.compose.ui.graphics.Color = when (entry) {
    is TraceEntry.Ignored -> warning
    is TraceEntry.TransitionStarted, is TraceEntry.TransitionEnded -> positive
    else -> ink
}
