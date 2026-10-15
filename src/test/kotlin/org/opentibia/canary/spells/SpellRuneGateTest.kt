package org.opentibia.canary.spells

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the spell-path player/tile gates:
 *  - `Combat::canDoCombat(player, tile, aggressive)` reached from
 *    `Spell::playerRuneSpellCheck` (spells.cpp:690)
 *  - `applyInstantSpellDirection` (spells.cpp:47-59) with the
 *    `Player::getAimAtTargetSpells()` refinement (spells.cpp:52-54)
 */
class SpellRuneGateTest {

    private val world = FakeSpellWorld(learnSpells = false, rateSpellCooldown = 1.0)
    private val scripts = FakeSpellScriptRunner()
    private val engine = SpellEngine(Spells(), world, scripts)

    private fun rune(
        needTarget: Boolean = true,
        aggressive: Boolean = true,
    ): RuneSpell = RuneSpell().apply {
        spellType = SpellType.SPELL_RUNE
        name = "light magic missile rune"
        spellId = 7
        scriptId = 1
        group = SpellGroup.SPELLGROUP_ATTACK
        runeId = 3174
        this.needTarget = needTarget
        this.aggressive = aggressive
        blockingSolid = true
        level = 15
        mana = 0
    }

    private fun caster(): FakeSpellCaster = FakeSpellCaster(levelValue = 20, manaValue = 100)

    @Test
    fun `rune use consults the tile combat gate and cancels with its return value`() {
        // spells.cpp:690-694 `returnValue = Combat::canDoCombat(player, tile, aggressive)`.
        val rune = rune()
        val item = Item(id = 3174, count = 10)
        val caster = caster()
        world.bottomVisibleCreature = { 42L }
        world.canDoCombatResult = ReturnValue.ACTIONNOTPERMITTEDINPROTECTIONZONE

        assertThat(engine.executeUse(caster, rune, item, FakeSpellTarget(id = 42), Position(101, 100, 7), false))
            .isFalse()

        assertThat(caster.cancelMessages).containsExactly(ReturnValue.ACTIONNOTPERMITTEDINPROTECTIONZONE)
        assertThat(scripts.runeCalls).isEmpty()
        assertThat(item.count).isEqualTo(10)
        assertThat(world.canDoCombatCalls).containsExactly(Position(101, 100, 7) to true)
    }

    @Test
    fun `rune use forwards the spell aggressiveness to the gate`() {
        val rune = rune(aggressive = false)
        val caster = caster()
        world.bottomVisibleCreature = { 42L }

        assertThat(engine.executeUse(caster, rune, Item(id = 3174), FakeSpellTarget(id = 42), Position(101, 100, 7), false))
            .isTrue()

        assertThat(world.canDoCombatCalls).containsExactly(Position(101, 100, 7) to false)
    }

    @Test
    fun `doors and tiles rejected by the gate never reach the script`() {
        val rune = rune(needTarget = false, aggressive = false)
        val caster = caster()
        world.canDoCombatResult = ReturnValue.CANNOTTHROW

        assertThat(engine.executeUse(caster, rune, Item(id = 3174), null, Position(101, 100, 7), false))
            .isFalse()

        assertThat(caster.cancelMessages).containsExactly(ReturnValue.CANNOTTHROW)
        assertThat(scripts.runeCalls).isEmpty()
    }

    // `applyInstantSpellDirection` (spells.cpp:47-59).

    private fun directionSpell(spellId: Int = 7): InstantSpell = InstantSpell().apply {
        spellType = SpellType.SPELL_INSTANT
        name = "test direction spell"
        words = "testspell"
        this.spellId = spellId
        group = SpellGroup.SPELLGROUP_ATTACK
    }

    @Test
    fun `aim at target spells turn the variant to the primary axis direction`() {
        // spells.cpp:50-57: the attacked creature switches the variant to
        // VARIANT_TARGETPOSITION and the aim set turns the facing.
        val caster = FakeSpellCaster(
            position = Position(100, 100, 7),
            direction = Direction.South,
            levelValue = 20,
            manaValue = 100,
        )
        caster.attackedTarget = FakeSpellTarget(id = 42, position = Position(102, 101, 7))
        caster.aimAtTargetSpellsValue = setOf(7)
        val spell = directionSpell().apply { needDirection = true }

        assertThat(engine.playerCastInstant(caster, spell)).isTrue()

        val variant = scripts.instantCalls.single().third
        assertThat(variant.type).isEqualTo(LuaVariantType.VARIANT_TARGETPOSITION)
        // dx = 2 > dy = 1 -> East -> caster position + east.
        assertThat(variant.pos).isEqualTo(Position(101, 100, 7))
    }

    @Test
    fun `direction spells without the aim mark keep the caster facing`() {
        val caster = FakeSpellCaster(
            position = Position(100, 100, 7),
            direction = Direction.South,
            levelValue = 20,
            manaValue = 100,
        )
        caster.attackedTarget = FakeSpellTarget(id = 42, position = Position(102, 101, 7))
        val spell = directionSpell().apply { needDirection = true }

        assertThat(engine.playerCastInstant(caster, spell)).isTrue()

        val variant = scripts.instantCalls.single().third
        assertThat(variant.type).isEqualTo(LuaVariantType.VARIANT_TARGETPOSITION)
        assertThat(variant.pos).isEqualTo(Position(100, 101, 7))
    }

    @Test
    fun `direction spells without an attacked creature keep the caster facing`() {
        val caster = FakeSpellCaster(
            position = Position(100, 100, 7),
            direction = Direction.East,
            levelValue = 20,
            manaValue = 100,
        )
        caster.aimAtTargetSpellsValue = setOf(7)
        val spell = directionSpell().apply { needDirection = true }

        assertThat(engine.playerCastInstant(caster, spell)).isTrue()

        val variant = scripts.instantCalls.single().third
        assertThat(variant.type).isEqualTo(LuaVariantType.VARIANT_POSITION)
        assertThat(variant.pos).isEqualTo(Position(101, 100, 7))
    }
}
