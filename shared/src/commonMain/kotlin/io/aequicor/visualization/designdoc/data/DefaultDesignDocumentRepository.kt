package io.aequicor.visualization.designdoc.data

import io.aequicor.visualization.designdoc.domain.repository.DesignDocumentRepository

/** Serves the bundled mission design document JSON. */
class DefaultDesignDocumentRepository : DesignDocumentRepository {
    override fun missionDocumentSource(): String = MissionDesignJson
}
