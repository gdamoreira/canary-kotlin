package org.opentibia.canary.systems.hazard

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatDamage
import org.opentibia.canary.combat.CombatOrigin
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.config.FeatureFlags

/**
 * Integration tests connecting `handleHazardSystemAttack`
 * (src/game/game.cpp:8193-8201) to the faithful `CombatDamage` core instead of
 * the plain-integer [HazardDamage] mirror.
 */
class HazardCombatDamageTest {

    @Test
    fun `handle mutates combat damage for a received hazard boost`() {
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDamageBoost(true) }
        val player = PlayerHazard(normalRandom = { _, _ -> 1 })
        player.setHazardSystemPoints(5)

        val damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = 100)
        HazardSystem.handleHazardSystemAttack(damage, player, monster, isPlayerAttacker = false)

        assertThat(damage.primaryValue).isEqualTo(110)
        assertThat(damage.extension).isTrue()
        assertThat(damage.exString).isEqualTo("(Hazard)")
    }

    @Test
    fun `handle leaves healing damage untouched`() {
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDamageBoost(true) }
        val player = PlayerHazard(normalRandom = { _, _ -> 1 })
        player.setHazardSystemPoints(5)

        val damage = CombatDamage(primaryType = CombatType.HEALING, primaryValue = 100)
        HazardSystem.handleHazardSystemAttack(damage, player, monster, isPlayerAttacker = false)

        assertThat(damage.primaryValue).isEqualTo(100)
        assertThat(damage.extension).isFalse()
    }

    @Test
    fun `handle applies the dealt dodge onto combat damage`() {
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDodge(true) }
        val player = PlayerHazard(normalRandom = { _, _ -> 1 })
        player.setHazardSystemPoints(5)

        val damage = CombatDamage(
            primaryType = CombatType.PHYSICALDAMAGE,
            primaryValue = 100,
            secondaryValue = 40,
            origin = CombatOrigin.MELEE,
        )
        HazardSystem.handleHazardSystemAttack(damage, player, monster, isPlayerAttacker = true)

        assertThat(damage.primaryValue).isEqualTo(0)
        assertThat(damage.secondaryValue).isEqualTo(0)
        assertThat(damage.hazardDodge).isTrue()
    }

    @Test
    fun `handle applies the received critical onto combat damage`() {
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemCrit(true) }
        val player = PlayerHazard(normalRandom = { _, _ -> 750 }, now = { 10000L })
        player.setHazardSystemPoints(5)

        val damage = CombatDamage(
            primaryType = CombatType.PHYSICALDAMAGE,
            primaryValue = 100,
            secondaryValue = 50,
        )
        HazardSystem.handleHazardSystemAttack(damage, player, monster, isPlayerAttacker = false)

        assertThat(damage.primaryValue).isEqualTo(151)
        assertThat(damage.secondaryValue).isEqualTo(76)
        assertThat(damage.critical).isTrue()
        assertThat(damage.extension).isTrue()
        assertThat(damage.exString).isEqualTo("(Hazard)")
        assertThat(player.getLastHazardSystemCriticalHit()).isEqualTo(10000L)
    }

    @Test
    fun `feature flags configure the hazard damage multiplier`() {
        val config = HazardConfig.fromFeatureFlags(FeatureFlags(hazardDamageMultiplier = 1000))
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDamageBoost(true) }
        val player = PlayerHazard(config = config, normalRandom = { _, _ -> 1 })
        player.setHazardSystemPoints(5)

        val damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = 100)
        HazardSystem.handleHazardSystemAttack(damage, player, monster, isPlayerAttacker = false)

        assertThat(damage.primaryValue).isEqualTo(150)
    }

    @Test
    fun `toggle hazard system feature flag disables the hazard math`() {
        val config = HazardConfig.fromFeatureFlags(FeatureFlags(toggleHazardSystem = false))
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDamageBoost(true) }
        val player = PlayerHazard(config = config, normalRandom = { _, _ -> 1 })
        player.setHazardSystemPoints(5)

        val damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = 100)
        HazardSystem.handleHazardSystemAttack(damage, player, monster, isPlayerAttacker = false)

        assertThat(damage.primaryValue).isEqualTo(100)
        assertThat(damage.extension).isFalse()
    }

    @Test
    fun `handle skips zero damage and non hazard monsters`() {
        val monster = MonsterHazard().apply { setHazard(true); setHazardSystemDamageBoost(true) }
        val player = PlayerHazard(normalRandom = { _, _ -> 1 })
        player.setHazardSystemPoints(5)

        val zero = CombatDamage(primaryValue = 0)
        HazardSystem.handleHazardSystemAttack(zero, player, monster, isPlayerAttacker = false)
        assertThat(zero.isEmpty()).isTrue()

        val plain = MonsterHazard()
        val untouched = CombatDamage(primaryValue = 100)
        HazardSystem.handleHazardSystemAttack(untouched, player, plain, isPlayerAttacker = false)
        assertThat(untouched.primaryValue).isEqualTo(100)
    }
}
