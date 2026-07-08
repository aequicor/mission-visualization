package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.editor.presentation.CompactLabel

@Composable
internal fun CompactText(
    label: CompactLabel,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
) {
    BoxWithConstraints(modifier) {
        val width = maxWidth
        val resolvedStyle = style.withWeight(fontWeight)
        val text = rememberCompactText(label, width, resolvedStyle)
        Text(
            text = text,
            modifier = if (textAlign == null) Modifier else Modifier.fillMaxWidth(),
            color = color,
            style = resolvedStyle,
            textAlign = textAlign,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun rememberCompactText(
    label: CompactLabel,
    maxWidth: Dp,
    style: TextStyle = LocalTextStyle.current,
): String {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val maxWidthPx = with(density) { maxWidth.toPx() }
    return remember(label, maxWidthPx, style, measurer) {
        if (maxWidth <= 0.dp) return@remember label.fallback
        label.variants.firstOrNull { variant ->
            measurer.measure(
                text = variant,
                style = style,
                maxLines = 1,
                softWrap = false,
            ).size.width <= maxWidthPx
        } ?: label.fallback
    }
}

private fun TextStyle.withWeight(fontWeight: FontWeight?): TextStyle =
    if (fontWeight == null) this else copy(fontWeight = fontWeight)
