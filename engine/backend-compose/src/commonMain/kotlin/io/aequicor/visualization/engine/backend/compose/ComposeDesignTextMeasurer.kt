package io.aequicor.visualization.engine.backend.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import io.aequicor.visualization.engine.ir.layout.DesignTextMeasurer
import io.aequicor.visualization.engine.ir.layout.MeasuredText
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.resolve.ResolvedPaint
import io.aequicor.visualization.engine.ir.resolve.ResolvedText
import io.aequicor.visualization.subsystems.typography.compose.ComposeTypographyMeasurer
import io.aequicor.visualization.subsystems.typography.compose.FontProvider
import io.aequicor.visualization.subsystems.typography.compose.NoFonts

/**
 * Bridges the pure layout engine to the typography subsystem's Compose measurer.
 * Document px map 1:1 to draw px (the artboard canvas applies the zoom transform),
 * and the subsystem's paragraph-stacked layout supplies real per-line baselines.
 */
class ComposeDesignTextMeasurer(
    /** Shared with the draw path so measured and painted layouts hit the same cache. */
    val typography: ComposeTypographyMeasurer,
) : DesignTextMeasurer {

    constructor(
        textMeasurer: TextMeasurer,
        density: Density,
        fontProvider: FontProvider = NoFonts,
    ) : this(ComposeTypographyMeasurer(textMeasurer, density, fontProvider))

    override fun measure(text: ResolvedText, maxWidth: Double?): MeasuredText {
        val measured = typography.measure(text.toRichText(), maxWidth)
        return MeasuredText(
            width = measured.width,
            height = measured.height,
            lineCount = measured.lineCount.coerceAtLeast(1),
            firstBaseline = measured.firstBaseline,
            lastBaseline = measured.lastBaseline,
        )
    }

    override fun firstBaseline(text: ResolvedText, maxWidth: Double?): Double =
        typography.measure(text.toRichText(), maxWidth).firstBaseline
            ?: (text.style.fontSize * 0.8)
}

internal fun List<ResolvedPaint>?.firstSolidColor(): Color? =
    this?.filterIsInstance<ResolvedPaint.Solid>()?.firstOrNull()?.let { solid ->
        solid.color.toComposeColor().copy(
            alpha = (solid.color.alpha / 255f) * solid.opacity.toFloat(),
        )
    }

internal fun DesignColor.toComposeColor(): Color =
    Color(
        red = red / 255f,
        green = green / 255f,
        blue = blue / 255f,
        alpha = alpha / 255f,
    )
