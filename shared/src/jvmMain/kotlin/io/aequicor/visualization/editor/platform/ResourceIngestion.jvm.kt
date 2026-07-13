package io.aequicor.visualization.editor.platform

import java.awt.Canvas
import java.awt.Component
import java.awt.Container
import java.awt.EventQueue
import java.awt.Image
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.Reader
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.RootPaneContainer
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Desktop image payload normalized for the common resource-ingestion callbacks. */
internal data class JvmIngestedImage(
    val bytes: ByteArray,
    val name: String,
    val width: Double,
    val height: Double,
)

private sealed interface TransferImageResult {
    data class Success(val image: JvmIngestedImage) : TransferImageResult
    data object Unsupported : TransferImageResult
    data object ReadFailed : TransferImageResult
}

/**
 * Installs native AWT drop handling on the Compose window plus a clipboard shortcut dispatcher.
 * File-list clipboard payloads preserve their original PNG/SVG bytes; bitmap-only clipboard
 * payloads (screenshots and copied browser images) are normalized to PNG.
 */
internal actual fun installResourceIngestion(
    onDrop: (base64: String, name: String, width: Double, height: Double, clientX: Double, clientY: Double) -> Unit,
    onPaste: (base64: String, name: String, width: Double, height: Double) -> Unit,
    onDragOver: (active: Boolean) -> Unit,
    onError: (error: IngestionError) -> Unit,
): ResourceIngestionHandle {
    val disposed = AtomicBoolean(false)
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()

    fun JvmIngestedImage.encoded(): String = Base64.getEncoder().encodeToString(bytes)

    val dropListener = object : DropTargetAdapter() {
        override fun dragEnter(event: DropTargetDragEvent) = updateDrag(event)

        override fun dragOver(event: DropTargetDragEvent) = updateDrag(event)

        private fun updateDrag(event: DropTargetDragEvent) {
            if (event.transferable.hasPotentialImage()) {
                event.acceptDrag(DnDConstants.ACTION_COPY)
                if (!disposed.get()) onDragOver(true)
            } else {
                event.rejectDrag()
                if (!disposed.get()) onDragOver(false)
            }
        }

        override fun dragExit(event: DropTargetEvent) {
            if (!disposed.get()) onDragOver(false)
        }

        override fun drop(event: DropTargetDropEvent) {
            if (!disposed.get()) onDragOver(false)
            if (!event.transferable.hasPotentialImage()) {
                event.rejectDrop()
                if (!disposed.get()) onError(IngestionError.UnsupportedType)
                return
            }

            event.acceptDrop(DnDConstants.ACTION_COPY)
            when (val result = readTransferableImage(event.transferable)) {
                is TransferImageResult.Success -> {
                    val point = event.location
                    event.dropComplete(true)
                    if (!disposed.get()) {
                        val image = result.image
                        onDrop(
                            image.encoded(),
                            image.name,
                            image.width,
                            image.height,
                            point.x.toDouble(),
                            point.y.toDouble(),
                        )
                    }
                }

                TransferImageResult.Unsupported -> {
                    event.dropComplete(false)
                    if (!disposed.get()) onError(IngestionError.UnsupportedType)
                }

                TransferImageResult.ReadFailed -> {
                    event.dropComplete(false)
                    if (!disposed.get()) onError(IngestionError.ReadFailed)
                }
            }
        }
    }

    var dropComponent: Component? = null
    var previousDropTarget: DropTarget? = null
    var installedDropTarget: DropTarget? = null

    fun attachDropTarget() {
        if (disposed.get() || installedDropTarget != null) return
        val component = resourceIngestionTarget() ?: return
        val previous = component.dropTarget
        val target = runCatching {
            DropTarget(component, DnDConstants.ACTION_COPY, dropListener, true)
        }.getOrNull() ?: return
        dropComponent = component
        previousDropTarget = previous
        installedDropTarget = target
    }

    // Composition normally runs after the ComposeWindow is visible. The deferred attempt covers
    // startup ordering where no active/showing AWT window was available during first composition.
    attachDropTarget()
    if (installedDropTarget == null) EventQueue.invokeLater(::attachDropTarget)

    val keyDispatcher = java.awt.KeyEventDispatcher { event ->
        if (disposed.get() || !event.isPasteShortcut()) return@KeyEventDispatcher false
        val transferable = runCatching { Toolkit.getDefaultToolkit().systemClipboard.getContents(null) }
            .getOrElse {
                onError(IngestionError.ReadFailed)
                return@KeyEventDispatcher false
            }
            ?: return@KeyEventDispatcher false

        when (val result = readTransferableImage(transferable)) {
            is TransferImageResult.Success -> {
                val image = result.image
                onPaste(image.encoded(), image.name, image.width, image.height)
                true
            }

            TransferImageResult.ReadFailed -> {
                onError(IngestionError.ReadFailed)
                true
            }

            // Leave ordinary text/non-image paste to the focused Compose control.
            TransferImageResult.Unsupported -> false
        }
    }
    focusManager.addKeyEventDispatcher(keyDispatcher)

    return object : ResourceIngestionHandle {
        override fun dispose() {
            if (!disposed.compareAndSet(false, true)) return
            focusManager.removeKeyEventDispatcher(keyDispatcher)
            onDragOver(false)

            val component = dropComponent
            val target = installedDropTarget
            if (component != null && component.dropTarget === target) {
                component.dropTarget = previousDropTarget
            } else {
                target?.component = null
            }
            installedDropTarget = null
            dropComponent = null
        }
    }
}

