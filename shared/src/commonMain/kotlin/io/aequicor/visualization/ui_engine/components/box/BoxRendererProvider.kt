package io.aequicor.visualization.ui_engine.components.box

import io.aequicor.visualization.ui_engine.components.common.ContainerNode
import io.aequicor.visualization.ui_engine.compose_render_engine.UiComponentRendererProvider
import io.aequicor.visualization.ui_engine.compose_render_engine.UiNodeRenderer

object BoxRendererProvider : UiComponentRendererProvider {
    override val type: String = "box"
    override val renderer: UiNodeRenderer = { node, context -> ContainerNode(node, context) }
}
