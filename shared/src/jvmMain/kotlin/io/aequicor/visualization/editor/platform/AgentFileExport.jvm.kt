package io.aequicor.visualization.editor.platform

import java.awt.KeyboardFocusManager
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

internal actual val platformSupportsAgentFileExport: Boolean = true

internal actual fun platformDownloadAgentFile(fileName: String, markdown: String) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Save $fileName"
        dialogType = JFileChooser.SAVE_DIALOG
        fileFilter = FileNameExtensionFilter("Markdown files (*.md)", "md")
        selectedFile = java.io.File(fileName)
    }
    val parent = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
    if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return

    val selected = chooser.selectedFile.toPath()
    val target = if (selected.fileName.toString().endsWith(".md", ignoreCase = true)) {
        selected
    } else {
        selected.resolveSibling("${selected.fileName}.md")
    }
    Files.writeString(target, markdown, StandardCharsets.UTF_8)
}
