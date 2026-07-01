package com.points.core.presentation.di

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.core.utils.isAssertOnMainThreadEnabled
import com.points.core.domain.CreatePointType
import com.points.core.domain.DecrementPoint
import com.points.core.domain.DeletePointType
import com.points.core.domain.EditPointType
import com.points.core.domain.GreetUseCase
import com.points.core.domain.IncrementPoint
import com.points.core.domain.ObservePointTypes
import com.points.core.domain.ObservePointValue
import com.points.core.domain.ObserveSyncStatus
import com.points.core.domain.ObserveTiles
import com.points.core.domain.PointEvent
import com.points.core.domain.ResetPointType
import com.points.core.domain.RestorePointType
import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import com.points.core.domain.PointTile
import com.points.core.domain.PointType
import com.points.core.domain.PointTypeDraft
import com.points.core.domain.RingState
import com.points.core.domain.SyncPointEvents
import com.points.core.domain.SyncStatus
import com.points.core.presentation.root.RootComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.Instant
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Dispatcher module swapped in for tests so `withContext`/store run synchronously. */
private val testDispatcherModule = module {
    single<CoroutineDispatcher>(named("io")) { UnconfinedTestDispatcher() }
    single<CoroutineDispatcher>(named("main")) { UnconfinedTestDispatcher() }
}

@OptIn(ExperimentalUuidApi::class)
private val sampleTypeId = Uuid.parse("00000000-0000-0000-0000-0000000000aa")

/**
 * Stand-in for the real data module: fake use cases so the component graph resolves without a SQL driver or
 * HTTP engine. [ObserveTiles] reports one tile to prove the home grid wires through; [ObservePointValue]
 * reports a fixed value so the detail child reflects the observed flow; create/edit are no-op fakes.
 */
@OptIn(ExperimentalUuidApi::class)
private val fakeDataModule = module {
    factory<IncrementPoint> { IncrementPoint { id, delta -> fakeEvent(id, delta) } }
    factory<DecrementPoint> { DecrementPoint { id, delta -> fakeEvent(id, -delta) } }
    factory<ObservePointValue> { ObservePointValue { flowOf(7L) } }
    factory<ObserveSyncStatus> { ObserveSyncStatus { flowOf(SyncStatus.Synced) } }
    factory<SyncPointEvents> { SyncPointEvents { } }
    factory<ObservePointTypes> { ObservePointTypes { flowOf(emptyList()) } }
    factory<ObserveTiles> { ObserveTiles { flowOf(listOf(sampleTile())) } }
    factory<CreatePointType> { CreatePointType { draft -> typeFrom(draft) } }
    factory<EditPointType> { EditPointType { id, draft -> typeFrom(draft, id) } }
    factory<ResetPointType> { ResetPointType { id -> fakeEvent(id, 0) } }
    factory<DeletePointType> { DeletePointType { } }
    factory<RestorePointType> { RestorePointType { } }
}

@OptIn(ExperimentalUuidApi::class)
private fun typeFrom(draft: PointTypeDraft, id: Uuid = Uuid.random()) = PointType(
    id = id, name = draft.name, hue = draft.hue, icon = draft.icon, mode = draft.mode,
    step = draft.step, goal = draft.goal, target = draft.target, unit = draft.unit,
    createdAt = Instant.fromEpochSeconds(0), updatedAt = Instant.fromEpochSeconds(0),
)

@OptIn(ExperimentalUuidApi::class)
private fun sampleTile() = PointTile(
    type = PointType(
        id = sampleTypeId, name = "Water", hue = 215, icon = "drop", mode = PointMode.DAILY,
        step = 1, goal = PointGoal.UP, target = 8, unit = "glasses",
        createdAt = Instant.fromEpochSeconds(0), updatedAt = Instant.fromEpochSeconds(0),
    ),
    value = 5,
    ring = RingState(progress = 5f / 8f, ticks = 8, useAccent = true, over = false),
    daysSinceLast = 0,
    climbingNext = 10,
)

@OptIn(ExperimentalUuidApi::class)
private fun fakeEvent(pointTypeId: Uuid, delta: Long) =
    PointEvent(Uuid.random(), pointTypeId, delta, "device-test", Instant.fromEpochSeconds(0))

class DiGraphTest {

    @BeforeTest
    fun setup() {
        isAssertOnMainThreadEnabled = false
    }

    @AfterTest
    fun teardown() {
        stopKoin()
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun resolvesGraphAndNavigatesAcrossHomeDetailAndCreate() {
        val koin = startKoin {
            modules(presentationModule, testDispatcherModule, fakeDataModule)
        }.koin

        assertNotNull(koin.get<GreetUseCase>())
        assertNotNull(koin.get<StoreFactory>())

        val context = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val root = koin.get<RootComponent> { parametersOf(context) }

        // Initial screen is Home, populated from ObserveTiles; sync overlay resolves.
        val home = root.stack.value.active.instance as RootComponent.Child.Home
        assertEquals(listOf("Water"), home.component.state.value.tiles.map { it.name })
        assertEquals("5 / 8 today", home.component.state.value.tiles.single().meta)
        assertEquals(SyncStatus.Synced, root.sync.state.value.status)

        // Tapping a tile pushes its per-type counter, which reflects the observed value (7).
        home.component.onTileClicked(sampleTypeId)
        val detail = root.stack.value.active.instance as RootComponent.Child.Detail
        assertEquals(7L, detail.component.state.value.value)

        // The detail's edit affordance pushes the create/edit form.
        detail.component.onEdit()
        assertTrue(root.stack.value.active.instance is RootComponent.Child.Create)

        // Back to Home; the FAB opens a fresh create form (editId = null, exercised via Koin parametersOf).
        detail.component.onBack() // pop Create
        detail.component.onBack() // pop Detail
        val homeAgain = root.stack.value.active.instance as RootComponent.Child.Home
        homeAgain.component.onCreate()
        val create = root.stack.value.active.instance as RootComponent.Child.Create
        assertTrue(!create.component.state.value.editing, "the FAB opens a new (non-editing) form")

        // Saving the new form pops back to Home.
        create.component.onName("Books read")
        create.component.onSave()
        assertTrue(root.stack.value.active.instance is RootComponent.Child.Home)
    }
}
