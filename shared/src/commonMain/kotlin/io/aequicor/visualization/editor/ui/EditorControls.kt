package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.editor.presentation.CompactLabel
import io.aequicor.visualization.editor.ui.strings.LocalStrings
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
    title: (T) -> CompactLabel,
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
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxHeight().clickable { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                val tabLabel = title(tab)
                val tabIcon = icon(tab)
                val horizontalPadding = 6.dp
                val iconAndGap = if (tabIcon == null) 0.dp else 22.dp
                val textMaxWidth = (maxWidth - horizontalPadding - horizontalPadding - iconAndGap).coerceAtLeast(0.dp)
                Row(
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tabIcon?.let {
                        EditorSvgIcon(
                            icon = it,
                            contentDescription = tabLabel.full,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelected) colors.accent else colors.controlInk,
                        )
                    }
                    CompactText(
                        label = tabLabel,
                        modifier = Modifier.widthIn(max = textMaxWidth),
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
    labelMinWidth: Dp = 22.dp,
    onCommit: (Double) -> Unit,
) {
    val colors = LocalEditorColors.current
    var draft by remember(resetKey, value) { mutableStateOf(value) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (label.isNotEmpty()) {
            Text(label, modifier = Modifier.widthIn(min = labelMinWidth), style = MaterialTheme.typography.bodySmall, color = Color.Black, maxLines = 1, softWrap = false)
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
                { Text(placeholder, style = MaterialTheme.typography.bodySmall, color = colors.mutedInk) }
            },
            trailingIcon = if (suffix.isEmpty()) null else {
                { Text(suffix, style = MaterialTheme.typography.bodySmall, color = Color.Black) }
            },
            colors = editorOutlinedTextFieldColors(),
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
    val colors = LocalEditorColors.current
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
            colors = editorOutlinedTextFieldColors(),
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
    LabeledField(compactLabelFor(label), content)
}

@Composable
internal fun LabeledField(label: CompactLabel, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CompactText(
            label = label,
            modifier = Modifier.width(64.dp),
            style = MaterialTheme.typography.bodySmall,
            color = LocalEditorColors.current.ink,
        )
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
    val swatchShape = RoundedCornerShape(4.dp)
    Surface(
        modifier = modifier.fillMaxWidth().height(36.dp),
        shape = RoundedCornerShape(6.dp),
        color = colors.controlSurface,
        border = BorderStroke(1.dp, colors.controlStroke),
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(20.dp)
                    .background(color, swatchShape)
                    .border(1.dp, colors.controlStroke, swatchShape)
                    .then(if (onSwatchClick != null) Modifier.clip(swatchShape).clickable { onSwatchClick() } else Modifier),
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
        color = colors.controlSurface,
        border = BorderStroke(1.dp, colors.controlStroke),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DropdownLeadingBox(size = 18.dp) { DefaultDropdownLeadingContent(value, modifier = Modifier.size(16.dp)) }
            Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            EditorSvgIcon(EditorIcon.ChevronDown, contentDescription = LocalStrings.current.common.openOptions, modifier = Modifier.size(13.dp), tint = colors.controlInk)
        }
    }
}

@Composable
internal fun SelectField(
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    optionLeadingContent: (@Composable (String) -> Unit)? = null,
) {
    val colors = LocalEditorColors.current
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Box(modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(36.dp).clip(shape).clickable { expanded = true },
            shape = shape,
            color = colors.controlSurface,
            border = BorderStroke(1.dp, colors.controlStroke),
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DropdownLeadingBox(size = 18.dp) {
                    if (leadingContent != null) leadingContent() else DefaultDropdownLeadingContent(value, modifier = Modifier.size(16.dp))
                }
                Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                EditorSvgIcon(EditorIcon.ChevronDown, contentDescription = LocalStrings.current.common.openOptions, modifier = Modifier.size(13.dp), tint = colors.controlInk)
            }
        }
        EditorDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                EditorDropdownMenuItem(
                    text = option,
                    onClick = { expanded = false; onSelect(option) },
                    leadingContent = optionLeadingContent?.let { content -> { content(option) } },
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
    val shape = RoundedCornerShape(6.dp)
    Surface(
        modifier = modifier.height(34.dp).clip(shape),
        shape = shape,
        color = colors.controlSurface,
        border = BorderStroke(1.dp, colors.controlStroke),
    ) {
        Row(Modifier.fillMaxHeight()) {
            options.forEach { option ->
                val active = option == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (active) colors.selectionFill else colors.controlSurface)
                        .clickable { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    CompactText(
                        label = compactLabelFor(label(option)),
                        modifier = Modifier.padding(horizontal = 3.dp).fillMaxWidth(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) colors.accent else colors.ink,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
internal fun EditorDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = LocalEditorColors.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(min = 220.dp),
        shape = RoundedCornerShape(6.dp),
        containerColor = colors.raisedSurface,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, colors.controlStroke),
    ) {
        content()
    }
}

@Composable
internal fun EditorDropdownMenuItem(
    text: String,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalEditorColors.current
    DropdownMenuItem(
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) colors.ink else colors.mutedInk,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = {
            DropdownLeadingBox(size = 18.dp) {
                if (leadingContent != null) leadingContent() else DefaultDropdownLeadingContent(text, modifier = Modifier.size(16.dp))
            }
        },
        trailingIcon = trailingContent,
    )
}

@Composable
internal fun DropdownMenuIcon(
    icon: EditorIcon,
    modifier: Modifier = Modifier.size(16.dp),
    tint: Color = LocalEditorColors.current.controlInk,
) {
    EditorSvgIcon(icon = icon, contentDescription = null, modifier = modifier, tint = tint)
}

@Composable
internal fun DefaultDropdownLeadingContent(
    label: String,
    modifier: Modifier = Modifier.size(16.dp),
    tint: Color = LocalEditorColors.current.controlInk,
) {
    DropdownMenuIcon(icon = dropdownIconForLabel(label), modifier = modifier, tint = tint)
}

@Composable
internal fun DropdownLeadingBox(size: Dp, content: @Composable () -> Unit) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        content()
    }
}

