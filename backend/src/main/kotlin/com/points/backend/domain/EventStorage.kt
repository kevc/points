package com.points.backend.domain

/**
 * A ledger event as the server stores it. [ownerId] is the tenancy/partition key: the server ledger holds
 * many owners, and every read is scoped to one.
 *
 * @property createdAt epoch milliseconds (the wire form is ISO-8601; the api layer parses on ingest). Used
 *   for display/ordering only, never as a sync cursor.
 * @property seq the server-assigned, monotonic sequence — the sync cursor. Set by the store on insert and
 *   stable across an idempotent re-receipt; it is meaningful only on reads (it is `0` on the append path,
 *   where the database assigns the real value).
 */
data class StoredEvent(
    val id: String,
    val ownerId: String,
    val pointTypeId: String,
    val delta: Long,
    val deviceId: String,
    val createdAt: Long,
    val seq: Long = 0L,
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

    /**
     * The owner's events with [StoredEvent.seq] greater than [sinceSeq], in ascending `seq` order — the
     * pull half of sync. A client passes the highest `seq` it has already stored and receives only what it
     * is missing.
     */
    suspend fun eventsSince(ownerId: String, sinceSeq: Long): List<StoredEvent>
}
