package com.points.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.points.core.database.LocalEventDataSource
import com.points.core.database.LocalPointTypeDataSource
import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import com.points.core.domain.PointTile
import com.points.core.domain.PointTypeDraft
import com.points.core.domain.milestoneOf
import com.points.core.network.PointsApiService
import com.points.core.network.pointsHttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class ObserveTilesTest {

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
    private val noon = Instant.parse("2026-06-15T12:00:00Z") // "now" for all tests

    private fun setup(clock: MutableClock): Pair<OfflineFirstPointRepository, com.points.core.domain.ObserveTiles> {
        val local = Local()
        val repo = OfflineFirstPointRepository(local.events, local.types, offlineApi, clock)
        return repo to observeTiles(types = repo, points = repo, clock = clock, zone = zone)
    }

    @Test
    fun emptyCatalogYieldsNoTiles() = runTest {
        val (_, observe) = setup(MutableClock(noon))
        assertEquals(emptyList(), observe().first())
    }

    @Test
    fun cumulativeTileUsesAllTimeSumAndAMonochromeMilestoneRing() = runTest {
        val clock = MutableClock(noon)
        val (repo, observe) = setup(clock)
        val pushups = repo.create(PointTypeDraft(name = "Push-ups", mode = PointMode.CUMULATIVE, step = 10))
        repo.append(pushups.id, 10)
        repo.append(pushups.id, 20)

        val tile = observe().first().single()
        assertEquals(30L, tile.value, "cumulative value is the all-time sum")
        assertEquals(milestoneOf(30).progress, tile.ring.progress)
        assertEquals(milestoneOf(30).climbingNext, tile.climbingNext)
    }

    @Test
    fun dailyTileCountsOnlyTodayAgainstTheTarget() = runTest {
        val clock = MutableClock(noon)
        val (repo, observe) = setup(clock)
        val water = repo.create(PointTypeDraft(name = "Water", mode = PointMode.DAILY, target = 8, unit = "glasses"))

        clock.instant = Instant.parse("2026-06-14T10:00:00Z") // yesterday — excluded from "today"
        repo.append(water.id, 3)
        clock.instant = Instant.parse("2026-06-15T08:00:00Z") // today
        repo.append(water.id, 5)
        clock.instant = noon

        val tile = observe().first().single()
        assertEquals(5L, tile.value, "only today's cups count toward the daily tile")
        assertEquals(5f / 8f, tile.ring.progress)
        assertEquals(8, tile.ring.ticks)
        assertFalse(tile.ring.over)
    }

    @Test
    fun easingTileShowsARecencyGaugeFromLastActivity() = runTest {
        val clock = MutableClock(noon)
        val (repo, observe) = setup(clock)
        val anger = repo.create(PointTypeDraft(name = "Times angry", mode = PointMode.CUMULATIVE, goal = PointGoal.DOWN))

        clock.instant = Instant.parse("2026-06-10T12:00:00Z") // 5 days before noon
        repo.append(anger.id, 1)
        clock.instant = noon

        val tile = observe().first().single()
        assertEquals(5, tile.daysSinceLast)
        assertEquals(5f / 30f, tile.ring.progress)
    }

    @Test
    fun easingTileWithoutActivityReadsFullyCalm() = runTest {
        val clock = MutableClock(noon)
        val (repo, observe) = setup(clock)
        repo.create(PointTypeDraft(name = "Times angry", mode = PointMode.CUMULATIVE, goal = PointGoal.DOWN))

        val tile = observe().first().single()
        assertEquals(1f, tile.ring.progress, "no anger ever → maximally calm")
    }

    @Test
    fun oneTilePerActiveTypeExcludingTombstoned() = runTest {
        val clock = MutableClock(noon)
        val (repo, observe) = setup(clock)
        repo.create(PointTypeDraft(name = "Water", mode = PointMode.DAILY, target = 8))
        val books = repo.create(PointTypeDraft(name = "Books read"))
        repo.create(PointTypeDraft(name = "Meditation"))
        repo.delete(books.id) // tombstoned → not a tile

        val names = observe().first().map { it.type.name }.toSet()
        assertEquals(setOf("Water", "Meditation"), names)
    }

    @Test
    fun tilesAreTypedAsPointTile() = runTest {
        val clock = MutableClock(noon)
        val (repo, observe) = setup(clock)
        repo.create(PointTypeDraft(name = "Water"))
        val tiles: List<PointTile> = observe().first()
        assertEquals(1, tiles.size)
    }
}
