package io.aequicor.visualization.editor.ui.strings

import io.aequicor.visualization.editor.presentation.CompactLabel
import io.aequicor.visualization.editor.presentation.EditorLayoutMode
import io.aequicor.visualization.editor.presentation.EffectType
import io.aequicor.visualization.editor.presentation.FillKind
import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.JustifyContent
import io.aequicor.visualization.engine.ir.model.LeadingTrim
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.TextAutoResize
import io.aequicor.visualization.engine.ir.model.TextDecorationStyle
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import io.aequicor.visualization.subsystems.annotations.AnnotationStatus
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowheadKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramCornerStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabelPosition
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStrokePattern
import io.aequicor.visualization.subsystems.diagrams.model.LineJumpStyle
import io.aequicor.visualization.subsystems.diagrams.model.UmlMessageKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlVisibility
import io.aequicor.visualization.subsystems.figures.BooleanOperationKind
import io.aequicor.visualization.subsystems.figures.HandleMirror
import io.aequicor.visualization.subsystems.figures.ShapeType

/**
 * Inspector-pane copy: the selection header, every Design-tab section (position / layout /
 * appearance / fill / stroke / shape / effects / typography), the Comments tab and the
 * draw.io-style Diagram editor. Round-trip selects resolve both the option list and the
 * matcher through the same resolver here so a picked value still maps back in Russian.
 */
interface InspectorStrings {
    // --- Empty state / selection header ---------------------------------------
    val nothingSelected: String
    fun selectedCount(count: Int): String
    val locked: String
    val duplicateSelection: String
    val deleteSelection: String

    // --- Position / rotation / constraints ------------------------------------
    val alignment: String
    val position: String
    val autoLayoutChildNote: String
    val absolutePositionInsideFrame: String
    val constraints: String
    val rotation: String
    fun layersSelectedNote(count: Int): String
    val rotate90: String
    val flipHorizontal: String
    val flipVertical: String
    val alignLeft: String
    val alignRight: String
    val alignTop: String
    val alignBottom: String
    val alignHorizontalCenter: String
    val alignVerticalCenter: String

    // --- Layout ----------------------------------------------------------------
    val dimensions: String
    val lockAspectRatio: String
    val resizing: String
    val autoLayout: String
    val convertToAutoLayout: String
    val convertToFrame: String
    val gap: String
    val padding: String
    val align: String
    val distribute: String
    val clipContent: String
    val componentInstanceNote: String
    val detachInstance: String

    // --- Appearance ------------------------------------------------------------
    val opacity: String
    val blend: String
    val radius: String

    // --- Fill ------------------------------------------------------------------
    val fills: String
    val noFills: String
    val angle: String
    val addStop: String
    val reverseGradient: String

    // --- Stroke ----------------------------------------------------------------
    val stroke: String
    val noStroke: String
    val dashed: String
    val ends: String
    val join: String

    // --- Shape / vector --------------------------------------------------------
    val shapeSection: CompactLabel
    val flatten: String
    val outlineStroke: String
    val type: String
    val sides: String
    val inner: String
    val point: String
    val mirror: String
    val sharpCorner: String
    val start: String
    val sweep: String
    val ratio: String
    val icon: String
    val iconPlaceholder: String
    val path: String
    val pathPlaceholder: String
    val viewBox: String
    val fillRuleLabel: String
    val fillRuleNonzero: String
    val fillRuleEvenOdd: String
    val regionFills: String
    fun regionN(index: Int): String
    val regionFill: String
    val noFill: String
    val noEditableGeometry: String
    val convertToEditable: String
    val paintBucketOn: String
    val paintBucketOff: String
    val clickRegionToFill: String
    val operation: String

    // --- Effects ---------------------------------------------------------------
    val effects: String
    val noEffects: String
    val blur: String
    val effectUnknown: String

    // --- Typography ------------------------------------------------------------
    val textStyle: String
    val applyStyle: String
    val italic: String
    val justify: String
    val alignCenter: String
    val alignMiddle: String
    val typeSettings: String
    val chooseFont: String
    val noFontsAvailable: String
    val sizePresets: String
    val autoLineHeight: String
    val resize: String
    val truncate: String
    val lines: String
    val decor: String
    val noDecoration: String
    val underline: String
    val strikethrough: String
    val line: String
    val skipInk: String
    val case: String
    val baseline: String
    val superscript: String
    val subscript: String
    val list: String
    val noList: String
    val bulleted: String
    val numbered: String
    val indent: String
    val paraSpace: String
    val paraIndent: String
    val trimLabel: String
    val hangingPunctuation: String
    val openType: String
    val variableAxes: String
    val axisWeight: String
    val axisOptical: String
    val axisWidth: String
    val axisSlant: String

    // --- Comments / annotations ------------------------------------------------
    val noAnnotationsYet: String
    val selectAnnotationHint: String
    val annotationsSection: CompactLabel
    val annotationSection: CompactLabel
    val noAnnotationsOnScreen: String
    val noText: String
    val kind: String
    val text: String
    val image: String
    val anchor: String
    val references: String
    val deleteAnnotation: String
    val annotationTextPlaceholder: String
    val detachImage: String
    val attachImagePlaceholder: String
    val imageShownNote: String
    val annotationImage: String
    val imageNotPreviewable: String
    fun pinnedNodeMissing(nodeId: String): String
    fun pinnedTo(name: String): String
    val detachAnchor: String
    fun freePointAt(x: String, y: String): String
    val noExtraReferences: String
    fun deletedNode(nodeId: String): String
    val addSelectedNode: String
    val status: String
    val createUnifiedIssuesPrompt: String

