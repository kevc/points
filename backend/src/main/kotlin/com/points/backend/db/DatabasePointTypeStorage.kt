package com.points.backend.db

import com.points.backend.domain.PointTypeStorage
import com.points.backend.domain.StoredPointType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types
import javax.sql.DataSource

/**
 * [PointTypeStorage] over raw JDBC. SQL lives in constants, all access runs on [Dispatchers.IO], and the
 * `point_type` table is created on construction (a real migration runner arrives later).
 *
 * A type is a last-write-wins register, and [upsert] must stay correct under concurrent writers (two devices
 * syncing the same owner), so it is built from single-statement atomics instead of read-then-write:
 * a conditional `UPDATE ... WHERE updated_at < ?` applies the merge and re-stamps [StoredPointType.seq] from
 * a database sequence in one statement, an insert-if-absent covers the brand-new id, and a loser of the
 * insert race (unique violation, both writers passed `NOT EXISTS`) falls back to the conditional update.
 * The sequence (not `MAX(seq)+1`) is what makes concurrently accepted writes take **unique** seqs — a
 * duplicate seq would be silently skipped by the `seq > ?` pull and never reach the other device. Every
 * accepted change gets a fresh seq so a rename re-propagates on the next pull — unlike an immutable event,
 * whose seq is stable.
 */
class DatabasePointTypeStorage(private val dataSource: DataSource) : PointTypeStorage {

    init {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(CREATE_TABLE)
                statement.execute(CREATE_OWNER_SEQ_INDEX)
                statement.execute(CREATE_SEQ)
                // Re-seed so the sequence can never lag rows written before it existed (a pre-#99 database):
                // a lagging sequence would deal already-taken seqs, recreating the silent-skip bug. Runs at
                // construction only — single instance at boot, before any traffic. (#55's migration runner
                // will own this properly.)
                val nextSeq = statement.executeQuery(MAX_SEQ).use { rs -> rs.next(); rs.getLong(1) + 1 }
                statement.execute("ALTER SEQUENCE point_type_seq RESTART WITH $nextSeq")
            }
        }
    }

    override suspend fun upsert(type: StoredPointType): Unit = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            if (connection.updateIfNewer(type) > 0) return@withContext
            val inserted = try {
                connection.insertIfAbsent(type)
            } catch (e: SQLException) {
                if (e.sqlState != UNIQUE_VIOLATION) throw e
                0 // lost the insert race — the row exists now; merge below
            }
            // Not inserted → a copy exists (stored, or a concurrent writer beat us): apply last-write-wins.
            // 0 rows here means the stored copy is newer or same — the correct no-op.
            if (inserted == 0) connection.updateIfNewer(type)
        }
    }

    override suspend fun typesSince(ownerId: String, sinceSeq: Long, limit: Int): List<StoredPointType> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(TYPES_SINCE).use { statement ->
                    statement.setString(1, ownerId)
                    statement.setLong(2, sinceSeq)
                    statement.setInt(3, limit)
                    statement.executeQuery().use { rs ->
                        buildList { while (rs.next()) add(rs.toStoredPointType()) }
                    }
                }
            }
        }

    private fun Connection.updateIfNewer(type: StoredPointType): Int =
        prepareStatement(UPDATE_IF_NEWER).use { statement ->
            statement.setString(1, type.name)
            statement.setInt(2, type.hue)
            statement.setString(3, type.icon)
            statement.setString(4, type.mode)
            statement.setLong(5, type.step)
            statement.setString(6, type.goal)
            statement.setNullableLong(7, type.target)
            statement.setString(8, type.unit)
            statement.setLong(9, type.updatedAt)
            statement.setNullableLong(10, type.deletedAt)
            statement.setString(11, type.id)
            statement.setLong(12, type.updatedAt) // WHERE updated_at < ? — strictly newer wins
            statement.executeUpdate()
        }

    private fun Connection.insertIfAbsent(type: StoredPointType): Int =
        prepareStatement(INSERT_IF_ABSENT).use { statement ->
            statement.setString(1, type.id)
            statement.setString(2, type.ownerId)
            statement.setString(3, type.name)
            statement.setInt(4, type.hue)
            statement.setString(5, type.icon)
            statement.setString(6, type.mode)
            statement.setLong(7, type.step)
            statement.setString(8, type.goal)
            statement.setNullableLong(9, type.target)
            statement.setString(10, type.unit)
            statement.setLong(11, type.createdAt)
            statement.setLong(12, type.updatedAt)
            statement.setNullableLong(13, type.deletedAt)
            statement.setString(14, type.id) // NOT EXISTS guard
            statement.executeUpdate()
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
        /** Unique/primary-key violation — the same SQLState on H2 and Postgres. */
        const val UNIQUE_VIOLATION = "23505"

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

        // NEXTVAL('...') works on both engines: native on Postgres, a compatibility function on H2.
        const val CREATE_SEQ = "CREATE SEQUENCE IF NOT EXISTS point_type_seq"

        const val MAX_SEQ = "SELECT COALESCE(MAX(seq), 0) FROM point_type"

        // Accept only a strictly newer write (LWW) and re-stamp seq, atomically in one statement — the row
        // lock makes concurrent updates serialize and re-check the guard, so arrival order can't matter.
        const val UPDATE_IF_NEWER = """
            UPDATE point_type SET
                seq = NEXTVAL('point_type_seq'), name = ?, hue = ?, icon = ?, mode = ?, step = ?, goal = ?,
                target = ?, unit = ?, updated_at = ?, deleted_at = ?
            WHERE id = ? AND updated_at < ?
        """

        // NOT EXISTS is the fast path; the id race it can't close resolves as a unique violation upstream.
        const val INSERT_IF_ABSENT = """
            INSERT INTO point_type
                (seq, id, owner_id, name, hue, icon, mode, step, goal, target, unit, created_at, updated_at, deleted_at)
            SELECT NEXTVAL('point_type_seq'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            WHERE NOT EXISTS (SELECT 1 FROM point_type WHERE id = ?)
        """

        const val TYPES_SINCE =
            "SELECT seq, id, owner_id, name, hue, icon, mode, step, goal, target, unit, created_at, " +
                "updated_at, deleted_at FROM point_type WHERE owner_id = ? AND seq > ? ORDER BY seq LIMIT ?"

        // Keeps the windowed pull (owner_id = ? AND seq > ? ORDER BY seq LIMIT ?) an index range scan.
        const val CREATE_OWNER_SEQ_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_point_type_owner_seq ON point_type (owner_id, seq)"
    }
}
