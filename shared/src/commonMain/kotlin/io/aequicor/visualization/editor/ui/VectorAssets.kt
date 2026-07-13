package io.aequicor.visualization.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import io.aequicor.visualization.editor.data.SlmVectorAssetProvider
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.shared.generated.resources.Res
import io.aequicor.visualization.subsystems.figures.VectorAssetProvider

/**
 * Builds the real [VectorAssetProvider] the artboard renders shape `iconRef`/`pathRef` against.
 *
 * The provider's registry is the bundled **editor-icons** SVG set (Material Symbols in
 * `composeResources/files/editor-icons`), keyed by icon name (`rectangle`, `ellipse`, …). A
 * shape `iconRef` whose last path segment normalises to a bundled name resolves to that outline;
 * a `pathRef` resolves only when its `DesignAsset.url` inlines a `data:image/svg+xml` payload
 * (the document model carries no bundled SVG bytes yet — see [SlmVectorAssetProvider]). Anything
 * unmatched leaves the renderer's box fallback in place.
 *
 * The bundle is read once (suspend `Res.readBytes`) and process-cached; while it loads the
 * returned provider resolves against the [document] assets only.
 */
@Composable
fun rememberVectorAssetProvider(document: DesignDocument): VectorAssetProvider {
    val svgSources = rememberEditorIconSvgSources().value
    return remember(svgSources, document) {
        SlmVectorAssetProvider(svgSources = svgSources, document = document)
    }
}

@Composable
private fun rememberEditorIconSvgSources(): State<Map<String, String>> =
    produceState(initialValue = editorIconSvgCache ?: emptyMap()) {
        value = editorIconSvgCache ?: loadEditorIconSvgSources().also { editorIconSvgCache = it }
    }

private var editorIconSvgCache: Map<String, String>? = null

private suspend fun loadEditorIconSvgSources(): Map<String, String> =
    EditorIcon.entries.associate { icon ->
        val name = icon.resourcePath.substringAfterLast('/').removeSuffix(".svg")
        name to Res.readBytes(icon.resourcePath).decodeToString()
    }
