package com.points.core.presentation.home

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.states
import com.points.core.domain.ObserveTiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Backs the home grid: exposes the tiles and reports a tap so the root can open that type's detail. */
@OptIn(ExperimentalUuidApi::class)
interface HomeComponent {
    val state: Value<HomeStore.State>
    fun onTileClicked(pointTypeId: Uuid)
}

@OptIn(ExperimentalUuidApi::class)
class DefaultHomeComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    observeTiles: ObserveTiles,
    mainContext: CoroutineContext,
    private val onOpenType: (Uuid) -> Unit,
) : HomeComponent, ComponentContext by componentContext {

    private val store = instanceKeeper.getStore { storeFactory.homeStore(observeTiles, mainContext) }

    private val scope = CoroutineScope(SupervisorJob() + mainContext)
        .also { scope -> lifecycle.doOnDestroy(scope::cancel) }

    override val state: Value<HomeStore.State> =
        MutableValue(store.state).also { value ->
            scope.launch { store.states.collect { value.value = it } }
        }

    override fun onTileClicked(pointTypeId: Uuid) = onOpenType(pointTypeId)
}
