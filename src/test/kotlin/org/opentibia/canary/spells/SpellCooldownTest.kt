package org.opentibia.canary.spells

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.SpellGroup

class SpellCooldownTest {

    private val world = FakeSpellWorld(learnSpells = false, rateSpellCooldown = 1.0)
    private val scripts = FakeSpellScriptRunner()
    private val engine = SpellEngine(Spells(), world, scripts)

    private fun spell(
        id: Int,
        group: SpellGroup = SpellGroup.SPELLGROUP_NONE,
        cooldown: Int = 2000,
        groupCooldown: Int = 2000,
    ): InstantSpell = InstantSpell().apply {
        spellType = SpellType.SPELL_INSTANT
        name = "Spell $id"
        words = "spell $id"
        spellId = id
        this.group = group
        this.cooldown = cooldown
        this.groupCooldown = groupCooldown
        mana = 0
        soundCastEffect = SoundEffect.SILENCE
    }

    private fun caster(): FakeSpellCaster = FakeSpellCaster(manaValue = 1000)

    private fun spellCooldownOf(caster: FakeSpellCaster) =
        caster.conditions.first { it.type == ConditionType.CONDITION_SPELLCOOLDOWN }

    private fun groupCooldownOf(caster: FakeSpellCaster) =
        caster.conditions.first { it.type == ConditionType.CONDITION_SPELLGROUPCOOLDOWN }

    // `Spell::applyCooldownConditions` (spells.cpp:822-885).

    @Test
    fun `spell cooldown ticks use the configured rate`() {
        val caster = caster()

        engine.applyCooldownConditions(caster, spell(1, cooldown = 3000, groupCooldown = 3000))

        assertThat(spellCooldownOf(caster).ticks).isEqualTo(3000)
        assertThat(groupCooldownOf(caster).ticks).isEqualTo(3000)
    }

    @Test
    fun `lowering the rate lengthens the cooldown ticks`() {
        world.rateSpellCooldown = 0.5
        val caster = caster()

        engine.applyCooldownConditions(caster, spell(1))

        assertThat(spellCooldownOf(caster).ticks).isEqualTo(4000)
        assertThat(groupCooldownOf(caster).ticks).isEqualTo(4000)
    }

    @Test
    fun `zero rate falls back to the safe minimum`() {
        world.rateSpellCooldown = 0.0
        val caster = caster()

        engine.applyCooldownConditions(caster, spell(1))

        assertThat(spellCooldownOf(caster).ticks).isEqualTo(20000)
    }

    @Test
    fun `zero cooldown values add no conditions`() {
        val caster = caster()

        engine.applyCooldownConditions(
            caster,
            spell(1, cooldown = 0, groupCooldown = 0),
        )

        assertThat(caster.conditions).isEmpty()
    }

    @Test
    fun `secondary group cooldown adds its own condition`() {
        val caster = caster()
        val spell = spell(1).apply {
            secondaryGroup = SpellGroup.SPELLGROUP_GREAT_BEAMS
            secondaryGroupCooldown = 4000
        }

        engine.applyCooldownConditions(caster, spell)

        val secondary = caster.conditions
            .filter { it.type == ConditionType.CONDITION_SPELLGROUPCOOLDOWN }
            .first { it.subId == SpellGroup.SPELLGROUP_GREAT_BEAMS.value }
        assertThat(secondary.ticks).isEqualTo(4000)
    }

    @Test
    fun `a shared group cooldown blocks a sibling spell`() {
        val support = spell(1, group = SpellGroup.SPELLGROUP_SUPPORT)
        val sibling = spell(2, group = SpellGroup.SPELLGROUP_SUPPORT)
        val otherGroup = spell(3, group = SpellGroup.SPELLGROUP_ATTACK)
        val caster = caster()

        engine.postCastSpell(caster, support, payCost = false)

        assertThat(engine.playerSpellCheck(caster, sibling)).isFalse()
        assertThat(engine.playerSpellCheck(caster, otherGroup)).isTrue()
    }

    @Test
    fun `a spell cooldown blocks only the same spell id`() {
        val first = spell(7, cooldown = 2000, groupCooldown = 0)
        val other = spell(8, cooldown = 2000, groupCooldown = 0)
        val caster = caster()

        engine.postCastSpell(caster, first, payCost = false)

        assertThat(engine.playerSpellCheck(caster, spell(7, cooldown = 2000, groupCooldown = 0))).isFalse()
        assertThat(engine.playerSpellCheck(caster, other)).isTrue()
    }

    @Test
    fun `a secondary group cooldown blocks a spell sharing the secondary group`() {
        val first = spell(1).apply {
            group = SpellGroup.SPELLGROUP_ATTACK
            secondaryGroup = SpellGroup.SPELLGROUP_GREAT_BEAMS
            secondaryGroupCooldown = 2000
        }
        val sibling = spell(2).apply {
            group = SpellGroup.SPELLGROUP_FOCUS
            secondaryGroup = SpellGroup.SPELLGROUP_GREAT_BEAMS
            secondaryGroupCooldown = 2000
        }
        val caster = caster()

        engine.postCastSpell(caster, first, payCost = false)

        assertThat(engine.playerSpellCheck(caster, sibling)).isFalse()
    }

    // Transcribed conjure data: `light_magic_missile_rune.lua`
    // group("support"), cooldown/groupCooldown 2s, mana 120, soul 1.
    @Test
    fun `conjuring a rune pays the script cost and applies the support group cooldown`() {
        val conjure = InstantSpell().apply {
            spellType = SpellType.SPELL_INSTANT
            name = "Light Magic Missile Rune"
            words = "adori min vis"
            group = SpellGroup.SPELLGROUP_SUPPORT
            cooldown = 2000
            groupCooldown = 2000
            level = 15
            mana = 120
            soul = 1
            aggressive = false
            addVocMap(1, true)
            addVocMap(2, true)
            addVocMap(5, true)
            addVocMap(6, true)
        }
        val caster = FakeSpellCaster(
            levelValue = 20,
            manaValue = 200,
            soulValue = 5,
            vocationIdValue = 1,
        )

        assertThat(engine.playerCastInstant(caster, conjure)).isTrue()

        assertThat(caster.manaValue).isEqualTo(80)
        assertThat(caster.soulValue).isEqualTo(4)
        assertThat(groupCooldownOf(caster).subId).isEqualTo(SpellGroup.SPELLGROUP_SUPPORT.value)
        assertThat(groupCooldownOf(caster).ticks).isEqualTo(2000)
    }
}
