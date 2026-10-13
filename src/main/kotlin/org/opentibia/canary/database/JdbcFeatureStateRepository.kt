package org.opentibia.canary.database

import javax.sql.DataSource

/**
 * Persisted weapon-proficiency perk, mirroring one `ProficiencyPerk` entry of
 * `WeaponProficiencyData` (`src/enums/weapon_proficiency.hpp:93-109` as
 * serialized by `WeaponProficiency::serializePerk`).
 */
data class WeaponProficiencyPerkState(
    val level: Int = 0,
    val index: Int = 0,
    val value: Double = 0.0,
    val spellId: Int = 0,
    val range: Int = 0,
    val bestiaryId: Int = 0,
    val bestiaryName: String = "",
    val augmentType: Int = 0,
    val skillId: Int = 0,
    val element: Int = 0,
    val type: Int = 0,
)

/**
 * Persisted weapon-proficiency entry, mirroring the `WeaponProficiencyData`
 * fields Canary stores per weapon id (`experience`, `mastered`, `perks`).
 */
data class WeaponProficiencyState(
    val experience: Int = 0,
    val mastered: Boolean = false,
    /** Ordered `WeaponProficiencyData::perks`; order is preserved on reload. */
    val perks: List<WeaponProficiencyPerkState> = emptyList(),
)

/**
 * Persisted bestiary charm assignment for one charm rune, mirroring the
 * `CharmInfo` stored in `Player::charmsArray` (`raceId` + `tier`).
 */
data class CharmAssignmentState(
    val raceId: Int = 0,
    val tier: Int = 0,
)

/**
 * Persisted wheel gem record, mirroring the `PlayerWheelGem` fields Canary
 * stores under the `revealed` KV scope (`player_wheel.hpp:44-69`). The gem's
 * position in [PlayerFeatureState.wheelGems] is its list order.
 */
data class WheelGemState(
    val locked: Boolean = false,
    val affinity: Int = 0,
    val quality: Int = 0,
    val basicModifier1: Int = 0,
    val basicModifier2: Int? = null,
    val supremeModifier: Int? = null,
)

/**
 * Persisted wheel gem modifier grade for one fragment type/position, mirroring
 * `PlayerWheel::m_basicGrades` / `m_supremeGrades` (persisted by Canary via the
 * `wheel-of-destiny/gems` KV scope, only for grades above zero).
 */