    // --- Diagram ---------------------------------------------------------------
    val diagramSection: CompactLabel
    val editDiagram: String
    val selectNodeOrEdge: String
    val diagramNameFallback: String
    val shapes: String
    val map: String
    val outline: String
    val layers: String
    val selectElementHint: String
    val diagramEmpty: String
    val diagramNotOnPage: String
    val freeEndpoint: String
    val label: String
    val style: String
    val fill: String
    val width: String
    val pattern: String
    val corners: String
    val sketch: String
    val shadow: String
    fun tableSize(rows: Int, columns: Int): String
    val addRow: String
    val addColumn: String
    val removeRow: String
    val removeColumn: String
    val mergeCells: String
    val merge: String
    val split: String
    val attributes: String
    val operations: String
    val noneYet: String
    val addMember: String
    val relation: String
    val routing: String
    val startHead: String
    val endHead: String
    val lineJumps: String
    val labels: String
    val reverseDirection: String
    val diagramActions: String
    val autoLayoutAction: String
    val insertTemplate: String
    val importText: String
    val importMermaid: String
    val importPlantUml: String

    // --- Round-trip / display resolvers for enums ------------------------------
    fun fillKind(kind: FillKind): String
    fun effectType(type: EffectType): String
    fun layoutMode(mode: EditorLayoutMode): String
    fun sizingMode(mode: SizingMode): String
    fun strokeAlign(align: StrokeAlign): String
    fun horizontalConstraint(constraint: HorizontalConstraint): String
    fun verticalConstraint(constraint: VerticalConstraint): String
    fun alignItems(align: AlignItems): String
    fun justifyContent(justify: JustifyContent): String
    fun autoResize(resize: TextAutoResize): String
    fun decorationStyle(style: TextDecorationStyle): String
    fun leadingTrim(trim: LeadingTrim): String
    fun shapeType(shape: ShapeType): String
    fun handleMirror(mirror: HandleMirror): String
    fun booleanOp(operation: BooleanOperationKind): String
    fun annotationKind(kind: AnnotationKind): String
    fun annotationStatus(status: AnnotationStatus): String
    fun diagramRelation(relation: DiagramRelation): String
    fun diagramRouting(style: DiagramRoutingStyle): String
    fun diagramPattern(pattern: DiagramStrokePattern): String
    fun diagramCorner(style: DiagramCornerStyle): String
    fun diagramLineJump(style: LineJumpStyle): String
    fun diagramArrowhead(kind: DiagramArrowheadKind): String
    fun umlVisibility(visibility: UmlVisibility): String
    fun diagramEdgeLabelPositionTitle(position: DiagramEdgeLabelPosition): String
    fun diagramFamily(family: String): String
}

object InspectorStringsEn : InspectorStrings {
    override val nothingSelected = "Nothing selected — select an object on the canvas or in Layers."
    override fun selectedCount(count: Int) = "$count selected"
    override val locked = "Locked"
    override val duplicateSelection = "Duplicate selection"
    override val deleteSelection = "Delete selection"

    override val alignment = "Alignment"
    override val position = "Position"
    override val autoLayoutChildNote = "Auto layout child — position follows the flow. Drag on canvas to reorder."
    override val absolutePositionInsideFrame = "Absolute position inside frame"
    override val constraints = "Constraints"
    override val rotation = "Rotation"
    override fun layersSelectedNote(count: Int) = "$count layers selected; locked layers are skipped."
    override val rotate90 = "Rotate 90 degrees"
    override val flipHorizontal = "Flip horizontal"
    override val flipVertical = "Flip vertical"
    override val alignLeft = "Align left"
    override val alignRight = "Align right"
    override val alignTop = "Align top"
    override val alignBottom = "Align bottom"
    override val alignHorizontalCenter = "Align horizontal center"
    override val alignVerticalCenter = "Align vertical center"

    override val dimensions = "Dimensions"
    override val lockAspectRatio = "Lock aspect ratio"
    override val resizing = "Resizing"
    override val autoLayout = "Auto layout"
    override val convertToAutoLayout = "Convert to Auto Layout"
    override val convertToFrame = "Convert to Frame"
    override val gap = "Gap"
    override val padding = "Padding"
    override val align = "Align"
    override val distribute = "Distribute"
    override val clipContent = "Clip content"
    override val componentInstanceNote = "Component instance — detach to edit its layout."
    override val detachInstance = "Detach instance"

    override val opacity = "Opacity"
    override val blend = "Blend"
    override val radius = "Radius"

    override val fills = "Fills"
    override val noFills = "No fills. Add one with +."
    override val angle = "Angle"
    override val addStop = "+ stop"
    override val reverseGradient = "reverse"

    override val stroke = "Stroke"
    override val noStroke = "No stroke. Add one with +."
    override val dashed = "Dashed"
    override val ends = "Ends"
    override val join = "Join"

    override val shapeSection = CompactLabel("Shape", "Shape", "Shp")
    override val flatten = "Flatten"
    override val outlineStroke = "Outline stroke"
    override val type = "Type"
    override val sides = "Sides"
    override val inner = "Inner"
    override val point = "Point"
    override val mirror = "Mirror"
    override val sharpCorner = "Sharp corner"
    override val start = "Start"
    override val sweep = "Sweep"
    override val ratio = "Ratio"
    override val icon = "Icon"
    override val iconPlaceholder = "ds/Icon/…"
    override val path = "Path"
    override val pathPlaceholder = "asset id"
    override val viewBox = "View box"
    override val fillRuleLabel = "Fill rule"
    override val fillRuleNonzero = "Nonzero"
    override val fillRuleEvenOdd = "Even-odd"
    override val regionFills = "Region fills"
    override fun regionN(index: Int) = "Region $index"
    override val regionFill = "Region fill"
    override val noFill = "No fill. Add one with +."
    override val noEditableGeometry = "No editable geometry yet — convert to edit points on the canvas."
    override val convertToEditable = "Convert to editable"
    override val paintBucketOn = "Paint bucket: on"
    override val paintBucketOff = "Paint bucket: off"
    override val clickRegionToFill = "Click a region on the canvas to fill it."
    override val operation = "Operation"

