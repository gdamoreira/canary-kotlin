package org.opentibia.canary.io

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.creatures.monsters.BosstiaryRarity_t
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.database.SqlDialect
import javax.sql.DataSource
import kotlin.math.sqrt

private val logger = KotlinLogging.logger {}

/**
 * `struct LevelInfo` (src/io/io_bosstiary.hpp:21-24): the kills/points pair of
 * one boss mastery stage.
 */
data class LevelInfo(val kills: Int, val points: Int)

/**
 * The `player_bosstiary` row Canary loads into `Player::setSlotBossId` /
 * `Player::setRemoveBossTime` and the bosstiary tracker
 * (iologindata_load_player.cpp:916-951, iologindata_save_player.cpp:786-835,
 * schema.sql:755-766).
 */
data class PlayerBosstiaryState(
    val bossIdSlotOne: Int = 0,
    val bossIdSlotTwo: Int = 0,
    val removeTimes: Int = 1,
    val tracker: List<Int> = emptyList(),
)

/**
 * Port of Canary's `IOBosstiary` (src/io/io_bosstiary.cpp, io_bosstiary.hpp).
 *
 * Covers the class's persistence and progression surface:
 *  - the daily boosted boss persisted in `boosted_boss`
 *    (`IOBosstiary::loadBoostedBoss`, io_bosstiary.cpp:22-124), including the
 *    player-slot resets,
 *  - the registered boss map and the boosted-name/id accessors
 *    (io_bosstiary.cpp:126-171),
 *  - the `levelInfos` mastery thresholds (io_bosstiary.hpp:44-48),
 *  - `calculateLootBonus` / `calculateBossPoints` / `calculteRemoveBoss`
 *    (io_bosstiary.cpp:217-308),
 *  - `player_bosstiary` slots/removeTimes/tracker and `players.boss_points`
 *    (iologindata_load_player.cpp:175,916-951;
 *    iologindata_save_player.cpp:264,786-835).
 *
 * Global singletons are replaced by constructor injection (AGENTS.md 1.1): the
 * monster registry is a [Monsters] lookup, the daily RNG is [randomIndex]
 * (called with the archfoe count like `uniform_random(0, size - 1)`) and the
 * current day is [todayProvider].
 *
 * PARITY GAPS: `IOBosstiary::addBosstiaryKill` side effects that mutate live
 * `Player`/`Game` state (bestiary kill counters, tracker refresh, entry-changed
 * packet, Podium of Vigour store inbox item) belong to the player/game layer and
 * are not reproduced here; this class exposes the deterministic stage/points
 * math they consume instead.
 */
