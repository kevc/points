package com.points.core.presentation.home

import com.arkivanov.mvikotlin.core.utils.isAssertOnMainThreadEnabled
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.points.core.domain.ObserveTiles
import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import com.points.core.domain.PointTile
import com.points.core.domain.PointType
import com.points.core.domain.RingState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class HomeStoreTest {

    @BeforeTest
    fun setup() {
        isAssertOnMainThreadEnabled = false
    }

    private fun type(
        name: String = "X",
        mode: PointMode = PointMode.CUMULATIVE,
        goal: PointGoal = PointGoal.UP,
        target: Long? = null,
        unit: String = "",
    ) = PointType(
        id = Uuid.random(), name = name, hue = 152, icon = "spark", mode = mode, step = 1,
        goal = goal, target = target, unit = unit,
        createdAt = Instant.fromEpochSeconds(0), updatedAt = Instant.fromEpochSeconds(0),
    )

    private fun tile(
        type: PointType,
        value: Long,
        daysSinceLast: Int = 0,
        climbingNext: Long = 0,
    ) = PointTile(
        type = type,
        value = value,
        ring = RingState(progress = 0f, ticks = 4, useAccent = false, over = false),
        daysSinceLast = daysSinceLast,
        climbingNext = climbingNext,
    )

    private fun statesFor(vararg tiles: PointTile): List<HomeTile> {
        val store = DefaultStoreFactory().homeStore(
            observeTiles = ObserveTiles { flowOf(tiles.toList()) },
            mainContext = UnconfinedTestDispatcher(),
        )
        return store.state.tiles
    }

    @Test
    fun cumulativeMetaShowsTheGroupedClimbingTarget() {
        val t = statesFor(tile(type(name = "Push-ups"), value = 4030, climbingNext = 5000)).single()
        assertEquals("→ 5,000", t.meta)
        assertEquals("4,030", t.valueText)
    }

    @Test
    fun dailyTargetMetaShowsTodayProgress() {
        val t = statesFor(tile(type(mode = PointMode.DAILY, target = 8, unit = "glasses"), value = 5)).single()
        assertEquals("5 / 8 today", t.meta)
        assertEquals("glasses", t.unit)
    }

    @Test
    fun tileCarriesTheStepForOnTileCounting() {
        val pushups = type(name = "Push-ups").copy(step = 10)
        assertEquals(10L, statesFor(tile(pushups, value = 0)).single().step)
    }

    @Test
    fun emptyCatalogIsLoadedSoTheFirstRunPromptCanShow() {
        val store = DefaultStoreFactory().homeStore(
            observeTiles = ObserveTiles { flowOf(emptyList()) },
            mainContext = UnconfinedTestDispatcher(),
        )
        assertTrue(store.state.tiles.isEmpty())
        assertTrue(store.state.loaded, "an emission arrived, so this is empty (not still loading)")
    }

    @Test
    fun easingMetaReadsAsCalmRecency() {
        val anger = type(name = "Times angry", goal = PointGoal.DOWN)
        assertEquals("today", statesFor(tile(anger, value = 12, daysSinceLast = 0)).single().meta)
        assertEquals("5d calm", statesFor(tile(anger, value = 12, daysSinceLast = 5)).single().meta)
        assertEquals("calm", statesFor(tile(anger, value = 0, daysSinceLast = Int.MAX_VALUE)).single().meta)
    }

    @Test
    fun groupThousandsFormatsWithCommas() {
        assertEquals("0", groupThousands(0))
        assertEquals("42", groupThousands(42))
        assertEquals("4,030", groupThousands(4030))
        assertEquals("1,000,000", groupThousands(1_000_000))
        assertEquals("-1,500", groupThousands(-1500))
    }
}
