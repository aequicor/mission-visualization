package io.aequicor.visualization.ui_engine.components.badge

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.ui_engine.ui_document_ir.propString
import io.aequicor.visualization.ui_engine.ui_document_ir.title
import io.aequicor.visualization.ui_engine.compose_render_engine.DeepLapis
import io.aequicor.visualization.ui_engine.compose_render_engine.UiComponentRendererProvider
import io.aequicor.visualization.ui_engine.compose_render_engine.UiNodeRenderer
import io.aequicor.visualization.ui_engine.compose_render_engine.toneStroke
import io.aequicor.visualization.ui_engine.compose_render_engine.toneSurface

object BadgeRendererProvider : UiComponentRendererProvider {
    override val type: String = "badge"
    override val renderer: UiNodeRenderer = { node, _ ->
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = toneSurface(node.style.tone),
            border = BorderStroke(1.dp, toneStroke(node.style.tone)),
        ) {
            Text(
                node.propString("text").ifBlank { node.title("Badge") },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = DeepLapis,
            )
        }
    }
}
