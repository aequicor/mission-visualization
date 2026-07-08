package io.aequicor.visualization.editor.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.aequicor.visualization.shared.generated.resources.Res
import io.aequicor.visualization.shared.generated.resources.align_horizontal_center
import io.aequicor.visualization.shared.generated.resources.align_horizontal_left
import io.aequicor.visualization.shared.generated.resources.align_horizontal_right
import io.aequicor.visualization.shared.generated.resources.align_vertical_bottom
import io.aequicor.visualization.shared.generated.resources.align_vertical_center
import io.aequicor.visualization.shared.generated.resources.align_vertical_top
import io.aequicor.visualization.shared.generated.resources.app_menu
import io.aequicor.visualization.shared.generated.resources.aspect_ratio
import io.aequicor.visualization.shared.generated.resources.assets
import io.aequicor.visualization.shared.generated.resources.arrow_down
import io.aequicor.visualization.shared.generated.resources.arrow_up
import io.aequicor.visualization.shared.generated.resources.chevron_down
import io.aequicor.visualization.shared.generated.resources.chevron_up
import io.aequicor.visualization.shared.generated.resources.close
import io.aequicor.visualization.shared.generated.resources.code
import io.aequicor.visualization.shared.generated.resources.color_selector
import io.aequicor.visualization.shared.generated.resources.comments
import io.aequicor.visualization.shared.generated.resources.component
import io.aequicor.visualization.shared.generated.resources.constraint_horizontal
import io.aequicor.visualization.shared.generated.resources.constraint_vertical
import io.aequicor.visualization.shared.generated.resources.design
import io.aequicor.visualization.shared.generated.resources.duplicate
import io.aequicor.visualization.shared.generated.resources.export
import io.aequicor.visualization.shared.generated.resources.fill
import io.aequicor.visualization.shared.generated.resources.flip_horizontal
import io.aequicor.visualization.shared.generated.resources.flip_vertical
import io.aequicor.visualization.shared.generated.resources.frame
import io.aequicor.visualization.shared.generated.resources.gradient
import io.aequicor.visualization.shared.generated.resources.hand_pan
import io.aequicor.visualization.shared.generated.resources.inspector
import io.aequicor.visualization.shared.generated.resources.layers
import io.aequicor.visualization.shared.generated.resources.layout
import io.aequicor.visualization.shared.generated.resources.link
import io.aequicor.visualization.shared.generated.resources.lock
import io.aequicor.visualization.shared.generated.resources.markdown
import io.aequicor.visualization.shared.generated.resources.marquee
import io.aequicor.visualization.shared.generated.resources.pen
import io.aequicor.visualization.shared.generated.resources.plus
import io.aequicor.visualization.shared.generated.resources.position
import io.aequicor.visualization.shared.generated.resources.rectangle
import io.aequicor.visualization.shared.generated.resources.rotate
import io.aequicor.visualization.shared.generated.resources.screens
import io.aequicor.visualization.shared.generated.resources.select
import io.aequicor.visualization.shared.generated.resources.source
import io.aequicor.visualization.shared.generated.resources.stroke
import io.aequicor.visualization.shared.generated.resources.text
import io.aequicor.visualization.shared.generated.resources.trash
import io.aequicor.visualization.shared.generated.resources.typography
import io.aequicor.visualization.shared.generated.resources.visibility
import io.aequicor.visualization.shared.generated.resources.zoom_fit
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

internal enum class EditorIcon(val resource: DrawableResource) {
    AppMenu(Res.drawable.app_menu),
    Source(Res.drawable.source),
    Screens(Res.drawable.screens),
    Inspector(Res.drawable.inspector),
    Markdown(Res.drawable.markdown),
    Assets(Res.drawable.assets),
    Layers(Res.drawable.layers),
    Select(Res.drawable.select),
    HandPan(Res.drawable.hand_pan),
    Marquee(Res.drawable.marquee),
    Frame(Res.drawable.frame),
    Component(Res.drawable.component),
    Rectangle(Res.drawable.rectangle),
    Pen(Res.drawable.pen),
    Text(Res.drawable.text),
    Link(Res.drawable.link),
    Code(Res.drawable.code),
    Design(Res.drawable.design),
    Comments(Res.drawable.comments),
    Position(Res.drawable.position),
    Layout(Res.drawable.layout),
    Fill(Res.drawable.fill),
    Stroke(Res.drawable.stroke),
    Typography(Res.drawable.typography),
    Visibility(Res.drawable.visibility),
    Lock(Res.drawable.lock),
    ColorSelector(Res.drawable.color_selector),
    Gradient(Res.drawable.gradient),
    ZoomFit(Res.drawable.zoom_fit),
    Export(Res.drawable.export),
    AlignHorizontalLeft(Res.drawable.align_horizontal_left),
    AlignHorizontalCenter(Res.drawable.align_horizontal_center),
    AlignHorizontalRight(Res.drawable.align_horizontal_right),
    AlignVerticalTop(Res.drawable.align_vertical_top),
    AlignVerticalCenter(Res.drawable.align_vertical_center),
    AlignVerticalBottom(Res.drawable.align_vertical_bottom),
    Rotate(Res.drawable.rotate),
    FlipHorizontal(Res.drawable.flip_horizontal),
    FlipVertical(Res.drawable.flip_vertical),
    AspectRatio(Res.drawable.aspect_ratio),
    ConstraintHorizontal(Res.drawable.constraint_horizontal),
    ConstraintVertical(Res.drawable.constraint_vertical),
    ChevronDown(Res.drawable.chevron_down),
    ChevronUp(Res.drawable.chevron_up),
    Duplicate(Res.drawable.duplicate),
    Trash(Res.drawable.trash),
    Plus(Res.drawable.plus),
    Close(Res.drawable.close),
    ArrowUp(Res.drawable.arrow_up),
    ArrowDown(Res.drawable.arrow_down),
}

@Composable
internal fun EditorSvgIcon(
    icon: EditorIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(icon.resource),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
