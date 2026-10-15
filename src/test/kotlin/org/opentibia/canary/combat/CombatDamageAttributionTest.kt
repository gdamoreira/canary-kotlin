package org.opentibia.canary.combat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.MonsterDeathHost
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.death.CreatureDeathPipeline
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import java.util.Random

/**
 * Transcript tests for the damage-attribution chain:
 *
 * - `Combat::CombatHealthFunc` -> `Game::combatChangeHealth` ->
 *   `Creature::drainHealth(attacker, damage)` (combat.cpp:911, game.cpp:8735);
 * - `Creature::onAttackedCreatureDrainHealth` (creature.cpp:1277-1283) ->
 *   `Creature::addDamagePoints` (creature.cpp:1195-1211), so the wave-70 death
 *   pipeline (`CreatureDeathPipeline`, creature.cpp:579-720) resolves the
 *   last hit and the most damage from combat kills;
 * - the summon/master experience chain (`Creature::onGainExperience`,
 *   creature.cpp:1308-1328): a summon's kill credits the summon's damage map
 *   entry and forwards half of the lost experience to its master.
 */
class CombatDamageAttributionTest {

    private val center = Position(1000, 1000, 7)

    private fun monster(id: Long, health: Int, position: Position = center, maxHealth: Int = 100): Monster =
        Monster(id = id, name = "Target$id", maxHealth = maxHealth, health = health, position = position)

