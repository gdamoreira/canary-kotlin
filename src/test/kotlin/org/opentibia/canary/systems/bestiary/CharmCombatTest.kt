package org.opentibia.canary.systems.bestiary

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatDamage
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionSpeed
import org.opentibia.canary.combat.condition.ConditionType

/**
 * Transcript tests for the charm combat procs, ported from
 * `src/io/iobestiary.cpp` (`parseCharmCombat`, `parseOffensiveCharmCombat`,
 * `parseDefensiveCharmCombat`, `parsePassiveCharmCombat`,
 * `parseOffensiveCharmCombatDamage`, `parseCharmCarnage`).
 *
 * Charm percentages and damage types are transcribed from the real Lua
 * registrations in `data/scripts/systems/bestiary_charms.lua`.
 */
class CharmCombatTest {

    private val wound = Charm(
        name = "Wound",
        id = CharmRune.CHARM_WOUND,
        category = CharmCategory.CHARM_MAJOR,
        type = CharmType.CHARM_OFFENSIVE,
        damageType = CombatType.PHYSICALDAMAGE,
        percent = 5.0,
        logMessage = true,
        effect = 10,
    )

    private val overpower = Charm(
        name = "Overpower",
        id = CharmRune.CHARM_OVERPOWER,
        category = CharmCategory.CHARM_MAJOR,
        type = CharmType.CHARM_OFFENSIVE,
        damageType = CombatType.NEUTRALDAMAGE,
        percent = 5.0,
        logMessage = true,
    )

    private val overflux = Charm(
        name = "Overflux",
        id = CharmRune.CHARM_OVERFLUX,
        category = CharmCategory.CHARM_MAJOR,
        type = CharmType.CHARM_OFFENSIVE,
        damageType = CombatType.NEUTRALDAMAGE,
        percent = 2.5,
        logMessage = true,
    )

    private val parry = Charm(
        name = "Parry",
        id = CharmRune.CHARM_PARRY,
        category = CharmCategory.CHARM_MAJOR,
        type = CharmType.CHARM_DEFENSIVE,
        damageType = CombatType.PHYSICALDAMAGE,
        logMessage = true,
    )

    private val fatal = Charm(
        name = "Fatal Hold",
        id = CharmRune.CHARM_FATAL,
        category = CharmCategory.CHARM_MINOR,
        type = CharmType.CHARM_PASSIVE,
    )

    private val carnage = Charm(
        name = "Carnage",
        id = CharmRune.CHARM_CARNAGE,
        category = CharmCategory.CHARM_MAJOR,
        type = CharmType.CHARM_OFFENSIVE,
        damageType = CombatType.NEUTRALDAMAGE,
        percent = 15.0,
        logMessage = true,
    )

    // ---- parseOffensiveCharmCombatDamage ------------------------------------

    @Test
    fun `offensive charm damage sets type value extension and log string`() {
        val harness = Harness()
        val damage = CombatDamage()
        val params = CharmCombatParams()

        harness.combat.parseOffensiveCharmCombatDamage(wound, -150, damage, params)

        assertThat(damage.primaryType).isEqualTo(CombatType.PHYSICALDAMAGE)
        assertThat(damage.primaryValue).isEqualTo(-150)
        assertThat(damage.extension).isTrue()
        assertThat(damage.exString).isEqualTo("(wound charm)")
        assertThat(params.combatType).isEqualTo(CombatType.PHYSICALDAMAGE)
        assertThat(params.impactEffect).isEqualTo(10)
        assertThat(params.aggressive).isTrue()
    }

    @Test
    fun `offensive charm damage without log message keeps the string empty`() {
        val harness = Harness()
        val noLog = wound.copy(logMessage = false)
        val damage = CombatDamage()
        val params = CharmCombatParams()

        harness.combat.parseOffensiveCharmCombatDamage(noLog, -150, damage, params)

        assertThat(damage.exString).isEmpty()
    }

    // ---- offensive charms ----------------------------------------------------

    @Test
    fun `wound charm caps damage at two times the player level`() {
        val harness = Harness()
        // value = min(ceil(100 * 2), ceil(3000 * 5 / 100)) = min(200, 150) = 150.
        val player = FakePlayer(level = 100)
        val target = FakeCreature(maxHealth = 3000)
        val damage = CombatDamage()

        val call = harness.combat.parseOffensiveCharmCombat(wound, player, target, damage, CharmCombatParams())

        assertThat(call).isTrue()
        assertThat(damage.primaryValue).isEqualTo(-150)
        assertThat(damage.primaryType).isEqualTo(CombatType.PHYSICALDAMAGE)
    }

