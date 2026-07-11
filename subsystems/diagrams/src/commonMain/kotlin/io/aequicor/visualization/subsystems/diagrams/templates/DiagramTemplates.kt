package io.aequicor.visualization.subsystems.diagrams.templates

import io.aequicor.visualization.subsystems.diagrams.layout.autoLayout
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.ErAttribute
import io.aequicor.visualization.subsystems.diagrams.model.ErCardinality
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.FlowchartNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActorNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlComponentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlDeploymentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMember
import io.aequicor.visualization.subsystems.diagrams.model.UmlMessageKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlVisibility
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph

/** A ready-made starter diagram: stable [id], display [name], and a laid-out [graph]. */
data class DiagramTemplate(
    val id: String,
    val name: String,
    val graph: DiagramGraph,
)

/**
 * The built-in template gallery, one per major diagram family:
 * uml-class, sequence, state-machine, activity, use-case, component, deployment,
 * flowchart, er. Every graph is valid (edges reference existing nodes) and already
 * positioned via [autoLayout] (sequence lifelines are placed side by side).
 */
fun diagramTemplates(): List<DiagramTemplate> = listOf(
    umlClassTemplate(),
    sequenceTemplate(),
    stateMachineTemplate(),
    activityTemplate(),
    useCaseTemplate(),
    componentTemplate(),
    deploymentTemplate(),
    flowchartTemplate(),
    erTemplate(),
)

private fun umlClassTemplate(): DiagramTemplate {
    val graph = diagramGraph {
        val shape = node(
            id = "shape",
            width = 180.0,
            height = 96.0,
            payload = UmlClassNode(
                name = "Shape",
                abstract = true,
                attributes = listOf(UmlMember("id: String", UmlVisibility.PRIVATE)),
                operations = listOf(UmlMember("area(): Double", abstract = true)),
            ),
        )
        val circle = node(
            id = "circle",
            width = 180.0,
            height = 96.0,
            payload = UmlClassNode(
                name = "Circle",
                attributes = listOf(UmlMember("radius: Double", UmlVisibility.PRIVATE)),
                operations = listOf(UmlMember("area(): Double")),
            ),
        )
        val square = node(
            id = "square",
            width = 180.0,
            height = 96.0,
            payload = UmlClassNode(
                name = "Square",
                attributes = listOf(UmlMember("side: Double", UmlVisibility.PRIVATE)),
                operations = listOf(UmlMember("area(): Double")),
            ),
        )
        edge("circle-shape", circle, shape, relation = DiagramRelation.Generalization)
        edge("square-shape", square, shape, relation = DiagramRelation.Generalization)
    }
    return DiagramTemplate("uml-class", "UML Class", autoLayout(graph))
}

private fun sequenceTemplate(): DiagramTemplate {
    val graph = diagramGraph {
        val user = node(
            id = "user",
            x = 0.0,
            y = 0.0,
            width = 140.0,
            height = 320.0,
            payload = UmlLifelineNode("User", actor = true),
        )
        val service = node(
            id = "service",
            x = 220.0,
            y = 0.0,
            width = 140.0,
            height = 320.0,
            payload = UmlLifelineNode("Service"),
        )
        edge(
            id = "m1",
            source = DiagramEndpoint.FloatingAnchor(user),
            target = DiagramEndpoint.FloatingAnchor(service),
            relation = DiagramRelation.Message(UmlMessageKind.SYNC),
            routing = DiagramRoutingStyle.STRAIGHT,
            label = "request()",
        )
        edge(
            id = "m2",
            source = DiagramEndpoint.FloatingAnchor(service),
            target = DiagramEndpoint.FloatingAnchor(user),
            relation = DiagramRelation.Message(UmlMessageKind.RETURN),
            routing = DiagramRoutingStyle.STRAIGHT,
            label = "response",
        )
    }
    return DiagramTemplate("sequence", "UML Sequence", graph)
}

