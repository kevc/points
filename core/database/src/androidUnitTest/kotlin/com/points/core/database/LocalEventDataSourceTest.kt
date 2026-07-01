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

    private fun event(delta: Long, id: Uuid = Uuid.random(), at: Long = 0, type: Uuid = typeId) = PointEvent(
        id = id,
        pointTypeId = type,
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
    fun aggregatesGroupsEveryTypeUnderOneOwnerInOneQuery() = runTest {
        val ds = newDataSource()
        val water = Uuid.random()
        val anger = Uuid.random()

        // Water: 2 yesterday + (3 + 1) today; latest positive at 9_000.
        ds.insert(event(2, at = 1_000, type = water))
        ds.insert(event(3, at = 5_000, type = water))
        ds.insert(event(1, at = 9_000, type = water))
        // Anger: one positive then a compensating reset — a decrement is not "activity" for recency.
        ds.insert(event(1, at = 3_000, type = anger))
        ds.insert(event(-1, at = 8_000, type = anger))

        val byType = ds.observeAggregates(sinceMillis = 5_000).first()

        assertEquals(setOf(water, anger), byType.keys, "one row per type that has events")
        assertEquals(6L, byType.getValue(water).total, "all-time sum")
        assertEquals(4L, byType.getValue(water).todayTotal, "today's window = 3 + 1")
        assertEquals(9_000L, byType.getValue(water).lastPositiveAt)
        assertEquals(0L, byType.getValue(anger).total, "1 + (-1) reset")
        assertEquals(3_000L, byType.getValue(anger).lastPositiveAt, "the reset decrement is not recency")
    }

    @Test
    fun aggregatesOmitsTypesWithNoEventsAndNullsRecencyWhenNoPositive() = runTest {
        val ds = newDataSource()
        val onlyNegative = Uuid.random()
        ds.insert(event(-2, at = 4_000, type = onlyNegative))

        val byType = ds.observeAggregates(sinceMillis = 0).first()
        assertEquals(null, byType[Uuid.random()], "a type with no events is absent")
        assertEquals(null, byType.getValue(onlyNegative).lastPositiveAt, "no positive event → null recency")
        assertEquals(-2L, byType.getValue(onlyNegative).total)
    }

    @Test
    fun aggregatesFoldsTodayIntoAllTimeWhenSinceIsZero() = runTest {
        val ds = newDataSource()
        ds.insert(event(2, at = 1_000))
        ds.insert(event(3, at = 9_000))
        val agg = ds.observeAggregates(sinceMillis = 0).first().getValue(typeId)
        assertEquals(agg.total, agg.todayTotal, "since 0 → today window == all-time")
        assertEquals(5L, agg.total)
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
