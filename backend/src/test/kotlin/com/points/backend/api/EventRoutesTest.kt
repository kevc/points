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
                setBody(PointEventDto(id, type, delta, "device-a", "2026-06-04T12:00:00Z"))
            }
            assertEquals(HttpStatusCode.Accepted, response.status)
        }

        post("e1", 1)
        post("e2", 1)
        post("e3", -1)

        val value: PointValueDto = client.get("/points/$type").body()
        assertEquals(PointValueDto(type, 1), value)
    }

    @Test
    fun unknownTypeReportsZero() = testApplication {
        installPoints()
        val client = createClient { install(ContentNegotiation) { json() } }

        val value: PointValueDto = client.get("/points/never-seen").body()
        assertEquals(0L, value.value)
    }

    @Test
    fun repostingSameEventIdDoesNotDoubleCount() = testApplication {
        installPoints()
        val client = createClient { install(ContentNegotiation) { json() } }
        val type = "type-1"
        val event = PointEventDto("dup", type, 1, "device-a", "2026-06-04T12:00:00Z")

        client.post("/events") { contentType(ContentType.Application.Json); setBody(event) }
        client.post("/events") { contentType(ContentType.Application.Json); setBody(event) }

        val value: PointValueDto = client.get("/points/$type").body()
        assertEquals(1L, value.value)
    }
}
