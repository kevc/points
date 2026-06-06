package com.points.core.domain

import kotlinx.coroutines.flow.Flow

/**
 * The observable state of offline-first reconciliation, surfaced to the UI.
 *
 * Connectivity is **infrastructure, not a domain concept** — the domain never sees a "connected" boolean,
 * only the resulting [SyncStatus]. The coordinator in `core/data` maps OS connectivity + sync attempts onto
 * these states; presentation renders them.
 */
enum class SyncStatus {
    /** No sync in flight and none yet attempted this session — the resting state before the first pass. */
    Idle,

    /** A reconciliation pass is currently running. */
    Syncing,

    /** The last pass completed successfully; the local ledger is reconciled with the backend. */
    Synced,

    /** Connectivity is down; sync is suspended until it returns. */
    Offline,

    /** The last pass failed while online (it will be retried with backoff). */
    Failed,
}

/**
 * Streams the current [SyncStatus]. Its own named `fun interface` (never a `typealias`) so the injected
 * instance erases to a distinct type and Koin bindings never collide.
 *
 * Implemented in `core/data` over the sync coordinator. The manual "sync now" trigger is the existing
 * [SyncPointEvents] use case — this port is observe-only.
 */
fun interface ObserveSyncStatus {
    operator fun invoke(): Flow<SyncStatus>
}
