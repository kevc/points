package com.points.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

actual fun defaultHttpClientEngine(): HttpClientEngine = Darwin.create()
