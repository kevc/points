package com.points.backend.db

import com.points.backend.domain.EventStorage
import com.points.backend.domain.StoredEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * [EventStorage] over raw JDBC. SQL lives in constants, all access runs on [Dispatchers.IO], and the
 * append-only `point_event` table is created on construction (a real migration runner arrives later).
 *
 * Each row carries a server-assigned, monotonic [StoredEvent.seq] (the sync cursor). Append is
 * **insert-if-absent**, not replace: an event is immutable, so re-receiving one by `id` is a no-op that
 * leaves its original `seq` untouched — which is what makes `seq` a stable, gap-free cursor.
 */
class DatabaseEventStorage(private val dataSource: DataSource) : EventStorage {

    init {
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.execute(CREATE_TABLE) }
        }
    }

    override suspend fun append(event: StoredEvent): Unit = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(INSERT_IF_ABSENT).use { statement ->
                statement.setString(1, event.id)
                statement.setString(2, event.ownerId)
                statement.setString(3, event.pointTypeId)
                statement.setLong(4, event.delta)
                statement.setString(5, event.deviceId)
                statement.setLong(6, event.createdAt)
                statement.setString(7, event.id) // NOT EXISTS guard
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

    override suspend fun eventsSince(ownerId: String, sinceSeq: Long): List<StoredEvent> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(EVENTS_SINCE).use { statement ->
                    statement.setString(1, ownerId)
                    statement.setLong(2, sinceSeq)
                    statement.executeQuery().use { rs ->
                        buildList { while (rs.next()) add(rs.toStoredEvent()) }
                    }
                }
            }
        }

    private fun ResultSet.toStoredEvent() = StoredEvent(
        id = getString("id"),
        ownerId = getString("owner_id"),
        pointTypeId = getString("point_type_id"),
        delta = getLong("delta"),
        deviceId = getString("device_id"),
        createdAt = getLong("created_at"),
        seq = getLong("seq"),
    )

    private companion object {
        const val CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS point_event (
                seq           BIGINT       AUTO_INCREMENT,
                id            VARCHAR(64)  PRIMARY KEY,
                owner_id      VARCHAR(64)  NOT NULL,
                point_type_id VARCHAR(64)  NOT NULL,
                delta         BIGINT       NOT NULL,
                device_id     VARCHAR(128) NOT NULL,
                created_at    BIGINT       NOT NULL
            )
        """

        // Insert only when the id is new, so an event's server-assigned seq never changes on re-receipt.
        // (Portable to Postgres; the existing MERGE...KEY(id) would replace the row and reassign seq.)
        const val INSERT_IF_ABSENT = """
            INSERT INTO point_event (id, owner_id, point_type_id, delta, device_id, created_at)
            SELECT ?, ?, ?, ?, ?, ?
            WHERE NOT EXISTS (SELECT 1 FROM point_event WHERE id = ?)
        """

        const val VALUE_FOR_TYPE =
            "SELECT COALESCE(SUM(delta), 0) FROM point_event WHERE owner_id = ? AND point_type_id = ?"

        const val EVENTS_SINCE =
            "SELECT seq, id, owner_id, point_type_id, delta, device_id, created_at " +
                "FROM point_event WHERE owner_id = ? AND seq > ? ORDER BY seq"
    }
}
