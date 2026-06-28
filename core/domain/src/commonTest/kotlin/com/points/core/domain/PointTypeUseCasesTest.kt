package com.points.core.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PointTypeUseCasesTest {

    private fun type(
        name: String = "Water",
        deletedAt: Instant? = null,
    ) = PointType(
        id = Uuid.random(),
        name = name,
        hue = 215,
        icon = "drop",
        mode = PointMode.DAILY,
        step = 1,
        goal = PointGoal.UP,
        target = 8,
        unit = "glasses",
        createdAt = Instant.fromEpochSeconds(0),
        updatedAt = Instant.fromEpochSeconds(0),
        deletedAt = deletedAt,
    )

    @Test
    fun draftDefaultsMatchTheNewPointForm() {
        val draft = PointTypeDraft(name = "Books read")
        assertEquals(PointMode.CUMULATIVE, draft.mode)
        assertEquals(PointGoal.UP, draft.goal)
        assertEquals(1L, draft.step)
        assertNull(draft.target, "a new point has no daily target by default")
    }

    @Test
    fun isActiveTracksTheTombstone() {
        assertTrue(type(deletedAt = null).isActive)
        assertFalse(type(deletedAt = Instant.fromEpochSeconds(1)).isActive)
    }

    @Test
    fun createPointTypeDelegatesTheDraft() = runTest {
        var captured: PointTypeDraft? = null
        val created = type(name = "Push-ups")
        val create = CreatePointType { draft -> captured = draft; created }

        val result = create(PointTypeDraft(name = "Push-ups", step = 10))

        assertEquals("Push-ups", captured?.name)
        assertEquals(10L, captured?.step)
        assertEquals(created, result)
    }

    @Test
    fun editPointTypeForwardsIdAndDraftAndCanReturnNull() = runTest {
        val id = Uuid.random()
        var capturedId: Uuid? = null
        val edit = EditPointType { typeId, _ -> capturedId = typeId; null }

        assertNull(edit(id, PointTypeDraft(name = "Renamed")))
        assertEquals(id, capturedId)
    }

    @Test
    fun deletePointTypeForwardsTheId() = runTest {
        val id = Uuid.random()
        var deleted: Uuid? = null
        val delete = DeletePointType { typeId -> deleted = typeId }

        delete(id)
        assertEquals(id, deleted)
    }

    @Test
    fun observePointTypesStreamsTheActiveList() = runTest {
        val types = listOf(type(name = "Water"), type(name = "Meditation"))
        val observe = ObservePointTypes { flowOf(types) }

        assertEquals(listOf("Water", "Meditation"), observe().first().map { it.name })
    }
}
