package com.points.backend.db

import com.points.backend.domain.StoredPointType
import com.points.backend.plugins.h2DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabasePointTypeStorageTest {

    private fun storage() =
        DatabasePointTypeStorage(h2DataSource("jdbc:h2:mem:types_${UUID.randomUUID()};DB_CLOSE_DELAY=-1"))

    private fun type(
        id: String,
        name: String = "Water",
        updatedAt: Long = 1_000,
        deletedAt: Long? = null,
        target: Long? = 8,
        ownerId: String = OWNER,
    ) = StoredPointType(
        id = id, ownerId = ownerId, name = name, hue = 215, icon = "drop", mode = "DAILY",
        step = 1, goal = "UP", target = target, unit = "glasses",
        createdAt = 500, updatedAt = updatedAt, deletedAt = deletedAt,
    )

    @Test
    fun upsertInsertsAndTypesSinceReturnsEveryField() = runTest {
        val storage = storage()
        storage.upsert(type("t1", target = null))
        val stored = storage.typesSince(OWNER, 0).single()
        assertEquals("t1", stored.id)
        assertEquals("Water", stored.name)
        assertNull(stored.target, "a null target round-trips")
        assertTrue(stored.seq > 0, "the store assigns a seq")
    }

    @Test
    fun upsertMergesLastWriteWinsByUpdatedAt() = runTest {
        val storage = storage()
        storage.upsert(type("t1", name = "Original", updatedAt = 1_000))

        storage.upsert(type("t1", name = "Newer", updatedAt = 2_000)) // newer wins
        assertEquals("Newer", storage.typesSince(OWNER, 0).single().name)

        storage.upsert(type("t1", name = "Stale", updatedAt = 1_500)) // older is a no-op
        assertEquals("Newer", storage.typesSince(OWNER, 0).single().name)
    }

    @Test
    fun acceptedUpdateReStampsSeqSoItRePropagates() = runTest {
        val storage = storage()
        storage.upsert(type("t1", updatedAt = 1_000))
        storage.upsert(type("t2", updatedAt = 1_000))
        val cursor = storage.typesSince(OWNER, 0).maxOf { it.seq } // a client caught up to here

        storage.upsert(type("t1", name = "Renamed", updatedAt = 2_000)) // accepted → new seq beyond cursor
        val missed = storage.typesSince(OWNER, cursor)
        assertEquals(listOf("Renamed"), missed.map { it.name })
        assertTrue(missed.single().seq > cursor)
    }

    @Test
    fun staleUpdateDoesNotReStampSeq() = runTest {
        val storage = storage()
        storage.upsert(type("t1", updatedAt = 2_000))
        val cursor = storage.typesSince(OWNER, 0).single().seq

        storage.upsert(type("t1", name = "Stale", updatedAt = 1_000)) // rejected → no new seq
        assertTrue(storage.typesSince(OWNER, cursor).isEmpty(), "a no-op update never re-propagates")
    }

    @Test
    fun tombstoneIsStoredAndReturnedSoDeletesPropagate() = runTest {
        val storage = storage()
        storage.upsert(type("t1", updatedAt = 1_000))
        storage.upsert(type("t1", updatedAt = 2_000, deletedAt = 2_000))
        assertEquals(2_000L, storage.typesSince(OWNER, 0).single().deletedAt)
    }

    @Test
    fun typesSinceReturnsOnlyNewerSeqInOrderScopedToOwner() = runTest {
        val storage = storage()
        storage.upsert(type("a1", ownerId = "owner-a", updatedAt = 1_000))
        storage.upsert(type("b1", ownerId = "owner-b", updatedAt = 1_000))
        storage.upsert(type("a2", ownerId = "owner-a", updatedAt = 1_000))

        val forA = storage.typesSince("owner-a", 0)
        assertEquals(listOf("a1", "a2"), forA.map { it.id })
        assertEquals(forA.map { it.seq }.sorted(), forA.map { it.seq }) // ascending
    }

    @Test
    fun typesSinceRespectsTheLimitInSeqOrder() = runTest {
        val storage = storage()
        (1..5).forEach { storage.upsert(type("t$it")) }

        val window = storage.typesSince(OWNER, 0, limit = 2)
        assertEquals(listOf("t1", "t2"), window.map { it.id }, "the window is the first N by seq")

        val rest = storage.typesSince(OWNER, window.last().seq, limit = 10)
        assertEquals(listOf("t3", "t4", "t5"), rest.map { it.id }, "the next window resumes from the cursor")
    }

    // A duplicate seq is silently skipped by the `seq > cursor` pull — a type change that never reaches
    // the other device, with no error anywhere. So uniqueness under concurrent writers is a hard invariant.
    @Test
    fun concurrentCreatesAssignUniqueSeqs() = runTest {
        val storage = storage()
        coroutineScope {
            (1..32).forEach { i -> launch(Dispatchers.Default) { storage.upsert(type("t$i")) } }
        }
        val seqs = storage.typesSince(OWNER, 0).map { it.seq }
        assertEquals(32, seqs.size)
        assertEquals(32, seqs.toSet().size, "concurrent accepted writes must never share a seq: $seqs")
    }

    // Two devices can sync the same brand-new type at the same moment (a shared owner creating offline).
    // Whoever loses the insert race must fall back to the LWW merge, not surface a 500.
    @Test
    fun concurrentUpsertsOfTheSameNewIdKeepOneRowWithoutErrors() = runTest {
        val storage = storage()
        coroutineScope {
            repeat(16) { launch(Dispatchers.Default) { storage.upsert(type("t1", updatedAt = 1_000)) } }
        }
        assertEquals(1, storage.typesSince(OWNER, 0).size)
    }

    @Test
    fun concurrentUpdatesConvergeToTheNewestWrite() = runTest {
        val storage = storage()
        storage.upsert(type("t1", name = "v0", updatedAt = 1_000))
        coroutineScope {
            (1..16).forEach { i ->
                launch(Dispatchers.Default) { storage.upsert(type("t1", name = "v$i", updatedAt = 1_000L + i)) }
            }
        }
        val stored = storage.typesSince(OWNER, 0).single()
        assertEquals("v16", stored.name, "last write by updatedAt wins regardless of arrival order")
        assertEquals(1_016L, stored.updatedAt)
    }

    private companion object {
        const val OWNER = "owner-1"
    }
}
