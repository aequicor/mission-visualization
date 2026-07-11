package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.engine.frontend.blocks.readers.BlockReading
import io.aequicor.visualization.engine.frontend.yaml.YamlList
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlScalar
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
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

/**
 * Parses the YAML value under `diagram:` into a [DiagramGraph]. Lenient by design:
 * malformed elements are dropped (or defaulted) with a diagnostic through
 * [BlockReading], never thrown — the rest of the graph survives.
 */
internal object DiagramYamlReader {

    fun read(value: YamlValue, reading: BlockReading): DiagramGraph? {
        if (value is YamlScalar && value.value == null) return DiagramGraph.Empty
        val map = value as? YamlMap ?: run {
            reading.error("`diagram` must be a map with nodes/edges/layers/groups", value)
            return null
        }
        val layers = readIdList(map, "layers", reading, "layer") { item -> readLayer(item, reading) }
        val nodes = readIdList(map, "nodes", reading, "node") { item -> readNode(item, reading) }
        val edges = readIdList(map, "edges", reading, "edge") { item -> readEdge(item, reading) }
        val groups = readIdList(map, "groups", reading, "group") { item -> readGroup(item, reading) }
        return DiagramGraph(nodes = nodes, edges = edges, layers = layers, groups = groups)
    }

    /** Reads a list entry, dropping duplicate-id items (id extracted by the caller's reader). */
    private fun <T : Any> readIdList(
        map: YamlMap,
        key: String,
        reading: BlockReading,
        what: String,
        readItem: (YamlValue) -> Pair<String, T>?,
    ): List<T> {
        val value = map.entries[key] ?: return emptyList()
        val list = value as? YamlList ?: run {
            reading.error("`diagram.$key` must be a list", value)
            return emptyList()
        }
        val seen = mutableSetOf<String>()
        return list.items.mapNotNull { item ->
            val (id, parsed) = readItem(item) ?: return@mapNotNull null
            if (!seen.add(id)) {
                reading.error("duplicate diagram $what id '$id'", item)
                null
            } else {
                parsed
            }
        }
    }

    // --- layers / groups ---

    private fun readLayer(value: YamlValue, reading: BlockReading): Pair<String, DiagramLayer>? {
        val map = value.asMap(reading, "diagram layer") ?: return null
        val id = map.requiredString("id", reading, "diagram layer") ?: return null
        val layer = DiagramLayer(
            id = DiagramLayerId(id),
            name = map.stringAt("name") ?: id,
            visible = map.boolAt("visible") ?: true,
            locked = map.boolAt("locked") ?: false,
        )
        return id to layer
    }

    private fun readGroup(value: YamlValue, reading: BlockReading): Pair<String, DiagramGroup>? {
        val map = value.asMap(reading, "diagram group") ?: return null
        val id = map.requiredString("id", reading, "diagram group") ?: return null
        val membersValue = map.entries["members"]
        val members = (membersValue as? YamlList)?.items
            ?.mapNotNull { it.scalarText() }
            ?.distinct()
            .orEmpty()
        if (members.isEmpty()) {
            reading.error("diagram group '$id' must list at least one member", membersValue ?: value)
            return null
        }
        return id to DiagramGroup(
            id = DiagramGroupId(id),
            memberIds = members.map(::DiagramNodeId),
            name = map.stringAt("name"),
        )
    }

    // --- nodes ---

