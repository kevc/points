package com.points.core.network

import com.points.shared.contract.PointEventDto
import com.points.shared.contract.PointValueDto
import com.points.shared.contract.SyncRequestDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PointsApiServiceTest {

    @Test
    fun postEventSendsJsonToEventsEndpoint() = runTest {
        lateinit var path: String
        lateinit var method: HttpMethod
        lateinit var body: String
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            method = request.method
            body = (request.body as TextContent).text
            respond("", HttpStatusCode.OK)
        }
        val service = PointsApiService(pointsHttpClient(engine), "https://api.test")

        service.postEvent(
            PointEventDto(
                id = "id-1",
                ownerId = "owner-1",
                pointTypeId = "type-1",
                delta = 2,
                deviceId = "device-a",
                createdAt = "2026-06-04T12:00:00Z",
            ),
        )

        assertEquals("/events", path)
        assertEquals(HttpMethod.Post, method)
        assertTrue(body.contains("\"delta\":2"), "body was: $body")
        assertTrue(body.contains("\"pointTypeId\":\"type-1\""), "body was: $body")
        assertTrue(body.contains("\"ownerId\":\"owner-1\""), "body was: $body")
    }

    @Test
    fun getValueParsesResponse() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/points/type-1", request.url.encodedPath)
            assertEquals("owner-1", request.url.parameters["owner"])
            respond(
                content = """{"pointTypeId":"type-1","value":7}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val service = PointsApiService(pointsHttpClient(engine), "https://api.test")

        assertEquals(PointValueDto("type-1", 7), service.getValue("owner-1", "type-1"))
    }

    @Test
    fun syncPostsRequestAndParsesResponse() = runTest {
        lateinit var path: String
        lateinit var body: String
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            body = (request.body as TextContent).text
            respond(
                content = """{"events":[],"nextSeq":5}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val service = PointsApiService(pointsHttpClient(engine), "https://api.test")

        val response = service.sync(SyncRequestDto(ownerId = "owner-1", sinceSeq = 3, events = emptyList()))

        assertEquals("/sync", path)
        assertTrue(body.contains("\"sinceSeq\":3"), "body was: $body")
        assertTrue(body.contains("\"ownerId\":\"owner-1\""), "body was: $body")
        assertEquals(5L, response.nextSeq)
    }
}
