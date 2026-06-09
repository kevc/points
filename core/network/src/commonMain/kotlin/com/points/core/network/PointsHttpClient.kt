package com.points.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json

/**
 * Builds an [HttpClient] configured for the Points API: JSON content negotiation over [engine], plus
 * request/response [Logging] so sync traffic is visible while debugging. The logger writes via `println`
 * (rather than `Logger.SIMPLE`, which is JVM/Android-only and absent from `commonMain`), so it surfaces in
 * Android logcat (`System.out`) and on the iOS console with no SLF4J binding. [LogLevel.INFO] logs the
 * method/URL and response status without bodies (no event payloads or owner tokens in the log); raise to
 * [LogLevel.ALL] locally if you need to see the JSON.
 */
fun pointsHttpClient(engine: HttpClientEngine): HttpClient =
    HttpClient(engine) {
        install(ContentNegotiation) { json() }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) = println("Points HTTP: $message")
            }
            level = LogLevel.INFO
        }
    }

/** The platform's default networking engine — OkHttp on Android, Darwin/NSURLSession on iOS. */
expect fun defaultHttpClientEngine(): HttpClientEngine
