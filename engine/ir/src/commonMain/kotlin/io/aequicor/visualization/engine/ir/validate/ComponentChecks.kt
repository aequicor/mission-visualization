package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.ComponentPropertyDefinition
import io.aequicor.visualization.engine.ir.model.ComponentPropertyType
import io.aequicor.visualization.engine.ir.model.DesignComponent
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.PropValue

/**
 * IR-COMP — components, libraries, variants, props, slots, overrides.
 *
 * - IR-COMP-001 (error): instance references an unknown component/component set.
 * - IR-COMP-002 (error): instance references an undeclared library.
 * - IR-COMP-003 (error): variant value outside the component set's axes.
 * - IR-COMP-004 (error): prop value type does not match the declared property.
 * - IR-COMP-005 (warning): prop that the component does not declare.
 * - IR-COMP-006 (error): slot content outside the declared min/maxItems.
 * - IR-COMP-007 (warning): slot node not allowed by the slot's allowedContent.
 * - IR-COMP-008 (error): override target path that does not exist in the component tree.
 * - IR-COMP-009 (error): exposedInstances path that does not exist.
 * - IR-COMP-010 (error): resetOverrides drops a required (no-default) prop the
 *   instance does not provide.
 * - IR-COMP-011 (error): static component recursion cycle.
 *
 * Library-hosted components are validated when the library document is provided via
 * [io.aequicor.visualization.engine.ir.resolve.ResolveContext.libraries]; a declared
 * but unprovided library only has its reference checked.
 */
internal object ComponentChecks {

    fun check(ctx: ValidationContext): List<DesignDiagnostic> = buildList {
        ctx.entries.forEach { entry ->
            val instance = entry.node.kind as? DesignNodeKind.Instance ?: return@forEach
            checkInstance(this, ctx, entry.node, instance)
        }
        ctx.document.components.forEach { (componentId, component) ->
            checkExposedInstances(this, ctx, componentId, component)
        }
        checkRecursion(this, ctx)
    }

    private fun checkInstance(
        sink: MutableList<DesignDiagnostic>,
        ctx: ValidationContext,
        node: DesignNode,
        instance: DesignNodeKind.Instance,
    ) {
        val requestedId = instance.literalComponentId() ?: return // dynamic refs resolve at runtime

        // Mirror the resolver's library routing: explicit libraryRef, else a
        // "<libId>/" componentId prefix known to the context's registry.
        var libraryId = ""
        var lookupId = requestedId
        if (instance.libraryRef.isNotEmpty()) {
            libraryId = instance.libraryRef
        } else {
            val prefix = requestedId.substringBefore('/')
            if (prefix != requestedId && prefix in ctx.resolveContext.libraries) {
                libraryId = prefix
                lookupId = requestedId.substringAfter('/')
            }
        }
        if (libraryId.isNotEmpty() && libraryId !in ctx.libraryIds) {
            sink += validationError(
                "IR-COMP-002",
                "Instance '${node.id}' references undeclared library '$libraryId'",
                ctx.location(node),
            )
            return
        }
        val host = if (libraryId.isEmpty()) {
            ctx.document
        } else {
            ctx.resolveContext.libraries[libraryId] ?: return // declared but not provided: unverifiable
        }

        val set = host.componentSets[lookupId]
        if (set != null) {
            instance.variant.forEach { (axis, value) ->
                val values = set.axes[axis]
                if (values == null || value !in values) {
                    sink += validationError(
                        "IR-COMP-003",
                        "Instance '${node.id}' selects variant $axis=$value, " +
                            "not an axis value of set '$lookupId'",
                        ctx.location(node),
                    )
                }
            }
        }
        val componentId = set?.variants?.values?.firstOrNull() ?: lookupId
        val component = host.components[componentId]
        if (set == null && component == null) {
            sink += validationError(
                "IR-COMP-001",
                "Instance '${node.id}' references unknown component '$requestedId'",
                ctx.location(node),
            )
            return
        }
        if (component != null) {
            checkProps(sink, ctx, node, instance, host, component)
            checkOverrides(sink, ctx, node, instance, host, component)
            checkRequiredProps(sink, ctx, node, instance, component)
        }
    }

    private fun checkProps(
        sink: MutableList<DesignDiagnostic>,
        ctx: ValidationContext,
        node: DesignNode,
        instance: DesignNodeKind.Instance,
        host: DesignDocument,
        component: DesignComponent,
    ) {
        instance.props.forEach { (name, value) ->
            val definition = component.properties[name]
            if (definition == null) {
                sink += validationWarning(
                    "IR-COMP-005",
                    "Instance '${node.id}' passes prop '$name' that component " +
                        "'${component.name.ifEmpty { "?" }}' does not declare",
                    ctx.location(node),
                )
                return@forEach
            }
            if (!typeMatches(definition.type, value)) {
                sink += validationError(
                    "IR-COMP-004",
                    "Prop '$name' of instance '${node.id}' is ${value::class.simpleName}, " +
                        "which does not match the declared ${definition.type} property",
                    ctx.location(node),
                )
            }
            if (definition.type == ComponentPropertyType.Slot && value is PropValue.SlotContent) {
                checkSlotContent(sink, ctx, node, name, definition, value)
            }
        }
    }

