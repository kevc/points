package com.points.core.presentation.counter

import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import com.points.core.domain.DecrementPoint
import com.points.core.domain.DeletePointType
import com.points.core.domain.IncrementPoint
import com.points.core.domain.ObservePointTypes
import com.points.core.domain.ObservePointValue
import com.points.core.domain.ResetPointType
import com.points.core.domain.RestorePointType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * State of the per-type counter screen: the current value, the type's [hue] (so the screen can tint itself in
 * the point's color, matching its home tile), plus a transient **undo** offer after a reset or a delete. Reset
 * and delete are reversible by design ("nothing is ever destroyed"): [undoLabel] drives a small Undo
 * affordance, [resetAmount]/[deleted] tell the store how to reverse it, and [deleted] also lets the UI disable
 * the counter while a removal is still undoable.
 */
@OptIn(ExperimentalUuidApi::class)
interface CounterStore : Store<CounterStore.Intent, CounterStore.State, Nothing> {
    sealed interface Intent {
        data object Increment : Intent
        data object Decrement : Intent
        data object Reset : Intent
        data object Delete : Intent
        data object Undo : Intent
        data object DismissUndo : Intent
    }

    data class State(
        val value: Long = 0,
        val hue: Int = 152,
        val undoLabel: String? = null,
        val resetAmount: Long? = null,
        val deleted: Boolean = false,
    )
}

private sealed interface Msg {
    data class ValueUpdated(val value: Long) : Msg
    data class HueUpdated(val hue: Int) : Msg
    data class Undoable(val label: String, val resetAmount: Long?, val deleted: Boolean) : Msg
    data object ClearUndo : Msg
}

/**
 * Builds the [CounterStore] for [pointTypeId]. Observing the value drives [CounterStore.State.value] and
 * observing the type keeps [CounterStore.State.hue] live (so a recolor reflects immediately); each intent
 * calls a use case and the value re-arrives through the observed flow (never optimistic). Reset appends a
 * compensating event and offers an undo that re-adds the amount; delete tombstones and offers an undo that
 * restores.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun StoreFactory.counterStore(
    pointTypeId: Uuid,
    increment: IncrementPoint,
    decrement: DecrementPoint,
    observeValue: ObservePointValue,
    observeTypes: ObservePointTypes,
    reset: ResetPointType,
    delete: DeletePointType,
    restore: RestorePointType,
    mainContext: CoroutineContext,
): CounterStore =
    object : CounterStore, Store<CounterStore.Intent, CounterStore.State, Nothing> by create(
        name = "CounterStore",
        initialState = CounterStore.State(),
        bootstrapper = SimpleBootstrapper(Unit),
        executorFactory = coroutineExecutorFactory<CounterStore.Intent, Unit, CounterStore.State, Msg, Nothing>(mainContext) {
            onAction<Unit> {
                launch { observeValue(pointTypeId).collect { dispatch(Msg.ValueUpdated(it)) } }
                launch {
                    observeTypes()
                        .mapNotNull { types -> types.firstOrNull { it.id == pointTypeId }?.hue }
                        .distinctUntilChanged()
                        .collect { dispatch(Msg.HueUpdated(it)) }
                }
            }
            onIntent<CounterStore.Intent.Increment> { launch { increment(pointTypeId, 1) } }
            onIntent<CounterStore.Intent.Decrement> { launch { decrement(pointTypeId, 1) } }
            onIntent<CounterStore.Intent.Reset> {
                val amount = state().value
                launch { reset(pointTypeId) }
                dispatch(Msg.Undoable(label = "Reset to zero", resetAmount = amount, deleted = false))
            }
            onIntent<CounterStore.Intent.Delete> {
                launch { delete(pointTypeId) }
                dispatch(Msg.Undoable(label = "Removed", resetAmount = null, deleted = true))
            }
            onIntent<CounterStore.Intent.Undo> {
                val s = state()
                val amount = s.resetAmount
                when {
                    amount != null -> launch { increment(pointTypeId, amount) }
                    s.deleted -> launch { restore(pointTypeId) }
                }
                dispatch(Msg.ClearUndo)
            }
            onIntent<CounterStore.Intent.DismissUndo> { dispatch(Msg.ClearUndo) }
        },
        reducer = { msg ->
            when (msg) {
                is Msg.ValueUpdated -> copy(value = msg.value)
                is Msg.HueUpdated -> copy(hue = msg.hue)
                is Msg.Undoable -> copy(undoLabel = msg.label, resetAmount = msg.resetAmount, deleted = msg.deleted)
                Msg.ClearUndo -> copy(undoLabel = null, resetAmount = null, deleted = false)
            }
        },
    ) {}
