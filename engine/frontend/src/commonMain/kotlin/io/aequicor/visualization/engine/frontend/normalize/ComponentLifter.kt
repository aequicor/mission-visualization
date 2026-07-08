package io.aequicor.visualization.engine.frontend.normalize

import io.aequicor.visualization.engine.frontend.blocks.ComponentPatch
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignComponent
import io.aequicor.visualization.engine.ir.model.DesignComponentSet
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.bindable

/**
 * Lifts `Component:`-marked subtrees into `document.components` and their variant
 * declarations into `document.componentSets`, then validates instance refs: local
 * refs resolve against lifted definitions (by id or declared name); refs containing
 * `/` are library refs validated at a later stage; anything else is an error.
 *
 * Component sets (Figma-faithful): a definition participates in a set when it
 * declares `component.variants` axes, `component.variant` values (the combination
 * this definition IS), or an explicit `component.set` id. Definitions group by the
 * explicit set id first, else by shared `component.name`; the set id is the explicit
 * one, else `"<firstDefinitionId>Set"`. The set's variants map uses the IR's
 * canonical key format (`axis=value,...` over ALL axes, unspecified axes filled with
 * the axis default — its first value). A group where no member declares `variant:`
 * values registers its first member as the sole default-combination fallback and
 * reports an info diagnostic. Instance refs by shared name rewrite to the SET id
 * when the group is variant-bearing (any `variant:` values or >1 member), else to
 * the component id.
 */
class ComponentLifter(private val diagnostics: DiagnosticCollector) {
    private class SetMember(
        val componentId: String,
        val declaredAxes: Map<String, List<String>>,
        val variantValues: Map<String, String>,
        val line: Int,
    )

    private class SetGroup(val setId: String, var name: String) {
        val members = mutableListOf<SetMember>()

        /** True once the set can distinguish variants (explicit values or siblings). */
        val isVariantBearing: Boolean
            get() = members.size > 1 || members.any { it.variantValues.isNotEmpty() }
    }

    private val componentsById = LinkedHashMap<String, DesignComponent>()
    private val componentSetsById = LinkedHashMap<String, DesignComponentSet>()
    private val idByName = mutableMapOf<String, String>()
    private val groupsByKey = LinkedHashMap<String, SetGroup>()
    private val groupsBySetId = mutableMapOf<String, SetGroup>()
    private val setIdByName = mutableMapOf<String, String>()
    private var defaultOnlySetsReported = false

    val components: Map<String, DesignComponent> get() = componentsById
    val componentSets: Map<String, DesignComponentSet> get() = componentSetsById

    /** Registers one materialized component definition root. */
    fun register(defRoot: DesignNode, patch: ComponentPatch?, line: Int) {
        if (defRoot.id in componentsById) {
            diagnostics.error("Duplicate component definition \"${defRoot.id}\"", line)
            return
        }
        val name = patch?.name ?: defRoot.name
        componentsById[defRoot.id] = DesignComponent(
            name = name,
            properties = patch?.properties ?: emptyMap(),
            root = defRoot,
        )
        if (name.isNotBlank()) idByName[name] = defRoot.id
        registerSetMember(defRoot.id, name, patch, line)
    }

    private fun registerSetMember(
        componentId: String,
        name: String,
        patch: ComponentPatch?,
        line: Int,
    ) {
        val axes = patch?.variantsAxes.orEmpty()
        val variantValues = patch?.variant.orEmpty()
        val explicitSetId = patch?.set
        if (axes.isEmpty() && variantValues.isEmpty() && explicitSetId == null) return

        val groupKey = when {
            explicitSetId != null -> "set:$explicitSetId"
            name.isNotBlank() -> "name:$name"
            else -> "id:$componentId"
        }
        val group = groupsByKey.getOrPut(groupKey) {
            SetGroup(setId = explicitSetId ?: "${componentId}Set", name = name)
        }
        val owner = groupsBySetId[group.setId]
        if (owner == null) {
            groupsBySetId[group.setId] = group
        } else if (owner !== group) {
            diagnostics.error(
                "Component set id \"${group.setId}\" is already used by another set",
                line,
            )
        }
        if (group.name.isBlank()) group.name = name
        val member = SetMember(componentId, axes, variantValues, line)
        group.members += member
        rebuildSet(group, newMember = member)
        if (group.isVariantBearing) {
            group.members
                .mapNotNull { componentsById[it.componentId]?.name }
                .filter { it.isNotBlank() }
                .forEach { memberName -> setIdByName[memberName] = group.setId }
        }
    }

