package com.points.core.presentation.detail

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.states
import com.points.core.domain.DecrementPoint
import com.points.core.domain.DeletePointType
import com.points.core.domain.IncrementPoint
import com.points.core.domain.ObservePointTrend
import com.points.core.domain.ResetPointType
import com.points.core.domain.RestorePointType
import com.points.core.domain.TrendRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Shared component backing the M5 per-point Detail screen: the trend snapshot plus chart selections via
 * [state], counter intents (± by the type's step, [onIncrementBy] for the custom step chips), the chart
 * controls ([onSetRange]/[onSetChart]), reversible reset/remove with undo, and [onBack]/[onEdit] navigation.
 */
interface DetailComponent {
    val state: Value<DetailStore.State>
    fun onIncrement()
    fun onDecrement()
    fun onIncrementBy(amount: Long)
    fun onSetRange(range: TrendRange)
    fun onSetChart(chart: ChartStyle)
    fun onBack()
    fun onEdit()
    fun onReset()
    fun onDelete()
    fun onUndo()
    fun onDismissUndo()
}

@OptIn(ExperimentalUuidApi::class)
class DefaultDetailComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    pointTypeId: Uuid,
    observeTrend: ObservePointTrend,
    increment: IncrementPoint,
    decrement: DecrementPoint,
    reset: ResetPointType,
    delete: DeletePointType,
    restore: RestorePointType,
    mainContext: CoroutineContext,
    private val onBackPressed: () -> Unit = {},
    private val onEditRequested: () -> Unit = {},
) : DetailComponent, ComponentContext by componentContext {

    private val store = instanceKeeper.getStore {
        storeFactory.detailStore(
            pointTypeId, observeTrend, increment, decrement, reset, delete, restore, mainContext,
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + mainContext)
        .also { scope -> lifecycle.doOnDestroy(scope::cancel) }

    override val state: Value<DetailStore.State> =
        MutableValue(store.state).also { value ->
            scope.launch { store.states.collect { value.value = it } }
        }

    override fun onIncrement() = store.accept(DetailStore.Intent.Increment)

    override fun onDecrement() = store.accept(DetailStore.Intent.Decrement)

    override fun onIncrementBy(amount: Long) = store.accept(DetailStore.Intent.IncrementBy(amount))

    override fun onSetRange(range: TrendRange) = store.accept(DetailStore.Intent.SetRange(range))

    override fun onSetChart(chart: ChartStyle) = store.accept(DetailStore.Intent.SetChart(chart))

    override fun onBack() = onBackPressed()

    override fun onEdit() = onEditRequested()

    override fun onReset() = store.accept(DetailStore.Intent.Reset)

    override fun onDelete() = store.accept(DetailStore.Intent.Delete)

    override fun onUndo() = store.accept(DetailStore.Intent.Undo)

    override fun onDismissUndo() = store.accept(DetailStore.Intent.DismissUndo)
}
