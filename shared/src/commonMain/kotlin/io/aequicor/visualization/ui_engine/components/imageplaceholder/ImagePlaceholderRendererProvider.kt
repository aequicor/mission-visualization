package io.aequicor.visualization.ui_engine.components.imageplaceholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.ui_engine.ui_document_ir.title
import io.aequicor.visualization.ui_engine.compose_render_engine.DeepLapis
import io.aequicor.visualization.ui_engine.compose_render_engine.UiComponentRendererProvider
import io.aequicor.visualization.ui_engine.compose_render_engine.UiNodeRenderer

object ImagePlaceholderRendererProvider : UiComponentRendererProvider {
    override val type: String = "imagePlaceholder"
    override val renderer: UiNodeRenderer = { node, _ ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE6F1F8), RoundedCornerShape(8.dp))
                .aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center,
        ) {
            Text(node.title("Image placeholder"), color = DeepLapis, fontWeight = FontWeight.SemiBold)
        }
    }
}
