package com.points.backend.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import org.slf4j.event.Level

/**
 * Logs one line per request (method, path, status) so the dev backend's traffic — including client sync —
 * is visible on stdout via the existing logback config. INFO matches the root logger level.
 */
fun Application.configureLogging() {
    install(CallLogging) {
        level = Level.INFO
    }
}
