package com.points.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.points.core.database.LocalEventDataSource
import com.points.core.database.LocalPointTypeDataSource
import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import com.points.core.domain.PointTypeDraft
import com.points.core.network.PointsApiService
import com.points.core.network.pointsHttpClient
import com.points.shared.contract.PointTypeDto
import com.points.shared.contract.SyncRequestDto
import com.points.shared.contract.SyncResponseDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class OfflineFirstPointTypeRepositoryTest {

    /** A clock the test advances explicitly, so last-write-wins ordering is deterministic (no ms races). */
    private class MutableClock(var instant: Instant = Instant.fromEpochSeconds(1_000)) : Clock {
        override fun now(): Instant = instant
    }

    /**
     * In-memory stand-in for the `/sync` type half: merge incoming types last-write-wins by `updatedAt`,
     * re-stamp a monotonic type-`seq` on every accepted change (so a rename re-propagates), and return the
     * owner's types newer than the client's type cursor — the same contract `DatabasePointTypeStorage` implements.
     */
    private class FakeTypeBackend {
        var online: Boolean = true
        private val rows = mutableMapOf<String, Pair<Long, PointTypeDto>>() // id -> (seq, dto)
        private var seq = 0L

        fun handle(request: SyncRequestDto): SyncResponseDto {
            request.pointTypes.forEach { incoming ->
                val existing = rows[incoming.id]
                if (existing == null ||
                    Instant.parse(incoming.updatedAt) > Instant.parse(existing.second.updatedAt)
                ) {
                    rows[incoming.id] = ++seq to incoming
                }
            }
            val missing = rows.values
                .filter { it.second.ownerId == request.ownerId && it.first > request.sinceTypeSeq }
                .sortedBy { it.first }
            return SyncResponseDto(
                events = emptyList(),
                nextSeq = request.sinceSeq,
                pointTypes = missing.map { it.second },
                nextTypeSeq = missing.maxOfOrNull { it.first } ?: request.sinceTypeSeq,
            )
        }
    }

    private fun apiFor(backend: FakeTypeBackend): PointsApiService {
        val json = Json
        val engine = MockEngine { request ->
            if (!backend.online) error("offline")
            val response = backend.handle(json.decodeFromString((request.body as TextContent).text))
            respond(
                content = json.encodeToString(response),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return PointsApiService(pointsHttpClient(engine), "https://api.test")
    }

    private class Local(ownerId: String? = null) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { LocalEventDataSource.createSchema(it) }
        val events = LocalEventDataSource(driver, Dispatchers.Unconfined, ownerId = ownerId)
        val types = LocalPointTypeDataSource(driver, Dispatchers.Unconfined, events.ownerId)
    }

    private fun repository(
        api: PointsApiService,
        local: Local = Local(),
        clock: Clock = Clock.System,
    ): OfflineFirstPointRepository =
        OfflineFirstPointRepository(local = local.events, types = local.types, api = api, clock = clock)

    private val offlineApi = PointsApiService(
        pointsHttpClient(MockEngine { error("offline") }),
        "https://api.test",
    )

    private val waterDraft = PointTypeDraft(
        name = "Water", hue = 215, icon = "drop", mode = PointMode.DAILY,
        step = 1, goal = PointGoal.UP, target = 8, unit = "glasses",
    )

    @Test
    fun createPersistsAndIsObservedWithItsDraftFields() = runTest {
        val repo = repository(offlineApi)
        val created = repo.create(waterDraft)

        assertEquals("Water", created.name)
        assertEquals(PointMode.DAILY, created.mode)
        assertEquals(8L, created.target)
        assertEquals(created.createdAt, created.updatedAt, "a fresh type's timestamps match")

        val observed = repo.observeTypes().first()
        assertEquals(listOf(created.id), observed.map { it.id })
    }

    @Test
    fun editUpdatesFieldsBumpsUpdatedAtAndPreservesCreatedAt() = runTest {
        val clock = MutableClock(Instant.fromEpochSeconds(100))
        val repo = repository(offlineApi, clock = clock)
        val created = repo.create(waterDraft)

        clock.instant = Instant.fromEpochSeconds(200)
        val edited = repo.edit(created.id, waterDraft.copy(name = "Hydration", target = 10))

        assertEquals("Hydration", edited?.name)
        assertEquals(10L, edited?.target)
        assertEquals(created.createdAt, edited?.createdAt, "createdAt is immutable across edits")
        assertEquals(Instant.fromEpochSeconds(200), edited?.updatedAt, "updatedAt is re-stamped")
        assertEquals(listOf("Hydration"), repo.observeTypes().first().map { it.name })
    }

    @Test
    fun editUnknownTypeReturnsNull() = runTest {
        assertNull(repository(offlineApi).edit(Uuid.random(), waterDraft))
    }

    @Test
    fun deleteTombstonesSoTheTypeLeavesTheActiveListAndIsIdempotent() = runTest {
        val repo = repository(offlineApi)
        val created = repo.create(waterDraft)
        assertEquals(1, repo.observeTypes().first().size)

        repo.delete(created.id)
        assertTrue(repo.observeTypes().first().isEmpty(), "a tombstoned type is hidden from the active list")

        repo.delete(created.id) // idempotent — no crash, still hidden
        assertTrue(repo.observeTypes().first().isEmpty())
    }

    @Test
    fun createSyncsToAnotherDeviceOnTheSameOwner() = runTest {
        val backend = FakeTypeBackend()
        val deviceA = Local()
        repository(apiFor(backend), deviceA).also { it.create(waterDraft); it.sync() }

        val deviceB = Local(deviceA.events.ownerId)
        val repoB = repository(apiFor(backend), deviceB)
        repoB.sync()

        assertEquals(listOf("Water"), repoB.observeTypes().first().map { it.name })
    }

    @Test
    fun renamePropagatesAcrossDevicesLastWriteWins() = runTest {
        val backend = FakeTypeBackend()
        val clock = MutableClock(Instant.fromEpochSeconds(1))
        val deviceA = Local()
        val repoA = repository(apiFor(backend), deviceA, clock)
        val created = repoA.create(waterDraft)
        repoA.sync()

        val deviceB = Local(deviceA.events.ownerId)
        val repoB = repository(apiFor(backend), deviceB)
        repoB.sync()
        assertEquals(listOf("Water"), repoB.observeTypes().first().map { it.name })

        clock.instant = Instant.fromEpochSeconds(2) // strictly newer than the create
        repoA.edit(created.id, waterDraft.copy(name = "Hydration"))
        repoA.sync()
        repoB.sync()

        assertEquals(listOf("Hydration"), repoB.observeTypes().first().map { it.name })
    }

    @Test
    fun deletePropagatesAsATombstone() = runTest {
        val backend = FakeTypeBackend()
        val clock = MutableClock(Instant.fromEpochSeconds(1))
        val deviceA = Local()
        val repoA = repository(apiFor(backend), deviceA, clock)
        val created = repoA.create(waterDraft)
        repoA.sync()

        val deviceB = Local(deviceA.events.ownerId)
        val repoB = repository(apiFor(backend), deviceB)
        repoB.sync()
        assertEquals(1, repoB.observeTypes().first().size)

        clock.instant = Instant.fromEpochSeconds(2)
        repoA.delete(created.id)
        repoA.sync()
        repoB.sync()

        assertTrue(repoB.observeTypes().first().isEmpty(), "B applies the tombstone and hides the type")
    }

    @Test
    fun concurrentEditsConvergeToTheLaterTimestamp() = runTest {
        val backend = FakeTypeBackend()
        val owner = Uuid.random().toString()
        val clockA = MutableClock(Instant.fromEpochSeconds(1))
        val clockB = MutableClock(Instant.fromEpochSeconds(1))
        val localA = Local(owner)
        val localB = Local(owner)
        val repoA = repository(apiFor(backend), localA, clockA)
        val repoB = repository(apiFor(backend), localB, clockB)

        // A creates and both devices learn about the type.
        val created = repoA.create(waterDraft)
        repoA.sync(); repoB.sync()

        // Both edit the same type offline; B's edit is stamped later, so it must win.
        clockA.instant = Instant.fromEpochSeconds(10)
        repoA.edit(created.id, waterDraft.copy(name = "From A"))
        clockB.instant = Instant.fromEpochSeconds(20)
        repoB.edit(created.id, waterDraft.copy(name = "From B"))

        // Reconcile in either order — last-write-wins by updatedAt converges both to "From B".
        repoA.sync(); repoB.sync(); repoA.sync()

        assertEquals(listOf("From B"), repoA.observeTypes().first().map { it.name })
        assertEquals(listOf("From B"), repoB.observeTypes().first().map { it.name })
    }

    @Test
    fun syncSkipsMalformedServerTypesAndAppliesTheRest() = runTest {
        val backend = FakeTypeBackend()
        val owner = Uuid.random().toString()

        fun dto(id: String, name: String, mode: String) = PointTypeDto(
            id = id, ownerId = owner, name = name, hue = 215, icon = "drop", mode = mode,
            step = 1, goal = "UP", target = null, unit = "glasses",
            createdAt = "2026-06-04T12:00:00Z", updatedAt = "2026-06-04T12:00:00Z", deletedAt = null,
        )

        // Another actor lands two malformed types (unknown mode enum, unparseable id) and one good one.
        backend.handle(
            SyncRequestDto(
                ownerId = owner,
                sinceSeq = 0,
                events = emptyList(),
                pointTypes = listOf(
                    dto(Uuid.random().toString(), name = "Poison mode", mode = "NOT_A_MODE"),
                    dto("not-a-uuid", name = "Poison id", mode = "DAILY"),
                    dto(Uuid.random().toString(), name = "Water", mode = "DAILY"),
                ),
            ),
        )

        val repo = repository(apiFor(backend), Local(owner))
        repo.sync() // must not throw — a poisoned type must not wedge every future reconcile

        assertEquals(listOf("Water"), repo.observeTypes().first().map { it.name }, "the well-formed type still applies")
        repo.sync() // the cursor advanced past the poison: reconciles keep succeeding
        assertEquals(listOf("Water"), repo.observeTypes().first().map { it.name })
    }
}
