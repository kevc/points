package com.points.core.domain

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Port for the point-type catalog. Outer modules (`core/data` over `core/database` + `core/network`)
 * implement it; the domain never knows how persistence or sync works. Use cases — not components — depend
 * on this.
 *
 * The repository owns identity and timestamps: [create] generates the [PointType.id] and stamps
 * `createdAt`/`updatedAt`; [edit] re-stamps `updatedAt`; [delete] sets the tombstone. Callers supply only a
 * [PointTypeDraft] (or an id). Type changes ride the same batch sync as the event ledger, merged
 * last-write-wins by `updatedAt`.
 */
@OptIn(ExperimentalUuidApi::class)
interface PointTypeRepository {
    /** Creates and persists a new point type from [draft], returning the stored entity. */
    suspend fun create(draft: PointTypeDraft): PointType

    /**
     * Applies [draft] to the existing type [id], re-stamping `updatedAt`, and returns the updated entity.
     * The immutable `createdAt` is preserved. A no-op if [id] is unknown (returns null).
     */
    suspend fun edit(id: Uuid, draft: PointTypeDraft): PointType?

    /** Tombstones the type [id] (soft delete — sets `deletedAt`, never erases). Idempotent. */
    suspend fun delete(id: Uuid)

    /** Clears the tombstone on type [id] (the undo for [delete]), re-stamping `updatedAt`. Idempotent. */
    suspend fun restore(id: Uuid)

    /** Emits the active (non-tombstoned) types, re-emitting whenever the catalog changes. */
    fun observeTypes(): Flow<List<PointType>>
}
