package org.opentibia.canary.systems.prey

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.database.SqlDialect
import java.sql.SQLException
import javax.sql.DataSource
import org.opentibia.canary.systems.taskhunting.PlayerTaskHuntingState
import org.opentibia.canary.systems.taskhunting.PreyTaskDataState
import org.opentibia.canary.systems.taskhunting.TaskHuntingConfig
import org.opentibia.canary.systems.taskhunting.TaskHuntingSlot

private val logger = KotlinLogging.logger {}

/**
 * Faithful port of the prey/task-hunting database persistence of `src/io/ioprey.cpp`:
 *  - `IOLoginDataLoad::loadPlayerPreyClass` (iologindata_load_player.cpp:808-853)
 *  - `IOLoginDataLoad::loadPlayerTaskHuntingClass` (iologindata_load_player.cpp:855-903)
 *  - `IOLoginDataSave::savePlayerPreyClass` (iologindata_save_player.cpp:661-718)
 *  - `IOLoginDataSave::savePlayerTaskHuntingClass` (iologindata_save_player.cpp:720-775)
 *
 * The `g_configManager()` / `g_database()` globals become constructor-injected
 * state (AGENTS.md 1.1): the player GUID is passed explicitly because the
 * engine-side state holders carry no `Player::getGUID`. `raceIdList` keeps
 * Canary's raw `PropWriteStream` uint16 little-endian blob encoding, and the
 * MySQL `ON DUPLICATE KEY UPDATE` upsert becomes UPDATE-then-INSERT, which is
 * portable between PostgreSQL and H2 PostgreSQL mode.
 */
