package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.BaselineAlign
import io.aequicor.visualization.subsystems.figures.BooleanOperationKind
import io.aequicor.visualization.engine.ir.model.ComponentPropertyType
import io.aequicor.visualization.engine.ir.model.EasingKind
import io.aequicor.visualization.engine.ir.model.ExportFormat
import io.aequicor.visualization.engine.ir.model.GradientKind
import io.aequicor.visualization.subsystems.figures.HandleMirror
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.ImageScaleMode
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.JustifyContent
import io.aequicor.visualization.engine.ir.model.LayoutGridAlignment
import io.aequicor.visualization.engine.ir.model.LayoutGridType
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.MaskType
import io.aequicor.visualization.engine.ir.model.MeasureAxis
import io.aequicor.visualization.engine.ir.model.MediaKind
import io.aequicor.visualization.engine.ir.model.OverflowMode
import io.aequicor.visualization.engine.ir.model.OverlayPosition
import io.aequicor.visualization.engine.ir.model.ResponsiveDimension
import io.aequicor.visualization.engine.ir.model.ScrollOverflow
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.model.TextCase
import io.aequicor.visualization.engine.ir.model.TextDecorationKind
import io.aequicor.visualization.engine.ir.model.TextListType
import io.aequicor.visualization.engine.ir.model.TransitionType
import io.aequicor.visualization.engine.ir.model.VariableType
import io.aequicor.visualization.engine.ir.model.VerticalConstraint

/** SLM spelling -> IR enum tables used by the typed-block readers. */
internal object ReaderEnums {
    val layoutMode: Map<String, LayoutMode> = mapOf(
        "none" to LayoutMode.None,
        "row" to LayoutMode.Horizontal,
        "horizontal" to LayoutMode.Horizontal,
        "column" to LayoutMode.Vertical,
        "vertical" to LayoutMode.Vertical,
        "grid" to LayoutMode.Grid,
    )

    val align: Map<String, AlignItems> = mapOf(
        "start" to AlignItems.Start,
        "center" to AlignItems.Center,
        "end" to AlignItems.End,
        "baseline" to AlignItems.Baseline,
        "stretch" to AlignItems.Stretch,
    )

    val baseline: Map<String, BaselineAlign> = mapOf(
        "first" to BaselineAlign.First,
        "last" to BaselineAlign.Last,
    )

    val distribution: Map<String, JustifyContent> = mapOf(
        "packed" to JustifyContent.Start,
        "start" to JustifyContent.Start,
        "center" to JustifyContent.Center,
        "end" to JustifyContent.End,
        "space-between" to JustifyContent.SpaceBetween,
        "spaceBetween" to JustifyContent.SpaceBetween,
    )

    val sizingMode: Map<String, SizingMode> = mapOf(
        "fixed" to SizingMode.Fixed,
        "hug" to SizingMode.Hug,
        "fill" to SizingMode.Fill,
    )

    val overflow: Map<String, OverflowMode> = mapOf(
        "visible" to OverflowMode.Visible,
        "hidden" to OverflowMode.Hidden,
        "auto" to OverflowMode.Auto,
    )

    val scrollDirection: Map<String, ScrollOverflow> = mapOf(
        "none" to ScrollOverflow.None,
        "horizontal" to ScrollOverflow.Horizontal,
        "vertical" to ScrollOverflow.Vertical,
        "both" to ScrollOverflow.Both,
    )

    val horizontalConstraint: Map<String, HorizontalConstraint> = mapOf(
        "left" to HorizontalConstraint.Left,
        "right" to HorizontalConstraint.Right,
        "center" to HorizontalConstraint.Center,
        "left-right" to HorizontalConstraint.LeftRight,
        "scale" to HorizontalConstraint.Scale,
    )

    val verticalConstraint: Map<String, VerticalConstraint> = mapOf(
        "top" to VerticalConstraint.Top,
        "bottom" to VerticalConstraint.Bottom,
        "center" to VerticalConstraint.Center,
        "top-bottom" to VerticalConstraint.TopBottom,
        "scale" to VerticalConstraint.Scale,
    )

