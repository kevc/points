package com.points.core.network

import com.points.shared.contract.PointEventDto
import com.points.shared.contract.PointValueDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Thin client over the Points backend. The M2 endpoints are un-authenticated:
 *  - `POST /events`               — append one event (idempotent upsert by id server-side)
 *  - `GET  /points/{id}?owner=`   — current value (`SUM(delta)`) for an owner's point type
 */
class PointsApiService(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun postEvent(event: PointEventDto) {
        client.post("$baseUrl/events") {
            contentType(ContentType.Application.Json)
            setBody(event)
        }
    }

    suspend fun getValue(ownerId: String, pointTypeId: String): PointValueDto =
        client.get("$baseUrl/points/$pointTypeId") { parameter("owner", ownerId) }.body()
}
