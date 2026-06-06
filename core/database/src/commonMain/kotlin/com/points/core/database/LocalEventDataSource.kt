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
 *
 * On first construction it provisions this install's stable identity ([ownerId] + [deviceId]) into the
 * single-row `local_state` table; a relaunch over the same database reuses it. Every ledger row is stamped
 * with [ownerId] and every read is scoped to it, so events stay partitioned by owner.
 */
@OptIn(ExperimentalUuidApi::class)
class LocalEventDataSource(
    driver: SqlDriver,
    private val queryContext: CoroutineContext,
) {
    private val database = PointsDatabase(driver)
    private val queries = database.pointEventQueries
    private val stateQueries = database.localStateQueries

    /** This install's identity, provisioned once and stable across relaunches. */
    val ownerId: String
    val deviceId: String

    init {
        stateQueries.provision(owner_id = Uuid.random().toString(), device_id = Uuid.random().toString())
        val state = stateQueries.selectState().executeAsOne()
        ownerId = state.owner_id
        deviceId = state.device_id
    }

    /** Appends (or idempotently re-applies, by id) one event to this owner's ledger. */
    fun insert(event: PointEvent) {
        queries.upsert(
            id = event.id.toString(),
            owner_id = ownerId,
            point_type_id = event.pointTypeId.toString(),
            delta = event.delta,
            device_id = event.deviceId,
            created_at = event.createdAt.toEpochMilliseconds(),
        )
    }

    /** Current value (`SUM(delta)`, 0 if empty) for this owner's [pointTypeId]. */
    fun value(pointTypeId: Uuid): Long =
        queries.valueForType(ownerId, pointTypeId.toString()).executeAsOne()

    /** Emits the current value for this owner's [pointTypeId], re-emitting whenever the ledger changes. */
    fun observeValue(pointTypeId: Uuid): Flow<Long> =
        queries.valueForType(ownerId, pointTypeId.toString()).asFlow().mapToOne(queryContext)

    companion object {
        /** Creates the ledger schema on a fresh driver. */
        fun createSchema(driver: SqlDriver) {
            PointsDatabase.Schema.create(driver)
        }
    }
}
