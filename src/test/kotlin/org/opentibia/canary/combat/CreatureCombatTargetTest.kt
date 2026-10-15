package org.opentibia.canary.combat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Buff
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.monsters.Faction
import org.opentibia.canary.creatures.monsters.MonsterPipelinesHost
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.monsters.MonsterTypeScriptInterface
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.SpectatorSector
import org.opentibia.canary.map.Spectators
import java.util.Random

/**
 * Transcript tests for the production [CreatureCombatTarget] adapter and the
 * game-hosted `Game.combatTargetOf` factory:
 *
 *  - `Combat::CombatHealthFunc`'s player-attacking-monster branch
 *    (src/creatures/combat/combat.cpp:895-899) reached through `Game`'s
 *    [CombatTargetState] binding fires the monster type's captured
 *    `onPlayerAttack` event (`Monster::onAttackedByPlayer`, monster.cpp:600-625);
 *  - `Creature::getBuff`/`Player::getMantra` drive `Combat::applyMantraAbsorb`
 *    (combat.cpp:739-759).
 */
class CreatureCombatTargetTest {

    private val center = Position(1000, 1000, 7)
    private lateinit var service: MonsterComputeService

    @BeforeEach
    fun setUp() {
        Spectators.clearCache()
        service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
    }

    private class RecordingScriptInterface : MonsterTypeScriptInterface {
        var eventId: Int = 0
        val calls: MutableList<Pair<Int, List<Any?>>> = mutableListOf()

        override fun getEvent(): Int = eventId

        override fun callEvent(eventId: Int, arguments: List<Any?>): Boolean {
            calls.add(eventId to arguments)
            return true
        }
    }

    private fun newHost(): MonsterPipelinesHost = MonsterPipelinesHost(
        service = service,
        mapAccess = SpectatorMapAccess { _, _ -> SpectatorSector() },
        resolveCreature = { null },
        clock = { 0L },
    )

    private fun newGame(host: MonsterPipelinesHost, type: MonsterType): Game {
        Spectators.clearCache()
        return Game(
            GameMap(),
            GameDispatcher(),
            monsterHost = host,
            monsterTypeProvider = { name -> if (name.equals("Rat", ignoreCase = true)) type else null },
        )
    }

    private fun Game.placeMonster(monster: Monster): Game {
        map.getOrCreateTile(center).ground = Item(id = 100)
        internalPlaceCreature(monster, center)
        return this
    }

