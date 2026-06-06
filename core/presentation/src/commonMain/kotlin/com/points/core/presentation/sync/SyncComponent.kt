package com.points.core.presentation.sync

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.states
import com.points.core.domain.ObserveSyncStatus
import com.points.core.domain.SyncPointEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Shared component backing the sync-status indicator: exposes the latest [SyncStore.State] and a
 * foreground hook the apps call on resume to nudge a reconcile (the on-creation trigger lives in the store).
 * Injects the individual use cases — never the repository or the coordinator.
 */
interface SyncComponent {
    val state: Value<SyncStore.State>

    /** Called by the apps when brought to the foreground (Android `ON_RESUME` / iOS `scenePhase.active`). */
    fun onAppForegrounded()
}

class DefaultSyncComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    observeSyncStatus: ObserveSyncStatus,
    syncPointEvents: SyncPointEvents,
    mainContext: CoroutineContext,
) : SyncComponent, ComponentContext by componentContext {

    private val store = instanceKeeper.getStore {
        storeFactory.syncStore(observeSyncStatus, syncPointEvents, mainContext)
    }

    private val scope = CoroutineScope(SupervisorJob() + mainContext)
        .also { scope -> lifecycle.doOnDestroy(scope::cancel) }

    override val state: Value<SyncStore.State> =
        MutableValue(store.state).also { value ->
            scope.launch { store.states.collect { value.value = it } }
        }

    override fun onAppForegrounded() = store.accept(SyncStore.Intent.Sync)
}