    override val effects = "Effects"
    override val noEffects = "No effects. Add drop shadow with +."
    override val blur = "Blur"
    override val effectUnknown = "Effect"

    override val textStyle = "Text style"
    override val applyStyle = "Apply style"
    override val italic = "Italic"
    override val justify = "Justify"
    override val alignCenter = "Align center"
    override val alignMiddle = "Align middle"
    override val typeSettings = "Type settings"
    override val chooseFont = "Choose font"
    override val noFontsAvailable = "No fonts available"
    override val sizePresets = "Size presets"
    override val autoLineHeight = "Auto line height"
    override val resize = "Resize"
    override val truncate = "Truncate"
    override val lines = "Lines"
    override val decor = "Decor"
    override val noDecoration = "No decoration"
    override val underline = "Underline"
    override val strikethrough = "Strikethrough"
    override val line = "Line"
    override val skipInk = "Skip ink"
    override val case = "Case"
    override val baseline = "Baseline"
    override val superscript = "Superscript"
    override val subscript = "Subscript"
    override val list = "List"
    override val noList = "No list"
    override val bulleted = "Bulleted"
    override val numbered = "Numbered"
    override val indent = "Indent"
    override val paraSpace = "Para space"
    override val paraIndent = "Para indent"
    override val trimLabel = "Trim"
    override val hangingPunctuation = "Hanging punctuation"
    override val openType = "OpenType"
    override val variableAxes = "Variable axes"
    override val axisWeight = "Weight"
    override val axisOptical = "Optical"
    override val axisWidth = "Width"
    override val axisSlant = "Slant"

    override val noAnnotationsYet = "No annotations yet — drop a note or issue with the comment tool."
    override val selectAnnotationHint = "Select an annotation badge on the canvas or in the list to edit it."
    override val annotationsSection = CompactLabel("Annotations", "Annots", "Ann")
    override val annotationSection = CompactLabel("Annotation", "Annot", "Ann")
    override val noAnnotationsOnScreen = "No annotations on this screen. Use the comment tool on the canvas."
    override val noText = "(no text)"
    override val kind = "Kind"
    override val text = "Text"
    override val image = "Image"
    override val anchor = "Anchor"
    override val references = "References"
    override val deleteAnnotation = "Delete annotation"
    override val annotationTextPlaceholder = "Annotation text…"
    override val detachImage = "Detach image"
    override val attachImagePlaceholder = "Paste a data:image/… URI to attach"
    override val imageShownNote = "Shown inside the expanded annotation card."
    override val annotationImage = "Annotation image"
    override val imageNotPreviewable = "Attached image is not previewable (not a base64 data URI)."
    override fun pinnedNodeMissing(nodeId: String) = "Pinned node is missing: $nodeId"
    override fun pinnedTo(name: String) = "Pinned to $name"
    override val detachAnchor = "Detach"
    override fun freePointAt(x: String, y: String) = "Free point at $x, $y"
    override val noExtraReferences = "No extra node references."
    override fun deletedNode(nodeId: String) = "$nodeId (deleted)"
    override val addSelectedNode = "Add selected node"
    override val status = "Status"
    override val createUnifiedIssuesPrompt = "Create one AI-agent prompt from issues"

    override val diagramSection = CompactLabel("Diagram", "Diagram", "Dgm")
    override val editDiagram = "Edit diagram"
    override val selectNodeOrEdge = "Select a node or an edge on the canvas."
    override val diagramNameFallback = "Diagram"
    override val shapes = "Shapes"
    override val map = "Map"
    override val outline = "Outline"
    override val layers = "Layers"
    override val selectElementHint = "Select an element in the outline or on the canvas."
    override val diagramEmpty = "The diagram is empty — add a shape from the palette above."
    override val diagramNotOnPage = "The diagram is not on the current page."
    override val freeEndpoint = "(free)"
    override val label = "Label"
    override val style = "Style"
    override val fill = "Fill"
    override val width = "Width"
    override val pattern = "Pattern"
    override val corners = "Corners"
    override val sketch = "Sketch"
    override val shadow = "Shadow"
    override fun tableSize(rows: Int, columns: Int) = "Table ($rows x $columns)"
    override val addRow = "+ Row"
    override val addColumn = "+ Column"
    override val removeRow = "- Row"
    override val removeColumn = "- Column"
    override val mergeCells = "Merge cells"
    override val merge = "Merge"
    override val split = "Split"
    override val attributes = "Attributes"
    override val operations = "Operations"
    override val noneYet = "None yet."
    override val addMember = "+ Add"
    override val relation = "Relation"
    override val routing = "Routing"
    override val startHead = "Start head"
    override val endHead = "End head"
    override val lineJumps = "Line jumps"
    override val labels = "Labels"
    override val reverseDirection = "Reverse direction"
    override val diagramActions = "Diagram"
    override val autoLayoutAction = "Auto-layout"
    override val insertTemplate = "Insert template"
    override val importText = "Import text"
    override val importMermaid = "Import Mermaid"
    override val importPlantUml = "Import PlantUML"

    override fun fillKind(kind: FillKind) = when (kind) {
        FillKind.Solid -> "Solid"
        FillKind.LinearGradient -> "Linear"
        FillKind.RadialGradient -> "Radial"
        FillKind.Image -> "Image"
    }

