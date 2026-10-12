package org.opentibia.canary.database

import javax.sql.DataSource

/**
 * Persisted weapon-proficiency entry, mirroring the `WeaponProficiencyData`
 * fields Canary stores per weapon id (`experience`, `mastered`).
 */
data class WeaponProficiencyState(
    val experience: Int = 0,
    val mastered: Boolean = false,
)

/**
 * Self-contained DTO for a player's advanced-feature state.
 *
 * The field layout follows the values Canary persists for these systems (see
 * `V3__advanced_feature_state.sql`) but stays dependency-free so the database
 * package does not depend on any feature package.
 */
data class PlayerFeatureState(
    val playerId: Long,
    val wheelPoints: Map<Int, Int> = emptyMap(),
    val offlineTrainingTime: Int = 43200,
    val offlineTrainingSkill: Int = -1,
    val dailyReward: Int = 1,
    val forgeDusts: Long = 0,
    val forgeDustLevel: Long = 100,
    val bestiaryKillCounts: Map<Int, Int> = emptyMap(),
    val charmPoints: Int = 0,
    val minorCharmEchoes: Int = 0,
    val maxCharmPoints: Int = 0,
    val maxMinorCharmEchoes: Int = 0,
    val charmExpansion: Boolean = false,
    val usedRunesBit: Int = 0,
    val unlockedRunesBit: Int = 0,
    val mounts: Set<Int> = emptySet(),
    val currentMount: Int = 0,
    val randomizeMount: Int = 0,
    val familiars: Set<Int> = emptySet(),
    val familiarLookType: Int = 0,
    val weaponProficiencies: Map<Int, WeaponProficiencyState> = emptyMap(),
)

interface FeatureStateRepository {
    fun load(playerId: Long): PlayerFeatureState?
    fun save(state: PlayerFeatureState)
}

class JdbcFeatureStateRepository(private val dataSource: DataSource) : FeatureStateRepository {

