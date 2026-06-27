package com.points.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.points.core.domain.PointEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class LocalEventDataSourceTest {

    private val typeId = Uuid.random()

    private fun newDataSource(): LocalEventDataSource {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LocalEventDataSource.createSchema(driver)
        return LocalEventDataSource(driver, Dispatchers.Unconfined)
    }

    private fun event(delta: Long, id: Uuid = Uuid.random(), at: Long = 0) = PointEvent(
        id = id,
        pointTypeId = typeId,
        delta = delta,
        deviceId = "device-a",
        createdAt = Instant.fromEpochMilliseconds(at),
    )

    @Test
    fun valueIsZeroForEmptyLedger() {
        assertEquals(0L, newDataSource().value(typeId))
    }

    @Test
    fun valueSumsDeltas() {
        val ds = newDataSource()
        ds.insert(event(1))
        ds.insert(event(1))
        ds.insert(event(-1))
        ds.insert(event(5))
        assertEquals(6L, ds.value(typeId))
    }

    @Test
    fun upsertIsIdempotentById() {
        val ds = newDataSource()
        val fixedId = Uuid.random()
        ds.insert(event(1, id = fixedId))
        ds.insert(event(1, id = fixedId)) // same id → replace, not a second increment
        assertEquals(1L, ds.value(typeId))
    }

    @Test
    fun valueIsScopedToPointType() {
        val ds = newDataSource()
        ds.insert(event(1))
        assertEquals(0L, ds.value(Uuid.random()))
    }

    @Test
    fun observeValueEmitsCurrentSum() = runTest {
        val ds = newDataSource()
        ds.insert(event(3))
        ds.insert(event(-1))
        assertEquals(2L, ds.observeValue(typeId).first())
    }

    @Test
    fun localInsertsArePendingUntilCleared() {
        val ds = newDataSource()
        val a = Uuid.random()
        val b = Uuid.random()
        ds.insert(event(1, id = a))
        ds.insert(event(1, id = b))
        assertEquals(setOf(a.toString(), b.toString()), ds.pendingEvents().map { it.id.toString() }.toSet())

        ds.clearPending(listOf(a.toString()))
        assertEquals(listOf(b.toString()), ds.pendingEvents().map { it.id.toString() })
    }

    @Test
    fun observePendingCountReflectsInsertsAndClears() = runTest {
        val ds = newDataSource()
        assertEquals(0L, ds.observePendingCount().first())

        val a = Uuid.random()
        ds.insert(event(1, id = a))
        ds.insert(event(1))
        assertEquals(2L, ds.observePendingCount().first())

        ds.clearPending(listOf(a.toString()))
        assertEquals(1L, ds.observePendingCount().first())

        // Events pulled from the server are already confirmed, so they never count as pending.
        ds.applySynced(event(1))
        assertEquals(1L, ds.observePendingCount().first())
    }

    @Test
    fun syncedEventsAreNotPending() {
        val ds = newDataSource()
        ds.applySynced(event(1))
        assertTrue(ds.pendingEvents().isEmpty())
        assertEquals(1L, ds.value(typeId)) // still counts toward the value
    }

    @Test
    fun cursorStartsAtZeroAndAdvances() {
        val ds = newDataSource()
        assertEquals(0L, ds.syncCursor())
        ds.setCursor(42)
        assertEquals(42L, ds.syncCursor())
    }

    @Test
    fun identityIsProvisionedAndStableAcrossReopen() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LocalEventDataSource.createSchema(driver)

        val first = LocalEventDataSource(driver, Dispatchers.Unconfined)
        assertTrue(first.ownerId.isNotBlank())
        assertTrue(first.deviceId.isNotBlank())

        // Reopening over the same database reuses the provisioned identity (no orphaned ledger).
        val second = LocalEventDataSource(driver, Dispatchers.Unconfined)
        assertEquals(first.ownerId, second.ownerId)
        assertEquals(first.deviceId, second.deviceId)
    }
}
