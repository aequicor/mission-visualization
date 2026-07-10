package io.aequicor.visualization.subsystems.typography.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import io.aequicor.visualization.subsystems.typography.FontDescriptor
import io.aequicor.visualization.subsystems.typography.NamedFontStyle
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.Res
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.inter_bold
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.inter_bold_italic
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.inter_italic
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.inter_medium
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.inter_regular
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.inter_semibold
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.jetbrains_mono_bold
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.jetbrains_mono_bold_italic
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.jetbrains_mono_italic
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.jetbrains_mono_regular
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.roboto_bold
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.roboto_bold_italic
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.roboto_italic
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.roboto_medium
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.roboto_regular
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.source_serif4_bold
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.source_serif4_bold_italic
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.source_serif4_italic
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.source_serif4_regular
import io.aequicor.visualization.subsystems.typography.compose.generated.resources.source_serif4_semibold
import org.jetbrains.compose.resources.Font

/**
 * Bundled Google Fonts set (static cuts, OFL/Apache-licensed — see
 * `composeResources/files/font-licenses/`). Weights between the shipped cuts fall back
 * to Compose font matching + synthesis.
 */
class BundledFontProvider internal constructor(
    override val generation: Int,
    override val families: List<FontFamilyInfo>,
    private val byNormalizedName: Map<String, FontFamily>,
) : FontProvider {

    override fun resolve(descriptor: FontDescriptor): FontFamily? =
        byNormalizedName[normalizeFamilyName(descriptor.family)]

    companion object {
        /** Aliases old/alternate family spellings onto the bundled families. */
        fun normalizeFamilyName(family: String): String {
            val key = family.lowercase().filter { it.isLetterOrDigit() }
            return when (key) {
                "sourceserifpro", "sourceserif" -> "sourceserif4"
                "jetbrainsmononl" -> "jetbrainsmono"
                else -> key
            }
        }
    }
}

/**
 * Builds the bundled provider. Resource fonts load asynchronously on web targets; when
 * they arrive this recomposes with a bumped [FontProvider.generation], invalidating
 * measurement caches so text re-lays-out with real metrics.
 */
@Composable
fun rememberBundledFontProvider(): FontProvider {
    val inter = FontFamily(
        Font(Res.font.inter_regular, FontWeight.W400),
        Font(Res.font.inter_medium, FontWeight.W500),
        Font(Res.font.inter_semibold, FontWeight.W600),
        Font(Res.font.inter_bold, FontWeight.W700),
        Font(Res.font.inter_italic, FontWeight.W400, FontStyle.Italic),
        Font(Res.font.inter_bold_italic, FontWeight.W700, FontStyle.Italic),
    )
    val sourceSerif = FontFamily(
        Font(Res.font.source_serif4_regular, FontWeight.W400),
        Font(Res.font.source_serif4_semibold, FontWeight.W600),
        Font(Res.font.source_serif4_bold, FontWeight.W700),
        Font(Res.font.source_serif4_italic, FontWeight.W400, FontStyle.Italic),
        Font(Res.font.source_serif4_bold_italic, FontWeight.W700, FontStyle.Italic),
    )
    val roboto = FontFamily(
        Font(Res.font.roboto_regular, FontWeight.W400),
        Font(Res.font.roboto_medium, FontWeight.W500),
        Font(Res.font.roboto_bold, FontWeight.W700),
        Font(Res.font.roboto_italic, FontWeight.W400, FontStyle.Italic),
        Font(Res.font.roboto_bold_italic, FontWeight.W700, FontStyle.Italic),
    )
    val jetBrainsMono = FontFamily(
        Font(Res.font.jetbrains_mono_regular, FontWeight.W400),
        Font(Res.font.jetbrains_mono_bold, FontWeight.W700),
        Font(Res.font.jetbrains_mono_italic, FontWeight.W400, FontStyle.Italic),
        Font(Res.font.jetbrains_mono_bold_italic, FontWeight.W700, FontStyle.Italic),
    )

    val generationHolder = remember { GenerationHolder() }
    return remember(inter, sourceSerif, roboto, jetBrainsMono) {
        BundledFontProvider(
            generation = generationHolder.next(),
            families = listOf(
                FontFamilyInfo(
                    family = "Inter",
                    styles = namedStyles(400 to false, 500 to false, 600 to false, 700 to false, 400 to true, 700 to true),
                    supportsSmallCaps = true,
                    preview = inter,
                ),
                FontFamilyInfo(
                    family = "Source Serif 4",
                    styles = namedStyles(400 to false, 600 to false, 700 to false, 400 to true, 700 to true),
                    preview = sourceSerif,
                ),
                FontFamilyInfo(
                    family = "Roboto",
                    styles = namedStyles(400 to false, 500 to false, 700 to false, 400 to true, 700 to true),
                    preview = roboto,
                ),
                FontFamilyInfo(
                    family = "JetBrains Mono",
                    styles = namedStyles(400 to false, 700 to false, 400 to true, 700 to true),
                    preview = jetBrainsMono,
                ),
            ),
            byNormalizedName = mapOf(
                "inter" to inter,
                "sourceserif4" to sourceSerif,
                "roboto" to roboto,
                "jetbrainsmono" to jetBrainsMono,
            ),
        )
    }
}

private class GenerationHolder {
    private var value = 0
    fun next(): Int = ++value
}

private fun namedStyles(vararg cuts: Pair<Int, Boolean>): List<NamedFontStyle> =
    cuts.map { (weight, italic) ->
        NamedFontStyle(
            name = io.aequicor.visualization.subsystems.typography.FontStyles.nameFor(weight, italic),
            weight = weight,
            italic = italic,
        )
    }
