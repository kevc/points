package com.points.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import com.points.core.domain.PointType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class LocalPointTypeDataSourceTest {

    /** A point-type source over a fresh in-memory db, scoped to a provisioned owner. */
    private fun newDataSource(): LocalPointTypeDataSource {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LocalEventDataSource.createSchema(driver)
        val owner = LocalEventDataSource(driver, Dispatchers.Unconfined).ownerId
        return LocalPointTypeDataSource(driver, Dispatchers.Unconfined, owner)
    }

    private fun type(
        id: Uuid = Uuid.random(),
        name: String = "Water",
        updatedAt: Long = 1_000,
        deletedAt: Long? = null,
    ) = PointType(
        id = id,
        name = name,
        hue = 215,
        icon = "drop",
        mode = PointMode.DAILY,
        step = 1,
        goal = PointGoal.UP,
        target = 8,
        unit = "glasses",
        createdAt = Instant.fromEpochMilliseconds(500),
        updatedAt = Instant.fromEpochMilliseconds(updatedAt),
        deletedAt = deletedAt?.let { Instant.fromEpochMilliseconds(it) },
    )

    @Test
    fun localUpsertRoundTripsEveryField() {
        val ds = newDataSource()
        val t = type(name = "Push-ups").copy(
            hue = 40, icon = "bolt", mode = PointMode.CUMULATIVE, step = 10,
            goal = PointGoal.DOWN, target = null, unit = "reps",
        )
        ds.upsertLocal(t)
        assertEquals(t, ds.typeById(t.id), "every field round-trips through the row, incl. a null target")
    }

    @Test
    fun activeTypesExcludesTombstonedAndIsObserved() = runTest {
        val ds = newDataSource()
        val water = type(name = "Water")
        val books = type(name = "Books read")
        ds.upsertLocal(water)
        ds.upsertLocal(books)
        ds.upsertLocal(
            books.copy(
                deletedAt = Instant.fromEpochMilliseconds(2_000),
                updatedAt = Instant.fromEpochMilliseconds(2_000),
            ),
        )

        assertEquals(listOf("Water"), ds.activeTypes().map { it.name })
        assertEquals(listOf("Water"), ds.observeTypes().first().map { it.name })
        // The tombstoned row still exists (soft delete), just hidden from the active list.
        assertTrue(ds.typeById(books.id)?.isActive == false)
    }

    @Test
    fun localChangesArePendingUntilCleared() = runTest {
        val ds = newDataSource()
        val a = type(name = "A")
        val b = type(name = "B")
        ds.upsertLocal(a)
        ds.upsertLocal(b)
        assertEquals(2L, ds.observePendingTypeCount().first())
        assertEquals(setOf("A", "B"), ds.pendingTypes().map { it.name }.toSet())

        ds.clearPending(listOf(a.id.toString()))
        assertEquals(listOf("B"), ds.pendingTypes().map { it.name })
        assertEquals(1L, ds.observePendingTypeCount().first())
    }

    @Test
    fun applySyncedInsertsAsConfirmedAndMergesLastWriteWins() {
        val ds = newDataSource()
        val id = Uuid.random()

        // A pulled type is confirmed (not pending) on insert.
        ds.applySynced(type(id = id, name = "Original", updatedAt = 1_000))
        assertEquals("Original", ds.typeById(id)?.name)
        assertTrue(ds.pendingTypes().isEmpty())

        // A newer remote edit wins…
        ds.applySynced(type(id = id, name = "Newer", updatedAt = 2_000))
        assertEquals("Newer", ds.typeById(id)?.name)

        // …a staler one is a no-op (last-write-wins by updatedAt).
        ds.applySynced(type(id = id, name = "Stale", updatedAt = 1_500))
        assertEquals("Newer", ds.typeById(id)?.name)
    }

    @Test
    fun applySyncedDoesNotClobberANewerLocalEdit() {
        val ds = newDataSource()
        val id = Uuid.random()
        ds.upsertLocal(type(id = id, name = "Local edit", updatedAt = 5_000))

        // An older remote copy must not overwrite the newer pending local edit.
        ds.applySynced(type(id = id, name = "Old remote", updatedAt = 3_000))
        assertEquals("Local edit", ds.typeById(id)?.name)
        assertEquals(1, ds.pendingTypes().size, "the local edit stays pending for the next push")
    }

    @Test
    fun typeByIdIsNullWhenUnknown() {
        assertNull(newDataSource().typeById(Uuid.random()))
    }

    @Test
    fun typeCursorStartsAtZeroAndAdvances() {
        val ds = newDataSource()
        assertEquals(0L, ds.typeCursor())
        ds.setTypeCursor(7)
        assertEquals(7L, ds.typeCursor())
    }
}
