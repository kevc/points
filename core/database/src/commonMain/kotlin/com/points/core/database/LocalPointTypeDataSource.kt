package com.points.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.db.SqlDriver
import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import com.points.core.domain.PointType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The local adapter over the SQLDelight point-type catalog — sibling to [LocalEventDataSource], sharing the
 * same driver/database file. Writes are synchronous (the caller — a use-case factory in `core/data` — owns
 * main-safety via `withContext(io)`); [queryContext] is only the context SQLDelight runs an observed query on.
 *
 * Identity is **not** provisioned here — [LocalEventDataSource] owns that; this source is handed the already
 * provisioned [ownerId] and scopes every read/write to it. A type is a last-write-wins register: local edits
 * go through [upsertLocal] (always win, marked pending), pulled changes through [applySynced] (win only when
 * strictly newer). The type sync cursor lives in the shared `local_state` row.
 *
 * @param ownerId this install's provisioned owner (from [LocalEventDataSource.ownerId]).
 */
@OptIn(ExperimentalUuidApi::class)
class LocalPointTypeDataSource(
    driver: SqlDriver,
    private val queryContext: CoroutineContext,
    private val ownerId: String,
) {
    private val database = PointsDatabase(driver)
    private val queries = database.pointTypeQueries
    private val stateQueries = database.localStateQueries

    /** Persists a locally-made create/edit/delete, marked pending until sync confirms it. */
    fun upsertLocal(type: PointType) = queries.upsertLocal(
        id = type.id.toString(),
        owner_id = ownerId,
        name = type.name,
        hue = type.hue.toLong(),
        icon = type.icon,
        mode = type.mode.name,
        step = type.step,
        goal = type.goal.name,
        target = type.target,
        unit = type.unit,
        created_at = type.createdAt.toEpochMilliseconds(),
        updated_at = type.updatedAt.toEpochMilliseconds(),
        deleted_at = type.deletedAt?.toEpochMilliseconds(),
    )

    /**
     * Applies a type pulled from the server, last-write-wins by `updatedAt`: a no-op if our copy is the same
     * age or newer, so a stale pull never clobbers a more recent local edit. The guard lives here (not in
     * SQL) because the SQLDelight dialect predates conditional UPSERT.
     */
    fun applySynced(type: PointType) {
        val existing = typeById(type.id)
        if (existing != null && type.updatedAt <= existing.updatedAt) return
        queries.upsertSynced(
            id = type.id.toString(),
            owner_id = ownerId,
            name = type.name,
            hue = type.hue.toLong(),
            icon = type.icon,
            mode = type.mode.name,
            step = type.step,
            goal = type.goal.name,
            target = type.target,
            unit = type.unit,
            created_at = type.createdAt.toEpochMilliseconds(),
            updated_at = type.updatedAt.toEpochMilliseconds(),
            deleted_at = type.deletedAt?.toEpochMilliseconds(),
        )
    }

    /** This owner's live (non-tombstoned) types. */
    fun activeTypes(): List<PointType> =
        queries.selectActive(ownerId).executeAsList().map { it.toPointType() }

    /** Emits this owner's live types, re-emitting whenever the catalog changes. */
    fun observeTypes(): Flow<List<PointType>> =
        queries.selectActive(ownerId).asFlow().mapToList(queryContext)
            .map { rows -> rows.map { it.toPointType() } }

    /** One type by id in any state (including tombstoned), or null if unknown. */
    fun typeById(id: Uuid): PointType? =
        queries.selectById(ownerId, id.toString()).executeAsOneOrNull()?.toPointType()

    /** This owner's type changes not yet confirmed by the backend — sync's upload set. */
    fun pendingTypes(): List<PointType> =
        queries.pendingTypes(ownerId).executeAsList().map { it.toPointType() }

    /** Emits this owner's count of pending (not-yet-synced) type changes. */
    fun observePendingTypeCount(): Flow<Long> =
        queries.pendingTypeCount(ownerId).asFlow().mapToOne(queryContext)

    /** Marks the given type ids confirmed (no longer pending) after a successful sync push. */
    fun clearPending(ids: List<String>) {
        if (ids.isNotEmpty()) queries.clearPendingTypes(ownerId, ids)
    }

    /** The highest server `seq` this install has pulled for the type catalog — the type sync pull cursor. */
    fun typeCursor(): Long = stateQueries.selectState().executeAsOne().type_cursor

    /** Advances the type sync pull cursor after a successful pull. */
    fun setTypeCursor(value: Long) = stateQueries.setTypeCursor(value)

    private fun Point_type.toPointType() = PointType(
        id = Uuid.parse(id),
        name = name,
        hue = hue.toInt(),
        icon = icon,
        mode = PointMode.valueOf(mode),
        step = step,
        goal = PointGoal.valueOf(goal),
        target = target,
        unit = unit,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
        deletedAt = deleted_at?.let { Instant.fromEpochMilliseconds(it) },
    )
}
