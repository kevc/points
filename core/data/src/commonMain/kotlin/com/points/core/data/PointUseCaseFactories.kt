@file:OptIn(ExperimentalUuidApi::class)

package com.points.core.data

import com.points.core.domain.DecrementPoint
import com.points.core.domain.IncrementPoint
import com.points.core.domain.ObservePointValue
import com.points.core.domain.PointRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi

/**
 * Factories that build the domain use cases over a [PointRepository], owning main-safety: the suspend
 * use cases hop to [io] via `withContext`, so neither the repository nor the calling component touches a
 * dispatcher. Tests swap [io] for a test dispatcher, making `withContext` a no-op.
 */
fun incrementPoint(repo: PointRepository, io: CoroutineDispatcher): IncrementPoint =
    IncrementPoint { pointTypeId, delta -> withContext(io) { repo.append(pointTypeId, delta) } }

fun decrementPoint(repo: PointRepository, io: CoroutineDispatcher): DecrementPoint =
    DecrementPoint { pointTypeId, delta -> withContext(io) { repo.append(pointTypeId, -delta) } }

fun observePointValue(repo: PointRepository): ObservePointValue =
    ObservePointValue { pointTypeId -> repo.observeValue(pointTypeId) }
