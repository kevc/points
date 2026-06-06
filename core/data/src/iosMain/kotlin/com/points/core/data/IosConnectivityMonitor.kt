package com.points.core.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_global_queue

/**
 * [ConnectivityMonitor] over the `Network` framework's `NWPathMonitor`: a thin adapter turning path updates
 * into the `Flow<Boolean>` the [SyncCoordinator] collects — no policy here. The monitor is started on
 * collection (its update handler fires immediately with the current path, so the first state needs no extra
 * emit) and cancelled when the collector cancels ([awaitClose]). Output is conflated + deduped so repeated
 * path callbacks don't churn the coordinator.
 */
@OptIn(ExperimentalForeignApi::class)
class IosConnectivityMonitor : ConnectivityMonitor {

    override val online: Flow<Boolean> = callbackFlow {
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(monitor) { path ->
            trySend(nw_path_get_status(path) == nw_path_status_satisfied)
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_global_queue(0L, 0uL))
        nw_path_monitor_start(monitor)
        awaitClose { nw_path_monitor_cancel(monitor) }
    }.conflate().distinctUntilChanged()
}