    private fun readNode(value: YamlValue, reading: BlockReading): Pair<String, DiagramNode>? {
        val map = value.asMap(reading, "diagram node") ?: return null
        val id = map.requiredString("id", reading, "diagram node") ?: return null
        val payload = readPayload(map, id, reading)

        val ports = readPorts(map.entries["ports"], id, reading)
        val labels = readNodeLabels(map, reading)

        val node = DiagramNode(
            id = DiagramNodeId(id),
            x = map.numberAt("x") ?: 0.0,
            y = map.numberAt("y") ?: 0.0,
            width = nonNegative(map.numberAt("w") ?: map.numberAt("width") ?: 0.0, "w", id, reading, value),
            height = nonNegative(map.numberAt("h") ?: map.numberAt("height") ?: 0.0, "h", id, reading, value),
            rotation = map.numberAt("rotation") ?: 0.0,
            payload = payload,
            ports = ports,
            style = readStyle(map.entries["style"], reading) ?: DiagramStyle.Default,
            labels = labels,
            parentId = map.stringAt("parent")?.let(::DiagramNodeId),
            layerId = map.stringAt("layer")?.let(::DiagramLayerId),
            locked = map.boolAt("locked") ?: false,
            visible = map.boolAt("visible") ?: true,
        )
        return id to node
    }

    private fun nonNegative(
        value: Double,
        key: String,
        nodeId: String,
        reading: BlockReading,
        at: YamlValue,
    ): Double {
        if (value < 0.0) {
            reading.warning("diagram node '$nodeId' has negative $key, coerced to 0", at)
            return 0.0
        }
        return value
    }

    private fun readPayload(map: YamlMap, nodeId: String, reading: BlockReading): DiagramNodePayload {
        val typeValue = map.entries["type"]
        val token = typeValue?.scalarText() ?: run {
            reading.warning("diagram node '$nodeId' has no type, defaulting to rectangle", map)
            return DiagramNodePayload.BasicShape()
        }
        val normalized = token.trim().lowercase().replace('-', '_')
        enumFromToken<DiagramShapeKind>(normalized)?.let {
            return DiagramNodePayload.BasicShape(it)
        }
        return when (normalized) {
            "container" -> DiagramNodePayload.ContainerNode(
                title = readLabelValue(map.entries["title"], reading),
                collapsed = map.boolAt("collapsed") ?: false,
            )

            "swimlane" -> DiagramNodePayload.SwimlaneNode(
                orientation = map.enumAt<DiagramOrientation>("orientation", reading)
                    ?: DiagramOrientation.HORIZONTAL,
                lanes = readLanes(map.entries["lanes"], reading),
                title = readLabelValue(map.entries["title"], reading),
            )

            "flowchart" -> DiagramNodePayload.FlowchartNode(
                kind = map.requiredEnum<FlowchartNodeKind>("kind", nodeId, reading, typeValue)
                    ?: FlowchartNodeKind.PROCESS,
            )

            "entity", "er_entity" -> DiagramNodePayload.ErEntityNode(
                name = map.stringAt("name") ?: "",
                attributes = readErAttributes(map.entries["attributes"], reading),
            )

            "bpmn" -> DiagramNodePayload.BpmnNode(
                kind = map.requiredEnum<BpmnNodeKind>("kind", nodeId, reading, typeValue)
                    ?: BpmnNodeKind.TASK,
            )

            "table" -> readTable(map, nodeId, reading)

            "class", "uml_class" -> UmlClassNode(
                name = map.stringAt("name") ?: "",
                stereotype = map.stringAt("stereotype"),
                abstract = map.boolAt("abstract") ?: false,
                attributes = readMembers(map.entries["fields"], reading),
                operations = readMembers(map.entries["methods"], reading),
            )

            "lifeline" -> UmlLifelineNode(
                name = map.stringAt("name") ?: "",
                actor = map.boolAt("actor") ?: false,
                activations = readActivations(map.entries["activations"], nodeId, reading),
            )

            "state" -> UmlStateNode(
                name = map.stringAt("name") ?: "",
                kind = map.enumAt<UmlStateKind>("kind", reading) ?: UmlStateKind.SIMPLE,
            )

            "activity" -> UmlActivityNode(
                kind = map.requiredEnum<UmlActivityKind>("kind", nodeId, reading, typeValue)
                    ?: UmlActivityKind.ACTION,
                name = map.stringAt("name") ?: "",
            )

            "actor" -> UmlActorNode(name = map.stringAt("name") ?: "")
            "use_case", "usecase" -> UmlUseCaseNode(name = map.stringAt("name") ?: "")
            "component" -> UmlComponentNode(
                name = map.stringAt("name") ?: "",
                stereotype = map.stringAt("stereotype"),
            )
            "deployment" -> UmlDeploymentNode(
                name = map.stringAt("name") ?: "",
                stereotype = map.stringAt("stereotype"),
            )
            "note" -> UmlNoteNode(text = map.stringAt("text") ?: "")
            "package" -> UmlPackageNode(name = map.stringAt("name") ?: "")

            else -> {
                reading.error("unknown diagram node type '$token' on node '$nodeId'", typeValue)
                DiagramNodePayload.BasicShape()
            }
        }
    }

