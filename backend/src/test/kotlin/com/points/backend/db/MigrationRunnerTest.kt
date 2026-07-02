package com.points.backend.db

import com.points.backend.plugins.h2DataSource
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MigrationRunnerTest {

    private fun dataSource(): DataSource =
        h2DataSource("jdbc:h2:mem:migrations_${UUID.randomUUID()};DB_CLOSE_DELAY=-1")

    private val v1 = Migration(
        version = 1,
        description = "widget table",
        statements = listOf("CREATE TABLE widget (id VARCHAR(64) PRIMARY KEY)"),
    )
    private val v2 = Migration(
        version = 2,
        description = "widget name column",
        statements = listOf("ALTER TABLE widget ADD COLUMN name VARCHAR(255)"),
    )

    private fun DataSource.appliedVersions(): List<Int> =
        connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT version FROM schema_version ORDER BY version").use { rs ->
                    buildList { while (rs.next()) add(rs.getInt(1)) }
                }
            }
        }

    private fun DataSource.execute(sql: String) =
        connection.use { connection -> connection.createStatement().use { it.execute(sql) } }

    @Test
    fun appliesPendingMigrationsInOrderAndRecordsThem() {
        val db = dataSource()
        MigrationRunner(db).run(listOf(v2, v1)) // order in the list must not matter

        assertEquals(listOf(1, 2), db.appliedVersions())
        db.execute("INSERT INTO widget (id, name) VALUES ('w1', 'Widget')") // both migrations took effect
    }

    @Test
    fun rerunningTheSameMigrationsIsANoOp() {
        val db = dataSource()
        val runner = MigrationRunner(db)
        runner.run(listOf(v1, v2))
        runner.run(listOf(v1, v2)) // second boot: nothing to do, nothing to fail on

        assertEquals(listOf(1, 2), db.appliedVersions())
    }

    @Test
    fun anUpgradeAppliesOnlyTheNewerMigrations() {
        val db = dataSource()
        MigrationRunner(db).run(listOf(v1)) // first deploy ships v1 only
        db.execute("INSERT INTO widget (id) VALUES ('pre-upgrade')")

        MigrationRunner(db).run(listOf(v1, v2)) // next deploy adds v2; v1 must not re-run

        assertEquals(listOf(1, 2), db.appliedVersions())
        db.execute("UPDATE widget SET name = 'kept' WHERE id = 'pre-upgrade'") // data survived, column added
    }

    @Test
    fun aFailingMigrationIsNotRecordedSoItRetriesNextBoot() {
        val db = dataSource()
        val broken = Migration(3, "broken", listOf("THIS IS NOT SQL"))

        MigrationRunner(db).run(listOf(v1))
        assertFailsWith<Exception> { MigrationRunner(db).run(listOf(v1, broken)) }

        assertEquals(listOf(1), db.appliedVersions(), "a failed migration must not be marked applied")
    }

    @Test
    fun rejectsDuplicateVersions() {
        val duplicate = Migration(1, "duplicate of v1", listOf("CREATE TABLE other (id INT)"))
        assertFailsWith<IllegalArgumentException> { MigrationRunner(dataSource()).run(listOf(v1, duplicate)) }
    }

    @Test
    fun pointsMigrationsBringAFreshDatabaseToTheCurrentSchema() {
        val db = dataSource()
        MigrationRunner(db).run(pointsMigrations)

        // The full runtime surface works against the migrated schema (identity, sequence, indexes).
        db.execute("INSERT INTO point_event (id, owner_id, point_type_id, delta, device_id, created_at) VALUES ('e1', 'o1', 't1', 1, 'd1', 0)")
        db.execute(
            "INSERT INTO point_type (seq, id, owner_id, name, hue, icon, mode, step, goal, target, unit, created_at, updated_at, deleted_at) " +
                "VALUES (NEXTVAL('point_type_seq'), 't1', 'o1', 'Water', 215, 'drop', 'DAILY', 1, 'UP', NULL, 'glasses', 0, 0, NULL)",
        )
        assertTrue(db.appliedVersions().isNotEmpty())
    }
}
