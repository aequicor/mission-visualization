package io.aequicor.visualization.ui_engine.components.button

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.ui_engine.runtime_state.UiCommand
import io.aequicor.visualization.ui_engine.ui_document_ir.propString
import io.aequicor.visualization.ui_engine.ui_document_ir.title
import io.aequicor.visualization.ui_engine.compose_render_engine.LapisBlue
import io.aequicor.visualization.ui_engine.compose_render_engine.UiComponentRendererProvider
import io.aequicor.visualization.ui_engine.compose_render_engine.UiNodeRenderer

object ButtonRendererProvider : UiComponentRendererProvider {
    override val type: String = "button"
    override val renderer: UiNodeRenderer = { node, context ->
        val primary = node.style.variant == "primary"
        val text = node.propString("text").ifBlank { node.title("Button") }
        val click: () -> Unit = {
            context.onEvent(UiCommand.SelectNode(node.id))
            node.actions.firstOrNull()?.let { action ->
                when (action.type) {
                    "navigate", "select", "openDialog" -> if (action.target.isNotBlank()) context.onEvent(UiCommand.SelectTarget(action.target))
                    else -> Unit
                }
            }
        }
        if (primary) {
            Button(
                onClick = click,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = LapisBlue),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            OutlinedButton(
                onClick = click,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LapisBlue),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
