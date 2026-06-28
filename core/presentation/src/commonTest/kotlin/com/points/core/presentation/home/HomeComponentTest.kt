package com.points.core.presentation.home

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.mvikotlin.core.utils.isAssertOnMainThreadEnabled
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.points.core.domain.DecrementPoint
import com.points.core.domain.IncrementPoint
import com.points.core.domain.ObserveTiles
import com.points.core.domain.PointEvent
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class HomeComponentTest {

    @BeforeTest
    fun setup() {
        isAssertOnMainThreadEnabled = false
    }

    private val typeId = Uuid.random()
    private var incremented: Pair<Uuid, Long>? = null
    private var decremented: Pair<Uuid, Long>? = null

    private fun event(id: Uuid, delta: Long) = PointEvent(Uuid.random(), id, delta, "d", Instant.fromEpochSeconds(0))

    private fun component(
        onOpen: (Uuid) -> Unit = {},
        onCreate: () -> Unit = {},
    ) = DefaultHomeComponent(
        componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
        storeFactory = DefaultStoreFactory(),
        observeTiles = ObserveTiles { flowOf(emptyList()) },
        increment = IncrementPoint { id, delta -> incremented = id to delta; event(id, delta) },
        decrement = DecrementPoint { id, delta -> decremented = id to delta; event(id, -delta) },
        mainContext = UnconfinedTestDispatcher(),
        onOpenType = onOpen,
        onCreateType = onCreate,
    )

    @Test
    fun onIncrementCountsTheTypeByItsStep() {
        component().onIncrement(typeId, 10)
        assertEquals(typeId to 10L, incremented)
    }

    @Test
    fun onDecrementCountsTheTypeDownByItsStep() {
        component().onDecrement(typeId, 5)
        assertEquals(typeId to 5L, decremented)
    }

    @Test
    fun onTileClickedReportsTheOpenTarget() {
        var opened: Uuid? = null
        component(onOpen = { opened = it }).onTileClicked(typeId)
        assertEquals(typeId, opened)
    }

    @Test
    fun onCreateReportsTheCreateRequest() {
        var created = 0
        component(onCreate = { created++ }).onCreate()
        assertEquals(1, created)
    }
}