    override fun effectType(type: EffectType) = when (type) {
        EffectType.DropShadow -> "Drop shadow"
        EffectType.InnerShadow -> "Inner shadow"
        EffectType.LayerBlur -> "Layer blur"
        EffectType.BackgroundBlur -> "Background blur"
    }

    override fun layoutMode(mode: EditorLayoutMode) = when (mode) {
        EditorLayoutMode.Free -> "Free"
        EditorLayoutMode.Vertical -> "Vert"
        EditorLayoutMode.Horizontal -> "Hori"
        EditorLayoutMode.Grid -> "Grid"
        EditorLayoutMode.Stack -> "Stack"
    }

    override fun sizingMode(mode: SizingMode) = when (mode) {
        SizingMode.Fixed -> "Fixed"
        SizingMode.Hug -> "Hug"
        SizingMode.Fill -> "Fill"
    }

    override fun strokeAlign(align: StrokeAlign) = when (align) {
        StrokeAlign.Inside -> "Inside"
        StrokeAlign.Center -> "Center"
        StrokeAlign.Outside -> "Outside"
    }

    override fun horizontalConstraint(constraint: HorizontalConstraint) = when (constraint) {
        HorizontalConstraint.Left -> "Left"
        HorizontalConstraint.Right -> "Right"
        HorizontalConstraint.Center -> "Center"
        HorizontalConstraint.LeftRight -> "Left & Right"
        HorizontalConstraint.Scale -> "Scale"
    }

    override fun verticalConstraint(constraint: VerticalConstraint) = when (constraint) {
        VerticalConstraint.Top -> "Top"
        VerticalConstraint.Bottom -> "Bottom"
        VerticalConstraint.Center -> "Center"
        VerticalConstraint.TopBottom -> "Top & Bottom"
        VerticalConstraint.Scale -> "Scale"
    }

    override fun alignItems(align: AlignItems) = when (align) {
        AlignItems.Start -> "Start"
        AlignItems.Center -> "Center"
        AlignItems.End -> "End"
        AlignItems.Baseline -> "Base"
        AlignItems.Stretch -> "Fill"
    }

    override fun justifyContent(justify: JustifyContent) = when (justify) {
        JustifyContent.Start -> "Start"
        JustifyContent.Center -> "Center"
        JustifyContent.End -> "End"
        JustifyContent.SpaceBetween -> "Between"
    }

    override fun autoResize(resize: TextAutoResize) = when (resize) {
        TextAutoResize.WidthAndHeight -> "Auto W"
        TextAutoResize.Height -> "Auto H"
        TextAutoResize.None -> "Fixed"
    }

    override fun decorationStyle(style: TextDecorationStyle) = when (style) {
        TextDecorationStyle.Solid -> "Solid"
        TextDecorationStyle.Dashed -> "Dash"
        TextDecorationStyle.Dotted -> "Dot"
        TextDecorationStyle.Wavy -> "Wave"
    }

    override fun leadingTrim(trim: LeadingTrim) = when (trim) {
        LeadingTrim.None -> "None"
        LeadingTrim.CapHeight -> "Cap"
    }

    override fun shapeType(shape: ShapeType) = when (shape) {
        ShapeType.Rectangle -> "Rectangle"
        ShapeType.Ellipse -> "Ellipse"
        ShapeType.Polygon -> "Polygon"
        ShapeType.Star -> "Star"
        ShapeType.Line -> "Line"
        ShapeType.Arrow -> "Arrow"
        ShapeType.Vector -> "Vector"
    }

    override fun handleMirror(mirror: HandleMirror) = when (mirror) {
        HandleMirror.None -> "No mirror"
        HandleMirror.Angle -> "Mirror angle"
        HandleMirror.AngleAndLength -> "Mirror angle & length"
    }

    override fun booleanOp(operation: BooleanOperationKind) = when (operation) {
        BooleanOperationKind.Union -> "Union"
        BooleanOperationKind.Subtract -> "Subtract"
        BooleanOperationKind.Intersect -> "Intersect"
        BooleanOperationKind.Exclude -> "Exclude"
    }

    override fun annotationKind(kind: AnnotationKind) = when (kind) {
        AnnotationKind.Note -> "Comment"
        AnnotationKind.Issue -> "Issue"
    }

    override fun annotationStatus(status: AnnotationStatus) = when (status) {
        AnnotationStatus.Open -> "Open"
        AnnotationStatus.InReview -> "In review"
        AnnotationStatus.Closed -> "Closed"
    }

    override fun diagramRelation(relation: DiagramRelation) = when (relation) {
        DiagramRelation.Plain -> "Plain"
        is DiagramRelation.Association -> if (relation.directed) "Directed association" else "Association"
        DiagramRelation.Aggregation -> "Aggregation"
        DiagramRelation.Composition -> "Composition"
        DiagramRelation.Generalization -> "Generalization"
        DiagramRelation.Dependency -> "Dependency"
        DiagramRelation.Realization -> "Realization"
        is DiagramRelation.Message -> when (relation.kind) {
            UmlMessageKind.SYNC -> "Message (sync)"
            UmlMessageKind.ASYNC -> "Message (async)"
            UmlMessageKind.RETURN -> "Message (return)"
            UmlMessageKind.CREATE -> "Message (create)"
            UmlMessageKind.DESTROY -> "Message (destroy)"
        }
        DiagramRelation.Transition -> "Transition"
        DiagramRelation.Include -> "Include"
        DiagramRelation.Extend -> "Extend"
        is DiagramRelation.EntityRelation -> "Entity relation"
    }

