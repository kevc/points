package com.points.core.data

import com.points.core.domain.ObserveSyncStatus
import com.points.core.domain.SyncPointEvents
import com.points.core.domain.SyncStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Headless driver for offline-first sync: it owns *when* to reconcile and the retry policy, and exposes the
 * resulting [SyncStatus] as a cold [Flow]. No UI and no real network — connectivity arrives via the injected
 * [ConnectivityMonitor], the count of unsynced local edits via [pending], and the reconcile itself is the
 * existing [SyncPointEvents] use case, so the whole thing is exercisable with fake flows + a fake use case +
 * virtual time.
 *
 * It is the *single* driver of sync: a local append simply lands in the ledger as pending, and the bump in
 * [pending] reactively drives the next pass here. (The repository no longer fires its own per-append sync —
 * those best-effort failures never reached this status stream, which is exactly how an already-running app
 * could sit on a stale "Synced" with pending events that weren't reaching an unreachable server.)
 *
 * Behavior while **online**, re-evaluated on every [pending] change (deduped):
 * - on connect, run an initial reconcile pass even with nothing pending — sync also *pulls* other devices'
 *   edits, not just pushes ours;
 * - a non-zero pending count (a local edit that isn't on the server yet) runs a reconcile pass: emit
 *   [SyncStatus.Syncing], then [SyncStatus.Synced] on success. On failure emit [SyncStatus.Failed] and retry
 *   with [BackoffPolicy] backoff while still online — so an unreachable backend now surfaces as "Sync failed"
 *   (matching a fresh launch) instead of a stale "Synced";
 * - once the initial pass has pulled, a pending count back at zero is simply [SyncStatus.Synced] with no
 *   redundant network round-trip.
 *
 * While **offline** it emits [SyncStatus.Offline] and idles until connectivity returns. `collectLatest`
 * cancels an in-flight pass the instant connectivity drops (→ [SyncStatus.Offline]) or a new edit arrives
 * (→ restart the pass). `distinctUntilChanged` on the output collapses the duplicate terminal states the
 * reactive re-evaluation can produce.
 *
 * Main-safety is already owned by [SyncPointEvents] (its factory hops to `named("io")`), so the coordinator
 * adds no dispatcher of its own.
 */
class SyncCoordinator(
    private val connectivity: ConnectivityMonitor,
    private val pending: Flow<Long>,
    private val sync: SyncPointEvents,
    private val backoff: BackoffPolicy = BackoffPolicy(),
) {
    val status: Flow<SyncStatus> = channelFlow {
        connectivity.online.distinctUntilChanged().collectLatest { online ->
            if (!online) {
                send(SyncStatus.Offline)
                return@collectLatest
            }
            // Reset per connect: force one reconcile pass on (re)connect to pull, then track the pending set.
            var pulledOnConnect = false
            pending.distinctUntilChanged().collectLatest { pendingCount ->
                if (pendingCount == 0L && pulledOnConnect) {
                    send(SyncStatus.Synced) // reconciled: nothing pending and we've already pulled this session
                    return@collectLatest
                }
                var attempt = 0
                while (true) {
                    send(SyncStatus.Syncing)
                    val result = runCatching { sync() }
                    if (result.isSuccess) {
                        pulledOnConnect = true
                        send(SyncStatus.Synced) // success clears pending; the next emission settles to Synced
                        return@collectLatest
                    }
                    send(SyncStatus.Failed)
                    delay(backoff.delayFor(++attempt))
                }
            }
        }
    }.distinctUntilChanged()
}

/**
 * Exponential backoff between failed reconcile attempts: [base] × [factor]^(attempt−1), clamped to [max].
 * The delay growth *stops* at [max]; attempts keep going while online so a transient failure eventually clears.
 */
class BackoffPolicy(
    private val base: Duration = 1.seconds,
    private val max: Duration = 60.seconds,
    private val factor: Double = 2.0,
) {
    /** Delay before retry [attempt] (1-based). */
    fun delayFor(attempt: Int): Duration {
        require(attempt >= 1) { "attempt must be 1-based, was $attempt" }
        val raw = base * factor.pow(attempt - 1)
        return minOf(raw, max)
    }
}

/** Builds [ObserveSyncStatus] over the coordinator's status stream — observe-only, no extra dispatcher needed. */
fun observeSyncStatus(coordinator: SyncCoordinator): ObserveSyncStatus =
    ObserveSyncStatus { coordinator.status }
