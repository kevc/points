package com.points.core.data.di

import com.points.core.data.DatabaseDriverFactory
import com.points.core.network.defaultHttpClientEngine
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The iOS simulator reaches the dev backend over `localhost`. Owner/device identity is provisioned and
 * persisted by the local ledger, not bound here.
 */
actual val platformDataModule: Module = module {
    single { DatabaseDriverFactory() }
    single<HttpClientEngine> { defaultHttpClientEngine() }
    single(named("baseUrl")) { "http://localhost:8080" }
}
