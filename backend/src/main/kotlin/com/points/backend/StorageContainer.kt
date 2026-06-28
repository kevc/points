package com.points.backend

import com.points.backend.domain.EventStorage
import com.points.backend.domain.PointTypeStorage

/**
 * Manual DI: the storage ports the routes need, assembled at startup and passed into the route config.
 * The backend deliberately does not use Koin (that is the client's DI).
 */
class StorageContainer(
    val events: EventStorage,
    val pointTypes: PointTypeStorage,
)
