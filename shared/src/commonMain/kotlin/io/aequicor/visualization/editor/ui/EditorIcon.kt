package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.shared.generated.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

// SVG resources are Material Symbols Outlined files from Google Fonts.
internal enum class EditorIcon(resourceName: String) {
    AppMenu("app_menu"),
    Source("source"),
    Screens("screens"),
    Inspector("inspector"),
    Markdown("markdown"),
    Assets("assets"),
    Layers("layers"),
    Select("select"),
    HandPan("hand_pan"),
    Marquee("marquee"),
    Frame("frame"),
    Component("component"),
    Rectangle("rectangle"),
    Ellipse("ellipse"),
    Polygon("polygon"),
    Star("star"),
    Line("line"),
    Arrow("arrow"),
    Pen("pen"),
    Flatten("flatten"),
    OutlineStroke("outline_stroke"),
    PaintBucket("paint_bucket"),
    Text("text"),
    Link("link"),
    Code("code"),
    Design("design"),
    Comments("comments"),
    Position("position"),
    Layout("layout"),
    Fill("fill"),
    Stroke("stroke"),
    Typography("typography"),
    Visibility("visibility"),
    VisibilityOff("visibility_off"),
    Lock("lock"),
    ColorSelector("color_selector"),
    Gradient("gradient"),
    ZoomFit("zoom_fit"),
    Export("export"),
    AlignHorizontalLeft("align_horizontal_left"),
    AlignHorizontalCenter("align_horizontal_center"),
    AlignHorizontalRight("align_horizontal_right"),
    AlignVerticalTop("align_vertical_top"),
    AlignVerticalCenter("align_vertical_center"),
    AlignVerticalBottom("align_vertical_bottom"),
    Rotate("rotate"),
    FlipHorizontal("flip_horizontal"),
    FlipVertical("flip_vertical"),
    AspectRatio("aspect_ratio"),
    ConstraintHorizontal("constraint_horizontal"),
    ConstraintVertical("constraint_vertical"),
    ChevronDown("chevron_down"),
    ChevronUp("chevron_up"),
    Duplicate("duplicate"),
    Trash("trash"),
    Plus("plus"),
    Close("close"),
    Save("save"),
    FolderOpen("folder_open"),
    Folder("folder"),
    Home("home"),
    ArrowBack("arrow_back"),
    ArrowUp("arrow_up"),
    ArrowDown("arrow_down"),
    Image("image"),
    PictureAsPdf("picture_as_pdf"),
    Timer("timer"),
    TouchApp("touch_app"),
    PlayArrow("play_arrow"),
    MotionPhotosOn("motion_photos_on"),
    TransitionSlide("transition_slide"),
    KeyboardReturn("keyboard_return"),
    SwapHorizontal("swap_horiz"),
    BlurOn("blur_on"),
    DeviceDesktop("desktop_windows"),
    DeviceMobile("smartphone"),
    DeviceTablet("tablet_mac"),
    FormatItalic("format_italic"),
    FormatUnderlined("format_underlined"),
    FormatStrikethrough("format_strikethrough"),
    FormatListBulleted("format_list_bulleted"),
    FormatListNumbered("format_list_numbered"),
    FormatAlignJustify("format_align_justify"),
    VerticalAlignTop("vertical_align_top"),
    VerticalAlignCenter("vertical_align_center"),
    VerticalAlignBottom("vertical_align_bottom"),
    FormatLineSpacing("format_line_spacing"),
    Tune("tune"),
    Superscript("superscript"),
    Subscript("subscript"),
    MatchCase("match_case"),
    Diagram("diagram"),
    Language("language"),
    Check("check"),
    Fullscreen("fullscreen"),
    ;

    val resourcePath: String = "files/editor-icons/$resourceName.svg"
}