    /** Recomputes a group's [DesignComponentSet]; only [newMember] reports collisions. */
    private fun rebuildSet(group: SetGroup, newMember: SetMember?) {
        // Axes: declared axes first (declaration order), observed `variant:` values
        // appended; the first value of an axis is its default.
        val axes = LinkedHashMap<String, MutableList<String>>()
        group.members.forEach { member ->
            member.declaredAxes.forEach { (axis, values) ->
                val list = axes.getOrPut(axis) { mutableListOf() }
                values.forEach { value -> if (value !in list) list += value }
            }
        }
        group.members.forEach { member ->
            member.variantValues.forEach { (axis, value) ->
                val list = axes.getOrPut(axis) { mutableListOf() }
                if (value !in list) list += value
            }
        }
        val variants = LinkedHashMap<String, String>()
        group.members.filter { it.variantValues.isNotEmpty() }.forEach { member ->
            val key = variantKey(axes) { axis -> member.variantValues[axis] }
            val existing = variants[key]
            when {
                existing == null -> variants[key] = member.componentId
                member === newMember -> diagnostics.error(
                    "Component set \"${group.setId}\" already defines variant \"$key\" " +
                        "(component \"$existing\")",
                    member.line,
                )
            }
        }
        if (variants.isEmpty()) {
            group.members.firstOrNull()?.let { sole ->
                variants[variantKey(axes) { null }] = sole.componentId
            }
        }
        componentSetsById[group.setId] = DesignComponentSet(
            name = group.name.ifBlank { group.setId },
            axes = axes.mapValues { (_, values) -> values.toList() },
            variants = variants,
        )
    }

    /** The IR's canonical variant key: `axis=value` pairs joined by `,` in axes order. */
    private fun variantKey(
        axes: Map<String, List<String>>,
        valueFor: (String) -> String?,
    ): String = axes.entries.joinToString(",") { (axis, values) ->
        "$axis=${valueFor(axis) ?: values.first()}"
    }

    /** Once per run: flags sets whose only variant is the auto-registered default. */
    private fun reportDefaultOnlySets() {
        if (defaultOnlySetsReported) return
        defaultOnlySetsReported = true
        groupsByKey.values.forEach { group ->
            if (group.members.any { it.variantValues.isNotEmpty() }) return@forEach
            val set = componentSetsById[group.setId] ?: return@forEach
            diagnostics.info(
                "Component set \"${group.setId}\" defines only the default variant " +
                    "(\"${set.variants.keys.first()}\"); add sibling definitions with " +
                    "`component.variant` values to cover other axis combinations",
                group.members.first().line,
            )
        }
    }

    /**
     * Validates every instance in [root]; local refs matching a definition NAME are
     * rewritten to the definition id (or its component-set id when the set is
     * variant-bearing). Returns the (possibly rewritten) tree.
     */
    fun resolveInstances(root: DesignNode): DesignNode {
        reportDefaultOnlySets()
        val kind = root.kind
        val resolvedNode = if (kind is DesignNodeKind.Instance) {
            resolveInstance(root, kind)
        } else {
            root
        }
        var changed = resolvedNode !== root
        val children = resolvedNode.children.map { child ->
            val next = resolveInstances(child)
            if (next !== child) changed = true
            next
        }
        return if (changed) resolvedNode.copy(children = children) else resolvedNode
    }

    private fun resolveInstance(node: DesignNode, kind: DesignNodeKind.Instance): DesignNode {
        val ref = (kind.componentId as? Bindable.Value)?.value ?: return node
        val line = node.sourceMap?.line ?: 0
        return when {
            ref.isEmpty() -> {
                diagnostics.error("Instance \"${node.id}\" has no component ref", line)
                node
            }
            ref in componentsById -> node
            ref in componentSetsById -> node // the resolver selects the variant
            setIdByName.containsKey(ref) -> node.copy(
                kind = kind.copy(componentId = setIdByName.getValue(ref).bindable()),
            )
            idByName.containsKey(ref) -> node.copy(
                kind = kind.copy(componentId = idByName.getValue(ref).bindable()),
            )
            "/" in ref -> node // library ref, validated when libraries are resolved
            else -> {
                diagnostics.error(
                    "Unresolved local component ref \"$ref\" on instance \"${node.id}\"",
                    line,
                )
                node
            }
        }
    }
}
