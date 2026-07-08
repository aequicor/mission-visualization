package io.aequicor.visualization.editor.domain

/** One bundled SLM design source: the `*.layout.md` file name plus its content. */
data class MissionDocumentSource(
    val fileName: String,
    val content: String,
)

/** Access to design-document sources; implementations live in the data layer. */
interface DesignDocumentRepository {
    /** SLM sources of the bundled mission design, one document per page. */
    fun missionDocumentSources(): List<MissionDocumentSource>
}
