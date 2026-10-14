package org.opentibia.canary.database

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerPronoun_t
import org.opentibia.canary.creatures.players.components.PlayerSex_t
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.map.Position
import org.opentibia.canary.persistence.PlayerStorage
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * One persisted `skills_t` slot of [PlayerRecord], mirroring the
 * `skill_<skill>` / `skill_<skill>_tries` column pair `IOLoginDataLoad::loadPlayerSkill`
 * (src/io/functions/iologindata_load_player.cpp:344-369) reads and
 * `IOLoginDataSave::savePlayerFirst` (iologindata_save_player.cpp:325-372)
 * writes.
 */
data class PlayerSkillRecord(
    val level: Int,
    val tries: Long,
)

/**
 * Persisted player row, mirroring the subset of the `players` row that the
 * Kotlin `Player` can hold after the V10 `pronoun`/`balance`/`blessingsN` and
 * V18 `skill_<skill>[_tries]` columns. Read by `IOLoginData::loadPlayerById` /
 * `loadPlayerByName` (src/io/iologindata.cpp:81-93) into
 * `IOLoginDataLoad::loadPlayerBasicInfo` / `loadPlayerExperience` /
 * `loadPlayerBlessings` / `loadPlayerSkill`
 * (src/io/functions/iologindata_load_player.cpp:121-369).
 *
 * [capacity] is the Canary `Player::capacity` value: the C++ loader multiplies
 * the persisted `cap` column by 100 (iologindata_load_player.cpp:153) and the
 * saver divides it back (iologindata_save_player.cpp:268).
 *
 * [skills] is ordered by the Canary `skills_t` value (`SKILL_FIST` first,
 * `SKILL_MANA_LEECH_AMOUNT` last, creatures_definitions.hpp:466-487); missing
 * rows default to the `players` column defaults (10/0 for the combat skills).
 */
data class PlayerRecord(
    val id: Long,
    val name: String,
    val accountId: Long,
    val groupId: Int,
    val vocation: Int,
    val level: Int,
    val health: Int,
    val healthMax: Int,
    val mana: Int,
    val manaMax: Int,
    val capacity: Double,
    val speed: Int,
    val experience: Long,
    val posX: Int,
    val posY: Int,
    val posZ: Int,
    val sex: Int,
    val pronoun: Int,
    val balance: Long,
    val deletion: Long,
    val blessings: List<Int>,
    val skills: List<PlayerSkillRecord> = emptyList(),
)

/**
 * `getGuidByNameEx` result, mirroring the three out-parameters of
 * `IOLoginData::getGuidByNameEx` (src/io/iologindata.cpp:322-340).
 */
data class GuidNameEx(
    val guid: Long,
    val name: String,
    val specialVip: Boolean,
)

/**
 * Login-side player-row seam used by
 * [org.opentibia.canary.io.iologindata.IOLoginData], mirroring the player
 * queries of `src/io/iologindata.cpp`.
 */
interface PlayerLoginRepository {
    /** `IOLoginData::loadPlayerById` row read (src/io/iologindata.cpp:81-86). */
    fun loadRecordById(id: Long): PlayerRecord?

    /** `IOLoginData::loadPlayerByName` row read (src/io/iologindata.cpp:88-93). */
    fun loadRecordByName(name: String): PlayerRecord?

    /** `IOLoginData::getNameByGuid` (src/io/iologindata.cpp:300-308); `""` when missing. */
    fun getNameByGuid(guid: Long): String

    /** `IOLoginData::getGuidByName` (src/io/iologindata.cpp:310-320); `0` when missing. */
    fun getGuidByName(name: String): Long

    /** `IOLoginData::getGuidByNameEx` (src/io/iologindata.cpp:322-340); `null` when missing. */
    fun getGuidByNameEx(name: String): GuidNameEx?

    /** `IOLoginData::formatPlayerName` (src/io/iologindata.cpp:342-355); `null` when missing. */
    fun formatPlayerName(name: String): String?

    /** `IOLoginData::increaseBankBalance` (src/io/iologindata.cpp:357-361). */
    fun increaseBankBalance(guid: Long, amount: Long): Boolean

    /** The `players` column set written by `IOLoginDataSave::savePlayerFirst` that the port models (iologindata_save_player.cpp:191-372). */
    fun savePlayerRecord(player: Player): Boolean
}