    private fun player(): Player =
        Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(999, 1000, 7))

    private fun monster(id: Long = 7): Monster =
        Monster(id = id, name = "Rat", maxHealth = 100, health = 100, position = center)

    @Test
    fun `a player spell hit on a monster fires the production onPlayerAttack event`() {
        val host = newHost()
        val scripts = RecordingScriptInterface().apply { eventId = 7 }
        val type = MonsterType("Rat").apply {
            info.scriptInterface = scripts
            info.monsterAttackedByPlayerEvent = 7
        }
        val game = newGame(host, type)
        val caster = player()
        val target = monster()
        game.placeMonster(target)

        Combat.doCombatHealth(
            caster = game.combatTargetOf(caster),
            target = game.combatTargetOf(target),
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -50),
            params = CombatParams(combatType = CombatType.PHYSICALDAMAGE),
            random = Random(1),
        )

        assertThat(scripts.calls).hasSize(1)
        assertThat(scripts.calls[0].first).isEqualTo(7)
        assertThat(scripts.calls[0].second).containsExactly(target, caster)
        assertThat(target.health).isEqualTo(50)
    }

    @Test
    fun `a fully blocked spell hit still fires the production onPlayerAttack event`() {
        // combat.cpp:895-899: the hook runs before `Game::combatBlockHit`.
        val host = newHost()
        val scripts = RecordingScriptInterface().apply { eventId = 7 }
        val type = MonsterType("Rat").apply {
            info.scriptInterface = scripts
            info.monsterAttackedByPlayerEvent = 7
        }
        val game = newGame(host, type)
        val target = monster()
        game.placeMonster(target)
        game.getArmor = { 1000 }

        Combat.doCombatHealth(
            caster = game.combatTargetOf(player()),
            target = game.combatTargetOf(target),
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -50),
            params = CombatParams(combatType = CombatType.PHYSICALDAMAGE, blockedByArmor = true),
            random = Random(1),
        )

        assertThat(scripts.calls).hasSize(1)
        assertThat(target.health).isEqualTo(100)
    }

    @Test
    fun `a monster caster does not fire the production onPlayerAttack event`() {
        val host = newHost()
        val scripts = RecordingScriptInterface().apply { eventId = 7 }
        val type = MonsterType("Rat").apply {
            info.scriptInterface = scripts
            info.monsterAttackedByPlayerEvent = 7
        }
        val game = newGame(host, type)
        val attacker = monster(id = 8)
        val target = monster(id = 7)
        game.placeMonster(target)

        Combat.doCombatHealth(
            caster = game.combatTargetOf(attacker),
            target = game.combatTargetOf(target),
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -50),
            params = CombatParams(combatType = CombatType.PHYSICALDAMAGE),
            random = Random(1),
        )

        assertThat(scripts.calls).isEmpty()
    }

    @Test
    fun `the adapter exposes the buff driven mantra absorb`() {
        val target = Player(id = 2, name = "Target", maxHealth = 150, health = 150, position = center)
        val adapter = CreatureCombatTarget(target)
        target.setBuff(Buff.BUFF_MANTRA, 100)

        // mantra = getMantra() + (getBuff(BUFF_MANTRA) - 100) = 100.
        assertThat(Combat.applyMantraAbsorb(adapter, CombatType.FIREDAMAGE, -50)).isEqualTo(0)
        assertThat(Combat.applyMantraAbsorb(adapter, CombatType.FIREDAMAGE, -10)).isEqualTo(0)
        assertThat(Combat.applyMantraAbsorb(adapter, CombatType.PHYSICALDAMAGE, -50)).isEqualTo(-50)
    }

    @Test
    fun `the adapter resolves the mantra from the equipped armor slots`() {
        // `Player::getMantra` (player.cpp:671-694) sums the seven armor slots.
        val target = Player(id = 2, name = "Target", maxHealth = 150, health = 150, position = center)
        val ring = Item(id = 2400, type = ItemType(id = 2400, name = "mantra ring", mantra = 40))
        target.internalAddThing(Slots_t.CONST_SLOT_RING.value, ring)
        val adapter = CreatureCombatTarget(target)

        assertThat(adapter.getMantra()).isEqualTo(40)
        // `Combat::applyMantraAbsorb` (combat.cpp:739-759): value + min(mantra, -value).
        assertThat(Combat.applyMantraAbsorb(adapter, CombatType.FIREDAMAGE, -50)).isEqualTo(-10)
        assertThat(Combat.applyMantraAbsorb(adapter, CombatType.ICEDAMAGE, -30)).isEqualTo(0)
        assertThat(Combat.applyMantraAbsorb(adapter, CombatType.EARTHDAMAGE, -10)).isEqualTo(0)
        assertThat(Combat.applyMantraAbsorb(adapter, CombatType.PHYSICALDAMAGE, -50)).isEqualTo(-50)
    }

    @Test
    fun `the adapter routes the player view and monster view of a creature`() {
        val hero = player()
        val rat = monster()

        val playerTarget = CreatureCombatTarget(hero)
        assertThat(playerTarget.getPlayer()).isInstanceOf(PlayerConditionTarget::class.java)
        assertThat((playerTarget.getPlayer() as PlayerConditionTarget).player).isSameAs(hero)
        assertThat(playerTarget.getMonster()).isNull()

        val monsterTarget = CreatureCombatTarget(rat, object : CombatTargetState {
            override fun isEnemyFaction(monster: Monster, faction: Faction): Boolean = true
        })
        assertThat(monsterTarget.getPlayer()).isNull()
        assertThat(monsterTarget.getMonster()?.isEnemyFaction(Faction.Player)).isTrue()
    }

    @Test
    fun `the adapter applies damage to health and mana with the creature clamps`() {
        // A monster avoids `Player::death`'s no-skill-loss health restore
        // (`PlayerDeathState.applyNoSkillLossDeath`, player.cpp:4257).
        val monster = Monster(id = 2, name = "Rat", maxHealth = 100, health = 100, maxMana = 50, mana = 40, position = center)
        val monsterAdapter = CreatureCombatTarget(monster)

        assertThat(monsterAdapter.drainHealth(30)).isEqualTo(70)
        assertThat(monsterAdapter.addHealth(50)).isEqualTo(100)
        assertThat(monsterAdapter.drainHealth(500)).isZero()

        val player = Player(id = 3, name = "Target", maxHealth = 100, health = 100, maxMana = 50, mana = 40, position = center)
        val playerAdapter = CreatureCombatTarget(player)

        assertThat(playerAdapter.changeMana(-10)).isEqualTo(30)
        assertThat(playerAdapter.changeMana(100)).isEqualTo(50)
        assertThat(playerAdapter.changeMana(-100)).isZero()
    }

    @Test
    fun `the adapter forwards condition immunity and combat conditions`() {
        val rat = monster()
        val target = CreatureCombatTarget(rat, object : CombatTargetState {
            override fun isImmuneCondition(creature: Creature, conditionType: ConditionType): Boolean =
                conditionType == ConditionType.CONDITION_POISON
        })

        assertThat(target.isImmune(ConditionType.CONDITION_POISON)).isTrue()
        assertThat(target.isImmune(ConditionType.CONDITION_FIRE)).isFalse()
    }
}
