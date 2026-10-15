package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.Combat
import org.opentibia.canary.combat.CombatParams
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.CreatureCombatTarget
import org.opentibia.canary.combat.condition.CombatType as ConditionCombatType
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.SpectatorSector
import org.opentibia.canary.map.Spectators

/**
 * Transcript tests for `MonsterPipelinesHost.combatTargetState()`: the
 * production [org.opentibia.canary.combat.CombatTargetState] over a registered
 * monster's `Monster::m_isImmune` latch and `MonsterType` immunity bitsets
 * (`Monster::isImmune`, src/creatures/monsters/monster.cpp:3535-3541) and the
 * `Monster::isEnemyFaction` master chain (monster.cpp:267-273).
 */
class MonsterCombatTargetStateTest {

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

    private fun newHost(): MonsterPipelinesHost = MonsterPipelinesHost(
        service = service,
        mapAccess = SpectatorMapAccess { _, _ -> SpectatorSector() },
        resolveCreature = { null },
        clock = { 0L },
    )

    private fun monster(id: Long = 7, name: String = "Rat"): Monster =
        Monster(id = id, name = name, maxHealth = 100, health = 100, position = center)

    private fun poison(): ConditionDamage {
        val condition = ConditionDamage(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_POISON)
        condition.addDamage(1, time = 1000, value = -3)
        return condition
    }

    @Test
    fun `the immunity bitset denies a condition through the host state`() {
        // monster.cpp:3535-3537: `m_isImmune || m_monsterType->info.m_conditionImmunities[type]`.
        val host = newHost()
        val type = MonsterType("Poison Rat").apply {
            info.m_conditionImmunities += ConditionType.CONDITION_POISON
        }
        val target = monster()
        host.register(target, type)
        val combatTarget = CreatureCombatTarget(target, host.combatTargetState())

        Combat.doCombatDefault(
            caster = null,
            target = combatTarget,
            params = CombatParams(conditionList = mutableListOf(poison())),
        )

        assertThat(target.getConditionsByType(ConditionType.CONDITION_POISON)).isEmpty()
    }

    @Test
    fun `a monster without the bitset still receives the condition`() {
        val host = newHost()
        val target = monster()
        host.register(target, MonsterType("Rat"))
        val combatTarget = CreatureCombatTarget(target, host.combatTargetState())

        Combat.doCombatDefault(
            caster = null,
            target = combatTarget,
            params = CombatParams(conditionList = mutableListOf(poison())),
        )

        assertThat(target.getConditionsByType(ConditionType.CONDITION_POISON)).hasSize(1)
    }

    @Test
    fun `the m_isImmune latch denies every combat and condition type`() {
        // monster.cpp:3536, 3540: the runtime `m_isImmune` override wins over the bitsets.
        val host = newHost()
        val target = monster()
        host.register(target, MonsterType("Rat"))
        host.entryOf(target.id)!!.behaviorState.immune = true
        val combatTarget = CreatureCombatTarget(target, host.combatTargetState())

        assertThat(combatTarget.isImmune(ConditionType.CONDITION_POISON)).isTrue()
        assertThat(combatTarget.isImmune(CombatType.FIREDAMAGE)).isTrue()
    }

    @Test
    fun `the damage immunity bitset denies a combat type`() {
        // monster.cpp:3539-3541: `m_isImmune || m_damageImmunities[combatTypeToIndex(combatType)]`.
        val host = newHost()
        val type = MonsterType("Fire Rat").apply {
            info.m_damageImmunities += ConditionCombatType.COMBAT_FIREDAMAGE
        }
        val target = monster()
        host.register(target, type)
        val combatTarget = CreatureCombatTarget(target, host.combatTargetState())

        assertThat(combatTarget.isImmune(CombatType.FIREDAMAGE)).isTrue()
        assertThat(combatTarget.isImmune(CombatType.ICEDAMAGE)).isFalse()
    }

    @Test
    fun `isEnemyFaction reads the type enemy factions`() {
        // monster.cpp:267-273: `m_monsterType->info.enemyFactions.contains(faction)`.
        val host = newHost()
        val type = MonsterType("Lion Rat").apply {
            info.enemyFactions += Faction.Player
        }
        val target = monster()
        host.register(target, type)
        val state = host.combatTargetState()

        assertThat(state.isEnemyFaction(target, Faction.Player)).isTrue()
        assertThat(state.isEnemyFaction(target, Faction.Marid)).isFalse()
    }

    @Test
    fun `isEnemyFaction delegates to the master monster`() {
        // monster.cpp:268-271: `master->getMonster()->isEnemyFaction(faction)`.
        val host = newHost()
        val masterType = MonsterType("Marid Rat").apply {
            info.enemyFactions += Faction.Marid
        }
        val master = monster(id = 8, name = "Marid Rat")
        host.register(master, masterType)
        val summon = monster(id = 9, name = "Summon Rat")
        host.register(summon, MonsterType("Summon Rat").apply { info.enemyFactions += Faction.Player })
        summon.setMaster(master)
        val state = host.combatTargetState()

        assertThat(state.isEnemyFaction(summon, Faction.Marid)).isTrue()
        assertThat(state.isEnemyFaction(summon, Faction.Player)).isFalse()
    }

    @Test
    fun `an unregistered monster keeps the inert defaults`() {
        val host = newHost()
        val state = host.combatTargetState()
        val stray = monster(id = 99, name = "Stray Rat")

        assertThat(state.isImmuneCondition(stray, ConditionType.CONDITION_POISON)).isFalse()
        assertThat(state.isImmune(stray, CombatType.FIREDAMAGE)).isFalse()
        assertThat(state.isEnemyFaction(stray, Faction.Player)).isFalse()
    }

    @Test
    fun `getCombatValues reads the registered behavior state`() {
        // `Creature::getCombatValues` (creature.hpp:620) over
        // `Monster::getCombatValues` (monster.cpp:3346-3355).
        val host = newHost()
        val target = monster()
        host.register(target, MonsterType("Rat"))
        assertThat(host.getCombatValues(target)).isNull()

        host.entryOf(target.id)!!.behaviorState.minCombatValue = 10
        host.entryOf(target.id)!!.behaviorState.maxCombatValue = 20

        assertThat(host.getCombatValues(target)).isEqualTo(10 to 20)
        assertThat(host.getCombatValues(monster(id = 99, name = "Stray Rat"))).isNull()
    }
}
