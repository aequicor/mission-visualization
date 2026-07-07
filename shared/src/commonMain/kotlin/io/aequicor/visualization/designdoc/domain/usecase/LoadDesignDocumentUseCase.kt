package io.aequicor.visualization.designdoc.domain.usecase

import io.aequicor.visualization.designdoc.domain.parser.DesignParseResult
import io.aequicor.visualization.designdoc.domain.parser.parseDesignDocument
import io.aequicor.visualization.designdoc.domain.repository.DesignDocumentRepository

/** Loads and parses the bundled mission design document. */
class LoadDesignDocumentUseCase(
    private val repository: DesignDocumentRepository,
) {
    operator fun invoke(): DesignParseResult =
        parseDesignDocument(repository.missionDocumentSource())
}

/** Parses an arbitrary design-document JSON source. */
class ParseDesignDocumentUseCase {
    operator fun invoke(source: String): DesignParseResult =
        parseDesignDocument(source)
}
