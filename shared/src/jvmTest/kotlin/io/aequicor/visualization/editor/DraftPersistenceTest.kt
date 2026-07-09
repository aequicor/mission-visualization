package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.DefaultDraftRepository
import io.aequicor.visualization.editor.data.DraftStorageKey
import io.aequicor.visualization.editor.data.KeyValueStore
import io.aequicor.visualization.editor.domain.ClearDraftUseCase
import io.aequicor.visualization.editor.domain.DraftSchemaVersion
import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.RestoreDraftSourcesUseCase
import io.aequicor.visualization.editor.domain.SaveDraftUseCase
import io.aequicor.visualization.editor.domain.WorkspaceDraft
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Local draft persistence: the SLM sources round-trip through the repository and
 * restore use case, schema/emptiness guards fall back to defaults, and a corrupt
 * payload is tolerated. Uses an in-memory [KeyValueStore] so no platform store is hit.
 */
class DraftPersistenceTest {

    private class FakeKeyValueStore : KeyValueStore {
        val entries = mutableMapOf<String, String>()
        override fun getString(key: String): String? = entries[key]
        override fun putString(key: String, value: String) { entries[key] = value }
        override fun remove(key: String) { entries.remove(key) }
    }

    private fun repository(store: KeyValueStore) = DefaultDraftRepository(store, Dispatchers.Unconfined)

    private val sample = listOf(
        MissionDocumentSource("mission-overview.layout.md", "# Overview\n\ncontent"),
        MissionDocumentSource("mission-telemetry.layout.md", "# Telemetry"),
    )

    @Test
    fun saveThenRestoreRoundTripsSources() = runBlocking {
        val repo = repository(FakeKeyValueStore())
        SaveDraftUseCase(repo)(sample)
        assertEquals(sample, RestoreDraftSourcesUseCase(repo)()?.files)
    }

    @Test
    fun saveThenRestoreRoundTripsProjectName() = runBlocking {
        val repo = repository(FakeKeyValueStore())
        SaveDraftUseCase(repo)(sample, projectName = "Orbit Console")
        val restored = RestoreDraftSourcesUseCase(repo)()
        assertEquals(sample, restored?.files)
        assertEquals("Orbit Console", restored?.projectName)
    }

    @Test
    fun restoreSupportsLegacyDraftWithoutProjectName() = runBlocking {
        val store = FakeKeyValueStore()
        store.putString(
            DraftStorageKey,
            """{"schemaVersion":$DraftSchemaVersion,"files":[{"fileName":"legacy.layout.md","content":"# Legacy"}]}""",
        )
        val restored = RestoreDraftSourcesUseCase(repository(store))()
        assertEquals(listOf(MissionDocumentSource("legacy.layout.md", "# Legacy")), restored?.files)
        assertEquals("", restored?.projectName)
    }

    @Test
    fun restoreReturnsNullWhenNoDraftStored() = runBlocking {
        assertNull(RestoreDraftSourcesUseCase(repository(FakeKeyValueStore()))())
    }

    @Test
    fun restoreIgnoresIncompatibleSchemaVersion() = runBlocking {
        val repo = repository(FakeKeyValueStore())
        repo.save(WorkspaceDraft(schemaVersion = DraftSchemaVersion + 1, files = sample))
        assertNull(RestoreDraftSourcesUseCase(repo)(), "a stale draft format falls back to defaults")
    }

    @Test
    fun restoreTreatsEmptyDraftAsAbsent() = runBlocking {
        val repo = repository(FakeKeyValueStore())
        repo.save(WorkspaceDraft(DraftSchemaVersion, emptyList()))
        assertNull(RestoreDraftSourcesUseCase(repo)())
    }

    @Test
    fun clearRemovesTheDraft() = runBlocking {
        val repo = repository(FakeKeyValueStore())
        SaveDraftUseCase(repo)(sample)
        ClearDraftUseCase(repo)()
        assertNull(repo.load())
    }

    @Test
    fun loadReturnsNullOnCorruptPayload() = runBlocking {
        val store = FakeKeyValueStore()
        store.putString(DraftStorageKey, "{ not valid json")
        assertNull(repository(store).load(), "a corrupt draft must not crash restore")
    }

    @Test
    fun restoredSourcesRecompileToADocument() = runBlocking {
        // The restore path recompiles the persisted SLM; confirm it yields a document.
        val repo = repository(FakeKeyValueStore())
        val defaults = compileMissionDocuments(
            io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository().missionDocumentSources(),
        )
        SaveDraftUseCase(repo)(defaults.sources)
        val restored = RestoreDraftSourcesUseCase(repo)()
        assertEquals(defaults.sources, restored?.files)
        val recompiled = compileMissionDocuments(restored!!.files)
        assertTrue(recompiled.document != null, "restored sources compile to a document")
    }
}
