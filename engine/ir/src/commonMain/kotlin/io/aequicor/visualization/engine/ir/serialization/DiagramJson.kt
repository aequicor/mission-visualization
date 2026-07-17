package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.BpmnNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowheadKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramColor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramConnectionMode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramCornerStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabelPosition
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGroup
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGroupId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLayer
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLayerId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramOrientation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortAnchor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStrokePattern
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStyle
import io.aequicor.visualization.subsystems.diagrams.model.ErAttribute
import io.aequicor.visualization.subsystems.diagrams.model.ErCardinality
import io.aequicor.visualization.subsystems.diagrams.model.FlowchartNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.LineJumpStyle
import io.aequicor.visualization.subsystems.diagrams.model.SwimlaneLane
import io.aequicor.visualization.subsystems.diagrams.model.TableCell
import io.aequicor.visualization.subsystems.diagrams.model.TableColumn
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.TableRow
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivation
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActorNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlComponentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlDeploymentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMember
import io.aequicor.visualization.subsystems.diagrams.model.UmlMessageKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlNoteNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlPackageNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlVisibility
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * JSON (de)serialization of the `diagram` node payload for the slm-ir/1.0 document format.
 *
 * The diagrams core (`:subsystems:diagrams`) is deliberately not annotated with
 * `@Serializable`; this file owns the wire format via DTO mirrors + explicit mappers,
 * exactly like the rest of the document format owns its shapes.
 *
 * Format conventions:
 * - sealed hierarchies (payload/endpoint/relation/anchor) use a `"type"` discriminator;
 * - enums are lowercase strings (`"orthogonal"`, `"er_one_or_many"`), read case-insensitively
 *   with a forward-compatible fallback to the model default;
 * - colors are `"#AARRGGBB"` hex strings;
 * - default values are omitted on write.
 */

private val DiagramJson = Json { ignoreUnknownKeys = true }

// --- Entry points -------------------------------------------------------------

/** Reads the `diagram` block of a `diagram` node; malformed payloads warn and become empty. */
internal fun DesignDocumentReader.readDiagramKind(obj: JsonObject, pointer: String): DesignNodeKind.Diagram {
    val element = obj["diagram"] ?: return DesignNodeKind.Diagram(DiagramGraph.Empty)
    return try {
        val dto = DiagramJson.decodeFromJsonElement(DiagramGraphDto.serializer(), element)
        DesignNodeKind.Diagram(dto.toModel())
    } catch (failure: Exception) {
        warn(
            "$pointer/diagram",
            "Malformed diagram graph (${failure.message.orEmpty().take(160)}); using an empty diagram",
        )
        DesignNodeKind.Diagram(DiagramGraph.Empty)
    }
}

/** Serializes a [DiagramGraph] to its document-format JSON object. */
internal fun writeDiagramGraph(graph: DiagramGraph): JsonObject =
    DiagramJson.encodeToJsonElement(DiagramGraphDto.serializer(), graph.toDto()).jsonObject

// --- Graph DTOs ---------------------------------------------------------------

@Serializable
internal data class DiagramGraphDto(
    val nodes: List<DiagramNodeDto> = emptyList(),
    val edges: List<DiagramEdgeDto> = emptyList(),
    val layers: List<DiagramLayerDto> = emptyList(),
    val groups: List<DiagramGroupDto> = emptyList(),
)

@Serializable
internal data class DiagramNodeDto(
    val id: String,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0,
    val rotation: Double = 0.0,
    val payload: DiagramNodePayloadDto = DiagramNodePayloadDto.BasicShapeDto(),
    val ports: List<DiagramPortDto> = emptyList(),
    val style: DiagramStyleDto? = null,
    val labels: List<DiagramLabelDto> = emptyList(),
    val parentId: String? = null,
    val layerId: String? = null,
    val locked: Boolean = false,
    val visible: Boolean = true,
)

@Serializable
internal data class DiagramEdgeDto(
    val id: String,
    val source: DiagramEndpointDto,
    val target: DiagramEndpointDto,
    val relation: DiagramRelationDto = DiagramRelationDto.PlainDto,
    val routing: String = "orthogonal",
    val waypoints: List<DiagramPointDto> = emptyList(),
    val style: DiagramStyleDto? = null,
    val labels: List<DiagramEdgeLabelDto> = emptyList(),
    val sourceArrowhead: DiagramArrowheadDto? = null,
    val targetArrowhead: DiagramArrowheadDto? = null,
    val lineJumps: String = "arc",
    val connectionMode: String = "line",
    val flowAnimation: Boolean = false,
    val layerId: String? = null,
)