private fun stateMachineTemplate(): DiagramTemplate {
    val graph = diagramGraph {
        val initial = node(
            id = "initial",
            width = 28.0,
            height = 28.0,
            payload = UmlStateNode(kind = UmlStateKind.INITIAL),
        )
        val idle = node("idle", width = 140.0, height = 48.0, payload = UmlStateNode("Idle"))
        val running = node("running", width = 140.0, height = 48.0, payload = UmlStateNode("Running"))
        val final = node(
            id = "final",
            width = 28.0,
            height = 28.0,
            payload = UmlStateNode(kind = UmlStateKind.FINAL),
        )
        edge("t1", initial, idle, relation = DiagramRelation.Transition)
        edge("t2", idle, running, relation = DiagramRelation.Transition, label = "start")
        edge("t3", running, idle, relation = DiagramRelation.Transition, label = "stop")
        edge("t4", running, final, relation = DiagramRelation.Transition, label = "shutdown")
    }
    return DiagramTemplate("state-machine", "UML State Machine", autoLayout(graph))
}

private fun activityTemplate(): DiagramTemplate {
    val graph = diagramGraph {
        val start = node(
            id = "start",
            width = 28.0,
            height = 28.0,
            payload = UmlActivityNode(UmlActivityKind.START),
        )
        val receive = node(
            id = "receive",
            width = 150.0,
            height = 48.0,
            payload = UmlActivityNode(UmlActivityKind.ACTION, "Receive order"),
        )
        val check = node(
            id = "check",
            width = 140.0,
            height = 80.0,
            payload = UmlActivityNode(UmlActivityKind.DECISION, "Valid?"),
        )
        val ship = node(
            id = "ship",
            width = 150.0,
            height = 48.0,
            payload = UmlActivityNode(UmlActivityKind.ACTION, "Ship order"),
        )
        val reject = node(
            id = "reject",
            width = 150.0,
            height = 48.0,
            payload = UmlActivityNode(UmlActivityKind.ACTION, "Reject order"),
        )
        val end = node(
            id = "end",
            width = 28.0,
            height = 28.0,
            payload = UmlActivityNode(UmlActivityKind.END),
        )
        edge("a1", start, receive, relation = DiagramRelation.Transition)
        edge("a2", receive, check, relation = DiagramRelation.Transition)
        edge("a3", check, ship, relation = DiagramRelation.Transition, label = "yes")
        edge("a4", check, reject, relation = DiagramRelation.Transition, label = "no")
        edge("a5", ship, end, relation = DiagramRelation.Transition)
        edge("a6", reject, end, relation = DiagramRelation.Transition)
    }
    return DiagramTemplate("activity", "UML Activity", autoLayout(graph))
}

private fun useCaseTemplate(): DiagramTemplate {
    val graph = diagramGraph {
        val customer = node(
            id = "customer",
            width = 60.0,
            height = 100.0,
            payload = UmlActorNode("Customer"),
        )
        val place = node("place", width = 160.0, height = 70.0, payload = UmlUseCaseNode("Place order"))
        val track = node("track", width = 160.0, height = 70.0, payload = UmlUseCaseNode("Track order"))
        val validate = node(
            id = "validate",
            width = 170.0,
            height = 70.0,
            payload = UmlUseCaseNode("Validate payment"),
        )
        edge("u1", customer, place, relation = DiagramRelation.Association())
        edge("u2", customer, track, relation = DiagramRelation.Association())
        edge("u3", place, validate, relation = DiagramRelation.Include, label = "«include»")
    }
    return DiagramTemplate("use-case", "UML Use Case", autoLayout(graph))
}

private fun componentTemplate(): DiagramTemplate {
    val graph = diagramGraph {
        val ui = node("ui", width = 160.0, height = 80.0, payload = UmlComponentNode("Web UI"))
        val api = node("api", width = 160.0, height = 80.0, payload = UmlComponentNode("API Gateway"))
        val db = node(
            id = "db",
            width = 160.0,
            height = 80.0,
            payload = UmlComponentNode("Database", stereotype = "service"),
        )
        edge("c1", ui, api, relation = DiagramRelation.Dependency)
        edge("c2", api, db, relation = DiagramRelation.Dependency)
    }
    return DiagramTemplate("component", "UML Component", autoLayout(graph))
}

