package com.points.backend.api

import com.points.backend.StorageContainer
import com.points.backend.domain.StoredEvent
import com.points.backend.domain.StoredPointType
import com.points.shared.contract.PointEventDto
import com.points.shared.contract.PointTypeDto
import com.points.shared.contract.PointValueDto
import com.points.shared.contract.SyncRequestDto
import com.points.shared.contract.SyncResponseDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail
import java.time.Instant

/**
 * The (un-authenticated until M6) ledger endpoints, scoped by owner:
 *  - `POST /events`             — append one event (idempotent upsert by id; owner carried in the body).
 *  - `GET  /points/{id}?owner=` — the current value (`SUM(delta)`) for an owner's point type.
 *  - `POST /sync`               — batch: upsert the client's pending events (union by id) **and** point-type
 *                                 changes (last-write-wins by updatedAt), and return the ones it is missing
 *                                 for each (`seq` greater than the matching cursor) plus the next cursors.
 *
 * The `owner` (query param / request field) stands in for the authenticated principal until M6 replaces it.
 */
fun Application.configureEventRoutes(storage: StorageContainer) {
    routing {
        post("/events") {
            val event = call.receive<PointEventDto>()
            storage.events.append(event.toStored())
            call.respond(HttpStatusCode.Accepted)
        }

        get("/points/{id}") {
            val pointTypeId = call.parameters.getOrFail("id")
            val ownerId = call.request.queryParameters.getOrFail("owner")
            val value = storage.events.valueFor(ownerId, pointTypeId)
            call.respond(PointValueDto(pointTypeId = pointTypeId, value = value))
        }

        post("/sync") {
            val request = call.receive<SyncRequestDto>()

            // Event half: union by id, return what the client is missing.
            request.events.forEach { storage.events.append(it.toStored()) }
            val missingEvents = storage.events.eventsSince(request.ownerId, request.sinceSeq)

            // Type half: merge last-write-wins, return type changes newer than the client's type cursor.
            request.pointTypes.forEach { storage.pointTypes.upsert(it.toStored()) }
            val missingTypes = storage.pointTypes.typesSince(request.ownerId, request.sinceTypeSeq)

            call.respond(
                SyncResponseDto(
                    events = missingEvents.map { it.toDto() },
                    nextSeq = missingEvents.maxOfOrNull { it.seq } ?: request.sinceSeq,
                    pointTypes = missingTypes.map { it.toDto() },
                    nextTypeSeq = missingTypes.maxOfOrNull { it.seq } ?: request.sinceTypeSeq,
                ),
            )
        }
    }
}

// The wire carries createdAt as an ISO-8601 string; the server stores it as epoch millis so it can be
// ordered and range-queried. seq is left at its default — the database assigns the real value on insert.
private fun PointEventDto.toStored() = StoredEvent(
    id = id,
    ownerId = ownerId,
    pointTypeId = pointTypeId,
    delta = delta,
    deviceId = deviceId,
    createdAt = Instant.parse(createdAt).toEpochMilli(),
)

private fun StoredEvent.toDto() = PointEventDto(
    id = id,
    ownerId = ownerId,
    pointTypeId = pointTypeId,
    delta = delta,
    deviceId = deviceId,
    createdAt = Instant.ofEpochMilli(createdAt).toString(),
)

// Types carry createdAt/updatedAt/deletedAt as ISO-8601 strings on the wire; the server stores epoch millis
// so updatedAt can be compared for last-write-wins. seq is assigned by the store on upsert (0 on ingest).
private fun PointTypeDto.toStored() = StoredPointType(
    id = id,
    ownerId = ownerId,
    name = name,
    hue = hue,
    icon = icon,
    mode = mode,
    step = step,
    goal = goal,
    target = target,
    unit = unit,
    createdAt = Instant.parse(createdAt).toEpochMilli(),
    updatedAt = Instant.parse(updatedAt).toEpochMilli(),
    deletedAt = deletedAt?.let { Instant.parse(it).toEpochMilli() },
)

private fun StoredPointType.toDto() = PointTypeDto(
    id = id,
    ownerId = ownerId,
    name = name,
    hue = hue,
    icon = icon,
    mode = mode,
    step = step,
    goal = goal,
    target = target,
    unit = unit,
    createdAt = Instant.ofEpochMilli(createdAt).toString(),
    updatedAt = Instant.ofEpochMilli(updatedAt).toString(),
    deletedAt = deletedAt?.let { Instant.ofEpochMilli(it).toString() },
)
