package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.ir.model.DesignColor
import kotlin.math.abs
import kotlin.math.roundToInt

// --- Value formatting --------------------------------------------------------

/** Rounds to a whole number when close, otherwise one decimal. */
internal fun Double.formatPx(): String {
    val rounded = roundToInt()
    return if (abs(this - rounded) < 0.05) rounded.toString() else ((this * 10).roundToInt() / 10.0).toString()
}

internal fun DesignColor.toHex(): String {
    fun component(value: Int): String = value.toString(16).uppercase().padStart(2, '0')
    return "#${component(red)}${component(green)}${component(blue)}"
}

internal fun DesignColor.toComposeColor(): Color =
    Color(red = red / 255f, green = green / 255f, blue = blue / 255f, alpha = alpha / 255f)

// --- Tab strip ---------------------------------------------------------------

@Composable
internal fun <T> TabStrip(
    tabs: List<T>,
    selected: T,
    title: (T) -> String,
    icon: (T) -> EditorIcon? = { null },
    onSelect: (T) -> Unit,
) {
    val colors = LocalEditorColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(colors.raisedSurface)
            .border(BorderStroke(1.dp, colors.softStroke)),
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().clickable { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    icon(tab)?.let { tabIcon ->
                        EditorSvgIcon(
                            icon = tabIcon,
                            contentDescription = title(tab),
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelected) colors.accent else colors.controlInk,
                        )
                    }
                    Text(
                        title(tab),
                        color = if (isSelected) colors.accent else Color.Black,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                if (isSelected) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.7f).height(3.dp).background(colors.accent))
                }
            }
        }
    }
}

// --- Numeric fields ----------------------------------------------------------

/**
 * Numeric inspector input bound to a computed value: the draft resets whenever the
 * key (selection/value) changes and valid numbers commit immediately (live editing).
 */
@Composable
internal fun InspectorNumberField(
    label: String,
    value: String,
    suffix: String,
    resetKey: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    onCommit: (Double) -> Unit,
) {
    var draft by remember(resetKey, value) { mutableStateOf(value) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (label.isNotEmpty()) {
            Text(label, modifier = Modifier.widthIn(min = 22.dp), style = MaterialTheme.typography.bodySmall, color = Color.Black)
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { input ->
                val sanitized = input.filter { it.isDigit() || it == '.' || it == '-' }
                draft = sanitized
                sanitized.toDoubleOrNull()?.let { parsed ->
                    if (parsed != value.toDoubleOrNull()) onCommit(parsed)
                }
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodySmall,
            placeholder = if (placeholder.isEmpty()) null else {
                { Text(placeholder, style = MaterialTheme.typography.bodySmall, color = LocalEditorColors.current.mutedInk) }
            },
            trailingIcon = if (suffix.isEmpty()) null else {
                { Text(suffix, style = MaterialTheme.typography.bodySmall, color = Color.Black) }
            },
        )
    }
}

/**
 * Numeric input that commits on Enter or focus loss instead of per keystroke — used
 * where each commit is expensive (SLM source write-back), so intermediate keystrokes
 * on the way to a value must not land.
 */
@Composable
internal fun InspectorCommitNumberField(
    label: String,
    value: String,
    suffix: String,
    resetKey: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCommit: (Double) -> Unit,
) {
    var draft by remember(resetKey, value) { mutableStateOf(value) }
    var hadFocus by remember(resetKey) { mutableStateOf(false) }
    fun commitDraft() {
        val parsed = draft.toDoubleOrNull() ?: return
        if (parsed != value.toDoubleOrNull()) onCommit(parsed)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (label.isNotEmpty()) {
            Text(label, modifier = Modifier.widthIn(min = 22.dp), style = MaterialTheme.typography.bodySmall, color = Color.Black)
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { input -> draft = input.filter { it.isDigit() || it == '.' || it == '-' } },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        hadFocus = true
                    } else if (hadFocus) {
                        hadFocus = false
                        commitDraft()
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                        commitDraft(); true
                    } else {
                        false
                    }
                },
            singleLine = true,
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodySmall,
            trailingIcon = if (suffix.isEmpty()) null else {
                { Text(suffix, style = MaterialTheme.typography.bodySmall, color = Color.Black) }
            },
        )
    }
}

/**
 * A [androidx.compose.material3.Slider] whose whole drag coalesces into a single undo
 * entry: [onBegin] fires on the first change of a gesture, [onEnd] on release. Use for
 * any inspector slider that mutates the document (opacity, effects) so a drag is one
 * undoable step rather than one-per-frame.
 */
