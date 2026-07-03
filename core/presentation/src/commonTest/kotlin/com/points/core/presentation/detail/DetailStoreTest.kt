package com.points.core.presentation.detail

import com.arkivanov.mvikotlin.core.utils.isAssertOnMainThreadEnabled
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.points.core.domain.DecrementPoint
import com.points.core.domain.DeletePointType
import com.points.core.domain.IncrementPoint
import com.points.core.domain.ObservePointTrend
import com.points.core.domain.PointEvent
import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import com.points.core.domain.PointType
import com.points.core.domain.ResetPointType
import com.points.core.domain.RestorePointType
import com.points.core.domain.TrendRange
import com.points.core.domain.pointTrendOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class DetailStoreTest {

    private val typeId = Uuid.random()

    @BeforeTest
    fun disableMainThreadAssertions() {
        isAssertOnMainThreadEnabled = false
    }

    /**
     * A fake ledger behind the ports the store injects: an event list plus a type catalog, with the trend
     * derived through the real pure [pointTrendOf] (so value/cumulative/hue semantics match production),
     * and delete/restore call counters.
     */
    private inner class FakeLedger(step: Long = 1, mode: PointMode = PointMode.CUMULATIVE) {
        val now = Instant.parse("2026-07-03T12:00:00Z")
        val zone = TimeZone.UTC
        val types = MutableStateFlow(listOf(type(typeId, hue = 152, step = step, mode = mode)))
        val events = MutableStateFlow<List<PointEvent>>(emptyList())
        var deleteCalls = 0
        var restoreCalls = 0

        val observeTrend = ObservePointTrend { id ->
            combine(types, events) { catalog, ledger ->
                catalog.firstOrNull { it.id == id }?.let { pointTrendOf(it, ledger, now, zone) }
            }.filterNotNull()
        }
        val increment = IncrementPoint { id, delta -> append(id, delta) }
        val decrement = DecrementPoint { id, delta -> append(id, -delta) }
        val reset = ResetPointType { id -> append(id, -events.value.filter { it.pointTypeId == id }.sumOf { it.delta }) }
        val delete = DeletePointType { deleteCalls++ }
        val restore = RestorePointType { restoreCalls++ }

        fun append(id: Uuid, delta: Long, at: Instant = now): PointEvent =
            PointEvent(Uuid.random(), id, delta, "device-test", at)
                .also { event -> events.update { it + event } }
    }

    private fun type(id: Uuid, hue: Int, step: Long, mode: PointMode) = PointType(
        id = id, name = "X", hue = hue, icon = "spark", mode = mode, step = step,
        goal = PointGoal.UP, target = null, unit = "",
        createdAt = Instant.fromEpochSeconds(0), updatedAt = Instant.fromEpochSeconds(0),
    )

    private fun store(ledger: FakeLedger) = DefaultStoreFactory().detailStore(
        typeId, ledger.observeTrend, ledger.increment, ledger.decrement,
        ledger.reset, ledger.delete, ledger.restore, UnconfinedTestDispatcher(),
    )

    @Test
    fun exposesTheObservedTrendWithItsValueAndHue() = runTest {
        val ledger = FakeLedger()
        ledger.append(typeId, 5)
        val store = store(ledger)
        assertEquals(5L, store.state.value)
        assertEquals(152, store.state.hue)
        assertEquals(5L, store.state.trend?.cumulative)
    }

    @Test
    fun hueFollowsARecolorOfTheType() = runTest {
        val ledger = FakeLedger()
        val store = store(ledger)
        ledger.types.value = listOf(type(typeId, hue = 215, step = 1, mode = PointMode.CUMULATIVE))
        assertEquals(215, store.state.hue, "the detail screen tints to the type's color")
    }

    @Test
    fun defaultsToTheMonthRangeAndTrendChart() = runTest {
        val store = store(FakeLedger())
        assertEquals(TrendRange.MONTH, store.state.range, "the design opens on the month view")
        assertEquals(ChartStyle.TREND, store.state.chart)
    }

    @Test
    fun rangeAndChartSelectionsStick() = runTest {
        val store = store(FakeLedger())
        store.accept(DetailStore.Intent.SetRange(TrendRange.YEAR))
        store.accept(DetailStore.Intent.SetChart(ChartStyle.CALENDAR))
        assertEquals(TrendRange.YEAR, store.state.range)
        assertEquals(ChartStyle.CALENDAR, store.state.chart)
    }

    @Test
    fun incrementAndDecrementUseTheTypesStep() = runTest {
        val ledger = FakeLedger(step = 5)
        val store = store(ledger)
        store.accept(DetailStore.Intent.Increment)
        store.accept(DetailStore.Intent.Increment)
        assertEquals(10L, store.state.value)
        store.accept(DetailStore.Intent.Decrement)
        assertEquals(5L, store.state.value)
    }

    @Test
    fun incrementByAddsACustomAmount() = runTest {
        val ledger = FakeLedger()
        val store = store(ledger)
        store.accept(DetailStore.Intent.IncrementBy(25))
        assertEquals(25L, store.state.value)
    }

    @Test
    fun resetZeroesTheValueAndOffersUndoForTheTotal() = runTest {
        val ledger = FakeLedger()
        ledger.append(typeId, 8)
        val store = store(ledger)
        store.accept(DetailStore.Intent.Reset)
        assertEquals(0L, store.state.value)
        assertEquals("Reset to zero", store.state.undoLabel)
        assertEquals(8L, store.state.resetAmount)
        assertFalse(store.state.deleted)
    }

    @Test
    fun undoAfterResetReAddsTheTotal() = runTest {
        val ledger = FakeLedger()
        ledger.append(typeId, 8)
        val store = store(ledger)
        store.accept(DetailStore.Intent.Reset)
        store.accept(DetailStore.Intent.Undo)
        assertEquals(8L, store.state.value, "undo re-adds the reset amount")
        assertNull(store.state.undoLabel)
        assertEquals(0, ledger.restoreCalls)
    }

    @Test
    fun resetOnADailyPointOffersTheLedgerTotalNotTodaysBucket() = runTest {
        val ledger = FakeLedger(mode = PointMode.DAILY)
        ledger.append(typeId, 5, at = ledger.now - 1.days)
        ledger.append(typeId, 3)
        val store = store(ledger)
        assertEquals(3L, store.state.value, "a daily point's value is today's bucket")
        store.accept(DetailStore.Intent.Reset)
        assertEquals(8L, store.state.resetAmount, "reset compensates the whole ledger, so undo re-adds the total")
    }

    @Test
    fun deleteOffersUndoAndMarksDeleted() = runTest {
        val ledger = FakeLedger()
        val store = store(ledger)
        store.accept(DetailStore.Intent.Delete)
        assertEquals(1, ledger.deleteCalls)
        assertEquals("Removed", store.state.undoLabel)
        assertTrue(store.state.deleted)
    }

    @Test
    fun undoAfterDeleteRestores() = runTest {
        val ledger = FakeLedger()
        val store = store(ledger)
        store.accept(DetailStore.Intent.Delete)
        store.accept(DetailStore.Intent.Undo)
        assertEquals(1, ledger.restoreCalls)
        assertFalse(store.state.deleted)
        assertNull(store.state.undoLabel)
    }

    @Test
    fun dismissUndoClearsTheOfferWithoutReversing() = runTest {
        val ledger = FakeLedger()
        ledger.append(typeId, 5)
        val store = store(ledger)
        store.accept(DetailStore.Intent.Reset)
        store.accept(DetailStore.Intent.DismissUndo)
        assertNull(store.state.undoLabel)
        assertEquals(0L, store.state.value, "the reset stands; only the undo offer was dismissed")
    }
}
