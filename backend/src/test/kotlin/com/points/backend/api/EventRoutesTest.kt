package com.points.backend.api

import com.points.backend.StorageContainer
import com.points.backend.configurePoints
import com.points.backend.db.DatabaseEventStorage
import com.points.backend.db.DatabasePointTypeStorage
import com.points.backend.plugins.h2DataSource
import com.points.shared.contract.PointEventDto
import com.points.shared.contract.PointTypeDto
import com.points.shared.contract.PointValueDto
import com.points.shared.contract.SyncRequestDto
import com.points.shared.contract.SyncResponseDto
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
import kotlin.test.assertTrue

class EventRoutesTest {

    private fun ApplicationTestBuilder.installPoints(syncPageSize: Int = 500) = application {
        val dataSource = h2DataSource("jdbc:h2:mem:routes_${UUID.randomUUID()};DB_CLOSE_DELAY=-1")
        configurePoints(
            StorageContainer(
                events = DatabaseEventStorage(dataSource),
                pointTypes = DatabasePointTypeStorage(dataSource),
            ),
            syncPageSize = syncPageSize,
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

    @Test
    fun syncUploadsPendingEventsAndReturnsTheOnesAClientIsMissing() = testApplication {
        installPoints()
        val client = createClient { install(ContentNegotiation) { json() } }
        val type = "type-1"
        fun event(id: String, delta: Long) =
            PointEventDto(id, OWNER, type, delta, "device-a", "2026-06-04T12:00:00Z")

        // Device A uploads two events from a fresh cursor and gets them back with the next cursor.
        val first: SyncResponseDto = client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequestDto(OWNER, sinceSeq = 0, events = listOf(event("e1", 1), event("e2", 1))))
        }.body()
        assertEquals(listOf("e1", "e2"), first.events.map { it.id })
        assertEquals(2L, first.events.size.toLong())

        // Device B, caught up to A's cursor, uploads its own event and pulls only what's newer.
        val second: SyncResponseDto = client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequestDto(OWNER, sinceSeq = first.nextSeq, events = listOf(event("e3", 1))))
        }.body()
        assertEquals(listOf("e3"), second.events.map { it.id })
        assertEquals(3L, client.get("/points/$type") { parameter("owner", OWNER) }.body<PointValueDto>().value)
    }

    @Test
    fun syncIsScopedToOwner() = testApplication {
        installPoints()
        val client = createClient { install(ContentNegotiation) { json() } }
        fun event(id: String, owner: String) =
            PointEventDto(id, owner, "type-1", 1, "device-a", "2026-06-04T12:00:00Z")

        client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequestDto("owner-b", 0, listOf(event("b1", "owner-b"))))
        }
        val forA: SyncResponseDto = client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequestDto("owner-a", 0, listOf(event("a1", "owner-a"))))
        }.body()
        // owner-a's pull never sees owner-b's event.
        assertEquals(listOf("a1"), forA.events.map { it.id })
    }

    @Test
    fun syncMergesPointTypesLastWriteWinsAndReturnsTheOnesAClientIsMissing() = testApplication {
        installPoints()
        val client = createClient { install(ContentNegotiation) { json() } }
        fun type(id: String, name: String, updatedAt: String) = PointTypeDto(
            id = id, ownerId = OWNER, name = name, hue = 215, icon = "drop", mode = "DAILY",
            step = 1, goal = "UP", target = 8, unit = "glasses",
            createdAt = "2026-06-04T12:00:00Z", updatedAt = updatedAt, deletedAt = null,
        )

        // Device A creates a type from a fresh type cursor and gets it back with the next cursor.
        val first: SyncResponseDto = client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequestDto(OWNER, 0, emptyList(), sinceTypeSeq = 0, pointTypes = listOf(type("t1", "Water", "2026-06-04T12:00:00Z"))))
        }.body()
        assertEquals(listOf("Water"), first.pointTypes.map { it.name })

        // Device B, caught up to A's type cursor, sees nothing new…
        val caughtUp: SyncResponseDto = client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequestDto(OWNER, 0, emptyList(), sinceTypeSeq = first.nextTypeSeq, pointTypes = emptyList()))
        }.body()
        assertEquals(emptyList(), caughtUp.pointTypes.map { it.name })

        // A newer rename wins and re-propagates (the type re-stamps its seq, so B pulls it again).
        client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequestDto(OWNER, 0, emptyList(), sinceTypeSeq = 0, pointTypes = listOf(type("t1", "Hydration", "2026-06-04T13:00:00Z"))))
        }
        val afterRename: SyncResponseDto = client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequestDto(OWNER, 0, emptyList(), sinceTypeSeq = first.nextTypeSeq, pointTypes = emptyList()))
        }.body()
        assertEquals(listOf("Hydration"), afterRename.pointTypes.map { it.name })

        // A stale rename (older updatedAt) is a no-op — last-write-wins.
        client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequestDto(OWNER, 0, emptyList(), sinceTypeSeq = 0, pointTypes = listOf(type("t1", "Stale", "2026-06-04T11:00:00Z"))))
        }
        val afterStale: SyncResponseDto = client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequestDto(OWNER, 0, emptyList(), sinceTypeSeq = afterRename.nextTypeSeq, pointTypes = emptyList()))
        }.body()
        assertEquals(emptyList(), afterStale.pointTypes.map { it.name }, "a stale update neither applies nor re-propagates")
    }

    @Test
    fun syncPointTypesAreScopedToOwner() = testApplication {
        installPoints()
        val client = createClient { install(ContentNegotiation) { json() } }
        fun type(id: String, owner: String) = PointTypeDto(
            id = id, ownerId = owner, name = "Water", hue = 215, icon = "drop", mode = "DAILY",
            step = 1, goal = "UP", target = 8, unit = "glasses",
            createdAt = "2026-06-04T12:00:00Z", updatedAt = "2026-06-04T12:00:00Z", deletedAt = null,
        )

        client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequestDto("owner-b", 0, emptyList(), sinceTypeSeq = 0, pointTypes = listOf(type("b1", "owner-b"))))
        }
        val forA: SyncResponseDto = client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequestDto("owner-a", 0, emptyList(), sinceTypeSeq = 0, pointTypes = listOf(type("a1", "owner-a"))))
        }.body()
        assertEquals(listOf("a1"), forA.pointTypes.map { it.id })
    }

    @Test
    fun malformedEventTimestampOnPostEventsReturns400() = testApplication {
        installPoints()
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.post("/events") {
            contentType(ContentType.Application.Json)
            setBody(PointEventDto("e1", OWNER, "type-1", 1, "device-a", "not-a-timestamp"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status, "an unparseable createdAt is the caller's error, not a 500")
    }

    @Test
    fun malformedEventTimestampInSyncReturns400WithoutPersistingTheBatch() = testApplication {
        installPoints()
        val client = createClient { install(ContentNegotiation) { json() } }
        val type = "type-1"

        val response = client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(
                SyncRequestDto(
                    OWNER,
                    sinceSeq = 0,
                    events = listOf(
                        PointEventDto("good", OWNER, type, 1, "device-a", "2026-06-04T12:00:00Z"),
                        PointEventDto("bad", OWNER, type, 1, "device-a", "not-a-timestamp"),
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)

        // The batch is validated before anything persists — no partial write slipped in ahead of the 400.
        val value: PointValueDto = client.get("/points/$type") { parameter("owner", OWNER) }.body()
        assertEquals(0L, value.value)
    }

    @Test
    fun malformedTypeTimestampInSyncReturns400() = testApplication {
        installPoints()
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(
                SyncRequestDto(
                    OWNER, 0, emptyList(), sinceTypeSeq = 0,
                    pointTypes = listOf(
                        PointTypeDto(
                            id = "t1", ownerId = OWNER, name = "Water", hue = 215, icon = "drop", mode = "DAILY",
                            step = 1, goal = "UP", target = 8, unit = "glasses",
                            createdAt = "2026-06-04T12:00:00Z", updatedAt = "not-a-timestamp", deletedAt = null,
                        ),
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun aValidSyncStillSucceedsAfterARejectedOne() = testApplication {
        installPoints()
        val client = createClient { install(ContentNegotiation) { json() } }
        val type = "type-1"

        client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequestDto(OWNER, 0, listOf(PointEventDto("bad", OWNER, type, 1, "device-a", "nope"))))
        }
        val ok: SyncResponseDto = client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequestDto(OWNER, 0, listOf(PointEventDto("good", OWNER, type, 2, "device-a", "2026-06-04T12:00:00Z"))))
        }.body()
        assertEquals(listOf("good"), ok.events.map { it.id })
    }

    @Test
    fun syncPagesLargeEventPullsAndSignalsHasMore() = testApplication {
        installPoints(syncPageSize = 2)
        val client = createClient { install(ContentNegotiation) { json() } }
        fun event(id: String) = PointEventDto(id, OWNER, "type-1", 1, "device-a", "2026-06-04T12:00:00Z")

        // Device A lands 5 events. Its own response is already windowed: 2 events, more to come.
        val upload: SyncResponseDto = client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequestDto(OWNER, 0, (1..5).map { event("e$it") }))
        }.body()
        assertEquals(2, upload.events.size, "a pull window never exceeds the page size")
        assertTrue(upload.hasMoreEvents)

        // A fresh device drains page by page until the server signals it is caught up.
        val seen = mutableListOf<String>()
        var cursor = 0L
        var hasMore = true
        var pulls = 0
        while (hasMore) {
            val page: SyncResponseDto = client.post("/sync") {
                contentType(ContentType.Application.Json)
                setBody(SyncRequestDto(OWNER, cursor, emptyList()))
            }.body()
            assertTrue(page.events.size <= 2, "every window is bounded")
            seen += page.events.map { it.id }
            cursor = page.nextSeq
            hasMore = page.hasMoreEvents
            pulls++
        }
        assertEquals(listOf("e1", "e2", "e3", "e4", "e5"), seen, "paging yields every event exactly once, in seq order")
        assertEquals(3, pulls, "5 events at page size 2 drain in 3 windows")
    }

    @Test
    fun syncPagesTypePullsAndSignalsHasMore() = testApplication {
        installPoints(syncPageSize = 2)
        val client = createClient { install(ContentNegotiation) { json() } }
        fun type(id: String) = PointTypeDto(
            id = id, ownerId = OWNER, name = "Type $id", hue = 215, icon = "drop", mode = "DAILY",
            step = 1, goal = "UP", target = null, unit = "glasses",
            createdAt = "2026-06-04T12:00:00Z", updatedAt = "2026-06-04T12:00:00Z", deletedAt = null,
        )

        client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequestDto(OWNER, 0, emptyList(), sinceTypeSeq = 0, pointTypes = listOf(type("t1"), type("t2"), type("t3"))))
        }

        val first: SyncResponseDto = client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequestDto(OWNER, 0, emptyList(), sinceTypeSeq = 0))
        }.body()
        assertEquals(listOf("t1", "t2"), first.pointTypes.map { it.id })
        assertTrue(first.hasMoreTypes)

        val second: SyncResponseDto = client.post("/sync") {
            contentType(ContentType.Application.Json)
            setBody(SyncRequestDto(OWNER, 0, emptyList(), sinceTypeSeq = first.nextTypeSeq))
        }.body()
        assertEquals(listOf("t3"), second.pointTypes.map { it.id })
        assertEquals(false, second.hasMoreTypes)
    }

    private companion object {
        const val OWNER = "owner-1"
    }
}
