package io.aequicor.visualization.ui_engine.components.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.ui_engine.ui_document_ir.title
import io.aequicor.visualization.ui_engine.compose_render_engine.UiComponentRendererProvider
import io.aequicor.visualization.ui_engine.compose_render_engine.UiNodeRenderer
import io.aequicor.visualization.ui_engine.compose_render_engine.ifDefault
import io.aequicor.visualization.ui_engine.compose_render_engine.spacingDp

object FormRendererProvider : UiComponentRendererProvider {
    override val type: String = "form"
    override val renderer: UiNodeRenderer = { node, context ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFBFDFF),
            border = BorderStroke(1.dp, Color(0xFFD6E4EE)),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(spacingDp(node.layout.gap).ifDefault(10.dp))) {
                Text(node.title("Form"), fontWeight = FontWeight.Bold)
                node.children.forEach { context.renderChild(it) }
            }
        }
    }
}
