package org.opentibia.canary.spells

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.creatures.PlayerFlags_t

/**
 * Transcript of `InstantSpell::canCast` (src/creatures/combat/spells.cpp:1419-1443).
 *
 * Unlike `Spell::playerSpellCheck` this path has no group/gamemaster bypass and
 * never sends a cancel message; it only answers whether the player may cast the
 * spell given the learning rules and the vocation map.
 */
class InstantSpellCanCastTest {

    private val world = FakeSpellWorld(learnSpells = true, rateSpellCooldown = 1.0)
    private val scripts = FakeSpellScriptRunner()
    private val registry = Spells()
    private val engine = SpellEngine(registry, world, scripts)

    private fun spell(): InstantSpell =
        InstantSpell().apply {
            spellType = SpellType.SPELL_INSTANT
            name = "Light Healing"
            words = "exura"
            spellId = 1
            group = SpellGroup.SPELLGROUP_HEALING
        }

    @Test
    fun `cannot use spells always refuses`() {
        val caster = FakeSpellCaster()
        caster.setFlag(PlayerFlags_t.CannotUseSpells)
        caster.book.learnInstantSpell("Light Healing")

        assertThat(engine.canCast(caster, spell())).isFalse()
    }

    @Test
    fun `ignore spell check always allows`() {
        val caster = FakeSpellCaster()
        caster.setFlag(PlayerFlags_t.IgnoreSpellCheck)

        assertThat(engine.canCast(caster, spell())).isTrue()
    }

    @Test
    fun `learn spells config requires the learned list entry`() {
        world.learnSpells = true
        val caster = FakeSpellCaster()
        val spell = spell()
        spell.addVocMap(caster.vocationIdValue, true)

        assertThat(engine.canCast(caster, spell)).isFalse()

        caster.book.learnInstantSpell(spell.name)
        assertThat(engine.canCast(caster, spell)).isTrue()
    }

    @Test
    fun `learnable spells require learning when the config is off`() {
        world.learnSpells = false
        val caster = FakeSpellCaster()
        val spell = spell().apply { learnable = true }
        spell.addVocMap(caster.vocationIdValue, true)

        assertThat(engine.canCast(caster, spell)).isFalse()

        caster.book.learnInstantSpell(spell.name)
        assertThat(engine.canCast(caster, spell)).isTrue()
    }

    @Test
    fun `non learnable spells with an empty vocation map are always castable`() {
        world.learnSpells = false
        val caster = FakeSpellCaster()

        assertThat(engine.canCast(caster, spell())).isTrue()
    }

    @Test
    fun `non learnable spells accept the caster vocation by key presence`() {
        world.learnSpells = false
        val caster = FakeSpellCaster()
        caster.vocationIdValue = 2

        // `vocSpellMap.contains` checks the key, not the stored bool (spells.cpp:1437).
        val spell = spell()
        spell.addVocMap(2, false)
        assertThat(engine.canCast(caster, spell)).isTrue()

        val otherVocation = spell()
        otherVocation.addVocMap(4, true)
        assertThat(engine.canCast(caster, otherVocation)).isFalse()
    }

    @Test
    fun `no canCast result depends on the game master group`() {
        world.learnSpells = false
        val caster = FakeSpellCaster()
        caster.groupIdValue = 4
        caster.vocationIdValue = 2

        val spell = spell()
        spell.addVocMap(4, true)

        assertThat(engine.canCast(caster, spell)).isFalse()
    }

    @Test
    fun `learn spells config ignores the vocation map entirely`() {
        world.learnSpells = true
        val caster = FakeSpellCaster()
        caster.vocationIdValue = 2
        val spell = spell()
        spell.addVocMap(4, true)

        caster.book.learnInstantSpell(spell.name)
        assertThat(engine.canCast(caster, spell)).isTrue()
    }
}