    private fun readLanes(value: YamlValue?, reading: BlockReading): List<SwimlaneLane> {
        val list = value?.asListOrNull() ?: return emptyList()
        return list.items.mapNotNull { item ->
            when (item) {
                is YamlScalar -> (item.value as? Double)?.let { SwimlaneLane(size = maxOf(it, 0.0)) }
                is YamlMap -> SwimlaneLane(
                    title = readLabelValue(item.entries["title"], reading),
                    size = maxOf(item.numberAt("size") ?: 120.0, 0.0),
                )
                else -> {
                    reading.error("swimlane lane must be a size number or a map", item)
                    null
                }
            }
        }
    }

    private fun readErAttributes(value: YamlValue?, reading: BlockReading): List<ErAttribute> {
        val list = value?.asListOrNull() ?: return emptyList()
        return list.items.mapNotNull { item ->
            val map = item as? YamlMap ?: run {
                reading.error("entity attribute must be a map with `name`", item)
                return@mapNotNull null
            }
            val name = map.stringAt("name") ?: run {
                reading.error("entity attribute is missing `name`", item)
                return@mapNotNull null
            }
            ErAttribute(
                name = name,
                type = map.stringAt("type"),
                primaryKey = map.boolAt("pk") ?: map.boolAt("primaryKey") ?: false,
                foreignKey = map.boolAt("fk") ?: map.boolAt("foreignKey") ?: false,
            )
        }
    }

    private fun readTable(map: YamlMap, nodeId: String, reading: BlockReading): DiagramNodePayload {
        val rows = (map.entries["rows"]?.asListOrNull())?.items?.mapNotNull { item ->
            when (item) {
                is YamlScalar -> (item.value as? Double)?.let { TableRow(height = maxOf(it, 0.0)) }
                is YamlMap -> TableRow(
                    height = maxOf(item.numberAt("height") ?: 32.0, 0.0),
                    header = item.boolAt("header") ?: false,
                )
                else -> null
            }
        }.orEmpty()
        val columns = (map.entries["columns"]?.asListOrNull() ?: map.entries["cols"]?.asListOrNull())
            ?.items?.mapNotNull { item ->
                when (item) {
                    is YamlScalar -> (item.value as? Double)?.let { TableColumn(width = maxOf(it, 0.0)) }
                    is YamlMap -> TableColumn(
                        width = maxOf(item.numberAt("width") ?: 120.0, 0.0),
                        header = item.boolAt("header") ?: false,
                    )
                    else -> null
                }
            }.orEmpty()

        val covered = mutableSetOf<Pair<Int, Int>>()
        val cells = (map.entries["cells"]?.asListOrNull())?.items?.mapNotNull { item ->
            val cellMap = item as? YamlMap ?: run {
                reading.error("table cell must be a map with row/col", item)
                return@mapNotNull null
            }
            val row = cellMap.intAt("row") ?: run {
                reading.error("table cell is missing `row`", item)
                return@mapNotNull null
            }
            val column = cellMap.intAt("col") ?: cellMap.intAt("column") ?: run {
                reading.error("table cell is missing `col`", item)
                return@mapNotNull null
            }
            val rowSpan = (cellMap.intAt("rowSpan") ?: 1).coerceAtLeast(1)
            val colSpan = (cellMap.intAt("colSpan") ?: 1).coerceAtLeast(1)
            if (row < 0 || column < 0 || row + rowSpan > rows.size || column + colSpan > columns.size) {
                reading.error(
                    "table cell ($row, $column) span ${rowSpan}x$colSpan is out of the " +
                        "${rows.size}x${columns.size} grid of node '$nodeId'",
                    item,
                )
                return@mapNotNull null
            }
            val positions = (row until row + rowSpan).flatMap { r ->
                (column until column + colSpan).map { c -> r to c }
            }
            if (positions.any { it in covered }) {
                reading.error("table cell ($row, $column) overlaps another cell on node '$nodeId'", item)
                return@mapNotNull null
            }
            covered += positions
            TableCell(
                row = row,
                column = column,
                rowSpan = rowSpan,
                colSpan = colSpan,
                label = readLabelValue(cellMap.entries["label"], reading),
                style = readStyle(cellMap.entries["style"], reading),
            )
        }.orEmpty()

        return TableNode(rows = rows, columns = columns, cells = cells)
    }

