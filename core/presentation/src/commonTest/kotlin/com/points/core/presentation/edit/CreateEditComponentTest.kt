package com.points.core.presentation.edit

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CreateEditComponentTest {

    @BeforeTest
    fun setup() {
        isAssertOnMainThreadEnabled = false
    }

    private fun component(
        onSaved: () -> Unit,
        onCancel: () -> Unit,
    ) = DefaultCreateEditComponent(
        componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
        storeFactory = DefaultStoreFactory(),
        editId = null,
        create = CreatePointType { draft ->
            PointType(
                id = Uuid.random(), name = draft.name, hue = draft.hue, icon = draft.icon, mode = draft.mode,
                step = draft.step, goal = draft.goal, target = draft.target, unit = draft.unit,
                createdAt = Instant.fromEpochSeconds(0), updatedAt = Instant.fromEpochSeconds(0),
            )
        },
        edit = EditPointType { _, _ -> null },
        observeTypes = ObservePointTypes { flowOf(emptyList()) },
        mainContext = UnconfinedTestDispatcher(),
        onSaved = onSaved,
        onCancelled = onCancel,
    )

    @Test
    fun saveInvokesOnSavedOnce() {
        var saved = 0
        var cancelled = 0
        val c = component(onSaved = { saved++ }, onCancel = { cancelled++ })
        c.onName("Books read")
        c.onSave()
        assertTrue(saved == 1, "onSaved fires after the save completes")
        assertFalse(cancelled > 0)
    }

    @Test
    fun cancelInvokesOnCancelledWithoutSaving() {
        var saved = 0
        var cancelled = 0
        val c = component(onSaved = { saved++ }, onCancel = { cancelled++ })
        c.onCancel()
        assertTrue(cancelled == 1)
        assertFalse(saved > 0)
    }

    @Test
    fun draftDefaultsRemainAvailable() {
        // Sanity: the shared draft default object is what the form starts from.
        val d = PointTypeDraft(name = "X")
        assertTrue(d.mode == PointMode.CUMULATIVE && d.goal == PointGoal.UP)
    }
}