    @Test
    fun `wound charm is capped by the level limit when the target is huge`() {
        val harness = Harness()
        // value = min(ceil(100 * 2), ceil(100000 * 5 / 100)) = min(200, 5000) = 200.
        val player = FakePlayer(level = 100)
        val target = FakeCreature(maxHealth = 100000)
        val damage = CombatDamage()

        harness.combat.parseOffensiveCharmCombat(wound, player, target, damage, CharmCombatParams())

        assertThat(damage.primaryValue).isEqualTo(-200)
    }

    @Test
    fun `overpower charm uses the maximum health limit`() {
        val harness = Harness()
        // value = min(ceil(3000 * 0.08), ceil(2000 * 5 / 100)) = min(240, 100) = 100.
        val player = FakePlayer(level = 100, maxHealth = 2000)
        val target = FakeCreature(maxHealth = 3000)
        val damage = CombatDamage()

        harness.combat.parseOffensiveCharmCombat(overpower, player, target, damage, CharmCombatParams())

        assertThat(damage.primaryValue).isEqualTo(-100)
        assertThat(damage.primaryType).isEqualTo(CombatType.NEUTRALDAMAGE)
    }

    @Test
    fun `overflux charm scales with the player maximum mana`() {
        val harness = Harness()
        // value = min(ceil(3000 * 0.08), ceil(4000 * 2.5 / 100)) = min(240, 100) = 100.
        val player = FakePlayer(level = 100, maxMana = 4000)
        val target = FakeCreature(maxHealth = 3000)
        val damage = CombatDamage()

        harness.combat.parseOffensiveCharmCombat(overflux, player, target, damage, CharmCombatParams())

        assertThat(damage.primaryValue).isEqualTo(-100)
    }

    @Test
    fun `cripple charm paralyzes the target and deals no damage`() {
        val harness = Harness()
        val cripple = Charm(
            name = "Cripple",
            id = CharmRune.CHARM_CRIPPLE,
            category = CharmCategory.CHARM_MINOR,
            type = CharmType.CHARM_OFFENSIVE,
            percent = 0.0,
        )
        val player = FakePlayer(level = 100)
        val target = FakeCreature(maxHealth = 3000)
        val damage = CombatDamage()

        val call = harness.combat.parseOffensiveCharmCombat(cripple, player, target, damage, CharmCombatParams())

        assertThat(call).isFalse()
        assertThat(damage.isEmpty()).isTrue()
        assertThat(target.conditions).hasSize(1)
        val applied = target.conditions[0] as ConditionSpeed
        assertThat(applied.type).isEqualTo(ConditionType.CONDITION_PARALYZE)
        assertThat(applied.ticks).isEqualTo(10000)
        assertThat(applied.mina).isEqualTo(-1.0f)
        assertThat(applied.maxa).isEqualTo(-1.0f)
    }

    @Test
    fun `unknown offensive charm id is ignored`() {
        val harness = Harness()
        val unknown = wound.copy(id = CharmRune.CHARM_BLESS)
        val damage = CombatDamage()

        val call = harness.combat.parseOffensiveCharmCombat(unknown, FakePlayer(), FakeCreature(), damage, CharmCombatParams())

        assertThat(call).isFalse()
        assertThat(damage.isEmpty()).isTrue()
    }

    // ---- carnage -------------------------------------------------------------

