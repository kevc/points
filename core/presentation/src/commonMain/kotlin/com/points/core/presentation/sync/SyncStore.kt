package com.points.core.presentation.sync

import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import com.points.core.domain.ObserveSyncStatus
import com.points.core.domain.SyncPointEvents
import com.points.core.domain.SyncStatus
import kotlinx.coroutines.CancellationException
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
 * Runs a reconcile as best-effort: a failure (e.g. an unreachable backend — the everyday offline-first
 * case) is swallowed so it never escapes this `launch` as an unhandled coroutine exception. That matters
 * because on Kotlin/Native an unhandled coroutine exception terminates the app (surfacing as
 * `trapOnUndeclaredException` under a debugger). Failure status is surfaced separately via
 * [ObserveSyncStatus]; the pending events simply reconcile on the next pass. Cancellation still propagates.
 */
private suspend fun SyncPointEvents.bestEffort() {
    try {
        invoke()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        // offline-first: leave pending events for the next sync
    }
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
                launch { syncPointEvents.bestEffort() } // trigger one reconcile on creation
            }
            onIntent<SyncStore.Intent.Sync> {
                launch { syncPointEvents.bestEffort() }
            }
        },
        reducer = { msg ->
            when (msg) {
                is Msg.StatusUpdated -> copy(status = msg.status)
            }
        },
    ) {}
