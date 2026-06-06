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
 * [ConnectivityMonitor] and the reconcile itself is the existing [SyncPointEvents] use case, so the whole
 * thing is exercisable with a fake flow + fake use case + virtual time.
 *
 * Behavior, per collected connectivity transition (deduped, so a repeated `online` value is ignored):
 * - **offline** → emit [SyncStatus.Offline] and idle until connectivity returns;
 * - **online** (first emission = app start, and every offline→online transition) → run a reconcile pass,
 *   emitting [SyncStatus.Syncing] then [SyncStatus.Synced] on success. On failure emit [SyncStatus.Failed]
 *   and retry with [BackoffPolicy] exponential backoff (capped) while still online; a success ends the pass
 *   and resets the backoff (the next transition starts fresh). `collectLatest` cancels an in-flight pass the
 *   instant connectivity drops, so a mid-retry disconnect flips straight to [SyncStatus.Offline].
 *
 * Main-safety is already owned by [SyncPointEvents] (its factory hops to `named("io")`), so the coordinator
 * adds no dispatcher of its own.
 */
class SyncCoordinator(
    private val connectivity: ConnectivityMonitor,
    private val sync: SyncPointEvents,
    private val backoff: BackoffPolicy = BackoffPolicy(),
) {
    val status: Flow<SyncStatus> = channelFlow {
        connectivity.online.distinctUntilChanged().collectLatest { online ->
            if (!online) {
                send(SyncStatus.Offline)
                return@collectLatest
            }
            var attempt = 0
            while (true) {
                send(SyncStatus.Syncing)
                val result = runCatching { sync() }
                if (result.isSuccess) {
                    send(SyncStatus.Synced)
                    return@collectLatest // hold Synced until the next offline→online transition; backoff resets
                }
                send(SyncStatus.Failed)
                delay(backoff.delayFor(++attempt))
            }
        }
    }
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
