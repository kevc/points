package com.points.core.domain

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PointUseCasesTest {

    private val typeId = Uuid.random()

    private fun event(delta: Long) = PointEvent(
        id = Uuid.random(),
        pointTypeId = typeId,
        delta = delta,
        deviceId = "device-a",
        createdAt = Instant.fromEpochSeconds(0),
    )

    @Test
    fun incrementExtensionDefaultsToOne() = runTest {
        var captured = 0L
        val increment = IncrementPoint { _, delta -> captured = delta; event(delta) }

        increment(typeId)
        assertEquals(1L, captured)

        increment(typeId, 5)
        assertEquals(5L, captured)
    }

    @Test
    fun decrementExtensionDefaultsToOne() = runTest {
        var captured = 0L
        val decrement = DecrementPoint { _, delta -> captured = delta; event(-delta) }

        decrement(typeId)
        assertEquals(1L, captured)

        decrement(typeId, 3)
        assertEquals(3L, captured)
    }
}
