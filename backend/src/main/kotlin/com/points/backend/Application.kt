package com.points.backend

import com.points.backend.api.configureEventRoutes
import com.points.backend.db.DatabaseEventStorage
import com.points.backend.plugins.configureSerialization
import com.points.backend.plugins.h2DataSource
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

/** Production wiring: H2-backed storage. */
fun Application.module() {
    configurePoints(StorageContainer(DatabaseEventStorage(h2DataSource())))
}

/** Installs plugins and routes against the given [storage]. Tests call this with their own container. */
fun Application.configurePoints(storage: StorageContainer) {
    configureSerialization()
    configureEventRoutes(storage)
}
