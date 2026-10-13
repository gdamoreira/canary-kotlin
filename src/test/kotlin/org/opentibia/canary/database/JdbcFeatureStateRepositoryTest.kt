package org.opentibia.canary.database

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JdbcFeatureStateRepositoryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: JdbcFeatureStateRepository

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_feature_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(4)
        repository = JdbcFeatureStateRepository(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `round-trips wheel points and offline training state`() {
        val state = PlayerFeatureState(
            playerId = 1,
            wheelPoints = mapOf(2 to 50, 3 to 75, 17 to 200),
            offlineTrainingTime = 21600,
            offlineTrainingSkill = 2,
        )

        repository.save(state)
        val loaded = repository.load(1)

        assertThat(loaded).isNotNull
        assertThat(loaded!!.wheelPoints).isEqualTo(mapOf(2 to 50, 3 to 75, 17 to 200))
        assertThat(loaded.offlineTrainingTime).isEqualTo(21600)
        assertThat(loaded.offlineTrainingSkill).isEqualTo(2)
    }

    @Test
    fun `round-trips daily reward and forge dust state`() {
        val state = PlayerFeatureState(
            playerId = 1,
            dailyReward = 0,
            forgeDusts = 123456789L,
            forgeDustLevel = 137L,
        )

        repository.save(state)
        val loaded = repository.load(1)

        assertThat(loaded).isNotNull
        assertThat(loaded!!.dailyReward).isEqualTo(0)
        assertThat(loaded.forgeDusts).isEqualTo(123456789L)
        assertThat(loaded.forgeDustLevel).isEqualTo(137L)
    }

    @Test
    fun `round-trips bestiary kill counts and charm state`() {
        val state = PlayerFeatureState(
            playerId = 1,
            bestiaryKillCounts = mapOf(31 to 1000, 47 to 12, 220 to 5),
            charmPoints = 240,
            minorCharmEchoes = 7,
            maxCharmPoints = 1000,
            maxMinorCharmEchoes = 12,
            charmExpansion = true,
            usedRunesBit = 0b1011,
            unlockedRunesBit = 0b1111,
        )

        repository.save(state)
        val loaded = repository.load(1)

        assertThat(loaded).isNotNull
        assertThat(loaded!!.bestiaryKillCounts).isEqualTo(mapOf(31 to 1000, 47 to 12, 220 to 5))
        assertThat(loaded.charmPoints).isEqualTo(240)
        assertThat(loaded.minorCharmEchoes).isEqualTo(7)
        assertThat(loaded.maxCharmPoints).isEqualTo(1000)
        assertThat(loaded.maxMinorCharmEchoes).isEqualTo(12)
        assertThat(loaded.charmExpansion).isTrue()
        assertThat(loaded.usedRunesBit).isEqualTo(0b1011)
        assertThat(loaded.unlockedRunesBit).isEqualTo(0b1111)
    }

    @Test
    fun `round-trips mounts and familiars state`() {
        val state = PlayerFeatureState(
            playerId = 1,
            mounts = setOf(1, 5, 94),
            currentMount = 5,
            randomizeMount = 1,
            familiars = setOf(1963, 1970),
            familiarLookType = 1970,
        )

        repository.save(state)
        val loaded = repository.load(1)

        assertThat(loaded).isNotNull
        assertThat(loaded!!.mounts).containsExactlyInAnyOrder(1, 5, 94)
        assertThat(loaded.currentMount).isEqualTo(5)
        assertThat(loaded.randomizeMount).isEqualTo(1)
        assertThat(loaded.familiars).containsExactlyInAnyOrder(1963, 1970)
        assertThat(loaded.familiarLookType).isEqualTo(1970)
    }

    @Test
    fun `round-trips weapon proficiency state`() {
        val state = PlayerFeatureState(
            playerId = 1,
            weaponProficiencies = mapOf(
                3275 to WeaponProficiencyState(experience = 125000, mastered = false),
                7406 to WeaponProficiencyState(experience = 30000000, mastered = true),
            ),
        )

        repository.save(state)
        val loaded = repository.load(1)

        assertThat(loaded).isNotNull
        assertThat(loaded!!.weaponProficiencies).isEqualTo(
            mapOf(
                3275 to WeaponProficiencyState(experience = 125000, mastered = false),
                7406 to WeaponProficiencyState(experience = 30000000, mastered = true),
            ),
        )
    }

    @Test
    fun `round-trips bestiary charm assignments`() {
        val assignments = mapOf(
            0 to CharmAssignmentState(raceId = 31, tier = 2),
            5 to CharmAssignmentState(raceId = 47, tier = 1),
            24 to CharmAssignmentState(raceId = 220, tier = 3),
        )
        val state = PlayerFeatureState(
            playerId = 1,
            charmAssignments = assignments,
        )

        repository.save(state)
        val loaded = repository.load(1)

        assertThat(loaded).isNotNull
        assertThat(loaded!!.charmAssignments).isEqualTo(assignments)
    }

    @Test
    fun `round-trips weapon proficiency perks`() {
        val perks = listOf(
            WeaponProficiencyPerkState(
                level = 1,
                index = 0,
                value = 0.1,
                type = 0,
            ),
            WeaponProficiencyPerkState(
                level = 2,
                index = 3,
                value = 0.05,
                spellId = 42,
                range = 6,
                bestiaryId = 31,
                bestiaryName = "Demon",
                augmentType = 1,
                skillId = 5,
                element = 2,
                type = 5,
            ),
        )
        val state = PlayerFeatureState(
            playerId = 1,
            weaponProficiencies = mapOf(
                3275 to WeaponProficiencyState(experience = 125000, mastered = false, perks = perks),
                7406 to WeaponProficiencyState(experience = 30000000, mastered = true),
            ),
        )

        repository.save(state)
        val loaded = repository.load(1)

        assertThat(loaded).isNotNull
        assertThat(loaded!!.weaponProficiencies[3275]?.perks).isEqualTo(perks)
        assertThat(loaded.weaponProficiencies[7406]?.perks).isEmpty()
    }

    @Test
    fun `round-trips wheel revealed gems active gems and grades`() {
        val gems = listOf(
            WheelGemState(
                locked = false,
                affinity = 0,
                quality = 1,
                basicModifier1 = 3,
                basicModifier2 = 9,
                supremeModifier = null,
            ),
            WheelGemState(
                locked = true,
                affinity = 3,
                quality = 2,
                basicModifier1 = 31,
                basicModifier2 = null,
                supremeModifier = 7,
            ),
        )
        val grades = listOf(
            WheelGemGradeState(fragmentType = 0, position = 4, grade = 3),
            WheelGemGradeState(fragmentType = 1, position = 12, grade = 1),
        )
        val state = PlayerFeatureState(
            playerId = 1,
            wheelGems = gems,
            activeWheelGems = mapOf(0 to 0, 3 to 1),
            wheelGemGrades = grades,
        )

        repository.save(state)
        val loaded = repository.load(1)

        assertThat(loaded).isNotNull
        assertThat(loaded!!.wheelGems).isEqualTo(gems)
        assertThat(loaded.activeWheelGems).isEqualTo(mapOf(0 to 0, 3 to 1))
        assertThat(loaded.wheelGemGrades).containsExactlyInAnyOrder(
            WheelGemGradeState(fragmentType = 0, position = 4, grade = 3),
            WheelGemGradeState(fragmentType = 1, position = 12, grade = 1),
        )
    }

    @Test
    fun `returns null for a player without persisted feature state`() {
        assertThat(repository.load(2)).isNull()
    }

    @Test
    fun `replaces existing state on resave`() {
        repository.save(
            PlayerFeatureState(
                playerId = 1,
                wheelPoints = mapOf(2 to 50),
                bestiaryKillCounts = mapOf(31 to 1),
                charmAssignments = mapOf(0 to CharmAssignmentState(raceId = 31, tier = 1)),
                mounts = setOf(1),
                familiars = setOf(1963),
                wheelGems = listOf(WheelGemState(locked = false, affinity = 0, quality = 0, basicModifier1 = 0)),
                activeWheelGems = mapOf(0 to 0),
                wheelGemGrades = listOf(WheelGemGradeState(fragmentType = 0, position = 1, grade = 1)),
                weaponProficiencies = mapOf(
                    3275 to WeaponProficiencyState(
                        1,
                        false,
                        listOf(WeaponProficiencyPerkState(level = 1, type = 0)),
                    ),
                ),
            ),
        )

        repository.save(
            PlayerFeatureState(
                playerId = 1,
                wheelPoints = mapOf(4 to 25),
                bestiaryKillCounts = mapOf(47 to 9),
                charmAssignments = mapOf(5 to CharmAssignmentState(raceId = 47, tier = 2)),
                mounts = setOf(5),
                familiars = setOf(1970),
                wheelGems = listOf(WheelGemState(locked = true, affinity = 3, quality = 2, basicModifier1 = 31)),
                activeWheelGems = mapOf(3 to 0),
                wheelGemGrades = listOf(WheelGemGradeState(fragmentType = 1, position = 2, grade = 2)),
                weaponProficiencies = mapOf(
                    7406 to WeaponProficiencyState(
                        2,
                        true,
                        listOf(WeaponProficiencyPerkState(level = 2, type = 5)),
                    ),
                ),
            ),
        )

        val loaded = repository.load(1)
        assertThat(loaded).isNotNull
        assertThat(loaded!!.wheelPoints).isEqualTo(mapOf(4 to 25))
        assertThat(loaded.bestiaryKillCounts).isEqualTo(mapOf(47 to 9))
        assertThat(loaded.charmAssignments).isEqualTo(mapOf(5 to CharmAssignmentState(raceId = 47, tier = 2)))
        assertThat(loaded.mounts).containsExactly(5)
        assertThat(loaded.familiars).containsExactly(1970)
        assertThat(loaded.wheelGems).isEqualTo(
            listOf(WheelGemState(locked = true, affinity = 3, quality = 2, basicModifier1 = 31)),
        )
        assertThat(loaded.activeWheelGems).isEqualTo(mapOf(3 to 0))
        assertThat(loaded.wheelGemGrades).isEqualTo(listOf(WheelGemGradeState(fragmentType = 1, position = 2, grade = 2)))
        assertThat(loaded.weaponProficiencies).isEqualTo(
            mapOf(7406 to WeaponProficiencyState(2, true, listOf(WeaponProficiencyPerkState(level = 2, type = 5)))),
        )
    }
}
