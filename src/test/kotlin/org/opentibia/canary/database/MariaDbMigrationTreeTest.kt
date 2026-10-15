package org.opentibia.canary.database

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Structural guard for the MariaDB migration tree (wave 73, task K2).
 *
 * `db/migration/mariadb` is a dialect transcription of the PostgreSQL tree
 * (`db/migration/postgres`, still at `db/migration/` until the dialect-core
 * task lands): same versions, same file names, same table, column and index
 * names, with the PostgreSQL-only constructs replaced by their MariaDB 11.4
 * equivalents. Real execution against a MariaDB server is covered by round 2's
 * testcontainers integration test; this suite keeps the two trees in lockstep
 * and fails on a PostgreSQL-only construct that would break the MariaDB chain.
 */
class MariaDbMigrationTreeTest {

    @Test
    fun `mariadb tree mirrors the postgres tree file for file`() {
        val postgresNames = migrationFiles(postgresTree()).map { it.fileName.toString() }
        val mariadbNames = migrationFiles(mariadbTree()).map { it.fileName.toString() }

        assertThat(mariadbNames).containsExactlyElementsOf(postgresNames)

        val versions = mariadbNames.map(::versionOf).sorted()
        assertThat(versions).containsExactlyElementsOf((1..14) + (16..22))
    }

    @Test
    fun `mariadb tree contains no PostgreSQL-only SQL`() {
        val forbidden = listOf("BIGSERIAL", "BYTEA", "ON CONFLICT", "pg_", "DOUBLE PRECISION")

        val offenders = migrationFiles(mariadbTree()).flatMap { file ->
            val sql = Files.readString(file)
            forbidden.filter { sql.contains(it, ignoreCase = true) }
                .map { token -> "${file.fileName}: $token" }
        }

        assertThat(offenders).isEmpty()
    }

    @Test
    fun `mariadb tree does not rely on ANSI_QUOTES for identifiers`() {
        val offenders = migrationFiles(mariadbTree())
            .filter { Files.readString(it).contains('"') }
            .map { it.fileName.toString() }

        assertThat(offenders).isEmpty()
    }

    @Test
    fun `mariadb seed guards keep NOT EXISTS and add FROM DUAL for the WHERE clause`() {
        val guarded = migrationFiles(mariadbTree()).filter { Files.readString(it).contains("WHERE NOT EXISTS") }

        assertThat(guarded.map { it.fileName.toString() })
            .containsExactly("V13__bestiary_bosstiary_persistence.sql", "V2__seed_test_accounts.sql")

        guarded.forEach { file ->
            val sql = Files.readString(file)
            assertThat(sql).contains("INSERT INTO")
            assertThat(sql).contains("FROM DUAL")
        }

        val withoutFrom = migrationFiles(mariadbTree())
            .filter { Files.readString(it).contains("WHERE NOT EXISTS") && !Files.readString(it).contains("FROM DUAL") }
            .map { it.fileName.toString() }

        assertThat(withoutFrom).isEmpty()
    }

    @Test
    fun `mariadb tree mirrors table column and index names`() {
        val postgres = schemaSurface(postgresTree())
        val mariadb = schemaSurface(mariadbTree())

        assertThat(mariadb.tables).containsExactlyInAnyOrderEntriesOf(postgres.tables)
        assertThat(mariadb.indexes).containsExactlyElementsOf(postgres.indexes)
        assertThat(mariadb.alterTargets).containsExactlyInAnyOrderEntriesOf(postgres.alterTargets)
    }
}

private const val MARIADB_TREE = "db/migration/mariadb"
private const val POSTGRES_TREE = "db/migration/postgres"
private const val LEGACY_MIGRATION_ROOT = "db/migration"

private val createTableRegex = Regex("CREATE TABLE IF NOT EXISTS\\s+(\\w+)\\s+\\((.*?)\\n\\);", RegexOption.DOT_MATCHES_ALL)
private val createIndexRegex = Regex("CREATE INDEX IF NOT EXISTS\\s+(\\w+)")
private val alterTableRegex = Regex("ALTER TABLE\\s+(\\w+)")
private val versionRegex = Regex("^V(\\d+)__.*\\.sql$")
private val constraintHeads = setOf("CONSTRAINT", "PRIMARY", "FOREIGN", "UNIQUE", "REFERENCES", "CHECK")

private data class SchemaSurface(
    val tables: Map<String, List<String>>,
    val indexes: List<String>,
    val alterTargets: Map<String, List<String>>,
)

/**
 * Resolves a directory of resources shipped through `src/main/resources`.
 * The dialect-core task moves the PostgreSQL tree under `postgres/`; until
 * that lands it is still the migration root itself, so both are probed.
 */
private fun postgresTree(): Path {
    val candidates = listOfNotNull(resourceDir(POSTGRES_TREE), resourceDir(LEGACY_MIGRATION_ROOT))
    return candidates.firstOrNull { migrationFiles(it).isNotEmpty() }
        ?: error("PostgreSQL migration tree not found under $POSTGRES_TREE or $LEGACY_MIGRATION_ROOT")
}

private fun mariadbTree(): Path = checkNotNull(resourceDir(MARIADB_TREE)) {
    "MariaDB migration tree not found under $MARIADB_TREE"
}

private fun resourceDir(path: String): Path? {
    val url = MariaDbMigrationTreeTest::class.java.getResource("/$path") ?: return null
    return Paths.get(url.toURI())
}

private fun migrationFiles(tree: Path): List<Path> =
    (tree.toFile().listFiles() ?: emptyArray())
        .filter { it.isFile && it.name.endsWith(".sql") }
        .map { it.toPath() }
        .sortedBy { it.fileName.toString() }

private fun versionOf(fileName: String): Int =
    requireNotNull(versionRegex.find(fileName)) { "unexpected migration file name: $fileName" }
        .groupValues[1]
        .toInt()

private fun schemaSurface(tree: Path): SchemaSurface {
    val tables = linkedMapOf<String, List<String>>()
    val indexes = mutableListOf<String>()
    val alterTargets = linkedMapOf<String, List<String>>()

    migrationFiles(tree).forEach { file ->
        val sql = Files.readString(file)
        createTableRegex.findAll(sql).forEach { match ->
            tables[match.groupValues[1]] = columnNames(match.groupValues[2])
        }
        createIndexRegex.findAll(sql).forEach { match -> indexes += match.groupValues[1] }
        alterTargets[file.fileName.toString()] = alterTableRegex.findAll(sql).map { it.groupValues[1] }.toList()
    }

    return SchemaSurface(tables, indexes, alterTargets)
}

private fun columnNames(createTableBody: String): List<String> =
    createTableBody.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filterNot { line -> line.substringBefore(' ').uppercase() in constraintHeads }
        .map { line -> line.substringBefore(' ').trim('`', '"') }
        .toList()
