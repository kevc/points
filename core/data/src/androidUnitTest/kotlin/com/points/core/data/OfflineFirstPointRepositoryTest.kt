package com.points.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.points.core.database.LocalEventDataSource
import com.points.core.domain.PointRepository
import com.points.core.network.PointsApiService
import com.points.core.network.pointsHttpClient
import com.points.shared.contract.PointEventDto
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class OfflineFirstPointRepositoryTest {

    private val typeId = Uuid.random()

    /**
     * An in-memory stand-in for the backend `/sync` endpoint: insert-if-absent by id, server-assigned
     * monotonic seq, and an owner-scoped "events since cursor" pull — the same contract the real
     * `DatabaseEventStorage` implements. [online] flips the network off to exercise offline-first behavior.
     */
    private class FakeSyncBackend {
        var online: Boolean = true
        private val rows = mutableListOf<Pair<Long, PointEventDto>>()
        private var seq = 0L

        fun handle(request: SyncRequestDto): SyncResponseDto {
            request.events.forEach { event ->
                if (rows.none { it.second.id == event.id }) rows += ++seq to event
            }
            val missing = rows
                .filter { it.second.ownerId == request.ownerId && it.first > request.sinceSeq }
                .sortedBy { it.first }
            return SyncResponseDto(
                events = missing.map { it.second },
                nextSeq = missing.maxOfOrNull { it.first } ?: request.sinceSeq,
            )
        }
    }

    private fun apiFor(backend: FakeSyncBackend): PointsApiService {
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

    private fun localFor(ownerId: String? = null): LocalEventDataSource {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LocalEventDataSource.createSchema(driver)
        return LocalEventDataSource(driver, Dispatchers.Unconfined, ownerId = ownerId)
    }

    private fun repository(api: PointsApiService, local: LocalEventDataSource = localFor()): PointRepository =
        OfflineFirstPointRepository(local = local, api = api)

    private val offlineApi = PointsApiService(
        pointsHttpClient(MockEngine { error("offline") }),
        "https://api.test",
    )

    @Test
    fun appendsAccumulateIntoObservedValue() = runTest {
        val repo = repository(offlineApi)
        repo.append(typeId, 1)
        repo.append(typeId, 1)
        repo.append(typeId, -1)
        assertEquals(1L, repo.observeValue(typeId).first())
    }

    @Test
    fun appendReturnsTheStoredEventWithDeviceAttribution() = runTest {
        val local = localFor()
        val repo = repository(offlineApi, local)
        val event = repo.append(typeId, 3)
        assertEquals(typeId, event.pointTypeId)
        assertEquals(3L, event.delta)
        assertTrue(event.deviceId.isNotBlank())
        assertEquals(local.deviceId, event.deviceId) // stamped from the install's provisioned identity
    }

    @Test
    fun appendStillPersistsWhenNetworkFails() = runTest {
        val repo = repository(offlineApi) // engine throws on every request
        repo.append(typeId, 5)
        assertEquals(5L, repo.observeValue(typeId).first())
    }

    @Test
    fun incrementAndDecrementFactoriesAdjustTheValue() = runTest {
        val repo = repository(offlineApi)
        incrementPoint(repo, Dispatchers.Unconfined)(typeId, 1)
        incrementPoint(repo, Dispatchers.Unconfined)(typeId, 1)
        decrementPoint(repo, Dispatchers.Unconfined)(typeId, 2)
        assertEquals(0L, observePointValue(repo)(typeId).first())
    }

    @Test
    fun appendPersistsAsPendingAndSyncPushesToOtherDevices() = runTest {
        val backend = FakeSyncBackend()
        val local = localFor()
        val repo = repository(apiFor(backend), local)
        repo.append(typeId, 2)

        // append only persists — pushing is the SyncCoordinator's job (it observes the pending count).
        assertEquals(1, local.pendingEvents().size, "append leaves the event pending; it does not auto-push")

        repo.sync() // the coordinator drives this reactively in the app; here we invoke it directly
        assertTrue(local.pendingEvents().isEmpty(), "sync pushes and clears pending")

        // A second device on the same owner pulls the pushed event.
        val other = localFor(local.ownerId)
        repository(apiFor(backend), other).sync()
        assertEquals(2L, other.value(typeId))
    }

    @Test
    fun appendOfflineStaysPendingThenSyncsWhenOnline() = runTest {
        val backend = FakeSyncBackend().apply { online = false }
        val local = localFor()
        val repo = repository(apiFor(backend), local)

        repo.append(typeId, 7) // event stays pending until a sync pushes it
        assertEquals(1, local.pendingEvents().size)
        assertEquals(7L, repo.observeValue(typeId).first())

        backend.online = true
        repo.sync()
        assertTrue(local.pendingEvents().isEmpty())
    }

    @Test
    fun twoDevicesConvergeAdditivelyAfterSync() = runTest {
        val backend = FakeSyncBackend().apply { online = false }
        val owner = Uuid.random().toString()
        val deviceA = repository(apiFor(backend), localFor(owner))
        val deviceB = repository(apiFor(backend), localFor(owner))

        deviceA.append(typeId, 3) // both edits happen offline on separate devices
        deviceB.append(typeId, 5)
        assertEquals(3L, deviceA.observeValue(typeId).first())
        assertEquals(5L, deviceB.observeValue(typeId).first())

        backend.online = true
        deviceA.sync() // push A
        deviceB.sync() // push B, pull A
        deviceA.sync() // pull B

        // SUM(delta) is order-independent, so both devices converge to 3 + 5 regardless of edit order.
        assertEquals(8L, deviceA.observeValue(typeId).first())
        assertEquals(8L, deviceB.observeValue(typeId).first())
    }

    @Test
    fun repeatedSyncIsIdempotent() = runTest {
        val backend = FakeSyncBackend()
        val repo = repository(apiFor(backend), localFor())
        repo.append(typeId, 4)
        repo.sync()
        repo.sync()
        assertEquals(4L, repo.observeValue(typeId).first())
    }

    @Test
    fun syncPointEventsFactoryReconcilesThroughTheUseCase() = runTest {
        val backend = FakeSyncBackend().apply { online = false }
        val local = localFor()
        val repo = repository(apiFor(backend), local)
        repo.append(typeId, 6)
        assertEquals(1, local.pendingEvents().size)

        backend.online = true
        syncPointEvents(repo, Dispatchers.Unconfined)()
        assertTrue(local.pendingEvents().isEmpty())
    }
}
