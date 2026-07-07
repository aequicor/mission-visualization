package io.aequicor.visualization.editor.data

import io.aequicor.visualization.editor.domain.DesignDocumentRepository

/** Serves the bundled mission design document JSON. */
class DefaultDesignDocumentRepository : DesignDocumentRepository {
    override fun missionDocumentSource(): String = MissionDesignJson
}
