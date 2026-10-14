package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatDamage
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.wheel.WheelStage
import org.opentibia.canary.systems.wheel.WheelStat

/**
 * Transcript tests for the Wheel of Destiny combat helpers:
 *
 *  - `Game::applyWheelOfDestinyHealing` (game.cpp:8264)
 *  - `Game::applyWheelOfDestinyEffectsToDamage` (game.cpp:8287)
 *  - `Game::getIOWheel` (game.cpp:12347)
 *
 * plus the `PlayerWheel::` accessors they call (player_wheel.cpp:3172-3241,
 * 3546-3985). The healing link drives the real `Game::combatChangeHealth`
 * (game.cpp:8344) state change (see `GameCombatPipelineTest`).
 */
class GameWheelCombatTest {

    private val center = Position(1000, 1000, 7)

    private fun newGame(): Game = Game(GameMap(), GameDispatcher())

    private fun Game.player(id: Long, name: String): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = center).also { addPlayer(it) }

    private fun Game.monster(id: Long, health: Int, maxHealth: Int): Monster =
        Monster(id = id, name = "Target", maxHealth = maxHealth, health = health, position = Position(1001, 1000, 7))

    // ----- applyWheelOfDestinyHealing -----

    @Test
    fun `applyWheelOfDestinyHealing adds the healing multiplier percentage`() {
        val game = newGame()

        val damage = CombatDamage(primaryValue = 100, healingMultiplier = 50)
        game.applyWheelOfDestinyHealing(damage, null, null)

        assertThat(damage.primaryValue).isEqualTo(150)
    }

    @Test
    fun `applyWheelOfDestinyHealing truncates the multiplier like the C++ int addition`() {
        val game = newGame()

        val damage = CombatDamage(primaryValue = 101, healingMultiplier = 33)
        game.applyWheelOfDestinyHealing(damage, null, null)

        assertThat(damage.primaryValue).isEqualTo(134)
    }

    @Test
    fun `applyWheelOfDestinyHealing adds the wheel healing stat to both values`() {
        val game = newGame()
        val player = game.player(1, "Druid")
        player.wheel().addStat(WheelStat.HEALING, 25)

        val damage = CombatDamage(primaryValue = 100, secondaryValue = 40)
        game.applyWheelOfDestinyHealing(damage, player, null)

        assertThat(damage.primaryValue).isEqualTo(125)
        assertThat(damage.secondaryValue).isEqualTo(65)
    }

    @Test
    fun `applyWheelOfDestinyHealing leaves a zero secondary value at zero`() {
        val game = newGame()
        val player = game.player(1, "Druid")
        player.wheel().addStat(WheelStat.HEALING, 25)

        val damage = CombatDamage(primaryValue = 100)
        game.applyWheelOfDestinyHealing(damage, player, null)

        assertThat(damage.secondaryValue).isZero()
    }

    @Test
    fun `applyWheelOfDestinyHealing applies the healing link as a self combat heal`() {
        val game = newGame()
        val player = game.player(1, "Druid")
        player.health = 50

        val damage = CombatDamage(primaryValue = 100, healingLink = 10)
        game.applyWheelOfDestinyHealing(damage, player, null)

        assertThat(player.health).isEqualTo(60)
    }

    @Test
    fun `applyWheelOfDestinyHealing ignores a zero healing link`() {
        val game = newGame()
        val player = game.player(1, "Druid")
        player.health = 50

        val damage = CombatDamage(primaryValue = 100)
        game.applyWheelOfDestinyHealing(damage, player, null)

        assertThat(player.health).isEqualTo(50)
    }

    @Test
    fun `applyWheelOfDestinyHealing adds the Blessing of the Grove bonus by target health`() {
        val game = newGame()
        val player = game.player(1, "Druid")
        val target = game.monster(2, health = 20, maxHealth = 100)
        player.wheel().setStage(WheelStage.BLESSING_OF_THE_GROVE, 3)

        val damage = CombatDamage(primaryValue = 100)
        game.applyWheelOfDestinyHealing(damage, player, target)

        assertThat(damage.primaryValue).isEqualTo(124)
    }

    @Test
    fun `applyWheelOfDestinyHealing skips the Blessing of the Grove bonus on self`() {
        val game = newGame()
        val player = game.player(1, "Druid")
        player.health = 20
        player.wheel().setStage(WheelStage.BLESSING_OF_THE_GROVE, 3)

        val damage = CombatDamage(primaryValue = 100)
        game.applyWheelOfDestinyHealing(damage, player, player)

        assertThat(damage.primaryValue).isEqualTo(100)
    }

    @Test
    fun `applyWheelOfDestinyHealing skips the grove bonus without the stage`() {
        val game = newGame()
        val player = game.player(1, "Druid")
        val target = game.monster(2, health = 20, maxHealth = 100)

        val damage = CombatDamage(primaryValue = 100)
        game.applyWheelOfDestinyHealing(damage, player, target)

        assertThat(damage.primaryValue).isEqualTo(100)
    }

    // ----- applyWheelOfDestinyEffectsToDamage -----

    @Test
    fun `applyWheelOfDestinyEffectsToDamage returns early on a fully empty hit`() {
        val game = newGame()
        val player = game.player(1, "Knight")

        val damage = CombatDamage(damageMultiplier = 50)
        game.applyWheelOfDestinyEffectsToDamage(damage, player, null)

        assertThat(damage.primaryValue).isZero()
        assertThat(damage.secondaryValue).isZero()
    }

    @Test
    fun `applyWheelOfDestinyEffectsToDamage applies the damage multiplier to both values`() {
        val game = newGame()
        val player = game.player(1, "Knight")

        val damage = CombatDamage(primaryValue = 100, secondaryValue = 50, damageMultiplier = 10)
        game.applyWheelOfDestinyEffectsToDamage(damage, player, null)

        assertThat(damage.primaryValue).isEqualTo(110)
        assertThat(damage.secondaryValue).isEqualTo(55)
    }

    @Test
    fun `applyWheelOfDestinyEffectsToDamage subtracts the wheel damage stat`() {
        val game = newGame()
        val player = game.player(1, "Knight")
        player.wheel().addStat(WheelStat.DAMAGE, 10)

        val damage = CombatDamage(primaryValue = 100, secondaryValue = 50)
        game.applyWheelOfDestinyEffectsToDamage(damage, player, null)

        assertThat(damage.primaryValue).isEqualTo(90)
        assertThat(damage.secondaryValue).isEqualTo(40)
    }

    @Test
    fun `applyWheelOfDestinyEffectsToDamage leaves a zero secondary at zero after the stat`() {
        val game = newGame()
        val player = game.player(1, "Knight")
        player.wheel().addStat(WheelStat.DAMAGE, 10)

        val damage = CombatDamage(primaryValue = 100)
        game.applyWheelOfDestinyEffectsToDamage(damage, player, null)

        assertThat(damage.secondaryValue).isZero()
    }

    @Test
    fun `applyWheelOfDestinyEffectsToDamage applies Twin Burst on Ice and Terra Burst`() {
        val ice = newGame()
        val icePlayer = ice.player(1, "Druid")
        val iceTarget = ice.monster(2, health = 80, maxHealth = 100)
        icePlayer.wheel().setStage(WheelStage.TWIN_BURST, 3)

        val iceDamage = CombatDamage(primaryValue = 100, secondaryValue = 50, instantSpellName = "Ice Burst")
        ice.applyWheelOfDestinyEffectsToDamage(iceDamage, icePlayer, iceTarget)

        val terra = newGame()
        val terraPlayer = terra.player(1, "Druid")
        val terraTarget = terra.monster(2, health = 80, maxHealth = 100)
        terraPlayer.wheel().setStage(WheelStage.TWIN_BURST, 3)

        val terraDamage = CombatDamage(primaryValue = 100, instantSpellName = "Terra Burst")
        terra.applyWheelOfDestinyEffectsToDamage(terraDamage, terraPlayer, terraTarget)

        assertThat(iceDamage.primaryValue).isEqualTo(160)
        assertThat(iceDamage.secondaryValue).isEqualTo(80)
        assertThat(terraDamage.primaryValue).isEqualTo(160)
    }

    @Test
    fun `applyWheelOfDestinyEffectsToDamage skips Twin Burst on other spells`() {
        val game = newGame()
        val player = game.player(1, "Druid")
        val target = game.monster(2, health = 80, maxHealth = 100)
        player.wheel().setStage(WheelStage.TWIN_BURST, 3)

        val damage = CombatDamage(primaryValue = 100, instantSpellName = "Fire Ball")
        game.applyWheelOfDestinyEffectsToDamage(damage, player, target)

        assertThat(damage.primaryValue).isEqualTo(100)
    }

    @Test
    fun `applyWheelOfDestinyEffectsToDamage applies Executioner's Throw below thirty percent`() {
        val game = newGame()
        val player = game.player(1, "Knight")
        val target = game.monster(2, health = 20, maxHealth = 100)
        player.wheel().setStage(WheelStage.EXECUTIONERS_THROW, 3)

        val damage = CombatDamage(primaryValue = 100, instantSpellName = "Executioner's Throw")
        game.applyWheelOfDestinyEffectsToDamage(damage, player, target)

        assertThat(damage.primaryValue).isEqualTo(250)
    }

    @Test
    fun `applyWheelOfDestinyEffectsToDamage applies Divine Grenade at full stage`() {
        val game = newGame()
        val player = game.player(1, "Paladin")
        val target = game.monster(2, health = 90, maxHealth = 100)
        player.wheel().setStage(WheelStage.DIVINE_GRENADE, 3)

        val damage = CombatDamage(primaryValue = 100, instantSpellName = "Divine Grenade")
        game.applyWheelOfDestinyEffectsToDamage(damage, player, target)

        assertThat(damage.primaryValue).isEqualTo(200)
    }

    @Test
    fun `applyWheelOfDestinyEffectsToDamage ignores the spell bonuses without an attacker`() {
        val game = newGame()

        val damage = CombatDamage(primaryValue = 100, instantSpellName = "Ice Burst", damageMultiplier = 10)
        game.applyWheelOfDestinyEffectsToDamage(damage, null, null)

        assertThat(damage.primaryValue).isEqualTo(110)
    }

    @Test
    fun `applyWheelOfDestinyEffectsToDamage combines the multiplier and the wheel damage stat`() {
        val game = newGame()
        val player = game.player(1, "Knight")
        player.wheel().addStat(WheelStat.DAMAGE, 10)
        val target = game.monster(2, health = 20, maxHealth = 100)
        player.wheel().setStage(WheelStage.EXECUTIONERS_THROW, 3)

        val damage = CombatDamage(primaryValue = 100, instantSpellName = "Executioner's Throw", damageMultiplier = 10)
        game.applyWheelOfDestinyEffectsToDamage(damage, player, target)

        assertThat(damage.primaryValue).isEqualTo(250)
    }

    // ----- getIOWheel -----

    @Test
    fun `getIOWheel exposes the owned deterministic wheel engine`() {
        val game = newGame()
        val player = game.player(1, "Knight")

        assertThat(game.getIOWheel()).isSameAs(game.getIOWheel())
        assertThat(game.getIOWheel().buildBonusData(player.wheel()).stats.health).isZero()
    }
}
