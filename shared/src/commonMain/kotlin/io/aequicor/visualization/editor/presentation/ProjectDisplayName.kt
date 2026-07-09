package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.editor.domain.MissionDocumentSource

internal const val DefaultProjectDisplayName: String = "Mission Visualization"

internal fun projectDisplayName(
    metadataName: String,
    documentName: String?,
    sources: List<MissionDocumentSource>,
): String =
    metadataName.trim().ifBlank {
        documentName?.trim().orEmpty().ifBlank {
            sources.firstOrNull()?.fileName?.projectBaseName().orEmpty().ifBlank {
                DefaultProjectDisplayName
            }
        }
    }

private fun String.projectBaseName(): String {
    val leaf = replace('\\', '/').substringAfterLast('/').trim()
    return leaf
        .removeSuffix(".layout.md")
        .removeSuffix(".slm.md")
        .removeSuffix(".slm")
        .removeSuffix(".md")
        .trim()
}
