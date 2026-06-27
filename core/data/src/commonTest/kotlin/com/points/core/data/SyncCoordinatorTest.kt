package com.points.core.data

import com.points.core.domain.SyncPointEvents
import com.points.core.domain.SyncStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SyncCoordinatorTest {

    /**
     * Conflated stand-in for [ConnectivityMonitor]: a replay-1 shared flow so a fresh collector sees the
     * current state immediately, and so duplicate `online` values can be emitted to prove the coordinator
     * dedups them itself (the real adapters also `distinctUntilChanged`, but the policy must not depend on it).
     */
    private class FakeConnectivity(initial: Boolean) : ConnectivityMonitor {
        private val flow = MutableSharedFlow<Boolean>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        init { flow.tryEmit(initial) }
        override val online: Flow<Boolean> = flow
        fun set(value: Boolean) { flow.tryEmit(value) }
    }

    /** Fake reconcile that fails its first [failuresBeforeSuccess] calls, then succeeds; counts invocations. */
    private class RecordingSync(private val failuresBeforeSuccess: Int = 0) : SyncPointEvents {
        var calls = 0
            private set
        override suspend fun invoke() {
            calls++
            if (calls <= failuresBeforeSuccess) error("sync failed (attempt $calls)")
        }
    }

    /** Reconcile whose reachability can be toggled mid-test — succeeds while [reachable], else throws. */
    private class ToggleableSync(var reachable: Boolean = true) : SyncPointEvents {
        var calls = 0
            private set
        override suspend fun invoke() {
            calls++
            if (!reachable) error("backend unreachable")
        }
    }

    /** Builds a coordinator, defaulting [pending] to a steady zero for the connectivity-only tests. */
    private fun coordinator(
        connectivity: ConnectivityMonitor,
        sync: SyncPointEvents,
        pending: Flow<Long> = MutableStateFlow(0L),
        backoff: BackoffPolicy = BackoffPolicy(),
    ): SyncCoordinator = SyncCoordinator(connectivity, pending, sync, backoff)

    /**
     * Collects [SyncCoordinator.status] into a list on an unconfined dispatcher bound to the test scheduler:
     * the unconfined dispatcher runs each transition eagerly, while sharing [TestScope.testScheduler] keeps the
     * coordinator's `delay()`s on the virtual clock that `advanceUntilIdle` drives. A foreground [Job] is
     * returned so the caller can cancel the otherwise-endless collection before the test ends.
     */
    private fun TestScope.collectStatus(coordinator: SyncCoordinator): Pair<List<SyncStatus>, Job> {
        val statuses = mutableListOf<SyncStatus>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { coordinator.status.collect { statuses += it } }
        return statuses to job
    }

    @Test
    fun syncsOnceOnStartWhenOnline() = runTest {
        val sync = RecordingSync()
        val (statuses, job) = collectStatus(coordinator(FakeConnectivity(true), sync))

        advanceUntilIdle()

        assertEquals(1, sync.calls)
        assertEquals(listOf(SyncStatus.Syncing, SyncStatus.Synced), statuses)
        job.cancel()
    }

    @Test
    fun offlineReportsOfflineAndDoesNotSync() = runTest {
        val sync = RecordingSync()
        val (statuses, job) = collectStatus(coordinator(FakeConnectivity(false), sync))

        advanceUntilIdle()

        assertEquals(0, sync.calls)
        assertEquals(listOf(SyncStatus.Offline), statuses)
        job.cancel()
    }

    @Test
    fun offlineToOnlineTriggersSyncAndStayingOnlineDoesNot() = runTest {
        val sync = RecordingSync()
        val connectivity = FakeConnectivity(false)
        val (statuses, job) = collectStatus(coordinator(connectivity, sync))
        advanceUntilIdle()
        assertEquals(0, sync.calls)

        connectivity.set(true)
        advanceUntilIdle()
        assertEquals(1, sync.calls)

        // A repeated `online` value must not re-trigger (deduped by the coordinator).
        connectivity.set(true)
        advanceUntilIdle()
        assertEquals(1, sync.calls)
        assertEquals(listOf(SyncStatus.Offline, SyncStatus.Syncing, SyncStatus.Synced), statuses)
        job.cancel()
    }

    @Test
    fun reconnectAfterDropTriggersAnotherSync() = runTest {
        val sync = RecordingSync()
        val connectivity = FakeConnectivity(true)
        val (_, job) = collectStatus(coordinator(connectivity, sync))
        advanceUntilIdle()
        assertEquals(1, sync.calls)

        connectivity.set(false)
        advanceUntilIdle()
        connectivity.set(true)
        advanceUntilIdle()

        assertEquals(2, sync.calls, "every offline→online transition re-syncs")
        job.cancel()
    }

    @Test
    fun failingSyncRetriesWithGrowingDelayThenSucceeds() = runTest {
        val sync = RecordingSync(failuresBeforeSuccess = 3)
        val coordinator = coordinator(
            FakeConnectivity(true),
            sync,
            backoff = BackoffPolicy(base = 1.seconds, max = 60.seconds, factor = 2.0),
        )
        val (statuses, job) = collectStatus(coordinator)

        advanceUntilIdle()

        // Attempts at t=0, +1s, +2s, +4s → 4th call succeeds. Total virtual time proves the delays grow
        // (constant 1s backoff would total 3s, not 7s).
        assertEquals(4, sync.calls)
        assertEquals(7_000L, testScheduler.currentTime)
        assertEquals(SyncStatus.Synced, statuses.last())
        assertEquals(3, statuses.count { it == SyncStatus.Failed })
        assertEquals(4, statuses.count { it == SyncStatus.Syncing })
        job.cancel()
    }

    @Test
    fun backoffDelayIsCappedAtMax() = runTest {
        val sync = RecordingSync(failuresBeforeSuccess = 4)
        val coordinator = coordinator(
            FakeConnectivity(true),
            sync,
            backoff = BackoffPolicy(base = 1.seconds, max = 2.seconds, factor = 2.0),
        )
        val (_, job) = collectStatus(coordinator)

        advanceUntilIdle()

        // Delays clamp at 2s: t=0, +1s, +2s, +2s, +2s → 5th call (t=7s) succeeds. Uncapped would be 15s.
        assertEquals(5, sync.calls)
        assertEquals(7_000L, testScheduler.currentTime)
        job.cancel()
    }

    @Test
    fun successResetsBackoffForTheNextReconnect() = runTest {
        val sync = RecordingSync(failuresBeforeSuccess = 1) // first pass: fail once (1s), then succeed
        val connectivity = FakeConnectivity(true)
        val (_, job) = collectStatus(coordinator(connectivity, sync, backoff = BackoffPolicy(base = 1.seconds)))
        advanceUntilIdle()
        assertEquals(2, sync.calls)
        val afterFirstPass = testScheduler.currentTime // 1s spent on the single backoff

        // Reconnect: this pass succeeds immediately. If backoff had not reset it would still be elevated,
        // but a clean success adds no delay — total time is unchanged from the first pass.
        connectivity.set(false)
        advanceUntilIdle()
        connectivity.set(true)
        advanceUntilIdle()

        assertEquals(3, sync.calls)
        assertEquals(afterFirstPass, testScheduler.currentTime, "a successful pass adds no backoff delay")
        job.cancel()
    }

    @Test
    fun droppingOfflineMidRetryFlipsToOffline() = runTest {
        val sync = RecordingSync(failuresBeforeSuccess = 100) // keeps failing while online
        val connectivity = FakeConnectivity(true)
        val (statuses, job) = collectStatus(coordinator(connectivity, sync, backoff = BackoffPolicy(base = 10.seconds)))

        // The unconfined collector runs the first attempt eagerly: it fails and parks on a 10s backoff.
        assertEquals(SyncStatus.Failed, statuses.last())

        connectivity.set(false)
        advanceUntilIdle()

        assertEquals(SyncStatus.Offline, statuses.last(), "a disconnect cancels the in-flight retry loop")
        assertTrue(sync.calls in 1..2, "the parked retry must not keep firing once offline")
        job.cancel()
    }

    @Test
    fun pendingEditWhileOnlinePushesAndSettlesBackToSynced() = runTest {
        val pending = MutableStateFlow(0L)
        val sync = RecordingSync()
        val (statuses, job) = collectStatus(coordinator(FakeConnectivity(true), sync, pending))
        advanceUntilIdle()
        assertEquals(1, sync.calls, "on-connect pull")
        assertEquals(SyncStatus.Synced, statuses.last())

        // A local edit lands as pending; the coordinator pushes it (its bump alone drives the sync).
        pending.value = 1
        advanceUntilIdle()
        assertEquals(2, sync.calls, "the pending edit drives exactly one push")
        assertEquals(SyncStatus.Synced, statuses.last())

        // A successful push clears pending; the count back at zero settles to Synced with no extra round-trip.
        pending.value = 0
        advanceUntilIdle()
        assertEquals(2, sync.calls, "a pending count back at zero does not re-sync")
        assertEquals(SyncStatus.Synced, statuses.last())
        job.cancel()
    }

    @Test
    fun pendingEditWithUnreachableBackendSurfacesFailedNotStaleSynced() = runTest {
        // Repro of #79: app already running, OS network up, backend stopped, then a local edit. The stale
        // "Synced" must give way to "Sync failed" — matching what a fresh launch already shows.
        val pending = MutableStateFlow(0L)
        val sync = ToggleableSync(reachable = true)
        val (statuses, job) = collectStatus(
            coordinator(FakeConnectivity(true), sync, pending, backoff = BackoffPolicy(base = 10.seconds)),
        )
        advanceUntilIdle()
        assertEquals(SyncStatus.Synced, statuses.last(), "the on-connect pull reconciles while the backend is up")

        // Backend goes away (OS still online); the edit can't be pushed. The unconfined collector runs the
        // push attempt eagerly: it fails and parks on the 10s backoff.
        sync.reachable = false
        pending.value = 1
        assertEquals(
            SyncStatus.Failed,
            statuses.last(),
            "an unsynced edit against an unreachable backend reads as Failed, not a stale Synced",
        )
        assertTrue(sync.calls >= 2, "the on-connect pull plus at least one push attempt for the edit")
        job.cancel()
    }

    @Test
    fun recoveringBackendClearsPendingAndReturnsToSynced() = runTest {
        val pending = MutableStateFlow(0L)
        val sync = ToggleableSync(reachable = false)
        val (statuses, job) = collectStatus(
            coordinator(FakeConnectivity(true), sync, pending, backoff = BackoffPolicy(base = 1.seconds)),
        )
        pending.value = 1 // edit while the backend is down → Failed + retry
        assertEquals(SyncStatus.Failed, statuses.last())

        // Backend recovers; a retry succeeds and the (now cleared) pending count settles to Synced.
        sync.reachable = true
        advanceUntilIdle()
        assertEquals(SyncStatus.Synced, statuses.last())
        pending.value = 0
        advanceUntilIdle()
        assertEquals(SyncStatus.Synced, statuses.last())
        job.cancel()
    }
}
