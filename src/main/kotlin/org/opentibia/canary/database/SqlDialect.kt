package org.opentibia.canary.database

import java.sql.Connection

/**
 * Runtime SQL dialect of the live JDBC connection, detected from its
 * `DatabaseMetaData` product name.
 *
 * PostgreSQL 16+ is the production default, H2 (PostgreSQL mode,
 * `DATABASE_TO_LOWER=TRUE`) is the local in-memory test engine, and MariaDB /
 * MySQL share upstream Canary's statements (e.g. the
 * `INSERT ... ON DUPLICATE KEY UPDATE col = VALUES(col)` upsert of
 * `DBInsert::execute`, database.cpp:453-481).
 */
enum class SqlDialect {
    H2,
    POSTGRESQL,
    MARIADB;

    /**
     * Quotes an SQL identifier for this dialect. H2 and PostgreSQL use ANSI
     * double quotes; MariaDB/MySQL in their default `sql_mode` treat `"name"`
     * as a string literal, so identifiers are wrapped in backticks — the same
     * quoting upstream Canary's queries use (e.g.
     * `SELECT \`key\`,\`value\` FROM \`player_storage\``,
     * src/io/player_storage_repository_db.cpp:27). An embedded quote character
     * is doubled, as both dialects require.
     */
    fun quoteIdentifier(name: String): String = when (this) {
        H2, POSTGRESQL -> "\"${name.replace("\"", "\"\"")}\""
        MARIADB -> "`${name.replace("`", "``")}`"
    }

    companion object {
        /**
         * Maps `DatabaseMetaData.getDatabaseProductName()` onto the dialect;
         * an engine with no verified SQL shape fails fast instead of silently
         * receiving another dialect's statements.
         */
        fun fromProductName(productName: String): SqlDialect = when {
            productName.contains("H2", ignoreCase = true) -> H2
            productName.contains("PostgreSQL", ignoreCase = true) -> POSTGRESQL
            productName.contains("MariaDB", ignoreCase = true) -> MARIADB
            productName.contains("MySQL", ignoreCase = true) -> MARIADB
            else -> throw IllegalArgumentException(
                "Unsupported database product '$productName': runtime SQL is only verified for H2, PostgreSQL, MariaDB, and MySQL.",
            )
        }

        /** Detects the dialect of `connection`'s live database. */
        fun from(connection: Connection): SqlDialect =
            fromProductName(connection.metaData.databaseProductName)
    }
}
