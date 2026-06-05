package com.points.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.db.SqlDriver
import com.points.core.domain.PointEvent
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The local adapter over the SQLDelight ledger. Writes are synchronous (the caller — a use-case factory
 * in `core/data` — owns main-safety via `withContext(io)`); [queryContext] is only the context SQLDelight
 * runs the value query on when a [observeValue] flow is collected, not a main-safety offload.
 */
@OptIn(ExperimentalUuidApi::class)
class LocalEventDataSource(
    driver: SqlDriver,
    private val queryContext: CoroutineContext,
) {
    private val queries = PointsDatabase(driver).pointEventQueries

    /** Appends (or idempotently re-applies, by id) one event to the ledger. */
    fun insert(event: PointEvent) {
        queries.upsert(
            event.id.toString(),
            event.pointTypeId.toString(),
            event.delta,
            event.deviceId,
            event.createdAt.toEpochMilliseconds(),
        )
    }

    /** Current value (`SUM(delta)`, 0 if empty) for [pointTypeId]. */
    fun value(pointTypeId: Uuid): Long =
        queries.valueForType(pointTypeId.toString()).executeAsOne()

    /** Emits the current value for [pointTypeId], re-emitting whenever the ledger changes. */
    fun observeValue(pointTypeId: Uuid): Flow<Long> =
        queries.valueForType(pointTypeId.toString()).asFlow().mapToOne(queryContext)

    companion object {
        /** Creates the ledger schema on a fresh driver. */
        fun createSchema(driver: SqlDriver) {
            PointsDatabase.Schema.create(driver)
        }
    }
}
