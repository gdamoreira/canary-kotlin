package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.AreaCombat
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.map.Position
import org.opentibia.canary.spells.SoundEffect

/**
 * Transcribes the monster `CombatSpell` actor
 * (src/creatures/combat/spells.cpp:368-490, src/creatures/monsters/monsters.cpp:83-283):
 * the `Combat` payload built by `Monsters::deserializeSpell` keeps the combat
 * type/effect/shoot params, and `spellBlock.spell->castSpell(monster, target)`
 * (monster.cpp:1979) reaches `Combat::doCombat` through the monster adapters.
 */
class MonsterCombatSpellTest {

    private val caster = Monster(id = 1, name = "Rat", maxHealth = 100, health = 100, position = Position(10, 10, 7))
    private val target = Monster(id = 2, name = "Hero", maxHealth = 100, health = 100, position = Position(12, 10, 7))

    @Test
    fun `setParam maps combat type effect and shoot onto the combat params`() {
        val combat = MonsterCombatSpellCombat()

        assertThat(combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_TYPE, CombatType.FIREDAMAGE.value)).isTrue()
        assertThat(combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_EFFECT, 7)).isTrue()
        assertThat(combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_DISTANCEEFFECT, 4)).isTrue()

        assertThat(combat.params.combatType).isEqualTo(CombatType.FIREDAMAGE)
        assertThat(combat.params.impactEffect).isEqualTo(7)
        assertThat(combat.params.distanceEffect).isEqualTo(4)
    }

    @Test
    fun `setParam maps the armor shield aggressive and field params`() {
        val combat = MonsterCombatSpellCombat()

        combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_BLOCKARMOR, 1)
        combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_BLOCKSHIELD, 1)
        combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_AGGRESSIVE, 0)
        combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_CREATEITEM, 2118)
        combat.setParam(MonsterCombatSpellCombat.COMBAT_PARAM_TARGETCASTERORTOPMOST, 1)

        assertThat(combat.params.blockedByArmor).isTrue()
        assertThat(combat.params.blockedByShield).isTrue()
        assertThat(combat.params.aggressive).isFalse()
        assertThat(combat.params.itemId).isEqualTo(2118)
        assertThat(combat.params.targetCasterOrTopMost).isTrue()
    }

    @Test
    fun `setParam rejects an unknown param`() {
        assertThat(MonsterCombatSpellCombat().setParam(99, 1)).isFalse()
    }

    @Test
    fun `hasArea follows the deserialized area`() {
        val combat = MonsterCombatSpellCombat()

        assertThat(combat.hasArea()).isFalse()
        combat.area = AreaCombat()
        assertThat(combat.hasArea()).isTrue()
    }

    @Test
    fun `target casts dispatch combat on the target creature`() {
        val combat = MonsterCombatSpellCombat()
        val calls = mutableListOf<Triple<Monster?, Creature?, Position>>()
        combat.executeCombat = { casterMonster, targetCreature, position ->
            calls.add(Triple(casterMonster, targetCreature, position))
        }
        val spell = MonsterCombatSpell(combat, needTarget = false, needDirection = false)

        spell.castSpell(caster, target)

        assertThat(calls).hasSize(1)
        assertThat(calls.single().first).isSameAs(caster)
        assertThat(calls.single().second).isSameAs(target)
        assertThat(calls.single().third).isEqualTo(target.position)
    }

    @Test
    fun `area casts without need target strike at the caster position`() {
        val combat = MonsterCombatSpellCombat()
        combat.area = AreaCombat()
        val positions = mutableListOf<Position>()
        combat.executeCombat = { _, targetCreature, position ->
            assertThat(targetCreature).isNull()
            positions.add(position)
        }
        val spell = MonsterCombatSpell(combat, needTarget = false, needDirection = false)

        spell.castSpell(caster, target)

        assertThat(positions).containsExactly(caster.position)
    }

    @Test
    fun `area casts with need target strike at the target position`() {
        val combat = MonsterCombatSpellCombat()
        combat.area = AreaCombat()
        val positions = mutableListOf<Position>()
        combat.executeCombat = { _, _, position -> positions.add(position) }
        val spell = MonsterCombatSpell(combat, needTarget = true, needDirection = false)

        spell.castSpell(caster, target)

        assertThat(positions).containsExactly(target.position)
    }

    @Test
    fun `non silent sound effects become cast and impact params`() {
        val combat = MonsterCombatSpellCombat()
        val spell = MonsterCombatSpell(combat, needTarget = false, needDirection = false)
        spell.soundCastEffect = SoundEffect.SPELL_LIGHT_HEALING
        spell.soundImpactEffect = SoundEffect.SPELL_SALVATION

        spell.castSpell(caster, target)

        assertThat(combat.params.soundCastEffect).isEqualTo(SoundEffect.SPELL_LIGHT_HEALING.value)
        assertThat(combat.params.soundImpactEffect).isEqualTo(SoundEffect.SPELL_SALVATION.value)
    }

    @Test
    fun `silent sound effects leave the params untouched`() {
        val combat = MonsterCombatSpellCombat()
        val spell = MonsterCombatSpell(combat, needTarget = false, needDirection = false)
        spell.soundCastEffect = SoundEffect.SILENCE
        spell.soundImpactEffect = SoundEffect.SILENCE

        spell.castSpell(caster, target)

        assertThat(combat.params.soundCastEffect).isZero()
        assertThat(combat.params.soundImpactEffect).isZero()
    }
}
