package com.points.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

/** Builds an [HttpClient] configured for the Points API: JSON content negotiation over [engine]. */
fun pointsHttpClient(engine: HttpClientEngine): HttpClient =
    HttpClient(engine) {
        install(ContentNegotiation) { json() }
    }

/** The platform's default networking engine — OkHttp on Android, Darwin/NSURLSession on iOS. */
expect fun defaultHttpClientEngine(): HttpClientEngine
