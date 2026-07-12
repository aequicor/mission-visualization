package io.aequicor.visualization.editor.data

import io.aequicor.visualization.editor.domain.RecentProject
import io.aequicor.visualization.editor.domain.RecentProjectKind
import io.aequicor.visualization.editor.domain.RecentProjectsCap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class InMemoryKeyValueStore : KeyValueStore {
    val map = mutableMapOf<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}

private fun folder(id: String, name: String = id, at: Long) =
    RecentProject(id, name, RecentProjectKind.LocalFolder, at, folderKey = id)

class RecentProjectsRepositoryTest {

    @Test
    fun emptyByDefault() {
        assertEquals(emptyList(), DefaultRecentProjectsRepository(InMemoryKeyValueStore()).list())
    }

    @Test
    fun upsertPersistsNewestFirst() {
        val repo = DefaultRecentProjectsRepository(InMemoryKeyValueStore())
        repo.upsert(folder("a", at = 100))
        repo.upsert(folder("b", at = 200))
        repo.upsert(folder("c", at = 150))
        assertEquals(listOf("b", "c", "a"), repo.list().map { it.id })
    }

    @Test
    fun upsertSameIdMovesToFrontAndDoesNotDuplicate() {
        val repo = DefaultRecentProjectsRepository(InMemoryKeyValueStore())
        repo.upsert(folder("a", name = "Old", at = 100))
        repo.upsert(folder("b", at = 200))
        repo.upsert(folder("a", name = "New", at = 300))
        val ids = repo.list().map { it.id }
        assertEquals(listOf("a", "b"), ids)
        assertEquals(1, ids.count { it == "a" })
        assertEquals("New", repo.list().first { it.id == "a" }.displayName)
    }

    @Test
    fun capsAtMaximumDroppingOldest() {
        val repo = DefaultRecentProjectsRepository(InMemoryKeyValueStore())
        repeat(RecentProjectsCap + 3) { i -> repo.upsert(folder("id$i", at = i.toLong())) }
        val list = repo.list()
        assertEquals(RecentProjectsCap, list.size)
        // Newest (highest timestamp) survive; the three oldest are dropped.
        assertTrue(list.none { it.id == "id0" || it.id == "id1" || it.id == "id2" })
        assertEquals("id${RecentProjectsCap + 2}", list.first().id)
    }

    @Test
    fun removeForgetsEntry() {
        val repo = DefaultRecentProjectsRepository(InMemoryKeyValueStore())
        repo.upsert(folder("a", at = 100))
        repo.upsert(folder("b", at = 200))
        repo.remove("a")
        assertEquals(listOf("b"), repo.list().map { it.id })
    }

    @Test
    fun welcomeEntriesAreNeverPersisted() {
        val repo = DefaultRecentProjectsRepository(InMemoryKeyValueStore())
        repo.upsert(RecentProject("welcome", "Welcome", RecentProjectKind.Welcome, 999))
        assertEquals(emptyList(), repo.list())
    }

    @Test
    fun corruptPayloadReadsAsEmpty() {
        val store = InMemoryKeyValueStore()
        store.putString(RecentProjectsStorageKey, "{ not valid json ]")
        assertEquals(emptyList(), DefaultRecentProjectsRepository(store).list())
    }

    @Test
    fun mismatchedSchemaReadsAsEmpty() {
        val store = InMemoryKeyValueStore()
        store.putString(RecentProjectsStorageKey, """{"schemaVersion":99,"projects":[{"id":"a","displayName":"A"}]}""")
        assertEquals(emptyList(), DefaultRecentProjectsRepository(store).list())
    }

    @Test
    fun roundTripsThroughStore() {
        val store = InMemoryKeyValueStore()
        DefaultRecentProjectsRepository(store).upsert(folder("a", name = "Alpha", at = 100))
        // A fresh repository over the same store sees the persisted entry.
        val reread = DefaultRecentProjectsRepository(store).list()
        assertEquals(1, reread.size)
        assertEquals("Alpha", reread.first().displayName)
        assertEquals(RecentProjectKind.LocalFolder, reread.first().kind)
        assertNull(reread.first().folderKey?.let { if (it == "a") null else it })
    }
}