class IOBosstiary(
    private val dataSource: DataSource,
    private val monsters: Monsters = Monsters(),
    private val randomIndex: (Int) -> Int = { kotlin.random.Random.nextInt(it) },
    private val todayProvider: () -> Int = { java.time.LocalDate.now().dayOfMonth },
) {
    /** `IOBosstiary::levelInfos` (io_bosstiary.hpp:44-48). */
    val levelInfos: Map<BosstiaryRarity_t, List<LevelInfo>> = linkedMapOf(
        BosstiaryRarity_t.RARITY_BANE to listOf(LevelInfo(25, 5), LevelInfo(100, 15), LevelInfo(300, 30)),
        BosstiaryRarity_t.RARITY_ARCHFOE to listOf(LevelInfo(5, 10), LevelInfo(20, 30), LevelInfo(60, 60)),
        BosstiaryRarity_t.RARITY_NEMESIS to listOf(LevelInfo(1, 10), LevelInfo(3, 30), LevelInfo(5, 60)),
    )

    /** `IOBosstiary::bosstiaryMap` (io_bosstiary.hpp:65), insertion ordered. */
    private val bosstiaryMap = linkedMapOf<Int, String>()

    /** `IOBosstiary::boostedBoss` (io_bosstiary.hpp:66). */
    private var boostedBoss = ""

    /** `IOBosstiary::boostedBossId` (io_bosstiary.hpp:67). */
    private var boostedBossId = 0

    /**
     * `IOBosstiary::loadBoostedBoss` (io_bosstiary.cpp:22-124): keeps the row's
     * boss when `date` is today, otherwise picks a random registered Archfoe,
     * rewrites the `boosted_boss` row (outfit included) and zeroes both
     * `player_bosstiary` slots that pointed at the new boss.
     */
    fun loadBoostedBoss() {
        val row = readBoostedBossRow()
        if (row == null) {
            logger.error { "[IOBosstiary::loadBoostedBoss] Failed to detect boosted boss database. (CODE 01)" }
            return
        }

        if (bosstiaryMap.size <= 1) {
            logger.error {
                "[IOBosstiary::loadBoostedBoss] It is not possible to create a boosted boss with only one registered boss. (CODE 02)"
            }
            return
        }

        val today = todayProvider()
        if (row.date.toIntOrNull() == today) {
            setBossBoostedName(row.boostName)
            setBossBoostedId(row.raceId.toIntOrNull() ?: 0)
            logger.info { "Boosted boss: ${row.boostName}" }
            return
        }

        val bossInfo = bosstiaryMap.entries.filter { (raceId, _) ->
            getMonsterTypeByBossRaceId(raceId)?.info?.bosstiaryRace == BosstiaryRarity_t.RARITY_ARCHFOE
        }
        if (bossInfo.isEmpty()) {
            logger.error { "Failed to boost a boss. There is no boss registered with the Archfoe Rarity." }
            return
        }

        val (bossId, bossName) = bossInfo[randomIndex(bossInfo.size)]
        val bossType = getMonsterTypeByBossRaceId(bossId)
        if (bossType == null) {
            logger.error { "[IOBosstiary::loadBoostedBoss] Failed to detect boosted boss database. (CODE 03)" }
            return
        }

        updateBoostedBossRow(today, bossName, bossId, bossType)
        resetPlayerBossSlot("boss_id_slot_one", bossId)
        resetPlayerBossSlot("boss_id_slot_two", bossId)
        setBossBoostedName(bossName)
        setBossBoostedId(bossId)
        logger.info { "Boosted boss: $bossName" }
    }

    /**
     * `IOBosstiary::addBosstiaryMonster` (io_bosstiary.cpp:126-135): the first
     * registration of a race id wins.
     */
    fun addBosstiaryMonster(raceId: Int, name: String) {
        bosstiaryMap.putIfAbsent(raceId, name)
    }

    /** `IOBosstiary::getBosstiaryMap` (io_bosstiary.cpp:137-139). */
    fun getBosstiaryMap(): Map<Int, String> = bosstiaryMap

    /** `IOBosstiary::setBossBoostedName` (io_bosstiary.cpp:141-143). */
    fun setBossBoostedName(name: String) {
        boostedBoss = name
    }

    /** `IOBosstiary::getBoostedBossName` (io_bosstiary.cpp:145-147). */
    fun getBoostedBossName(): String = boostedBoss

    /** `IOBosstiary::setBossBoostedId` (io_bosstiary.cpp:149-151). */
    fun setBossBoostedId(raceId: Int) {
        boostedBossId = raceId
    }

    /** `IOBosstiary::getBoostedBossId` (io_bosstiary.cpp:153-155). */
    fun getBoostedBossId(): Int = boostedBossId

    /**
     * `IOBosstiary::getMonsterTypeByBossRaceId` (io_bosstiary.cpp:157-171):
     * resolves the registered boss name through the monster registry.
     */
    fun getMonsterTypeByBossRaceId(raceId: Int): MonsterType? {
        for ((bossRaceId, bossName) in bosstiaryMap) {
            if (bossRaceId == raceId) {
                val monsterType = monsters.getMonsterType(bossName)
                if (monsterType == null) {
                    logger.error { "[IOBosstiary::getMonsterTypeByBossRaceId] Boss with id $raceId not found in boss map" }
                    continue
                }
                return monsterType
            }
        }
        return null
    }

    /**
     * `IOLoginDataLoad::loadPlayerBosstiary`
     * (iologindata_load_player.cpp:916-951): slots, `removeTimes` and the
     * tracker race ids; a missing row yields Canary's defaults.
     */
    fun loadPlayerBosstiary(playerId: Long): PlayerBosstiaryState = dataSource.connection.use { conn ->
        conn.prepareStatement(
            "SELECT boss_id_slot_one, boss_id_slot_two, remove_times, tracker FROM player_bosstiary WHERE player_id = ?",
        ).use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    PlayerBosstiaryState(
                        bossIdSlotOne = rs.getInt("boss_id_slot_one"),
                        bossIdSlotTwo = rs.getInt("boss_id_slot_two"),
                        removeTimes = rs.getInt("remove_times"),
                        tracker = decodeUint16Blob(rs.getBytes("tracker")),
                    )
                } else {
                    PlayerBosstiaryState()
                }
            }
        }
    }

    /**
     * `IOLoginDataSave::savePlayerBosstiary`
     * (iologindata_save_player.cpp:786-835): Canary deletes the player's row and
     * inserts a fresh one with the serialized tracker.
     */
    fun savePlayerBosstiary(playerId: Long, state: PlayerBosstiaryState) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement("DELETE FROM player_bosstiary WHERE player_id = ?").use { stmt ->
                    stmt.setLong(1, playerId)
                    stmt.executeUpdate()
                }
                conn.prepareStatement(
                    """
                    INSERT INTO player_bosstiary (player_id, boss_id_slot_one, boss_id_slot_two, remove_times, tracker)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setLong(1, playerId)
                    stmt.setInt(2, state.bossIdSlotOne)
                    stmt.setInt(3, state.bossIdSlotTwo)
                    stmt.setInt(4, state.removeTimes)
                    stmt.setBytes(5, encodeUint16Blob(state.tracker))
                    stmt.executeUpdate()
                }
                conn.commit()
            } catch (ex: Exception) {
                conn.rollback()
                throw ex
            }
        }
    }

    /** `players`.`boss_points` load (`IOLoginDataLoad` line 175). */
    fun loadBossPoints(playerId: Long): Int = dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT boss_points FROM players WHERE id = ?").use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt("boss_points") else 0 }
        }
    }

    /** `players`.`boss_points` save (`IOLoginDataSave` line 264). */
    fun saveBossPoints(playerId: Long, points: Int) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE players SET boss_points = ? WHERE id = ?").use { stmt ->
                stmt.setInt(1, points)
                stmt.setLong(2, playerId)
                stmt.executeUpdate()
            }
        }
    }

    /**
     * `IOBosstiary::getBossCurrentLevel` (io_bosstiary.cpp:275-301) over the
     * player's kill count; the C++ `player->getBestiaryKillCount(bossId)` read is
     * supplied by the caller as [currentKills].
     */
    fun getBossCurrentLevel(bossId: Int, currentKills: Int): Int {
        if (bossId == 0) {
            return 0
        }

        val mType = getMonsterTypeByBossRaceId(bossId) ?: return 0
        val stages = levelInfos[mType.info.bosstiaryRace] ?: run {
            logger.warn { "[IOBosstiary::getBossCurrentLevel] boss with id $bossId and name ${mType.name} not found in bossRace" }
            return 0
        }

        var level = 0
        for (raceInfo in stages) {
            if (currentKills >= raceInfo.kills) {
                level++
            }
        }
        return level
    }

    /** `IOBosstiary::calculateLootBonus` (io_bosstiary.cpp:217-225), truncating like the C++ cast. */
    fun calculateLootBonus(bossPoints: Int): Int {
        if (bossPoints <= 250) {
            return 25 + bossPoints / 10
        } else if (bossPoints < 1250) {
            return (37.5 + bossPoints / 20).toInt()
        }
        return (100 + 0.5 * (sqrt(8.0 * ((bossPoints - 1250) / 5) + 81.0) - 9.0)).toInt()
    }

    /** `IOBosstiary::calculateBossPoints` (io_bosstiary.cpp:227-239), truncating like the C++ cast. */
    fun calculateBossPoints(lootBonus: Int): Int {
        if (lootBonus <= 25) {
            return 0
        }

        if (lootBonus <= 50) {
            return 10 * lootBonus - 250
        } else if (lootBonus <= 100) {
            return 20 * lootBonus - 750
        }
        return (2.5 * lootBonus * lootBonus - 477.5 * lootBonus + 24000).toInt()
    }

    /**
     * `IOBosstiary::getBosstiaryFinished` (io_bosstiary.cpp:241-273) against the
     * registered boss map; the C++ `player->getBestiaryKillCount(bossId)` read is
     * supplied by [killCount] and the returned ids are sorted like Canary's
     * `stdext::vector_set`.
     */
    fun getBosstiaryFinished(killCount: (Int) -> Int, level: Int = 1): List<Int> {
        val unlockedMonsters = sortedSetOf<Int>()
        for ((bossId, bossName) in bosstiaryMap) {
            val bossKills = killCount(bossId)
            if (bossKills == 0) {
                continue
            }

            val mType = monsters.getMonsterType(bossName, silent = true)
            if (mType == null) {
                logger.error { "[IOBosstiary::getBosstiaryFinished] Boss with id $bossId and name $bossName not found in boss map" }
                continue
            }

            val stages = levelInfos[mType.info.bosstiaryRace]
            if (stages == null) {
                logger.warn { "[IOBosstiary::getBosstiaryFinished] boss with id $bossId and name $bossName not found in bossRace" }
                continue
            }

            val levelKills = stages.getOrNull(level - 1)?.kills ?: continue
            if (bossKills >= levelKills) {
                unlockedMonsters.add(bossId)
            }
        }
        return unlockedMonsters.toList()
    }

    /** `IOBosstiary::calculteRemoveBoss` (io_bosstiary.cpp:303-308). */
    fun calculteRemoveBoss(removeTimes: Int): Int {
        if (removeTimes < 2) {
            return 0
        }
        return 300000 * removeTimes - 500000
    }

    /**
     * `IOBosstiary::getBossRaceKillStages` (io_bosstiary.cpp:310-318): the empty
     * list models Canary's static empty vector fallback.
     */
    fun getBossRaceKillStages(race: BosstiaryRarity_t): List<LevelInfo> = levelInfos[race] ?: emptyList()

    private data class BoostedBossRow(val date: String, val boostName: String, val raceId: String)

    private fun readBoostedBossRow(): BoostedBossRow? = dataSource.connection.use { conn ->
        conn.prepareStatement(
            "SELECT ${SqlDialect.from(conn).quoteIdentifier("date")}, boostname, raceid FROM boosted_boss",
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    BoostedBossRow(
                        date = rs.getString("date") ?: "",
                        boostName = rs.getString("boostname") ?: "",
                        raceId = rs.getString("raceid") ?: "",
                    )
                } else {
                    null
                }
            }
        }
    }

    private fun updateBoostedBossRow(today: Int, bossName: String, bossId: Int, bossType: MonsterType) {
        val outfit = bossType.info.outfit
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                UPDATE boosted_boss SET
                    ${SqlDialect.from(conn).quoteIdentifier("date")} = ?, boostname = ?, raceid = ?,
                    looktypeEx = ?, looktype = ?, lookfeet = ?, looklegs = ?,
                    lookhead = ?, lookbody = ?, lookaddons = ?, lookmount = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, today.toString())
                stmt.setString(2, bossName)
                stmt.setString(3, bossId.toString())
                stmt.setInt(4, outfit.lookTypeEx)
                stmt.setInt(5, outfit.lookType)
                stmt.setInt(6, outfit.lookFeet)
                stmt.setInt(7, outfit.lookLegs)
                stmt.setInt(8, outfit.lookHead)
                stmt.setInt(9, outfit.lookBody)
                stmt.setInt(10, outfit.lookAddons)
                stmt.setInt(11, outfit.lookMount)
                stmt.executeUpdate()
            }
        }
    }

    private fun resetPlayerBossSlot(column: String, bossId: Int) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE player_bosstiary SET $column = 0 WHERE $column = ?").use { stmt ->
                stmt.setInt(1, bossId)
                stmt.executeUpdate()
            }
        }
    }

    private fun decodeUint16Blob(bytes: ByteArray?): List<Int> {
        if (bytes == null) {
            return emptyList()
        }
        val values = ArrayList<Int>(bytes.size / 2)
        var index = 0
        while (index + 1 < bytes.size) {
            values.add((bytes[index].toInt() and 0xFF) or ((bytes[index + 1].toInt() and 0xFF) shl 8))
            index += 2
        }
        return values
    }

    private fun encodeUint16Blob(values: List<Int>): ByteArray {
        val bytes = ByteArray(values.size * 2)
        values.forEachIndexed { index, value ->
            bytes[index * 2] = (value and 0xFF).toByte()
            bytes[index * 2 + 1] = ((value ushr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
