package com.points.backend.api

import com.points.backend.StorageContainer
import com.points.backend.domain.StoredEvent
import com.points.shared.contract.PointEventDto
import com.points.shared.contract.PointValueDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail

/**
 * The M2 (un-authenticated) ledger endpoints:
 *  - `POST /events`      — append one event (idempotent upsert by id).
 *  - `GET  /points/{id}` — the current value (`SUM(delta)`) for a point type.
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
            val value = storage.events.valueFor(pointTypeId)
            call.respond(PointValueDto(pointTypeId = pointTypeId, value = value))
        }
    }
}

private fun PointEventDto.toStored() = StoredEvent(
    id = id,
    pointTypeId = pointTypeId,
    delta = delta,
    deviceId = deviceId,
    createdAt = createdAt,
)
