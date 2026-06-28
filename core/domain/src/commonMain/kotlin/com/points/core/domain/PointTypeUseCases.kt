package com.points.core.domain

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The point-type use cases, grouped thematically. Each is its own named `fun interface` (never a
 * `typealias`) so injected instances erase to distinct types and Koin bindings never collide.
 *
 * Callers (Decompose components / MVIKotlin stores) inject these individually — never [PointTypeRepository].
 * Factory functions in `core/data` build them via SAM and own main-safety (`withContext(io)`).
 */

/** Creates a new point type from a [PointTypeDraft]. */
fun interface CreatePointType {
    suspend operator fun invoke(draft: PointTypeDraft): PointType
}

/** Applies a [PointTypeDraft] to an existing type, returning the updated entity (null if unknown). */
@OptIn(ExperimentalUuidApi::class)
fun interface EditPointType {
    suspend operator fun invoke(id: Uuid, draft: PointTypeDraft): PointType?
}

/** Tombstones a point type (soft delete — reversible, never erased). */
@OptIn(ExperimentalUuidApi::class)
fun interface DeletePointType {
    suspend operator fun invoke(id: Uuid)
}

/** Streams the active (non-tombstoned) point types. */
fun interface ObservePointTypes {
    operator fun invoke(): Flow<List<PointType>>
}
