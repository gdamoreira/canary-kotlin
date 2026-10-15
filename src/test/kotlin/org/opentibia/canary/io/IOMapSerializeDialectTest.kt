package org.opentibia.canary.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.database.SqlDialect

/**
 * SQL-shape unit tests for the upserts of `IOMapSerialize::SaveHouseInfoGuard`
 * (src/io/iomapserialize.cpp:403-468): the H2 and PostgreSQL statements pin the
 * previously shipped text byte-for-byte, and MariaDB gets upstream Canary's
 * `INSERT ... ON DUPLICATE KEY UPDATE col = VALUES(col)` form
 * (`DBInsert::execute`, src/database/database.cpp:453-481).
 */
class IOMapSerializeDialectTest {

    private val houseColumns = listOf(
        "id", "owner", "paid", "warnings", "name", "town_id", "rent", "size", "beds", "bidder",
        "bidder_name", "highest_bid", "internal_bid", "bid_end_date", "state", "transfer_status",
    )

    private val houseListColumns = listOf("house_id", "listid", "list", "version")

    @Test
    fun `H2 houses upsert is the MERGE KEY shape`() {
        assertThat(IOMapSerialize.houseUpsertSql(SqlDialect.H2)).isEqualTo(
            """
            MERGE INTO houses (id, owner, paid, warnings, name, town_id, rent, size, beds, bidder,
                bidder_name, highest_bid, internal_bid, bid_end_date, state, transfer_status)
            KEY (id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        )
    }

    @Test
    fun `PostgreSQL houses upsert is the ON CONFLICT DO UPDATE shape`() {
        assertThat(IOMapSerialize.houseUpsertSql(SqlDialect.POSTGRESQL)).isEqualTo(
            """
            INSERT INTO houses (id, owner, paid, warnings, name, town_id, rent, size, beds, bidder,
                bidder_name, highest_bid, internal_bid, bid_end_date, state, transfer_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                owner = EXCLUDED.owner,
                paid = EXCLUDED.paid,
                warnings = EXCLUDED.warnings,
                name = EXCLUDED.name,
                town_id = EXCLUDED.town_id,
                rent = EXCLUDED.rent,
                size = EXCLUDED.size,
                beds = EXCLUDED.beds,
                bidder = EXCLUDED.bidder,
                bidder_name = EXCLUDED.bidder_name,
                highest_bid = EXCLUDED.highest_bid,
                internal_bid = EXCLUDED.internal_bid,
                bid_end_date = EXCLUDED.bid_end_date,
                state = EXCLUDED.state,
                transfer_status = EXCLUDED.transfer_status
            """.trimIndent(),
        )
    }

    @Test
    fun `MariaDB houses upsert is the ON DUPLICATE KEY UPDATE VALUES shape`() {
        assertThat(IOMapSerialize.houseUpsertSql(SqlDialect.MARIADB)).isEqualTo(
            """
            INSERT INTO houses (id, owner, paid, warnings, name, town_id, rent, size, beds, bidder,
                bidder_name, highest_bid, internal_bid, bid_end_date, state, transfer_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                owner = VALUES(owner),
                paid = VALUES(paid),
                warnings = VALUES(warnings),
                name = VALUES(name),
                town_id = VALUES(town_id),
                rent = VALUES(rent),
                size = VALUES(size),
                beds = VALUES(beds),
                bidder = VALUES(bidder),
                bidder_name = VALUES(bidder_name),
                highest_bid = VALUES(highest_bid),
                internal_bid = VALUES(internal_bid),
                bid_end_date = VALUES(bid_end_date),
                state = VALUES(state),
                transfer_status = VALUES(transfer_status)
            """.trimIndent(),
        )
    }

    @Test
    fun `every dialect upserts the same houses column set`() {
        for (dialect in SqlDialect.entries) {
            val sql = IOMapSerialize.houseUpsertSql(dialect)
            assertThat(columnsOf(sql, "houses")).describedAs("%s houses columns", dialect).isEqualTo(houseColumns)
        }
    }

    @Test
    fun `every dialect upserts the same house lists column set`() {
        for (dialect in SqlDialect.entries) {
            val sql = IOMapSerialize.houseListsUpsertSql(dialect)
            assertThat(columnsOf(sql, "house_lists")).describedAs("%s house_lists columns", dialect)
                .isEqualTo(houseListColumns)
        }
    }

    @Test
    fun `H2 house lists upsert is the MERGE KEY shape`() {
        assertThat(IOMapSerialize.houseListsUpsertSql(SqlDialect.H2)).isEqualTo(
            """
            MERGE INTO house_lists (house_id, listid, list, version)
            KEY (house_id, listid)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
        )
    }

    @Test
    fun `PostgreSQL house lists upsert is the ON CONFLICT DO UPDATE shape`() {
        assertThat(IOMapSerialize.houseListsUpsertSql(SqlDialect.POSTGRESQL)).isEqualTo(
            """
            INSERT INTO house_lists (house_id, listid, list, version)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (house_id, listid) DO UPDATE SET
                list = EXCLUDED.list,
                version = EXCLUDED.version
            """.trimIndent(),
        )
    }

    @Test
    fun `MariaDB house lists upsert is the ON DUPLICATE KEY UPDATE VALUES shape`() {
        assertThat(IOMapSerialize.houseListsUpsertSql(SqlDialect.MARIADB)).isEqualTo(
            """
            INSERT INTO house_lists (house_id, listid, list, version)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                list = VALUES(list),
                version = VALUES(version)
            """.trimIndent(),
        )
    }

    private fun columnsOf(sql: String, table: String): List<String> {
        val start = sql.indexOf("$table (") + "$table (".length
        val end = sql.indexOf(')', start)
        return sql.substring(start, end).split(',').map { it.trim() }
    }
}