    /** `"+ text"` symbol shorthand or a `{ text, visibility, static, abstract }` map. */
    private fun readMembers(value: YamlValue?, reading: BlockReading): List<UmlMember> {
        val list = value?.asListOrNull() ?: return emptyList()
        return list.items.mapNotNull { item ->
            when (item) {
                is YamlScalar -> item.scalarText()?.let(::parseMemberShorthand)
                is YamlMap -> {
                    val text = item.stringAt("text") ?: run {
                        reading.error("uml member map is missing `text`", item)
                        return@mapNotNull null
                    }
                    UmlMember(
                        text = text,
                        visibility = item.enumAt<UmlVisibility>("visibility", reading)
                            ?: UmlVisibility.PUBLIC,
                        static = item.boolAt("static") ?: false,
                        abstract = item.boolAt("abstract") ?: false,
                    )
                }
                else -> {
                    reading.error("uml member must be a string or a map", item)
                    null
                }
            }
        }
    }

    private fun parseMemberShorthand(text: String): UmlMember {
        val trimmed = text.trim()
        val visibility = trimmed.firstOrNull()?.let { symbol ->
            UmlVisibility.entries.firstOrNull { it.symbol == symbol }
        }
        return if (visibility != null && trimmed.length > 1 && trimmed[1] == ' ') {
            UmlMember(text = trimmed.substring(2).trim(), visibility = visibility)
        } else {
            UmlMember(text = trimmed)
        }
    }

    private fun readActivations(
        value: YamlValue?,
        nodeId: String,
        reading: BlockReading,
    ): List<UmlActivation> {
        val list = value?.asListOrNull() ?: return emptyList()
        return list.items.mapNotNull { item ->
            val pair = when (item) {
                is YamlList -> {
                    val numbers = item.items.mapNotNull { (it as? YamlScalar)?.value as? Double }
                    if (numbers.size == 2) numbers[0] to numbers[1] else null
                }
                is YamlMap -> {
                    val start = item.numberAt("start")
                    val end = item.numberAt("end")
                    if (start != null && end != null) start to end else null
                }
                else -> null
            } ?: run {
                reading.error("lifeline activation on '$nodeId' must be [start, end]", item)
                return@mapNotNull null
            }
            val start = pair.first.coerceIn(0.0, 1.0)
            val end = pair.second.coerceIn(0.0, 1.0)
            if (start != pair.first || end != pair.second || start > end) {
                reading.warning("lifeline activation on '$nodeId' coerced into 0..1", item)
            }
            UmlActivation(start = minOf(start, end), end = maxOf(start, end))
        }
    }

    private fun readPorts(value: YamlValue?, nodeId: String, reading: BlockReading): List<DiagramPort> {
        val list = value?.asListOrNull() ?: return emptyList()
        val seen = mutableSetOf<String>()
        return list.items.mapNotNull { item ->
            val map = item.asMap(reading, "diagram port") ?: return@mapNotNull null
            val id = map.requiredString("id", reading, "diagram port") ?: return@mapNotNull null
            if (!seen.add(id)) {
                reading.error("duplicate port id '$id' on node '$nodeId'", item)
                return@mapNotNull null
            }
            val anchor = readPortAnchor(map, id, nodeId, reading, item) ?: return@mapNotNull null
            DiagramPort(id = DiagramPortId(id), anchor = anchor)
        }
    }

