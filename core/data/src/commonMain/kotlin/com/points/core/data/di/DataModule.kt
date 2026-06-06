package com.points.core.data.di

import com.points.core.data.DatabaseDriverFactory
import com.points.core.data.OfflineFirstPointRepository
import com.points.core.data.SyncCoordinator
import com.points.core.data.decrementPoint
import com.points.core.data.incrementPoint
import com.points.core.data.observePointValue
import com.points.core.data.observeSyncStatus
import com.points.core.data.syncPointEvents
import com.points.core.database.LocalEventDataSource
import com.points.core.domain.DecrementPoint
import com.points.core.domain.IncrementPoint
import com.points.core.domain.ObservePointValue
import com.points.core.domain.ObserveSyncStatus
import com.points.core.domain.PointRepository
import com.points.core.domain.SyncPointEvents
import com.points.core.network.PointsApiService
import com.points.core.network.pointsHttpClient
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Data-layer bindings: the offline-first repository over the local ledger + API client, and the
 * individual use cases components inject. The `named("io")` dispatcher comes from the presentation
 * dispatcher module; the SQL driver, HTTP engine, `named("baseUrl")`, and `ConnectivityMonitor` come from
 * [platformDataModule]. Owner/device identity is provisioned and held by [LocalEventDataSource], not injected.
 */
val dataModule: Module = module {
    single { get<DatabaseDriverFactory>().create() }
    single { LocalEventDataSource(get(), get<CoroutineDispatcher>(named("io"))) }
    single { pointsHttpClient(get()) }
    single { PointsApiService(get(), get<String>(named("baseUrl"))) }
    single<PointRepository> {
        OfflineFirstPointRepository(
            local = get(),
            api = get(),
        )
    }
    factory<IncrementPoint> { incrementPoint(get(), get<CoroutineDispatcher>(named("io"))) }
    factory<DecrementPoint> { decrementPoint(get(), get<CoroutineDispatcher>(named("io"))) }
    factory<ObservePointValue> { observePointValue(get()) }
    factory<SyncPointEvents> { syncPointEvents(get(), get<CoroutineDispatcher>(named("io"))) }
    // One coordinator per app: it holds the sync status stream all observers share. `ConnectivityMonitor`
    // is supplied by platformDataModule (T21).
    single { SyncCoordinator(get(), get()) }
    factory<ObserveSyncStatus> { observeSyncStatus(get()) }
}

/** Platform-supplied bindings: SQL driver factory, HTTP engine, and API base URL. */
expect val platformDataModule: Module
