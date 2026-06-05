package com.points.core.data.di

import com.points.core.data.DatabaseDriverFactory
import com.points.core.network.defaultHttpClientEngine
import io.ktor.client.engine.HttpClientEngine
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * `10.0.2.2` is the Android emulator's alias for the host loopback, where the dev backend runs.
 * The device id is process-stable for M2 (persistence arrives with sync in M3).
 */
@OptIn(ExperimentalUuidApi::class)
actual val platformDataModule: Module = module {
    single { DatabaseDriverFactory(androidContext()) }
    single<HttpClientEngine> { defaultHttpClientEngine() }
    single(named("baseUrl")) { "http://10.0.2.2:8080" }
    single(named("deviceId")) { Uuid.random().toString() }
}