    @Test
    fun `carnage damages neighbouring monsters and returns false`() {
        val harness = Harness()
        val neighbour = FakeCreature(maxHealth = 1000, position = CharmPosition(99, 100, 7))
        harness.world.tiles.add(CharmPosition(99, 100, 7))
        harness.world.monsters[CharmPosition(99, 100, 7)] = neighbour

        val dispatches = mutableListOf<Pair<CharmCombatCreature, CombatDamage>>()
        val combat = harness.combatWith { caster, victim, dmg, _ ->
            assertThat(caster).isSameAs(harness.player)
            dispatches.add(victim to dmg)
        }

        val damage = CombatDamage()
        // monster must be dead for carnage to fire (iobestiary.cpp:71-73).
        val deadTarget = FakeCreature(maxHealth = 3000, position = CharmPosition(100, 100, 7), monster = FakeMonster(dead = true))

        val call = combat.parseOffensiveCharmCombat(carnage, harness.player, deadTarget, damage, CharmCombatParams())

        assertThat(call).isFalse()
        assertThat(damage.isEmpty()).isTrue()
        // damage = min(ceil(3000 * 15 / 100), 100 * 6) = min(450, 600) = 450.
        assertThat(dispatches).hasSize(1)
        assertThat(dispatches[0].first).isSameAs(neighbour)
        assertThat(dispatches[0].second.primaryValue).isEqualTo(-450)
        assertThat(dispatches[0].second.primaryType).isEqualTo(CombatType.NEUTRALDAMAGE)
        assertThat(harness.world.effects).containsExactly(CharmPosition(99, 100, 7) to CharmCombat.CONST_ME_DRAWBLOOD)
    }

    @Test
    fun `carnage is skipped for a living monster`() {
        val harness = Harness()
        val aliveTarget = FakeCreature(maxHealth = 3000, position = CharmPosition(100, 100, 7), monster = FakeMonster(dead = false))
        harness.world.tiles.add(CharmPosition(99, 100, 7))
        harness.world.monsters[CharmPosition(99, 100, 7)] = FakeCreature()

        val call = harness.combat.parseOffensiveCharmCombat(carnage, harness.player, aliveTarget, CombatDamage(), CharmCombatParams())

        assertThat(call).isFalse()
        assertThat(harness.world.effects).isEmpty()
    }

    // ---- defensive charms ----------------------------------------------------

    @Test
    fun `parry charm reflects real damage as neutral and blocks by armor`() {
        val harness = Harness()
        val damage = CombatDamage()
        val params = CharmCombatParams()

        val call = harness.combat.parseDefensiveCharmCombat(parry, harness.player, FakeCreature(), 250, true, damage, params)

        assertThat(call).isTrue()
        assertThat(damage.primaryType).isEqualTo(CombatType.NEUTRALDAMAGE)
        assertThat(damage.primaryValue).isEqualTo(-250)
        assertThat(damage.extension).isTrue()
        assertThat(damage.exString).isEqualTo("(parry charm)")
        assertThat(params.aggressive).isTrue()
        assertThat(params.blockedByArmor).isTrue()
    }

    @Test
    fun `dodge charm emits the magic effect and deals no damage`() {
        val harness = Harness()
        val dodge = Charm(
            name = "Dodge",
            id = CharmRune.CHARM_DODGE,
            category = CharmCategory.CHARM_MAJOR,
            type = CharmType.CHARM_DEFENSIVE,
            effect = 3,
        )
        val target = FakeCreature(position = CharmPosition(50, 50, 7))
        val damage = CombatDamage()

        val call = harness.combat.parseDefensiveCharmCombat(dodge, harness.player, target, 0, false, damage, CharmCombatParams())

        assertThat(call).isFalse()
        assertThat(damage.isEmpty()).isTrue()
        assertThat(harness.world.effects).containsExactly(CharmPosition(50, 50, 7) to 3)
    }

    @Test
    fun `adrenaline charm hastes the player with the canary formula`() {
        val harness = Harness()
        val adrenaline = Charm(
            name = "Adrenaline Burst",
            id = CharmRune.CHARM_ADRENALINE,
            category = CharmCategory.CHARM_MINOR,
            type = CharmType.CHARM_DEFENSIVE,
        )

        harness.combat.parseDefensiveCharmCombat(adrenaline, harness.player, FakeCreature(), 0, false, CombatDamage(), CharmCombatParams())

        assertThat(harness.player.conditions).hasSize(1)
        val applied = harness.player.conditions[0] as ConditionSpeed
        assertThat(applied.type).isEqualTo(ConditionType.CONDITION_HASTE)
        assertThat(applied.ticks).isEqualTo(10000)
        assertThat(applied.mina).isEqualTo(2.5f)
        assertThat(applied.minb).isEqualTo(40.0f)
        assertThat(applied.maxa).isEqualTo(2.5f)
        assertThat(applied.maxb).isEqualTo(40.0f)
    }

    // ---- passive charms ------------------------------------------------------