    val gridType: Map<String, LayoutGridType> = mapOf(
        "columns" to LayoutGridType.Columns,
        "rows" to LayoutGridType.Rows,
        "grid" to LayoutGridType.Grid,
    )

    val gridAlignment: Map<String, LayoutGridAlignment> = mapOf(
        "stretch" to LayoutGridAlignment.Stretch,
        "start" to LayoutGridAlignment.Start,
        "center" to LayoutGridAlignment.Center,
        "end" to LayoutGridAlignment.End,
    )

    val gradientKind: Map<String, GradientKind> = mapOf(
        "linearGradient" to GradientKind.Linear,
        "radialGradient" to GradientKind.Radial,
        "angularGradient" to GradientKind.Angular,
        "diamondGradient" to GradientKind.Diamond,
    )

    val strokeAlign: Map<String, StrokeAlign> = mapOf(
        "inside" to StrokeAlign.Inside,
        "center" to StrokeAlign.Center,
        "outside" to StrokeAlign.Outside,
    )

    val textAlignHorizontal: Map<String, TextAlignHorizontal> = mapOf(
        "left" to TextAlignHorizontal.Left,
        "start" to TextAlignHorizontal.Left,
        "center" to TextAlignHorizontal.Center,
        "right" to TextAlignHorizontal.Right,
        "end" to TextAlignHorizontal.Right,
        "justified" to TextAlignHorizontal.Justified,
        "justify" to TextAlignHorizontal.Justified,
    )

    val textAlignVertical: Map<String, TextAlignVertical> = mapOf(
        "top" to TextAlignVertical.Top,
        "center" to TextAlignVertical.Center,
        "bottom" to TextAlignVertical.Bottom,
    )

    val textCase: Map<String, TextCase> = mapOf(
        "none" to TextCase.None,
        "upper" to TextCase.Upper,
        "lower" to TextCase.Lower,
        "title" to TextCase.Title,
    )

    val textDecoration: Map<String, TextDecorationKind> = mapOf(
        "none" to TextDecorationKind.None,
        "underline" to TextDecorationKind.Underline,
        "strikethrough" to TextDecorationKind.Strikethrough,
    )

    val textListType: Map<String, TextListType> = mapOf(
        "none" to TextListType.None,
        "bullet" to TextListType.Bullet,
        "ordered" to TextListType.Ordered,
    )

    val shapeKind: Map<String, ShapeType> = mapOf(
        "rectangle" to ShapeType.Rectangle,
        "ellipse" to ShapeType.Ellipse,
        "polygon" to ShapeType.Polygon,
        "star" to ShapeType.Star,
        "line" to ShapeType.Line,
        "arrow" to ShapeType.Arrow,
        "vector" to ShapeType.Vector,
    )

    val booleanOp: Map<String, BooleanOperationKind> = mapOf(
        "union" to BooleanOperationKind.Union,
        "subtract" to BooleanOperationKind.Subtract,
        "intersect" to BooleanOperationKind.Intersect,
        "exclude" to BooleanOperationKind.Exclude,
    )

    val handleMirror: Map<String, HandleMirror> = mapOf(
        "none" to HandleMirror.None,
        "angle" to HandleMirror.Angle,
        "angleAndLength" to HandleMirror.AngleAndLength,
    )

    val maskType: Map<String, MaskType> = mapOf(
        "alpha" to MaskType.Alpha,
        "vector" to MaskType.Vector,
        "luminance" to MaskType.Luminance,
    )

    val mediaKind: Map<String, MediaKind> = mapOf(
        "image" to MediaKind.Image,
        "video" to MediaKind.Video,
    )

    val fillMode: Map<String, ImageScaleMode> = mapOf(
        "fill" to ImageScaleMode.Fill,
        "fit" to ImageScaleMode.Fit,
        "crop" to ImageScaleMode.Crop,
        "tile" to ImageScaleMode.Tile,
        "stretch" to ImageScaleMode.Stretch,
    )