internal fun dropdownIconForLabel(label: String): EditorIcon {
    val normalized = label.lowercase()
    return when {
        "назад" in normalized -> EditorIcon.ArrowBack
        normalized == "back" -> EditorIcon.KeyboardReturn
        "откры" in normalized || normalized.startsWith("open") -> EditorIcon.FolderOpen
        "сохран" in normalized || normalized.startsWith("save") -> EditorIcon.Save
        "папк" in normalized || "folder" in normalized -> EditorIcon.Folder
        "экспорт" in normalized || "export" in normalized -> EditorIcon.Export
        "png" in normalized -> EditorIcon.Image
        "pdf" in normalized -> EditorIcon.PictureAsPdf
        "zip" in normalized -> EditorIcon.Folder
        "on click" in normalized -> EditorIcon.TouchApp
        "on press" in normalized -> EditorIcon.HandPan
        "delay" in normalized -> EditorIcon.Timer
        "navigate" in normalized -> EditorIcon.Link
        "target" in normalized || "screen" in normalized || "pick a screen" in normalized -> EditorIcon.Screens
        "desktop" in normalized -> EditorIcon.DeviceDesktop
        "tablet" in normalized -> EditorIcon.DeviceTablet
        "mobile" in normalized -> EditorIcon.DeviceMobile
        "instant" in normalized -> EditorIcon.PlayArrow
        "dissolve" in normalized || "push" in normalized || "slide" in normalized || "move" in normalized || "animate" in normalized -> EditorIcon.TransitionSlide
        "ease" in normalized || "linear" in normalized -> EditorIcon.MotionPhotosOn
        "fade" in normalized || "pop" in normalized || "float" in normalized || "pulse" in normalized || "spin" in normalized || "custom" in normalized -> EditorIcon.MotionPhotosOn
        "solid" in normalized -> EditorIcon.Fill
        "radial" in normalized || "gradient" in normalized -> EditorIcon.Gradient
        "image" in normalized -> EditorIcon.Image
        "drop shadow" in normalized || "inner shadow" in normalized -> EditorIcon.Visibility
        "blur" in normalized -> EditorIcon.BlurOn
        "inside" in normalized || "outside" in normalized -> EditorIcon.Stroke
        "butt" in normalized || "round" in normalized || "square" in normalized || "arrow" in normalized -> EditorIcon.Stroke
        "fixed" in normalized -> EditorIcon.AspectRatio
        "hug" in normalized -> EditorIcon.ConstraintHorizontal
        "fill" in normalized -> EditorIcon.Fill
        "left" in normalized -> EditorIcon.AlignHorizontalLeft
        "right" in normalized -> EditorIcon.AlignHorizontalRight
        "top" in normalized -> EditorIcon.AlignVerticalTop
        "bottom" in normalized -> EditorIcon.AlignVerticalBottom
        "center" in normalized -> EditorIcon.AlignHorizontalCenter
        "scale" in normalized -> EditorIcon.AspectRatio
        "union" in normalized || "subtract" in normalized || "intersect" in normalized || "exclude" in normalized -> EditorIcon.Component
        "normal" in normalized || "multiply" in normalized || "screen" in normalized || "overlay" in normalized ||
            "darken" in normalized || "lighten" in normalized || "color-" in normalized || "difference" in normalized -> EditorIcon.Design
        else -> EditorIcon.Select
    }
}

