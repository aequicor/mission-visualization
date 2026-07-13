package io.aequicor.visualization.editor.platform

/** Removes the OS drop/paste listeners installed by [installResourceIngestion]. */
interface ResourceIngestionHandle {
    fun dispose()
}

/** No-op handle for platforms without OS resource ingestion. */
internal val NoResourceIngestion: ResourceIngestionHandle = object : ResourceIngestionHandle {
    override fun dispose() = Unit
}

/**
 * Installs OS drag-drop and paste listeners that surface image files the user brings in from
 * outside the app. Web installs DOM listeners around the Compose shadow-root; desktop bridges the
 * native AWT drop target and system clipboard into the same callbacks.
 *
 * [onDrop] fires with the base64 file bytes, the original file name, the image's intrinsic
 * width/height, and the drop point in platform window pixels (CSS/AWT logical pixels; the caller
 * maps it into canvas/doc space).
 * [onPaste] fires the same minus coordinates (paste has no drop point). [onDragOver] toggles true
 * while an OS file drag hovers the app and false when it leaves or drops, so the canvas can show a
 * drop affordance. [onError] reports a non-fatal ingestion problem by [IngestionError] so the caller
 * can surface a localized message instead of silently ignoring it. All are invoked on the UI thread.
 * Returns a handle whose [ResourceIngestionHandle.dispose] removes the listeners.
 */
internal expect fun installResourceIngestion(
    onDrop: (base64: String, name: String, width: Double, height: Double, clientX: Double, clientY: Double) -> Unit,
    onPaste: (base64: String, name: String, width: Double, height: Double) -> Unit,
    onDragOver: (active: Boolean) -> Unit,
    onError: (error: IngestionError) -> Unit,
): ResourceIngestionHandle

/** Non-fatal problems the ingestion layer surfaces so the UI can localize a message. */
enum class IngestionError {
    /** The user dropped/pasted something with no image file among the payload. */
    UnsupportedType,

    /** The file bytes could not be read or decoded as an image. */
    ReadFailed,
}
