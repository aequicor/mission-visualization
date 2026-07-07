package io.aequicor.visualization.ui_engine.components.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.ui_engine.runtime_state.UiCommand
import io.aequicor.visualization.ui_engine.ui_document_ir.propString
import io.aequicor.visualization.ui_engine.ui_document_ir.title
import io.aequicor.visualization.ui_engine.compose_render_engine.UiComponentRendererProvider
import io.aequicor.visualization.ui_engine.compose_render_engine.UiNodeRenderer

object InputRendererProvider : UiComponentRendererProvider {
    override val type: String = "input"
    override val renderer: UiNodeRenderer = { node, context ->
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFD6E4EE))) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(node.title("Input"), style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = context.state.inputValues[node.id].orEmpty(),
                    onValueChange = {
                        context.onEvent(UiCommand.SelectNode(node.id))
                        context.onEvent(UiCommand.UpdateInputValue(node.id, it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(node.propString("placeholder").ifBlank { "Placeholder" }, style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
