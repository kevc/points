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
 * Wire form of a point type (the mutable catalog metadata), shared by client and backend. Like
 * [PointEventDto], ids and timestamps are carried as strings so the contract stays free of platform
 * serializers. Unlike events, a type is mutable, so sync merges it **last-write-wins by [updatedAt]** rather
 * than as a union — resending an older copy is a no-op. [deletedAt] non-null is a tombstone (soft delete).
 *
 * @property mode the [PointType] mode enum name on the wire (e.g. "CUMULATIVE"/"DAILY").
 * @property goal the goal enum name on the wire (e.g. "UP"/"DOWN").
 * @property target the optional daily target; null means no target.
 */
@Serializable
data class PointTypeDto(
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
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)

/**
 * A batch sync request: the push + pull halves in one round trip for one [ownerId].
 *
 * @property sinceSeq the highest server `seq` this client has already stored. The server returns only
 *   events newer than this — clock-skew-proof, unlike a timestamp cursor.
 * @property events the client's pending (not-yet-confirmed) events to upload; the server upserts them by
 *   id (idempotent) and assigns each a `seq`.
 * @property sinceTypeSeq the highest server type-`seq` this client has stored — the pull cursor for the
 *   point-type catalog. Independent of [sinceSeq] because a type re-stamps its seq on every edit, whereas
 *   events are immutable. Defaults to 0 for a fresh client.
 * @property pointTypes the client's pending type changes to upload; the server merges them last-write-wins
 *   by `updatedAt` and re-stamps a type-`seq`. Defaults to empty.
 */
@Serializable
data class SyncRequestDto(
    val ownerId: String,
    val sinceSeq: Long,
    val events: List<PointEventDto>,
    val sinceTypeSeq: Long = 0,
    val pointTypes: List<PointTypeDto> = emptyList(),
)

/**
 * The batch sync response: a **window** of the events the client was missing, and the cursor to send next
 * time. The server bounds each pull to a page, so a long-offline device drains in several round trips
 * instead of one unbounded response — [hasMoreEvents]/[hasMoreTypes] tell the client to immediately sync
 * again from the advanced cursors until both are false.
 *
 * @property events the owner's events with `seq` greater than the request's `sinceSeq`, ascending, capped
 *   at the server's page size.
 * @property nextSeq the highest `seq` in [events] (or the request's `sinceSeq` if none) — the client stores
 *   it as its new cursor.
 * @property pointTypes the owner's type changes with type-`seq` greater than the request's `sinceTypeSeq`,
 *   ascending, capped at the server's page size; the client applies them last-write-wins. Defaults to empty.
 * @property nextTypeSeq the highest type-`seq` in [pointTypes] (or the request's `sinceTypeSeq` if none) —
 *   the client's new type cursor. Defaults to 0.
 * @property hasMoreEvents whether events beyond this page remain on the server ([events] was truncated at
 *   the page size). Defaults to false so a pre-paging response reads as a complete pull.
 * @property hasMoreTypes the same signal for the type half.
 */
@Serializable
data class SyncResponseDto(
    val events: List<PointEventDto>,
    val nextSeq: Long,
    val pointTypes: List<PointTypeDto> = emptyList(),
    val nextTypeSeq: Long = 0,
    val hasMoreEvents: Boolean = false,
    val hasMoreTypes: Boolean = false,
)
