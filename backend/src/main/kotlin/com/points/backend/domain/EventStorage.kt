package com.points.backend.domain

/**
 * A ledger event as the server stores it. IDs and the timestamp are strings, matching the wire form.
 * [ownerId] is the tenancy/partition key: the server ledger holds many owners, and every read is scoped
 * to one.
 */
data class StoredEvent(
    val id: String,
    val ownerId: String,
    val pointTypeId: String,
    val delta: Long,
    val deviceId: String,
    val createdAt: String,
)

/**
 * Port over the server-side event ledger. The `db` layer implements it over raw JDBC; the `api` layer
 * depends only on this. Append is an idempotent upsert by [StoredEvent.id], so a client re-sending an
 * event is a no-op union.
 */
interface EventStorage {
    suspend fun append(event: StoredEvent)

    /** Current value (`SUM(delta)`, 0 if none) for [ownerId]'s [pointTypeId]. */
    suspend fun valueFor(ownerId: String, pointTypeId: String): Long
}
