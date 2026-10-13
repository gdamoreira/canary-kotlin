package org.opentibia.canary.spells

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

class RuneSpellUseTest {

    private val world = FakeSpellWorld(learnSpells = false, rateSpellCooldown = 1.0)
    private val scripts = FakeSpellScriptRunner()
    private val engine = SpellEngine(Spells(), world, scripts)

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        // `playerRuneSpellCheck` inspects the destination tile, not the passed
        // target (`spells.cpp:697-708`), so every use test needs a visible
        // creature on the tile unless the test says otherwise.
        world.bottomVisibleCreature = { 2L }
    }

    // `data/scripts/runes/light_magic_missile.lua` engine state.
    private fun lightMagicMissileRune(charges: Int = 10): RuneSpell = RuneSpell().apply {
        spellType = SpellType.SPELL_RUNE
        name = "light magic missile rune"
        spellId = 7
        scriptId = 1
        group = SpellGroup.SPELLGROUP_ATTACK
        soundCastEffect = SoundEffect.SPELL_OR_RUNE
        soundImpactEffect = SoundEffect.SPELL_LIGHT_MAGIC_MISSILE_RUNE
        runeId = 3174
        allowFarUse = true
        this.charges = charges
        level = 15
        cooldown = 2000
        groupCooldown = 2000
        needTarget = true
        blockingSolid = true
        mana = 0
    }

    private fun caster(): FakeSpellCaster =
        FakeSpellCaster(levelValue = 20, manaValue = 100)

    private fun runeItem(count: Int = 10): Item = Item(id = 3174, count = count)

    // `RuneSpell::executeUse` (spells.cpp:1501-1559).

    @Test
    fun `using a rune executes the script with the target number`() {
        val rune = lightMagicMissileRune()
        val item = runeItem()
        val caster = caster()
        val target = FakeSpellTarget(id = 42)
        world.bottomVisibleCreature = { 42L }

        assertThat(engine.executeUse(caster, rune, item, target, Position(101, 100, 7), isHotkey = false))
            .isTrue()

        val call = scripts.runeCalls.single()
        assertThat(call.rune).isSameAs(rune)
        assertThat(call.variant.runeName).isEqualTo(rune.name)
        assertThat(call.variant.type).isEqualTo(LuaVariantType.VARIANT_NUMBER)
        assertThat(call.variant.number).isEqualTo(42)
        assertThat(call.isHotkey).isFalse()
    }

    @Test
    fun `using a rune consumes one charge when the config removes charges`() {
        val rune = lightMagicMissileRune()
        val item = runeItem(count = 10)
        val caster = caster()

        assertThat(engine.executeUse(caster, rune, item, FakeSpellTarget(), Position(101, 100, 7), false))
            .isTrue()

        assertThat(item.count).isEqualTo(9)
    }

    @Test
    fun `the last charge transforms the rune to zero`() {
        val rune = lightMagicMissileRune()
        val item = runeItem(count = 1)
        val caster = caster()

        assertThat(engine.executeUse(caster, rune, item, FakeSpellTarget(), Position(101, 100, 7), false))
            .isTrue()

        assertThat(item.count).isEqualTo(0)
    }

    @Test
    fun `chargeless runes keep their item count`() {
        val rune = lightMagicMissileRune(charges = 0)
        val item = runeItem(count = 10)
        val caster = caster()

        assertThat(rune.hasCharges).isFalse()
        assertThat(engine.executeUse(caster, rune, item, FakeSpellTarget(), Position(101, 100, 7), false))
            .isTrue()

        assertThat(item.count).isEqualTo(10)
    }

    @Test
    fun `the remove rune charges config can be disabled`() {
        world.removeRuneCharges = false
        val rune = lightMagicMissileRune()
        val item = runeItem(count = 10)
        val caster = caster()

        assertThat(engine.executeUse(caster, rune, item, FakeSpellTarget(), Position(101, 100, 7), false))
            .isTrue()

        assertThat(item.count).isEqualTo(10)
    }

    @Test
    fun `a refusing rune script pays nothing and consumes no charge`() {
        scripts.runeResult = false
        val rune = lightMagicMissileRune()
        val item = runeItem(count = 10)
        val caster = caster()

        assertThat(engine.executeUse(caster, rune, item, FakeSpellTarget(), Position(101, 100, 7), false))
            .isFalse()

        assertThat(item.count).isEqualTo(10)
        assertThat(caster.conditions).isEmpty()
    }

    @Test
    fun `an unloaded rune script refuses to cast`() {
        val rune = lightMagicMissileRune().apply { scriptId = 0 }
        val item = runeItem()
        val caster = caster()

        assertThat(engine.executeUse(caster, rune, item, FakeSpellTarget(), Position(101, 100, 7), false))
            .isFalse()
        assertThat(scripts.runeCalls).isEmpty()
    }

    @Test
    fun `a target rune without any visible creature cancels`() {
        val rune = lightMagicMissileRune()
        val item = runeItem()
        val caster = caster()
        world.bottomVisibleCreature = { null }

        assertThat(engine.executeUse(caster, rune, item, null, Position(101, 100, 7), false)).isFalse()

        assertThat(caster.cancelMessages).contains(ReturnValue.CANONLYUSETHISRUNEONCREATURES)
        assertThat(item.count).isEqualTo(10)
    }

    @Test
    fun `a position rune sends the target position variant`() {
        val rune = lightMagicMissileRune().apply {
            needTarget = false
            blockingSolid = false
        }
        val item = runeItem()
        val caster = caster()

        assertThat(engine.executeUse(caster, rune, item, null, Position(105, 100, 7), false)).isTrue()

        val call = scripts.runeCalls.single()
        assertThat(call.variant.type).isEqualTo(LuaVariantType.VARIANT_POSITION)
        assertThat(call.variant.pos).isEqualTo(Position(105, 100, 7))
    }

    @Test
    fun `a rune out of range cancels with destination out of reach`() {
        val rune = lightMagicMissileRune().apply { range = 3 }
        val item = runeItem()
        val caster = caster()
        world.canThrow = { _, _, _, range -> range != 3 }

        assertThat(engine.executeUse(caster, rune, item, FakeSpellTarget(), Position(110, 100, 7), false))
            .isFalse()

        assertThat(caster.cancelMessages).containsExactly(ReturnValue.DESTINATIONOUTOFREACH)
    }

    @Test
    fun `a rune on a blocked solid tile cancels`() {
        val rune = lightMagicMissileRune()
        val item = runeItem()
        val caster = caster()
        world.tileBlockingSolid = { true }
        world.bottomVisibleCreature = { null }

        assertThat(engine.executeUse(caster, rune, item, null, Position(101, 100, 7), false)).isFalse()

        assertThat(caster.cancelMessages).contains(ReturnValue.NOTENOUGHROOM)
    }

    @Test
    fun `powerless casters cannot use attack runes`() {
        val rune = lightMagicMissileRune()
        val item = runeItem()
        val caster = caster()
        caster.conditions.add(
            org.opentibia.canary.combat.condition.ConditionGeneric(
                org.opentibia.canary.combat.condition.ConditionId.CONDITIONID_COMBAT,
                ConditionType.CONDITION_POWERLESS,
                ticks = 1000,
            ),
        )

        assertThat(engine.executeUse(caster, rune, item, FakeSpellTarget(), Position(101, 100, 7), false))
            .isFalse()
        assertThat(item.count).isEqualTo(10)
    }

    @Test
    fun `rune use applies the rune cooldowns after a successful cast`() {
        val rune = lightMagicMissileRune()
        val caster = caster()

        assertThat(engine.executeUse(caster, rune, runeItem(), FakeSpellTarget(), Position(101, 100, 7), false))
            .isTrue()

        val spellCooldown = caster.conditions.first { it.type == ConditionType.CONDITION_SPELLCOOLDOWN }
        assertThat(spellCooldown.subId).isEqualTo(rune.spellId)
        assertThat(spellCooldown.ticks).isEqualTo(2000)

        val groupCooldown = caster.conditions.first { it.type == ConditionType.CONDITION_SPELLGROUPCOOLDOWN }
        assertThat(groupCooldown.subId).isEqualTo(SpellGroup.SPELLGROUP_ATTACK.value)
    }

    @Test
    fun `rune casts send the rune cast and impact sounds`() {
        val rune = lightMagicMissileRune()
        val caster = caster()

        assertThat(engine.executeUse(caster, rune, runeItem(), FakeSpellTarget(), Position(101, 100, 7), false))
            .isTrue()

        assertThat(world.doubleSounds).containsExactly(
            Triple(
                caster.getPosition(),
                SoundEffect.SPELL_OR_RUNE,
                SoundEffect.SPELL_LIGHT_MAGIC_MISSILE_RUNE,
            ),
        )
    }
}
