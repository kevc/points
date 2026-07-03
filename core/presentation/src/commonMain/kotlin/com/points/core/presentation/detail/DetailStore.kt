package com.points.core.presentation.detail

import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import com.points.core.domain.DecrementPoint
import com.points.core.domain.DeletePointType
import com.points.core.domain.IncrementPoint
import com.points.core.domain.ObservePointTrend
import com.points.core.domain.PointTrend
import com.points.core.domain.ResetPointType
import com.points.core.domain.RestorePointType
import com.points.core.domain.TrendRange
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Which chart the Detail card shows — the segmented control's three positions. */
enum class ChartStyle {
    /** The area trend line ("Trend"). */
    TREND,

    /** Per-bucket bars ("Bars"). */
    BARS,

    /** The calendar heatmap ("Cal"). */
    CALENDAR,
}

/**
 * State of the M5 per-point Detail screen: the live [PointTrend] snapshot (value, stats, insight, and the
 * `daily[]` array every chart derives from), the user's chart selections ([range]/[chart]), plus a transient
 * **undo** offer after a reset or a delete. Reset and delete are reversible by design ("nothing is ever
 * destroyed"): [undoLabel] drives a small Undo affordance, [resetAmount]/[deleted] tell the store how to
 * reverse it, and [deleted] also lets the UI disable the counter while a removal is still undoable.
 *
 * Chart data is *not* materialized here: renderers apply the pure domain derivations (`series(range)`,
 * `heatmap()`, `recentLog(n)`) to [trend] synchronously.
 */
@OptIn(ExperimentalUuidApi::class)
interface DetailStore : Store<DetailStore.Intent, DetailStore.State, Nothing> {
    sealed interface Intent {
        /** Add the type's own step (the big + button). */
        data object Increment : Intent

        /** Subtract the type's own step (the round − button). */
        data object Decrement : Intent

        /** Add a custom [amount] (the step chips). */
        data class IncrementBy(val amount: Long) : Intent

        data class SetRange(val range: TrendRange) : Intent
        data class SetChart(val chart: ChartStyle) : Intent
        data object Reset : Intent
        data object Delete : Intent
        data object Undo : Intent
        data object DismissUndo : Intent
    }

    data class State(
        val trend: PointTrend? = null,
        val range: TrendRange = TrendRange.MONTH,
        val chart: ChartStyle = ChartStyle.TREND,
        val undoLabel: String? = null,
        val resetAmount: Long? = null,
        val deleted: Boolean = false,
    ) {
        /** The hero value (0 until the first trend snapshot arrives). */
        val value: Long get() = trend?.value ?: 0L

        /** The type's hue, so the screen can tint itself like its home tile. */
        val hue: Int get() = trend?.type?.hue ?: 152
    }
}

private sealed interface Msg {
    data class TrendUpdated(val trend: PointTrend) : Msg
    data class RangeSelected(val range: TrendRange) : Msg
    data class ChartSelected(val chart: ChartStyle) : Msg
    data class Undoable(val label: String, val resetAmount: Long?, val deleted: Boolean) : Msg
    data object ClearUndo : Msg
}

/**
 * Builds the [DetailStore] for [pointTypeId]. A single [ObservePointTrend] collection drives the whole state
 * — value, hue, stats, and chart inputs all arrive as one [PointTrend] snapshot, re-emitted on every ledger
 * change, on a rename/recolor, and at local midnight; each intent calls a use case and the trend re-arrives
 * through the observed flow (never optimistic). Increment/decrement move by the type's own step; reset
 * appends a compensating event for the **ledger total** (so its undo re-adds `trend.cumulative`, which for a
 * daily point is more than today's bucket); delete tombstones and offers an undo that restores. A tombstoned
 * type stops emitting, so the last snapshot stays on screen while the removal is undoable.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun StoreFactory.detailStore(
    pointTypeId: Uuid,
    observeTrend: ObservePointTrend,
    increment: IncrementPoint,
    decrement: DecrementPoint,
    reset: ResetPointType,
    delete: DeletePointType,
    restore: RestorePointType,
    mainContext: CoroutineContext,
): DetailStore =
    object : DetailStore, Store<DetailStore.Intent, DetailStore.State, Nothing> by create(
        name = "DetailStore",
        initialState = DetailStore.State(),
        bootstrapper = SimpleBootstrapper(Unit),
        executorFactory = coroutineExecutorFactory<DetailStore.Intent, Unit, DetailStore.State, Msg, Nothing>(mainContext) {
            onAction<Unit> {
                launch { observeTrend(pointTypeId).collect { dispatch(Msg.TrendUpdated(it)) } }
            }
            onIntent<DetailStore.Intent.Increment> {
                val step = state().step
                launch { increment(pointTypeId, step) }
            }
            onIntent<DetailStore.Intent.Decrement> {
                val step = state().step
                launch { decrement(pointTypeId, step) }
            }
            onIntent<DetailStore.Intent.IncrementBy> { intent ->
                launch { increment(pointTypeId, intent.amount) }
            }
            onIntent<DetailStore.Intent.SetRange> { dispatch(Msg.RangeSelected(it.range)) }
            onIntent<DetailStore.Intent.SetChart> { dispatch(Msg.ChartSelected(it.chart)) }
            onIntent<DetailStore.Intent.Reset> {
                val amount = state().trend?.cumulative ?: 0L
                launch { reset(pointTypeId) }
                dispatch(Msg.Undoable(label = "Reset to zero", resetAmount = amount, deleted = false))
            }
            onIntent<DetailStore.Intent.Delete> {
                launch { delete(pointTypeId) }
                dispatch(Msg.Undoable(label = "Removed", resetAmount = null, deleted = true))
            }
            onIntent<DetailStore.Intent.Undo> {
                val s = state()
                val amount = s.resetAmount
                when {
                    amount != null -> launch { increment(pointTypeId, amount) }
                    s.deleted -> launch { restore(pointTypeId) }
                }
                dispatch(Msg.ClearUndo)
            }
            onIntent<DetailStore.Intent.DismissUndo> { dispatch(Msg.ClearUndo) }
        },
        reducer = { msg ->
            when (msg) {
                is Msg.TrendUpdated -> copy(trend = msg.trend)
                is Msg.RangeSelected -> copy(range = msg.range)
                is Msg.ChartSelected -> copy(chart = msg.chart)
                is Msg.Undoable -> copy(undoLabel = msg.label, resetAmount = msg.resetAmount, deleted = msg.deleted)
                Msg.ClearUndo -> copy(undoLabel = null, resetAmount = null, deleted = false)
            }
        },
    ) {}

/** The type's step, floored at 1, or 1 until the first snapshot arrives. */
private val DetailStore.State.step: Long get() = trend?.type?.step?.coerceAtLeast(1L) ?: 1L
