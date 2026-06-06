package com.points.shared.contract

import kotlinx.serialization.Serializable

/**
 * Wire form of a ledger event, shared by client and backend. UUIDs and the timestamp are carried as
 * strings so the contract stays free of platform-specific (`Uuid`/`Instant`) serializers; each side maps
 * to/from its own domain types. Sync upserts by [id], so resending an event is idempotent.
 *
 * @property ownerId the tenancy/partition key — every event belongs to exactly one owner, and sync only
 *   ever exchanges events within a single owner. Pre-auth it is an anonymous per-install token; from M6
 *   (Google sign-in) it is bound to the account, with no change to the events already keyed by it.
 */
@Serializable
data class PointEventDto(
    val id: String,
    val ownerId: String,
    val pointTypeId: String,
    val delta: Long,
    val deviceId: String,
    val createdAt: String,
)

/** Wire form of a point type's current value: the server's `SUM(delta)` for [pointTypeId]. */
@Serializable
data class PointValueDto(
    val pointTypeId: String,
    val value: Long,
)
