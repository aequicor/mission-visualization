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
 * axes into `document.componentSets`, then validates instance refs: local refs
 * resolve against lifted definitions (by id or declared name); refs containing
 * `/` are library refs validated at a later stage; anything else is an error.
 */
class ComponentLifter(private val diagnostics: DiagnosticCollector) {
    private val componentsById = LinkedHashMap<String, DesignComponent>()
    private val componentSetsById = LinkedHashMap<String, DesignComponentSet>()
    private val idByName = mutableMapOf<String, String>()

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
        val axes = patch?.variantsAxes
        if (!axes.isNullOrEmpty()) {
            componentSetsById["${defRoot.id}Set"] = DesignComponentSet(
                name = name.ifBlank { defRoot.id },
                axes = axes,
            )
        }
    }

    /**
     * Validates every instance in [root]; local refs matching a definition NAME are
     * rewritten to the definition id. Returns the (possibly rewritten) tree.
     */
    fun resolveInstances(root: DesignNode): DesignNode {
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
