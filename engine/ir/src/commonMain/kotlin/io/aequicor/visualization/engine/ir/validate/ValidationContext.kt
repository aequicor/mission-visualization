package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.DesignVariable
import io.aequicor.visualization.engine.ir.model.SourceLocation
import io.aequicor.visualization.engine.ir.resolve.ResolveContext

/**
 * One node occurrence during validation traversal, with enough surroundings for
 * sibling- and scope-level checks.
 */
internal class NodeEntry(
    val node: DesignNode,
    val parent: DesignNode?,
    /** Ids of the node's siblings (including itself). */
    val siblingIds: Set<String>,
    /** "" for page trees; the component id for component trees. */
    val scope: String,
)

/**
 * Shared validation state: the document, the resolve environment, and precomputed
 * indexes the check groups read.
 *
 * Traversal covers the page forest and every local component tree. Node id
 * uniqueness is scoped per tree family (the page forest is one scope, each
 * component tree its own): the resolver namespaces instance internals, so a
 * component-internal id may legitimately repeat a page id.
 */
internal class ValidationContext(
    val document: DesignDocument,
    val resolveContext: ResolveContext,
    val options: ValidationOptions,
) {
    /** Every node in the page forest and in local component trees, parents attached. */
    val entries: List<NodeEntry> = buildList {
        document.pages.forEach { page ->
            val rootSiblings = page.children.map { it.id }.toSet()
            page.children.forEach { addTree(it, parent = null, siblingIds = rootSiblings, scope = "") }
        }
        document.components.forEach { (componentId, component) ->
            addTree(component.root, parent = null, siblingIds = setOf(component.root.id), scope = componentId)
        }
    }

    /** Node ids reachable from pages — the id space interaction/handoff targets address. */
    val pageNodeIds: Set<String> =
        entries.asSequence().filter { it.scope.isEmpty() }.map { it.node.id }.toSet()

    /** All node ids, including component internals. */
    val allNodeIds: Set<String> = entries.asSequence().map { it.node.id }.toSet()

    /** varId -> (collectionId, variable) over every collection. */
    val variableIndex: Map<String, Pair<String, DesignVariable>> =
        document.variables.collections.flatMap { (collectionId, collection) ->
            collection.vars.map { (varId, variable) -> varId to (collectionId to variable) }
        }.toMap()

    val assetIds: Set<String> = document.assets.keys

    val breakpointIds: Set<String> = document.breakpoints.map { it.id }.toSet()

    val devicePresetIds: Set<String> = document.devicePresets.map { it.id }.toSet()

    /** Locales the document knows about: source, targets, and bundled resources. */
    val declaredLocales: Set<String> = buildSet {
        document.i18n.sourceLocale.takeIf { it.isNotEmpty() }?.let(::add)
        addAll(document.i18n.targetLocales)
        addAll(document.i18n.resources.keys)
        addAll(resolveContext.resources.keys)
    }

    /** Library ids usable by instances: declared in the document or provided by the context. */
    val libraryIds: Set<String> =
        document.libraries.map { it.id }.toSet() + resolveContext.libraries.keys

    /** Context resources win over document bundles, per locale and key (mirrors the resolver). */
    val mergedResources: Map<String, Map<String, String>> =
        (document.i18n.resources.keys + resolveContext.resources.keys).associateWith { locale ->
            document.i18n.resources[locale].orEmpty() + resolveContext.resources[locale].orEmpty()
        }

    /** True when any interaction anywhere opens an overlay. */
    val hasOpenOverlay: Boolean = allActions().any { it is DesignAction.OpenOverlay }

    fun allActions(): Sequence<DesignAction> =
        entries.asSequence().flatMap { entry -> entry.node.interactions.asSequence().flatMap { it.actions } } +
            document.actionSets.values.asSequence().flatten()

    fun location(node: DesignNode): SourceLocation? = node.sourceMap
}

private fun MutableList<NodeEntry>.addTree(
    node: DesignNode,
    parent: DesignNode?,
    siblingIds: Set<String>,
    scope: String,
) {
    add(NodeEntry(node, parent, siblingIds, scope))
    val childIds = node.children.map { it.id }.toSet()
    node.children.forEach { addTree(it, parent = node, siblingIds = childIds, scope = scope) }
}

internal fun validationError(
    code: String,
    message: String,
    location: SourceLocation? = null,
): DesignDiagnostic = DesignDiagnostic(DesignSeverity.Error, message, location, code)

internal fun validationWarning(
    code: String,
    message: String,
    location: SourceLocation? = null,
): DesignDiagnostic = DesignDiagnostic(DesignSeverity.Warning, message, location, code)

/** Literal component reference of an instance, or null when it is bound dynamically. */
internal fun DesignNodeKind.Instance.literalComponentId(): String? =
    (componentId as? Bindable.Value)?.value