    val trigger: Map<String, InteractionTrigger> = mapOf(
        "onClick" to InteractionTrigger.OnClick,
        "onHover" to InteractionTrigger.OnHover,
        "onPress" to InteractionTrigger.OnPress,
        "onDrag" to InteractionTrigger.OnDrag,
        "onKey" to InteractionTrigger.OnKey,
        "afterDelay" to InteractionTrigger.AfterDelay,
        "whileHovering" to InteractionTrigger.WhileHovering,
        "whilePressed" to InteractionTrigger.WhilePressed,
        "onVariableChange" to InteractionTrigger.OnVariableChange,
    )

    val overlayPosition: Map<String, OverlayPosition> = mapOf(
        "center" to OverlayPosition.Center,
        "topLeft" to OverlayPosition.TopLeft,
        "topCenter" to OverlayPosition.TopCenter,
        "topRight" to OverlayPosition.TopRight,
        "bottomLeft" to OverlayPosition.BottomLeft,
        "bottomCenter" to OverlayPosition.BottomCenter,
        "bottomRight" to OverlayPosition.BottomRight,
        "manual" to OverlayPosition.Manual,
    )

    val transitionType: Map<String, TransitionType> = mapOf(
        "instant" to TransitionType.Instant,
        "dissolve" to TransitionType.Dissolve,
        "smartAnimate" to TransitionType.SmartAnimate,
        "moveIn" to TransitionType.MoveIn,
        "moveOut" to TransitionType.MoveOut,
        "push" to TransitionType.Push,
        "slideIn" to TransitionType.SlideIn,
        "slideOut" to TransitionType.SlideOut,
    )

    val easing: Map<String, EasingKind> = mapOf(
        "linear" to EasingKind.Linear,
        "easeIn" to EasingKind.EaseIn,
        "easeOut" to EasingKind.EaseOut,
        "easeInOut" to EasingKind.EaseInOut,
        "easeInBack" to EasingKind.EaseInBack,
        "easeOutBack" to EasingKind.EaseOutBack,
    )

    val responsiveDimension: Map<String, ResponsiveDimension> = mapOf(
        "breakpoint" to ResponsiveDimension.Breakpoint,
        "devicePreset" to ResponsiveDimension.DevicePreset,
        "platform" to ResponsiveDimension.Platform,
        "theme" to ResponsiveDimension.Theme,
        "density" to ResponsiveDimension.Density,
        "locale" to ResponsiveDimension.Locale,
        "direction" to ResponsiveDimension.Direction,
        "brand" to ResponsiveDimension.Brand,
        "state" to ResponsiveDimension.State,
    )

    val variableType: Map<String, VariableType> = mapOf(
        "color" to VariableType.Color,
        "number" to VariableType.Number,
        "text" to VariableType.Text,
        "string" to VariableType.Text,
        "bool" to VariableType.Bool,
        "boolean" to VariableType.Bool,
    )

    val componentPropertyType: Map<String, ComponentPropertyType> = mapOf(
        "text" to ComponentPropertyType.Text,
        "boolean" to ComponentPropertyType.Boolean,
        "instanceSwap" to ComponentPropertyType.InstanceSwap,
        "variant" to ComponentPropertyType.Variant,
        "slot" to ComponentPropertyType.Slot,
        "number" to ComponentPropertyType.Number,
        "string" to ComponentPropertyType.RawString,
        "dataBinding" to ComponentPropertyType.DataBinding,
    )

    val exportFormat: Map<String, ExportFormat> = mapOf(
        "png" to ExportFormat.Png,
        "jpg" to ExportFormat.Jpg,
        "jpeg" to ExportFormat.Jpg,
        "svg" to ExportFormat.Svg,
        "pdf" to ExportFormat.Pdf,
    )

    val measureAxis: Map<String, MeasureAxis> = mapOf(
        "inline" to MeasureAxis.Inline,
        "block" to MeasureAxis.Block,
    )
}
