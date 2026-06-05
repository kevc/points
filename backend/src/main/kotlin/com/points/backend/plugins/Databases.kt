package com.points.backend.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

/** Default dev datasource: H2 in-memory, kept alive for the JVM's lifetime. Postgres is wired in later. */
const val DEV_H2_URL = "jdbc:h2:mem:points;DB_CLOSE_DELAY=-1"

fun h2DataSource(jdbcUrl: String = DEV_H2_URL): DataSource =
    HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            driverClassName = "org.h2.Driver"
            maximumPoolSize = 4
        },
    )
