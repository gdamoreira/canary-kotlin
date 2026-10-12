package org.opentibia.canary.persistence

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.PlayerFeatures
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.JdbcFeatureStateRepository
import org.opentibia.canary.systems.offlinetraining.Skill
import org.opentibia.canary.systems.proficiency.WeaponProficiencyData
import org.opentibia.canary.systems.wheel.WheelSlots

class PlayerFeatureStateLoaderTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var loader: PlayerFeatureStateLoader

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_feature_loader_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(3)
        loader = PlayerFeatureStateLoader(JdbcFeatureStateRepository(dataSource))
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun populatedFeatures(): PlayerFeatures {
        val features = PlayerFeatures(playerId = 1, levelProvider = { 250 })
        features.wheel.setPointsBySlotType(WheelSlots.SLOT_GREEN_50, 50)
        features.wheel.setPointsBySlotType(WheelSlots.SLOT_RED_200, 200)
        features.offlineTraining.setOfflineTrainingSkill(Skill.SKILL_SWORD)
        features.offlineTraining.addOfflineTrainingTime(7_200_000)
        features.dailyReward.setDailyReward(0)
        features.forge.setForgeDusts(123_456_789L)
        features.forge.addForgeDustLevel(137L)
        features.bestiary.addBestiaryKillCount(31, 1000)
        features.bestiary.addBestiaryKillCount(220, 5)
        features.bestiary.setCharmPoints(240)
        features.bestiary.setMinorCharmEchoes(7)
        features.bestiary.setMaxCharmPoints(1000)
        features.bestiary.setMaxMinorCharmEchoes(12)
        features.bestiary.hasCharmExpansion = true
        features.bestiary.setUsedRunesBit(0b1011)
        features.bestiary.setUnlockedRunesBit(0b1111)
        features.mounts.setOwnedMounts(setOf(1, 5, 94))
        features.mounts.setCurrentMount(5)
        features.mounts.setRandomMount(1)
        features.familiars.addFamiliar(1963)
        features.familiars.addFamiliar(1970)
        features.familiars.setFamiliarLooktype(1970)
        features.weaponProficiencies[3275] = WeaponProficiencyData(experience = 125_000, mastered = false)
        features.weaponProficiencies[7406] = WeaponProficiencyData(experience = 30_000_000, mastered = true)
        return features
    }

    @Test
    fun `round-trips every persisted feature field through the H2 database`() {
        val original = populatedFeatures()
        loader.save(original)

        val restored = PlayerFeatures(playerId = 1, levelProvider = { 250 })
        assertThat(loader.loadInto(1, restored)).isTrue()

        assertThat(restored.wheel.pointsBySlot).isEqualTo(original.wheel.pointsBySlot)
        assertThat(restored.offlineTraining.getOfflineTrainingTime()).isEqualTo(7_200_000)
        assertThat(restored.offlineTraining.getOfflineTrainingSkill()).isEqualTo(Skill.SKILL_SWORD)
        assertThat(restored.dailyReward.isDailyReward).isEqualTo(0)
        assertThat(restored.forge.getForgeDusts()).isEqualTo(123_456_789L)
        assertThat(restored.forge.getForgeDustLevel()).isEqualTo(137L)
        assertThat(restored.bestiary.getBestiaryKillCounts()).isEqualTo(mapOf(31 to 1000, 220 to 5))
        assertThat(restored.bestiary.getCharmPoints()).isEqualTo(240)
        assertThat(restored.bestiary.getMinorCharmEchoes()).isEqualTo(7)
        assertThat(restored.bestiary.getMaxCharmPoints()).isEqualTo(1000)
        assertThat(restored.bestiary.getMaxMinorCharmEchoes()).isEqualTo(12)
        assertThat(restored.bestiary.hasCharmExpansion).isTrue()
        assertThat(restored.bestiary.getUsedRunesBit()).isEqualTo(0b1011)
        assertThat(restored.bestiary.getUnlockedRunesBit()).isEqualTo(0b1111)
        assertThat(restored.mounts.getOwnedMounts()).containsExactlyInAnyOrder(1, 5, 94)
        assertThat(restored.mounts.getCurrentMount()).isEqualTo(5)
        assertThat(restored.mounts.isRandomMounted()).isTrue()
        assertThat(restored.familiars.familiars.map { it.lookType }).containsExactlyInAnyOrder(1963, 1970)
        assertThat(restored.familiars.familiarLooktype).isEqualTo(1970)
        assertThat(restored.weaponProficiencies[3275]?.experience).isEqualTo(125_000)
        assertThat(restored.weaponProficiencies[3275]?.mastered).isFalse()
        assertThat(restored.weaponProficiencies[7406]?.experience).isEqualTo(30_000_000)
        assertThat(restored.weaponProficiencies[7406]?.mastered).isTrue()
    }

    @Test
    fun `loadInto returns false when no persisted feature state exists`() {
        val features = PlayerFeatures(playerId = 2)

        assertThat(loader.loadInto(2, features)).isFalse()
    }

    @Test
    fun `loadInto replaces state already present on the target features`() {
        loader.save(populatedFeatures())

        val target = PlayerFeatures(playerId = 1)
        target.bestiary.addBestiaryKillCount(31, 999)
        target.bestiary.addBestiaryKillCount(47, 9)
        target.mounts.setOwnedMounts(setOf(7))
        target.familiars.addFamiliar(1)

        assertThat(loader.loadInto(1, target)).isTrue()

        assertThat(target.bestiary.getBestiaryKillCounts()).isEqualTo(mapOf(31 to 1000, 220 to 5))
        assertThat(target.mounts.getOwnedMounts()).containsExactlyInAnyOrder(1, 5, 94)
        assertThat(target.familiars.familiars.map { it.lookType }).containsExactlyInAnyOrder(1963, 1970)
    }
}
