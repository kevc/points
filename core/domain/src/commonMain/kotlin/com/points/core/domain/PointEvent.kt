package com.points.core.domain

import kotlinx.datetime.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * An immutable increment/decrement appended to the ledger. Events are never updated or hard-deleted; a
 * "reset" is a compensating [delta] event, not a deletion.
 *
 * @property id client-generated, so syncing two devices is an idempotent union (upsert by [id]).
 * @property delta +1 / -1 / arbitrary. The current value of a point type is the sum of its deltas.
 */
@OptIn(ExperimentalUuidApi::class)
data class PointEvent(
    val id: Uuid,
    val pointTypeId: Uuid,
    val delta: Long,
    val deviceId: String,
    val createdAt: Instant,
)

/**
 * The current value of a set of events: the sum of their deltas.
 *
 * This is the canonical (PN-counter style CRDT) definition of "value" — order-independent, so events
 * applied in any order on any device converge. The SQL `SUM(delta)` query mirrors this exactly.
 *
 * Callers that hold events for multiple point types should filter to one [PointEvent.pointTypeId] first.
 */
fun Iterable<PointEvent>.currentValue(): Long = sumOf(PointEvent::delta)
