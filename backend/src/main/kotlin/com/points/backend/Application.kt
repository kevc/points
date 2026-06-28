package com.points.backend

import com.points.backend.api.configureEventRoutes
import com.points.backend.db.DatabaseEventStorage
import com.points.backend.db.DatabasePointTypeStorage
import com.points.backend.plugins.configureLogging
import com.points.backend.plugins.configureSerialization
import com.points.backend.plugins.h2DataSource
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

/** Production wiring: H2-backed storage. Both stores share one data source (the same database). */
fun Application.module() {
    val dataSource = h2DataSource()
    configurePoints(
        StorageContainer(
            events = DatabaseEventStorage(dataSource),
            pointTypes = DatabasePointTypeStorage(dataSource),
        ),
    )
}

/** Installs plugins and routes against the given [storage]. Tests call this with their own container. */
fun Application.configurePoints(storage: StorageContainer) {
    configureLogging()
    configureSerialization()
    configureEventRoutes(storage)
}
