package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.aequicor.visualization.editor.ui.strings.LocalStrings
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.mcp.McpServerController
import io.aequicor.visualization.mcp.McpServerStatus

@Composable
internal fun McpServerDialog(controller: McpServerController, onDismiss: () -> Unit) {
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current.menu
    val clipboard = LocalClipboardManager.current
    val locked = controller.status == McpServerStatus.Starting || controller.status == McpServerStatus.Running
    val portNumber = controller.port.toIntOrNull()
    val validPort = portNumber != null && portNumber in 1024..65535
    val canStart = !locked && validPort && controller.allowedFolder.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            modifier = Modifier.width(680.dp).heightIn(max = 780.dp),
            shape = RoundedCornerShape(14.dp),
            color = colors.raisedSurface,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(strings.mcpTitle, style = MaterialTheme.typography.titleLarge, color = colors.ink, fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = controller.port,
                    onValueChange = { controller.updatePort(it.filter(Char::isDigit).take(5)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !locked,
                    singleLine = true,
                    label = { Text(strings.mcpPort) },
                    supportingText = if (controller.port.isNotBlank() && !validPort) {
                        { Text("1024–65535", color = colors.statusDanger) }
                    } else null,
                )

                Text(strings.mcpAllowedFolder, style = MaterialTheme.typography.labelLarge, color = colors.ink)
                SelectionContainer {
                    Text(
                        controller.allowedFolder.ifBlank { "—" },
                        modifier = Modifier.fillMaxWidth().background(colors.paneSurface, RoundedCornerShape(6.dp)).padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (controller.allowedFolder.isBlank()) colors.mutedInk else colors.codeInk,
                    )
                }
                OutlinedButton(onClick = controller::chooseAllowedFolder, enabled = !locked) {
                    Text(strings.mcpChooseFolder)
                }

                McpStatusRow(controller.status)
                controller.errorMessage?.takeIf(String::isNotBlank)?.let { message ->
                    Text(message, color = colors.statusDanger, style = MaterialTheme.typography.bodySmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (locked) {
                        Button(onClick = controller::stop, enabled = controller.status == McpServerStatus.Running) {
                            Text(strings.mcpStop)
                        }
                    } else {
                        Button(onClick = controller::start, enabled = canStart) { Text(strings.mcpStart) }
                    }
                    OutlinedButton(onClick = onDismiss) { Text(strings.mcpClose) }
                }

                if (controller.status == McpServerStatus.Running) {
                    Text(strings.mcpEndpoint, style = MaterialTheme.typography.labelLarge, color = colors.ink)
                    SelectionContainer {
                        Text(
                            controller.endpoint,
                            modifier = Modifier.fillMaxWidth().background(colors.paneSurface, RoundedCornerShape(6.dp)).padding(10.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.codeInk,
                        )
                    }
                    Text(strings.mcpPrompt, style = MaterialTheme.typography.labelLarge, color = colors.ink)
                    SelectionContainer {
                        Text(
                            controller.prompt,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
                                .background(colors.paneSurface, RoundedCornerShape(6.dp)).padding(12.dp)
                                .verticalScroll(rememberScrollState()),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.codeInk,
                        )
                    }
                    Button(onClick = { clipboard.setText(AnnotatedString(controller.prompt)) }) {
                        Text(strings.mcpCopyPrompt)
                    }
                }
            }
        }
    }
}

@Composable
private fun McpStatusRow(status: McpServerStatus) {
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current.menu
    val (label, color) = when (status) {
        McpServerStatus.Stopped -> strings.mcpStopped to colors.mutedInk
        McpServerStatus.Starting -> strings.mcpStarting to colors.statusWarning
        McpServerStatus.Running -> strings.mcpRunning to colors.statusPositive
        McpServerStatus.Error -> strings.mcpError to colors.statusDanger
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Text("${strings.mcpStatus}: $label", color = colors.ink, style = MaterialTheme.typography.bodyMedium)
    }
}
