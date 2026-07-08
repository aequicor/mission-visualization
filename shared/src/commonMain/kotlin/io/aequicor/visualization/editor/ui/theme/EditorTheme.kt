package io.aequicor.visualization.editor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Canonical palette of the mission editor chrome, grouped by role. This is the
 * single source of truth for editor colors: composables read the palette via
 * [LocalEditorColors] instead of hardcoding hex literals. The values are the
 * exact colors MissionEditorScreen shipped with, so providing the defaults is
 * visually identical to the pre-theme screen.
 */
@Immutable
data class EditorColors(
    // Accent (primary action / selection emphasis).
    val accent: Color = Color(0xFF1E88FF),
    val accentContainer: Color = Color(0xFFDCEEFF),
    val onAccentContainer: Color = Color(0xFF0E4F9D),
    // App chrome: window background gradient and the rounded shell around the panes.
    val chrome: Color = Color(0xFFEAF5FF),
    val chromeGradientStart: Color = Color(0xFFF3FAFF),
    val chromeGradientEnd: Color = Color(0xFFF9FDFF),
    val shellStroke: Color = Color(0xFFCEE1F2),
    // Surfaces (panes and their internal areas).
    val surfaceVariant: Color = Color(0xFFF4F8FC),
    val paneSurface: Color = Color(0xFFFBFDFF),
    val raisedSurface: Color = Color(0xFFFDFEFF),
    val gutterSurface: Color = Color(0xFFF3F7FB),
    val statusBarSurface: Color = Color(0xFFF7FAFE),
    val activeLineSurface: Color = Color(0xFFF8FAFC),
    // Interactive controls on white panes need a distinct surface and a stronger outline.
    val controlSurface: Color = Color(0xFFF6FAFE),
    val controlDisabledSurface: Color = Color(0xFFF1F5F9),
    // Strokes.
    val panelStroke: Color = Color(0xFFD6E3EF),
    val softStroke: Color = Color(0xFFE3EAF2),
    val controlStroke: Color = Color(0xFFB9CBE0),
    val controlDisabledStroke: Color = Color(0xFFD7E2EC),
    val divider: Color = Color(0xFFCFE0EF),
    // Ink (text and glyphs, from high to low emphasis).
    val ink: Color = Color(0xFF111827),
    val mutedInk: Color = Color(0xFF5E6B7A),
    val codeInk: Color = Color(0xFF263449),
    val statusBarInk: Color = Color(0xFF334155),
    val subtleInk: Color = Color(0xFF41617E),
    val gutterInk: Color = Color(0xFF64748B),
    val controlInk: Color = Color(0xFF31516E),
    // Selection highlights.
    val selectionFill: Color = Color(0xFFEAF4FF),
    val selectionStroke: Color = Color(0xFFE2F0FF),
    val thumbnailSelectedStroke: Color = Color(0xFFB9D9FF),
    // Preview canvas aids (dot grid, dimension badges, anchor widget, thumbnails).
    val canvasDot: Color = Color(0xFFE4ECF5),
    val badgeSurface: Color = Color(0xFFF8FBFF),
    val badgeStroke: Color = Color(0xFFBFD8F5),
    val anchorDot: Color = Color(0xFFC8D3DF),
    val thumbnailBlock: Color = Color(0xFFEAF2FA),
    val thumbnailBar: Color = Color(0xFFD9E5F1),
    // Status indicators (screen list dots).
    val statusPositive: Color = Color(0xFF17C46B),
    val statusWarning: Color = Color(0xFFFFB800),
    val statusDanger: Color = Color(0xFFFF1D1D),
)

/** Editor palette for the current composition; provided by [EditorTheme]. */
val LocalEditorColors = staticCompositionLocalOf { EditorColors() }

/**
 * Provides [LocalEditorColors] and a [MaterialTheme] color scheme derived from
 * the same palette, so Material components pick up matching defaults.
 */
@Composable
fun EditorTheme(content: @Composable () -> Unit) {
    val colors = EditorColors()
    CompositionLocalProvider(LocalEditorColors provides colors) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = colors.accent,
                onPrimary = Color.White,
                primaryContainer = colors.accentContainer,
                onPrimaryContainer = colors.onAccentContainer,
                surface = Color.White,
                onSurface = colors.ink,
                surfaceVariant = colors.surfaceVariant,
                onSurfaceVariant = colors.mutedInk,
                background = colors.chrome,
                onBackground = colors.ink,
                outline = colors.panelStroke,
            ),
            content = content,
        )
    }
}