    private fun readPortAnchor(
        map: YamlMap,
        portId: String,
        nodeId: String,
        reading: BlockReading,
        at: YamlValue,
    ): DiagramPortAnchor? {
        val atValue = map.entries["at"]
        if (atValue != null) {
            val numbers = (atValue as? YamlList)?.items
                ?.mapNotNull { (it as? YamlScalar)?.value as? Double }
            if (numbers?.size != 2) {
                reading.error("port '$portId' `at` must be [x, y]", atValue)
                return null
            }
            return DiagramPortAnchor.RelativePoint(x = numbers[0], y = numbers[1])
        }
        val side = map.enumAt<DiagramNodeSide>("side", reading) ?: run {
            reading.error("port '$portId' on node '$nodeId' needs `side` or `at`", at)
            return null
        }
        val rawOffset = map.numberAt("offset") ?: 0.5
        val offset = rawOffset.coerceIn(0.0, 1.0)
        if (offset != rawOffset) {
            reading.warning("port '$portId' offset coerced into 0..1", at)
        }
        return DiagramPortAnchor.SideOffset(side = side, offset = offset)
    }

    private fun readNodeLabels(map: YamlMap, reading: BlockReading): List<DiagramLabel> = buildList {
        readLabelValue(map.entries["label"], reading)?.let(::add)
        map.entries["labels"]?.asListOrNull()?.items?.forEach { item ->
            readLabelValue(item, reading)?.let(::add)
        }
    }

    /** Scalar text or `{ text, markdown }`; null when absent or unreadable. */
    private fun readLabelValue(value: YamlValue?, reading: BlockReading): DiagramLabel? =
        when (value) {
            null -> null
            is YamlScalar -> value.scalarText()?.let { DiagramLabel(text = it) }
            is YamlMap -> {
                val text = value.stringAt("text") ?: run {
                    reading.error("label map is missing `text`", value)
                    return null
                }
                DiagramLabel(text = text, markdown = value.boolAt("markdown") ?: false)
            }
            else -> {
                reading.error("label must be a string or a map", value)
                null
            }
        }

    // --- edges ---

    private fun readEdge(value: YamlValue, reading: BlockReading): Pair<String, DiagramEdge>? {
        val map = value.asMap(reading, "diagram edge") ?: return null
        val id = map.requiredString("id", reading, "diagram edge") ?: return null
        val source = readEndpoint(map.entries["from"], id, "from", reading, value) ?: return null
        val target = readEndpoint(map.entries["to"], id, "to", reading, value) ?: return null

        val edge = DiagramEdge(
            id = DiagramEdgeId(id),
            source = source,
            target = target,
            relation = readRelation(map.entries["relation"], id, reading),
            routing = map.enumAt<DiagramRoutingStyle>("routing", reading)
                ?: DiagramRoutingStyle.ORTHOGONAL,
            waypoints = readWaypoints(map.entries["waypoints"], id, reading),
            style = readStyle(map.entries["style"], reading) ?: DiagramStyle.Default,
            labels = readEdgeLabels(map, id, reading),
            sourceArrowhead = readArrowheadEnd(map, "source", reading),
            targetArrowhead = readArrowheadEnd(map, "target", reading),
            lineJumps = map.enumAt<LineJumpStyle>("lineJumps", reading) ?: LineJumpStyle.NONE,
            connectionMode = map.enumAt<DiagramConnectionMode>("mode", reading)
                ?: DiagramConnectionMode.LINE,
            flowAnimation = map.boolAt("animated") ?: false,
            layerId = map.stringAt("layer")?.let(::DiagramLayerId),
        )
        return id to edge
    }