    override fun diagramRouting(style: DiagramRoutingStyle) = when (style) {
        DiagramRoutingStyle.STRAIGHT -> "Straight"
        DiagramRoutingStyle.ORTHOGONAL -> "Orthogonal"
        DiagramRoutingStyle.SIMPLE -> "Simple"
        DiagramRoutingStyle.ISOMETRIC -> "Isometric"
        DiagramRoutingStyle.CURVED -> "Curved"
        DiagramRoutingStyle.ENTITY_RELATION -> "Entity relation"
    }

    override fun diagramPattern(pattern: DiagramStrokePattern) = when (pattern) {
        DiagramStrokePattern.SOLID -> "Solid"
        DiagramStrokePattern.DASHED -> "Dashed"
        DiagramStrokePattern.DOTTED -> "Dotted"
    }

    override fun diagramCorner(style: DiagramCornerStyle) = when (style) {
        DiagramCornerStyle.SHARP -> "Sharp"
        DiagramCornerStyle.ROUNDED -> "Rounded"
        DiagramCornerStyle.CURVED -> "Curved"
    }

    override fun diagramLineJump(style: LineJumpStyle) = when (style) {
        LineJumpStyle.NONE -> "None"
        LineJumpStyle.ARC -> "Arc"
        LineJumpStyle.GAP -> "Gap"
        LineJumpStyle.SHARP -> "Sharp"
    }

    override fun diagramArrowhead(kind: DiagramArrowheadKind) = when (kind) {
        DiagramArrowheadKind.NONE -> "None"
        DiagramArrowheadKind.OPEN -> "Open"
        DiagramArrowheadKind.BLOCK -> "Block"
        DiagramArrowheadKind.BLOCK_FILLED -> "Block filled"
        DiagramArrowheadKind.DIAMOND -> "Diamond"
        DiagramArrowheadKind.DIAMOND_FILLED -> "Diamond filled"
        DiagramArrowheadKind.TRIANGLE -> "Triangle"
        DiagramArrowheadKind.TRIANGLE_FILLED -> "Triangle filled"
        DiagramArrowheadKind.OVAL -> "Oval"
        DiagramArrowheadKind.OVAL_FILLED -> "Oval filled"
        DiagramArrowheadKind.CROSS -> "Cross"
        DiagramArrowheadKind.DASH -> "Dash"
        DiagramArrowheadKind.ER_ONE -> "ER one"
        DiagramArrowheadKind.ER_MANY -> "ER many"
        DiagramArrowheadKind.ER_ONE_OR_MANY -> "ER one or many"
        DiagramArrowheadKind.ER_ZERO_OR_ONE -> "ER zero or one"
        DiagramArrowheadKind.ER_ZERO_OR_MANY -> "ER zero or many"
    }

    override fun umlVisibility(visibility: UmlVisibility) = when (visibility) {
        UmlVisibility.PUBLIC -> "Public"
        UmlVisibility.PRIVATE -> "Private"
        UmlVisibility.PROTECTED -> "Protected"
        UmlVisibility.PACKAGE -> "Package"
    }

    override fun diagramEdgeLabelPositionTitle(position: DiagramEdgeLabelPosition) = when (position) {
        DiagramEdgeLabelPosition.SOURCE -> "Start"
        DiagramEdgeLabelPosition.MIDDLE -> "Middle"
        DiagramEdgeLabelPosition.TARGET -> "End"
    }

    override fun diagramFamily(family: String) = family
}

object InspectorStringsRu : InspectorStrings {
    override val nothingSelected = "Ничего не выбрано — выберите объект на холсте или в слоях."
    override fun selectedCount(count: Int) = "Выбрано: $count"
    override val locked = "Заблокировано"
    override val duplicateSelection = "Дублировать выделение"
    override val deleteSelection = "Удалить выделение"

    override val alignment = "Выравнивание"
    override val position = "Позиция"
    override val autoLayoutChildNote = "Дочерний элемент авто-раскладки — позиция следует потоку. Перетащите на холсте, чтобы изменить порядок."
    override val absolutePositionInsideFrame = "Абсолютная позиция внутри фрейма"
    override val constraints = "Привязки"
    override val rotation = "Поворот"
    override fun layersSelectedNote(count: Int) = "Выбрано слоёв: $count; заблокированные слои пропускаются."
    override val rotate90 = "Повернуть на 90°"
    override val flipHorizontal = "Отразить по горизонтали"
    override val flipVertical = "Отразить по вертикали"
    override val alignLeft = "По левому краю"
    override val alignRight = "По правому краю"
    override val alignTop = "По верху"
    override val alignBottom = "По низу"
    override val alignHorizontalCenter = "Центр по горизонтали"
    override val alignVerticalCenter = "Центр по вертикали"

    override val dimensions = "Размеры"
    override val lockAspectRatio = "Сохранять пропорции"
    override val resizing = "Изменение размера"
    override val autoLayout = "Авто-раскладка"
    override val convertToAutoLayout = "Преобразовать в Auto Layout"
    override val convertToFrame = "Преобразовать во фрейм"
    override val gap = "Зазор"
    override val padding = "Внутренние отступы"
    override val align = "Выравнивание"
    override val distribute = "Распределить"
    override val clipContent = "Обрезать содержимое"
    override val componentInstanceNote = "Экземпляр компонента — отсоедините, чтобы редактировать раскладку."
    override val detachInstance = "Отсоединить экземпляр"

    override val opacity = "Непрозрачность"
    override val blend = "Смешивание"
    override val radius = "Радиус"

    override val fills = "Заливки"
    override val noFills = "Нет заливок. Добавьте через +."
    override val angle = "Угол"
    override val addStop = "+ точка"
    override val reverseGradient = "обратить"

    override val stroke = "Обводка"
    override val noStroke = "Нет обводки. Добавьте через +."
    override val dashed = "Пунктир"
    override val ends = "Концы"
    override val join = "Соединение"

