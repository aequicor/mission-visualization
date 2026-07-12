package io.aequicor.visualization.editor.platform

// OS drag-drop / paste ingestion ships web-only in v1; other platforms install nothing.
internal actual fun installResourceIngestion(
    onDrop: (base64: String, name: String, width: Double, height: Double, clientX: Double, clientY: Double) -> Unit,
    onPaste: (base64: String, name: String, width: Double, height: Double) -> Unit,
    onDragOver: (active: Boolean) -> Unit,
    onError: (error: IngestionError) -> Unit,
): ResourceIngestionHandle = NoResourceIngestion