@Composable
internal fun UndoableSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onBegin: () -> Unit,
    onChange: (Float) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var dragging by remember { mutableStateOf(false) }
    androidx.compose.material3.Slider(
        value = value,
        onValueChange = { next ->
            if (!dragging) {
                dragging = true
                onBegin()
            }
            onChange(next)
        },
        onValueChangeFinished = {
            if (dragging) {
                dragging = false
                onEnd()
            }
        },
        valueRange = valueRange,
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
internal fun LabeledField(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.width(52.dp), style = MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f)) { content() }
    }
}

// --- Swatch / hex field ------------------------------------------------------

@Composable
internal fun SwatchField(
    color: Color,
    value: String,
    rightValue: String,
    resetKey: String,
    onCommitHex: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSwatchClick: (() -> Unit)? = null,
) {
    val colors = LocalEditorColors.current
    var draft by remember(resetKey, value) { mutableStateOf(value) }
    Surface(
        modifier = modifier.fillMaxWidth().height(36.dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        border = BorderStroke(1.dp, colors.softStroke),
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(20.dp)
                    .background(color, RoundedCornerShape(4.dp))
                    .border(1.dp, colors.softStroke, RoundedCornerShape(4.dp))
                    .then(if (onSwatchClick != null) Modifier.clickable { onSwatchClick() } else Modifier),
            )
            BasicTextField(
                value = draft,
                onValueChange = { input ->
                    draft = input.take(9)
                    if (Regex("^#?[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$").matches(input.trim())) {
                        onCommitHex(if (input.startsWith("#")) input.trim() else "#${input.trim()}")
                    }
                },
                modifier = Modifier.padding(start = 6.dp).weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.ink),
            )
            if (rightValue.isNotEmpty()) Text(rightValue, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// --- Selects -----------------------------------------------------------------

@Composable
internal fun SelectLike(value: String, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    Surface(
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        border = BorderStroke(1.dp, colors.softStroke),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            EditorSvgIcon(EditorIcon.ChevronDown, contentDescription = "Open options", modifier = Modifier.size(13.dp), tint = colors.controlInk)
        }
    }
}

@Composable
internal fun SelectField(
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEditorColors.current
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(36.dp).clickable { expanded = true },
            shape = RoundedCornerShape(6.dp),
            color = Color.White,
            border = BorderStroke(1.dp, colors.softStroke),
        ) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                EditorSvgIcon(EditorIcon.ChevronDown, contentDescription = "Open options", modifier = Modifier.size(13.dp), tint = colors.controlInk)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.bodySmall) },
                    onClick = { expanded = false; onSelect(option) },
                )
            }
        }
    }
}

/** Compact segmented control: one row of labelled toggles. */
@Composable
internal fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEditorColors.current
    Surface(
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        border = BorderStroke(1.dp, colors.softStroke),
    ) {
        Row(Modifier.fillMaxHeight()) {
            options.forEach { option ->
                val active = option == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (active) colors.selectionFill else Color.White)
                        .clickable { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) colors.accent else colors.ink,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// --- Buttons -----------------------------------------------------------------

@Composable
internal fun SmallIconButton(
    icon: EditorIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalEditorColors.current
    Surface(
        modifier = modifier.size(34.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = if (active) colors.selectionFill else Color.White,
        border = BorderStroke(1.dp, if (active) colors.selectionStroke else colors.panelStroke),
    ) {
        Box(contentAlignment = Alignment.Center) {
            EditorSvgIcon(
                icon = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = if (!enabled) colors.mutedInk else if (active) colors.accent else colors.ink,
            )
        }
    }
}

@Composable
internal fun SmallSquareButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalEditorColors.current
    Surface(
        modifier = modifier.size(34.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = if (active) colors.selectionFill else Color.White,
        border = BorderStroke(1.dp, if (active) colors.selectionStroke else colors.panelStroke),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (!enabled) colors.mutedInk else if (active) colors.accent else colors.ink,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun HeaderIconButton(icon: EditorIcon, contentDescription: String, onClick: () -> Unit, active: Boolean = false) {
    val colors = LocalEditorColors.current
    Surface(
        modifier = Modifier.size(42.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (active) colors.selectionFill else colors.raisedSurface,
        border = BorderStroke(1.dp, if (active) colors.selectionStroke else colors.panelStroke),
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            EditorSvgIcon(
                icon = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = if (active) colors.accent else colors.ink,
            )
        }
    }
}
