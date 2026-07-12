package io.aequicor.visualization.editor.data

import io.aequicor.visualization.editor.domain.DraftRepository
import io.aequicor.visualization.editor.domain.WorkspaceDraft
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Storage key; the `v1` suffix pairs with [WorkspaceDraft] schema versioning. */
internal const val DraftStorageKey: String = "mv.slm.draft.v1"
internal const val FolderPendingStorageKey: String = "mv.folder.pending.v1"

private val DraftJson: Json = Json { ignoreUnknownKeys = true }

/**
 * Draft persistence over a [KeyValueStore]: (de)serializes a JSON [DraftEnvelopeDto]
 * under a single key on the injected [dispatcher] for main-safety. A corrupt or
 * unreadable payload restores as null rather than throwing.
 */
class DefaultDraftRepository(
    private val store: KeyValueStore,
    private val dispatcher: CoroutineDispatcher,
    private val key: String = DraftStorageKey,
    private val json: Json = DraftJson,
) : DraftRepository {

    override suspend fun load(): WorkspaceDraft? = withContext(dispatcher) {
        val raw = store.getString(key) ?: return@withContext null
        runCatching { json.decodeFromString(DraftEnvelopeDto.serializer(), raw).toDomain() }.getOrNull()
    }

    override suspend fun save(draft: WorkspaceDraft) {
        val raw = json.encodeToString(DraftEnvelopeDto.serializer(), draft.toDto())
        withContext(dispatcher) { store.putString(key, raw) }
    }

    override suspend fun clear() {
        withContext(dispatcher) { store.remove(key) }
    }
}
