package com.points.core.data.di

import com.points.core.data.DatabaseDriverFactory
import com.points.core.data.OfflineFirstPointRepository
import com.points.core.data.SyncCoordinator
import com.points.core.data.createPointType
import com.points.core.data.dayTicks
import com.points.core.data.decrementPoint
import com.points.core.data.deletePointType
import com.points.core.data.editPointType
import com.points.core.data.incrementPoint
import com.points.core.data.observePointTrend
import com.points.core.data.observePointTypes
import com.points.core.data.observePointValue
import com.points.core.data.observeSyncStatus
import com.points.core.data.observeTiles
import com.points.core.data.resetPointType
import com.points.core.data.restorePointType
import com.points.core.data.syncPointEvents
import com.points.core.database.LocalEventDataSource
import com.points.core.database.LocalPointTypeDataSource
import com.points.core.domain.CreatePointType
import com.points.core.domain.DecrementPoint
import com.points.core.domain.DeletePointType
import com.points.core.domain.EditPointType
import com.points.core.domain.IncrementPoint
import com.points.core.domain.ObservePointTrend
import com.points.core.domain.ObservePointTypes
import com.points.core.domain.ObservePointValue
import com.points.core.domain.ObserveSyncStatus
import com.points.core.domain.ObserveTiles
import com.points.core.domain.PointRepository
import com.points.core.domain.PointTypeRepository
import com.points.core.domain.ResetPointType
import com.points.core.domain.RestorePointType
import com.points.core.domain.SyncPointEvents
import com.points.core.network.PointsApiService
import com.points.core.network.pointsHttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
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
    // Shares the same driver/db as the ledger; identity is provisioned by LocalEventDataSource, so it is
    // handed that owner rather than provisioning its own.
    single { LocalPointTypeDataSource(get(), get<CoroutineDispatcher>(named("io")), get<LocalEventDataSource>().ownerId) }
    single { pointsHttpClient(get()) }
    single { PointsApiService(get(), get<String>(named("baseUrl"))) }
    // One repository instance serves both ports (it owns the single batch sync for events + types).
    single { OfflineFirstPointRepository(local = get(), types = get(), api = get()) }
    single<PointRepository> { get<OfflineFirstPointRepository>() }
    single<PointTypeRepository> { get<OfflineFirstPointRepository>() }
    factory<IncrementPoint> { incrementPoint(get(), get<CoroutineDispatcher>(named("io"))) }
    factory<DecrementPoint> { decrementPoint(get(), get<CoroutineDispatcher>(named("io"))) }
    factory<ObservePointValue> { observePointValue(get()) }
    factory<SyncPointEvents> { syncPointEvents(get(), get<CoroutineDispatcher>(named("io"))) }
    factory<CreatePointType> { createPointType(get(), get<CoroutineDispatcher>(named("io"))) }
    factory<EditPointType> { editPointType(get(), get<CoroutineDispatcher>(named("io"))) }
    factory<DeletePointType> { deletePointType(get(), get<CoroutineDispatcher>(named("io"))) }
    factory<RestorePointType> { restorePointType(get(), get<CoroutineDispatcher>(named("io"))) }
    factory<ResetPointType> { resetPointType(get(), get<CoroutineDispatcher>(named("io"))) }
    factory<ObservePointTypes> { observePointTypes(get()) }
    // The home read model: active types × mode-aware value × recency → ring, via the pure domain math. The
    // day window/timezone are read live and the grid re-rolls at local midnight via dayTicks (issue #108).
    factory<ObserveTiles> {
        observeTiles(
            types = get(),
            points = get(),
            clock = Clock.System,
            zone = TimeZone.Companion::currentSystemDefault,
            dayTicks = dayTicks(Clock.System, TimeZone.Companion::currentSystemDefault),
        )
    }
    // The Detail read model: the point's ledger bucketed by local day → value, series, stats, heatmap, insight.
    factory<ObservePointTrend> {
        observePointTrend(
            types = get(),
            points = get(),
            clock = Clock.System,
            zone = TimeZone.Companion::currentSystemDefault,
            dayTicks = dayTicks(Clock.System, TimeZone.Companion::currentSystemDefault),
        )
    }
    // One coordinator per app: it holds the sync status stream all observers share. `ConnectivityMonitor`
    // is supplied by platformDataModule (T21); the pending-count flow is events + type changes combined, so
    // an unpushed edit of either kind surfaces as unsynced rather than a stale "Synced".
    single {
        val pending = combine(
            get<LocalEventDataSource>().observePendingCount(),
            get<LocalPointTypeDataSource>().observePendingTypeCount(),
        ) { events, types -> events + types }
        SyncCoordinator(get(), pending, get())
    }
    factory<ObserveSyncStatus> { observeSyncStatus(get()) }
}

/** Platform-supplied bindings: SQL driver factory, HTTP engine, and API base URL. */
expect val platformDataModule: Module