private fun deploymentTemplate(): DiagramTemplate {
    val graph = diagramGraph {
        val client = node(
            id = "client",
            width = 180.0,
            height = 90.0,
            payload = UmlDeploymentNode("Client", stereotype = "device"),
        )
        val web = node(
            id = "web",
            width = 180.0,
            height = 90.0,
            payload = UmlDeploymentNode("Web Server", stereotype = "device"),
        )
        val db = node(
            id = "db",
            width = 180.0,
            height = 90.0,
            payload = UmlDeploymentNode("DB Server", stereotype = "device"),
        )
        edge("d1", client, web, relation = DiagramRelation.Association(), label = "HTTPS")
        edge("d2", web, db, relation = DiagramRelation.Association(), label = "JDBC")
    }
    return DiagramTemplate("deployment", "UML Deployment", autoLayout(graph))
}

private fun flowchartTemplate(): DiagramTemplate {
    val graph = diagramGraph {
        val start = node(
            id = "start",
            width = 140.0,
            height = 48.0,
            payload = DiagramNodePayload.FlowchartNode(FlowchartNodeKind.TERMINATOR),
            label = "Start",
        )
        val input = node(
            id = "input",
            width = 150.0,
            height = 56.0,
            payload = DiagramNodePayload.FlowchartNode(FlowchartNodeKind.INPUT_OUTPUT),
            label = "Read input",
        )
        val process = node(
            id = "process",
            width = 150.0,
            height = 56.0,
            payload = DiagramNodePayload.FlowchartNode(FlowchartNodeKind.PROCESS),
            label = "Process data",
        )
        val decision = node(
            id = "decision",
            width = 150.0,
            height = 80.0,
            payload = DiagramNodePayload.FlowchartNode(FlowchartNodeKind.DECISION),
            label = "Valid?",
        )
        val end = node(
            id = "end",
            width = 140.0,
            height = 48.0,
            payload = DiagramNodePayload.FlowchartNode(FlowchartNodeKind.TERMINATOR),
            label = "End",
        )
        edge("f1", start, input, relation = DiagramRelation.Association(directed = true))
        edge("f2", input, process, relation = DiagramRelation.Association(directed = true))
        edge("f3", process, decision, relation = DiagramRelation.Association(directed = true))
        edge("f4", decision, end, relation = DiagramRelation.Association(directed = true), label = "yes")
        edge("f5", decision, process, relation = DiagramRelation.Association(directed = true), label = "no")
    }
    return DiagramTemplate("flowchart", "Flowchart", autoLayout(graph))
}

private fun erTemplate(): DiagramTemplate {
    val graph = diagramGraph {
        val customer = node(
            id = "customer",
            width = 180.0,
            height = 100.0,
            payload = DiagramNodePayload.ErEntityNode(
                name = "Customer",
                attributes = listOf(
                    ErAttribute("id", type = "Int", primaryKey = true),
                    ErAttribute("name", type = "String"),
                ),
            ),
        )
        val order = node(
            id = "order",
            width = 180.0,
            height = 100.0,
            payload = DiagramNodePayload.ErEntityNode(
                name = "Order",
                attributes = listOf(
                    ErAttribute("id", type = "Int", primaryKey = true),
                    ErAttribute("customer_id", type = "Int", foreignKey = true),
                ),
            ),
        )
        edge(
            id = "r1",
            source = DiagramEndpoint.FloatingAnchor(customer),
            target = DiagramEndpoint.FloatingAnchor(order),
            relation = DiagramRelation.EntityRelation(
                sourceCardinality = ErCardinality.ONE,
                targetCardinality = ErCardinality.ZERO_OR_MANY,
            ),
            routing = DiagramRoutingStyle.ENTITY_RELATION,
            label = "places",
        )
    }
    return DiagramTemplate("er", "Entity-Relationship", autoLayout(graph))
}
