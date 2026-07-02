package com.points.backend.db

import javax.sql.DataSource

/**
 * One versioned schema change: [statements] run in order, exactly once per database, tracked by [version]
 * in the `schema_version` table. Once a migration has shipped it is immutable — schema evolution is a new
 * migration with the next version, never an edit to an applied one.
 */
data class Migration(
    val version: Int,
    val description: String,
    val statements: List<String>,
)

/**
 * Applies pending [Migration]s at startup, before any storage touches the database. The `schema_version`
 * table records what has been applied; a re-run (every boot) is a no-op for recorded versions, and an
 * upgrade applies only the versions newer than the recorded maximum, in ascending order.
 *
 * Each migration commits atomically with its `schema_version` row, so a failed statement leaves the version
 * unrecorded and the migration retries on the next boot. (Postgres makes that rollback cover DDL too; H2
 * auto-commits DDL, so mid-migration atomicity there is best-effort — acceptable for the in-memory dev
 * database, which resets every boot anyway.) Assumes a single instance migrates at a time — fine for the
 * single-node deploys this backend targets; revisit alongside multi-instance deployment if that arrives.
 */
class MigrationRunner(private val dataSource: DataSource) {

    fun run(migrations: List<Migration>) {
        val ordered = migrations.sortedBy { it.version }
        val versions = ordered.map { it.version }
        require(versions.distinct() == versions) { "duplicate migration versions: $versions" }

        dataSource.connection.use { connection ->
            connection.createStatement().use { it.execute(CREATE_SCHEMA_VERSION) }
            val current = connection.createStatement().use { statement ->
                statement.executeQuery(MAX_VERSION).use { rs -> rs.next(); rs.getInt(1) }
            }

            ordered.filter { it.version > current }.forEach { migration ->
                connection.autoCommit = false
                try {
                    migration.statements.forEach { sql ->
                        connection.createStatement().use { it.execute(sql) }
                    }
                    connection.prepareStatement(RECORD_VERSION).use { statement ->
                        statement.setInt(1, migration.version)
                        statement.setString(2, migration.description)
                        statement.setLong(3, System.currentTimeMillis())
                        statement.executeUpdate()
                    }
                    connection.commit()
                } catch (e: Exception) {
                    connection.rollback()
                    throw e
                } finally {
                    connection.autoCommit = true
                }
            }
        }
    }

    private companion object {
        const val CREATE_SCHEMA_VERSION = """
            CREATE TABLE IF NOT EXISTS schema_version (
                version     INT          PRIMARY KEY,
                description VARCHAR(255) NOT NULL,
                applied_at  BIGINT       NOT NULL
            )
        """

        const val MAX_VERSION = "SELECT COALESCE(MAX(version), 0) FROM schema_version"

        const val RECORD_VERSION =
            "INSERT INTO schema_version (version, description, applied_at) VALUES (?, ?, ?)"
    }
}