private fun resourceIngestionTarget(): Component? {
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    val window = focusManager.activeWindow
        ?: focusManager.focusedWindow
        ?: Window.getWindows().lastOrNull { it.isShowing && it.isDisplayable }
    val root = (window as? RootPaneContainer)?.contentPane ?: window ?: return null
    return findResourceIngestionTarget(root)
}

/**
 * Compose Desktop's Skia surface is a heavyweight AWT [Canvas]. Heavyweight components consume
 * native drag/drop before their lightweight Swing parents, so the DropTarget must be installed on
 * that rendering canvas rather than on ComposeWindow.contentPane.
 */
internal fun findResourceIngestionTarget(root: Component): Component =
    root.firstDescendantCanvas() ?: root

private fun Component.firstDescendantCanvas(): Canvas? {
    if (this is Canvas) return this
    if (this !is Container) return null
    components.forEach { child -> child.firstDescendantCanvas()?.let { return it } }
    return null
}

private fun KeyEvent.isPasteShortcut(): Boolean {
    if (id != KeyEvent.KEY_PRESSED || isAltDown) return false
    return (keyCode == KeyEvent.VK_V && (isControlDown || isMetaDown)) ||
        (keyCode == KeyEvent.VK_INSERT && isShiftDown)
}

private fun Transferable.hasPotentialImage(): Boolean {
    if (isDataFlavorSupported(DataFlavor.javaFileListFlavor) || isDataFlavorSupported(DataFlavor.imageFlavor)) {
        return true
    }
    return transferDataFlavors.any { it.isSupportedDirectImageFlavor() }
}

