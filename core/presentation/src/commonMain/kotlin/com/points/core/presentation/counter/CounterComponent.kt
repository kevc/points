package com.points.core.presentation.counter

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.states
import com.points.core.domain.DecrementPoint
import com.points.core.domain.IncrementPoint
import com.points.core.domain.ObservePointValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The single implicit point type counted in M2 (one counter; full type management is M4). */
@OptIn(ExperimentalUuidApi::class)
val DEFAULT_POINT_TYPE_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000001")

/**
 * Shared component backing the per-type counter screen: exposes the value and the increment/decrement
 * intents, plus [onBack] to pop back to the home grid. From M4 it is parameterized by the tapped type's id
 * (no longer the single [DEFAULT_POINT_TYPE_ID]).
 */
interface CounterComponent {
    val state: Value<CounterStore.State>
    fun onIncrement()
    fun onDecrement()
    fun onBack()
    fun onEdit()
}

@OptIn(ExperimentalUuidApi::class)
class DefaultCounterComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    pointTypeId: Uuid,
    increment: IncrementPoint,
    decrement: DecrementPoint,
    observeValue: ObservePointValue,
    mainContext: CoroutineContext,
    private val onBackPressed: () -> Unit = {},
    private val onEditRequested: () -> Unit = {},
) : CounterComponent, ComponentContext by componentContext {

    private val store = instanceKeeper.getStore {
        storeFactory.counterStore(pointTypeId, increment, decrement, observeValue, mainContext)
    }

    private val scope = CoroutineScope(SupervisorJob() + mainContext)
        .also { scope -> lifecycle.doOnDestroy(scope::cancel) }

    override val state: Value<CounterStore.State> =
        MutableValue(store.state).also { value ->
            scope.launch { store.states.collect { value.value = it } }
        }

    override fun onIncrement() = store.accept(CounterStore.Intent.Increment)

    override fun onDecrement() = store.accept(CounterStore.Intent.Decrement)

    override fun onBack() = onBackPressed()

    override fun onEdit() = onEditRequested()
}
