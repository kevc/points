package com.points.backend.db

import com.points.backend.domain.EventStorage
import com.points.backend.domain.StoredEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

/**
 * [EventStorage] over raw JDBC. SQL lives in constants and all access runs on [Dispatchers.IO]. The
 * `point_event` schema is owned by [pointsMigrations], applied at startup — never created here.
 *
 * Each row carries a server-assigned, monotonic [StoredEvent.seq] (the sync cursor). Append is
 * **insert-if-absent**, not replace: an event is immutable, so re-receiving one by `id` is a no-op that
 * leaves its original `seq` untouched — which is what makes `seq` a stable cursor. `seq` is dealt by the
 * database (an identity column), never computed from the table's contents, so concurrent appends can't
 * take the same value — a duplicate seq would be silently skipped by the `seq > ?` pull.
 */
class DatabaseEventStorage(private val dataSource: DataSource) : EventStorage {

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
                try {
                    statement.executeUpdate()
                } catch (e: SQLException) {
                    // Two writers can both pass NOT EXISTS with the same id; the loser's violation is just
                    // the idempotent union discovering the event already landed — swallow it, same as the
                    // sequential re-receipt no-op. Anything else is a real failure.
                    if (e.sqlState != UNIQUE_VIOLATION) throw e
                }
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

    override suspend fun eventsSince(ownerId: String, sinceSeq: Long, limit: Int): List<StoredEvent> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(EVENTS_SINCE).use { statement ->
                    statement.setString(1, ownerId)
                    statement.setLong(2, sinceSeq)
                    statement.setInt(3, limit)
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
        /** Unique/primary-key violation — the same SQLState on H2 and Postgres. */
        const val UNIQUE_VIOLATION = "23505"

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
                "FROM point_event WHERE owner_id = ? AND seq > ? ORDER BY seq LIMIT ?"
    }
}