    private fun damage(value: Int): CombatDamage =
        CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = value)

    private fun params(): CombatParams = CombatParams(combatType = CombatType.PHYSICALDAMAGE)

    // --- Combat::CombatHealthFunc -> Creature::drainHealth(attacker, damage) ---

    @Test
    fun `combat damage records the caster as the attacker on the target damage map`() {
        val attacker = monster(10, health = 100)
        val target = monster(20, health = 100)

        Combat.doCombatHealth(
            caster = CreatureCombatTarget(attacker),
            target = CreatureCombatTarget(target),
            damage = damage(-30),
            params = params(),
            random = Random(1),
        )

        assertThat(target.health).isEqualTo(70)
        assertThat(target.getDamageMap()[10L]?.total).isEqualTo(30)
        assertThat(target.lastHitCreatureId).isEqualTo(10L)
    }

    @Test
    fun `a casterless combat hit stays unattributed`() {
        val target = monster(20, health = 100)

        Combat.doCombatHealth(
            caster = null,
            target = CreatureCombatTarget(target),
            damage = damage(-30),
            params = params(),
            random = Random(1),
        )

        assertThat(target.health).isEqualTo(70)
        assertThat(target.getDamageMap()).isEmpty()
        assertThat(target.lastHitCreatureId).isZero()
    }

    @Test
    fun `combat damage accumulates per attacker and stamps the last hit`() {
        val first = monster(10, health = 100)
        val second = monster(11, health = 100)
        val target = monster(20, health = 100)

        Combat.doCombatHealth(CreatureCombatTarget(first), CreatureCombatTarget(target), damage(-30), params(), Random(1))
        Combat.doCombatHealth(CreatureCombatTarget(second), CreatureCombatTarget(target), damage(-20), params(), Random(1))

        assertThat(target.getDamageMap()[10L]?.total).isEqualTo(30)
        assertThat(target.getDamageMap()[11L]?.total).isEqualTo(20)
        assertThat(target.lastHitCreatureId).isEqualTo(11L)
    }

    // --- Creature::drainHealth(attacker, damage) ordering ---

    @Test
    fun `a lethal drain credits the attacker before the death resolves the damage map`() {
        // Canary queues `onDeath` through the dispatcher (creature.cpp:861-875),
        // so the credit of `Creature::drainHealth` (creature.cpp:893-899) lands
        // before the death body reads the damage map. The port runs the death
        // synchronously, so the credit must run first to keep the last hit.
        val attacker = monster(10, health = 100)
        val target = monster(20, health = 30)
        val payloads = mutableListOf<DeathPayload>()
        target.captureDeath(payloads)
        target.deathPipeline = CreatureDeathPipeline(
            resolveCreature = { id -> mapOf(10L to attacker, 20L to target)[id] },
        )

        target.drainHealth(attacker, 30)

        assertThat(target.health).isZero()
        assertThat(target.getDamageMap()[10L]?.total).isEqualTo(30)
        assertThat(payloads.single().killer).isSameAs(attacker)
        assertThat(payloads.single().mostDamageKiller).isSameAs(attacker)
    }

    // --- Game::combatChangeHealth end-to-end ---

    private class DeathPayload(val killer: Creature?, val mostDamageKiller: Creature?)

    private fun Monster.captureDeath(payloads: MutableList<DeathPayload>) {
        onDeathEventsHost = { _, _, killer, mostDamage, _, _ -> payloads.add(DeathPayload(killer, mostDamage)) }
    }

    private fun newGame(): Game = Game(GameMap(), GameDispatcher())

    private fun Game.placePlayer(id: Long, name: String, position: Position): Player {
        map.getOrCreateTile(position).ground = Item(id = 100)
        return Player(id = id, name = name, maxHealth = 150, health = 150, position = position).also {
            addPlayer(it)
            internalPlaceCreature(it, position)
        }
    }

    private fun Game.placeMonster(id: Long, health: Int = 100, position: Position): Monster {
        map.getOrCreateTile(position).ground = Item(id = 100)
        return monster(id, health, position).also { internalPlaceCreature(it, position) }
    }

    @Test
    fun `a combat kill resolves the last hit and the biggest damage into the death payload`() {
        val game = newGame()
        val topDamage = game.placePlayer(10, "Top", Position(998, 1000, 7))
        val lastHit = game.placePlayer(11, "Last", Position(999, 1000, 7))
        val target = game.placeMonster(20, position = Position(1001, 1000, 7))
        val payloads = mutableListOf<DeathPayload>()
        target.captureDeath(payloads)

        assertThat(game.getCreatureByID(10L)).isSameAs(topDamage)
        assertThat(game.getCreatureByID(11L)).isSameAs(lastHit)
        assertThat(game.getCreatureByID(20L)).isSameAs(target)
        assertThat(target.deathPipeline).isNotNull()

        assertThat(game.combatChangeHealth(topDamage, target, damage(-70))).isTrue()
        assertThat(game.combatChangeHealth(lastHit, target, damage(-40))).isTrue()

        assertThat(target.health).isZero()
        assertThat(target.getDamageMap()[10L]?.total).isEqualTo(70)
        // game.cpp:8716-8735: the credit is the real damage, clamped to the health.
        assertThat(target.getDamageMap()[11L]?.total).isEqualTo(30)
        assertThat(target.lastHitCreatureId).isEqualTo(11L)
        assertThat(payloads).hasSize(1)
        assertThat(payloads.single().killer).isSameAs(lastHit)
        assertThat(payloads.single().mostDamageKiller).isSameAs(topDamage)
    }

    @Test
    fun `a summon's killing blow credits the summon and forwards half its experience`() {
        val game = newGame()
        val master = game.placePlayer(10, "Master", Position(998, 1000, 7))
        val summon = game.placeMonster(30, position = Position(1002, 1000, 7))
        summon.setMaster(master)
        val target = game.placeMonster(20, position = Position(1001, 1000, 7))
        target.monsterDeathHost = MonsterDeathHost(experience = 100)
        val payloads = mutableListOf<DeathPayload>()
        target.captureDeath(payloads)

        game.combatChangeHealth(summon, target, damage(-100))

        assertThat(target.getDamageMap()[30L]?.total).isEqualTo(100)
        assertThat(payloads.single().killer).isSameAs(summon)
        // creature.cpp:659-660: the most damage master replaces the summon.
        assertThat(payloads.single().mostDamageKiller).isSameAs(master)
        // creature.cpp:1308-1328: a non-familiar summon forwards half the gain.
        assertThat(master.experience).isEqualTo(50L)
    }

    @Test
    fun `an environmental hit stays unattributed and resolves no killer`() {
        val game = newGame()
        val target = game.placeMonster(20, position = Position(1001, 1000, 7))
        val payloads = mutableListOf<DeathPayload>()
        target.captureDeath(payloads)

        assertThat(game.combatChangeHealth(null, target, damage(-120))).isTrue()

        assertThat(target.getDamageMap()).isEmpty()
        assertThat(target.lastHitCreatureId).isZero()
        assertThat(payloads.single().killer).isNull()
        assertThat(payloads.single().mostDamageKiller).isNull()
    }
}