data class WheelGemGradeState(
    val fragmentType: Int,
    val position: Int,
    val grade: Int,
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
    /** `Player::charmsArray` charm rune id -> race/tier assignment. */
    val charmAssignments: Map<Int, CharmAssignmentState> = emptyMap(),
    /** `PlayerWheel::m_revealedGems` in load order. */
    val wheelGems: List<WheelGemState> = emptyList(),
    /** `PlayerWheel::m_activeGems` affinity -> index into [wheelGems]. */
    val activeWheelGems: Map<Int, Int> = emptyMap(),
    /** `PlayerWheel::m_basicGrades` / `m_supremeGrades`, non-zero entries only. */
    val wheelGemGrades: List<WheelGemGradeState> = emptyList(),
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
                replaceBestiaryCharmAssignments(conn, state)
                replaceMounts(conn, state)
                replaceFamiliars(conn, state)
                replaceWeaponProficiencies(conn, state)
                replaceWeaponProficiencyPerks(conn, state)
                replaceWheelGems(conn, state)
                replaceWheelActiveGems(conn, state)
                replaceWheelGemGrades(conn, state)
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
                charmAssignments = loadCharmAssignments(conn, playerId),
                wheelGems = loadWheelGems(conn, playerId),
                activeWheelGems = loadActiveWheelGems(conn, playerId),
                wheelGemGrades = loadWheelGemGrades(conn, playerId),
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

    private fun replaceWeaponProficiencyPerks(conn: java.sql.Connection, state: PlayerFeatureState) {
        deleteChildren(conn, "player_weapon_proficiency_perks", state.playerId)
        val sql = """
            INSERT INTO player_weapon_proficiency_perks (
                player_id, weapon_id, perk_order, perk_level, perk_index, perk_type, perk_value,
                spell_id, perk_range, bestiary_id, bestiary_name, augment_type, skill_id, element
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            state.weaponProficiencies.entries.sortedBy { it.key }.forEach { (weaponId, proficiency) ->
                proficiency.perks.forEachIndexed { order, perk ->
                    stmt.setLong(1, state.playerId)
                    stmt.setInt(2, weaponId)
                    stmt.setInt(3, order)
                    stmt.setInt(4, perk.level)
                    stmt.setInt(5, perk.index)
                    stmt.setInt(6, perk.type)
                    stmt.setDouble(7, perk.value)
                    stmt.setInt(8, perk.spellId)
                    stmt.setInt(9, perk.range)
                    stmt.setInt(10, perk.bestiaryId)
                    stmt.setString(11, perk.bestiaryName)
                    stmt.setInt(12, perk.augmentType)
                    stmt.setInt(13, perk.skillId)
                    stmt.setInt(14, perk.element)
                    stmt.addBatch()
                }
            }
            stmt.executeBatch()
        }
    }

    private fun replaceBestiaryCharmAssignments(conn: java.sql.Connection, state: PlayerFeatureState) {
        deleteChildren(conn, "player_bestiary_charms", state.playerId)
        val sql = "INSERT INTO player_bestiary_charms (player_id, charm_id, race_id, tier) VALUES (?, ?, ?, ?)"
        conn.prepareStatement(sql).use { stmt ->
            state.charmAssignments.entries.sortedBy { it.key }.forEach { (charmId, assignment) ->
                stmt.setLong(1, state.playerId)
                stmt.setInt(2, charmId)
                stmt.setInt(3, assignment.raceId)
                stmt.setInt(4, assignment.tier)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun replaceWheelGems(conn: java.sql.Connection, state: PlayerFeatureState) {
        deleteChildren(conn, "player_wheel_gems", state.playerId)
        val sql = """
            INSERT INTO player_wheel_gems (
                player_id, gem_order, locked, affinity, quality,
                basic_modifier1, basic_modifier2, supreme_modifier
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            state.wheelGems.forEachIndexed { order, gem ->
                stmt.setLong(1, state.playerId)
                stmt.setInt(2, order)
                stmt.setBoolean(3, gem.locked)
                stmt.setInt(4, gem.affinity)
                stmt.setInt(5, gem.quality)
                stmt.setInt(6, gem.basicModifier1)
                setNullableInt(stmt, 7, gem.basicModifier2)
                setNullableInt(stmt, 8, gem.supremeModifier)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun replaceWheelActiveGems(conn: java.sql.Connection, state: PlayerFeatureState) {
        deleteChildren(conn, "player_wheel_active_gems", state.playerId)
        val sql = "INSERT INTO player_wheel_active_gems (player_id, affinity, gem_order) VALUES (?, ?, ?)"
        conn.prepareStatement(sql).use { stmt ->
            state.activeWheelGems.entries.sortedBy { it.key }.forEach { (affinity, order) ->
                stmt.setLong(1, state.playerId)
                stmt.setInt(2, affinity)
                stmt.setInt(3, order)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun replaceWheelGemGrades(conn: java.sql.Connection, state: PlayerFeatureState) {
        deleteChildren(conn, "player_wheel_gem_grades", state.playerId)
        val sql = """
            INSERT INTO player_wheel_gem_grades (player_id, fragment_type, gem_position, grade)
            VALUES (?, ?, ?, ?)
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            state.wheelGemGrades.forEach { entry ->
                stmt.setLong(1, state.playerId)
                stmt.setInt(2, entry.fragmentType)
                stmt.setInt(3, entry.position)
                stmt.setInt(4, entry.grade)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun setNullableInt(stmt: java.sql.PreparedStatement, index: Int, value: Int?) {
        if (value == null) {
            stmt.setNull(index, java.sql.Types.INTEGER)
        } else {
            stmt.setInt(index, value)
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
        val perksByWeapon = loadWeaponProficiencyPerks(conn, playerId)
        val result = linkedMapOf<Int, WeaponProficiencyState>()
        conn.prepareStatement(
            "SELECT weapon_id, experience, mastered FROM player_weapon_proficiency WHERE player_id = ?",
        ).use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val weaponId = rs.getInt("weapon_id")
                    result[weaponId] = WeaponProficiencyState(
                        experience = rs.getInt("experience"),
                        mastered = rs.getBoolean("mastered"),
                        perks = perksByWeapon[weaponId] ?: emptyList(),
                    )
                }
            }
        }
        return result
    }

    private fun loadWeaponProficiencyPerks(
        conn: java.sql.Connection,
        playerId: Long,
    ): Map<Int, List<WeaponProficiencyPerkState>> {
        val result = linkedMapOf<Int, MutableList<WeaponProficiencyPerkState>>()
        conn.prepareStatement(
            """
            SELECT weapon_id, perk_level, perk_index, perk_type, perk_value, spell_id,
                   perk_range, bestiary_id, bestiary_name, augment_type, skill_id, element
            FROM player_weapon_proficiency_perks
            WHERE player_id = ?
            ORDER BY weapon_id, perk_order
            """.trimIndent(),
        ).use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    result.getOrPut(rs.getInt("weapon_id")) { mutableListOf() }.add(
                        WeaponProficiencyPerkState(
                            level = rs.getInt("perk_level"),
                            index = rs.getInt("perk_index"),
                            type = rs.getInt("perk_type"),
                            value = rs.getDouble("perk_value"),
                            spellId = rs.getInt("spell_id"),
                            range = rs.getInt("perk_range"),
                            bestiaryId = rs.getInt("bestiary_id"),
                            bestiaryName = rs.getString("bestiary_name"),
                            augmentType = rs.getInt("augment_type"),
                            skillId = rs.getInt("skill_id"),
                            element = rs.getInt("element"),
                        ),
                    )
                }
            }
        }
        return result
    }

    private fun loadCharmAssignments(
        conn: java.sql.Connection,
        playerId: Long,
    ): Map<Int, CharmAssignmentState> {
        val result = linkedMapOf<Int, CharmAssignmentState>()
        conn.prepareStatement(
            "SELECT charm_id, race_id, tier FROM player_bestiary_charms WHERE player_id = ? ORDER BY charm_id",
        ).use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    result[rs.getInt("charm_id")] = CharmAssignmentState(
                        raceId = rs.getInt("race_id"),
                        tier = rs.getInt("tier"),
                    )
                }
            }
        }
        return result
    }

    private fun loadWheelGems(conn: java.sql.Connection, playerId: Long): List<WheelGemState> {
        val result = mutableListOf<WheelGemState>()
        conn.prepareStatement(
            """
            SELECT locked, affinity, quality, basic_modifier1, basic_modifier2, supreme_modifier
            FROM player_wheel_gems
            WHERE player_id = ?
            ORDER BY gem_order
            """.trimIndent(),
        ).use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    result.add(
                        WheelGemState(
                            locked = rs.getBoolean("locked"),
                            affinity = rs.getInt("affinity"),
                            quality = rs.getInt("quality"),
                            basicModifier1 = rs.getInt("basic_modifier1"),
                            basicModifier2 = rs.nullableInt("basic_modifier2"),
                            supremeModifier = rs.nullableInt("supreme_modifier"),
                        ),
                    )
                }
            }
        }
        return result
    }

    private fun loadActiveWheelGems(conn: java.sql.Connection, playerId: Long): Map<Int, Int> {
        val result = linkedMapOf<Int, Int>()
        conn.prepareStatement(
            "SELECT affinity, gem_order FROM player_wheel_active_gems WHERE player_id = ? ORDER BY affinity",
        ).use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    result[rs.getInt("affinity")] = rs.getInt("gem_order")
                }
            }
        }
        return result
    }

    private fun loadWheelGemGrades(conn: java.sql.Connection, playerId: Long): List<WheelGemGradeState> {
        val result = mutableListOf<WheelGemGradeState>()
        conn.prepareStatement(
            """
            SELECT fragment_type, gem_position, grade
            FROM player_wheel_gem_grades
            WHERE player_id = ?
            ORDER BY fragment_type, gem_position
            """.trimIndent(),
        ).use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    result.add(
                        WheelGemGradeState(
                            fragmentType = rs.getInt("fragment_type"),
                            position = rs.getInt("gem_position"),
                            grade = rs.getInt("grade"),
                        ),
                    )
                }
            }
        }
        return result
    }

    private fun java.sql.ResultSet.nullableInt(column: String): Int? {
        val value = getInt(column)
        return if (wasNull()) null else value
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
