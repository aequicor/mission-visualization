package io.aequicor.visualization.subsystems.typography.compose

import androidx.compose.ui.text.font.FontFamily
import io.aequicor.visualization.subsystems.typography.FontDescriptor
import io.aequicor.visualization.subsystems.typography.NamedFontStyle
import io.aequicor.visualization.subsystems.typography.TypographyStyle
import io.aequicor.visualization.subsystems.typography.fontWeightOrDefault

/**
 * Resolves font-family names from the document into Compose [FontFamily] instances.
 *
 * Implementations may load asynchronously; [generation] must change whenever a
 * previously-unresolved font becomes available so measurement caches invalidate and
 * text re-lays-out with real metrics.
 */
interface FontProvider {
    /** Monotonic value that changes when the set of loaded fonts changes. */
    val generation: Int

    /** Families this provider can offer, for picker UIs. */
    val families: List<FontFamilyInfo>

    /**
     * The family for [descriptor], or null when unknown (callers fall back to
     * [FontFamily.Default]). Weight/italic selection stays with Compose font matching;
     * providers with exact variable instances may bake [FontDescriptor.axes] in.
     */
    fun resolve(descriptor: FontDescriptor): FontFamily?
}

data class FontFamilyInfo(
    val family: String,
    /** Named styles genuinely available (not synthesized). */
    val styles: List<NamedFontStyle>,
    /** Whether the family ships OpenType small caps (`smcp`/`c2sc`). */
    val supportsSmallCaps: Boolean = false,
    /** Family instance for previews (picker rows rendered in their own typeface). */
    val preview: FontFamily? = null,
)

/** Provider with no fonts: everything renders in [FontFamily.Default]. */
object NoFonts : FontProvider {
    override val generation: Int = 0
    override val families: List<FontFamilyInfo> = emptyList()
    override fun resolve(descriptor: FontDescriptor): FontFamily? = null
}

fun TypographyStyle.fontDescriptor(): FontDescriptor = FontDescriptor(
    family = fontFamily.orEmpty(),
    weight = fontWeightOrDefault(),
    italic = italic ?: false,
    axes = axes,
)
