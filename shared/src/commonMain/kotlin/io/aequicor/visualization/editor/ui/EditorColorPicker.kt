package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.Hsva
import io.aequicor.visualization.engine.ir.model.toDesignColor
import io.aequicor.visualization.engine.ir.model.toHsva
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Figma-like color control: a compact row (swatch + hex field + opacity) that expands into an
 * anchored [Popup] with an HSV square, hue and alpha sliders, RGB / hex fields and recent
 * swatches. Pure UI: every change is reported through [onChange]; the popup open/close bracket
 * a single live edit session via [onEditStart] / [onEditEnd]. The color is treated as opaque RGB
 * (its own alpha byte is ignored); fill opacity travels separately in [alpha] (0f..1f).
 */
@Composable
internal fun ColorPickerField(
    rgb: DesignColor,
    alpha: Float,
    label: String,
    recent: List<DesignColor>,
    enabled: Boolean = true,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
    onChange: (rgb: DesignColor, alpha: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEditorColors.current
    val density = LocalDensity.current

    val latestOnChange by rememberUpdatedState(onChange)
    val latestOnEditStart by rememberUpdatedState(onEditStart)
    val latestOnEditEnd by rememberUpdatedState(onEditEnd)

    var open by remember { mutableStateOf(false) }
    // Working HSV is the source of truth while the panel is open; (re)seeded from the current props
    // by openPanel() every time the panel opens, so a session always starts from the live color.
    var working by remember { mutableStateOf(seedHsva(rgb, alpha)) }
    var openRgb by remember { mutableStateOf(rgb) }
    var openAlpha by remember { mutableStateOf(alpha) }

    // Emit a working change: rgb is forced opaque, opacity travels in the separate alpha argument.
    fun emit(next: Hsva) {
        working = next
        latestOnChange(next.copy(alpha = 1f).toDesignColor(), next.alpha.coerceIn(0f, 1f))
    }

    fun openPanel() {
        if (!enabled) return
        working = seedHsva(rgb, alpha)
        openRgb = rgb
        openAlpha = alpha.coerceIn(0f, 1f)
        open = true
        latestOnEditStart()
    }

    fun closePanel(restore: Boolean) {
        if (restore) {
            val restored = seedHsva(openRgb, openAlpha)
            working = restored
            latestOnChange(restored.copy(alpha = 1f).toDesignColor(), restored.alpha)
        }
        open = false
        latestOnEditEnd()
    }

    fun commitHex(color: DesignColor) {
        val keepAlpha = if (open) working.alpha else alpha.coerceIn(0f, 1f)
        emit(color.toHsva().copy(alpha = keepAlpha))
    }

    fun setChannel(r: Int? = null, g: Int? = null, b: Int? = null) {
        val base = working.copy(alpha = 1f).toDesignColor()
        val nr = (r ?: base.red).coerceIn(0, 255).toLong()
        val ng = (g ?: base.green).coerceIn(0, 255).toLong()
        val nb = (b ?: base.blue).coerceIn(0, 255).toLong()
        val argb = (0xFFL shl 24) or (nr shl 16) or (ng shl 8) or nb
        emit(DesignColor(argb).toHsva().copy(alpha = working.alpha))
    }

    // Effective color for the always-visible row (working while open, incoming props while closed).
    val effective = if (open) working else seedHsva(rgb, alpha)
    val opaque = effective.copy(alpha = 1f).toDesignColor()
    val shownColor = opaque.toComposeColor().copy(alpha = effective.alpha.coerceIn(0f, 1f))
    val shownHex = opaque.toHex()
    val percent = (effective.alpha.coerceIn(0f, 1f) * 100f).roundToInt()

    Box(modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            shape = RoundedCornerShape(6.dp),
            color = colors.controlSurface,
            border = BorderStroke(1.dp, colors.controlStroke),
        ) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CheckerSwatch(
                    color = shownColor,
                    modifier = Modifier.size(20.dp),
                    onClick = if (enabled) { { if (open) closePanel(restore = false) else openPanel() } } else null,
                )
                HexTextField(
                    text = if (open) shownHex else label,
                    enabled = enabled,
                    onCommit = { commitHex(it) },
                    modifier = Modifier.padding(start = 6.dp).weight(1f),
                )
                EditorSvgIcon(
                    icon = EditorIcon.ColorSelector,
                    contentDescription = if (open) "Close color selector" else "Open color selector",
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(17.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(enabled = enabled) { if (open) closePanel(restore = false) else openPanel() },
                    tint = if (enabled) colors.controlInk else colors.mutedInk,
                )
                Text("$percent%", style = MaterialTheme.typography.bodySmall, color = colors.mutedInk)
            }
        }

        if (open) {
            val offsetY = with(density) { 40.dp.roundToPx() }
            Popup(
                offset = IntOffset(0, offsetY),
                onDismissRequest = { closePanel(restore = false) },
                properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
            ) {
                val panelFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { panelFocus.requestFocus() } }
                Surface(
                    modifier = Modifier.width(248.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.raisedSurface,
                    border = BorderStroke(1.dp, colors.panelStroke),
                    shadowElevation = 10.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .focusRequester(panelFocus)
                            .focusTarget()
                            .onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.Escape -> { closePanel(restore = true); true }
                                    Key.Enter, Key.NumPadEnter -> { closePanel(restore = false); true }
                                    else -> false
                                }
                            },
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SaturationValueSquare(hsv = working, onChange = { emit(it) })
                        HueSlider(hue = working.hue, onHue = { emit(working.copy(hue = it)) })
                        AlphaSlider(hsv = working, onAlpha = { emit(working.copy(alpha = it)) })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChannelField("R", opaque.red, { setChannel(r = it) }, Modifier.weight(1f))
                            ChannelField("G", opaque.green, { setChannel(g = it) }, Modifier.weight(1f))
                            ChannelField("B", opaque.blue, { setChannel(b = it) }, Modifier.weight(1f))
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(30.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = colors.controlSurface,
                            border = BorderStroke(1.dp, colors.controlStroke),
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("HEX", style = MaterialTheme.typography.labelSmall, color = colors.mutedInk)
                                HexTextField(
                                    text = shownHex,
                                    enabled = true,
                                    onCommit = { commitHex(it) },
                                    modifier = Modifier.padding(start = 6.dp).weight(1f),
                                )
                            }
                        }
                        if (recent.isNotEmpty()) {
                            RecentSwatchRow(
                                recent = recent,
                                onPick = { picked -> emit(picked.toHsva().copy(alpha = working.alpha)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Seeds working HSV from an opaque RGB [color] but with the externally tracked [a] opacity. */
private fun seedHsva(color: DesignColor, a: Float): Hsva = color.toHsva().copy(alpha = a.coerceIn(0f, 1f))

// --- Saturation / value square -----------------------------------------------

/** 2D pad: X = saturation, Y = 1 - value; base is the pure hue tinted by white then black overlays. */
@Composable
private fun SaturationValueSquare(hsv: Hsva, onChange: (Hsva) -> Unit, modifier: Modifier = Modifier) {
    val latest by rememberUpdatedState(hsv)
    val onEdit by rememberUpdatedState(onChange)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun apply(pos: Offset) {
                        val s = (pos.x / size.width).coerceIn(0f, 1f)
                        val v = (1f - pos.y / size.height).coerceIn(0f, 1f)
                        onEdit(latest.copy(saturation = s, value = v))
                    }
                    apply(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.changedToUp()) break
                        apply(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        drawRect(Hsva(hsv.hue, 1f, 1f, 1f).toDesignColor().toComposeColor())
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val cx = hsv.saturation.coerceIn(0f, 1f) * size.width
        val cy = (1f - hsv.value.coerceIn(0f, 1f)) * size.height
        val fill = hsv.copy(alpha = 1f).toDesignColor().toComposeColor()
        drawCircle(fill, radius = 6f, center = Offset(cx, cy))
        drawCircle(Color.White, radius = 6f, center = Offset(cx, cy), style = Stroke(2f))
        drawCircle(Color.Black.copy(alpha = 0.5f), radius = 7.5f, center = Offset(cx, cy), style = Stroke(1f))
    }
}

// --- Hue slider --------------------------------------------------------------

/** Rainbow bar; the thumb X encodes hue 0..360. */
@Composable
private fun HueSlider(hue: Float, onHue: (Float) -> Unit, modifier: Modifier = Modifier) {
    val onEdit by rememberUpdatedState(onHue)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun apply(pos: Offset) = onEdit((pos.x / size.width).coerceIn(0f, 1f) * 360f)
                    apply(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.changedToUp()) break
                        apply(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        drawRect(
            Brush.horizontalGradient(
                listOf(
                    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                    Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
                ),
            ),
        )
        drawSliderThumb((hue / 360f) * size.width)
    }
}

// --- Alpha slider ------------------------------------------------------------

/** Opacity bar over a checkerboard; gradient runs from transparent to opaque of the current color. */
@Composable
private fun AlphaSlider(hsv: Hsva, onAlpha: (Float) -> Unit, modifier: Modifier = Modifier) {
    val onEdit by rememberUpdatedState(onAlpha)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun apply(pos: Offset) = onEdit((pos.x / size.width).coerceIn(0f, 1f))
                    apply(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.changedToUp()) break
                        apply(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        drawCheckerboard(6f)
        val base = hsv.copy(alpha = 1f).toDesignColor().toComposeColor()
        drawRect(Brush.horizontalGradient(listOf(base.copy(alpha = 0f), base.copy(alpha = 1f))))
        drawSliderThumb(hsv.alpha.coerceIn(0f, 1f) * size.width)
    }
}

// --- Numeric / hex fields ----------------------------------------------------

/** One 0..255 channel input; commits whenever the draft is a valid, changed byte. */
@Composable
private fun ChannelField(label: String, value: Int, onCommit: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    var draft by remember(value) { mutableStateOf(value.toString()) }
    Surface(
        modifier = modifier.height(30.dp),
        shape = RoundedCornerShape(6.dp),
        color = colors.controlSurface,
        border = BorderStroke(1.dp, colors.controlStroke),
    ) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.mutedInk)
            BasicTextField(
                value = draft,
                onValueChange = { input ->
                    val sanitized = input.filter { it.isDigit() }.take(3)
                    draft = sanitized
                    sanitized.toIntOrNull()?.let { parsed -> if (parsed in 0..255 && parsed != value) onCommit(parsed) }
                },
                modifier = Modifier.padding(start = 4.dp).weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.ink),
            )
        }
    }
}

/** Hex input; commits a parsed [DesignColor] when the draft is a full 6-digit RRGGBB. */
@Composable
private fun HexTextField(text: String, enabled: Boolean, onCommit: (DesignColor) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    var draft by remember(text) { mutableStateOf(text) }
    BasicTextField(
        value = draft,
        onValueChange = { input ->
            draft = input.take(7)
            val digits = input.trim().removePrefix("#")
            if (Regex("^[0-9a-fA-F]{6}$").matches(digits)) DesignColor.fromHex("#$digits")?.let(onCommit)
        },
        enabled = enabled,
        modifier = modifier,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.ink),
    )
}

