package com.points.shared.contract

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PointDtosTest {

    private val json = Json

    @Test
    fun pointEventDtoRoundTrips() {
        val dto = PointEventDto(
            id = "11111111-1111-1111-1111-111111111111",
            ownerId = "00000000-0000-0000-0000-000000000000",
            pointTypeId = "22222222-2222-2222-2222-222222222222",
            delta = -3,
            deviceId = "device-a",
            createdAt = "2026-06-04T12:00:00Z",
        )
        assertEquals(dto, json.decodeFromString<PointEventDto>(json.encodeToString(dto)))
    }

    @Test
    fun pointValueDtoRoundTrips() {
        val dto = PointValueDto(pointTypeId = "22222222-2222-2222-2222-222222222222", value = 42)
        assertEquals(dto, json.decodeFromString<PointValueDto>(json.encodeToString(dto)))
    }
}
