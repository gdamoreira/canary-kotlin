package org.opentibia.canary.spells

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.map.Position

/**
 * Transcribes the engine metadata registered by the real Canary spell scripts:
 *  - `data/scripts/spells/conjuring/light_magic_missile_rune.lua` (conjure)
 *  - `data/scripts/runes/light_magic_missile.lua` (rune use)
 *
 * The scripts stay Lua; these tests pin the C++ state (`Spell`/`RuneSpell`
 * setters from `spell_functions.cpp`) they drive through `spell:*`.
 */
class SpellMetadataTest {

    private fun lightMagicMissileConjure(): InstantSpell =
        InstantSpell().apply {
            spellType = SpellType.SPELL_INSTANT
            name = "Light Magic Missile Rune"
            words = "adori min vis"
            group = SpellGroup.SPELLGROUP_SUPPORT
            cooldown = 2 * 1000
            groupCooldown = 2 * 1000
            level = 15
            mana = 120
            soul = 1
            aggressive = false
            addVocMap(1, true)
            addVocMap(2, true)
            addVocMap(5, true)
            addVocMap(6, true)
        }

    private fun lightMagicMissileRune(): RuneSpell =
        RuneSpell().apply {
            spellType = SpellType.SPELL_RUNE
            name = "light magic missile rune"
            spellId = 7
            group = SpellGroup.SPELLGROUP_ATTACK
            soundCastEffect = SoundEffect.SPELL_OR_RUNE
            soundImpactEffect = SoundEffect.SPELL_LIGHT_MAGIC_MISSILE_RUNE
            runeId = 3174
            allowFarUse = true
            charges = 10
            level = 15
            magicLevel = 0
            cooldown = 2 * 1000
            groupCooldown = 2 * 1000
            needTarget = true
            blockingSolid = true
            blockingCreature = false
        }

    @Test
    fun `conjure spell exposes its group level and mana and soul costs`() {
        val conjure = lightMagicMissileConjure()
        val caster = FakeSpellCaster(maxManaValue = 200)

        assertThat(conjure.isInstant()).isTrue()
        assertThat(conjure.group).isEqualTo(SpellGroup.SPELLGROUP_SUPPORT)
        assertThat(conjure.cooldown).isEqualTo(2000)
        assertThat(conjure.groupCooldown).isEqualTo(2000)
        assertThat(conjure.level).isEqualTo(15)
        assertThat(conjure.aggressive).isFalse()
        assertThat(conjure.getManaCost(caster)).isEqualTo(120)
        assertThat(conjure.getSoulCost()).isEqualTo(1)
        assertThat(conjure.vocSpellMap.keys).containsExactlyInAnyOrder(1, 2, 5, 6)
    }

    @Test
    fun `rune spell exposes its item id and conjured charges`() {
        val rune = lightMagicMissileRune()

        assertThat(rune.isInstant()).isFalse()
        assertThat(rune.runeId).isEqualTo(3174)
        assertThat(rune.charges).isEqualTo(10)
        assertThat(rune.hasCharges).isTrue()
        assertThat(rune.needTarget).isTrue()
        assertThat(rune.blockingSolid).isTrue()
        assertThat(rune.blockingCreature).isFalse()
        assertThat(rune.allowFarUse).isTrue()
        assertThat(rune.range).isEqualTo(-1)
    }

    // `RuneSpell::setCharges` (spells.cpp:1629-1634): `hasCharges` only ever latches on.
    @Test
    fun `setCharges latches hasCharges once a positive charge count is set`() {
        val rune = RuneSpell()

        assertThat(rune.hasCharges).isFalse()

        rune.charges = 0
        assertThat(rune.hasCharges).isFalse()

        rune.charges = 10
        assertThat(rune.hasCharges).isTrue()

        rune.charges = 0
        assertThat(rune.hasCharges).isTrue()
    }

    // `Spell::getManaCost` (spells.cpp:952-977) manaPercent branch.
    @Test
    fun `manaPercent costs a fraction of the maximum mana`() {
        val spell = InstantSpell().apply { manaPercent = 10 }
        val caster = FakeSpellCaster(maxManaValue = 500)

        assertThat(spell.getManaCost(caster)).isEqualTo(50)
    }

    @Test
    fun `zero mana and zero manaPercent cost nothing`() {
        val spell = InstantSpell()
        val caster = FakeSpellCaster(maxManaValue = 500)

        assertThat(spell.getManaCost(caster)).isEqualTo(0)
    }

    // `BaseSpell` defaults (spells.hpp:99-100).
    @Test
    fun `base spell defaults its cast sound to spell or rune and impact to silence`() {
        val spell = InstantSpell()

        assertThat(spell.soundCastEffect).isEqualTo(SoundEffect.SPELL_OR_RUNE)
        assertThat(spell.soundImpactEffect).isEqualTo(SoundEffect.SILENCE)
    }

    // `Spells::getCasterPosition` (spells.cpp:337-339) with a directional caster.
    @Test
    fun `caster position follows the caster facing`() {
        val caster = FakeSpellCaster(position = Position(100, 100, 7))

        assertThat(Spells.getCasterPosition(caster, org.opentibia.canary.game.movement.Direction.South))
            .isEqualTo(Position(100, 101, 7))
    }
}
