package io.aequicor.visualization.ui_engine.components.screen

import io.aequicor.visualization.ui_engine.components.common.ContainerNode
import io.aequicor.visualization.ui_engine.compose_render_engine.UiComponentRendererProvider
import io.aequicor.visualization.ui_engine.compose_render_engine.UiNodeRenderer

object ScreenRendererProvider : UiComponentRendererProvider {
    override val type: String = "screen"
    override val renderer: UiNodeRenderer = { node, context -> ContainerNode(node, context) }
}
