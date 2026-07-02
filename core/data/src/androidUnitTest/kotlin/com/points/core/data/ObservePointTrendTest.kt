package com.points.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.points.core.database.LocalEventDataSource
import com.points.core.database.LocalPointTypeDataSource
import com.points.core.domain.PointMode
import com.points.core.domain.PointTrend
import com.points.core.domain.PointTypeDraft
import com.points.core.domain.TrendRange
import com.points.core.domain.series
import com.points.core.network.PointsApiService
import com.points.core.network.pointsHttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi

/**
 * [observePointTrend] through the port over an in-memory SQLite ledger: it buckets the point's events by local
 * day and re-emits reactively as the ledger and catalog change.
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)
class ObservePointTrendTest {

    private class MutableClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private class Local {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { LocalEventDataSource.createSchema(it) }
        val events = LocalEventDataSource(driver, Dispatchers.Unconfined)
        val types = LocalPointTypeDataSource(driver, Dispatchers.Unconfined, events.ownerId)
    }

    private val offlineApi = PointsApiService(pointsHttpClient(MockEngine { error("offline") }), "https://api.test")
    private val zone = TimeZone.UTC
    private val noon = Instant.parse("2026-06-15T12:00:00Z") // a Monday

    private fun setup(clock: MutableClock): Pair<OfflineFirstPointRepository, com.points.core.domain.ObservePointTrend> {
        val local = Local()
        val repo = OfflineFirstPointRepository(local.events, local.types, offlineApi, clock)
        return repo to observePointTrend(types = repo, points = repo, clock = clock, zone = { zone }, dayTicks = flowOf(Unit))
    }

    @Test
    fun bucketsACumulativeLedgerIntoAValueSeriesAndWindow() = runTest {
        val clock = MutableClock(noon)
        val (repo, observe) = setup(clock)
        val books = repo.create(PointTypeDraft(name = "Books", mode = PointMode.CUMULATIVE))
        clock.instant = Instant.parse("2026-06-13T12:00:00Z")
        repo.append(books.id, 10)
        clock.instant = noon
        repo.append(books.id, 20)

        val t = observe(books.id).first()
        assertEquals(30L, t.value)
        assertEquals(30L, t.deltaThisWeek, "both events fall in the last 7 days")
        val week = t.series(TrendRange.WEEK)
        assertEquals(20L, week.bars.last())
        assertEquals(30L, week.line.last(), "the cumulative line ends at the running total")
    }

    @Test
    fun dailyTrendValueIsTodaysBucket() = runTest {
        val clock = MutableClock(noon)
        val (repo, observe) = setup(clock)
        val water = repo.create(PointTypeDraft(name = "Water", mode = PointMode.DAILY, target = 8))
        clock.instant = Instant.parse("2026-06-14T10:00:00Z")
        repo.append(water.id, 3) // yesterday
        clock.instant = noon
        repo.append(water.id, 5) // today

        assertEquals(5L, observe(water.id).first().value)
    }

    @Test
    fun reEmitsAsTheLedgerChanges() = runTest {
        val clock = MutableClock(noon)
        val (repo, observe) = setup(clock)
        val meditate = repo.create(PointTypeDraft(name = "Meditate", mode = PointMode.CUMULATIVE))

        val values = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observe(meditate.id).collect { values += it.value }
        }
        runCurrent()
        repo.append(meditate.id, 1)
        runCurrent()
        repo.append(meditate.id, 1)
        runCurrent()

        assertEquals(listOf(0L, 1L, 2L), values, "the trend re-emits on each append")
    }

    @Test
    fun reflectsARename() = runTest {
        val clock = MutableClock(noon)
        val (repo, observe) = setup(clock)
        val t = repo.create(PointTypeDraft(name = "Old", mode = PointMode.CUMULATIVE))

        val names = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observe(t.id).collect { trend: PointTrend -> names += trend.type.name }
        }
        runCurrent()
        repo.edit(t.id, PointTypeDraft(name = "New", mode = PointMode.CUMULATIVE))
        runCurrent()

        assertEquals(listOf("Old", "New"), names)
    }
}