private fun readTransferableImage(transferable: Transferable): TransferImageResult = try {
    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        val files = (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
            .orEmpty()
            .filterIsInstance<File>()
        val imageFile = files.firstOrNull(::isSupportedImageFile)
            ?: return TransferImageResult.Unsupported
        return readJvmImageFile(imageFile)
            ?.let(TransferImageResult::Success)
            ?: TransferImageResult.ReadFailed
    }

    val directFlavor = transferable.transferDataFlavors.firstOrNull(DataFlavor::isSupportedDirectImageFlavor)
    if (directFlavor != null) {
        val bytes = transferable.getTransferData(directFlavor).asTransferBytes()
            ?: return TransferImageResult.ReadFailed
        val extension = extensionForImageSubtype(directFlavor.subType)
            ?: return TransferImageResult.Unsupported
        val image = imagePayload(bytes, "pasted-image-${System.currentTimeMillis()}.$extension")
        return image?.let(TransferImageResult::Success) ?: TransferImageResult.ReadFailed
    }

    if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
        val image = transferable.getTransferData(DataFlavor.imageFlavor) as? Image
            ?: return TransferImageResult.ReadFailed
        val payload = awtImagePayload(image) ?: return TransferImageResult.ReadFailed
        return TransferImageResult.Success(payload)
    }

    // Vector tools and browsers sometimes publish SVG source as plain text instead of an
    // image/svg+xml clipboard flavor. Only claim it when it really parses as an SVG document.
    if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        val text = transferable.getTransferData(DataFlavor.stringFlavor) as? String
            ?: return TransferImageResult.Unsupported
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        if (parseSvgIntrinsicSize(bytes) != null) {
            return TransferImageResult.Success(
                imagePayload(bytes, "pasted-image-${System.currentTimeMillis()}.svg")
                    ?: return TransferImageResult.ReadFailed,
            )
        }
    }

    TransferImageResult.Unsupported
} catch (_: Exception) {
    TransferImageResult.ReadFailed
}

private fun DataFlavor.isSupportedDirectImageFlavor(): Boolean =
    this != DataFlavor.imageFlavor && primaryType.equals("image", ignoreCase = true) &&
        extensionForImageSubtype(subType) != null

private fun extensionForImageSubtype(subtype: String): String? = when (subtype.lowercase(Locale.ROOT)) {
    "png" -> "png"
    "svg+xml", "svg" -> "svg"
    "jpeg", "jpg" -> "jpg"
    "gif" -> "gif"
    "webp" -> "webp"
    else -> null
}

private fun Any?.asTransferBytes(): ByteArray? = when (this) {
    is ByteArray -> this
    is ByteBuffer -> duplicate().let { buffer -> ByteArray(buffer.remaining()).also(buffer::get) }
    is InputStream -> use(InputStream::readBytes)
    is Reader -> use(Reader::readText).toByteArray(StandardCharsets.UTF_8)
    is String -> toByteArray(StandardCharsets.UTF_8)
    else -> null
}

private val supportedImageExtensions = setOf("png", "svg", "jpg", "jpeg", "gif", "webp")

private fun isSupportedImageFile(file: File): Boolean =
    file.isFile && file.extension.lowercase(Locale.ROOT) in supportedImageExtensions

/** Reads a supported desktop image file while preserving its original encoded bytes. */
internal fun readJvmImageFile(file: File): JvmIngestedImage? {
    if (!isSupportedImageFile(file)) return null
    val bytes = runCatching { Files.readAllBytes(file.toPath()) }.getOrNull() ?: return null
    return imagePayload(bytes, file.name)
}

private fun imagePayload(bytes: ByteArray, name: String): JvmIngestedImage? {
    if (bytes.isEmpty()) return null
    if (name.endsWith(".svg", ignoreCase = true) || bytes.lookLikeSvgMarkup()) {
        val size = parseSvgIntrinsicSize(bytes) ?: return null
        return JvmIngestedImage(bytes, name, size.first, size.second)
    }

    val raster = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
    if (raster != null && raster.width > 0 && raster.height > 0) {
        return JvmIngestedImage(bytes, name, raster.width.toDouble(), raster.height.toDouble())
    }

    // The JRE commonly lacks a WebP ImageIO plugin, while Skia (used by Compose) can still decode
    // it. Preserve a valid-looking WebP and let the common sizing fallback handle its dimensions.
    if (name.endsWith(".webp", ignoreCase = true) && bytes.lookLikeWebP()) {
        return JvmIngestedImage(bytes, name, 0.0, 0.0)
    }
    return null
}

private fun ByteArray.lookLikeSvgMarkup(): Boolean {
    val head = copyOfRange(0, minOf(size, 1024))
        .toString(StandardCharsets.UTF_8)
        .trimStart('\uFEFF', ' ', '\n', '\r', '\t')
    return head.startsWith("<svg", ignoreCase = true) ||
        (head.startsWith("<?xml", ignoreCase = true) && head.contains("<svg", ignoreCase = true))
}