    @Test
    fun `fatal charm sets the fatal hold duration on the monster`() {
        val harness = Harness()
        val monster = FakeMonster()
        val target = FakeCreature(monster = monster)

        val call = harness.combat.parsePassiveCharmCombat(fatal, harness.player, target, 0, CombatDamage(), CharmCombatParams())

        assertThat(call).isFalse()
        assertThat(monster.fatalHold).isEqualTo(30000)
    }

    @Test
    fun `passive charm without a monster is a no-op`() {
        val harness = Harness()

        val call = harness.combat.parsePassiveCharmCombat(fatal, harness.player, FakeCreature(), 0, CombatDamage(), CharmCombatParams())

        assertThat(call).isFalse()
    }

    // ---- parseCharmCombat dispatch -------------------------------------------

    @Test
    fun `parse charm combat dispatches offensive damage to combat health`() {
        val harness = Harness()
        val dispatches = mutableListOf<CombatDamage>()
        val combat = harness.combatWith { _, _, dmg, _ -> dispatches.add(dmg) }

        val result = combat.parseCharmCombat(wound, harness.player, FakeCreature(maxHealth = 3000), realDamage = 0)

        assertThat(result).isTrue()
        assertThat(dispatches).hasSize(1)
        assertThat(dispatches[0].primaryValue).isEqualTo(-150)
    }

    @Test
    fun `parse charm combat sends the cancel message`() {
        val harness = Harness()
        val withCancel = parry.copy(cancelMessage = "You parried an attack. (parry charm)")

        harness.combat.parseCharmCombat(withCancel, harness.player, FakeCreature(), realDamage = 250)

        assertThat(harness.player.cancelMessages)
            .containsExactly("You parried an attack. (parry charm)")
    }

    @Test
    fun `parse charm combat rejects null inputs`() {
        val harness = Harness()

        assertThat(harness.combat.parseCharmCombat(null, harness.player, FakeCreature())).isFalse()
        assertThat(harness.combat.parseCharmCombat(wound, null, FakeCreature())).isFalse()
        assertThat(harness.combat.parseCharmCombat(wound, harness.player, null)).isFalse()
    }

    // ---- fakes ---------------------------------------------------------------

    private class Harness {
        val world = FakeWorld()
        val player = FakePlayer()
        val combat = CharmCombat(world) { _, _, _, _ -> }

        fun combatWith(dispatch: (CharmCombatPlayer, CharmCombatCreature, CombatDamage, CharmCombatParams) -> Unit): CharmCombat =
            CharmCombat(world, dispatch)
    }

    private class FakeMonster(private val dead: Boolean = false) : CharmCombatMonster {
        var fatalHold: Int = -1
        override fun isDead(): Boolean = dead
        override fun setFatalHoldDuration(duration: Int) {
            fatalHold = duration
        }
    }

    private class FakeCreature(
        override val maxHealth: Int = 1000,
        override val position: CharmPosition = CharmPosition(100, 100, 7),
        override val monster: CharmCombatMonster? = null,
    ) : CharmCombatCreature {
        val conditions = mutableListOf<Condition>()
        override fun isDead(): Boolean = false
        override fun addCondition(condition: Condition) {
            conditions.add(condition)
        }
    }

    private class FakePlayer(
        override val level: Int = 100,
        override val maxHealth: Int = 2000,
        override val maxMana: Int = 4000,
        override val position: CharmPosition = CharmPosition(100, 100, 7),
        override val monster: CharmCombatMonster? = null,
    ) : CharmCombatPlayer {
        val conditions = mutableListOf<Condition>()
        val cancelMessages = mutableListOf<String>()
        override fun isDead(): Boolean = false
        override fun addCondition(condition: Condition) {
            conditions.add(condition)
        }

        override fun sendCancelMessage(message: String) {
            cancelMessages.add(message)
        }
    }

    private class FakeWorld : CharmCombatWorld {
        val effects = mutableListOf<Pair<CharmPosition, Int>>()
        val tiles = mutableSetOf<CharmPosition>()
        val monsters = mutableMapOf<CharmPosition, CharmCombatCreature>()

        override fun tileExists(position: CharmPosition): Boolean = position in tiles
        override fun topMonsterAt(position: CharmPosition): CharmCombatCreature? = monsters[position]

        override fun addMagicEffect(position: CharmPosition, effect: Int) {
            effects.add(position to effect)
        }
    }
}
