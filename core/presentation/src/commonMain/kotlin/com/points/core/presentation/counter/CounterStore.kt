package com.points.core.presentation.counter

import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import com.points.core.domain.DecrementPoint
import com.points.core.domain.IncrementPoint
import com.points.core.domain.ObservePointValue
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** State of the counter screen: the current value (`SUM(delta)`) of the observed point type. */
interface CounterStore : Store<CounterStore.Intent, CounterStore.State, Nothing> {
    sealed interface Intent {
        data object Increment : Intent
        data object Decrement : Intent
    }

    data class State(val value: Long = 0)
}

private sealed interface Msg {
    data class ValueUpdated(val value: Long) : Msg
}

/**
 * Builds the [CounterStore] for [pointTypeId]. On creation it starts observing the value; each
 * [CounterStore.Intent] appends an event via the injected use cases. The value then re-arrives through
 * the observed flow, so the store never optimistically guesses — it reflects the ledger.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun StoreFactory.counterStore(
    pointTypeId: Uuid,
    increment: IncrementPoint,
    decrement: DecrementPoint,
    observeValue: ObservePointValue,
    mainContext: CoroutineContext,
): CounterStore =
    object : CounterStore, Store<CounterStore.Intent, CounterStore.State, Nothing> by create(
        name = "CounterStore",
        initialState = CounterStore.State(),
        bootstrapper = SimpleBootstrapper(Unit),
        executorFactory = coroutineExecutorFactory<CounterStore.Intent, Unit, CounterStore.State, Msg, Nothing>(mainContext) {
            onAction<Unit> {
                launch {
                    observeValue(pointTypeId).collect { dispatch(Msg.ValueUpdated(it)) }
                }
            }
            onIntent<CounterStore.Intent.Increment> {
                launch { increment(pointTypeId, 1) }
            }
            onIntent<CounterStore.Intent.Decrement> {
                launch { decrement(pointTypeId, 1) }
            }
        },
        reducer = { msg ->
            when (msg) {
                is Msg.ValueUpdated -> copy(value = msg.value)
            }
        },
    ) {}
