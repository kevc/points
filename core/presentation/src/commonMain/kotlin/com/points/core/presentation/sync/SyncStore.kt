package com.points.core.presentation.sync

import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import com.points.core.domain.ObserveSyncStatus
import com.points.core.domain.SyncPointEvents
import com.points.core.domain.SyncStatus
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** State of the sync indicator: the latest [SyncStatus] reported by the sync coordinator. */
interface SyncStore : Store<SyncStore.Intent, SyncStore.State, Nothing> {
    sealed interface Intent {
        /** Request a reconcile now — fired once on creation and again when the app is foregrounded. */
        data object Sync : Intent
    }

    data class State(val status: SyncStatus = SyncStatus.Idle)
}

private sealed interface Msg {
    data class StatusUpdated(val status: SyncStatus) : Msg
}

/**
 * Builds the [SyncStore]. On creation it starts observing [ObserveSyncStatus] into state and fires one
 * best-effort [SyncPointEvents] pass; [SyncStore.Intent.Sync] fires another (used for the foreground
 * trigger). The status itself is driven by the coordinator — the store only reflects it and nudges sync.
 */
internal fun StoreFactory.syncStore(
    observeSyncStatus: ObserveSyncStatus,
    syncPointEvents: SyncPointEvents,
    mainContext: CoroutineContext,
): SyncStore =
    object : SyncStore, Store<SyncStore.Intent, SyncStore.State, Nothing> by create(
        name = "SyncStore",
        initialState = SyncStore.State(),
        bootstrapper = SimpleBootstrapper(Unit),
        executorFactory = coroutineExecutorFactory<SyncStore.Intent, Unit, SyncStore.State, Msg, Nothing>(mainContext) {
            onAction<Unit> {
                launch { observeSyncStatus().collect { dispatch(Msg.StatusUpdated(it)) } }
                launch { syncPointEvents() } // trigger one reconcile on creation
            }
            onIntent<SyncStore.Intent.Sync> {
                launch { syncPointEvents() }
            }
        },
        reducer = { msg ->
            when (msg) {
                is Msg.StatusUpdated -> copy(status = msg.status)
            }
        },
    ) {}
