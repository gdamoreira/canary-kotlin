package org.opentibia.canary.persistence

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.JdbcFeatureStateRepository
import org.opentibia.canary.game.scheduling.SaveManager
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.offlinetraining.Skill
import org.opentibia.canary.systems.proficiency.WeaponProficiencyData
import org.opentibia.canary.systems.wheel.WheelSlots

class PlayerPersistenceServiceTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: JdbcFeatureStateRepository
    private lateinit var service: PlayerPersistenceService

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_persistence_flow_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(3)
        repository = JdbcFeatureStateRepository(dataSource)
        service = PlayerPersistenceService(repository)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun player(id: Long, name: String): Player = Player(
        id = id,
        name = name,
        maxHealth = 100,
        health = 100,
        position = Position(100, 100, 7),
    )

    private fun populate(player: Player) {
        val features = player.features
        features.wheel.setPointsBySlotType(WheelSlots.SLOT_GREEN_50, 50)
        features.wheel.setPointsBySlotType(WheelSlots.SLOT_RED_200, 200)
        features.offlineTraining.setOfflineTrainingSkill(Skill.SKILL_SWORD)
        features.offlineTraining.addOfflineTrainingTime(7_200_000)
        features.dailyReward.setDailyReward(0)
        features.forge.setForgeDusts(123_456_789L)
        features.forge.addForgeDustLevel(137L)
        features.bestiary.addBestiaryKillCount(31, 1000)
        features.bestiary.setCharmPoints(240)
        features.bestiary.setMinorCharmEchoes(7)
        features.mounts.setOwnedMounts(setOf(1, 5, 94))
        features.mounts.setCurrentMount(5)
        features.familiars.addFamiliar(1963)
        features.familiars.setFamiliarLooktype(1963)
        features.weaponProficiencies[3275] = WeaponProficiencyData(experience = 125_000, mastered = false)
    }

    @Test
    fun `save all persists feature state for online players`() {
        val storage = InMemoryPlayerStorage()
        val saveManager = SaveManager(storage, service)
        val player = player(1, "Alice")
        populate(player)
        saveManager.registerOnlinePlayer(player)

        val saved = saveManager.saveAll()

        assertThat(saved).isEqualTo(1)
        val persisted = repository.load(1)
        assertThat(persisted).isNotNull
        assertThat(persisted?.wheelPoints).containsEntry(WheelSlots.SLOT_GREEN_50.value, 50)
        assertThat(persisted?.bestiaryKillCounts).containsEntry(31, 1000)
        assertThat(persisted?.mounts).containsExactlyInAnyOrder(1, 5, 94)
        assertThat(persisted?.weaponProficiencies?.get(3275)?.experience).isEqualTo(125_000)
    }

    @Test
    fun `load restores feature state for the login path`() {
        val savedPlayer = player(1, "Bob")
        populate(savedPlayer)
        service.save(savedPlayer)

        val loginPlayer = player(1, "Bob")
        assertThat(service.load(loginPlayer)).isTrue()

        val features = loginPlayer.features
        assertThat(features.wheel.pointsBySlot).containsEntry(WheelSlots.SLOT_GREEN_50, 50)
        assertThat(features.offlineTraining.getOfflineTrainingTime()).isEqualTo(7_200_000)
        assertThat(features.offlineTraining.getOfflineTrainingSkill()).isEqualTo(Skill.SKILL_SWORD)
        assertThat(features.bestiary.getBestiaryKillCounts()).containsEntry(31, 1000)
        assertThat(features.mounts.getOwnedMounts()).containsExactlyInAnyOrder(1, 5, 94)
        assertThat(features.familiars.familiars.map { it.lookType }).containsExactly(1963)
        assertThat(features.weaponProficiencies[3275]?.experience).isEqualTo(125_000)
    }

    @Test
    fun `load returns false when no persisted feature state exists`() {
        val freshPlayer = player(2, "Newcomer")

        assertThat(service.load(freshPlayer)).isFalse()
    }

    @Test
    fun `shutdown persists feature state for retained sessions`() {
        val storage = InMemoryPlayerStorage()
        val saveManager = SaveManager(storage, service)
        val player = player(2, "Carol")
        populate(player)
        saveManager.registerOnlinePlayer(player)

        assertThat(saveManager.shutdown()).isTrue()

        assertThat(repository.load(2)).isNotNull
    }
}
