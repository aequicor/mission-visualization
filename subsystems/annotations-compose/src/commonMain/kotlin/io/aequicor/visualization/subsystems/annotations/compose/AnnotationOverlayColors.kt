package io.aequicor.visualization.subsystems.annotations.compose

import androidx.compose.ui.graphics.Color
import io.aequicor.visualization.subsystems.annotations.AnnotationKind

/**
 * Theme tokens the annotation overlay draws with, passed in by the caller so this module
 * stays independent of the app theme (mirrors anchoring-compose `GuideStyle` and
 * figures-compose `FigurePreviewStyle`). The editor maps its `EditorColors` tokens here:
 * issue = `statusWarning`, note = neutral chrome/ink tokens.
 */
data class AnnotationOverlayColors(
    val noteFill: Color,       // collapsed note badge fill (neutral)
    val noteStroke: Color,     // note badge outline
    val issueFill: Color,      // collapsed issue badge fill (statusWarning)
    val issueStroke: Color,    // issue badge outline / card accent border
    val ink: Color,            // card body text
    val mutedInk: Color,       // card secondary text (author line)
    val cardSurface: Color,    // expanded card background
    val cardStroke: Color,     // expanded note card border
    val selectionStroke: Color, // selected badge/card outline
    /** Dangling badge fill (node anchor no longer resolves); defaults keep old callers working. */
    val danglingFill: Color = cardSurface,
    /** Dangling badge dashed outline + inner dot. */
    val danglingStroke: Color = mutedInk,
)

/** Badge fill for [kind]: issue-yellow vs neutral note. */
fun AnnotationOverlayColors.badgeFill(kind: AnnotationKind): Color = when (kind) {
    AnnotationKind.Note -> noteFill
    AnnotationKind.Issue -> issueFill
}

/** Badge outline for [kind]. */
fun AnnotationOverlayColors.badgeStroke(kind: AnnotationKind): Color = when (kind) {
    AnnotationKind.Note -> noteStroke
    AnnotationKind.Issue -> issueStroke
}

/** Expanded-card border for [kind]/[selected]: selection wins, then the issue accent. */
fun AnnotationOverlayColors.cardBorder(kind: AnnotationKind, selected: Boolean): Color = when {
    selected -> selectionStroke
    kind == AnnotationKind.Issue -> issueStroke
    else -> cardStroke
}
