package com.points.core.domain

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The counter use cases, grouped thematically. Each is its own named `fun interface` (never a
 * `typealias`) so injected instances erase to distinct types and Koin bindings never collide.
 *
 * Callers (Decompose components / MVIKotlin stores) inject these individually — never [PointRepository].
 * Factory functions in `core/data` build them via SAM and own main-safety (`withContext(io)`).
 *
 * A `fun interface`'s SAM method cannot declare a default value, so the common "by one" case is offered
 * as an extension overload ([invoke]) rather than a `delta = 1` default.
 */
@OptIn(ExperimentalUuidApi::class)
fun interface IncrementPoint {
    /** Appends `+delta` to the ledger for [pointTypeId]. */
    suspend operator fun invoke(pointTypeId: Uuid, delta: Long): PointEvent
}

/** Increments [pointTypeId] by one. */
@OptIn(ExperimentalUuidApi::class)
suspend operator fun IncrementPoint.invoke(pointTypeId: Uuid): PointEvent = invoke(pointTypeId, 1)

/** Appends `-delta` to the ledger for [pointTypeId]. */
@OptIn(ExperimentalUuidApi::class)
fun interface DecrementPoint {
    suspend operator fun invoke(pointTypeId: Uuid, delta: Long): PointEvent
}

/** Decrements [pointTypeId] by one. */
@OptIn(ExperimentalUuidApi::class)
suspend operator fun DecrementPoint.invoke(pointTypeId: Uuid): PointEvent = invoke(pointTypeId, 1)

/** Streams the current value of [pointTypeId]. */
@OptIn(ExperimentalUuidApi::class)
fun interface ObservePointValue {
    operator fun invoke(pointTypeId: Uuid): Flow<Long>
}

/**
 * Reconciles the local ledger with the backend: uploads pending events and pulls any the device is missing,
 * merging additively. Safe to call repeatedly (idempotent) and best-effort (a network failure leaves the
 * local ledger untouched and pending events intact for the next pass).
 */
fun interface SyncPointEvents {
    suspend operator fun invoke()
}

/**
 * Resets a point type's value to zero the non-destructive way: it appends a single **compensating** event
 * (`delta = −currentValue`), so the history stays intact and the change syncs additively like any other.
 * Returns that event; its `delta` negated is the amount to re-add to undo the reset.
 */
@OptIn(ExperimentalUuidApi::class)
fun interface ResetPointType {
    suspend operator fun invoke(pointTypeId: Uuid): PointEvent
}