@Serializable
internal data class DiagramLayerDto(
    val id: String,
    val name: String = "",
    val visible: Boolean = true,
    val locked: Boolean = false,
)

@Serializable
internal data class DiagramGroupDto(
    val id: String,
    val members: List<String> = emptyList(),
    val name: String? = null,
)

@Serializable
internal data class DiagramPointDto(val x: Double, val y: Double)

@Serializable
internal data class DiagramLabelDto(val text: String, val markdown: Boolean = false)

@Serializable
internal data class DiagramEdgeLabelDto(
    val label: DiagramLabelDto,
    val position: String = "middle",
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
)

@Serializable
internal data class DiagramStyleDto(
    val fill: String? = null,
    val stroke: String? = null,
    val strokeWidth: Double = 1.0,
    val pattern: String = "solid",
    val opacity: Double = 1.0,
    val cornerStyle: String = "rounded",
    val sketch: Boolean = false,
    val shadow: Boolean = false,
)

@Serializable
internal data class DiagramArrowheadDto(
    val kind: String = "none",
    val size: Double = 8.0,
    val inset: Double = 0.0,
)

@Serializable
internal data class DiagramPortDto(
    val id: String,
    val anchor: DiagramPortAnchorDto,
)

@Serializable
internal sealed interface DiagramPortAnchorDto {

    @Serializable
    @SerialName("side")
    data class SideDto(val side: String, val offset: Double = 0.5) : DiagramPortAnchorDto

    @Serializable
    @SerialName("point")
    data class PointDto(val x: Double, val y: Double) : DiagramPortAnchorDto
}

@Serializable
internal sealed interface DiagramEndpointDto {

    @Serializable
    @SerialName("floating")
    data class FloatingDto(val nodeId: String) : DiagramEndpointDto

    @Serializable
    @SerialName("port")
    data class PortDto(val nodeId: String, val portId: String) : DiagramEndpointDto

    @Serializable
    @SerialName("free")
    data class FreeDto(val x: Double, val y: Double) : DiagramEndpointDto
}

@Serializable
internal sealed interface DiagramRelationDto {

    @Serializable
    @SerialName("plain")
    data object PlainDto : DiagramRelationDto

    @Serializable
    @SerialName("association")
    data class AssociationDto(val directed: Boolean = false) : DiagramRelationDto

    @Serializable
    @SerialName("aggregation")
    data object AggregationDto : DiagramRelationDto

    @Serializable
    @SerialName("composition")
    data object CompositionDto : DiagramRelationDto

    @Serializable
    @SerialName("generalization")
    data object GeneralizationDto : DiagramRelationDto

    @Serializable
    @SerialName("dependency")
    data object DependencyDto : DiagramRelationDto

    @Serializable
    @SerialName("realization")
    data object RealizationDto : DiagramRelationDto

    @Serializable
    @SerialName("message")
    data class MessageDto(val kind: String) : DiagramRelationDto

    @Serializable
    @SerialName("transition")
    data object TransitionDto : DiagramRelationDto

    @Serializable
    @SerialName("include")
    data object IncludeDto : DiagramRelationDto

    @Serializable
    @SerialName("extend")
    data object ExtendDto : DiagramRelationDto

    @Serializable
    @SerialName("entityRelation")
    data class EntityRelationDto(
        val sourceCardinality: String = "one",
        val targetCardinality: String = "many",
    ) : DiagramRelationDto
}

// --- Payload DTOs ---------------------------------------------------------------

@Serializable
internal sealed interface DiagramNodePayloadDto {

    @Serializable
    @SerialName("shape")
    data class BasicShapeDto(val shape: String = "rectangle") : DiagramNodePayloadDto

    @Serializable
    @SerialName("container")
    data class ContainerDto(
        val title: DiagramLabelDto? = null,
        val collapsed: Boolean = false,
    ) : DiagramNodePayloadDto

