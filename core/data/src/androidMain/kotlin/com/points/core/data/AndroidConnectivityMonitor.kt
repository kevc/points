package com.points.core.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [ConnectivityMonitor] over [ConnectivityManager]: a thin adapter that turns the default-network callbacks
 * into the `Flow<Boolean>` the [SyncCoordinator] collects — no policy here. The callback is registered on
 * collection and unregistered when the collector cancels ([awaitClose]); the current state is emitted up
 * front so a subscriber never waits for the first change. Output is conflated + deduped so a burst of OS
 * callbacks doesn't churn the coordinator.
 *
 * Requires `ACCESS_NETWORK_STATE`, declared in this module's manifest so it merges into any consuming app.
 */
class AndroidConnectivityMonitor(private val context: Context) : ConnectivityMonitor {

    override val online: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(manager.isOnline()) }
            override fun onUnavailable() { trySend(false) }
        }

        trySend(manager.isOnline()) // current state on subscribe
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.conflate().distinctUntilChanged()
}

/** Whether the active network currently has internet capability. */
private fun ConnectivityManager.isOnline(): Boolean =
    getNetworkCapabilities(activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
