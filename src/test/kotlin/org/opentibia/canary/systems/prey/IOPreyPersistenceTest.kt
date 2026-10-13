package org.opentibia.canary.systems.prey

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.systems.taskhunting.PlayerTaskHuntingState
import org.opentibia.canary.systems.taskhunting.PreyTaskDataState
import org.opentibia.canary.systems.taskhunting.TaskHuntingConfig
import org.opentibia.canary.systems.taskhunting.TaskHuntingSlot

/**
 * H2 (Flyway V1-V12) round-trip tests for [IOPreyPersistence], transcribed from
 * Canary's prey/task-hunting persistence:
 *  - `IOLoginDataLoad::loadPlayerPreyClass` (iologindata_load_player.cpp:808-853)
 *  - `IOLoginDataLoad::loadPlayerTaskHuntingClass` (iologindata_load_player.cpp:855-903)
 *  - `IOLoginDataSave::savePlayerPreyClass` (iologindata_save_player.cpp:661-718)
 *  - `IOLoginDataSave::savePlayerTaskHuntingClass` (iologindata_save_player.cpp:720-775)
 * and the `player_prey` / `player_taskhunt` tables (schema.sql:717-754).
 */
class IOPreyPersistenceTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var persistence: IOPreyPersistence
    private val fixedNow = 1_700_000_000_000L
    private var playerId = 0L

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_io_prey_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(10)
        persistence = newPersistence()
        playerId = insertPlayer(100, "PreyOwner")
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun newPersistence(
        preyConfig: PreyConfig = PreyConfig(),
        taskHuntingConfig: TaskHuntingConfig = TaskHuntingConfig(),
    ): IOPreyPersistence = IOPreyPersistence(
        dataSource = dataSource,
        preyConfig = preyConfig,
        taskHuntingConfig = taskHuntingConfig,
        now = { fixedNow },
        preySlotFactory = { id -> PreySlot(id, uniformRandom = { _, _ -> 0 }, now = { fixedNow }) },
        taskSlotFactory = { id -> TaskHuntingSlot(id, uniformRandom = { _, _ -> 0 }, now = { fixedNow }) },
    )

    @Test
    fun `prey slot round trips every persisted column and monster list bytes`() {
        val source = PlayerPreyState()
        val slot = PreySlot(0)
        slot.state = PreyDataState.Active
        slot.selectedRaceId = 40
        slot.option = PreyOption.Locked
        slot.bonus = PreyBonus.Damage
        slot.bonusRarity = 7
        slot.bonusPercentage = 19
        slot.bonusTimeLeft = 3600
        slot.freeRerollTimeStamp = fixedNow + 500
        slot.raceIdList.addAll(listOf(1, 258))
        source.setPreySlotClass(slot)

        assertThat(persistence.savePlayerPreyClass(source, playerId)).isTrue()

        val loaded = PlayerPreyState()
        loaded.isPremium = true
        persistence.loadPlayerPreyClass(loaded, playerId)

        val stored = loaded.getPreySlotById(0)
        assertThat(stored).isNotNull
        assertThat(stored!!.state).isEqualTo(PreyDataState.Active)
        assertThat(stored.selectedRaceId).isEqualTo(40)
        assertThat(stored.option).isEqualTo(PreyOption.Locked)
        assertThat(stored.bonus).isEqualTo(PreyBonus.Damage)
        assertThat(stored.bonusRarity).isEqualTo(7)
        assertThat(stored.bonusPercentage).isEqualTo(19)
        assertThat(stored.bonusTimeLeft).isEqualTo(3600)
        assertThat(stored.freeRerollTimeStamp).isEqualTo(fixedNow + 500)
        assertThat(stored.raceIdList).containsExactly(1, 258)

        // PropWriteStream writes uint16_t little-endian: 1 -> 01 00, 258 -> 02 01.
        assertThat(preyMonsterListBytes(0)).containsExactly(1, 0, 2, 1)
    }

    @Test
    fun `load prey respects slot two premium lock`() {
        val source = PlayerPreyState()
        val slot = PreySlot(1)
        slot.state = PreyDataState.Locked
        source.setPreySlotClass(slot)

        assertThat(persistence.savePlayerPreyClass(source, playerId)).isTrue()

        val nonPremium = PlayerPreyState()
        nonPremium.isPremium = false
        persistence.loadPlayerPreyClass(nonPremium, playerId)
        assertThat(nonPremium.getPreySlotById(1)?.state).isEqualTo(PreyDataState.Locked)

        val premium = PlayerPreyState()
        premium.isPremium = true
        persistence.loadPlayerPreyClass(premium, playerId)
        assertThat(premium.getPreySlotById(1)?.state).isEqualTo(PreyDataState.Selection)
    }

    @Test
    fun `load prey does nothing and save is a no-op when disabled`() {
        val source = PlayerPreyState()
        val slot = PreySlot(0)
        slot.state = PreyDataState.Active
        slot.selectedRaceId = 40
        source.setPreySlotClass(slot)
        assertThat(persistence.savePlayerPreyClass(source, playerId)).isTrue()

        val disabled = newPersistence(preyConfig = PreyConfig(preyEnabled = false))
        val loaded = PlayerPreyState()
        loaded.isPremium = true
        disabled.loadPlayerPreyClass(loaded, playerId)
        assertThat(loaded.preys).isEmpty()

        val skipped = PlayerPreyState()
        val skippedSlot = PreySlot(0)
        skippedSlot.state = PreyDataState.Active
        skipped.setPreySlotClass(skippedSlot)
        assertThat(disabled.savePlayerPreyClass(skipped, playerId)).isTrue()
        assertThat(preyRowCount()).isEqualTo(1)
    }

    @Test
    fun `save prey updates an existing row instead of duplicating it`() {
        val slot = PreySlot(0)
        slot.state = PreyDataState.Selection
        slot.bonusTimeLeft = 10
        val source = PlayerPreyState()
        source.setPreySlotClass(slot)
        assertThat(persistence.savePlayerPreyClass(source, playerId)).isTrue()

        slot.state = PreyDataState.Active
        slot.bonusTimeLeft = 20
        assertThat(persistence.savePlayerPreyClass(source, playerId)).isTrue()

        assertThat(preyRowCount()).isEqualTo(1)
        val loaded = PlayerPreyState()
        loaded.isPremium = true
        persistence.loadPlayerPreyClass(loaded, playerId)
        assertThat(loaded.getPreySlotById(0)?.state).isEqualTo(PreyDataState.Active)
        assertThat(loaded.getPreySlotById(0)?.bonusTimeLeft).isEqualTo(20)
    }

    @Test
    fun `save prey skips players without slots`() {
        val empty = PlayerPreyState()

        assertThat(persistence.savePlayerPreyClass(empty, playerId)).isTrue()

        assertThat(preyRowCount()).isEqualTo(0)
    }

    @Test
    fun `empty monster list round trips as empty`() {
        val slot = PreySlot(2)
        slot.state = PreyDataState.Selection
        val source = PlayerPreyState()
        source.setPreySlotClass(slot)

        assertThat(persistence.savePlayerPreyClass(source, playerId)).isTrue()
        assertThat(preyMonsterListBytes(2)).isEmpty()

        val loaded = PlayerPreyState()
        loaded.isPremium = true
        persistence.loadPlayerPreyClass(loaded, playerId)
        assertThat(loaded.getPreySlotById(2)?.raceIdList).isEmpty()
    }

    @Test
    fun `task hunting slot round trips every persisted column`() {
        val source = PlayerTaskHuntingState()
        val slot = newTaskSlot(0)
        slot.state = PreyTaskDataState.Active
        slot.selectedRaceId = 40
        slot.upgrade = true
        slot.rarity = 4
        slot.currentKills = 12
        slot.disabledUntilTimeStamp = fixedNow + 900
        slot.freeRerollTimeStamp = fixedNow + 500
        slot.raceIdList.addAll(listOf(1, 258))
        source.setTaskHuntingSlotClass(slot)

        assertThat(persistence.savePlayerTaskHuntingClass(source, playerId)).isTrue()

        val loaded = PlayerTaskHuntingState()
        loaded.isPremium = true
        persistence.loadPlayerTaskHuntingClass(loaded, playerId)

        val stored = loaded.getTaskHuntingSlotById(0)
        assertThat(stored).isNotNull
        assertThat(stored!!.state).isEqualTo(PreyTaskDataState.Active)
        assertThat(stored.selectedRaceId).isEqualTo(40)
        assertThat(stored.upgrade).isTrue()
        assertThat(stored.rarity).isEqualTo(4)
        assertThat(stored.currentKills).isEqualTo(12)
        assertThat(stored.disabledUntilTimeStamp).isEqualTo(fixedNow + 900)
        assertThat(stored.freeRerollTimeStamp).isEqualTo(fixedNow + 500)
        assertThat(stored.raceIdList).containsExactly(1, 258)

        assertThat(taskMonsterListBytes(0)).containsExactly(1, 0, 2, 1)
    }

    @Test
    fun `load task hunting promotes expired inactive cooldown to selection`() {
        val source = PlayerTaskHuntingState()
        val slot = newTaskSlot(0)
        slot.state = PreyTaskDataState.Inactive
        slot.disabledUntilTimeStamp = fixedNow - 1
        source.setTaskHuntingSlotClass(slot)
        assertThat(persistence.savePlayerTaskHuntingClass(source, playerId)).isTrue()

        val loaded = PlayerTaskHuntingState()
        loaded.isPremium = true
        persistence.loadPlayerTaskHuntingClass(loaded, playerId)

        assertThat(loaded.getTaskHuntingSlotById(0)?.state).isEqualTo(PreyTaskDataState.Selection)
    }

    @Test
    fun `load task hunting keeps inactive within cooldown`() {
        val source = PlayerTaskHuntingState()
        val slot = newTaskSlot(0)
        slot.state = PreyTaskDataState.Inactive
        slot.disabledUntilTimeStamp = fixedNow + 1
        source.setTaskHuntingSlotClass(slot)
        assertThat(persistence.savePlayerTaskHuntingClass(source, playerId)).isTrue()

        val loaded = PlayerTaskHuntingState()
        loaded.isPremium = true
        persistence.loadPlayerTaskHuntingClass(loaded, playerId)

        assertThat(loaded.getTaskHuntingSlotById(0)?.state).isEqualTo(PreyTaskDataState.Inactive)
    }

    @Test
    fun `load task hunting respects slot two premium lock`() {
        val source = PlayerTaskHuntingState()
        val slot = newTaskSlot(1)
        slot.state = PreyTaskDataState.Locked
        source.setTaskHuntingSlotClass(slot)
        assertThat(persistence.savePlayerTaskHuntingClass(source, playerId)).isTrue()

        val nonPremium = PlayerTaskHuntingState()
        nonPremium.isPremium = false
        persistence.loadPlayerTaskHuntingClass(nonPremium, playerId)
        assertThat(nonPremium.getTaskHuntingSlotById(1)?.state).isEqualTo(PreyTaskDataState.Locked)

        val premium = PlayerTaskHuntingState()
        premium.isPremium = true
        persistence.loadPlayerTaskHuntingClass(premium, playerId)
        assertThat(premium.getTaskHuntingSlotById(1)?.state).isEqualTo(PreyTaskDataState.Selection)
    }

    @Test
    fun `save task hunting updates an existing row instead of duplicating it`() {
        val source = PlayerTaskHuntingState()
        val slot = newTaskSlot(0)
        slot.state = PreyTaskDataState.Selection
        slot.currentKills = 1
        source.setTaskHuntingSlotClass(slot)
        assertThat(persistence.savePlayerTaskHuntingClass(source, playerId)).isTrue()

        slot.state = PreyTaskDataState.Active
        slot.currentKills = 5
        assertThat(persistence.savePlayerTaskHuntingClass(source, playerId)).isTrue()

        assertThat(taskRowCount()).isEqualTo(1)
        val loaded = PlayerTaskHuntingState()
        loaded.isPremium = true
        persistence.loadPlayerTaskHuntingClass(loaded, playerId)
        assertThat(loaded.getTaskHuntingSlotById(0)?.state).isEqualTo(PreyTaskDataState.Active)
        assertThat(loaded.getTaskHuntingSlotById(0)?.currentKills).isEqualTo(5)
    }

    @Test
    fun `load and save task hunting are no-ops when disabled`() {
        val enabled = PlayerTaskHuntingState()
        val slot = newTaskSlot(0)
        slot.state = PreyTaskDataState.Active
        slot.selectedRaceId = 40
        enabled.setTaskHuntingSlotClass(slot)
        assertThat(persistence.savePlayerTaskHuntingClass(enabled, playerId)).isTrue()

        val disabled = newPersistence(taskHuntingConfig = TaskHuntingConfig(taskHuntingEnabled = false))
        val loaded = PlayerTaskHuntingState()
        loaded.isPremium = true
        disabled.loadPlayerTaskHuntingClass(loaded, playerId)
        assertThat(loaded.taskHunting).isEmpty()

        val skipped = PlayerTaskHuntingState()
        val skippedSlot = newTaskSlot(0)
        skippedSlot.state = PreyTaskDataState.Active
        skipped.setTaskHuntingSlotClass(skippedSlot)
        assertThat(disabled.savePlayerTaskHuntingClass(skipped, playerId)).isTrue()
        assertThat(taskRowCount()).isEqualTo(1)
    }

    private fun newTaskSlot(id: Int): TaskHuntingSlot =
        TaskHuntingSlot(id, uniformRandom = { _, _ -> 0 }, now = { fixedNow })

    private fun insertPlayer(id: Long, name: String): Long {
        dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO players (id, name, account_id) VALUES (?, ?, 1)").use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, name)
                stmt.executeUpdate()
            }
        }
        return id
    }

    private fun preyRowCount(): Int = rowCount("player_prey")

    private fun taskRowCount(): Int = rowCount("player_taskhunt")

    private fun rowCount(table: String): Int =
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COUNT(*) FROM $table WHERE player_id = $playerId").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    private fun preyMonsterListBytes(slot: Int): ByteArray = monsterListBytes("player_prey", slot)

    private fun taskMonsterListBytes(slot: Int): ByteArray = monsterListBytes("player_taskhunt", slot)

    private fun monsterListBytes(table: String, slot: Int): ByteArray =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT monster_list FROM $table WHERE player_id = ? AND slot = ?").use { stmt ->
                stmt.setLong(1, playerId)
                stmt.setInt(2, slot)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.getBytes(1) ?: ByteArray(0)
                }
            }
        }
}