    private fun typeMatches(type: ComponentPropertyType, value: PropValue): Boolean =
        when (type) {
            ComponentPropertyType.Text -> value is PropValue.Text || value is PropValue.Content
            ComponentPropertyType.RawString -> value is PropValue.Text
            ComponentPropertyType.Boolean -> value is PropValue.Bool
            ComponentPropertyType.Number -> value is PropValue.Number
            ComponentPropertyType.InstanceSwap -> value is PropValue.Reference || value is PropValue.Text
            ComponentPropertyType.Variant -> value is PropValue.Reference || value is PropValue.Text
            ComponentPropertyType.Slot -> value is PropValue.SlotContent
            ComponentPropertyType.DataBinding -> value is PropValue.Data
        }

    private fun checkSlotContent(
        sink: MutableList<DesignDiagnostic>,
        ctx: ValidationContext,
        node: DesignNode,
        name: String,
        definition: ComponentPropertyDefinition,
        value: PropValue.SlotContent,
    ) {
        val count = value.nodes.size
        val min = definition.minItems
        val max = definition.maxItems
        if ((min != null && count < min) || (max != null && count > max)) {
            sink += validationError(
                "IR-COMP-006",
                "Slot '$name' of instance '${node.id}' has $count item(s); " +
                    "allowed ${min ?: 0}..${max?.toString() ?: "*"}",
                ctx.location(node),
            )
        }
        if (definition.allowedContent.isNotEmpty()) {
            value.nodes.forEach { slotNode ->
                val ref = (slotNode.kind as? DesignNodeKind.Instance)?.literalComponentId()
                if (ref == null || ref !in definition.allowedContent) {
                    sink += validationWarning(
                        "IR-COMP-007",
                        "Slot '$name' of instance '${node.id}' contains " +
                            "'${slotNode.id}', not one of ${definition.allowedContent}",
                        ctx.location(node),
                    )
                }
            }
        }
    }

    private fun checkOverrides(
        sink: MutableList<DesignDiagnostic>,
        ctx: ValidationContext,
        node: DesignNode,
        instance: DesignNodeKind.Instance,
        host: DesignDocument,
        component: DesignComponent,
    ) {
        instance.overrides.forEach { override ->
            if (!pathExists(host, ctx, component.root, override.target)) {
                sink += validationError(
                    "IR-COMP-008",
                    "Override target ${override.target} of instance '${node.id}' " +
                        "does not exist in the component tree",
                    ctx.location(node),
                )
            }
        }
    }

    private fun checkExposedInstances(
        sink: MutableList<DesignDiagnostic>,
        ctx: ValidationContext,
        componentId: String,
        component: DesignComponent,
    ) {
        component.exposedInstances.forEach { path ->
            if (!pathExists(ctx.document, ctx, component.root, path)) {
                sink += validationError(
                    "IR-COMP-009",
                    "exposedInstances path $path of component '$componentId' does not exist",
                    ctx.location(component.root),
                )
            }
        }
    }

    /**
     * Walks an id path: the first segment addresses a node inside [root]'s tree;
     * when that node is an instance and segments remain, the walk continues inside
     * its component (dynamic or unresolvable references end the walk permissively).
     */
    private fun pathExists(
        host: DesignDocument,
        ctx: ValidationContext,
        root: DesignNode,
        path: List<String>,
    ): Boolean {
        if (path.isEmpty()) return false
        val target = root.findById(path.first()) ?: return false
        if (path.size == 1) return true
        val instance = target.kind as? DesignNodeKind.Instance ?: return false
        val componentId = instance.literalComponentId() ?: return true
        val innerHost = when {
            instance.libraryRef.isNotEmpty() -> ctx.resolveContext.libraries[instance.libraryRef] ?: return true
            else -> host
        }
        val resolvedId = innerHost.componentSets[componentId]?.variants?.values?.firstOrNull() ?: componentId
        val component = innerHost.components[resolvedId] ?: return true
        return pathExists(innerHost, ctx, component.root, path.drop(1))
    }

    private fun checkRequiredProps(
        sink: MutableList<DesignDiagnostic>,
        ctx: ValidationContext,
        node: DesignNode,
        instance: DesignNodeKind.Instance,
        component: DesignComponent,
    ) {
        if (!instance.resetOverrides) return
        component.properties.forEach { (name, definition) ->
            val required = definition.default == null && definition.type != ComponentPropertyType.Variant
            if (required && name !in instance.props) {
                sink += validationError(
                    "IR-COMP-010",
                    "Instance '${node.id}' resets overrides but does not provide " +
                        "required prop '$name' (no default declared)",
                    ctx.location(node),
                )
            }
        }
    }

    /** Static component graph: componentId -> component ids its tree instantiates. */
    private fun checkRecursion(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext) {
        val components = ctx.document.components
        val edges = components.mapValues { (_, component) ->
            (listOf(component.root) + component.root.allDescendants())
                .mapNotNull { (it.kind as? DesignNodeKind.Instance) }
                .filter { it.libraryRef.isEmpty() }
                .mapNotNull { instance ->
                    instance.literalComponentId()?.let { id ->
                        ctx.document.componentSets[id]?.variants?.values?.firstOrNull() ?: id
                    }
                }
        }
        val reported = mutableSetOf<String>()

        fun visit(componentId: String, stack: List<String>) {
            if (componentId in stack) {
                val cycle = stack.dropWhile { it != componentId } + componentId
                if (reported.add(cycle.toSet().sorted().joinToString("->"))) {
                    sink += validationError(
                        "IR-COMP-011",
                        "Component recursion cycle: ${cycle.joinToString(" -> ")}",
                    )
                }
                return
            }
            edges[componentId].orEmpty().forEach { visit(it, stack + componentId) }
        }
        components.keys.forEach { visit(it, emptyList()) }
    }
}
