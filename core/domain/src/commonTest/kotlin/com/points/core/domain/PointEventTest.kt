package com.points.core.domain

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PointEventTest {

    private val typeId = Uuid.random()

    private fun event(delta: Long, at: Long): PointEvent =
        PointEvent(
            id = Uuid.random(),
            pointTypeId = typeId,
            delta = delta,
            deviceId = "device-a",
            createdAt = Instant.fromEpochSeconds(at),
        )

    @Test
    fun emptyLedgerValueIsZero() {
        assertEquals(0L, emptyList<PointEvent>().currentValue())
    }

    @Test
    fun valueIsTheSumOfDeltas() {
        val events = listOf(event(1, 1), event(1, 2), event(-1, 3), event(5, 4))
        assertEquals(6L, events.currentValue())
    }

    @Test
    fun valueIsOrderIndependent() {
        val events = listOf(event(3, 1), event(-1, 2), event(10, 3), event(-7, 4))
        assertEquals(events.currentValue(), events.reversed().currentValue())
        assertEquals(events.currentValue(), events.shuffled().currentValue())
    }

    @Test
    fun decrementsCanDriveTheValueNegative() {
        val events = listOf(event(1, 1), event(-1, 2), event(-1, 3))
        assertEquals(-1L, events.currentValue())
    }
}