    override fun save(state: PlayerFeatureState) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                upsertBaseState(conn, state)
                replaceWheelSlots(conn, state)
                replaceBestiaryKillCounts(conn, state)
                replaceMounts(conn, state)
                replaceFamiliars(conn, state)
                replaceWeaponProficiencies(conn, state)
                conn.commit()
            } catch (ex: Exception) {
                conn.rollback()
                throw ex
            }
        }
    }

    override fun load(playerId: Long): PlayerFeatureState? {
        dataSource.connection.use { conn ->
            val hasBase = conn.prepareStatement(
                "SELECT charm_points FROM player_feature_state WHERE player_id = ?",
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.executeQuery().use { rs -> rs.next() }
            }
            if (!hasBase) {
                return null
            }
            return PlayerFeatureState(
                playerId = playerId,
                wheelPoints = loadWheelPoints(conn, playerId),
                offlineTrainingTime = readBaseInt(conn, playerId, "offlinetraining_time"),
                offlineTrainingSkill = readBaseInt(conn, playerId, "offlinetraining_skill"),
                dailyReward = readBaseInt(conn, playerId, "daily_reward"),
                forgeDusts = readBaseLong(conn, playerId, "forge_dusts"),
                forgeDustLevel = readBaseLong(conn, playerId, "forge_dust_level"),
                bestiaryKillCounts = loadBestiaryKillCounts(conn, playerId),
                charmPoints = readBaseInt(conn, playerId, "charm_points"),
                minorCharmEchoes = readBaseInt(conn, playerId, "minor_charm_echoes"),
                maxCharmPoints = readBaseInt(conn, playerId, "max_charm_points"),
                maxMinorCharmEchoes = readBaseInt(conn, playerId, "max_minor_charm_echoes"),
                charmExpansion = readBaseBoolean(conn, playerId, "charm_expansion"),
                usedRunesBit = readBaseInt(conn, playerId, "used_runes_bit"),
                unlockedRunesBit = readBaseInt(conn, playerId, "unlocked_runes_bit"),
                mounts = loadMounts(conn, playerId),
                currentMount = readBaseInt(conn, playerId, "current_mount"),
                randomizeMount = readBaseInt(conn, playerId, "randomize_mount"),
                familiars = loadFamiliars(conn, playerId),
                familiarLookType = readBaseInt(conn, playerId, "familiar_look_type"),
                weaponProficiencies = loadWeaponProficiencies(conn, playerId),
            )
        }
    }

    private fun upsertBaseState(conn: java.sql.Connection, state: PlayerFeatureState) {
        val sql = """
            MERGE INTO player_feature_state (
                player_id, offlinetraining_time, offlinetraining_skill, daily_reward,
                forge_dusts, forge_dust_level, charm_points, minor_charm_echoes,
                max_charm_points, max_minor_charm_echoes, charm_expansion,
                used_runes_bit, unlocked_runes_bit, current_mount, randomize_mount,
                familiar_look_type
            ) KEY (player_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, state.playerId)
            stmt.setInt(2, state.offlineTrainingTime)
            stmt.setInt(3, state.offlineTrainingSkill)
            stmt.setInt(4, state.dailyReward)
            stmt.setLong(5, state.forgeDusts)
            stmt.setLong(6, state.forgeDustLevel)
            stmt.setInt(7, state.charmPoints)
            stmt.setInt(8, state.minorCharmEchoes)
            stmt.setInt(9, state.maxCharmPoints)
            stmt.setInt(10, state.maxMinorCharmEchoes)
            stmt.setBoolean(11, state.charmExpansion)
            stmt.setInt(12, state.usedRunesBit)
            stmt.setInt(13, state.unlockedRunesBit)
            stmt.setInt(14, state.currentMount)
            stmt.setInt(15, state.randomizeMount)
            stmt.setInt(16, state.familiarLookType)
            stmt.executeUpdate()
        }
    }

    private fun replaceWheelSlots(conn: java.sql.Connection, state: PlayerFeatureState) {
        deleteChildren(conn, "player_wheel_slots", state.playerId)
        val sql = "INSERT INTO player_wheel_slots (player_id, slot, points) VALUES (?, ?, ?)"
        conn.prepareStatement(sql).use { stmt ->
            state.wheelPoints.entries.sortedBy { it.key }.forEach { (slot, points) ->
                stmt.setLong(1, state.playerId)
                stmt.setInt(2, slot)
                stmt.setInt(3, points)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun replaceBestiaryKillCounts(conn: java.sql.Connection, state: PlayerFeatureState) {
        deleteChildren(conn, "player_bestiary_kill_counts", state.playerId)
        val sql = "INSERT INTO player_bestiary_kill_counts (player_id, raceid, kill_count) VALUES (?, ?, ?)"
        conn.prepareStatement(sql).use { stmt ->
            state.bestiaryKillCounts.entries.sortedBy { it.key }.forEach { (raceid, count) ->
                stmt.setLong(1, state.playerId)
                stmt.setInt(2, raceid)
                stmt.setInt(3, count)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun replaceMounts(conn: java.sql.Connection, state: PlayerFeatureState) {
        deleteChildren(conn, "player_mounts", state.playerId)
        val sql = "INSERT INTO player_mounts (player_id, mount_id) VALUES (?, ?)"
        conn.prepareStatement(sql).use { stmt ->
            state.mounts.sorted().forEach { mountId ->
                stmt.setLong(1, state.playerId)
                stmt.setInt(2, mountId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun replaceFamiliars(conn: java.sql.Connection, state: PlayerFeatureState) {
        deleteChildren(conn, "player_familiars", state.playerId)
        val sql = "INSERT INTO player_familiars (player_id, look_type) VALUES (?, ?)"
        conn.prepareStatement(sql).use { stmt ->
            state.familiars.sorted().forEach { lookType ->
                stmt.setLong(1, state.playerId)
                stmt.setInt(2, lookType)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun replaceWeaponProficiencies(conn: java.sql.Connection, state: PlayerFeatureState) {
        deleteChildren(conn, "player_weapon_proficiency", state.playerId)
        val sql = """
            INSERT INTO player_weapon_proficiency (player_id, weapon_id, experience, mastered)
            VALUES (?, ?, ?, ?)
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            state.weaponProficiencies.entries.sortedBy { it.key }.forEach { (weaponId, proficiency) ->
                stmt.setLong(1, state.playerId)
                stmt.setInt(2, weaponId)
                stmt.setInt(3, proficiency.experience)
                stmt.setBoolean(4, proficiency.mastered)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun deleteChildren(conn: java.sql.Connection, table: String, playerId: Long) {
        conn.prepareStatement("DELETE FROM $table WHERE player_id = ?").use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeUpdate()
        }
    }

    private fun loadWheelPoints(conn: java.sql.Connection, playerId: Long): Map<Int, Int> {
        val result = linkedMapOf<Int, Int>()
        conn.prepareStatement("SELECT slot, points FROM player_wheel_slots WHERE player_id = ?").use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    result[rs.getInt("slot")] = rs.getInt("points")
                }
            }
        }
        return result
    }

    private fun loadBestiaryKillCounts(conn: java.sql.Connection, playerId: Long): Map<Int, Int> {
        val result = linkedMapOf<Int, Int>()
        conn.prepareStatement(
            "SELECT raceid, kill_count FROM player_bestiary_kill_counts WHERE player_id = ?",
        ).use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    result[rs.getInt("raceid")] = rs.getInt("kill_count")
                }
            }
        }
        return result
    }

    private fun loadMounts(conn: java.sql.Connection, playerId: Long): Set<Int> {
        val result = linkedSetOf<Int>()
        conn.prepareStatement("SELECT mount_id FROM player_mounts WHERE player_id = ?").use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    result.add(rs.getInt("mount_id"))
                }
            }
        }
        return result
    }

    private fun loadFamiliars(conn: java.sql.Connection, playerId: Long): Set<Int> {
        val result = linkedSetOf<Int>()
        conn.prepareStatement("SELECT look_type FROM player_familiars WHERE player_id = ?").use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    result.add(rs.getInt("look_type"))
                }
            }
        }
        return result
    }

    private fun loadWeaponProficiencies(
        conn: java.sql.Connection,
        playerId: Long,
    ): Map<Int, WeaponProficiencyState> {
        val result = linkedMapOf<Int, WeaponProficiencyState>()
        conn.prepareStatement(
            "SELECT weapon_id, experience, mastered FROM player_weapon_proficiency WHERE player_id = ?",
        ).use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    result[rs.getInt("weapon_id")] = WeaponProficiencyState(
                        experience = rs.getInt("experience"),
                        mastered = rs.getBoolean("mastered"),
                    )
                }
            }
        }
        return result
    }

    private fun readBaseInt(conn: java.sql.Connection, playerId: Long, column: String): Int =
        conn.prepareStatement("SELECT $column FROM player_feature_state WHERE player_id = ?").use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getInt(column)
            }
        }

    private fun readBaseLong(conn: java.sql.Connection, playerId: Long, column: String): Long =
        conn.prepareStatement("SELECT $column FROM player_feature_state WHERE player_id = ?").use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getLong(column)
            }
        }

    private fun readBaseBoolean(conn: java.sql.Connection, playerId: Long, column: String): Boolean =
        conn.prepareStatement("SELECT $column FROM player_feature_state WHERE player_id = ?").use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getBoolean(column)
            }
        }
}