    @Serializable
    @SerialName("swimlane")
    data class SwimlaneDto(
        val orientation: String = "horizontal",
        val lanes: List<SwimlaneLaneDto> = emptyList(),
        val title: DiagramLabelDto? = null,
    ) : DiagramNodePayloadDto

    @Serializable
    @SerialName("flowchart")
    data class FlowchartDto(val kind: String = "process") : DiagramNodePayloadDto

    @Serializable
    @SerialName("erEntity")
    data class ErEntityDto(
        val name: String = "",
        val attributes: List<ErAttributeDto> = emptyList(),
    ) : DiagramNodePayloadDto

    @Serializable
    @SerialName("bpmn")
    data class BpmnDto(val kind: String = "task") : DiagramNodePayloadDto

    @Serializable
    @SerialName("table")
    data class TableDto(
        val rows: List<TableRowDto> = emptyList(),
        val columns: List<TableColumnDto> = emptyList(),
        val cells: List<TableCellDto> = emptyList(),
    ) : DiagramNodePayloadDto

    @Serializable
    @SerialName("umlClass")
    data class UmlClassDto(
        val name: String = "",
        val stereotype: String? = null,
        val abstract: Boolean = false,
        val attributes: List<UmlMemberDto> = emptyList(),
        val operations: List<UmlMemberDto> = emptyList(),
    ) : DiagramNodePayloadDto

    @Serializable
    @SerialName("umlLifeline")
    data class UmlLifelineDto(
        val name: String = "",
        val actor: Boolean = false,
        val activations: List<UmlActivationDto> = emptyList(),
    ) : DiagramNodePayloadDto

    @Serializable
    @SerialName("umlState")
    data class UmlStateDto(
        val name: String = "",
        val kind: String = "simple",
    ) : DiagramNodePayloadDto

    @Serializable
    @SerialName("umlActivity")
    data class UmlActivityDto(
        val kind: String = "action",
        val name: String = "",
    ) : DiagramNodePayloadDto

    @Serializable
    @SerialName("umlActor")
    data class UmlActorDto(val name: String = "") : DiagramNodePayloadDto

    @Serializable
    @SerialName("umlUseCase")
    data class UmlUseCaseDto(val name: String = "") : DiagramNodePayloadDto

    @Serializable
    @SerialName("umlComponent")
    data class UmlComponentDto(
        val name: String = "",
        val stereotype: String? = null,
    ) : DiagramNodePayloadDto

    @Serializable
    @SerialName("umlDeployment")
    data class UmlDeploymentDto(
        val name: String = "",
        val stereotype: String? = null,
    ) : DiagramNodePayloadDto

    @Serializable
    @SerialName("umlNote")
    data class UmlNoteDto(val text: String = "") : DiagramNodePayloadDto

    @Serializable
    @SerialName("umlPackage")
    data class UmlPackageDto(val name: String = "") : DiagramNodePayloadDto
}

@Serializable
internal data class SwimlaneLaneDto(
    val title: DiagramLabelDto? = null,
    val size: Double = 120.0,
)

@Serializable
internal data class ErAttributeDto(
    val name: String = "",
    val type: String? = null,
    val primaryKey: Boolean = false,
    val foreignKey: Boolean = false,
)

@Serializable
internal data class TableRowDto(val height: Double = 32.0, val header: Boolean = false)

@Serializable
internal data class TableColumnDto(val width: Double = 120.0, val header: Boolean = false)

@Serializable
internal data class TableCellDto(
    val row: Int = 0,
    val column: Int = 0,
    val rowSpan: Int = 1,
    val colSpan: Int = 1,
    val label: DiagramLabelDto? = null,
    val style: DiagramStyleDto? = null,
)

@Serializable
internal data class UmlMemberDto(
    val text: String = "",
    val visibility: String = "public",
    val static: Boolean = false,
    val abstract: Boolean = false,
)

@Serializable
internal data class UmlActivationDto(val start: Double = 0.0, val end: Double = 0.0)

// --- Enum and color helpers -----------------------------------------------------

/** Lowercase wire name of an enum constant, e.g. `ER_ONE_OR_MANY` -> `"er_one_or_many"`. */
private fun Enum<*>.wireName(): String = name.lowercase()

