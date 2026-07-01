package com.points.core.presentation.edit

import com.arkivanov.mvikotlin.core.utils.isAssertOnMainThreadEnabled
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.points.core.domain.CreatePointType
import com.points.core.domain.EditPointType
import com.points.core.domain.ObservePointTypes
import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import com.points.core.domain.PointType
import com.points.core.domain.PointTypeDraft
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CreateEditStoreTest {

    @BeforeTest
    fun setup() {
        isAssertOnMainThreadEnabled = false
    }

    private var createdDraft: PointTypeDraft? = null
    private var editedId: Uuid? = null
    private var editedDraft: PointTypeDraft? = null

    private fun typeFrom(draft: PointTypeDraft, id: Uuid = Uuid.random()) = PointType(
        id = id, name = draft.name, hue = draft.hue, icon = draft.icon, mode = draft.mode,
        step = draft.step, goal = draft.goal, target = draft.target, unit = draft.unit,
        createdAt = Instant.fromEpochSeconds(0), updatedAt = Instant.fromEpochSeconds(0),
    )

    private fun store(
        editId: Uuid? = null,
        existing: List<PointType> = emptyList(),
    ): CreateEditStore {
        val create = CreatePointType { draft -> createdDraft = draft; typeFrom(draft) }
        val edit = EditPointType { id, draft -> editedId = id; editedDraft = draft; typeFrom(draft, id) }
        val observe = ObservePointTypes { flowOf(existing) }
        return DefaultStoreFactory().createEditStore(editId, create, edit, observe, UnconfinedTestDispatcher())
    }

    @Test
    fun defaultsAreANewClimbingCumulativeForm() {
        val s = store().state
        assertFalse(s.editing)
        assertEquals(PointMode.CUMULATIVE, s.mode)
        assertEquals(PointGoal.UP, s.goal)
        assertEquals(1L, s.step)
        assertEquals(0L, s.target, "target defaults to Off")
    }

    @Test
    fun stepStepperFollowsTheNonLinearLadder() {
        val store = store()
        repeat(9) { store.accept(CreateEditStore.Intent.StepUp) } // 1 -> 10 (by 1)
        assertEquals(10L, store.state.step)
        store.accept(CreateEditStore.Intent.StepUp) // 10 -> 15 (by 5 at/above 10)
        assertEquals(15L, store.state.step)
        store.accept(CreateEditStore.Intent.StepDown) // 15 -> 10 (by 5 above 10)
        assertEquals(10L, store.state.step)
        store.accept(CreateEditStore.Intent.StepDown) // 10 -> 9 (by 1)
        assertEquals(9L, store.state.step)
    }

    @Test
    fun stepNeverDropsBelowOne() {
        val store = store()
        repeat(5) { store.accept(CreateEditStore.Intent.StepDown) }
        assertEquals(1L, store.state.step)
    }

    @Test
    fun targetStepperFloorsAtZeroOff() {
        val store = store()
        store.accept(CreateEditStore.Intent.TargetDown)
        assertEquals(0L, store.state.target)
        store.accept(CreateEditStore.Intent.TargetUp)
        assertEquals(1L, store.state.target)
    }

    @Test
    fun saveBuildsADraftFromTheForm() {
        val store = store()
        store.accept(CreateEditStore.Intent.SetName("  Water  "))
        store.accept(CreateEditStore.Intent.SetHue(215))
        store.accept(CreateEditStore.Intent.SetMode(PointMode.DAILY))
        repeat(8) { store.accept(CreateEditStore.Intent.TargetUp) }
        store.accept(CreateEditStore.Intent.Save)

        val draft = createdDraft!!
        assertEquals("Water", draft.name, "name is trimmed")
        assertEquals(215, draft.hue)
        assertEquals(PointMode.DAILY, draft.mode)
        assertEquals(8L, draft.target)
        assertEquals(PointGoal.UP, draft.goal, "daily forces an Up tone")
    }

    @Test
    fun blankNameFallsBackToUntitled() {
        val store = store()
        store.accept(CreateEditStore.Intent.Save)
        assertEquals("Untitled", createdDraft!!.name)
    }

    @Test
    fun cumulativeSaveDropsAnyDailyTarget() {
        val store = store()
        store.accept(CreateEditStore.Intent.SetMode(PointMode.CUMULATIVE))
        store.accept(CreateEditStore.Intent.SetGoal(PointGoal.DOWN))
        repeat(5) { store.accept(CreateEditStore.Intent.TargetUp) } // target ignored for cumulative
        store.accept(CreateEditStore.Intent.Save)

        assertNull(createdDraft!!.target, "a cumulative type has no daily target")
        assertEquals(PointGoal.DOWN, createdDraft!!.goal, "cumulative keeps its tone")
    }

    @Test
    fun editLoadsTheExistingTypeAndSaveEditsIt() {
        val id = Uuid.random()
        val existing = typeFrom(
            PointTypeDraft(name = "Water", hue = 215, icon = "drop", mode = PointMode.DAILY, target = 8, unit = "glasses"),
            id = id,
        )
        val store = store(editId = id, existing = listOf(existing))

        assertTrue(store.state.editing)
        assertEquals("Water", store.state.name)
        assertEquals(8L, store.state.target)

        store.accept(CreateEditStore.Intent.SetName("Hydration"))
        store.accept(CreateEditStore.Intent.Save)

        assertEquals(id, editedId)
        assertEquals("Hydration", editedDraft!!.name)
        assertEquals("glasses", editedDraft!!.unit, "immutable unit is carried through an edit")
        assertNull(createdDraft, "editing must not create a new type")
    }
}
