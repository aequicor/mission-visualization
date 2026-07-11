package io.aequicor.visualization.subsystems.annotations.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.aequicor.visualization.subsystems.annotations.Annotation
import io.aequicor.visualization.subsystems.annotations.AnnotationImage
import io.aequicor.visualization.subsystems.annotations.AnnotationKind

internal val AnnotationCardWidth = 200.dp

/** Hard cap on the expanded card so a long body never blocks an unbounded canvas region. */
internal val AnnotationCardMaxHeight = 240.dp

/** Cap on the attached image so a tall screenshot leaves room for body and author. */
internal val AnnotationCardImageMaxHeight = 120.dp

/**
 * Expanded annotation plate: body text, the optional attached image (decoded from its
 * data URI) and the author line. Issues get a warning header strip and accent border;
 * selection overrides the border with the selection token. Click selects the annotation.
 * The card is capped at [AnnotationCardMaxHeight]: overlong bodies scroll in place while
 * the image and author line stay visible.
 */
@Composable
fun AnnotationCard(
    annotation: Annotation,
    colors: AnnotationOverlayColors,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onSelect: () -> Unit = {},
) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier
            .width(AnnotationCardWidth)
            .heightIn(max = AnnotationCardMaxHeight)
            .clip(shape)
            .background(colors.cardSurface)
            .border(if (selected) 2.dp else 1.dp, colors.cardBorder(annotation.kind, selected), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            ),
    ) {
        if (annotation.kind == AnnotationKind.Issue) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(colors.issueFill))
        }
        Column(
            Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (annotation.body.text.isNotEmpty()) {
                BasicText(
                    text = annotation.body.text,
                    // fill = false: short bodies keep their natural height; overlong ones
                    // take the space left by the image and author line and scroll in place.
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    style = TextStyle(color = colors.ink, fontSize = 12.sp, lineHeight = 16.sp),
                )
            }
            annotation.image?.let { AnnotationCardImage(it) }
            annotation.author?.let { author ->
                BasicText(
                    text = author,
                    style = TextStyle(color = colors.mutedInk, fontSize = 10.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

/** The card's attached image; silently omitted when the source is not decodable. */
@Composable
private fun AnnotationCardImage(image: AnnotationImage) {
    val bitmap = rememberAnnotationImage(image.source) ?: return
    val sized = if (image.width > 0.0 && image.height > 0.0) {
        Modifier
            .fillMaxWidth()
            .heightIn(max = AnnotationCardImageMaxHeight)
            .aspectRatio((image.width / image.height).toFloat())
    } else {
        Modifier.fillMaxWidth().heightIn(max = AnnotationCardImageMaxHeight)
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = sized.clip(RoundedCornerShape(4.dp)),
        contentScale = ContentScale.Fit,
    )
}
