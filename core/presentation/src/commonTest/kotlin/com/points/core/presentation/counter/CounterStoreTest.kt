package com.points.core.presentation.counter

import com.arkivanov.mvikotlin.core.utils.isAssertOnMainThreadEnabled
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.points.core.domain.DecrementPoint
import com.points.core.domain.IncrementPoint
import com.points.core.domain.ObservePointValue
import com.points.core.domain.PointEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CounterStoreTest {

    private val typeId = Uuid.random()

    @BeforeTest
    fun disableMainThreadAssertions() {
        isAssertOnMainThreadEnabled = false
    }

    /** A fake ledger: a backing value the increment/decrement use cases mutate and observe streams. */
    private class FakeLedger {
        val value = MutableStateFlow(0L)
        val increment = IncrementPoint { id, delta -> value.update { it + delta }; event(id, delta) }
        val decrement = DecrementPoint { id, delta -> value.update { it - delta }; event(id, -delta) }
        val observe = ObservePointValue { value }

        private fun event(id: Uuid, delta: Long) =
            PointEvent(Uuid.random(), id, delta, "device-test", Instant.fromEpochSeconds(0))
    }

    @Test
    fun showsObservedValueOnCreation() = runTest {
        val ledger = FakeLedger().apply { value.value = 5 }
        val store = DefaultStoreFactory().counterStore(
            typeId, ledger.increment, ledger.decrement, ledger.observe, UnconfinedTestDispatcher(),
        )
        assertEquals(5L, store.state.value)
    }

    @Test
    fun incrementAndDecrementIntentsDriveTheValue() = runTest {
        val ledger = FakeLedger()
        val store = DefaultStoreFactory().counterStore(
            typeId, ledger.increment, ledger.decrement, ledger.observe, UnconfinedTestDispatcher(),
        )

        store.accept(CounterStore.Intent.Increment)
        store.accept(CounterStore.Intent.Increment)
        assertEquals(2L, store.state.value)

        store.accept(CounterStore.Intent.Decrement)
        assertEquals(1L, store.state.value)
    }
}