    /**
     * Endpoint forms: `nodeId` (floating), `nodeId.portId` (fixed, split at the last dot),
     * `[x, y]` (free point), `{ node: ... }`, `{ node: ..., port: ... }`, `{ x: ..., y: ... }`.
     */
    private fun readEndpoint(
        value: YamlValue?,
        edgeId: String,
        key: String,
        reading: BlockReading,
        at: YamlValue,
    ): DiagramEndpoint? {
        when (value) {
            null -> {
                reading.error("diagram edge '$edgeId' is missing `$key`", at)
                return null
            }
            is YamlScalar -> {
                val text = value.scalarText() ?: run {
                    reading.error("diagram edge '$edgeId' `$key` must reference a node", value)
                    return null
                }
                val dot = text.lastIndexOf('.')
                return if (dot > 0 && dot < text.length - 1) {
                    DiagramEndpoint.FixedPort(
                        nodeId = DiagramNodeId(text.substring(0, dot)),
                        portId = DiagramPortId(text.substring(dot + 1)),
                    )
                } else {
                    DiagramEndpoint.FloatingAnchor(DiagramNodeId(text))
                }
            }
            is YamlList -> {
                val numbers = value.items.mapNotNull { (it as? YamlScalar)?.value as? Double }
                if (numbers.size != 2) {
                    reading.error("diagram edge '$edgeId' free endpoint must be [x, y]", value)
                    return null
                }
                return DiagramEndpoint.FreePoint(x = numbers[0], y = numbers[1])
            }
            is YamlMap -> {
                val node = value.stringAt("node")
                if (node != null) {
                    val port = value.stringAt("port")
                    return if (port != null) {
                        DiagramEndpoint.FixedPort(DiagramNodeId(node), DiagramPortId(port))
                    } else {
                        DiagramEndpoint.FloatingAnchor(DiagramNodeId(node))
                    }
                }
                val x = value.numberAt("x")
                val y = value.numberAt("y")
                if (x != null && y != null) return DiagramEndpoint.FreePoint(x = x, y = y)
                reading.error(
                    "diagram edge '$edgeId' `$key` map needs `node` (+ optional `port`) or `x`/`y`",
                    value,
                )
                return null
            }
        }
    }

    private fun readRelation(value: YamlValue?, edgeId: String, reading: BlockReading): DiagramRelation {
        when (value) {
            null -> return DiagramRelation.Plain
            is YamlScalar -> {
                val token = value.scalarText()?.trim()?.lowercase()?.replace('-', '_')
                scalarRelation(token)?.let { return it }
                if (token == "message") {
                    reading.error("relation `message` needs a map with `kind` (sync/async/...)", value)
                    return DiagramRelation.Plain
                }
                reading.error("unknown relation '$token' on edge '$edgeId'", value)
                return DiagramRelation.Plain
            }
            is YamlMap -> {
                val type = value.stringAt("type")?.trim()?.lowercase()?.replace('-', '_')
                return when (type) {
                    "association" -> DiagramRelation.Association(
                        directed = value.boolAt("directed") ?: false,
                    )
                    "message" -> {
                        val kind = value.enumAt<UmlMessageKind>("kind", reading) ?: run {
                            reading.error("relation `message` on edge '$edgeId' needs a valid `kind`", value)
                            return DiagramRelation.Plain
                        }
                        DiagramRelation.Message(kind)
                    }
                    "er", "entity_relation" -> DiagramRelation.EntityRelation(
                        sourceCardinality = value.enumAt<ErCardinality>("source", reading)
                            ?: ErCardinality.ONE,
                        targetCardinality = value.enumAt<ErCardinality>("target", reading)
                            ?: ErCardinality.MANY,
                    )
                    else -> {
                        scalarRelation(type)?.let { return it }
                        reading.error("unknown relation type '$type' on edge '$edgeId'", value)
                        DiagramRelation.Plain
                    }
                }
            }
            else -> {
                reading.error("relation must be a token or a map", value)
                return DiagramRelation.Plain
            }
        }
    }

