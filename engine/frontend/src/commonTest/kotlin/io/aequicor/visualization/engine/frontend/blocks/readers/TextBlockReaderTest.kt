package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.TextPatch
import io.aequicor.visualization.engine.frontend.blocks.TextSpanPatch
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.model.TextCase
import io.aequicor.visualization.engine.ir.model.TextDecorationKind
import io.aequicor.visualization.engine.ir.model.TextListSettings
import io.aequicor.visualization.engine.ir.model.TextListType
import io.aequicor.visualization.engine.ir.model.TextTruncate
import io.aequicor.visualization.engine.ir.model.UnitValue
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextBlockReaderTest {
    /** Spec "Text and Typography" example (~line 755-786). */
    @Test
    fun readsSpecTypographyExample() {
        val (patch, collector) = readSingle(
            """
            text:
              key: missionDashboard.title
              defaultText: Mission Control
              style: typography.heading.lg
              typography:
                fontFamily: Inter
                fontWeight: 700
                fontSize: 24
                lineHeight: 32
                letterSpacing: 0
                paragraphSpacing: 0
                horizontalAlign: start
                verticalAlign: center
                decoration: none
                case: none
                openType:
                  liga: true
                  tnum: true
                variableFont:
                  weight: 700
                  opticalSize: 24
              resizing:
                width: fill
                height: hug
              maxLines: 1
              overflow: truncate
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            TextPatch(
                key = "missionDashboard.title",
                defaultText = "Mission Control",
                styleRef = "typography.heading.lg",
                typography = DesignTextStyle(
                    fontFamily = "Inter",
                    fontWeight = 700.0.bindable(),
                    fontSize = 24.0.bindable(),
                    lineHeight = UnitValue(DesignUnit.Px, 32.0),
                    letterSpacing = UnitValue(DesignUnit.Px, 0.0),
                    paragraphSpacing = 0.0,
                    textAlignHorizontal = TextAlignHorizontal.Left,
                    textAlignVertical = TextAlignVertical.Center,
                    textCase = TextCase.None,
                    textDecoration = TextDecorationKind.None,
                    fontFeatures = mapOf("liga" to true, "tnum" to true),
                    variableAxes = mapOf("wght" to 700.0, "opsz" to 24.0),
                ),
                resizingWidth = SizingMode.Fill,
                resizingHeight = SizingMode.Hug,
                truncate = TextTruncate(maxLines = 1),
            ),
            patch,
        )
    }

    /** Spec rich text spans example (~line 790-805). */
    @Test
    fun readsSpecRichTextSpansExample() {
        val (patch, collector) = readSingle(
            """
            text:
              key: missionDashboard.notice
              defaultText: "Проверьте SLA перед запуском миссии."
              spans:
                - range: [0, 10]
                  style: typography.body.strong
                - text: SLA
                  link:
                    type: url
                    href: https://example.com/sla
                  style: typography.link
              list:
                type: none
                indent: 0
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            TextPatch(
                key = "missionDashboard.notice",
                defaultText = "Проверьте SLA перед запуском миссии.",
                spans = listOf(
                    TextSpanPatch(start = 0, end = 10, styleRef = "typography.body.strong"),
                    TextSpanPatch(
                        start = 10,
                        end = 13,
                        styleRef = "typography.link",
                        linkUrl = "https://example.com/sla",
                    ),
                ),
                list = TextListSettings(type = TextListType.None, indent = 0),
            ),
            patch,
        )
    }

    @Test
    fun spanTextNotFoundWarnsAndDropsSpan() {
        val (patch, collector) = readSingle(
            """
            text:
              defaultText: hello
              spans:
                - text: missing
                  style: typography.link
            """,
        )
        assertEquals(TextPatch(defaultText = "hello", spans = emptyList()), patch)
        assertTrue(collector.diagnostics.any { "missing" in it.message })
    }

    @Test
    fun maxLinesWithoutOverflowTruncatesWithoutEllipsis() {
        val (patch, _) = readSingle("text:\n  maxLines: 3")
        assertEquals(TextPatch(truncate = TextTruncate(3, ellipsis = false)), patch)
    }
}