class IOPreyPersistence(
    private val dataSource: DataSource,
    private val preyConfig: PreyConfig = PreyConfig(),
    private val taskHuntingConfig: TaskHuntingConfig = TaskHuntingConfig(),
    private val now: () -> Long = { System.currentTimeMillis() },
    private val preySlotFactory: (Int) -> PreySlot = { id -> PreySlot(id, config = preyConfig, now = now) },
    private val taskSlotFactory: (Int) -> TaskHuntingSlot = { id ->
        TaskHuntingSlot(id, config = taskHuntingConfig, now = now)
    },
) {
    /** `IOLoginDataLoad::loadPlayerPreyClass` (iologindata_load_player.cpp:808-853). */
    fun loadPlayerPreyClass(player: PlayerPreyState, playerGuid: Long) {
        if (!preyConfig.preyEnabled) {
            return
        }

        for (row in selectPreyRows(playerGuid)) {
            val slot = preySlotFactory(row.slot)
            val state = preyStateOf(row.state)
            if (slot.id == PreyEngine.PREY_SLOT_TWO && state == PreyDataState.Locked) {
                slot.state = if (!player.isPremium) PreyDataState.Locked else PreyDataState.Selection
            } else {
                slot.state = state
            }
            slot.selectedRaceId = row.raceId
            slot.option = preyOptionOf(row.option)
            slot.bonus = preyBonusOf(row.bonusType)
            slot.bonusRarity = row.bonusRarity
            slot.bonusPercentage = row.bonusPercentage
            slot.bonusTimeLeft = row.bonusTime
            slot.freeRerollTimeStamp = row.freeReroll
            slot.raceIdList.addAll(decodeMonsterList(row.monsterList))
            player.setPreySlotClass(slot)
        }
    }

    /** `IOLoginDataSave::savePlayerPreyClass` (iologindata_save_player.cpp:661-718). */
    fun savePlayerPreyClass(player: PlayerPreyState, playerGuid: Long): Boolean {
        if (!preyConfig.preyEnabled) {
            return true
        }

        for (slotId in PreyEngine.PREY_SLOT_FIRST..PreyEngine.PREY_SLOT_LAST) {
            val slot = player.getPreySlotById(slotId) ?: continue
            if (!upsertPreyRow(playerGuid, slot)) {
                logger.warn { "[savePlayerPreyClass] - Error saving prey slot data from player $playerGuid" }
                return false
            }
        }
        return true
    }

    /** `IOLoginDataLoad::loadPlayerTaskHuntingClass` (iologindata_load_player.cpp:855-903). */
    fun loadPlayerTaskHuntingClass(player: PlayerTaskHuntingState, playerGuid: Long) {
        if (!taskHuntingConfig.taskHuntingEnabled) {
            return
        }

        for (row in selectTaskHuntingRows(playerGuid)) {
            val slot = taskSlotFactory(row.slot)
            val state = taskStateOf(row.state)
            if (slot.id == PreyEngine.PREY_SLOT_TWO && state == PreyTaskDataState.Locked) {
                slot.state = if (!player.isPremium) PreyTaskDataState.Locked else PreyTaskDataState.Selection
            } else {
                slot.state = state
            }
            slot.selectedRaceId = row.raceId
            slot.upgrade = row.upgrade
            slot.rarity = row.rarity
            slot.currentKills = row.kills
            slot.disabledUntilTimeStamp = row.disabledTime
            slot.freeRerollTimeStamp = row.freeReroll
            slot.raceIdList.addAll(decodeMonsterList(row.monsterList))

            if (slot.state == PreyTaskDataState.Inactive && slot.disabledUntilTimeStamp < now()) {
                slot.state = PreyTaskDataState.Selection
            }

            player.setTaskHuntingSlotClass(slot)
        }
    }

    /** `IOLoginDataSave::savePlayerTaskHuntingClass` (iologindata_save_player.cpp:720-775). */
    fun savePlayerTaskHuntingClass(player: PlayerTaskHuntingState, playerGuid: Long): Boolean {
        if (!taskHuntingConfig.taskHuntingEnabled) {
            return true
        }

        for (slotId in PreyEngine.PREY_SLOT_FIRST..PreyEngine.PREY_SLOT_LAST) {
            val slot = player.getTaskHuntingSlotById(slotId) ?: continue
            if (!upsertTaskHuntingRow(playerGuid, slot)) {
                logger.warn { "[savePlayerTaskHuntingClass] - Error saving task hunting slot data from player $playerGuid" }
                return false
            }
        }
        return true
    }

    private data class PreyRow(
        val slot: Int,
        val state: Int,
        val raceId: Int,
        val option: Int,
        val bonusType: Int,
        val bonusRarity: Int,
        val bonusPercentage: Int,
        val bonusTime: Int,
        val freeReroll: Long,
        val monsterList: ByteArray?,
    )

    private data class TaskHuntingRow(
        val slot: Int,
        val state: Int,
        val raceId: Int,
        val upgrade: Boolean,
        val rarity: Int,
        val kills: Int,
        val disabledTime: Long,
        val freeReroll: Long,
        val monsterList: ByteArray?,
    )

    private fun selectPreyRows(playerGuid: Long): List<PreyRow> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT slot, state, raceid, ${SqlDialect.from(connection).quoteIdentifier("option")}, " +
                    "bonus_type, bonus_rarity, bonus_percentage, " +
                    "bonus_time, free_reroll, monster_list FROM player_prey WHERE player_id = ?",
            ).use { statement ->
                statement.setLong(1, playerGuid)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                PreyRow(
                                    slot = resultSet.getInt("slot"),
                                    state = resultSet.getInt("state"),
                                    raceId = resultSet.getInt("raceid"),
                                    option = resultSet.getInt("option"),
                                    bonusType = resultSet.getInt("bonus_type"),
                                    bonusRarity = resultSet.getInt("bonus_rarity"),
                                    bonusPercentage = resultSet.getInt("bonus_percentage"),
                                    bonusTime = resultSet.getInt("bonus_time"),
                                    freeReroll = resultSet.getLong("free_reroll"),
                                    monsterList = resultSet.getBytes("monster_list"),
                                ),
                            )
                        }
                    }
                }
            }
        }

    private fun selectTaskHuntingRows(playerGuid: Long): List<TaskHuntingRow> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT slot, state, raceid, upgrade, rarity, kills, disabled_time, free_reroll, monster_list " +
                    "FROM player_taskhunt WHERE player_id = ?",
            ).use { statement ->
                statement.setLong(1, playerGuid)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                TaskHuntingRow(
                                    slot = resultSet.getInt("slot"),
                                    state = resultSet.getInt("state"),
                                    raceId = resultSet.getInt("raceid"),
                                    upgrade = resultSet.getInt("upgrade") != 0,
                                    rarity = resultSet.getInt("rarity"),
                                    kills = resultSet.getInt("kills"),
                                    disabledTime = resultSet.getLong("disabled_time"),
                                    freeReroll = resultSet.getLong("free_reroll"),
                                    monsterList = resultSet.getBytes("monster_list"),
                                ),
                            )
                        }
                    }
                }
            }
        }

    private fun upsertPreyRow(playerGuid: Long, slot: PreySlot): Boolean =
        inTransaction { connection ->
            val option = SqlDialect.from(connection).quoteIdentifier("option")
            val updated = connection.prepareStatement(
                "UPDATE player_prey SET state = ?, raceid = ?, $option = ?, bonus_type = ?, bonus_rarity = ?, " +
                    "bonus_percentage = ?, bonus_time = ?, free_reroll = ?, monster_list = ? " +
                    "WHERE player_id = ? AND slot = ?",
            ).use { statement ->
                statement.setInt(1, slot.state.value)
                statement.setInt(2, slot.selectedRaceId)
                statement.setInt(3, slot.option.value)
                statement.setInt(4, slot.bonus.value)
                statement.setInt(5, slot.bonusRarity)
                statement.setInt(6, slot.bonusPercentage)
                statement.setInt(7, slot.bonusTimeLeft)
                statement.setLong(8, slot.freeRerollTimeStamp)
                statement.setBytes(9, encodeMonsterList(slot.raceIdList))
                statement.setLong(10, playerGuid)
                statement.setInt(11, slot.id)
                statement.executeUpdate()
            }

            if (updated == 0) {
                connection.prepareStatement(
                    "INSERT INTO player_prey (player_id, slot, state, raceid, $option, bonus_type, bonus_rarity, " +
                        "bonus_percentage, bonus_time, free_reroll, monster_list) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                ).use { statement ->
                    statement.setLong(1, playerGuid)
                    statement.setInt(2, slot.id)
                    statement.setInt(3, slot.state.value)
                    statement.setInt(4, slot.selectedRaceId)
                    statement.setInt(5, slot.option.value)
                    statement.setInt(6, slot.bonus.value)
                    statement.setInt(7, slot.bonusRarity)
                    statement.setInt(8, slot.bonusPercentage)
                    statement.setInt(9, slot.bonusTimeLeft)
                    statement.setLong(10, slot.freeRerollTimeStamp)
                    statement.setBytes(11, encodeMonsterList(slot.raceIdList))
                    statement.executeUpdate()
                }
            }
        }

    private fun upsertTaskHuntingRow(playerGuid: Long, slot: TaskHuntingSlot): Boolean =
        inTransaction { connection ->
            val updated = connection.prepareStatement(
                "UPDATE player_taskhunt SET state = ?, raceid = ?, upgrade = ?, rarity = ?, kills = ?, " +
                    "disabled_time = ?, free_reroll = ?, monster_list = ? WHERE player_id = ? AND slot = ?",
            ).use { statement ->
                statement.setInt(1, slot.state.value)
                statement.setInt(2, slot.selectedRaceId)
                statement.setInt(3, if (slot.upgrade) 1 else 0)
                statement.setInt(4, slot.rarity)
                statement.setInt(5, slot.currentKills)
                statement.setLong(6, slot.disabledUntilTimeStamp)
                statement.setLong(7, slot.freeRerollTimeStamp)
                statement.setBytes(8, encodeMonsterList(slot.raceIdList))
                statement.setLong(9, playerGuid)
                statement.setInt(10, slot.id)
                statement.executeUpdate()
            }

            if (updated == 0) {
                connection.prepareStatement(
                    "INSERT INTO player_taskhunt (player_id, slot, state, raceid, upgrade, rarity, kills, " +
                        "disabled_time, free_reroll, monster_list) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                ).use { statement ->
                    statement.setLong(1, playerGuid)
                    statement.setInt(2, slot.id)
                    statement.setInt(3, slot.state.value)
                    statement.setInt(4, slot.selectedRaceId)
                    statement.setInt(5, if (slot.upgrade) 1 else 0)
                    statement.setInt(6, slot.rarity)
                    statement.setInt(7, slot.currentKills)
                    statement.setLong(8, slot.disabledUntilTimeStamp)
                    statement.setLong(9, slot.freeRerollTimeStamp)
                    statement.setBytes(10, encodeMonsterList(slot.raceIdList))
                    statement.executeUpdate()
                }
            }
        }

    private fun inTransaction(action: (java.sql.Connection) -> Unit): Boolean =
        try {
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    action(connection)
                    connection.commit()
                } catch (ex: Exception) {
                    connection.rollback()
                    throw ex
                }
            }
            true
        } catch (ex: SQLException) {
            logger.error(ex) { "[IOPreyPersistence] - Failed to save prey/task hunting slot" }
            false
        }

    private fun preyStateOf(value: Int): PreyDataState =
        PreyDataState.entries.firstOrNull { it.value == value } ?: PreyDataState.Locked

    private fun preyOptionOf(value: Int): PreyOption =
        PreyOption.entries.firstOrNull { it.value == value } ?: PreyOption.None

    private fun preyBonusOf(value: Int): PreyBonus =
        PreyBonus.entries.firstOrNull { it.value == value } ?: PreyBonus.None

    private fun taskStateOf(value: Int): PreyTaskDataState =
        PreyTaskDataState.entries.firstOrNull { it.value == value } ?: PreyTaskDataState.Locked

    /** `PropWriteStream::write<uint16_t>`: the raw little-endian race id bytes indexed by `getStream`. */
    private fun encodeMonsterList(raceIds: List<Int>): ByteArray {
        val bytes = ByteArray(raceIds.size * 2)
        raceIds.forEachIndexed { index, raceId ->
            bytes[index * 2] = (raceId and 0xFF).toByte()
            bytes[index * 2 + 1] = ((raceId ushr 8) and 0xFF).toByte()
        }
        return bytes
    }

    /** `PropStream::read<uint16_t>`: stops on a trailing partial entry. */
    private fun decodeMonsterList(monsterList: ByteArray?): List<Int> {
        if (monsterList == null) {
            return emptyList()
        }

        val raceIds = mutableListOf<Int>()
        var index = 0
        while (index + 1 < monsterList.size) {
            val low = monsterList[index].toInt() and 0xFF
            val high = monsterList[index + 1].toInt() and 0xFF
            raceIds.add(low or (high shl 8))
            index += 2
        }
        return raceIds
    }
}