    private fun scalarRelation(token: String?): DiagramRelation? = when (token) {
        "plain" -> DiagramRelation.Plain
        "association" -> DiagramRelation.Association()
        "aggregation" -> DiagramRelation.Aggregation
        "composition" -> DiagramRelation.Composition
        "generalization" -> DiagramRelation.Generalization
        "dependency" -> DiagramRelation.Dependency
        "realization" -> DiagramRelation.Realization
        "transition" -> DiagramRelation.Transition
        "include" -> DiagramRelation.Include
        "extend" -> DiagramRelation.Extend
        "er", "entity_relation" -> DiagramRelation.EntityRelation()
        else -> null
    }

    private fun readWaypoints(value: YamlValue?, edgeId: String, reading: BlockReading): List<DiagramPoint> {
        val list = value?.asListOrNull() ?: return emptyList()
        return list.items.mapNotNull { item ->
            val numbers = when (item) {
                is YamlList -> item.items.mapNotNull { (it as? YamlScalar)?.value as? Double }
                is YamlMap -> listOfNotNull(item.numberAt("x"), item.numberAt("y"))
                else -> emptyList()
            }
            if (numbers.size != 2) {
                reading.error("waypoint on edge '$edgeId' must be [x, y]", item)
                null
            } else {
                DiagramPoint(x = numbers[0], y = numbers[1])
            }
        }
    }

    private fun readEdgeLabels(map: YamlMap, edgeId: String, reading: BlockReading): List<DiagramEdgeLabel> {
        val result = mutableListOf<DiagramEdgeLabel>()
        readLabelValue(map.entries["label"], reading)?.let {
            result += DiagramEdgeLabel(label = it)
        }
        map.entries["labels"]?.asListOrNull()?.items?.forEach { item ->
            when (item) {
                is YamlScalar -> item.scalarText()?.let {
                    result += DiagramEdgeLabel(label = DiagramLabel(it))
                }
                is YamlMap -> {
                    val text = item.stringAt("text") ?: run {
                        reading.error("edge label on '$edgeId' is missing `text`", item)
                        return@forEach
                    }
                    result += DiagramEdgeLabel(
                        label = DiagramLabel(
                            text = text,
                            markdown = item.boolAt("markdown") ?: false,
                        ),
                        position = item.enumAt<DiagramEdgeLabelPosition>("position", reading)
                            ?: DiagramEdgeLabelPosition.MIDDLE,
                        offsetX = item.numberAt("dx") ?: 0.0,
                        offsetY = item.numberAt("dy") ?: 0.0,
                    )
                }
                else -> reading.error("edge label must be a string or a map", item)
            }
        }
        // The model allows at most one label per position and three total.
        val seen = mutableSetOf<DiagramEdgeLabelPosition>()
        return result.filter { label ->
            val kept = seen.add(label.position)
            if (!kept) {
                reading.warning(
                    "edge '$edgeId' has multiple labels at ${label.position.slmToken()}, keeping the first",
                    map,
                )
            }
            kept
        }
    }

    private fun readArrowheadEnd(map: YamlMap, end: String, reading: BlockReading): DiagramArrowhead {
        val arrowheads = map.entries["arrowheads"] as? YamlMap ?: return DiagramArrowhead.None
        val value = arrowheads.entries[end] ?: return DiagramArrowhead.None
        return when (value) {
            is YamlScalar -> {
                val kind = value.scalarText()?.let { enumFromToken<DiagramArrowheadKind>(it) }
                if (kind == null) {
                    reading.error("unknown arrowhead kind '${value.scalarText()}'", value)
                    DiagramArrowhead.None
                } else {
                    DiagramArrowhead(kind = kind)
                }
            }
            is YamlMap -> DiagramArrowhead(
                kind = value.enumAt<DiagramArrowheadKind>("kind", reading)
                    ?: DiagramArrowheadKind.NONE,
                size = maxOf(value.numberAt("size") ?: 8.0, 0.0),
                inset = value.numberAt("inset") ?: 0.0,
            )
            else -> {
                reading.error("arrowhead must be a kind token or a map", value)
                DiagramArrowhead.None
            }
        }
    }

