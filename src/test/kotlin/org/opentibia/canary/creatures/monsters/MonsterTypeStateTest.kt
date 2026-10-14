package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.map.Position

/**
 * Transcribes the `Monster` constructor's monster-type copy and the
 * type-driven host seams:
 * - `Monster::Monster` copies `mType->info.attackSpells`/`defenseSpells` into
 *   the instance vectors (src/creatures/monsters/monster.cpp:104-105)
 * - `Monster::changeTargetSpeed` -> `info.changeTargetChance` and
 *   `info.strategiesTargetNearest`/`...Health`/`...Damage` reads
 *   (monster.cpp:1115-1125, 2141-2200)
 * - `Monster::canUseAttack` over the instance attack spells (monster.cpp:2095-2107)
 * - `Monster::commitCombatIntention` cast call
 *   (`spellBlock.spell->castSpell(monster, target)`, monster.cpp:1979)
 */
class MonsterTypeStateTest {

    private val origin = Position(10, 10, 7)

    private fun monster(id: Long = 1) = Monster(id, "Demon", 100, 100, position = origin)

    @Test
    fun `constructor copies the attack and defense spell vectors from the type`() {
        val type = MonsterType("Demon")
        val strike = BaseSpell { _, _ -> }
        val ward = BaseSpell { _, _ -> }
        type.info.attackSpells += SpellBlock(
            spell = strike,
            chance = 25,
            speed = 1000,
            range = 4,
            minCombatValue = 5,
            maxCombatValue = 9,
            combatSpell = true,
            isMelee = true,
        )
        type.info.defenseSpells += SpellBlock(spell = ward, chance = 50)

        val state = MonsterTypeState(type)

        assertThat(state.attackSpells).containsExactly(
            MonsterSpellBlock(
                spell = strike,
                chance = 25,
                speed = 1000,
                range = 4,
                minCombatValue = 5,
                maxCombatValue = 9,
                combatSpell = true,
                isMelee = true,
            ),
        )
        assertThat(state.defenseSpells).containsExactly(MonsterSpellBlock(spell = ward, chance = 50))

        state.attackSpells += MonsterSpellBlock(spell = ward)
        assertThat(type.info.attackSpells).hasSize(1)
        assertThat(state.attackSpells).hasSize(2)
    }

    @Test
    fun `a state without a monster type starts empty`() {
        val state = MonsterTypeState(null)

        assertThat(state.attackSpells).isEmpty()
        assertThat(state.defenseSpells).isEmpty()
        assertThat(state.forgeStack).isZero()
        assertThat(state.monsterForgeClassification).isEqualTo(ForgeClassification.Normal)
        assertThat(state.timeToChangeFiendish).isZero()
        assertThat(state.hazard).isFalse()
        assertThat(state.soulPit).isFalse()
    }

    @Test
    fun `bindHost replaces the inert attack spell, strategy and chance seams`() {
        val type = MonsterType("Demon").apply {
            info.isHostile = true
            info.changeTargetChance = 20
            info.strategiesTargetNearest = 10
            info.strategiesTargetHealth = 20
            info.strategiesTargetDamage = 30
            info.attackSpells += SpellBlock(spell = BaseSpell { _, _ -> }, range = 5)
        }
        val state = MonsterTypeState(type)
        val host = MonsterAsyncHost()

        state.bindHost(host, monster())

        assertThat(host.attackSpells()).hasSize(1)
        assertThat(host.changeTargetChance()).isEqualTo(20)
        assertThat(host.strategiesTargetNearest()).isEqualTo(10)
        assertThat(host.strategiesTargetHealth()).isEqualTo(20)
        assertThat(host.strategiesTargetDamage()).isEqualTo(30)
        assertThat(
            host.canUseAttack(origin, TargetCreatureFacts(id = 2, position = Position(14, 10, 7))),
        ).isTrue()
        assertThat(
            host.canUseAttack(origin, TargetCreatureFacts(id = 2, position = Position(16, 10, 7))),
        ).isFalse()
    }

    @Test
    fun `bindHost keeps a passive monster attack usable without ranged spells`() {
        val type = MonsterType("Demon").apply { info.isHostile = false }
        val state = MonsterTypeState(type)
        val host = MonsterAsyncHost()

        state.bindHost(host, monster())

        assertThat(host.attackSpells()).isEmpty()
        assertThat(
            host.canUseAttack(origin, TargetCreatureFacts(id = 2, position = Position(30, 30, 7))),
        ).isTrue()
    }

    @Test
    fun `bindHost casts the block spell onto the resolved creature`() {
        val casts = mutableListOf<Pair<Long, Long>>()
        val strike = BaseSpell { caster, target -> casts += caster.id to target.id }
        val type = MonsterType("Demon").apply { info.attackSpells += SpellBlock(spell = strike, range = 5) }
        val state = MonsterTypeState(type)
        val casterMonster = monster(id = 1)
        val targetMonster = Monster(2, "Rat", 100, 100, position = Position(11, 10, 7))
        val host = MonsterAsyncHost()

        state.bindHost(host, casterMonster, resolveCreature = { if (it == 2L) targetMonster else null })

        val block = host.attackSpells().single()
        host.castSpell(block, TargetCreatureFacts(id = 2, position = targetMonster.position))
        assertThat(casts).containsExactly(1L to 2L)

        host.castSpell(block, TargetCreatureFacts(id = 3, position = Position(12, 10, 7)))
        assertThat(casts).hasSize(1)
    }

    @Test
    fun `bindHost ignores blocks without a spell`() {
        val type = MonsterType("Demon").apply { info.attackSpells += SpellBlock(spell = null, range = 5) }
        val state = MonsterTypeState(type)
        val host = MonsterAsyncHost()

        state.bindHost(host, monster(), resolveCreature = { error("a null spell must not resolve") })

        host.castSpell(host.attackSpells().single(), TargetCreatureFacts(id = 2, position = Position(11, 10, 7)))
    }
}
