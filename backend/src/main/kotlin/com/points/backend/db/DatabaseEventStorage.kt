package com.points.backend.db

import com.points.backend.domain.EventStorage
import com.points.backend.domain.StoredEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.sql.DataSource

/**
 * [EventStorage] over raw JDBC. SQL lives in constants, all access runs on [Dispatchers.IO], and the
 * append-only `point_event` table is created on construction (a real migration runner arrives later).
 */
class DatabaseEventStorage(private val dataSource: DataSource) : EventStorage {

    init {
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.execute(CREATE_TABLE) }
        }
    }

    override suspend fun append(event: StoredEvent): Unit = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(UPSERT).use { statement ->
                statement.setString(1, event.id)
                statement.setString(2, event.ownerId)
                statement.setString(3, event.pointTypeId)
                statement.setLong(4, event.delta)
                statement.setString(5, event.deviceId)
                statement.setString(6, event.createdAt)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun valueFor(ownerId: String, pointTypeId: String): Long = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(VALUE_FOR_TYPE).use { statement ->
                statement.setString(1, ownerId)
                statement.setString(2, pointTypeId)
                statement.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
            }
        }
    }

    private companion object {
        const val CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS point_event (
                id            VARCHAR(64)  PRIMARY KEY,
                owner_id      VARCHAR(64)  NOT NULL,
                point_type_id VARCHAR(64)  NOT NULL,
                delta         BIGINT       NOT NULL,
                device_id     VARCHAR(128) NOT NULL,
                created_at    VARCHAR(64)  NOT NULL
            )
        """

        // MERGE ... KEY(id) is H2's idempotent upsert: re-sending an event by id replaces, never doubles.
        const val UPSERT =
            "MERGE INTO point_event (id, owner_id, point_type_id, delta, device_id, created_at) KEY(id) " +
                "VALUES (?, ?, ?, ?, ?, ?)"

        const val VALUE_FOR_TYPE =
            "SELECT COALESCE(SUM(delta), 0) FROM point_event WHERE owner_id = ? AND point_type_id = ?"
    }
}
