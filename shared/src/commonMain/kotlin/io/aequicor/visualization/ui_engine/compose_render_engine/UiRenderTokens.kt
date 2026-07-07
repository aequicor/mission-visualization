package io.aequicor.visualization.ui_engine.compose_render_engine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LapisBlue = Color(0xFF1F5FA8)
val DeepLapis = Color(0xFF143A66)
val SignalTeal = Color(0xFF2BB8A8)
val CoralNote = Color(0xFFE97155)
val Ink = Color(0xFF172033)
val Cloud = Color(0xFFF6FAFF)
val Mist = Color(0xFFEAF1F8)

fun spacingDp(token: String): Dp =
    when (token) {
        "none" -> 0.dp
        "xs" -> 4.dp
        "sm" -> 8.dp
        "md" -> 12.dp
        "lg" -> 18.dp
        "xl" -> 24.dp
        else -> Dp.Unspecified
    }

fun Dp.ifDefault(default: Dp): Dp =
    if (this == Dp.Unspecified) default else this

@Composable
fun toneSurface(tone: String): Color =
    when (tone) {
        "primary" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        "accent", "success" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        "warning" -> Color(0xFFFFF2D8)
        "danger" -> Color(0xFFFFE7DF)
        "info" -> Color(0xFFEAF2FC)
        else -> Color(0xFFF8FBFF)
    }

@Composable
fun toneStroke(tone: String): Color =
    when (tone) {
        "primary" -> MaterialTheme.colorScheme.primary
        "accent", "success" -> MaterialTheme.colorScheme.secondary
        "warning" -> Color(0xFFC77800)
        "danger" -> CoralNote
        else -> Color(0xFFD6E4EE)
    }

@Composable
fun StepDot(number: Int) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}
