package com.points.backend.domain

/**
 * A point type as the server stores it. [ownerId] is the tenancy/partition key. Unlike [StoredEvent] (an
 * immutable ledger row), a type is **mutable** and merged last-write-wins by [updatedAt].
 *
 * @property createdAt epoch milliseconds; immutable identity timestamp (wire form is ISO-8601, parsed on ingest).
 * @property updatedAt epoch milliseconds; the **last-write-wins key** — a higher value wins on conflict.
 * @property deletedAt epoch milliseconds, or null; non-null is a tombstone (soft delete).
 * @property seq the server-assigned, monotonic sequence — the type sync cursor. Re-stamped on **every**
 *   accepted change (so a rename re-propagates), unlike an event's stable seq. Meaningful only on reads.
 */
data class StoredPointType(
    val id: String,
    val ownerId: String,
    val name: String,
    val hue: Int,
    val icon: String,
    val mode: String,
    val step: Long,
    val goal: String,
    val target: Long?,
    val unit: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val seq: Long = 0L,
)

/**
 * Port over the server-side point-type catalog. The `db` layer implements it over raw JDBC; the `api` layer
 * depends only on this. [upsert] merges last-write-wins: a change is applied (and re-stamped with a new
 * [StoredPointType.seq]) only when its [StoredPointType.updatedAt] is strictly newer than the stored copy, so
 * resending a stale type is a no-op union.
 */
interface PointTypeStorage {
    suspend fun upsert(type: StoredPointType)

    /**
     * The owner's types with [StoredPointType.seq] greater than [sinceSeq], in ascending `seq` order — the
     * pull half of type sync. Includes tombstoned types so deletes propagate.
     */
    suspend fun typesSince(ownerId: String, sinceSeq: Long): List<StoredPointType>
}
