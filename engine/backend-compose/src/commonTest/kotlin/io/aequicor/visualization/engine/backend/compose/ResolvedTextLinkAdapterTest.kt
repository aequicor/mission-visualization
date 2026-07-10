package io.aequicor.visualization.engine.backend.compose

import io.aequicor.visualization.engine.ir.model.TextLink
import io.aequicor.visualization.engine.ir.resolve.ResolvedText
import io.aequicor.visualization.engine.ir.resolve.ResolvedTextStyle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The ResolvedText -> RichText adapter carries hyperlink ranges through unchanged, so the
 * measurer's `linkRects`/`linkAt` (tested in the typography subsystem) can hit-test them.
 * Full geometry hit-testing needs a skiko-backed measurer and is covered there.
 */
class ResolvedTextLinkAdapterTest {

    @Test
    fun linksSurviveTheAdapter() {
        val resolved = ResolvedText(
            characters = "Visit our site now",
            style = ResolvedTextStyle(fontSize = 16.0),
            links = listOf(
                TextLink(start = 6, end = 14, url = "https://example.com"),
                TextLink(start = 0, end = 5, url = "", nodeTarget = "screen_2"),
            ),
        )
        val rich = resolved.toRichText()
        assertEquals(2, rich.links.size)
        assertEquals(6 to 14, rich.links[0].start to rich.links[0].end)
        assertEquals("https://example.com", rich.links[0].url)
        assertEquals("screen_2", rich.links[1].nodeTarget)
    }

    @Test
    fun noLinksYieldsEmpty() {
        val resolved = ResolvedText(characters = "plain", style = ResolvedTextStyle())
        assertEquals(0, resolved.toRichText().links.size)
    }
}
