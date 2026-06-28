package com.points.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.points.core.database.LocalEventDataSource
import com.points.core.database.LocalPointTypeDataSource
import com.points.core.domain.PointTypeDraft
import com.points.core.network.PointsApiService
import com.points.core.network.pointsHttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class ResetAndRestoreTest {

    private val offlineApi = PointsApiService(pointsHttpClient(MockEngine { error("offline") }), "https://api.test")

    private fun repo(): OfflineFirstPointRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { LocalEventDataSource.createSchema(it) }
        val events = LocalEventDataSource(driver, Dispatchers.Unconfined)
        val types = LocalPointTypeDataSource(driver, Dispatchers.Unconfined, events.ownerId)
        return OfflineFirstPointRepository(events, types, offlineApi)
    }

    private val io = Dispatchers.Unconfined

    @Test
    fun resetAppendsACompensatingDeltaToZeroWithoutErasingHistory() = runTest {
        val repo = repo()
        val type = repo.create(PointTypeDraft(name = "Push-ups", step = 10))
        repo.append(type.id, 10)
        repo.append(type.id, 3)
        assertEquals(13L, repo.currentValue(type.id))

        val resetEvent = resetPointType(repo, io)(type.id)
        assertEquals(-13L, resetEvent.delta, "reset is a compensating −value event")
        assertEquals(0L, repo.observeValue(type.id).first())
    }

    @Test
    fun undoingAResetReAddsTheAmount() = runTest {
        val repo = repo()
        val type = repo.create(PointTypeDraft(name = "Books"))
        repo.append(type.id, 5)

        val resetEvent = resetPointType(repo, io)(type.id)
        assertEquals(0L, repo.currentValue(type.id))

        // Undo = re-add the inverse of the reset's delta.
        incrementPoint(repo, io)(type.id, -resetEvent.delta)
        assertEquals(5L, repo.observeValue(type.id).first())
    }

    @Test
    fun resetOnAnEmptyCounterIsANoOpZero() = runTest {
        val repo = repo()
        val type = repo.create(PointTypeDraft(name = "New"))
        val resetEvent = resetPointType(repo, io)(type.id)
        assertEquals(0L, resetEvent.delta)
        assertEquals(0L, repo.currentValue(type.id))
    }

    @Test
    fun deleteThenRestoreBringsTheTypeBack() = runTest {
        val repo = repo()
        val type = repo.create(PointTypeDraft(name = "Water"))
        assertEquals(1, repo.observeTypes().first().size)

        deletePointType(repo, io)(type.id)
        assertTrue(repo.observeTypes().first().isEmpty(), "tombstoned type leaves the active list")

        restorePointType(repo, io)(type.id)
        assertEquals(listOf("Water"), repo.observeTypes().first().map { it.name }, "restore un-tombstones it")
    }

    @Test
    fun restoreOnALiveTypeIsANoOp() = runTest {
        val repo = repo()
        val type = repo.create(PointTypeDraft(name = "Water"))
        restorePointType(repo, io)(type.id)
        assertEquals(1, repo.observeTypes().first().size)
    }

    @Test
    fun restoredTypeKeepsItsLedgerHistory() = runTest {
        val repo = repo()
        val type = repo.create(PointTypeDraft(name = "Meditation"))
        repo.append(type.id, 7)
        deletePointType(repo, io)(type.id)
        restorePointType(repo, io)(type.id)
        // Events were never erased by the tombstone, so the value survives the round trip.
        assertEquals(7L, repo.observeValue(type.id).first())
    }
}
