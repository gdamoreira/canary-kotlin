package org.opentibia.canary.systems.hazard

import java.util.Random
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for the Gnomprona Hazard engine core. Expected values are
 * transcribed from Canary:
 *  - src/creatures/monsters/monster.hpp:260-274,460-464 / monster.cpp:3152-3190
 *  - src/creatures/players/player.cpp:3641-3646 (exp bonus)
 *  - src/creatures/players/player.cpp:5513-5647 (attack parsers + points)
 *  - src/game/game.cpp:8193-8201 (handleHazardSystemAttack)
 *  - src/config/configmanager.cpp:153,180,277-288 (HAZARD_* defaults)
 *  - src/utils/const.hpp:34 (STORAGEVALUE_HAZARDCOUNT)
 *  - src/creatures/creatures_definitions.hpp:841,852 (COMBAT_HEALING/COMBAT_NONE)
 *  - src/utils/tools.cpp:466-475 (normal_random distribution)
 */
class HazardSystemTest {

    // ---- Config / constants --------------------------------------------------

    @Test
    fun `hazard config defaults match canary configmanager`() {
        val config = HazardConfig()
        assertThat(config.toggleHazardSystem).isTrue()
        assertThat(config.criticalChance).isEqualTo(750)
        assertThat(config.criticalInterval).isEqualTo(2000)
        assertThat(config.criticalMultiplier).isEqualTo(25)
        assertThat(config.damageMultiplier).isEqualTo(200)
        assertThat(config.defenseMultiplier).isEqualTo(0)
        assertThat(config.dodgeMultiplier).isEqualTo(85)
        assertThat(config.expBonusMultiplier).isEqualTo(2.0)
        assertThat(config.lootBonusMultiplier).isEqualTo(2)
        assertThat(config.podsDamage).isEqualTo(5)
        assertThat(config.podsDropMultiplier).isEqualTo(87)
        assertThat(config.podsTimeToDamage).isEqualTo(2000)
        assertThat(config.podsTimeToSpawn).isEqualTo(4000)
        assertThat(config.spawnPlunderMultiplier).isEqualTo(25)
    }

    @Test
    fun `hazard storage and combat ids match canary`() {
        assertThat(PlayerHazard.STORAGEVALUE_HAZARDCOUNT).isEqualTo(112550)
        assertThat(CombatType.COMBAT_HEALING).isEqualTo(7)
        assertThat(CombatType.COMBAT_NONE).isEqualTo(255)
    }

    // ---- Monster hazard flags ------------------------------------------------

    @Test
    fun `monster hazard flags default false and round trip`() {
        val monster = MonsterHazard()
        assertThat(monster.getHazard()).isFalse()
        assertThat(monster.getHazardSystemCrit()).isFalse()
        assertThat(monster.getHazardSystemDodge()).isFalse()
        assertThat(monster.getHazardSystemDamageBoost()).isFalse()
        assertThat(monster.getHazardSystemDefenseBoost()).isFalse()

        monster.setHazard(true)
        monster.setHazardSystemCrit(true)
        monster.setHazardSystemDodge(true)
        monster.setHazardSystemDamageBoost(true)
        monster.setHazardSystemDefenseBoost(true)

        assertThat(monster.getHazard()).isTrue()
        assertThat(monster.getHazardSystemCrit()).isTrue()
        assertThat(monster.getHazardSystemDodge()).isTrue()
        assertThat(monster.getHazardSystemDamageBoost()).isTrue()
        assertThat(monster.getHazardSystemDefenseBoost()).isTrue()
    }

    // ---- Player hazard points ------------------------------------------------

    @Test
    fun `hazard points default zero and clamp to unsigned short`() {
        val player = PlayerHazard()
        assertThat(player.getHazardSystemPoints()).isEqualTo(0)

        player.setHazardSystemPoints(5)
        assertThat(player.getHazardSystemPoints()).isEqualTo(5)

        player.setHazardSystemPoints(0)
        assertThat(player.getHazardSystemPoints()).isEqualTo(0)

        player.setHazardSystemPoints(-3)
        assertThat(player.getHazardSystemPoints()).isEqualTo(0)

        player.setHazardSystemPoints(100000)
        assertThat(player.getHazardSystemPoints()).isEqualTo(65535)
    }

    @Test
    fun `setting hazard points requests a counter reload`() {
        val player = PlayerHazard()
        assertThat(player.reloadHazardSystemPointsCounter).isTrue()
        player.setHazardSystemPoints(7)
        assertThat(player.reloadHazardSystemPointsCounter).isTrue()
    }

