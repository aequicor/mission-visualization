package io.aequicor.visualization.ui_engine.components.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.ui_engine.ui_document_ir.propString
import io.aequicor.visualization.ui_engine.ui_document_ir.title
import io.aequicor.visualization.ui_engine.compose_render_engine.DeepLapis
import io.aequicor.visualization.ui_engine.compose_render_engine.SignalTeal
import io.aequicor.visualization.ui_engine.compose_render_engine.UiComponentRendererProvider
import io.aequicor.visualization.ui_engine.compose_render_engine.UiNodeRenderer

object TopBarRendererProvider : UiComponentRendererProvider {
    override val type: String = "topBar"
    override val renderer: UiNodeRenderer = { node, _ ->
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = DeepLapis, contentColor = Color.White) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(node.title("Top bar"), fontWeight = FontWeight.Bold)
                    if (node.propString("text").isNotBlank()) {
                        Text(node.propString("text"), style = MaterialTheme.typography.bodySmall, color = Color(0xFFC8E0F8))
                    }
                }
                Box(Modifier.size(28.dp).clip(CircleShape).background(SignalTeal))
            }
        }
    }
}
