package com.points.core.domain

import kotlinx.datetime.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A thing being counted (e.g. "days since quitting", "times meditated").
 *
 * Deletion is a tombstone, never a destructive remove: [deletedAt] is set rather than the row erased,
 * so the event ledger that references it stays meaningful and sync stays additive.
 */
@OptIn(ExperimentalUuidApi::class)
data class PointType(
    val id: Uuid,
    val name: String,
    val createdAt: Instant,
    val deletedAt: Instant? = null,
)
