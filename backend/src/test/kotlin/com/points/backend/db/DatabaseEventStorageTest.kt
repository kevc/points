package com.points.backend.db

import com.points.backend.domain.StoredEvent
import com.points.backend.plugins.h2DataSource
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseEventStorageTest {

    private fun storage() =
        DatabaseEventStorage(h2DataSource("jdbc:h2:mem:store_${UUID.randomUUID()};DB_CLOSE_DELAY=-1"))

    private fun event(id: String, delta: Long) =
        StoredEvent(id = id, pointTypeId = TYPE, delta = delta, deviceId = "device-a", createdAt = TS)

    @Test
    fun valueIsZeroWhenEmpty() = runTest {
        assertEquals(0L, storage().valueFor(TYPE))
    }

    @Test
    fun sumsDeltas() = runTest {
        val storage = storage()
        storage.append(event("e1", 1))
        storage.append(event("e2", 1))
        storage.append(event("e3", -1))
        assertEquals(1L, storage.valueFor(TYPE))
    }

    @Test
    fun appendIsIdempotentById() = runTest {
        val storage = storage()
        storage.append(event("dup", 1))
        storage.append(event("dup", 1)) // same id → replace, not a second increment
        assertEquals(1L, storage.valueFor(TYPE))
    }

    private companion object {
        const val TYPE = "type-1"
        const val TS = "2026-06-04T12:00:00Z"
    }
}
