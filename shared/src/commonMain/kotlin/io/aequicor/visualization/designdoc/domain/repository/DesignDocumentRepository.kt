package io.aequicor.visualization.designdoc.domain.repository

/** Access to design-document sources; implementations live in the data layer. */
interface DesignDocumentRepository {
    /** Raw JSON source of the bundled mission design document. */
    fun missionDocumentSource(): String
}
