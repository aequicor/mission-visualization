package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.readers.slm
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The core invariant of the generator: `parse(emit(node)) ≡ node`. We compile a CNL sentence
 * into IR, emit CNL back from that IR, recompile, and assert the authored fields survive —
 * plus idempotence (`emit(x) == emit(parse(emit(x)))`).
 */
class CnlEmitterRoundTripTest {
    private fun compileBody(body: String): DesignNode {
        // Build the source by concatenation so a multi-line body is not mangled by trimIndent.
        val header = slm(
            """
            ---
            screen: demo
            sourceLocale: en-US
            targetLocales: [en-US]
            frame: { preset: desktop-1440, width: 1440, height: 1024 }
            ---

            # Demo
            """,
        )
        val src = header + "\n\n" + body.trimIndent() + "\n"
        val result = compileSlm(src)
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            result.diagnostics.filter { it.severity == DesignSeverity.Error }.joinToString { it.message },
        )
        return assertNotNull(result.document).pages.single().children.single()
    }

    private fun leaf(sentence: String, kind: (DesignNodeKind) -> Boolean): DesignNode =
        compileBody(sentence).allDescendants().first { kind(it.kind) }

    private fun assertSameFields(a: DesignNode, b: DesignNode) {
        assertEquals(a.fills, b.fills, "fills")
        assertEquals(a.strokes, b.strokes, "strokes")
        assertEquals(a.cornerRadius, b.cornerRadius, "cornerRadius")
        assertEquals(a.opacity, b.opacity, "opacity")
        assertEquals(a.rotation, b.rotation, "rotation")
        assertEquals(a.size, b.size, "size")
        assertEquals(a.sizing, b.sizing, "sizing")
        assertEquals(a.constraints, b.constraints, "constraints")
        assertEquals(a.layout.mode, b.layout.mode, "mode")
        assertEquals(a.layout.gap, b.layout.gap, "gap")
        assertEquals(a.layout.rowGap, b.layout.rowGap, "rowGap")
        assertEquals(a.layout.columnGap, b.layout.columnGap, "columnGap")
        assertEquals(a.layout.paddingLogical, b.layout.paddingLogical, "padding")
        assertEquals(a.layout.wrap, b.layout.wrap, "wrap")
        assertEquals(a.layout.clipsContent, b.layout.clipsContent, "clip")
        assertEquals(a.layout.justifyContent, b.layout.justifyContent, "distribute")
        assertEquals(a.layout.alignItems, b.layout.alignItems, "alignItems")
        assertEquals(a.layout.baseline, b.layout.baseline, "baseline")
        assertEquals(a.minSize, b.minSize, "minSize")
        assertEquals(a.maxSize, b.maxSize, "maxSize")
        assertEquals(a.anchors, b.anchors, "anchors")
        assertEquals(a.layoutChild.absolute, b.layoutChild.absolute, "absolute")
        assertEquals(a.scroll, b.scroll, "scroll")
        assertEquals(a.gridPlacement, b.gridPlacement, "gridPlacement")
        assertEquals(a.guides, b.guides, "guides")
        assertEquals(a.layoutGrids, b.layoutGrids, "layoutGrids")
        assertEquals(a.layout.columns, b.layout.columns, "columns")
        assertEquals(a.layout.rows, b.layout.rows, "rows")
        assertEquals(a.layout.implicitRows, b.layout.implicitRows, "implicitRows")
        assertEquals(a.layout.implicitRowMin, b.layout.implicitRowMin, "implicitRowMin")
        val instanceA = a.kind as? DesignNodeKind.Instance
        val instanceB = b.kind as? DesignNodeKind.Instance
        if (instanceA != null || instanceB != null) {
            assertEquals(instanceA?.componentId?.literalOrNull(), instanceB?.componentId?.literalOrNull(), "componentId")
            assertEquals(instanceA?.libraryRef, instanceB?.libraryRef, "libraryRef")
            assertEquals(instanceA?.variant, instanceB?.variant, "variant")
            assertEquals(instanceA?.props, instanceB?.props, "props")
            assertEquals(instanceA?.overrides, instanceB?.overrides, "overrides")
            assertEquals(instanceA?.detach, instanceB?.detach, "detach")
            assertEquals(instanceA?.resetOverrides, instanceB?.resetOverrides, "resetOverrides")
        }
        val textA = a.kind as? DesignNodeKind.Text
        val textB = b.kind as? DesignNodeKind.Text
        if (textA != null || textB != null) {
            assertEquals(textA?.textStyle, textB?.textStyle, "textStyle")
            assertEquals(CnlGrammar.textLiteral(a), CnlGrammar.textLiteral(b), "text")
            assertEquals(textA?.autoResize, textB?.autoResize, "autoResize")
            assertEquals(textA?.truncate, textB?.truncate, "truncate")
            assertEquals(textA?.list, textB?.list, "list")
            assertEquals(textA?.textStyleId, textB?.textStyleId, "textStyleId")
            assertEquals(textA?.links, textB?.links, "links")
        }
        val shapeA = a.kind as? DesignNodeKind.Shape
        val shapeB = b.kind as? DesignNodeKind.Shape
        if (shapeA != null || shapeB != null) {
            assertEquals(shapeA?.pointCount, shapeB?.pointCount, "pointCount")
            assertEquals(shapeA?.innerRadius, shapeB?.innerRadius, "innerRadius")
            assertEquals(shapeA?.arcStartDeg, shapeB?.arcStartDeg, "arcStart")
            assertEquals(shapeA?.arcSweepDeg, shapeB?.arcSweepDeg, "arcSweep")
            assertEquals(shapeA?.viewBox, shapeB?.viewBox, "viewBox")
            assertEquals(shapeA?.iconRef, shapeB?.iconRef, "iconRef")
            assertEquals(shapeA?.pathRef, shapeB?.pathRef, "pathRef")
            assertEquals(shapeA?.paths, shapeB?.paths, "paths")
            assertEquals(shapeA?.network, shapeB?.network, "network")
            assertEquals(shapeA?.regionFills, shapeB?.regionFills, "regionFills")
        }
        val mediaA = (a.kind as? DesignNodeKind.Media)?.media
        val mediaB = (b.kind as? DesignNodeKind.Media)?.media
        if (mediaA != null || mediaB != null) {
            assertEquals(mediaA?.assetId, mediaB?.assetId, "media.assetId")
            assertEquals(mediaA?.kind, mediaB?.kind, "media.kind")
            assertEquals(mediaA?.fillMode, mediaB?.fillMode, "media.fillMode")
            assertEquals(mediaA?.focalPoint, mediaB?.focalPoint, "media.focalPoint")
            assertEquals(mediaA?.alt?.defaultText, mediaB?.alt?.defaultText, "media.alt")
            assertEquals(mediaA?.opacity?.literalOrNull(), mediaB?.opacity?.literalOrNull(), "media.opacity")
            assertEquals(mediaA?.blendMode, mediaB?.blendMode, "media.blendMode")
            assertEquals(mediaA?.posterAssetId, mediaB?.posterAssetId, "media.poster")
            assertEquals(mediaA?.autoplay, mediaB?.autoplay, "media.autoplay")
            assertEquals(mediaA?.loop, mediaB?.loop, "media.loop")
            assertEquals(mediaA?.replaceable, mediaB?.replaceable, "media.replaceable")
            assertEquals(mediaA?.muted, mediaB?.muted, "media.muted")
        }
        assertEquals(a.mask, b.mask, "mask")
        assertEquals(
            (a.kind as? DesignNodeKind.BooleanOperation)?.operation,
            (b.kind as? DesignNodeKind.BooleanOperation)?.operation, "booleanOp",
        )
        assertEquals(
            a.interactions.map { it.copy(sourceMap = null) },
            b.interactions.map { it.copy(sourceMap = null) },
            "interactions",
        )
        assertEquals(a.motion, b.motion, "motion")
        assertEquals(a.exportSettings, b.exportSettings, "exportSettings")
        assertEquals(
            a.responsive.map { it.selectors to it.patch },
            b.responsive.map { it.selectors to it.patch },
            "responsive",
        )
    }

    private fun assertLeafRoundTrips(sentence: String, kind: (DesignNodeKind) -> Boolean) {
        val node1 = leaf(sentence, kind)
        val cnl = CnlEmitter.emitSentence(node1)
        val node2 = leaf(cnl, kind)
        assertSameFields(node1, node2)
        assertEquals(cnl, CnlEmitter.emitSentence(node2), "emit is idempotent")
    }

    @Test
    fun rectangleWithSizeFillRadius() =
        assertLeafRoundTrips("Rectangle 520 by 8 color #22C55E radius 4") { it is DesignNodeKind.Shape }

    @Test
    fun rectangleWithStroke() =
        assertLeafRoundTrips("Rectangle 96 by 32 color #14532D stroke #1F2937 2 outside radius 16") {
            it is DesignNodeKind.Shape
        }

    @Test
    fun rectangleWithOpacityAndRotation() =
        assertLeafRoundTrips("Rectangle 40 by 40 color #2563EB opacity 0.5 rotation 30") {
            it is DesignNodeKind.Shape
        }

    @Test
    fun rectangleWithAlphaFill() =
        assertLeafRoundTrips("Rectangle 40 by 40 color #2563EB80") { it is DesignNodeKind.Shape }

    @Test
    fun ellipse() =
        assertLeafRoundTrips("Ellipse 40 by 40 color #2563EB") { it is DesignNodeKind.Shape }

    @Test
    fun rectangleWithStackedFills() =
        assertLeafRoundTrips("Rectangle 40 by 40 color #FF0000 color #00FF0080") { it is DesignNodeKind.Shape }

    @Test
    fun rectangleWithTokenFill() =
        assertLeafRoundTrips("Rectangle 40 by 40 color \$color.accent") { it is DesignNodeKind.Shape }

    @Test
    fun linearGradientFill() =
        assertLeafRoundTrips("Rectangle 200 by 120 gradient (linear from (0 0) to (0 1) stops (#4F46E5 at 0) (#9333EA at 1))") {
            it is DesignNodeKind.Shape
        }

    @Test
    fun radialGradientWithOpacity() =
        assertLeafRoundTrips("Rectangle 80 by 80 color #0B0B0F gradient (radial stops (#4F46E5 at 0) (#9333EA at 1) opacity 0.8)") {
            it is DesignNodeKind.Shape
        }

    @Test
    fun imageFill() =
        assertLeafRoundTrips("Rectangle 300 by 200 image (asset «hero.jpg» crop focus (0.5 0.5) replaceable)") {
            it is DesignNodeKind.Shape
        }

    @Test
    fun videoFill() =
        assertLeafRoundTrips("Rectangle 300 by 200 video (asset «promo.mp4» poster «promo.jpg» autoplay loop muted no)") {
            it is DesignNodeKind.Shape
        }

    @Test
    fun solidFillWithPerFillProps() =
        assertLeafRoundTrips("Rectangle 40 by 40 color (#4F46E5 opacity 0.5 blend multiply visible no)") {
            it is DesignNodeKind.Shape
        }

    @Test
    fun perCornerRadius() =
        assertLeafRoundTrips("Rectangle 120 by 80 color #111827 radius (12 12 0 0)") { it is DesignNodeKind.Shape }

    @Test
    fun radiusWithSmoothing() =
        assertLeafRoundTrips("Rectangle 40 by 40 color #2563EB radius 16 smoothing 0.6") { it is DesignNodeKind.Shape }

    @Test
    fun nodeBlendMode() =
        assertLeafRoundTrips("Rectangle 40 by 40 color #FF0000 blend multiply") { it is DesignNodeKind.Shape }

    @Test
    fun perCornerRadiusSmoothingBlend() =
        assertLeafRoundTrips("Rectangle 96 by 96 color #14532D radius (24 24 0 0) smoothing 0.6 blend screen") {
            it is DesignNodeKind.Shape
        }

    @Test
    fun sharedStyleRefs() =
        assertLeafRoundTrips("Frame styles (fill card.primary effect shadow.card grid layout.12col)") {
            it is DesignNodeKind.Frame
        }

    @Test
    fun strokeStackDashCapJoin() =
        assertLeafRoundTrips("Rectangle 40 by 40 stroke (color #4F46E5 color \$accent weight 2 align outside dash (4 2) cap round join round)") {
            it is DesignNodeKind.Shape
        }

    @Test
    fun strokeDashCap() =
        assertLeafRoundTrips("Rectangle 40 by 40 stroke (color #111827 dash (6 3) cap round)") { it is DesignNodeKind.Shape }

    @Test
    fun dropShadowEffect() =
        assertLeafRoundTrips("Rectangle 40 by 40 color #FFFFFF effect (dropShadow color #00000040 offset (0 2) blur 8)") {
            it is DesignNodeKind.Shape
        }

    @Test
    fun multipleEffects() =
        assertLeafRoundTrips("Rectangle 40 by 40 effect (innerShadow color \$shadow offset (0 1) blur 4 spread 1) effect (backgroundBlur 12)") {
            it is DesignNodeKind.Shape
        }

    @Test
    fun strokeAndLayerBlur() =
        assertLeafRoundTrips("Rectangle 40 by 40 stroke (color #111827) effect (layerBlur 6)") { it is DesignNodeKind.Shape }

    @Test
    fun typographyDeep() =
        assertLeafRoundTrips(
            "Text «Ship status» font «Inter Display» line-height 140% tracking 0.5 paragraph-spacing 8 text-align center text-valign top case upper decoration underline",
        ) { it is DesignNodeKind.Text }

    @Test
    fun typographyFeaturesAxesListAutosize() =
        assertLeafRoundTrips(
            "Text «Metrics» features (liga on) (tnum off) axes (opsz 28) (wght 620) autosize height truncate 2 list (bullet indent 1)",
        ) { it is DesignNodeKind.Text }

    @Test
    fun typographyPercentAndMaxLines() =
        assertLeafRoundTrips(
            "Text «Caption» line-height 20 tracking 1% decoration strikethrough case title text-align justified maxLines 3 text-style \$body",
        ) { it is DesignNodeKind.Text }

    @Test
    fun sizingModesWithMinMax() =
        assertLeafRoundTrips("Rectangle width (fill min 320 max 520) height hug") { it is DesignNodeKind.Shape }

    @Test
    fun sizingFixedWithMin() =
        assertLeafRoundTrips("Rectangle width (fixed 200 min 100) height (fill max 400)") { it is DesignNodeKind.Shape }

    @Test
    fun containerAlignDistributeWrapClip() =
        assertLeafRoundTrips("Frame column align (inline stretch) distribute space-between wrap clip") {
            it is DesignNodeKind.Frame
        }

    @Test
    fun absoluteWithAnchors() =
        assertLeafRoundTrips("Frame absolute anchor (inlineEnd 4 blockStart 4) width (fixed 8) height (fixed 8)") {
            it is DesignNodeKind.Frame
        }

    @Test
    fun perAxisConstraints() =
        assertLeafRoundTrips("Rectangle 40 by 40 constraints (horizontal left-right vertical scale)") { it is DesignNodeKind.Shape }

    @Test
    fun gapAxes() =
        assertLeafRoundTrips("Frame grid gap (row 24 column 24)") { it is DesignNodeKind.Frame }

    @Test
    fun gapAuto() =
        assertLeafRoundTrips("Frame row gap auto") { it is DesignNodeKind.Frame }

    @Test
    fun overflowAndScroll() =
        assertLeafRoundTrips("Frame overflow (x hidden y auto) scroll (direction vertical fixedChildren (missionPanelHeader))") {
            it is DesignNodeKind.Frame
        }

    @Test
    fun gridColumnsImplicitRowsGap() =
        assertLeafRoundTrips("Frame grid columns (count 12 track 1fr) rows (auto min 96) gap (row 24 column 24)") {
            it is DesignNodeKind.Frame
        }

    @Test
    fun gridExplicitRows() =
        assertLeafRoundTrips("Frame grid columns (count 3 track 2fr) rows (count 2 track 1fr)") { it is DesignNodeKind.Frame }

    @Test
    fun gridChildPlacement() =
        assertLeafRoundTrips("Rectangle place (column 1 row 1 columnSpan 8 rowSpan 2)") { it is DesignNodeKind.Shape }

    @Test
    fun guidesAndGrids() =
        assertLeafRoundTrips("Frame guides (vertical 72) (horizontal 120) grids (columns count 12 gutter 24 margin 72 alignment stretch color #EEEEEE)") {
            it is DesignNodeKind.Frame
        }

    @Test
    fun textWithExternalLink() =
        assertLeafRoundTrips("Text «Read more» link (range (0 9) url «https://a.co»)") { it is DesignNodeKind.Text }

    @Test
    fun textWithNodeLink() =
        assertLeafRoundTrips("Text «Open cart» link (range (0 9) to checkout)") { it is DesignNodeKind.Text }

    @Test
    fun textWithMultipleLinks() =
        assertLeafRoundTrips("Text «Read the terms and privacy» link (range (4 9) url «https://a.co/terms») link (range (14 21) to privacy)") {
            it is DesignNodeKind.Text
        }

    @Test
    fun textWithSizeWeightColor() =
        assertLeafRoundTrips("Text «Active missions» size 20 bold color #F8FAFC") { it is DesignNodeKind.Text }

    @Test
    fun buttonWithText() =
        assertLeafRoundTrips("Button «Create mission» size 16 bold color #22C55E") { it is DesignNodeKind.Text }

    @Test
    fun instanceVariantAndProps() =
        assertLeafRoundTrips("Instance of ds/Button variant (size md tone primary) props (label «Save» loading true count 3)") {
            it is DesignNodeKind.Instance
        }

    @Test
    fun instanceLibraryAndDataProps() =
        assertLeafRoundTrips("Instance of ds/Button library acme/ui props (total {{cart.total}} icon (swap ds/Icon/Check))") {
            it is DesignNodeKind.Instance
        }

    @Test
    fun instanceDetachReset() =
        assertLeafRoundTrips("Instance of ds/Card detach reset") { it is DesignNodeKind.Instance }

    @Test
    fun instanceSlotsAndNested() =
        assertLeafRoundTrips(
            "Instance of ds/Card slot actions (ds/Button props (label «Save»)) (ds/Button props (label «Cancel»)) " +
                "nested title (variant (size sm) props (text «Overview»))",
        ) { it is DesignNodeKind.Instance }

    @Test
    fun instanceContentProp() =
        assertLeafRoundTrips("Instance of ds/Badge props (label (text «New» key badge.new))") {
            it is DesignNodeKind.Instance
        }

    @Test
    fun containerHeadingRoundTrips() {
        val doc = """
            ## Card column gap 12 padding 24 color #111827 radius 16

            Rectangle 100 by 8 color #22C55E
            Text «Active missions» size 20 bold color #F8FAFC
        """.trimIndent()
        val card1 = compileBody(doc).children.first()
        assertEquals("Card", card1.name)

        val emitted = CnlEmitter.emitSubtree(card1, level = 2).joinToString("\n")
        val card2 = compileBody(emitted).children.first()

        assertEquals(card1.name, card2.name)
        assertSameFields(card1, card2)
        assertEquals(card1.children.size, card2.children.size, "child count")
        card1.children.zip(card2.children).forEach { (childA, childB) -> assertSameFields(childA, childB) }
    }

    @Test
    fun imageMediaNode() =
        assertLeafRoundTrips(
            "Image media (asset media/hero video crop focus center alt «Hero banner» opacity 0.85 blend multiply poster media/hero_thumb autoplay loop unmuted)",
        ) { it is DesignNodeKind.Media }

    @Test
    fun imageMediaFitMode() =
        assertLeafRoundTrips("Image media (asset icons/avatar fit)") { it is DesignNodeKind.Media }

    @Test
    fun starPointsInner() =
        assertLeafRoundTrips("Star points 5 inner 0.45") { it is DesignNodeKind.Shape }

    @Test
    fun polygonPoints() =
        assertLeafRoundTrips("Polygon points 6") { it is DesignNodeKind.Shape }

    @Test
    fun vectorViewBoxIcon() =
        assertLeafRoundTrips("Vector viewbox (0 0 24 24) icon ds/Icon/Plus") { it is DesignNodeKind.Shape }

    @Test
    fun vectorSvgViewBox() =
        assertLeafRoundTrips("Vector viewbox (0 0 48 48) svg brand/logo_svg") { it is DesignNodeKind.Shape }

    @Test
    fun vectorInlinePaths() =
        assertLeafRoundTrips("Vector viewbox (0 0 24 24) path «M4 12L20 12» path «M12 4L12 20» evenodd") {
            it is DesignNodeKind.Shape
        }

    @Test
    fun vectorNetwork() =
        assertLeafRoundTrips(
            "Vector network (vertex (0 0 corner) vertex (24 0) vertex (24 24 in (-8 0) out (8 0) mirror angle) segment (0 1) segment (1 2) segment (2 0) region evenodd loops (0 1 2))",
        ) { it is DesignNodeKind.Shape }

    @Test
    fun ellipseArcPie() =
        assertLeafRoundTrips("Ellipse 40 by 40 color #2563EB arc (-90 270)") { it is DesignNodeKind.Shape }

    @Test
    fun ellipseArcDonut() =
        assertLeafRoundTrips("Ellipse 80 by 80 color #F59E0B inner 0.5 arc (0 180)") { it is DesignNodeKind.Shape }

    @Test
    fun vectorNetworkVertexCornerRadius() =
        assertLeafRoundTrips(
            "Vector network (vertex (0 0 corner radius 6) vertex (24 0) vertex (24 24) segment (0 1) segment (1 2) segment (2 0))",
        ) { it is DesignNodeKind.Shape }

    @Test
    fun vectorNetworkRegionSolidFill() =
        assertLeafRoundTrips(
            "Vector network (vertex (0 0) vertex (24 0) vertex (24 24) segment (0 1) segment (1 2) segment (2 0) region loops (0 1 2) fill #22C55E)",
        ) { it is DesignNodeKind.Shape }

    @Test
    fun vectorNetworkRegionTokenFill() =
        assertLeafRoundTrips(
            "Vector network (vertex (0 0) vertex (24 0) vertex (24 24) segment (0 1) segment (1 2) segment (2 0) region evenodd loops (0 1 2) fill \$color.accent)",
        ) { it is DesignNodeKind.Shape }

    @Test
    fun booleanOperation() =
        assertLeafRoundTrips("Frame boolean subtract") { it is DesignNodeKind.BooleanOperation }

    @Test
    fun maskAlphaClips() =
        assertLeafRoundTrips("Rectangle mask alpha clips (title_bar hero_image)") { it is DesignNodeKind.Shape }

    @Test
    fun maskLuminance() =
        assertLeafRoundTrips("Ellipse mask luminance") { it is DesignNodeKind.Shape }

    @Test
    fun interactionNavigateSmartAnimate() =
        assertLeafRoundTrips("Button onClick navigate (home) animate (type smartAnimate duration 400)") {
            it is DesignNodeKind.Text
        }

    @Test
    fun interactionOnKeySetVariableCloseOverlay() =
        assertLeafRoundTrips("Frame onKey (Enter) setVariable (isOpen) to (true) closeOverlay") {
            it is DesignNodeKind.Frame
        }

    @Test
    fun interactionOverlayThenWhileHoveringVariant() =
        assertLeafRoundTrips(
            "Rectangle onClick openOverlay (menu) overlay (position bottomCenter closeOnOutside false) " +
                "whileHovering changeToVariant (self) variant (state hover)",
        ) { it is DesignNodeKind.Shape }

    @Test
    fun interactionAfterDelaySpring() =
        assertLeafRoundTrips(
            "Frame afterDelay (3000) navigate (splash) animate (easing spring stiffness 120 damping 14 duration 500)",
        ) { it is DesignNodeKind.Frame }

    @Test
    fun motionKeyframes() =
        assertLeafRoundTrips("Vector motion (loader) duration 800 loop frames (0 rotation 0) (1 rotation 360)") {
            it is DesignNodeKind.Shape
        }

    @Test
    fun interactionOpenLink() =
        assertLeafRoundTrips("Text «Docs» onClick openLink (https://example.com/docs)") { it is DesignNodeKind.Text }

    @Test
    fun interactionScrollToAndRunActionSet() =
        assertLeafRoundTrips("Button onClick scrollTo (section2) animated (false) onPress runActionSet (resetAll)") {
            it is DesignNodeKind.Text
        }

    @Test
    fun interactionSwapOverlayPush() =
        assertLeafRoundTrips("Frame onClick swapOverlay (sheet) animate (type push direction right)") {
            it is DesignNodeKind.Frame
        }

    @Test
    fun interactionOnVariableChangeVariant() =
        assertLeafRoundTrips("Frame onVariableChange (theme) changeToVariant (root) variant (mode dark)") {
            it is DesignNodeKind.Frame
        }

    @Test
    fun interactionBack() =
        assertLeafRoundTrips("Button onClick back") { it is DesignNodeKind.Text }

    @Test
    fun fillDataBinding() =
        assertLeafRoundTrips("Rectangle 120 by 40 color {{theme.bg}}") { it is DesignNodeKind.Shape }

    @Test
    fun fillDataBindingWithProps() =
        assertLeafRoundTrips("Rectangle 120 by 40 color ({{theme.bg}} opacity 0.5)") { it is DesignNodeKind.Shape }

    @Test
    fun opacityDataBinding() =
        assertLeafRoundTrips("Rectangle 120 by 40 color #FFFFFF opacity {{fade}}") { it is DesignNodeKind.Shape }

    @Test
    fun opacityVarBinding() =
        assertLeafRoundTrips("Rectangle 120 by 40 color #FFFFFF opacity \$anim.fade") { it is DesignNodeKind.Shape }

    @Test
    fun responsiveLayoutVariant() =
        assertLeafRoundTrips("Frame column gap 12 when (breakpoint sm) column gap 8") { it is DesignNodeKind.Frame }

    @Test
    fun responsiveStyleVariant() =
        assertLeafRoundTrips("Rectangle 100 by 40 color #4F46E5 when (theme dark) color #0B0B0B") {
            it is DesignNodeKind.Shape
        }

    @Test
    fun responsiveMultiDimensionVariants() =
        assertLeafRoundTrips("Frame when (platform ios density high) radius 12 when (breakpoint lg) row gap 24") {
            it is DesignNodeKind.Frame
        }

    @Test
    fun responsiveTextVariants() =
        assertLeafRoundTrips("Text «Title» when (theme dark) size 18 bold when (density high) size 20") {
            it is DesignNodeKind.Text
        }

    @Test
    fun exportSettingsList() =
        assertLeafRoundTrips("Image export (png at 2 «@2x») (svg)") { it is DesignNodeKind.Media }

    @Test
    fun exportMultipleClauses() =
        assertLeafRoundTrips("Rectangle 200 by 120 export (png) export (svg)") { it is DesignNodeKind.Shape }

    // Handoff is lifted to DesignDocument.handoff (no node field, not re-emittable) → compile-clean smoke test only.
    @Test
    fun handoffNoteMeasureCodeCompilesClean() {
        val node = leaf(
            "Rectangle 40 by 40 note «Keep 8pt spacing» (target card audience dev) " +
                "measure (from title to cta inline value 16) code (framework «Compose» component «MissionCard»)",
        ) { it is DesignNodeKind.Shape }
        assertTrue(node.kind is DesignNodeKind.Shape)
    }
}
