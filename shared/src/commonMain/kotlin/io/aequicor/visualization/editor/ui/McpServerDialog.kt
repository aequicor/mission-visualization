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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun McpServerDialog(controller: McpServerController, onDismiss: () -> Unit) {
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current.menu
    val clipboard = LocalClipboardManager.current
    val clipboardScope = rememberCoroutineScope()
    var clipboardErrorFor by remember { mutableStateOf<PromptClipboardTarget?>(null) }
    val dialogScrollState = rememberScrollState()
    val locked = controller.status == McpServerStatus.Starting || controller.status == McpServerStatus.Running
    val portNumber = controller.port.toIntOrNull()
    val validPort = portNumber != null && portNumber in 1024..65535
    val canStart = !locked && validPort && controller.allowedFolder.isNotBlank()

    fun copyPrompt(target: PromptClipboardTarget, text: String) {
        clipboardErrorFor = null
        clipboardScope.launch {
            val copied = writeClipboardWithRetry {
                clipboard.setText(AnnotatedString(text))
            }
            clipboardErrorFor = target.takeUnless { copied }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            modifier = Modifier.width(720.dp).heightIn(max = 820.dp),
            shape = RoundedCornerShape(14.dp),
            color = colors.raisedSurface,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
                    .visibleVerticalScrollbar(dialogScrollState, colors.mutedInk)
                    .verticalScroll(dialogScrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(strings.mcpTitle, style = MaterialTheme.typography.titleLarge, color = colors.ink, fontWeight = FontWeight.SemiBold)
                Text(strings.mcpDescription, style = MaterialTheme.typography.bodyMedium, color = colors.mutedInk)

                McpStepHeader(strings.mcpStepServer)

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
                    McpStepHeader(strings.mcpStepConnectionPrompt)

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
                    Text(strings.mcpConnectionPromptHelp, style = MaterialTheme.typography.bodySmall, color = colors.mutedInk)
                    CopyablePromptBlock(controller.connectionPrompt)
                    Button(onClick = { copyPrompt(PromptClipboardTarget.Connection, controller.connectionPrompt) }) {
                        Text(strings.mcpCopyConnectionPrompt)
                    }
                    if (clipboardErrorFor == PromptClipboardTarget.Connection) {
                        Text(strings.mcpClipboardBusy, color = colors.statusDanger, style = MaterialTheme.typography.bodySmall)
                    }

                    McpStepHeader(strings.mcpStepSetupPrompt)
                    Text(strings.mcpSetupPromptHelp, style = MaterialTheme.typography.bodySmall, color = colors.mutedInk)
                    CopyablePromptBlock(controller.setupPrompt)
                    Button(onClick = { copyPrompt(PromptClipboardTarget.Setup, controller.setupPrompt) }) {
                        Text(strings.mcpCopySetupPrompt)
                    }
                    if (clipboardErrorFor == PromptClipboardTarget.Setup) {
                        Text(strings.mcpClipboardBusy, color = colors.statusDanger, style = MaterialTheme.typography.bodySmall)
                    }

                    McpStepHeader(strings.mcpStepVerify)
                    McpVerificationCard(controller)
                }
            }
        }
    }
}

private enum class PromptClipboardTarget { Connection, Setup }

private suspend fun writeClipboardWithRetry(write: () -> Unit): Boolean {
    repeat(ClipboardWriteAttempts) { attempt ->
        if (runCatching(write).isSuccess) return true
        if (attempt < ClipboardWriteAttempts - 1) delay(ClipboardRetryDelayMillis)
    }
    return false
}

private const val ClipboardWriteAttempts = 10
private const val ClipboardRetryDelayMillis = 50L

@Composable
private fun McpStepHeader(label: String) {
    val colors = LocalEditorColors.current
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = colors.ink,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun CopyablePromptBlock(prompt: String) {
    val colors = LocalEditorColors.current
    SelectionContainer {
        Text(
            prompt,
            modifier = Modifier.fillMaxWidth()
                .background(colors.paneSurface, RoundedCornerShape(8.dp))
                .padding(12.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = colors.codeInk,
        )
    }
}

private fun Modifier.visibleVerticalScrollbar(
    scrollState: androidx.compose.foundation.ScrollState,
    color: Color,
): Modifier = drawWithContent {
    drawContent()
    if (scrollState.maxValue <= 0) return@drawWithContent

    val barWidth = 4.dp.toPx()
    val edgeInset = 3.dp.toPx()
    val minimumThumbHeight = 32.dp.toPx()
    val contentHeight = size.height + scrollState.maxValue.toFloat()
    val thumbHeight = (size.height * size.height / contentHeight)
        .coerceIn(minimumThumbHeight, size.height)
    val availableTravel = (size.height - thumbHeight).coerceAtLeast(0f)
    val progress = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
    val thumbTop = availableTravel * progress

    drawRoundRect(
        color = color.copy(alpha = 0.18f),
        topLeft = Offset(size.width - barWidth - edgeInset, 0f),
        size = Size(barWidth, size.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth, barWidth),
    )
    drawRoundRect(
        color = color.copy(alpha = 0.72f),
        topLeft = Offset(size.width - barWidth - edgeInset, thumbTop),
        size = Size(barWidth, thumbHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth, barWidth),
    )
}

@Composable
private fun McpVerificationCard(controller: McpServerController) {
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current.menu
    val verification = controller.projectVerification
    val statusColor = when {
        verification == null -> colors.statusWarning
        verification.verified -> colors.statusPositive
        else -> colors.statusDanger
    }
    val statusLabel = when {
        verification == null -> strings.mcpWaitingVerification
        verification.verified -> strings.mcpVerified(verification.agentName)
        else -> strings.mcpVerificationFailed
    }
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = colors.paneSurface,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(9.dp).background(statusColor, CircleShape))
                Text(statusLabel, color = colors.ink, style = MaterialTheme.typography.bodyMedium)
            }
            verification?.let {
                Text(it.message, color = if (it.verified) colors.mutedInk else colors.statusDanger, style = MaterialTheme.typography.bodySmall)
                Text("${strings.mcpAgentProject}: ${it.agentProjectPath}", color = colors.codeInk, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text("${strings.mcpLayoutsFolder}: ${it.layoutsPath}", color = colors.codeInk, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
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
