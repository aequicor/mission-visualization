package io.aequicor.visualization.ui_engine.components.section

import io.aequicor.visualization.ui_engine.components.common.ContainerNode
import io.aequicor.visualization.ui_engine.compose_render_engine.UiComponentRendererProvider
import io.aequicor.visualization.ui_engine.compose_render_engine.UiNodeRenderer

object SectionRendererProvider : UiComponentRendererProvider {
    override val type: String = "section"
    override val renderer: UiNodeRenderer = { node, context -> ContainerNode(node, context) }
}
