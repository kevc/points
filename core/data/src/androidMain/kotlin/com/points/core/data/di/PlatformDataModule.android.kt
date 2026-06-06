package com.points.core.data.di

import com.points.core.data.AndroidConnectivityMonitor
import com.points.core.data.ConnectivityMonitor
import com.points.core.data.DatabaseDriverFactory
import com.points.core.network.defaultHttpClientEngine
import io.ktor.client.engine.HttpClientEngine
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * `10.0.2.2` is the Android emulator's alias for the host loopback, where the dev backend runs.
 * Owner/device identity is provisioned and persisted by the local ledger, not bound here.
 */
actual val platformDataModule: Module = module {
    single { DatabaseDriverFactory(androidContext()) }
    single<HttpClientEngine> { defaultHttpClientEngine() }
    single(named("baseUrl")) { "http://10.0.2.2:8080" }
    single<ConnectivityMonitor> { AndroidConnectivityMonitor(androidContext()) }
}
