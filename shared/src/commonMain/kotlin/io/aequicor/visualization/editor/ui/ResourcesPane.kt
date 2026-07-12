package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.editor.platform.IngestionError
import io.aequicor.visualization.editor.platform.createProjectResourceStore
import io.aequicor.visualization.MissionEditorStateHolder
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.ui.strings.LocalStrings
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.literalOrNull

/**
 * Resources tab: the project's `res/` images (those referenced by the current document), each with a
 * thumbnail, file name, intrinsic size and usage count. Reads bytes from the same platform store the
 * canvas renders against (IndexedDB on web); clicking a row selects the first node using that image.
 * Empty until the user drops or pastes an image — it never lists orphaned bytes, matching what
 * Save-to-folder/zip writes.
 */
@Composable
internal fun ResourcesTab(state: MissionEditorStateHolder, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current
    val document = state.designState.document
    val store = remember { createProjectResourceStore() }
    val provider = rememberImageAssetProvider(document, store)
    val refs = remember(document) {
        document?.let(::collectResourceImageRefs)?.sortedBy { it.lowercase() } ?: emptyList()
    }
    if (refs.isEmpty()) {
        Box(modifier.fillMaxSize().background(colors.paneSurface).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EditorSvgIcon(EditorIcon.Assets, contentDescription = null, tint = colors.subtleInk, modifier = Modifier.size(28.dp))
                Text(strings.source.resourcesEmptyTitle, style = MaterialTheme.typography.bodyMedium, color = colors.mutedInk, textAlign = TextAlign.Center)
                Text(strings.source.resourcesEmptyHint, style = MaterialTheme.typography.bodySmall, color = colors.subtleInk, textAlign = TextAlign.Center)
            }
        }
        return
    }
    val usage = remember(document) { document?.let(::resourceUsageByRef) ?: emptyMap() }
    LazyColumn(
        modifier.fillMaxSize().background(colors.paneSurface),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(refs, key = { it }) { ref ->
            ResourceRow(
                name = ref.substringAfterLast('/'),
                bitmap = provider.resolve(ref),
                usageCount = usage[ref] ?: 0,
                onClick = { firstNodeUsingRef(document, ref)?.let { state.dispatch(DesignEditorIntent.SelectNode(it)) } },
            )
        }
    }
}

@Composable
private fun ResourceRow(name: String, bitmap: ImageBitmap?, usageCount: Int, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(4.dp))
                .background(colors.raisedSurface)
                .border(1.dp, colors.softStroke, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = name, modifier = Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Fit)
            } else {
                EditorSvgIcon(EditorIcon.Image, contentDescription = null, tint = colors.subtleInk, modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(name, style = MaterialTheme.typography.bodySmall, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = if (bitmap != null) {
                "${bitmap.width}×${bitmap.height} · ${strings.source.resourceUsage(usageCount)}"
            } else {
                strings.source.resourceMissing
            }
            Text(meta, style = MaterialTheme.typography.labelSmall, color = colors.mutedInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Canvas overlay for external-image ingestion: a dashed drop affordance while an OS file drag hovers
 * the app, and a transient banner for a non-fatal [IngestionError]. Renders nothing when idle and
 * installs no pointer handlers, so it never intercepts canvas gestures. Place it as the last child of
 * the canvas [BoxScope] so it paints on top.
 */
@Composable
internal fun ResourceDropOverlay(dragActive: Boolean, error: IngestionError?) {
    if (!dragActive && error == null) return
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current
    Box(Modifier.fillMaxSize()) {
        if (dragActive) {
            Box(
                Modifier.matchParentSize()
                    .background(colors.accent.copy(alpha = 0.06f))
                    .drawBehind {
                        val inset = 6.dp.toPx()
                        drawRoundRect(
                            color = colors.accent,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - inset * 2, size.height - inset * 2),
                            cornerRadius = CornerRadius(14.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorSvgIcon(EditorIcon.Image, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                    Text(strings.canvas.dropImageHere, style = MaterialTheme.typography.bodyMedium, color = colors.accent)
                }
            }
        }
        if (error != null) {
            IngestionErrorBanner(error)
        }
    }
}

@Composable
private fun BoxScope.IngestionErrorBanner(error: IngestionError) {
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current
    Surface(
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
        color = colors.raisedSurface,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.softStroke),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(colors.statusWarning))
            Text(
                text = when (error) {
                    IngestionError.UnsupportedType -> strings.canvas.ingestUnsupportedType
                    IngestionError.ReadFailed -> strings.canvas.ingestReadFailed
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink,
            )
        }
    }
}

/** Number of media nodes / image-video paints referencing each `res/…` path in the document. */
internal fun resourceUsageByRef(document: DesignDocument): Map<String, Int> {
    val counts = mutableMapOf<String, Int>()
    fun bump(ref: String?) {
        if (ref != null && ref.startsWith("res/")) counts[ref] = (counts[ref] ?: 0) + 1
    }
    fun visit(node: DesignNode) {
        (node.kind as? DesignNodeKind.Media)?.media?.assetId?.literalOrNull()?.let(::bump)
        node.fills?.forEach { paint ->
            when (paint) {
                is DesignPaint.Image -> bump(paint.assetId)
                is DesignPaint.Video -> bump(paint.assetId)
                else -> Unit
            }
        }
        node.children.forEach(::visit)
    }
    document.pages.forEach { page -> page.children.forEach(::visit) }
    return counts
}

/** Id of the first node (document order) whose media/fill references [ref], or null. */
internal fun firstNodeUsingRef(document: DesignDocument?, ref: String): String? {
    document ?: return null
    var found: String? = null
    fun visit(node: DesignNode) {
        if (found != null) return
        val usesMedia = (node.kind as? DesignNodeKind.Media)?.media?.assetId?.literalOrNull() == ref
        val usesFill = node.fills?.any { paint ->
            (paint is DesignPaint.Image && paint.assetId == ref) || (paint is DesignPaint.Video && paint.assetId == ref)
        } == true
        if (usesMedia || usesFill) {
            found = node.id
            return
        }
        node.children.forEach(::visit)
    }
    document.pages.forEach { page -> page.children.forEach(::visit) }
    return found
}
