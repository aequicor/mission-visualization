package io.aequicor.visualization.subsystems.annotations.compose

import androidx.compose.ui.graphics.Color
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import kotlin.test.Test
import kotlin.test.assertEquals

class AnnotationOverlayColorsTest {

    private val colors = AnnotationOverlayColors(
        noteFill = Color(0xFF111111),
        noteStroke = Color(0xFF222222),
        issueFill = Color(0xFF333333),
        issueStroke = Color(0xFF444444),
        ink = Color(0xFF555555),
        mutedInk = Color(0xFF666666),
        cardSurface = Color(0xFF777777),
        cardStroke = Color(0xFF888888),
        selectionStroke = Color(0xFF999999),
    )

    @Test
    fun badgeFillPicksKindToken() {
        assertEquals(colors.noteFill, colors.badgeFill(AnnotationKind.Note))
        assertEquals(colors.issueFill, colors.badgeFill(AnnotationKind.Issue))
    }

    @Test
    fun badgeStrokePicksKindToken() {
        assertEquals(colors.noteStroke, colors.badgeStroke(AnnotationKind.Note))
        assertEquals(colors.issueStroke, colors.badgeStroke(AnnotationKind.Issue))
    }

    @Test
    fun cardBorderPrefersSelectionThenIssueAccent() {
        assertEquals(colors.selectionStroke, colors.cardBorder(AnnotationKind.Issue, selected = true))
        assertEquals(colors.selectionStroke, colors.cardBorder(AnnotationKind.Note, selected = true))
        assertEquals(colors.issueStroke, colors.cardBorder(AnnotationKind.Issue, selected = false))
        assertEquals(colors.cardStroke, colors.cardBorder(AnnotationKind.Note, selected = false))
    }
}
