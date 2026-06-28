package com.points.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.db.SqlDriver
import com.points.core.domain.PointEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
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
 *
 * @param ownerId a known owner to adopt — supplied when pairing a second device onto an existing owner or
 *   restoring a ledger. When null (the default), a fresh anonymous owner is generated. Ignored if this
 *   database was already provisioned.
 */
@OptIn(ExperimentalUuidApi::class)
class LocalEventDataSource(
    driver: SqlDriver,
    private val queryContext: CoroutineContext,
    ownerId: String? = null,
) {
    private val database = PointsDatabase(driver)
    private val queries = database.pointEventQueries
    private val stateQueries = database.localStateQueries

    /** This install's identity, provisioned once and stable across relaunches. */
    val ownerId: String
    val deviceId: String

    init {
        stateQueries.provision(
            owner_id = ownerId ?: Uuid.random().toString(),
            device_id = Uuid.random().toString(),
        )
        val state = stateQueries.selectState().executeAsOne()
        this.ownerId = state.owner_id
        deviceId = state.device_id
    }

    /** Appends a locally-created event, marked pending until sync confirms it. */
    fun insert(event: PointEvent) = upsert(event, pending = 1)

    /** Applies an event pulled from the server (already confirmed, so not pending). */
    fun applySynced(event: PointEvent) = upsert(event, pending = 0)

    private fun upsert(event: PointEvent, pending: Long) {
        queries.upsert(
            id = event.id.toString(),
            owner_id = ownerId,
            point_type_id = event.pointTypeId.toString(),
            delta = event.delta,
            device_id = event.deviceId,
            created_at = event.createdAt.toEpochMilliseconds(),
            pending = pending,
        )
    }

    /** This owner's events not yet confirmed by the backend — sync's upload set. */
    fun pendingEvents(): List<PointEvent> =
        queries.pendingEvents(ownerId).executeAsList().map { row ->
            PointEvent(
                id = Uuid.parse(row.id),
                pointTypeId = Uuid.parse(row.point_type_id),
                delta = row.delta,
                deviceId = row.device_id,
                createdAt = Instant.fromEpochMilliseconds(row.created_at),
            )
        }

    /** Marks the given event ids confirmed (no longer pending) after a successful sync push. */
    fun clearPending(ids: List<String>) {
        if (ids.isNotEmpty()) queries.clearPending(ownerId, ids)
    }

    /**
     * Emits this owner's count of pending (not-yet-synced) events, re-emitting whenever the ledger changes.
     * The sync coordinator collects this so an append while the server is unreachable surfaces as an
     * unsynced state instead of leaving the indicator stuck on a stale "Synced".
     */
    fun observePendingCount(): Flow<Long> =
        queries.pendingCount(ownerId).asFlow().mapToOne(queryContext)

    /** The highest server `seq` this install has pulled — the sync pull cursor. */
    fun syncCursor(): Long = stateQueries.selectState().executeAsOne().sync_cursor

    /** Advances the sync pull cursor after a successful pull. */
    fun setCursor(value: Long) = stateQueries.setCursor(value)

    /** Current value (`SUM(delta)`, 0 if empty) for this owner's [pointTypeId]. */
    fun value(pointTypeId: Uuid): Long =
        queries.valueForType(ownerId, pointTypeId.toString()).executeAsOne()

    /** Emits the current value for this owner's [pointTypeId], re-emitting whenever the ledger changes. */
    fun observeValue(pointTypeId: Uuid): Flow<Long> =
        queries.valueForType(ownerId, pointTypeId.toString()).asFlow().mapToOne(queryContext)

    /**
     * Emits the value accumulated at or after [sinceMillis] for this owner's [pointTypeId] — the mode-aware
     * read for a daily type's "today" count. Re-emits whenever the ledger changes.
     */
    fun observeValueSince(pointTypeId: Uuid, sinceMillis: Long): Flow<Long> =
        queries.valueForTypeSince(ownerId, pointTypeId.toString(), sinceMillis).asFlow().mapToOne(queryContext)

    /**
     * Emits the epoch-millis timestamp of this owner's most recent positive event for [pointTypeId] (the
     * recency input for an easing tile's gauge), or null when there is none. Re-emits on ledger changes.
     */
    fun observeLastActivity(pointTypeId: Uuid): Flow<Long?> =
        queries.lastActivityAt(ownerId, pointTypeId.toString()).asFlow().mapToOne(queryContext)
            .map { if (it == 0L) null else it }

    companion object {
        /** Creates the ledger schema on a fresh driver. */
        fun createSchema(driver: SqlDriver) {
            PointsDatabase.Schema.create(driver)
        }
    }
}
