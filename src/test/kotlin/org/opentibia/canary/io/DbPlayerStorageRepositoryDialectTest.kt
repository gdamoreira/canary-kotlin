package org.opentibia.canary.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.database.SqlDialect

/**
 * SQL-shape unit tests for the dialect-aware statements of
 * [DbPlayerStorageRepository]. H2 keeps its shipped `MERGE ... USING (VALUES)`
 * bulk upsert, PostgreSQL gets the equivalent `ON CONFLICT ... DO UPDATE`, and
 * MariaDB gets upstream Canary's `INSERT ... ON DUPLICATE KEY UPDATE
 * value = VALUES(value)` (`DBInsert::upsert`, src/database/database.cpp:453-475;
 * `src/io/player_storage_repository_db.cpp:45-57`). `key` and `value` are
 * keywords under MariaDB/MySQL, so every identifier is dialect-quoted.
 */
class DbPlayerStorageRepositoryDialectTest {

    @Test
    fun `H2 load reads the ANSI-quoted key and value columns`() {
        assertThat(DbPlayerStorageRepository.loadSql(SqlDialect.H2))
            .isEqualTo("""SELECT "key", "value" FROM player_storage WHERE player_id = ?""")
        assertThat(DbPlayerStorageRepository.loadSql(SqlDialect.POSTGRESQL))
            .isEqualTo("""SELECT "key", "value" FROM player_storage WHERE player_id = ?""")
    }

    @Test
    fun `MariaDB load reads the backtick-quoted key and value columns`() {
        assertThat(DbPlayerStorageRepository.loadSql(SqlDialect.MARIADB))
            .isEqualTo("SELECT `key`, `value` FROM player_storage WHERE player_id = ?")
    }

    @Test
    fun `delete keys quotes the key column per dialect`() {
        assertThat(DbPlayerStorageRepository.deleteKeysSql(SqlDialect.H2, "?, ?"))
            .isEqualTo("""DELETE FROM player_storage WHERE player_id = ? AND "key" IN (?, ?)""")
        assertThat(DbPlayerStorageRepository.deleteKeysSql(SqlDialect.MARIADB, "?, ?"))
            .isEqualTo("DELETE FROM player_storage WHERE player_id = ? AND `key` IN (?, ?)")
    }

    @Test
    fun `H2 upsert is the shipped MERGE USING VALUES shape`() {
        assertThat(DbPlayerStorageRepository.upsertSql(SqlDialect.H2, 2)).isEqualTo(
            """MERGE INTO player_storage AS target USING (VALUES (?, ?, ?), (?, ?, ?)) """ +
                """AS source (player_id, "key", "value")""" +
                """ ON target.player_id = source.player_id AND target."key" = source."key" """ +
                """WHEN MATCHED THEN UPDATE SET "value" = source."value" """ +
                """WHEN NOT MATCHED THEN INSERT (player_id, "key", "value")""" +
                """ VALUES (source.player_id, source."key", source."value")""",
        )
    }

    @Test
    fun `PostgreSQL upsert is the ON CONFLICT DO UPDATE shape`() {
        assertThat(DbPlayerStorageRepository.upsertSql(SqlDialect.POSTGRESQL, 2)).isEqualTo(
            """INSERT INTO player_storage (player_id, "key", "value") VALUES (?, ?, ?), (?, ?, ?) """ +
                """ON CONFLICT (player_id, "key") DO UPDATE SET "value" = EXCLUDED."value"""",
        )
    }

    @Test
    fun `MariaDB upsert is the ON DUPLICATE KEY UPDATE VALUES shape`() {
        assertThat(DbPlayerStorageRepository.upsertSql(SqlDialect.MARIADB, 2)).isEqualTo(
            "INSERT INTO player_storage (player_id, `key`, `value`) VALUES (?, ?, ?), (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)",
        )
    }

    @Test
    fun `every dialect binds the same player_id key value row shape`() {
        for (dialect in SqlDialect.entries) {
            val sql = DbPlayerStorageRepository.upsertSql(dialect, 3)
            assertThat(sql).describedAs("%s row placeholders", dialect)
                .contains("(?, ?, ?), (?, ?, ?), (?, ?, ?)")
        }
    }
}