    override val shapeSection = CompactLabel("Фигура", "Фигура", "Фиг")
    override val flatten = "Свести"
    override val outlineStroke = "Контур обводки"
    override val type = "Тип"
    override val sides = "Стороны"
    override val inner = "Внутренний"
    override val point = "Вершина"
    override val mirror = "Зеркало"
    override val sharpCorner = "Острый угол"
    override val start = "Начало"
    override val sweep = "Развёртка"
    override val ratio = "Отношение"
    override val icon = "Иконка"
    override val iconPlaceholder = "ds/Icon/…"
    override val path = "Контур"
    override val pathPlaceholder = "id ресурса"
    override val viewBox = "Область просмотра"
    override val fillRuleLabel = "Правило заливки"
    override val fillRuleNonzero = "Ненулевой"
    override val fillRuleEvenOdd = "Чётный-нечётный"
    override val regionFills = "Заливки областей"
    override fun regionN(index: Int) = "Область $index"
    override val regionFill = "Заливка области"
    override val noFill = "Нет заливки. Добавьте через +."
    override val noEditableGeometry = "Пока нет редактируемой геометрии — сделайте редактируемым, чтобы править точки на холсте."
    override val convertToEditable = "Сделать редактируемым"
    override val paintBucketOn = "Заливка: вкл"
    override val paintBucketOff = "Заливка: выкл"
    override val clickRegionToFill = "Кликните область на холсте, чтобы залить её."
    override val operation = "Операция"

    override val effects = "Эффекты"
    override val noEffects = "Нет эффектов. Добавьте тень через +."
    override val blur = "Размытие"
    override val effectUnknown = "Эффект"

    override val textStyle = "Стиль текста"
    override val applyStyle = "Применить стиль"
    override val italic = "Курсив"
    override val justify = "По ширине"
    override val alignCenter = "По центру (гориз.)"
    override val alignMiddle = "По центру (верт.)"
    override val typeSettings = "Настройки текста"
    override val chooseFont = "Выбрать шрифт"
    override val noFontsAvailable = "Шрифты недоступны"
    override val sizePresets = "Пресеты размера"
    override val autoLineHeight = "Авто высота строки"
    override val resize = "Изменение размера"
    override val truncate = "Обрезать"
    override val lines = "Строки"
    override val decor = "Оформление"
    override val noDecoration = "Без оформления"
    override val underline = "Подчёркивание"
    override val strikethrough = "Зачёркивание"
    override val line = "Линия"
    override val skipInk = "Пропуск штрихов"
    override val case = "Регистр"
    override val baseline = "Базовая линия"
    override val superscript = "Надстрочный"
    override val subscript = "Подстрочный"
    override val list = "Список"
    override val noList = "Без списка"
    override val bulleted = "Маркированный"
    override val numbered = "Нумерованный"
    override val indent = "Отступ"
    override val paraSpace = "Интервал абзаца"
    override val paraIndent = "Абзацный отступ"
    override val trimLabel = "Обрезка"
    override val hangingPunctuation = "Висячая пунктуация"
    override val openType = "OpenType"
    override val variableAxes = "Вариативные оси"
    override val axisWeight = "Насыщенность"
    override val axisOptical = "Оптический размер"
    override val axisWidth = "Ширина"
    override val axisSlant = "Наклон"

    override val noAnnotationsYet = "Пока нет аннотаций — добавьте заметку или замечание инструментом комментариев."
    override val selectAnnotationHint = "Выберите значок аннотации на холсте или в списке, чтобы отредактировать её."
    override val annotationsSection = CompactLabel("Аннотации", "Аннот", "Анн")
    override val annotationSection = CompactLabel("Аннотация", "Аннот", "Анн")
    override val noAnnotationsOnScreen = "На этом экране нет аннотаций. Используйте инструмент комментариев на холсте."
    override val noText = "(нет текста)"
    override val kind = "Тип"
    override val text = "Текст"
    override val image = "Изображение"
    override val anchor = "Якорь"
    override val references = "Ссылки"
    override val deleteAnnotation = "Удалить аннотацию"
    override val annotationTextPlaceholder = "Текст аннотации…"
    override val detachImage = "Отсоединить изображение"
    override val attachImagePlaceholder = "Вставьте data:image/… URI, чтобы прикрепить"
    override val imageShownNote = "Показывается в развёрнутой карточке аннотации."
    override val annotationImage = "Изображение аннотации"
    override val imageNotPreviewable = "Прикреплённое изображение нельзя показать (не base64 data URI)."
    override fun pinnedNodeMissing(nodeId: String) = "Закреплённый узел отсутствует: $nodeId"
    override fun pinnedTo(name: String) = "Закреплено к $name"
    override val detachAnchor = "Открепить"
    override fun freePointAt(x: String, y: String) = "Свободная точка в $x, $y"
    override val noExtraReferences = "Нет дополнительных ссылок на узлы."
    override fun deletedNode(nodeId: String) = "$nodeId (удалён)"
    override val addSelectedNode = "Добавить выбранный узел"
    override val status = "Статус"
    override val createUnifiedIssuesPrompt = "Создать единый промпт для ИИ-агента из замечаний"