/** Case- and underscore-insensitive enum parse with a forward-compatible [default]. */
private inline fun <reified E : Enum<E>> parseEnumOr(raw: String, default: E): E =
    enumValues<E>().firstOrNull {
        it.name.replace("_", "").equals(raw.replace("_", ""), ignoreCase = true)
    } ?: default

private fun DiagramColor.toHex(): String {
    val hex = argb.toUInt().toString(16).uppercase().padStart(8, '0')
    return "#$hex"
}

private fun parseDiagramColor(raw: String): DiagramColor? =
    raw.removePrefix("#").takeIf { it.length == 8 }?.toUIntOrNull(16)
        ?.let { DiagramColor(it.toULong()) }

// --- Model -> DTO -----------------------------------------------------------------

internal fun DiagramGraph.toDto(): DiagramGraphDto = DiagramGraphDto(
    nodes = nodes.map { it.toDto() },
    edges = edges.map { it.toDto() },
    layers = layers.map { DiagramLayerDto(it.id.value, it.name, it.visible, it.locked) },
    groups = groups.map { DiagramGroupDto(it.id.value, it.memberIds.map(DiagramNodeId::value), it.name) },
)

private fun DiagramNode.toDto(): DiagramNodeDto = DiagramNodeDto(
    id = id.value,
    x = x,
    y = y,
    width = width,
    height = height,
    rotation = rotation,
    payload = payload.toDto(),
    ports = ports.map { DiagramPortDto(it.id.value, it.anchor.toDto()) },
    style = style.toDtoOrNull(),
    labels = labels.map { it.toDto() },
    parentId = parentId?.value,
    layerId = layerId?.value,
    locked = locked,
    visible = visible,
)

private fun DiagramEdge.toDto(): DiagramEdgeDto = DiagramEdgeDto(
    id = id.value,
    source = source.toDto(),
    target = target.toDto(),
    relation = relation.toDto(),
    routing = routing.wireName(),
    waypoints = waypoints.map { DiagramPointDto(it.x, it.y) },
    style = style.toDtoOrNull(),
    labels = labels.map {
        DiagramEdgeLabelDto(it.label.toDto(), it.position.wireName(), it.offsetX, it.offsetY)
    },
    sourceArrowhead = sourceArrowhead.toDtoOrNull(),
    targetArrowhead = targetArrowhead.toDtoOrNull(),
    lineJumps = lineJumps.wireName(),
    connectionMode = connectionMode.wireName(),
    flowAnimation = flowAnimation,
    layerId = layerId?.value,
)

private fun DiagramLabel.toDto(): DiagramLabelDto = DiagramLabelDto(text, markdown)

/** Default style serializes as an absent field. */
private fun DiagramStyle.toDtoOrNull(): DiagramStyleDto? =
    takeIf { it != DiagramStyle.Default }?.let {
        DiagramStyleDto(
            fill = it.fill?.toHex(),
            stroke = it.stroke?.toHex(),
            strokeWidth = it.strokeWidth,
            pattern = it.pattern.wireName(),
            opacity = it.opacity,
            cornerStyle = it.cornerStyle.wireName(),
            sketch = it.sketch,
            shadow = it.shadow,
        )
    }

/** The no-arrowhead marker serializes as an absent field. */
private fun DiagramArrowhead.toDtoOrNull(): DiagramArrowheadDto? =
    takeIf { it != DiagramArrowhead.None }?.let {
        DiagramArrowheadDto(it.kind.wireName(), it.size, it.inset)
    }

private fun DiagramPortAnchor.toDto(): DiagramPortAnchorDto = when (this) {
    is DiagramPortAnchor.SideOffset -> DiagramPortAnchorDto.SideDto(side.wireName(), offset)
    is DiagramPortAnchor.RelativePoint -> DiagramPortAnchorDto.PointDto(x, y)
}

private fun DiagramEndpoint.toDto(): DiagramEndpointDto = when (this) {
    is DiagramEndpoint.FloatingAnchor -> DiagramEndpointDto.FloatingDto(nodeId.value)
    is DiagramEndpoint.FixedPort -> DiagramEndpointDto.PortDto(nodeId.value, portId.value)
    is DiagramEndpoint.FreePoint -> DiagramEndpointDto.FreeDto(x, y)
}

