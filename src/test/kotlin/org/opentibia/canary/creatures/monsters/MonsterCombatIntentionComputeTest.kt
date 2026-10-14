package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.FlagAsyncClass_t
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.map.Position

/**
 * Transcribes the deferred combat-intention pipeline:
 * - `Monster::requestCombatIntention` (src/creatures/monsters/monster.cpp:1769-1785)
 * - `Monster::startPendingCombatIntention` (src/creatures/monsters/monster.cpp:1787-1804)
 * - `Monster::prepareCombatIntention` (src/creatures/monsters/monster.cpp:1806-1874)
 * - `Monster::completeCombatIntention` (src/creatures/monsters/monster.cpp:1876-1919)
 * - `Monster::deferPendingCombatIntention` (src/creatures/monsters/monster.cpp:1921-1927)
 * - `Monster::clearCombatIntention` (src/creatures/monsters/monster.cpp:1929-1933)
 * - `Monster::commitCombatIntention` (src/creatures/monsters/monster.cpp:1935-2000)
 * - `Monster::nextCombatIntentionGeneration` (src/creatures/monsters/monster.cpp:2002-2007)
 */
class MonsterCombatIntentionComputeTest {

    private val origin = Position(100, 100, 7)
    private lateinit var service: MonsterComputeService
    private lateinit var targetState: MonsterTargetListState
    private lateinit var behaviorState: MonsterBehaviorState
    private lateinit var host: MonsterAsyncHost
    private lateinit var pipeline: MonsterAsyncPipelines
    private val casts = mutableListOf<Long>()
    private val flagged = mutableListOf<FlagAsyncClass_t>()
    private var lookUpdates = 0

    @BeforeEach
    fun setUp() {
        service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        targetState = MonsterTargetListState()
        behaviorState = MonsterBehaviorState().apply { attackTicks = 100 }
        host = MonsterAsyncHost()
        host.getPosition = { origin }
        host.monsterFacts = { MonsterTargetFacts(origin = origin, id = 7) }
        host.resolveCreature = { if (it == 2L) target() else null }
        host.currentAttackedId = { 2L }
        host.castSpell = { _, target -> casts.add(target.id) }
        host.updateLookDirection = { lookUpdates++ }
        host.setAsyncTaskFlag = { flagged.add(it) }
        host.random = { min, _ -> min }
        host.resolveMonster = { pipeline }
        pipeline = MonsterAsyncPipelines(
            monsterId = 7,
            service = service,
            targetState = targetState,
            behaviorState = behaviorState,
            host = host,
        )
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
    }

    private fun target(
        id: Long = 2,
        x: Int = 102,
        y: Int = 100,
        z: Int = 7,
        health: Int = 100,
        zoneType: ZoneType = ZoneType.Normal,
        isLoginProtected: Boolean = false,
    ) = TargetCreatureFacts(
        id = id,
        position = Position(x, y, z),
        health = health,
        zoneType = zoneType,
        isLoginProtected = isLoginProtected,
    )

    private fun spellBlock(
        range: Int = 5,
        melee: Boolean = false,
        speed: Int = 100,
        chance: Int = 100,
    ): MonsterSpellBlock = MonsterSpellBlock(
        spell = BaseSpell { _, _ -> },
        chance = chance,
        speed = speed,
        range = range,
        isMelee = melee,
    )

    @Test
    fun `the geometry evaluation commits the suggested spell on drain`() {
        host.attackSpells = { listOf(spellBlock(range = 5), spellBlock(range = 1)) }

        pipeline.requestCombatIntention(interval = 100, target = target())
        assertThat(pipeline.combatIntentionOutstanding).isTrue()
        assertThat(casts).isEmpty()

        assertThat(service.drainCompletions(4)).isEqualTo(1)

        assertThat(casts).containsExactly(2L)
        assertThat(pipeline.combatIntentionOutstanding).isFalse()
        assertThat(behaviorState.attackTicks).isZero()
        assertThat(lookUpdates).isEqualTo(1)
    }

    @Test
    fun `a changed attackTicks snapshot clears before the commit`() {
        host.attackSpells = { listOf(spellBlock()) }

        pipeline.requestCombatIntention(interval = 100, target = target())
        behaviorState.attackTicks += 100
        service.drainCompletions(4)

        assertThat(casts).isEmpty()
        assertThat(pipeline.combatIntentionOutstanding).isFalse()
    }

    @Test
    fun `a moved target clears the intent`() {
        host.attackSpells = { listOf(spellBlock()) }

        pipeline.requestCombatIntention(interval = 100, target = target())
        host.resolveCreature = { if (it == 2L) target(x = 108) else null }
        service.drainCompletions(4)

        assertThat(casts).isEmpty()
        assertThat(pipeline.combatIntentionOutstanding).isFalse()
    }

    @Test
    fun `a changed monster reload epoch clears the intent`() {
        host.attackSpells = { listOf(spellBlock()) }
        var reloadEpoch = 1L
        host.monsterReloadEpoch = { reloadEpoch }

        pipeline.requestCombatIntention(interval = 100, target = target())
        reloadEpoch = 2L
        service.drainCompletions(4)

        assertThat(casts).isEmpty()
        assertThat(pipeline.combatIntentionOutstanding).isFalse()
    }

    @Test
    fun `a changed fleeing state clears the intent`() {
        host.attackSpells = { listOf(spellBlock()) }
        var fleeing = false
        host.isFleeing = { fleeing }

        pipeline.requestCombatIntention(interval = 100, target = target())
        fleeing = true
        service.drainCompletions(4)

        assertThat(casts).isEmpty()
        assertThat(pipeline.combatIntentionOutstanding).isFalse()
    }