    override val diagramSection = CompactLabel("Диаграмма", "Диагр", "Дгм")
    override val editDiagram = "Редактировать диаграмму"
    override val selectNodeOrEdge = "Выберите узел или связь на холсте."
    override val diagramNameFallback = "Диаграмма"
    override val shapes = "Фигуры"
    override val map = "Карта"
    override val outline = "Структура"
    override val layers = "Слои"
    override val selectElementHint = "Выберите элемент в структуре или на холсте."
    override val diagramEmpty = "Диаграмма пуста — добавьте фигуру из палитры выше."
    override val diagramNotOnPage = "Диаграмма не на текущей странице."
    override val freeEndpoint = "(свободный)"
    override val label = "Подпись"
    override val style = "Стиль"
    override val fill = "Заливка"
    override val width = "Ширина"
    override val pattern = "Узор"
    override val corners = "Углы"
    override val sketch = "Набросок"
    override val shadow = "Тень"
    override fun tableSize(rows: Int, columns: Int) = "Таблица ($rows x $columns)"
    override val addRow = "+ Строка"
    override val addColumn = "+ Столбец"
    override val removeRow = "- Строка"
    override val removeColumn = "- Столбец"
    override val mergeCells = "Объединить ячейки"
    override val merge = "Объединить"
    override val split = "Разделить"
    override val attributes = "Атрибуты"
    override val operations = "Операции"
    override val noneYet = "Пока нет."
    override val addMember = "+ Добавить"
    override val relation = "Связь"
    override val routing = "Маршрутизация"
    override val startHead = "Начальный маркер"
    override val endHead = "Конечный маркер"
    override val lineJumps = "Перекрёстки линий"
    override val labels = "Подписи"
    override val reverseDirection = "Обратить направление"
    override val diagramActions = "Диаграмма"
    override val autoLayoutAction = "Авто-раскладка"
    override val insertTemplate = "Вставить шаблон"
    override val importText = "Импорт текста"
    override val importMermaid = "Импорт Mermaid"
    override val importPlantUml = "Импорт PlantUML"

    override fun fillKind(kind: FillKind) = when (kind) {
        FillKind.Solid -> "Сплошной"
        FillKind.LinearGradient -> "Линейный"
        FillKind.RadialGradient -> "Радиальный"
        FillKind.Image -> "Изображение"
    }

    override fun effectType(type: EffectType) = when (type) {
        EffectType.DropShadow -> "Тень"
        EffectType.InnerShadow -> "Внутренняя тень"
        EffectType.LayerBlur -> "Размытие слоя"
        EffectType.BackgroundBlur -> "Размытие фона"
    }

    override fun layoutMode(mode: EditorLayoutMode) = when (mode) {
        EditorLayoutMode.Free -> "Своб"
        EditorLayoutMode.Vertical -> "Верт"
        EditorLayoutMode.Horizontal -> "Гор"
        EditorLayoutMode.Grid -> "Сетка"
        EditorLayoutMode.Stack -> "Стек"
    }

    override fun sizingMode(mode: SizingMode) = when (mode) {
        SizingMode.Fixed -> "Фикс."
        SizingMode.Hug -> "По содерж."
        SizingMode.Fill -> "Заполнить"
    }

    override fun strokeAlign(align: StrokeAlign) = when (align) {
        StrokeAlign.Inside -> "Внутри"
        StrokeAlign.Center -> "По центру"
        StrokeAlign.Outside -> "Снаружи"
    }

    override fun horizontalConstraint(constraint: HorizontalConstraint) = when (constraint) {
        HorizontalConstraint.Left -> "Слева"
        HorizontalConstraint.Right -> "Справа"
        HorizontalConstraint.Center -> "По центру"
        HorizontalConstraint.LeftRight -> "Слева и справа"
        HorizontalConstraint.Scale -> "Масштаб"
    }

    override fun verticalConstraint(constraint: VerticalConstraint) = when (constraint) {
        VerticalConstraint.Top -> "Сверху"
        VerticalConstraint.Bottom -> "Снизу"
        VerticalConstraint.Center -> "По центру"
        VerticalConstraint.TopBottom -> "Сверху и снизу"
        VerticalConstraint.Scale -> "Масштаб"
    }

    override fun alignItems(align: AlignItems) = when (align) {
        AlignItems.Start -> "Нач."
        AlignItems.Center -> "Центр"
        AlignItems.End -> "Кон."
        AlignItems.Baseline -> "Осн."
        AlignItems.Stretch -> "Раст."
    }

    override fun justifyContent(justify: JustifyContent) = when (justify) {
        JustifyContent.Start -> "Нач."
        JustifyContent.Center -> "Центр"
        JustifyContent.End -> "Кон."
        JustifyContent.SpaceBetween -> "Между"
    }

    override fun autoResize(resize: TextAutoResize) = when (resize) {
        TextAutoResize.WidthAndHeight -> "Авто Ш"
        TextAutoResize.Height -> "Авто В"
        TextAutoResize.None -> "Фикс."
    }

    override fun decorationStyle(style: TextDecorationStyle) = when (style) {
        TextDecorationStyle.Solid -> "Сплошн."
        TextDecorationStyle.Dashed -> "Штрих"
        TextDecorationStyle.Dotted -> "Точки"
        TextDecorationStyle.Wavy -> "Волна"
    }

    override fun leadingTrim(trim: LeadingTrim) = when (trim) {
        LeadingTrim.None -> "Нет"
        LeadingTrim.CapHeight -> "Cap"
    }

    override fun shapeType(shape: ShapeType) = when (shape) {
        ShapeType.Rectangle -> "Прямоугольник"
        ShapeType.Ellipse -> "Эллипс"
        ShapeType.Polygon -> "Многоугольник"
        ShapeType.Star -> "Звезда"
        ShapeType.Line -> "Линия"
        ShapeType.Arrow -> "Стрелка"
        ShapeType.Vector -> "Вектор"
    }

    override fun handleMirror(mirror: HandleMirror) = when (mirror) {
        HandleMirror.None -> "Без зеркала"
        HandleMirror.Angle -> "Зеркалить угол"
        HandleMirror.AngleAndLength -> "Зеркалить угол и длину"
    }