private fun DiagramRelation.toDto(): DiagramRelationDto = when (this) {
    DiagramRelation.Plain -> DiagramRelationDto.PlainDto
    is DiagramRelation.Association -> DiagramRelationDto.AssociationDto(directed)
    DiagramRelation.Aggregation -> DiagramRelationDto.AggregationDto
    DiagramRelation.Composition -> DiagramRelationDto.CompositionDto
    DiagramRelation.Generalization -> DiagramRelationDto.GeneralizationDto
    DiagramRelation.Dependency -> DiagramRelationDto.DependencyDto
    DiagramRelation.Realization -> DiagramRelationDto.RealizationDto
    is DiagramRelation.Message -> DiagramRelationDto.MessageDto(kind.wireName())
    DiagramRelation.Transition -> DiagramRelationDto.TransitionDto
    DiagramRelation.Include -> DiagramRelationDto.IncludeDto
    DiagramRelation.Extend -> DiagramRelationDto.ExtendDto
    is DiagramRelation.EntityRelation -> DiagramRelationDto.EntityRelationDto(
        sourceCardinality = sourceCardinality.wireName(),
        targetCardinality = targetCardinality.wireName(),
    )
}

private fun DiagramNodePayload.toDto(): DiagramNodePayloadDto = when (this) {
    is DiagramNodePayload.BasicShape -> DiagramNodePayloadDto.BasicShapeDto(shape.wireName())
    is DiagramNodePayload.ContainerNode -> DiagramNodePayloadDto.ContainerDto(title?.toDto(), collapsed)
    is DiagramNodePayload.SwimlaneNode -> DiagramNodePayloadDto.SwimlaneDto(
        orientation = orientation.wireName(),
        lanes = lanes.map { SwimlaneLaneDto(it.title?.toDto(), it.size) },
        title = title?.toDto(),
    )
    is DiagramNodePayload.FlowchartNode -> DiagramNodePayloadDto.FlowchartDto(kind.wireName())
    is DiagramNodePayload.ErEntityNode -> DiagramNodePayloadDto.ErEntityDto(
        name = name,
        attributes = attributes.map { ErAttributeDto(it.name, it.type, it.primaryKey, it.foreignKey) },
    )
    is DiagramNodePayload.BpmnNode -> DiagramNodePayloadDto.BpmnDto(kind.wireName())
    is TableNode -> DiagramNodePayloadDto.TableDto(
        rows = rows.map { TableRowDto(it.height, it.header) },
        columns = columns.map { TableColumnDto(it.width, it.header) },
        cells = cells.map {
            TableCellDto(it.row, it.column, it.rowSpan, it.colSpan, it.label?.toDto(), it.style?.toDtoOrNull())
        },
    )
    is UmlClassNode -> DiagramNodePayloadDto.UmlClassDto(
        name = name,
        stereotype = stereotype,
        abstract = abstract,
        attributes = attributes.map { it.toDto() },
        operations = operations.map { it.toDto() },
    )
    is UmlLifelineNode -> DiagramNodePayloadDto.UmlLifelineDto(
        name = name,
        actor = actor,
        activations = activations.map { UmlActivationDto(it.start, it.end) },
    )
    is UmlStateNode -> DiagramNodePayloadDto.UmlStateDto(name, kind.wireName())
    is UmlActivityNode -> DiagramNodePayloadDto.UmlActivityDto(kind.wireName(), name)
    is UmlActorNode -> DiagramNodePayloadDto.UmlActorDto(name)
    is UmlUseCaseNode -> DiagramNodePayloadDto.UmlUseCaseDto(name)
    is UmlComponentNode -> DiagramNodePayloadDto.UmlComponentDto(name, stereotype)
    is UmlDeploymentNode -> DiagramNodePayloadDto.UmlDeploymentDto(name, stereotype)
    is UmlNoteNode -> DiagramNodePayloadDto.UmlNoteDto(text)
    is UmlPackageNode -> DiagramNodePayloadDto.UmlPackageDto(name)
}

private fun UmlMember.toDto(): UmlMemberDto =
    UmlMemberDto(text, visibility.wireName(), static, abstract)

// --- DTO -> model -----------------------------------------------------------------