private fun ByteArray.lookLikeWebP(): Boolean = size >= 12 &&
    copyOfRange(0, 4).decodeToString() == "RIFF" && copyOfRange(8, 12).decodeToString() == "WEBP"

private fun awtImagePayload(source: Image): JvmIngestedImage? {
    val loaded = if (source.getWidth(null) > 0 && source.getHeight(null) > 0) source else ImageIcon(source).image
    val width = loaded.getWidth(null).takeIf { it > 0 } ?: return null
    val height = loaded.getHeight(null).takeIf { it > 0 } ?: return null
    val buffered = if (loaded is BufferedImage) {
        loaded
    } else {
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { target ->
            target.createGraphics().useGraphics { graphics -> graphics.drawImage(loaded, 0, 0, null) }
        }
    }
    val bytes = ByteArrayOutputStream().use { output ->
        if (!ImageIO.write(buffered, "png", output)) return null
        output.toByteArray()
    }
    return JvmIngestedImage(
        bytes = bytes,
        name = "pasted-image-${System.currentTimeMillis()}.png",
        width = width.toDouble(),
        height = height.toDouble(),
    )
}

private inline fun <T> java.awt.Graphics2D.useGraphics(block: (java.awt.Graphics2D) -> T): T =
    try {
        block(this)
    } finally {
        dispose()
    }

/**
 * Returns intrinsic SVG dimensions in CSS pixels. Width/height win; a viewBox supplies missing
 * dimensions and preserves its aspect ratio. The fallback matches the SVG replaced-element size.
 */
internal fun parseSvgIntrinsicSize(bytes: ByteArray): Pair<Double, Double>? = runCatching {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isXIncludeAware = false
        isExpandEntityReferences = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
        runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
    }
    val root = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
    val rootName = (root.localName ?: root.tagName.substringAfter(':')).lowercase(Locale.ROOT)
    require(rootName == "svg")

    val width = root.getAttribute("width").parseSvgLength()
    val height = root.getAttribute("height").parseSvgLength()
    val viewBox = root.getAttribute("viewBox")
        .trim()
        .split(Regex("[\\s,]+"))
        .mapNotNull(String::toDoubleOrNull)
        .takeIf { it.size == 4 && it[2] > 0.0 && it[3] > 0.0 }
    val viewWidth = viewBox?.get(2)
    val viewHeight = viewBox?.get(3)

    when {
        width != null && height != null -> width to height
        width != null && viewWidth != null && viewHeight != null -> width to (width * viewHeight / viewWidth)
        height != null && viewWidth != null && viewHeight != null -> (height * viewWidth / viewHeight) to height
        viewWidth != null && viewHeight != null -> viewWidth to viewHeight
        width != null -> width to DefaultSvgHeight
        height != null -> DefaultSvgWidth to height
        else -> DefaultSvgWidth to DefaultSvgHeight
    }
}.getOrNull()?.takeIf { (width, height) -> width.isFinite() && height.isFinite() && width > 0.0 && height > 0.0 }

private fun String.parseSvgLength(): Double? {
    val match = SvgLengthRegex.matchEntire(trim()) ?: return null
    val value = match.groupValues[1].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    return when (match.groupValues[2].lowercase(Locale.ROOT)) {
        "", "px" -> value
        "in" -> value * CssPixelsPerInch
        "cm" -> value * CssPixelsPerInch / 2.54
        "mm" -> value * CssPixelsPerInch / 25.4
        "q" -> value * CssPixelsPerInch / 101.6
        "pt" -> value * CssPixelsPerInch / 72.0
        "pc" -> value * CssPixelsPerInch / 6.0
        else -> null
    }
}

private val SvgLengthRegex = Regex("([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*([a-zA-Z%]*)")
private const val CssPixelsPerInch = 96.0
private const val DefaultSvgWidth = 300.0
private const val DefaultSvgHeight = 150.0