    @Test
    fun `disabled hazard toggle makes points and parsers no-ops`() {
        val config = HazardConfig(toggleHazardSystem = false)
        val player = PlayerHazard(config = config, normalRandom = { _, _ -> 1 })
        player.setHazardSystemPoints(5)
        assertThat(player.getHazardSystemPoints()).isEqualTo(0)

        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDamageBoost(true) }
        val damage = HazardDamage(primaryValue = 100)
        player.parseAttackRecvHazardSystem(damage, monster)
        assertThat(damage.primaryValue).isEqualTo(100)
    }

    // ---- handleHazardSystemAttack dispatch -----------------------------------

    @Test
    fun `handle dispatches received attack and skips zero or non hazard`() {
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDamageBoost(true) }
        val player = PlayerHazard(normalRandom = { _, _ -> 1 })
        player.setHazardSystemPoints(5)

        val damage = HazardDamage(primaryValue = 100)
        HazardSystem.handleHazardSystemAttack(damage, player, monster, isPlayerAttacker = false)
        assertThat(damage.primaryValue).isEqualTo(110)

        val zero = HazardDamage(primaryValue = 0)
        HazardSystem.handleHazardSystemAttack(zero, player, monster, isPlayerAttacker = false)
        assertThat(zero.primaryValue).isEqualTo(0)

        val plainMonster = MonsterHazard()
        val untouched = HazardDamage(primaryValue = 100)
        HazardSystem.handleHazardSystemAttack(untouched, player, plainMonster, isPlayerAttacker = false)
        assertThat(untouched.primaryValue).isEqualTo(100)
    }

    // ---- Recv: damage boost --------------------------------------------------

    @Test
    fun `recv damage boost scales damage per hazard level`() {
        val expected = mapOf(
            1 to 102, 2 to 104, 3 to 106, 4 to 108, 5 to 110, 6 to 112,
            7 to 114, 8 to 116, 9 to 118, 10 to 120, 11 to 122, 12 to 124,
        )
        expected.forEach { (points, primary) ->
            val player = PlayerHazard(normalRandom = { _, _ -> 1 })
            player.setHazardSystemPoints(points)
            val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDamageBoost(true) }
            val damage = HazardDamage(primaryValue = 100, secondaryValue = 40)
            player.parseAttackRecvHazardSystem(damage, monster)
            assertThat(damage.primaryValue).`as`("points=$points").isEqualTo(primary)
            assertThat(damage.extension).isTrue()
            assertThat(damage.exString).isEqualTo("(Hazard)")
        }
    }

    @Test
    fun `recv damage boost ignores zero secondary`() {
        val player = PlayerHazard(normalRandom = { _, _ -> 1 })
        player.setHazardSystemPoints(5)
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDamageBoost(true) }
        val damage = HazardDamage(primaryValue = 100, secondaryValue = 0)
        player.parseAttackRecvHazardSystem(damage, monster)
        assertThat(damage.primaryValue).isEqualTo(110)
        assertThat(damage.secondaryValue).isEqualTo(0)
    }

    @Test
    fun `recv damage boost truncates stage to unsigned short`() {
        // 328 * 200 = 65600 -> uint16_t 64; ceil(100 * 64 / 10000) = 1.
        val player = PlayerHazard(normalRandom = { _, _ -> 1 })
        player.setHazardSystemPoints(328)
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDamageBoost(true) }
        val damage = HazardDamage(primaryValue = 100)
        player.parseAttackRecvHazardSystem(damage, monster)
        assertThat(damage.primaryValue).isEqualTo(101)
    }

    // ---- Recv: critical ------------------------------------------------------

    @Test
    fun `recv critical scales primary and secondary per hazard level`() {
        val expectedPrimary = mapOf(
            1 to 150, 2 to 151, 3 to 151, 4 to 151, 5 to 151, 6 to 152,
            7 to 152, 8 to 152, 9 to 152, 10 to 153, 11 to 153, 12 to 153,
        )
        expectedPrimary.forEach { (points, primary) ->
            val player = PlayerHazard(normalRandom = { _, _ -> 750 }, now = { 10000L })
            player.setHazardSystemPoints(points)
            val monster = MonsterHazard().apply { setHazard(true); setHazardSystemCrit(true) }
            val damage = HazardDamage(primaryValue = 100)
            player.parseAttackRecvHazardSystem(damage, monster)
            assertThat(damage.primaryValue).`as`("points=$points").isEqualTo(primary)
            assertThat(damage.critical).isTrue()
            assertThat(damage.extension).isTrue()
            assertThat(damage.exString).isEqualTo("(Hazard)")
            assertThat(player.getLastHazardSystemCriticalHit()).isEqualTo(10000L)
        }
    }

    @Test
    fun `recv critical boosts secondary too`() {
        val player = PlayerHazard(normalRandom = { _, _ -> 750 }, now = { 10000L })
        player.setHazardSystemPoints(5)
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemCrit(true) }
        val damage = HazardDamage(primaryValue = 100, secondaryValue = 50)
        player.parseAttackRecvHazardSystem(damage, monster)
        assertThat(damage.primaryValue).isEqualTo(151)
        assertThat(damage.secondaryValue).isEqualTo(76)
    }

    @Test
    fun `recv critical blocked inside the interval`() {
        val player = PlayerHazard(normalRandom = { _, _ -> 1 }, now = { 1999L })
        player.setHazardSystemPoints(5)
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemCrit(true) }
        val damage = HazardDamage(primaryValue = 100)
        player.parseAttackRecvHazardSystem(damage, monster)
        assertThat(damage.primaryValue).isEqualTo(100)
        assertThat(damage.critical).isFalse()
    }

    @Test
    fun `recv critical blocked when roll above chance`() {
        val player = PlayerHazard(normalRandom = { _, _ -> 751 }, now = { 10000L })
        player.setHazardSystemPoints(5)
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemCrit(true) }
        val damage = HazardDamage(primaryValue = 100)
        player.parseAttackRecvHazardSystem(damage, monster)
        assertThat(damage.primaryValue).isEqualTo(100)
    }

    @Test
    fun `recv critical blocked when damage already critical`() {
        val player = PlayerHazard(normalRandom = { _, _ -> 1 }, now = { 10000L })
        player.setHazardSystemPoints(5)
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemCrit(true) }
        val damage = HazardDamage(primaryValue = 100, critical = true)
        player.parseAttackRecvHazardSystem(damage, monster)
        assertThat(damage.primaryValue).isEqualTo(100)
    }

    @Test
    fun `recv applies critical before damage boost`() {
        val player = PlayerHazard(normalRandom = { _, _ -> 1 }, now = { 10000L })
        player.setHazardSystemPoints(12)
        val monster = MonsterHazard().apply {
            setHazard(true)
            setHazardSystemCrit(true)
            setHazardSystemDamageBoost(true)
        }
        val damage = HazardDamage(primaryValue = 100, secondaryValue = 50)
        player.parseAttackRecvHazardSystem(damage, monster)
        assertThat(damage.primaryValue).isEqualTo(190)
        assertThat(damage.secondaryValue).isEqualTo(96)
    }

    @Test
    fun `recv ignores healing combat`() {
        val player = PlayerHazard(normalRandom = { _, _ -> 1 })
        player.setHazardSystemPoints(5)
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDamageBoost(true) }
        val damage = HazardDamage(primaryType = CombatType.COMBAT_HEALING, primaryValue = 100)
        player.parseAttackRecvHazardSystem(damage, monster)
        assertThat(damage.primaryValue).isEqualTo(100)
        assertThat(damage.extension).isFalse()
    }

    @Test
    fun `recv uses lowest party hazard points`() {
        val player = PlayerHazard(
            normalRandom = { _, _ -> 1 },
            partyPoints = { listOf(3, 10) },
            partyLeaderPoints = { 2 },
        )
        player.setHazardSystemPoints(5)
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDamageBoost(true) }
        val damage = HazardDamage(primaryValue = 100)
        player.parseAttackRecvHazardSystem(damage, monster)
        // stage = 2 * 200 = 400 -> ceil(100 * 400 / 10000) = 4.
        assertThat(damage.primaryValue).isEqualTo(104)
    }

    // ---- Dealt: dodge --------------------------------------------------------

    @Test
    fun `dealt dodge zeroes damage when roll within stage`() {
        val player = PlayerHazard(normalRandom = { _, _ -> 425 })
        player.setHazardSystemPoints(5)
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDodge(true) }
        val damage = HazardDamage(primaryValue = 100, secondaryValue = 40)
        player.parseAttackDealtHazardSystem(damage, monster)
        assertThat(damage.primaryValue).isEqualTo(0)
        assertThat(damage.secondaryValue).isEqualTo(0)
        assertThat(damage.hazardDodge).isTrue()
    }

    @Test
    fun `dealt dodge not triggered when roll above stage`() {
        val player = PlayerHazard(normalRandom = { _, _ -> 426 })
        player.setHazardSystemPoints(5)
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDodge(true) }
        val damage = HazardDamage(primaryValue = 100)
        player.parseAttackDealtHazardSystem(damage, monster)
        assertThat(damage.primaryValue).isEqualTo(100)
        assertThat(damage.hazardDodge).isFalse()
    }

    @Test
    fun `dealt dodge stage truncates to unsigned short`() {
        // 772 * 85 = 65620 -> uint16_t 84.
        val player = PlayerHazard(normalRandom = { _, _ -> 84 })
        player.setHazardSystemPoints(772)
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDodge(true) }
        val damage = HazardDamage(primaryValue = 100)
        player.parseAttackDealtHazardSystem(damage, monster)
        assertThat(damage.hazardDodge).isTrue()

        val missPlayer = PlayerHazard(normalRandom = { _, _ -> 85 })
        missPlayer.setHazardSystemPoints(772)
        val missDamage = HazardDamage(primaryValue = 100)
        missPlayer.parseAttackDealtHazardSystem(missDamage, monster)
        assertThat(missDamage.hazardDodge).isFalse()
    }

    @Test
    fun `handle dispatches dealt attack dodge`() {
        val player = PlayerHazard(normalRandom = { _, _ -> 1 })
        player.setHazardSystemPoints(5)
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDodge(true) }
        val damage = HazardDamage(primaryValue = 100)
        HazardSystem.handleHazardSystemAttack(damage, player, monster, isPlayerAttacker = true)
        assertThat(damage.hazardDodge).isTrue()
        assertThat(damage.primaryValue).isEqualTo(0)
    }

    // ---- Dealt: defense boost ------------------------------------------------

    @Test
    fun `dealt defense boost reduces damage`() {
        val player = PlayerHazard(config = HazardConfig(defenseMultiplier = 500), normalRandom = { _, _ -> 1 })
        player.setHazardSystemPoints(5)
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDefenseBoost(true) }
        val damage = HazardDamage(primaryValue = 100, secondaryValue = 50)
        player.parseAttackDealtHazardSystem(damage, monster)
        assertThat(damage.primaryValue).isEqualTo(75)
        assertThat(damage.secondaryValue).isEqualTo(37)
        assertThat(damage.exString).isEqualTo("(hazard -25%)")
    }

    @Test
    fun `dealt defense boost with zero multiplier is a no-op`() {
        val player = PlayerHazard(normalRandom = { _, _ -> 1 })
        player.setHazardSystemPoints(5)
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDefenseBoost(true) }
        val damage = HazardDamage(primaryValue = 100)
        player.parseAttackDealtHazardSystem(damage, monster)
        assertThat(damage.primaryValue).isEqualTo(100)
        assertThat(damage.exString).isEqualTo("")
    }

    @Test
    fun `dealt ignores healing combat`() {
        val player = PlayerHazard(config = HazardConfig(defenseMultiplier = 500), normalRandom = { _, _ -> 1 })
        player.setHazardSystemPoints(5)
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDefenseBoost(true) }
        val damage = HazardDamage(primaryType = CombatType.COMBAT_HEALING, primaryValue = 100)
        player.parseAttackDealtHazardSystem(damage, monster)
        assertThat(damage.primaryValue).isEqualTo(100)
    }

    @Test
    fun `dealt uses lowest party hazard points`() {
        val player = PlayerHazard(
            config = HazardConfig(defenseMultiplier = 500),
            normalRandom = { _, _ -> 1 },
            partyPoints = { listOf(3) },
        )
        player.setHazardSystemPoints(5)
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDefenseBoost(true) }
        val damage = HazardDamage(primaryValue = 100)
        player.parseAttackDealtHazardSystem(damage, monster)
        // stage = 3 * 500 = 1500 -> ceil(100 * 1500 / 10000) = 15.
        assertThat(damage.primaryValue).isEqualTo(85)
        assertThat(damage.exString).isEqualTo("(hazard -15%)")
    }

    // ---- Experience bonus ----------------------------------------------------

    @Test
    fun `hazard experience bonus scales with points`() {
        val monster = MonsterHazard().apply { setHazard(true) }

        val player5 = PlayerHazard()
        player5.setHazardSystemPoints(5)
        assertThat(player5.hazardExperience(1000L, monster)).isEqualTo(1175L)

        val player12 = PlayerHazard()
        player12.setHazardSystemPoints(12)
        assertThat(player12.hazardExperience(1000L, monster)).isEqualTo(1420L)
    }

    @Test
    fun `hazard experience bonus requires hazard monster and points`() {
        val plainMonster = MonsterHazard()
        val player = PlayerHazard()
        player.setHazardSystemPoints(5)
        assertThat(player.hazardExperience(1000L, plainMonster)).isEqualTo(1000L)

        val hazardMonster = MonsterHazard().apply { setHazard(true) }
        val noPoints = PlayerHazard()
        assertThat(noPoints.hazardExperience(1000L, hazardMonster)).isEqualTo(1000L)
    }

    // ---- Seeded RNG ----------------------------------------------------------

    @Test
    fun `seeded normal random is deterministic and in range`() {
        val first = PlayerHazard.defaultNormalRandom(Random(1234))
        val second = PlayerHazard.defaultNormalRandom(Random(1234))

        val firstRolls = List(50) { first(1, 10000) }
        val secondRolls = List(50) { second(1, 10000) }

        assertThat(firstRolls).isEqualTo(secondRolls)
        assertThat(firstRolls).allMatch { it in 1..10000 }
    }
}
