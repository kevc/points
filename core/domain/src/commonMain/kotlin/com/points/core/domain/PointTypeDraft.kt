package com.points.core.domain

/**
 * The user-editable fields of a [PointType] — everything the create/edit form collects, minus the
 * identity and timestamps the repository owns ([PointType.id], [PointType.createdAt], [PointType.updatedAt],
 * [PointType.deletedAt]). One value object serves both `CreatePointType` and `EditPointType` so the two
 * stay in lockstep as fields are added.
 *
 * Defaults match the design's "new point" form: a climbing cumulative tally, hue 152 (sage), step 1, no
 * daily target.
 */
data class PointTypeDraft(
    val name: String,
    val hue: Int = 152,
    val icon: String = "spark",
    val mode: PointMode = PointMode.CUMULATIVE,
    val step: Long = 1,
    val goal: PointGoal = PointGoal.UP,
    val target: Long? = null,
    val unit: String = "",
)