// --- Recent swatches ---------------------------------------------------------

/** One-line, horizontally scrollable strip of up to ten recent colors. */
@Composable
private fun RecentSwatchRow(recent: List<DesignColor>, onPick: (DesignColor) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        recent.take(10).forEach { color ->
            CheckerSwatch(
                color = color.toComposeColor().copy(alpha = 1f),
                modifier = Modifier.size(20.dp),
                onClick = { onPick(color) },
            )
        }
    }
}

// --- Shared swatch (color over a checkerboard) -------------------------------

/** A bordered, rounded swatch drawn over a light checkerboard so partial alpha reads. */
@Composable
private fun CheckerSwatch(color: Color, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val colors = LocalEditorColors.current
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .clip(shape)
            .border(1.dp, colors.controlStroke, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCheckerboard(4f)
            drawRect(color)
        }
    }
}

// --- Draw helpers ------------------------------------------------------------

private val CheckerLight = Color(0xFFFFFFFF)
private val CheckerDark = Color(0xFFDDDDDD)

/** Fills the draw area with a two-tone [cell]-sized checkerboard (content colors, not theme tokens). */
private fun DrawScope.drawCheckerboard(cell: Float) {
    drawRect(CheckerLight)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = 0f
        var col = 0
        while (x < size.width) {
            if ((row + col) % 2 == 1) {
                drawRect(
                    CheckerDark,
                    topLeft = Offset(x, y),
                    size = Size(min(cell, size.width - x), min(cell, size.height - y)),
                )
            }
            x += cell
            col++
        }
        y += cell
        row++
    }
}

/** Draws a circular slider thumb (white disc + dark ring) centered vertically at [x]. */
private fun DrawScope.drawSliderThumb(x: Float) {
    val r = size.height / 2f - 0.5f
    val cx = x.coerceIn(r, size.width - r)
    val cy = size.height / 2f
    drawCircle(Color.White, radius = r, center = Offset(cx, cy))
    drawCircle(Color.Black.copy(alpha = 0.55f), radius = r, center = Offset(cx, cy), style = Stroke(1.5f))
}