/**
 * JDBC player persistence.
 *
 * [PlayerStorage.save]/[PlayerStorage.loadByName] keep their game-path
 * contracts and now read/write the full V10 `players` row set; the
 * [PlayerLoginRepository] methods transcribe the `IOLoginData` player queries.
 * `getGuidByNameEx` resolves the special-VIP group flag through
 * [groupHasSpecialVip], the Kotlin seam for `Groups::getFlagNumber(SpecialVIP)`
 * (iologindata.cpp:334-338), because the port's `Group` has no flag table yet.
 *
 * All name lookups use the exact `name = ?` predicate of the C++
 * `fmt::format` queries (no `LOWER`), matching the MySQL case-insensitive
 * collation only on databases that default to it.
 */
class JdbcPlayerRepository(
    private val dataSource: DataSource,
    private val groupHasSpecialVip: (Int) -> Boolean = { false },
) : PlayerStorage, PlayerLoginRepository {

    override fun save(player: Player) {
        savePlayerRecord(player)
    }

    override fun savePlayerRecord(player: Player): Boolean {
        val skillColumns = SKILL_COLUMNS.joinToString("") { ", $it = ?, ${it}_tries = ?" }
        val sql = """
            UPDATE players SET
                name = ?, level = ?, vocation = ?, health = ?, healthmax = ?,
                mana = ?, manamax = ?, cap = ?, speed = ?, experience = ?,
                posx = ?, posy = ?, posz = ?, sex = ?, pronoun = ?,
                blessings1 = ?, blessings2 = ?, blessings3 = ?, blessings4 = ?,
                blessings5 = ?, blessings6 = ?, blessings7 = ?, blessings8 = ?
                $skillColumns
            WHERE id = ?
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, player.name)
                stmt.setInt(2, player.level)
                stmt.setInt(3, player.getVocationId())
                stmt.setInt(4, player.health)
                stmt.setInt(5, player.maxHealth)
                stmt.setInt(6, player.mana)
                stmt.setInt(7, player.maxMana)
                stmt.setDouble(8, player.capacity / 100.0)
                stmt.setInt(9, player.speed)
                stmt.setLong(10, player.experience)
                stmt.setInt(11, player.position.x)
                stmt.setInt(12, player.position.y)
                stmt.setInt(13, player.position.z)
                stmt.setInt(14, player.getSex().value)
                stmt.setInt(15, player.getPronoun().value)
                for (index in 1..8) {
                    stmt.setInt(15 + index, player.getBlessingCount(index))
                }

                // `IOLoginDataSave::savePlayerFirst` writes every `skills_t`
                // slot as (level, tries) (iologindata_save_player.cpp:325-372).
                var parameter = 24
                for (skill in skills_t.FIRST..skills_t.LAST) {
                    val slot = skills_t.fromValue(skill) ?: continue
                    stmt.setInt(parameter++, player.getBaseSkill(slot))
                    stmt.setLong(parameter++, player.getSkillTries(slot))
                }
                stmt.setLong(parameter, player.id)
                return stmt.executeUpdate() > 0
            }
        }
    }

    override fun loadByName(name: String): Player? {
        val record = loadRecordByName(name) ?: return null
        if (record.deletion != 0L) {
            return null
        }
        return toPlayer(record)
    }

    override fun loadRecordById(id: Long): PlayerRecord? =
        queryRecord("$RECORD_COLUMNS WHERE id = ?") { it.setLong(1, id) }

    override fun loadRecordByName(name: String): PlayerRecord? =
        queryRecord("$RECORD_COLUMNS WHERE name = ?") { it.setString(1, name) }

    /** `IOLoginData::getNameByGuid` (src/io/iologindata.cpp:300-308). */
    override fun getNameByGuid(guid: Long): String {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT name FROM players WHERE id = ?").use { stmt ->
                stmt.setLong(1, guid)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) rs.getString("name") else ""
                }
            }
        }
    }

    /** `IOLoginData::getGuidByName` (src/io/iologindata.cpp:310-320). */
    override fun getGuidByName(name: String): Long {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT id FROM players WHERE name = ?").use { stmt ->
                stmt.setString(1, name)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) rs.getLong("id") else 0L
                }
            }
        }
    }

    /** `IOLoginData::getGuidByNameEx` (src/io/iologindata.cpp:322-340). */
    override fun getGuidByNameEx(name: String): GuidNameEx? {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT name, id, group_id FROM players WHERE name = ?").use { stmt ->
                stmt.setString(1, name)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) {
                        return null
                    }
                    return GuidNameEx(
                        guid = rs.getLong("id"),
                        name = rs.getString("name"),
                        specialVip = groupHasSpecialVip(rs.getInt("group_id")),
                    )
                }
            }
        }
    }

    /** `IOLoginData::formatPlayerName` (src/io/iologindata.cpp:342-355). */
    override fun formatPlayerName(name: String): String? {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT name FROM players WHERE name = ?").use { stmt ->
                stmt.setString(1, name)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) rs.getString("name") else null
                }
            }
        }
    }

    /** `IOLoginData::increaseBankBalance` (src/io/iologindata.cpp:357-361). */
    override fun increaseBankBalance(guid: Long, amount: Long): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE players SET balance = balance + ? WHERE id = ?").use { stmt ->
                stmt.setLong(1, amount)
                stmt.setLong(2, guid)
                return stmt.executeUpdate() > 0
            }
        }
    }

    private fun queryRecord(sql: String, bind: (java.sql.PreparedStatement) -> Unit): PlayerRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                bind(stmt)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) {
                        return null
                    }
                    return readRecord(rs)
                }
            }
        }
    }

    private fun readRecord(rs: ResultSet): PlayerRecord = PlayerRecord(
        id = rs.getLong("id"),
        name = rs.getString("name"),
        accountId = rs.getLong("account_id"),
        groupId = rs.getInt("group_id"),
        vocation = rs.getInt("vocation"),
        level = rs.getInt("level"),
        health = rs.getInt("health"),
        healthMax = rs.getInt("healthmax"),
        mana = rs.getInt("mana"),
        manaMax = rs.getInt("manamax"),
        capacity = rs.getDouble("cap") * 100.0,
        speed = rs.getInt("speed"),
        experience = rs.getLong("experience"),
        posX = rs.getInt("posx"),
        posY = rs.getInt("posy"),
        posZ = rs.getInt("posz"),
        sex = rs.getInt("sex"),
        pronoun = rs.getInt("pronoun"),
        balance = rs.getLong("balance"),
        deletion = rs.getLong("deletion"),
        blessings = (1..8).map { rs.getInt("blessings$it") },
        skills = SKILL_COLUMNS.map { PlayerSkillRecord(rs.getInt(it), rs.getLong("${it}_tries")) },
    )

    private fun toPlayer(record: PlayerRecord): Player {
        val player = Player(
            id = record.id,
            name = record.name,
            maxHealth = record.healthMax,
            health = record.health,
            maxMana = record.manaMax,
            mana = record.mana,
            capacity = record.capacity,
            speed = record.speed,
            position = Position(record.posX, record.posY, record.posZ),
            experience = record.experience,
            level = record.level,
        )
        player.vocationId = record.vocation
        player.setSex(PlayerSex_t.entries.firstOrNull { it.value == record.sex } ?: PlayerSex_t.FEMALE)
        player.setPronoun(
            PlayerPronoun_t.entries.firstOrNull { it.value == record.pronoun } ?: PlayerPronoun_t.PLAYERPRONOUN_UNSET,
        )
        for (index in 1..8) {
            val count = record.blessings[index - 1]
            if (count != 0) {
                player.addBlessing(index, count)
            }
        }
        return player
    }

    private companion object {
        /**
         * The persisted `skills_t` column bases in Canary's enum order
         * (creatures_definitions.hpp:466-487): `SKILL_FIST .. SKILL_MANA_LEECH_AMOUNT`.
         */
        val SKILL_COLUMNS: List<String> = listOf(
            "skill_fist",
            "skill_club",
            "skill_sword",
            "skill_axe",
            "skill_dist",
            "skill_shielding",
            "skill_fishing",
            "skill_critical_hit_chance",
            "skill_critical_hit_damage",
            "skill_life_leech_chance",
            "skill_life_leech_amount",
            "skill_mana_leech_chance",
            "skill_mana_leech_amount",
        )

        const val RECORD_COLUMNS = """
            SELECT id, name, account_id, group_id, vocation, level, health, healthmax,
                   mana, manamax, cap, speed, experience, posx, posy, posz, sex, pronoun,
                   balance, deletion, blessings1, blessings2, blessings3, blessings4,
                   blessings5, blessings6, blessings7, blessings8,
                   skill_fist, skill_fist_tries, skill_club, skill_club_tries,
                   skill_sword, skill_sword_tries, skill_axe, skill_axe_tries,
                   skill_dist, skill_dist_tries, skill_shielding, skill_shielding_tries,
                   skill_fishing, skill_fishing_tries,
                   skill_critical_hit_chance, skill_critical_hit_chance_tries,
                   skill_critical_hit_damage, skill_critical_hit_damage_tries,
                   skill_life_leech_chance, skill_life_leech_chance_tries,
                   skill_life_leech_amount, skill_life_leech_amount_tries,
                   skill_mana_leech_chance, skill_mana_leech_chance_tries,
                   skill_mana_leech_amount, skill_mana_leech_amount_tries
            FROM players
        """
    }
}
