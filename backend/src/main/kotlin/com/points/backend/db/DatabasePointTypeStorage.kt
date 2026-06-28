package com.points.backend.db

import com.points.backend.domain.PointTypeStorage
import com.points.backend.domain.StoredPointType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import javax.sql.DataSource

/**
 * [PointTypeStorage] over raw JDBC. SQL lives in constants, all access runs on [Dispatchers.IO], and the
 * `point_type` table is created on construction (a real migration runner arrives later).
 *
 * A type is a last-write-wins register, so [upsert] is read-then-write: insert when new, otherwise overwrite
 * the mutable fields only when the incoming `updated_at` is strictly newer than the stored copy. Every
 * accepted change is re-stamped with a fresh, monotonic [StoredPointType.seq] (`MAX(seq)+1`) so a rename
 * re-propagates to other devices on the next pull — unlike an immutable event, whose seq is stable.
 */
class DatabasePointTypeStorage(private val dataSource: DataSource) : PointTypeStorage {

    init {
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.execute(CREATE_TABLE) }
        }
    }

    override suspend fun upsert(type: StoredPointType): Unit = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val existingUpdatedAt: Long? = connection.prepareStatement(SELECT_UPDATED_AT).use { statement ->
                statement.setString(1, type.id)
                statement.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
            }
            // Stale or unchanged → no-op (last-write-wins by updatedAt).
            if (existingUpdatedAt != null && type.updatedAt <= existingUpdatedAt) return@withContext

            val nextSeq = connection.prepareStatement(NEXT_SEQ).use { statement ->
                statement.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
            }
            val sql = if (existingUpdatedAt == null) INSERT else UPDATE
            connection.prepareStatement(sql).use { statement ->
                if (existingUpdatedAt == null) statement.bindInsert(type, nextSeq) else statement.bindUpdate(type, nextSeq)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun typesSince(ownerId: String, sinceSeq: Long): List<StoredPointType> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(TYPES_SINCE).use { statement ->
                    statement.setString(1, ownerId)
                    statement.setLong(2, sinceSeq)
                    statement.executeQuery().use { rs ->
                        buildList { while (rs.next()) add(rs.toStoredPointType()) }
                    }
                }
            }
        }

    private fun PreparedStatement.bindInsert(type: StoredPointType, seq: Long) {
        setLong(1, seq)
        setString(2, type.id)
        setString(3, type.ownerId)
        setString(4, type.name)
        setInt(5, type.hue)
        setString(6, type.icon)
        setString(7, type.mode)
        setLong(8, type.step)
        setString(9, type.goal)
        setNullableLong(10, type.target)
        setString(11, type.unit)
        setLong(12, type.createdAt)
        setLong(13, type.updatedAt)
        setNullableLong(14, type.deletedAt)
    }

    private fun PreparedStatement.bindUpdate(type: StoredPointType, seq: Long) {
        setLong(1, seq)
        setString(2, type.name)
        setInt(3, type.hue)
        setString(4, type.icon)
        setString(5, type.mode)
        setLong(6, type.step)
        setString(7, type.goal)
        setNullableLong(8, type.target)
        setString(9, type.unit)
        setLong(10, type.updatedAt)
        setNullableLong(11, type.deletedAt)
        setString(12, type.id) // WHERE id = ?
    }

    private fun PreparedStatement.setNullableLong(index: Int, value: Long?) {
        if (value == null) setNull(index, Types.BIGINT) else setLong(index, value)
    }

    private fun ResultSet.toStoredPointType() = StoredPointType(
        id = getString("id"),
        ownerId = getString("owner_id"),
        name = getString("name"),
        hue = getInt("hue"),
        icon = getString("icon"),
        mode = getString("mode"),
        step = getLong("step"),
        goal = getString("goal"),
        target = getLong("target").takeUnless { wasNull() },
        unit = getString("unit"),
        createdAt = getLong("created_at"),
        updatedAt = getLong("updated_at"),
        deletedAt = getLong("deleted_at").takeUnless { wasNull() },
        seq = getLong("seq"),
    )

    private companion object {
        const val CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS point_type (
                seq        BIGINT       NOT NULL,
                id         VARCHAR(64)  PRIMARY KEY,
                owner_id   VARCHAR(64)  NOT NULL,
                name       VARCHAR(255) NOT NULL,
                hue        INT          NOT NULL,
                icon       VARCHAR(64)  NOT NULL,
                mode       VARCHAR(32)  NOT NULL,
                step       BIGINT       NOT NULL,
                goal       VARCHAR(32)  NOT NULL,
                target     BIGINT,
                unit       VARCHAR(64)  NOT NULL,
                created_at BIGINT       NOT NULL,
                updated_at BIGINT       NOT NULL,
                deleted_at BIGINT
            )
        """

        const val SELECT_UPDATED_AT = "SELECT updated_at FROM point_type WHERE id = ?"

        // Re-stamp every accepted change with the next monotonic seq so updates re-propagate on pull.
        const val NEXT_SEQ = "SELECT COALESCE(MAX(seq), 0) + 1 FROM point_type"

        const val INSERT = """
            INSERT INTO point_type
                (seq, id, owner_id, name, hue, icon, mode, step, goal, target, unit, created_at, updated_at, deleted_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

        const val UPDATE = """
            UPDATE point_type SET
                seq = ?, name = ?, hue = ?, icon = ?, mode = ?, step = ?, goal = ?, target = ?, unit = ?,
                updated_at = ?, deleted_at = ?
            WHERE id = ?
        """

        const val TYPES_SINCE =
            "SELECT seq, id, owner_id, name, hue, icon, mode, step, goal, target, unit, created_at, " +
                "updated_at, deleted_at FROM point_type WHERE owner_id = ? AND seq > ? ORDER BY seq"
    }
}
