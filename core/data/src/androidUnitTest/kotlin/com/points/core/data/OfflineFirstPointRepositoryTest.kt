package com.points.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.points.core.database.LocalEventDataSource
import com.points.core.domain.PointRepository
import com.points.core.network.PointsApiService
import com.points.core.network.pointsHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class OfflineFirstPointRepositoryTest {

    private val typeId = Uuid.random()

    /** Records the JSON bodies POSTed to /events. */
    private class RecordingApi {
        val posted = mutableListOf<String>()
        val service = PointsApiService(
            pointsHttpClient(
                MockEngine { request ->
                    posted += (request.body as TextContent).text
                    respond("", HttpStatusCode.Accepted)
                },
            ),
            "https://api.test",
        )
    }

    private fun newLocal(): LocalEventDataSource {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LocalEventDataSource.createSchema(driver)
        return LocalEventDataSource(driver, Dispatchers.Unconfined)
    }

    private fun repository(api: PointsApiService, local: LocalEventDataSource = newLocal()): PointRepository =
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
        val local = newLocal()
        val repo = repository(offlineApi, local)
        val event = repo.append(typeId, 3)
        assertEquals(typeId, event.pointTypeId)
        assertEquals(3L, event.delta)
        assertTrue(event.deviceId.isNotBlank())
        assertEquals(local.deviceId, event.deviceId) // stamped from the install's provisioned identity
    }

    @Test
    fun appendPushesEventToBackendWithOwner() = runTest {
        val api = RecordingApi()
        val local = newLocal()
        val repo = repository(api.service, local)
        repo.append(typeId, 2)
        assertEquals(1, api.posted.size)
        val body = api.posted.single()
        assertTrue(body.contains("\"delta\":2"), "posted: $body")
        assertTrue(body.contains("\"ownerId\":\"${local.ownerId}\""), "posted: $body")
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
}
