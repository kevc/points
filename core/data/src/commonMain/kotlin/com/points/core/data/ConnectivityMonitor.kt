package com.points.core.data

import kotlinx.coroutines.flow.Flow

/**
 * Reports OS network reachability as a stream. **Defined here, not in `core/domain`** — connectivity is
 * infrastructure, so the domain never sees it; only the [SyncCoordinator] consumes it, mapping it onto the
 * domain's [com.points.core.domain.SyncStatus].
 *
 * Implementations emit the current state on subscribe and then on every change, conflated so duplicate OS
 * callbacks don't churn the coordinator. Platform adapters (Android `ConnectivityManager`, iOS
 * `NWPathMonitor`) land in T21 and are bound in `platformDataModule`.
 */
interface ConnectivityMonitor {
    /** `true` while the device has network connectivity. Emits the current value immediately on collection. */
    val online: Flow<Boolean>
}
