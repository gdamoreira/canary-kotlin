package org.opentibia.canary.spells

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position

/**
 * Transcript of `CombatSpell` (src/creatures/combat/spells.cpp:368-490).
 *
 * The scripted branch hands the Lua `onCastSpell` bridge the exact variant; the
 * combat branch sets the cast/impact sound params and dispatches `doCombat`.
 */
class CombatSpellTest {

    private val caster = FakeSpellCaster(
        position = Position(100, 100, 7),
        direction = Direction.East,
    )
    private val target = FakeSpellTarget(id = 42, position = Position(105, 100, 7))

    private fun spell(
        combat: FakeCombatSpellCombat? = FakeCombatSpellCombat(),
        needTarget: Boolean = false,
        needDirection: Boolean = false,
        runner: FakeCombatSpellRunner = FakeCombatSpellRunner(),
    ): Triple<CombatSpell, FakeCombatSpellCombat?, FakeCombatSpellRunner> {
        val combatSpell = CombatSpell(combat, needTarget, needDirection, runner)
        return Triple(combatSpell, combat, runner)
    }

    @Test
    fun `get combat returns the injected combat`() {
        val combat = FakeCombatSpellCombat()
        val combatSpell = CombatSpell(combat, false, false, FakeCombatSpellRunner())

        assertThat(combatSpell.getCombat()).isSameAs(combat)
    }

    @Test
    fun `position casts dispatch combat at the creature position`() {
        val (combatSpell, combat, _) = spell()

        assertThat(combatSpell.castSpell(caster)).isTrue()

        assertThat(combat!!.positionCalls).containsExactly(caster.getPosition())
        assertThat(combat.targetCalls).isEmpty()
    }

    @Test
    fun `direction casts dispatch combat at the faced tile`() {
        val (combatSpell, combat, _) = spell(needDirection = true)

        assertThat(combatSpell.castSpell(caster)).isTrue()

        assertThat(combat!!.positionCalls).containsExactly(Position(101, 100, 7))
    }

    @Test
    fun `sound effects are set as cast and impact params`() {
        val (combatSpell, combat, _) = spell()
        combatSpell.soundCastEffect = SoundEffect.SPELL_LIGHT_HEALING
        combatSpell.soundImpactEffect = SoundEffect.SPELL_SALVATION

        assertThat(combatSpell.castSpell(caster)).isTrue()

        assertThat(combat!!.params).containsExactly(
            CombatSpell.COMBAT_PARAM_CASTSOUND to SoundEffect.SPELL_LIGHT_HEALING.value,
            CombatSpell.COMBAT_PARAM_IMPACTSOUND to SoundEffect.SPELL_SALVATION.value,
        )
    }

    @Test
    fun `silent casts set no sound params`() {
        val (combatSpell, combat, _) = spell()
        combatSpell.soundCastEffect = SoundEffect.SILENCE
        combatSpell.soundImpactEffect = SoundEffect.SILENCE

        assertThat(combatSpell.castSpell(caster)).isTrue()

        assertThat(combat!!.params).isEmpty()
    }

    @Test
    fun `scripted position casts send a position variant and skip combat`() {
        val (combatSpell, combat, runner) = spell(needDirection = true)
        combatSpell.scriptId = 7
        runner.result = false

        assertThat(combatSpell.castSpell(caster)).isFalse()

        assertThat(combat!!.positionCalls).isEmpty()
        val variant = runner.calls.single().second
        assertThat(variant.type).isEqualTo(LuaVariantType.VARIANT_POSITION)
        assertThat(variant.pos).isEqualTo(Position(101, 100, 7))
    }

    @Test
    fun `target casts without combat refuse`() {
        val (combatSpell, _, runner) = spell(combat = null)
        combatSpell.scriptId = 7

        assertThat(combatSpell.castSpell(caster, target)).isFalse()
        assertThat(runner.calls).isEmpty()
    }

    @Test
    fun `area target casts without a script dispatch at the target position`() {
        val (combatSpell, combat, _) = spell(combat = FakeCombatSpellCombat(area = true), needTarget = true)

        assertThat(combatSpell.castSpell(caster, target)).isTrue()

        assertThat(combat!!.positionCalls).containsExactly(target.getPosition())
        assertThat(combat.targetCalls).isEmpty()
    }

    @Test
    fun `area casts without a target delegate to the position cast`() {
        val (combatSpell, combat, _) = spell(combat = FakeCombatSpellCombat(area = true), needTarget = false)

        assertThat(combatSpell.castSpell(caster, target)).isTrue()

        assertThat(combat!!.positionCalls).containsExactly(caster.getPosition())
    }

    @Test
    fun `single target casts without a script dispatch on the target`() {
        val (combatSpell, combat, _) = spell(combat = FakeCombatSpellCombat(area = false))

        assertThat(combatSpell.castSpell(caster, target)).isTrue()

        assertThat(combat!!.targetCalls).containsExactly(target)
        assertThat(combat.positionCalls).isEmpty()
    }

    @Test
    fun `scripted area casts with a target send the target position variant`() {
        val (combatSpell, _, runner) = spell(
            combat = FakeCombatSpellCombat(area = true),
            needTarget = true,
        )
        combatSpell.scriptId = 7

        assertThat(combatSpell.castSpell(caster, target)).isTrue()

        val variant = runner.calls.single().second
        assertThat(variant.type).isEqualTo(LuaVariantType.VARIANT_POSITION)
        assertThat(variant.pos).isEqualTo(target.getPosition())
    }

    @Test
    fun `scripted single target casts send the target number variant`() {
        val (combatSpell, _, runner) = spell(combat = FakeCombatSpellCombat(area = false))
        combatSpell.scriptId = 7

        assertThat(combatSpell.castSpell(caster, target)).isTrue()

        val variant = runner.calls.single().second
        assertThat(variant.type).isEqualTo(LuaVariantType.VARIANT_NUMBER)
        assertThat(variant.number).isEqualTo(42)
    }
}

/** Recording double for the `Combat` instance state `CombatSpell` drives. */
class FakeCombatSpellCombat(
    var area: Boolean = false,
) : CombatSpellCombat {

    val params: MutableList<Pair<Int, Int>> = mutableListOf()
    val positionCalls: MutableList<Position> = mutableListOf()
    val targetCalls: MutableList<SpellTarget> = mutableListOf()

    override fun hasArea(): Boolean = area

    override fun setParam(param: Int, value: Int): Boolean {
        params.add(param to value)
        return true
    }

    override fun doCombat(creature: SpellCaster?, position: Position) {
        positionCalls.add(position)
    }

    override fun doCombat(creature: SpellCaster?, target: SpellTarget) {
        targetCalls.add(target)
    }
}

/** Recording double for the Lua `onCastSpell` bridge of a combat spell. */
class FakeCombatSpellRunner(
    var result: Boolean = true,
) : CombatSpellScriptRunner {

    val calls: MutableList<Pair<SpellCaster?, LuaVariant>> = mutableListOf()

    override fun executeCastSpell(creature: SpellCaster?, variant: LuaVariant): Boolean {
        calls.add(creature to variant)
        return result
    }
}
