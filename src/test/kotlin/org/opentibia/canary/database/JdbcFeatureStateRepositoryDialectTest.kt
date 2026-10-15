package org.opentibia.canary.database

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * SQL-shape unit tests for the `player_feature_state` base-row upsert of
 * [JdbcFeatureStateRepository]: H2 keeps the shipped `MERGE ... KEY` shape,
 * PostgreSQL gets the equivalent `ON CONFLICT ... DO UPDATE`, and MariaDB gets
 * upstream Canary's `INSERT ... ON DUPLICATE KEY UPDATE col = VALUES(col)`
 * (`DBInsert::execute`, src/database/database.cpp:453-481).
 */
class JdbcFeatureStateRepositoryDialectTest {

    private val baseColumns = listOf(
        "player_id", "offlinetraining_time", "offlinetraining_skill", "daily_reward",
        "forge_dusts", "forge_dust_level", "charm_points", "minor_charm_echoes",
        "max_charm_points", "max_minor_charm_echoes", "charm_expansion",
        "used_runes_bit", "unlocked_runes_bit", "current_mount", "randomize_mount",
        "familiar_look_type",
    )

    @Test
    fun `H2 base state upsert is the MERGE KEY shape`() {
        assertThat(JdbcFeatureStateRepository.upsertBaseStateSql(SqlDialect.H2)).isEqualTo(
            """
            MERGE INTO player_feature_state (
                player_id, offlinetraining_time, offlinetraining_skill, daily_reward,
                forge_dusts, forge_dust_level, charm_points, minor_charm_echoes,
                max_charm_points, max_minor_charm_echoes, charm_expansion,
                used_runes_bit, unlocked_runes_bit, current_mount, randomize_mount,
                familiar_look_type
            ) KEY (player_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        )
    }

    @Test
    fun `PostgreSQL base state upsert is the ON CONFLICT DO UPDATE shape`() {
        assertThat(JdbcFeatureStateRepository.upsertBaseStateSql(SqlDialect.POSTGRESQL)).isEqualTo(
            """
            INSERT INTO player_feature_state (
                player_id, offlinetraining_time, offlinetraining_skill, daily_reward,
                forge_dusts, forge_dust_level, charm_points, minor_charm_echoes,
                max_charm_points, max_minor_charm_echoes, charm_expansion,
                used_runes_bit, unlocked_runes_bit, current_mount, randomize_mount,
                familiar_look_type
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (player_id) DO UPDATE SET
                offlinetraining_time = EXCLUDED.offlinetraining_time,
                offlinetraining_skill = EXCLUDED.offlinetraining_skill,
                daily_reward = EXCLUDED.daily_reward,
                forge_dusts = EXCLUDED.forge_dusts,
                forge_dust_level = EXCLUDED.forge_dust_level,
                charm_points = EXCLUDED.charm_points,
                minor_charm_echoes = EXCLUDED.minor_charm_echoes,
                max_charm_points = EXCLUDED.max_charm_points,
                max_minor_charm_echoes = EXCLUDED.max_minor_charm_echoes,
                charm_expansion = EXCLUDED.charm_expansion,
                used_runes_bit = EXCLUDED.used_runes_bit,
                unlocked_runes_bit = EXCLUDED.unlocked_runes_bit,
                current_mount = EXCLUDED.current_mount,
                randomize_mount = EXCLUDED.randomize_mount,
                familiar_look_type = EXCLUDED.familiar_look_type
            """.trimIndent(),
        )
    }

    @Test
    fun `MariaDB base state upsert is the ON DUPLICATE KEY UPDATE VALUES shape`() {
        assertThat(JdbcFeatureStateRepository.upsertBaseStateSql(SqlDialect.MARIADB)).isEqualTo(
            """
            INSERT INTO player_feature_state (
                player_id, offlinetraining_time, offlinetraining_skill, daily_reward,
                forge_dusts, forge_dust_level, charm_points, minor_charm_echoes,
                max_charm_points, max_minor_charm_echoes, charm_expansion,
                used_runes_bit, unlocked_runes_bit, current_mount, randomize_mount,
                familiar_look_type
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                offlinetraining_time = VALUES(offlinetraining_time),
                offlinetraining_skill = VALUES(offlinetraining_skill),
                daily_reward = VALUES(daily_reward),
                forge_dusts = VALUES(forge_dusts),
                forge_dust_level = VALUES(forge_dust_level),
                charm_points = VALUES(charm_points),
                minor_charm_echoes = VALUES(minor_charm_echoes),
                max_charm_points = VALUES(max_charm_points),
                max_minor_charm_echoes = VALUES(max_minor_charm_echoes),
                charm_expansion = VALUES(charm_expansion),
                used_runes_bit = VALUES(used_runes_bit),
                unlocked_runes_bit = VALUES(unlocked_runes_bit),
                current_mount = VALUES(current_mount),
                randomize_mount = VALUES(randomize_mount),
                familiar_look_type = VALUES(familiar_look_type)
            """.trimIndent(),
        )
    }

    @Test
    fun `every dialect upserts the same player_feature_state column set`() {
        for (dialect in SqlDialect.entries) {
            val sql = JdbcFeatureStateRepository.upsertBaseStateSql(dialect)
            assertThat(columnsOf(sql)).describedAs("%s player_feature_state columns", dialect)
                .isEqualTo(baseColumns)
        }
    }

    private fun columnsOf(sql: String): List<String> {
        val start = sql.indexOf("player_feature_state (") + "player_feature_state (".length
        val end = sql.indexOf(')', start)
        return sql.substring(start, end).split(',').map { it.trim() }
    }
}
