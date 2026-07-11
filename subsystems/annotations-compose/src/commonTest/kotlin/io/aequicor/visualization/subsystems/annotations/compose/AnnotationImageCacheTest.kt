package io.aequicor.visualization.subsystems.annotations.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AnnotationImageCacheTest {

    @Test
    fun missReturnsNull() {
        val cache = AnnotationImageCache()

        assertNull(cache.get("data:image/png;base64,AAAA"))
        assertEquals(0, cache.size)
    }

    @Test
    fun failedDecodeIsCachedAsNullBitmapEntry() {
        val cache = AnnotationImageCache()

        cache.put("bad", null)

        val entry = cache.get("bad")
        assertNotNull(entry, "a recorded failed decode must be a hit, not a miss")
        assertNull(entry.bitmap)
        assertEquals(1, cache.size)
    }

    @Test
    fun putForSameSourceOverwritesWithoutGrowing() {
        val cache = AnnotationImageCache(maxEntries = 2)

        cache.put("a", null)
        cache.put("a", null)

        assertEquals(1, cache.size)
    }

    @Test
    fun evictsLeastRecentlyUsedEntryBeyondCapacity() {
        val cache = AnnotationImageCache(maxEntries = 2)

        cache.put("a", null)
        cache.put("b", null)
        cache.put("c", null)

        assertNull(cache.get("a"), "oldest entry must be evicted")
        assertNotNull(cache.get("b"))
        assertNotNull(cache.get("c"))
        assertEquals(2, cache.size)
    }

    @Test
    fun getRefreshesRecencySoHotEntrySurvivesEviction() {
        val cache = AnnotationImageCache(maxEntries = 2)

        cache.put("a", null)
        cache.put("b", null)
        assertNotNull(cache.get("a")) // "a" is now the most recently used.
        cache.put("c", null)

        assertNotNull(cache.get("a"), "recently used entry must survive")
        assertNull(cache.get("b"), "stale entry must be the one evicted")
    }

    @Test
    fun rejectsNonPositiveCapacity() {
        assertFailsWith<IllegalArgumentException> { AnnotationImageCache(maxEntries = 0) }
        assertFailsWith<IllegalArgumentException> { AnnotationImageCache(maxEntries = -1) }
    }
}
