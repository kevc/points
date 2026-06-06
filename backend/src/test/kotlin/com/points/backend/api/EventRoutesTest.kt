package com.points.backend.api

import com.points.backend.StorageContainer
import com.points.backend.configurePoints
import com.points.backend.db.DatabaseEventStorage
import com.points.backend.plugins.h2DataSource
import com.points.shared.contract.PointEventDto
import com.points.shared.contract.PointValueDto
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class EventRoutesTest {

    private fun ApplicationTestBuilder.installPoints() = application {
        configurePoints(
            StorageContainer(
                DatabaseEventStorage(h2DataSource("jdbc:h2:mem:routes_${UUID.randomUUID()};DB_CLOSE_DELAY=-1")),
            ),
        )
    }

    @Test
    fun postedEventsAccumulateIntoTheValue() = testApplication {
        installPoints()
        val client = createClient { install(ContentNegotiation) { json() } }
        val type = "type-1"

        suspend fun post(id: String, delta: Long) {
            val response = client.post("/events") {
                contentType(ContentType.Application.Json)
                setBody(PointEventDto(id, OWNER, type, delta, "device-a", "2026-06-04T12:00:00Z"))
            }
            assertEquals(HttpStatusCode.Accepted, response.status)
        }

        post("e1", 1)
        post("e2", 1)
        post("e3", -1)

        val value: PointValueDto = client.get("/points/$type") { parameter("owner", OWNER) }.body()
        assertEquals(PointValueDto(type, 1), value)
    }

    @Test
    fun unknownTypeReportsZero() = testApplication {
        installPoints()
        val client = createClient { install(ContentNegotiation) { json() } }

        val value: PointValueDto = client.get("/points/never-seen") { parameter("owner", OWNER) }.body()
        assertEquals(0L, value.value)
    }

    @Test
    fun repostingSameEventIdDoesNotDoubleCount() = testApplication {
        installPoints()
        val client = createClient { install(ContentNegotiation) { json() } }
        val type = "type-1"
        val event = PointEventDto("dup", OWNER, type, 1, "device-a", "2026-06-04T12:00:00Z")

        client.post("/events") { contentType(ContentType.Application.Json); setBody(event) }
        client.post("/events") { contentType(ContentType.Application.Json); setBody(event) }

        val value: PointValueDto = client.get("/points/$type") { parameter("owner", OWNER) }.body()
        assertEquals(1L, value.value)
    }

    @Test
    fun valuesAreIsolatedPerOwner() = testApplication {
        installPoints()
        val client = createClient { install(ContentNegotiation) { json() } }
        val type = "type-1"

        suspend fun post(id: String, owner: String, delta: Long) {
            client.post("/events") {
                contentType(ContentType.Application.Json)
                setBody(PointEventDto(id, owner, type, delta, "device-a", "2026-06-04T12:00:00Z"))
            }
        }
        post("a1", "owner-a", 5)
        post("b1", "owner-b", 3)

        assertEquals(5L, client.get("/points/$type") { parameter("owner", "owner-a") }.body<PointValueDto>().value)
        assertEquals(3L, client.get("/points/$type") { parameter("owner", "owner-b") }.body<PointValueDto>().value)
    }

    private companion object {
        const val OWNER = "owner-1"
    }
}