    @Test
    fun `a changed decision epoch clears the intent`() {
        host.attackSpells = { listOf(spellBlock()) }

        pipeline.requestCombatIntention(interval = 100, target = target())
        MonsterTargeting.markTargetDecisionChanged(targetState)
        service.drainCompletions(4)

        assertThat(casts).isEmpty()
        assertThat(pipeline.combatIntentionOutstanding).isFalse()
        assertThat(flagged).isEmpty()
    }

    @Test
    fun `a login protected target is rejected at completion`() {
        host.attackSpells = { listOf(spellBlock()) }
        host.resolveCreature = { if (it == 2L) target(isLoginProtected = true) else null }

        pipeline.requestCombatIntention(interval = 100, target = target())
        service.drainCompletions(4)

        assertThat(casts).isEmpty()
        assertThat(pipeline.combatIntentionOutstanding).isFalse()
    }

    @Test
    fun `a protection zone on either side clears the intent`() {
        host.attackSpells = { listOf(spellBlock()) }
        host.resolveCreature = { if (it == 2L) target(zoneType = ZoneType.Protection) else null }

        pipeline.requestCombatIntention(interval = 100, target = target())
        service.drainCompletions(4)

        assertThat(casts).isEmpty()
        assertThat(pipeline.combatIntentionOutstanding).isFalse()
    }

    @Test
    fun `a stale generation defers the pending intention and flags the task`() {
        host.attackSpells = { listOf(spellBlock()) }

        pipeline.requestCombatIntention(interval = 100, target = target())
        pipeline.requestCombatIntention(interval = 50, target = target())
        service.drainCompletions(4)

        assertThat(casts).isEmpty()
        assertThat(pipeline.combatIntentionOutstanding).isFalse()
        assertThat(pipeline.pendingCombatIntention?.interval).isEqualTo(50)
        assertThat(flagged).containsExactly(FlagAsyncClass_t.CombatIntention)
    }

    @Test
    fun `a canceled evaluation clears the intent`() {
        host.attackSpells = { listOf(spellBlock()) }

        pipeline.requestCombatIntention(interval = 100, target = target())
        val generation = pipeline.activeCombatIntentionGeneration
        pipeline.completeCombatIntention(
            generation,
            MonsterCombatIntentionResult().apply { canceled = true },
        )

        assertThat(casts).isEmpty()
        assertThat(pipeline.combatIntentionOutstanding).isFalse()
    }

    @Test
    fun `out of order geometry indices clear without casting`() {
        host.attackSpells = { listOf(spellBlock(), spellBlock()) }

        pipeline.requestCombatIntention(interval = 100, target = target())
        val generation = pipeline.activeCombatIntentionGeneration
        pipeline.completeCombatIntention(
            generation,
            MonsterCombatIntentionResult().apply { geometricallyEligibleSpellIndices = listOf(1, 0) },
        )

        assertThat(casts).isEmpty()
        assertThat(pipeline.combatIntentionOutstanding).isFalse()
    }

    @Test
    fun `an out of range geometry index clears without casting`() {
        host.attackSpells = { listOf(spellBlock()) }

        pipeline.requestCombatIntention(interval = 100, target = target())
        val generation = pipeline.activeCombatIntentionGeneration
        pipeline.completeCombatIntention(
            generation,
            MonsterCombatIntentionResult().apply { geometricallyEligibleSpellIndices = listOf(4) },
        )

        assertThat(casts).isEmpty()
        assertThat(pipeline.combatIntentionOutstanding).isFalse()
    }

    @Test
    fun `empty attack spell lists commit synchronously without the geometry hint`() {
        host.attackSpells = { emptyList() }

        pipeline.requestCombatIntention(interval = 100, target = target())

        assertThat(service.getStats().accepted).isZero()
        assertThat(casts).isEmpty()
        assertThat(pipeline.combatIntentionOutstanding).isFalse()
        assertThat(lookUpdates).isEqualTo(1)
        assertThat(behaviorState.attackTicks).isZero()
    }

    @Test
    fun `an oversized attack spell list commits synchronously without the geometry hint`() {
        host.attackSpells = { List(257) { MonsterSpellBlock(spell = null, speed = 100) } }

        pipeline.requestCombatIntention(interval = 100, target = target())

        assertThat(service.getStats().accepted).isZero()
        assertThat(casts).isEmpty()
        assertThat(pipeline.combatIntentionOutstanding).isFalse()
        assertThat(lookUpdates).isEqualTo(1)
    }

    @Test
    fun `the geometry hint gates spells the evaluator did not approve`() {
        host.attackSpells = { listOf(spellBlock(range = 0), spellBlock(range = 0)) }

        pipeline.requestCombatIntention(interval = 100, target = target())
        val generation = pipeline.activeCombatIntentionGeneration
        pipeline.completeCombatIntention(
            generation,
            MonsterCombatIntentionResult().apply { geometricallyEligibleSpellIndices = listOf(1) },
        )

        assertThat(casts).containsExactly(2L)
        assertThat(pipeline.combatIntentionOutstanding).isFalse()
    }

    @Test
    fun `a rejected submission clears the intent and refreshes the look direction`() {
        host.attackSpells = { listOf(spellBlock()) }
        service.shutdown()

        pipeline.requestCombatIntention(interval = 100, target = target())

        assertThat(casts).isEmpty()
        assertThat(pipeline.combatIntentionOutstanding).isFalse()
        assertThat(lookUpdates).isEqualTo(1)
    }
}
