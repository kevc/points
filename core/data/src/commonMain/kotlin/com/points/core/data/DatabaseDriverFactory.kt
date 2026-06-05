package com.points.core.data

import app.cash.sqldelight.db.SqlDriver

/**
 * Creates the platform SQL driver for the ledger. Android needs a `Context` (passed in by DI from the
 * app), iOS needs nothing. Kept in the wiring module so `core/database` stays driver-agnostic.
 */
expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}
