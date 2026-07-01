@file:OptIn(ExperimentalUuidApi::class)

package com.points.core.data

import com.points.core.domain.CreatePointType
import com.points.core.domain.DeletePointType
import com.points.core.domain.EditPointType
import com.points.core.domain.ObservePointTypes
import com.points.core.domain.PointTypeRepository
import com.points.core.domain.RestorePointType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi

/**
 * Factories that build the point-type use cases over a [PointTypeRepository], owning main-safety: the
 * suspend use cases hop to [io] via `withContext`, so neither the repository nor the calling component
 * touches a dispatcher. Tests swap [io] for a test dispatcher, making `withContext` a no-op.
 */
fun createPointType(repo: PointTypeRepository, io: CoroutineDispatcher): CreatePointType =
    CreatePointType { draft -> withContext(io) { repo.create(draft) } }

fun editPointType(repo: PointTypeRepository, io: CoroutineDispatcher): EditPointType =
    EditPointType { id, draft -> withContext(io) { repo.edit(id, draft) } }

fun deletePointType(repo: PointTypeRepository, io: CoroutineDispatcher): DeletePointType =
    DeletePointType { id -> withContext(io) { repo.delete(id) } }

fun restorePointType(repo: PointTypeRepository, io: CoroutineDispatcher): RestorePointType =
    RestorePointType { id -> withContext(io) { repo.restore(id) } }

fun observePointTypes(repo: PointTypeRepository): ObservePointTypes =
    ObservePointTypes { repo.observeTypes() }