internal fun DiagramGraphDto.toModel(): DiagramGraph = DiagramGraph(
    nodes = nodes.map { it.toModel() },
    edges = edges.map { it.toModel() },
    layers = layers.map { DiagramLayer(DiagramLayerId(it.id), it.name.ifEmpty { it.id }, it.visible, it.locked) },
    groups = groups.map { DiagramGroup(DiagramGroupId(it.id), it.members.map(::DiagramNodeId), it.name) },
)

private fun DiagramNodeDto.toModel(): DiagramNode = DiagramNode(
    id = DiagramNodeId(id),
    x = x,
    y = y,
    width = width,
    height = height,
    rotation = rotation,
    payload = payload.toModel(),
    ports = ports.map { DiagramPort(DiagramPortId(it.id), it.anchor.toModel()) },
    style = style?.toModel() ?: DiagramStyle.Default,
    labels = labels.map { it.toModel() },
    parentId = parentId?.let(::DiagramNodeId),
    layerId = layerId?.let(::DiagramLayerId),
    locked = locked,
    visible = visible,
)

private fun DiagramEdgeDto.toModel(): DiagramEdge = DiagramEdge(
    id = DiagramEdgeId(id),
    source = source.toModel(),
    target = target.toModel(),
    relation = relation.toModel(),
    routing = parseEnumOr(routing, DiagramRoutingStyle.ORTHOGONAL),
    waypoints = waypoints.map { DiagramPoint(it.x, it.y) },
    style = style?.toModel() ?: DiagramStyle.Default,
    labels = labels.map { labelDto ->
        DiagramEdgeLabel(
            label = labelDto.label.toModel(),
            position = parseEnumOr(labelDto.position, DiagramEdgeLabelPosition.MIDDLE),
            offsetX = labelDto.offsetX,
            offsetY = labelDto.offsetY,
        )
    },
    sourceArrowhead = sourceArrowhead?.toModel() ?: DiagramArrowhead.None,
    targetArrowhead = targetArrowhead?.toModel() ?: DiagramArrowhead.None,
    lineJumps = parseEnumOr(lineJumps, LineJumpStyle.ARC),
    connectionMode = parseEnumOr(connectionMode, DiagramConnectionMode.LINE),
    flowAnimation = flowAnimation,
    layerId = layerId?.let(::DiagramLayerId),
)

private fun DiagramLabelDto.toModel(): DiagramLabel = DiagramLabel(text, markdown)

private fun DiagramStyleDto.toModel(): DiagramStyle = DiagramStyle(
    fill = fill?.let(::parseDiagramColor),
    stroke = stroke?.let(::parseDiagramColor),
    strokeWidth = strokeWidth,
    pattern = parseEnumOr(pattern, DiagramStrokePattern.SOLID),
    opacity = opacity,
    cornerStyle = parseEnumOr(cornerStyle, DiagramCornerStyle.ROUNDED),
    sketch = sketch,
    shadow = shadow,
)

private fun DiagramArrowheadDto.toModel(): DiagramArrowhead =
    DiagramArrowhead(parseEnumOr(kind, DiagramArrowheadKind.NONE), size, inset)

private fun DiagramPortAnchorDto.toModel(): DiagramPortAnchor = when (this) {
    is DiagramPortAnchorDto.SideDto ->
        DiagramPortAnchor.SideOffset(parseEnumOr(side, DiagramNodeSide.TOP), offset)
    is DiagramPortAnchorDto.PointDto -> DiagramPortAnchor.RelativePoint(x, y)
}

private fun DiagramEndpointDto.toModel(): DiagramEndpoint = when (this) {
    is DiagramEndpointDto.FloatingDto -> DiagramEndpoint.FloatingAnchor(DiagramNodeId(nodeId))
    is DiagramEndpointDto.PortDto -> DiagramEndpoint.FixedPort(DiagramNodeId(nodeId), DiagramPortId(portId))
    is DiagramEndpointDto.FreeDto -> DiagramEndpoint.FreePoint(x, y)
}