@Composable
internal fun EditorSvgIcon(
    icon: EditorIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val vector = rememberSvgIcon(icon).value
    if (vector == null) {
        Spacer(modifier)
        return
    }

    Icon(
        imageVector = vector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
private fun rememberSvgIcon(icon: EditorIcon): State<ImageVector?> =
    produceState<ImageVector?>(initialValue = svgIconCache[icon], key1 = icon) {
        // A cold first load fires every icon's fetch at once, racing the browser's HTTP
        // cache and Compose's own resource cache into existence — a transient failure
        // there must not blank the icon forever, so retry a few times before giving up.
        var attempt = 0
        while (value == null) {
            try {
                value = svgIconCache[icon] ?: loadSvgIcon(icon).also { svgIconCache[icon] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                attempt += 1
                if (attempt >= MAX_ICON_LOAD_ATTEMPTS) break
                delay(ICON_LOAD_RETRY_DELAY_MS * attempt)
            }
        }
    }

private const val MAX_ICON_LOAD_ATTEMPTS = 4
private const val ICON_LOAD_RETRY_DELAY_MS = 300L

private val svgIconCache = mutableMapOf<EditorIcon, ImageVector>()

private suspend fun loadSvgIcon(icon: EditorIcon): ImageVector =
    parseSvgIcon(icon.name, Res.readBytes(icon.resourcePath).decodeToString())

private data class SvgPathElement(
    val pathData: String,
    val translateX: Float,
    val translateY: Float,
)

private fun parseSvgIcon(name: String, svg: String): ImageVector {
    val svgTag = SvgTagRegex.find(svg)?.value.orEmpty()
    val svgAttributes = parseAttributes(svgTag)
    val viewBox = parseViewBox(svgAttributes)
    val width = parseFloatPrefix(svgAttributes["width"]) ?: 24f
    val height = parseFloatPrefix(svgAttributes["height"]) ?: 24f
    val paths = PathTagRegex.findAll(svg).mapNotNull { match ->
        val attributes = parseAttributes(match.value)
        val d = attributes["d"] ?: return@mapNotNull null
        val translation = parseTranslate(attributes["transform"])
        SvgPathElement(
            pathData = d,
            translateX = translation.first,
            translateY = translation.second,
        )
    }.toList()

    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = width.dp,
        defaultHeight = height.dp,
        viewportWidth = viewBox[2],
        viewportHeight = viewBox[3],
    )

    builder.addGroup(
        translationX = -viewBox[0],
        translationY = -viewBox[1],
    )
    paths.forEachIndexed { index, element ->
        if (element.translateX != 0f || element.translateY != 0f) {
            builder.addGroup(
                translationX = element.translateX,
                translationY = element.translateY,
            )
            builder.addPath(
                pathData = parsePathNodes(element.pathData),
                name = "$name-$index",
                fill = SolidColor(Color.Black),
            )
            builder.clearGroup()
        } else {
            builder.addPath(
                pathData = parsePathNodes(element.pathData),
                name = "$name-$index",
                fill = SolidColor(Color.Black),
            )
        }
    }
    builder.clearGroup()

    return builder.build()
}

private fun parsePathNodes(pathData: String) =
    PathParser().parsePathString(pathData).toNodes().toList()

private fun parseViewBox(attributes: Map<String, String>): FloatArray {
    val viewBox = attributes["viewBox"]
        ?.trim()
        ?.split(SvgNumberSplitRegex)
        ?.mapNotNull { it.toFloatOrNull() }

    if (viewBox != null && viewBox.size == 4) {
        return floatArrayOf(viewBox[0], viewBox[1], viewBox[2], viewBox[3])
    }

    val width = parseFloatPrefix(attributes["width"]) ?: 24f
    val height = parseFloatPrefix(attributes["height"]) ?: 24f
    return floatArrayOf(0f, 0f, width, height)
}

private fun parseTranslate(transform: String?): Pair<Float, Float> {
    if (transform == null) return 0f to 0f
    val match = TranslateRegex.find(transform) ?: return 0f to 0f
    val x = match.groupValues[1].toFloatOrNull() ?: 0f
    val y = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toFloatOrNull() ?: 0f
    return x to y
}

private fun parseAttributes(tag: String): Map<String, String> =
    AttributeRegex.findAll(tag).associate { match ->
        match.groupValues[1] to unescapeXml(match.groupValues[2])
    }

private fun unescapeXml(value: String): String =
    value
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

private fun parseFloatPrefix(value: String?): Float? =
    value?.let { FloatPrefixRegex.find(it)?.value?.toFloatOrNull() }

private val SvgTagRegex = Regex("""<svg\b[^>]*>""")
private val PathTagRegex = Regex("""<path\b[^>]*>""")
private val AttributeRegex = Regex("""([A-Za-z_:][A-Za-z0-9_:.-]*)\s*=\s*"([^"]*)"""")
private val SvgNumberSplitRegex = Regex("""[\s,]+""")
private val FloatPrefixRegex = Regex("""[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?""")
private val TranslateRegex = Regex("""translate\(\s*([-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?)\s*(?:(?:,|\s)\s*([-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?))?\s*\)""")
