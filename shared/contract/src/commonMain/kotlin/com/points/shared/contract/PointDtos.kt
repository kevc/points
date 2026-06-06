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

/**
 * A batch sync request: the push + pull halves in one round trip for one [ownerId].
 *
 * @property sinceSeq the highest server `seq` this client has already stored. The server returns only
 *   events newer than this — clock-skew-proof, unlike a timestamp cursor.
 * @property events the client's pending (not-yet-confirmed) events to upload; the server upserts them by
 *   id (idempotent) and assigns each a `seq`.
 */
@Serializable
data class SyncRequestDto(
    val ownerId: String,
    val sinceSeq: Long,
    val events: List<PointEventDto>,
)

/**
 * The batch sync response: the events the client was missing, and the cursor to send next time.
 *
 * @property events the owner's events with `seq` greater than the request's `sinceSeq`, ascending.
 * @property nextSeq the highest `seq` in [events] (or the request's `sinceSeq` if none) — the client stores
 *   it as its new cursor.
 */
@Serializable
data class SyncResponseDto(
    val events: List<PointEventDto>,
    val nextSeq: Long,
)
