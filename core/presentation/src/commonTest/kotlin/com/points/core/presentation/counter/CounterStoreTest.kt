package com.points.core.presentation.counter

import com.arkivanov.mvikotlin.core.utils.isAssertOnMainThreadEnabled
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.points.core.domain.DecrementPoint
import com.points.core.domain.DeletePointType
import com.points.core.domain.IncrementPoint
import com.points.core.domain.ObservePointTypes
import com.points.core.domain.ObservePointValue
import com.points.core.domain.PointEvent
import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import com.points.core.domain.PointType
import com.points.core.domain.ResetPointType
import com.points.core.domain.RestorePointType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
class CounterStoreTest {

    private val typeId = Uuid.random()

    @BeforeTest
    fun disableMainThreadAssertions() {
        isAssertOnMainThreadEnabled = false
    }

    /** A fake ledger: a backing value the use cases mutate/observe, the type stream (for hue), plus
     *  delete/restore call counters. */
    private class FakeLedger {
        val value = MutableStateFlow(0L)
        val types = MutableStateFlow<List<PointType>>(emptyList())
        var deleteCalls = 0
        var restoreCalls = 0

        val increment = IncrementPoint { id, delta -> value.update { it + delta }; event(id, delta) }
        val decrement = DecrementPoint { id, delta -> value.update { it - delta }; event(id, -delta) }
        val observe = ObservePointValue { value }
        val observeTypes = ObservePointTypes { types }
        val reset = ResetPointType { id -> val old = value.value; value.value = 0; event(id, -old) }
        val delete = DeletePointType { deleteCalls++ }
        val restore = RestorePointType { restoreCalls++ }

        private fun event(id: Uuid, delta: Long) =
            PointEvent(Uuid.random(), id, delta, "device-test", Instant.fromEpochSeconds(0))
    }

    private fun type(id: Uuid, hue: Int) = PointType(
        id = id, name = "X", hue = hue, icon = "spark", mode = PointMode.CUMULATIVE, step = 1,
        goal = PointGoal.UP, target = null, unit = "",
        createdAt = Instant.fromEpochSeconds(0), updatedAt = Instant.fromEpochSeconds(0),
    )

    private fun store(ledger: FakeLedger) = DefaultStoreFactory().counterStore(
        typeId, ledger.increment, ledger.decrement, ledger.observe, ledger.observeTypes,
        ledger.reset, ledger.delete, ledger.restore, UnconfinedTestDispatcher(),
    )

    @Test
    fun showsObservedValueOnCreation() = runTest {
        val ledger = FakeLedger().apply { value.value = 5 }
        assertEquals(5L, store(ledger).state.value)
    }

    @Test
    fun reflectsTheTypesHueSoTheScreenCanTintItself() = runTest {
        val ledger = FakeLedger().apply { types.value = listOf(type(typeId, hue = 215)) }
        assertEquals(215, store(ledger).state.hue, "the counter screen tints to the type's color")
    }

    @Test
    fun incrementAndDecrementIntentsDriveTheValue() = runTest {
        val ledger = FakeLedger()
        val store = store(ledger)
        store.accept(CounterStore.Intent.Increment)
        store.accept(CounterStore.Intent.Increment)
        assertEquals(2L, store.state.value)
        store.accept(CounterStore.Intent.Decrement)
        assertEquals(1L, store.state.value)
    }

    @Test
    fun resetZeroesTheValueAndOffersUndoForTheAmount() = runTest {
        val ledger = FakeLedger().apply { value.value = 8 }
        val store = store(ledger)
        store.accept(CounterStore.Intent.Reset)
        assertEquals(0L, store.state.value)
        assertEquals("Reset to zero", store.state.undoLabel)
        assertEquals(8L, store.state.resetAmount)
        assertFalse(store.state.deleted)
    }

    @Test
    fun undoAfterResetReAddsTheAmount() = runTest {
        val ledger = FakeLedger().apply { value.value = 8 }
        val store = store(ledger)
        store.accept(CounterStore.Intent.Reset)
        store.accept(CounterStore.Intent.Undo)
        assertEquals(8L, store.state.value, "undo re-adds the reset amount")
        assertNull(store.state.undoLabel)
        assertEquals(0, ledger.restoreCalls)
    }

    @Test
    fun deleteOffersUndoAndMarksDeleted() = runTest {
        val ledger = FakeLedger()
        val store = store(ledger)
        store.accept(CounterStore.Intent.Delete)
        assertEquals(1, ledger.deleteCalls)
        assertEquals("Removed", store.state.undoLabel)
        assertTrue(store.state.deleted)
    }

    @Test
    fun undoAfterDeleteRestores() = runTest {
        val ledger = FakeLedger()
        val store = store(ledger)
        store.accept(CounterStore.Intent.Delete)
        store.accept(CounterStore.Intent.Undo)
        assertEquals(1, ledger.restoreCalls)
        assertFalse(store.state.deleted)
        assertNull(store.state.undoLabel)
    }

    @Test
    fun dismissUndoClearsTheOfferWithoutReversing() = runTest {
        val ledger = FakeLedger().apply { value.value = 5 }
        val store = store(ledger)
        store.accept(CounterStore.Intent.Reset)
        store.accept(CounterStore.Intent.DismissUndo)
        assertNull(store.state.undoLabel)
        assertEquals(0L, store.state.value, "the reset stands; only the undo offer was dismissed")
    }
}
