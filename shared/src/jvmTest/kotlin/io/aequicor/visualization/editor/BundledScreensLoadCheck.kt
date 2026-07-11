package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.domain.LoadDesignDocumentUseCase
import kotlin.test.Test
import kotlin.test.assertEquals

/** All 7 bundled sources must survive the editor's compile+merge (what the app shows). */
class BundledScreensLoadCheck {

    @Test
    fun allBundledScreensLoad() {
        val documents = LoadDesignDocumentUseCase(DefaultDesignDocumentRepository())()
        val pages = documents.document?.pages?.map { it.name }.orEmpty()
        println("PAGES(${pages.size}): $pages")
        documents.diagnostics
            .filter { it.severity.name.lowercase().contains("error") }
            .forEach { println("ERROR: ${it.message}") }
        assertEquals(7, pages.size, "pages: $pages")
    }
}
