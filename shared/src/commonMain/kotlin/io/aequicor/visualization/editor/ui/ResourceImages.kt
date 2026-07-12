package io.aequicor.visualization.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import io.aequicor.visualization.editor.platform.ProjectResourceStore
import io.aequicor.visualization.editor.platform.rasterizeSvgToPng
import io.aequicor.visualization.engine.backend.compose.ImageAssetProvider
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Builds the [ImageAssetProvider] the artboard renders media nodes and image paints against. It
 * loads the bytes of every `res/…` reference in [document] from the platform [store] (IndexedDB on
 * web), decodes raster formats via [decodeToImageBitmap] and rasterizes SVG via [rasterizeSvgToPng],
 * and caches the result in a snapshot map. `generation` bumps as each bitmap lands so the canvas
 * redraws the real image in place of the placeholder. Refs that fail to load stay as a placeholder.
 */
@Composable
internal fun rememberImageAssetProvider(
    document: DesignDocument?,
    store: ProjectResourceStore,
): ImageAssetProvider {
    // Keyed by store so the cache survives document edits (bytes don't change under a stable path).
    val cache = remember(store) { mutableStateMapOf<String, ImageBitmap?>() }
    val generationState = remember(store) { mutableStateOf(0) }
    val refs = remember(document) { document?.let(::collectResourceImageRefs) ?: emptySet() }
    LaunchedEffect(refs, store) {
        refs.forEach { ref ->
            if (ref in cache) return@forEach
            cache[ref] = null // reserve so a failed load is not retried every recomposition
            val bitmap = loadResourceBitmap(store, ref)
            if (bitmap != null) {
                cache[ref] = bitmap
                generationState.value += 1
            }
        }
    }
    return remember(cache) {
        object : ImageAssetProvider {
            override val generation: Int get() = generationState.value
            override fun resolve(ref: String): ImageBitmap? = cache[ref]
        }
    }
}

/** Distinct `res/…` references used by media nodes and image/video paints across the document. */
internal fun collectResourceImageRefs(document: DesignDocument): Set<String> {
    val refs = mutableSetOf<String>()
    fun consider(ref: String?) {
        if (ref != null && ref.startsWith("res/")) refs += ref
    }
    fun visit(node: DesignNode) {
        (node.kind as? DesignNodeKind.Media)?.media?.assetId?.literalOrNull()?.let(::consider)
        node.fills?.forEach { paint ->
            when (paint) {
                is DesignPaint.Image -> consider(paint.assetId)
                is DesignPaint.Video -> consider(paint.assetId)
                else -> Unit
            }
        }
        node.children.forEach(::visit)
    }
    document.pages.forEach { page -> page.children.forEach(::visit) }
    return refs
}

private suspend fun loadResourceBitmap(
    store: ProjectResourceStore,
    ref: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): ImageBitmap? {
    val bytes = store.read(ref) ?: return null
    // SVG is rasterized to PNG (browser <canvas>); raster formats decode directly.
    val pngBytes = if (ref.endsWith(".svg", ignoreCase = true) || looksLikeSvg(bytes)) {
        rasterizeSvgToPng(bytes) ?: return null
    } else {
        bytes
    }
    return withContext(dispatcher) { runCatching { pngBytes.decodeToImageBitmap() }.getOrNull() }
}

/** Heuristic SVG sniff for byte payloads whose path lacks a `.svg` extension. */
private fun looksLikeSvg(bytes: ByteArray): Boolean {
    val head = bytes.take(256).toByteArray().decodeToString().trimStart('﻿', ' ', '\n', '\r', '\t')
    return head.startsWith("<svg", ignoreCase = true) ||
        (head.startsWith("<?xml", ignoreCase = true) && head.contains("<svg", ignoreCase = true))
}