    // --- style ---

    /** Null when absent; unknown/invalid properties fall back to defaults with diagnostics. */
    private fun readStyle(value: YamlValue?, reading: BlockReading): DiagramStyle? {
        if (value == null) return null
        val map = value as? YamlMap ?: run {
            reading.error("style must be a map", value)
            return null
        }
        val rawOpacity = map.numberAt("opacity") ?: 1.0
        val opacity = rawOpacity.coerceIn(0.0, 1.0)
        if (opacity != rawOpacity) reading.warning("style opacity coerced into 0..1", value)
        return DiagramStyle(
            fill = map.colorAt("fill", reading),
            stroke = map.colorAt("stroke", reading),
            strokeWidth = maxOf(map.numberAt("strokeWidth") ?: 1.0, 0.0),
            pattern = map.enumAt<DiagramStrokePattern>("pattern", reading)
                ?: DiagramStrokePattern.SOLID,
            opacity = opacity,
            cornerStyle = map.enumAt<DiagramCornerStyle>("corners", reading)
                ?: DiagramCornerStyle.SHARP,
            sketch = map.boolAt("sketch") ?: false,
            shadow = map.boolAt("shadow") ?: false,
        )
    }

    // --- small YAML access helpers ---

    private fun YamlValue.asMap(reading: BlockReading, what: String): YamlMap? =
        this as? YamlMap ?: run {
            reading.error("$what must be a map", this)
            null
        }

    private fun YamlValue.asListOrNull(): YamlList? = this as? YamlList

    private fun YamlValue.scalarText(): String? {
        val scalar = this as? YamlScalar ?: return null
        return when (val raw = scalar.value) {
            is String -> raw
            is Double -> yamlNumber(raw)
            is Boolean -> raw.toString()
            else -> null
        }
    }

    private fun YamlMap.stringAt(key: String): String? = entries[key]?.scalarText()

    private fun YamlMap.numberAt(key: String): Double? =
        (entries[key] as? YamlScalar)?.value as? Double

    private fun YamlMap.intAt(key: String): Int? = numberAt(key)?.toInt()

    private fun YamlMap.boolAt(key: String): Boolean? =
        (entries[key] as? YamlScalar)?.value as? Boolean

    private fun YamlMap.requiredString(key: String, reading: BlockReading, what: String): String? =
        stringAt(key) ?: run {
            reading.error("$what is missing `$key`", this)
            null
        }

    private inline fun <reified E : Enum<E>> YamlMap.enumAt(
        key: String,
        reading: BlockReading,
    ): E? {
        val value = entries[key] ?: return null
        val token = value.scalarText() ?: run {
            reading.error("`$key` must be a token", value)
            return null
        }
        return enumFromToken<E>(token) ?: run {
            reading.error("unknown `$key` value '$token'", value)
            null
        }
    }

    private inline fun <reified E : Enum<E>> YamlMap.requiredEnum(
        key: String,
        nodeId: String,
        reading: BlockReading,
        at: YamlValue?,
    ): E? {
        val value = entries[key] ?: run {
            reading.error("diagram node '$nodeId' is missing `$key`", at ?: this)
            return null
        }
        val token = value.scalarText() ?: run {
            reading.error("`$key` on node '$nodeId' must be a token", value)
            return null
        }
        return enumFromToken<E>(token) ?: run {
            reading.error("unknown `$key` value '$token' on node '$nodeId'", value)
            null
        }
    }

    private fun YamlMap.colorAt(key: String, reading: BlockReading): DiagramColor? {
        val value = entries[key] ?: return null
        val text = value.scalarText() ?: run {
            reading.error("`$key` must be a #AARRGGBB color string", value)
            return null
        }
        return parseDiagramColor(text) ?: run {
            reading.error("malformed color '$text' under `$key` (expected #RRGGBB or #AARRGGBB)", value)
            null
        }
    }
}
