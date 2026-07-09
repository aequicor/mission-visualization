package io.aequicor.visualization.engine.ir.projection

import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode

/**
 * Pure resolver from a navigation target / screen id to the screen-root frame node.
 *
 * After [io.aequicor.visualization.engine.ir.model.DesignDocument] merge a page's id
 * equals its screen-meta id, so `DesignAction.Navigate(to = "missionTelemetry")` maps
 * to the first child of the page with that id. This index tolerates three target
 * spellings, mirroring the IR's forward-compatible "unknown falls back" philosophy:
 *
 * 1. a page id (== screen-meta id post-merge) → that page's root frame;
 * 2. the document's own screen-meta id → the first page's root frame;
 * 3. an arbitrary node id → that node (a caller may target a specific frame directly).
 *
 * An unresolved target returns `null`; the runtime turns that into a traced
 * `Ignored("unknown screen …")` rather than a crash.
 */
class ScreenIndex(private val document: DesignDocument) {

    /** page id → its screen-root frame (the page's first child). */
    private val rootByScreenId: Map<String, DesignNode> =
        document.pages.mapNotNull { page ->
            page.children.firstOrNull()?.let { root -> page.id to root }
        }.toMap()

    /** The screen ids this index can navigate to by id. */
    val screenIds: List<String> get() = rootByScreenId.keys.toList()

    /** The page id whose root frame is [node], or "" when [node] is not a screen root. */
    fun screenIdOfRoot(node: DesignNode): String =
        rootByScreenId.entries.firstOrNull { it.value.id == node.id }?.key.orEmpty()

    /** The screen-root frame for [target], across page-id / screen-meta-id / node-id. */
    fun rootFrameFor(target: String): DesignNode? {
        if (target.isBlank()) return null
        rootByScreenId[target]?.let { return it }
        if (document.screen?.id == target) {
            document.pages.firstOrNull()?.children?.firstOrNull()?.let { return it }
        }
        return document.nodeById(target)
    }

    /** The screen id [target] resolves to (page id), or "" when unknown. */
    fun screenIdFor(target: String): String {
        if (rootByScreenId.containsKey(target)) return target
        if (document.screen?.id == target) return document.pages.firstOrNull()?.id.orEmpty()
        val node = document.nodeById(target) ?: return ""
        return document.pageOfNode(node.id)?.id.orEmpty()
    }
}
