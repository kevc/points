package com.points.core.presentation.home

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.states
import com.points.core.domain.DecrementPoint
import com.points.core.domain.IncrementPoint
import com.points.core.domain.ObserveTiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Backs the home grid: exposes the tiles, reports a tap to open a type's detail, and a FAB tap to create. */
@OptIn(ExperimentalUuidApi::class)
interface HomeComponent {
    val state: Value<HomeStore.State>
    fun onTileClicked(pointTypeId: Uuid)
    fun onCreate()

    /** Count [pointTypeId] up/down by [step] straight from its tile — the primary action, no screen change. */
    fun onIncrement(pointTypeId: Uuid, step: Long)
    fun onDecrement(pointTypeId: Uuid, step: Long)
}

@OptIn(ExperimentalUuidApi::class)
class DefaultHomeComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    observeTiles: ObserveTiles,
    private val increment: IncrementPoint,
    private val decrement: DecrementPoint,
    mainContext: CoroutineContext,
    private val onOpenType: (Uuid) -> Unit,
    private val onCreateType: () -> Unit,
) : HomeComponent, ComponentContext by componentContext {

    private val store = instanceKeeper.getStore { storeFactory.homeStore(observeTiles, mainContext) }

    private val scope = CoroutineScope(SupervisorJob() + mainContext)
        .also { scope -> lifecycle.doOnDestroy(scope::cancel) }

    override val state: Value<HomeStore.State> =
        MutableValue(store.state).also { value ->
            scope.launch { store.states.collect { value.value = it } }
        }

    override fun onTileClicked(pointTypeId: Uuid) = onOpenType(pointTypeId)

    override fun onCreate() = onCreateType()

    override fun onIncrement(pointTypeId: Uuid, step: Long) {
        scope.launch { increment(pointTypeId, step) }
    }

    override fun onDecrement(pointTypeId: Uuid, step: Long) {
        scope.launch { decrement(pointTypeId, step) }
    }
}
