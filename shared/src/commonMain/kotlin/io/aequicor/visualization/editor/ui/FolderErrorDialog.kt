package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.aequicor.visualization.editor.ui.strings.LocalStrings
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors

/**
 * Why an external folder project could not be opened, with the compiler's own words.
 *
 * The project used to fail this silently: the loader compile-gates on error severity and simply
 * held the last good canvas — which, from the landing screen, looks exactly like the click doing
 * nothing. [details] carries the concrete file/line/message so the cause is fixable rather than
 * merely reported.
 */
@Composable
internal fun FolderErrorDialog(
    details: List<String>,
    onDismiss: () -> Unit,
    fromLanding: Boolean = false,
) {
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current.menu
    val scroll = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(560.dp).heightIn(max = 520.dp),
            shape = RoundedCornerShape(14.dp),
            color = colors.raisedSurface,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    strings.folderExternalErrorTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.ink,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    // On the landing there is no "last working version" to fall back to — the
                    // editor-mode copy would be lying there.
                    if (fromLanding) strings.folderExternalErrorOnLanding else strings.folderExternalError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedInk,
                )
                if (details.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .background(colors.surfaceVariant, RoundedCornerShape(9.dp))
                            .border(1.dp, colors.panelStroke, RoundedCornerShape(9.dp))
                            .padding(12.dp)
                            .verticalScroll(scroll),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        details.forEach { detail ->
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.codeInk,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TinyButton(strings.folderExternalErrorDismiss) { onDismiss() }
                }
            }
        }
    }
}