private fun compactLabelFor(text: String): CompactLabel = when (text) {
    "Distribute" -> CompactLabel("Distribute", "Distrib", "Dist")
    "Alignment" -> CompactLabel("Alignment", "Align", "Algn")
    "Constraints" -> CompactLabel("Constraints", "Const", "Cnst")
    "Between" -> CompactLabel("Between", "Btwn", "Btw")
    "Center" -> CompactLabel("Center", "Ctr", "C")
    "Start" -> CompactLabel("Start", "Start", "S")
    "End" -> CompactLabel("End", "End", "E")
    "Stretch" -> CompactLabel("Stretch", "Fill", "Fill")
    "Fixed" -> CompactLabel("Fixed", "Fix", "Fx")
    "Vertical" -> CompactLabel("Vertical", "Vert", "V")
    "Horizontal" -> CompactLabel("Horizontal", "Horz", "H")
    "Auto W" -> CompactLabel("Auto W", "W", "W")
    "Auto H" -> CompactLabel("Auto H", "H", "H")
    "Left & Right" -> CompactLabel("Left & Right", "L + R", "L/R")
    "Top & Bottom" -> CompactLabel("Top & Bottom", "T + B", "T/B")
    else -> CompactLabel(text)
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
    val borderColor = when {
        active -> colors.accent
        enabled -> colors.controlStroke
        else -> colors.controlDisabledStroke
    }
    val shape = RoundedCornerShape(7.dp)
    Surface(
        modifier = modifier.size(34.dp).clip(shape).clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        color = when {
            active -> colors.selectionFill
            enabled -> colors.controlSurface
            else -> colors.controlDisabledSurface
        },
        border = BorderStroke(1.dp, borderColor),
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
    val borderColor = when {
        active -> colors.accent
        enabled -> colors.controlStroke
        else -> colors.controlDisabledStroke
    }
    val shape = RoundedCornerShape(7.dp)
    Surface(
        modifier = modifier.size(34.dp).clip(shape).clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        color = when {
            active -> colors.selectionFill
            enabled -> colors.controlSurface
            else -> colors.controlDisabledSurface
        },
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (!enabled) colors.mutedInk else if (active) colors.accent else colors.ink,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun HeaderIconButton(icon: EditorIcon, contentDescription: String, onClick: () -> Unit, active: Boolean = false) {
    val colors = LocalEditorColors.current
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = Modifier.size(42.dp).clip(shape).clickable(onClick = onClick),
        shape = shape,
        color = if (active) colors.selectionFill else colors.controlSurface,
        border = BorderStroke(1.dp, if (active) colors.accent else colors.controlStroke),
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

@Composable
private fun editorOutlinedTextFieldColors() = with(LocalEditorColors.current) {
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = ink,
        unfocusedTextColor = ink,
        disabledTextColor = mutedInk,
        focusedContainerColor = controlSurface,
        unfocusedContainerColor = controlSurface,
        disabledContainerColor = controlDisabledSurface,
        cursorColor = accent,
        focusedBorderColor = accent,
        unfocusedBorderColor = controlStroke,
        disabledBorderColor = controlDisabledStroke,
        focusedPlaceholderColor = mutedInk,
        unfocusedPlaceholderColor = mutedInk,
        disabledPlaceholderColor = mutedInk,
        focusedTrailingIconColor = controlInk,
        unfocusedTrailingIconColor = controlInk,
        disabledTrailingIconColor = mutedInk,
    )
}