private fun DiagramRelationDto.toModel(): DiagramRelation = when (this) {
    DiagramRelationDto.PlainDto -> DiagramRelation.Plain
    is DiagramRelationDto.AssociationDto -> DiagramRelation.Association(directed)
    DiagramRelationDto.AggregationDto -> DiagramRelation.Aggregation
    DiagramRelationDto.CompositionDto -> DiagramRelation.Composition
    DiagramRelationDto.GeneralizationDto -> DiagramRelation.Generalization
    DiagramRelationDto.DependencyDto -> DiagramRelation.Dependency
    DiagramRelationDto.RealizationDto -> DiagramRelation.Realization
    is DiagramRelationDto.MessageDto -> DiagramRelation.Message(parseEnumOr(kind, UmlMessageKind.SYNC))
    DiagramRelationDto.TransitionDto -> DiagramRelation.Transition
    DiagramRelationDto.IncludeDto -> DiagramRelation.Include
    DiagramRelationDto.ExtendDto -> DiagramRelation.Extend
    is DiagramRelationDto.EntityRelationDto -> DiagramRelation.EntityRelation(
        sourceCardinality = parseEnumOr(sourceCardinality, ErCardinality.ONE),
        targetCardinality = parseEnumOr(targetCardinality, ErCardinality.MANY),
    )
}

private fun DiagramNodePayloadDto.toModel(): DiagramNodePayload = when (this) {
    is DiagramNodePayloadDto.BasicShapeDto ->
        DiagramNodePayload.BasicShape(parseEnumOr(shape, DiagramShapeKind.RECTANGLE))
    is DiagramNodePayloadDto.ContainerDto ->
        DiagramNodePayload.ContainerNode(title?.toModel(), collapsed)
    is DiagramNodePayloadDto.SwimlaneDto -> DiagramNodePayload.SwimlaneNode(
        orientation = parseEnumOr(orientation, DiagramOrientation.HORIZONTAL),
        lanes = lanes.map { SwimlaneLane(it.title?.toModel(), it.size) },
        title = title?.toModel(),
    )
    is DiagramNodePayloadDto.FlowchartDto ->
        DiagramNodePayload.FlowchartNode(parseEnumOr(kind, FlowchartNodeKind.PROCESS))
    is DiagramNodePayloadDto.ErEntityDto -> DiagramNodePayload.ErEntityNode(
        name = name,
        attributes = attributes.map { ErAttribute(it.name, it.type, it.primaryKey, it.foreignKey) },
    )
    is DiagramNodePayloadDto.BpmnDto ->
        DiagramNodePayload.BpmnNode(parseEnumOr(kind, BpmnNodeKind.TASK))
    is DiagramNodePayloadDto.TableDto -> TableNode(
        rows = rows.map { TableRow(it.height, it.header) },
        columns = columns.map { TableColumn(it.width, it.header) },
        cells = cells.map {
            TableCell(it.row, it.column, it.rowSpan, it.colSpan, it.label?.toModel(), it.style?.toModel())
        },
    )
    is DiagramNodePayloadDto.UmlClassDto -> UmlClassNode(
        name = name,
        stereotype = stereotype,
        abstract = abstract,
        attributes = attributes.map { it.toModel() },
        operations = operations.map { it.toModel() },
    )
    is DiagramNodePayloadDto.UmlLifelineDto -> UmlLifelineNode(
        name = name,
        actor = actor,
        activations = activations.map { UmlActivation(it.start, it.end) },
    )
    is DiagramNodePayloadDto.UmlStateDto -> UmlStateNode(name, parseEnumOr(kind, UmlStateKind.SIMPLE))
    is DiagramNodePayloadDto.UmlActivityDto ->
        UmlActivityNode(parseEnumOr(kind, UmlActivityKind.ACTION), name)
    is DiagramNodePayloadDto.UmlActorDto -> UmlActorNode(name)
    is DiagramNodePayloadDto.UmlUseCaseDto -> UmlUseCaseNode(name)
    is DiagramNodePayloadDto.UmlComponentDto -> UmlComponentNode(name, stereotype)
    is DiagramNodePayloadDto.UmlDeploymentDto -> UmlDeploymentNode(name, stereotype)
    is DiagramNodePayloadDto.UmlNoteDto -> UmlNoteNode(text)
    is DiagramNodePayloadDto.UmlPackageDto -> UmlPackageNode(name)
}

private fun UmlMemberDto.toModel(): UmlMember =
    UmlMember(text, parseEnumOr(visibility, UmlVisibility.PUBLIC), static, abstract)