    override fun booleanOp(operation: BooleanOperationKind) = when (operation) {
        BooleanOperationKind.Union -> "Объединение"
        BooleanOperationKind.Subtract -> "Вычитание"
        BooleanOperationKind.Intersect -> "Пересечение"
        BooleanOperationKind.Exclude -> "Исключение"
    }

    override fun annotationKind(kind: AnnotationKind) = when (kind) {
        AnnotationKind.Note -> "Комментарий"
        AnnotationKind.Issue -> "Замечание"
    }

    override fun annotationStatus(status: AnnotationStatus) = when (status) {
        AnnotationStatus.Open -> "Открыто"
        AnnotationStatus.InReview -> "Проверяется"
        AnnotationStatus.Closed -> "Закрыто"
    }

    override fun diagramRelation(relation: DiagramRelation) = when (relation) {
        DiagramRelation.Plain -> "Обычная"
        is DiagramRelation.Association -> if (relation.directed) "Направленная ассоциация" else "Ассоциация"
        DiagramRelation.Aggregation -> "Агрегация"
        DiagramRelation.Composition -> "Композиция"
        DiagramRelation.Generalization -> "Обобщение"
        DiagramRelation.Dependency -> "Зависимость"
        DiagramRelation.Realization -> "Реализация"
        is DiagramRelation.Message -> when (relation.kind) {
            UmlMessageKind.SYNC -> "Сообщение (синхр.)"
            UmlMessageKind.ASYNC -> "Сообщение (асинхр.)"
            UmlMessageKind.RETURN -> "Сообщение (возврат)"
            UmlMessageKind.CREATE -> "Сообщение (создание)"
            UmlMessageKind.DESTROY -> "Сообщение (уничтожение)"
        }
        DiagramRelation.Transition -> "Переход"
        DiagramRelation.Include -> "Включение"
        DiagramRelation.Extend -> "Расширение"
        is DiagramRelation.EntityRelation -> "Сущность-связь"
    }

    override fun diagramRouting(style: DiagramRoutingStyle) = when (style) {
        DiagramRoutingStyle.STRAIGHT -> "Прямая"
        DiagramRoutingStyle.ORTHOGONAL -> "Ортогональная"
        DiagramRoutingStyle.SIMPLE -> "Простая"
        DiagramRoutingStyle.ISOMETRIC -> "Изометрия"
        DiagramRoutingStyle.CURVED -> "Скруглённая"
        DiagramRoutingStyle.ENTITY_RELATION -> "Сущность-связь"
    }

    override fun diagramPattern(pattern: DiagramStrokePattern) = when (pattern) {
        DiagramStrokePattern.SOLID -> "Сплошной"
        DiagramStrokePattern.DASHED -> "Пунктир"
        DiagramStrokePattern.DOTTED -> "Точечный"
    }

    override fun diagramCorner(style: DiagramCornerStyle) = when (style) {
        DiagramCornerStyle.SHARP -> "Острые"
        DiagramCornerStyle.ROUNDED -> "Округлые"
        DiagramCornerStyle.CURVED -> "Плавные"
    }

    override fun diagramLineJump(style: LineJumpStyle) = when (style) {
        LineJumpStyle.NONE -> "Нет"
        LineJumpStyle.ARC -> "Дуга"
        LineJumpStyle.GAP -> "Разрыв"
        LineJumpStyle.SHARP -> "Острый"
    }

    override fun diagramArrowhead(kind: DiagramArrowheadKind) = when (kind) {
        DiagramArrowheadKind.NONE -> "Нет"
        DiagramArrowheadKind.OPEN -> "Открытый"
        DiagramArrowheadKind.BLOCK -> "Блок"
        DiagramArrowheadKind.BLOCK_FILLED -> "Блок закрашенный"
        DiagramArrowheadKind.DIAMOND -> "Ромб"
        DiagramArrowheadKind.DIAMOND_FILLED -> "Ромб закрашенный"
        DiagramArrowheadKind.TRIANGLE -> "Треугольник"
        DiagramArrowheadKind.TRIANGLE_FILLED -> "Треугольник закрашенный"
        DiagramArrowheadKind.OVAL -> "Овал"
        DiagramArrowheadKind.OVAL_FILLED -> "Овал закрашенный"
        DiagramArrowheadKind.CROSS -> "Крест"
        DiagramArrowheadKind.DASH -> "Штрих"
        DiagramArrowheadKind.ER_ONE -> "ER один"
        DiagramArrowheadKind.ER_MANY -> "ER много"
        DiagramArrowheadKind.ER_ONE_OR_MANY -> "ER один или много"
        DiagramArrowheadKind.ER_ZERO_OR_ONE -> "ER ноль или один"
        DiagramArrowheadKind.ER_ZERO_OR_MANY -> "ER ноль или много"
    }

    override fun umlVisibility(visibility: UmlVisibility) = when (visibility) {
        UmlVisibility.PUBLIC -> "Публичный"
        UmlVisibility.PRIVATE -> "Приватный"
        UmlVisibility.PROTECTED -> "Защищённый"
        UmlVisibility.PACKAGE -> "Пакетный"
    }

    override fun diagramEdgeLabelPositionTitle(position: DiagramEdgeLabelPosition) = when (position) {
        DiagramEdgeLabelPosition.SOURCE -> "Начало"
        DiagramEdgeLabelPosition.MIDDLE -> "Середина"
        DiagramEdgeLabelPosition.TARGET -> "Конец"
    }

    override fun diagramFamily(family: String) = when (family) {
        "Flowchart" -> "Блок-схема"
        "Structure" -> "Структура"
        "Basic" -> "Базовые"
        else -> family
    }
}
