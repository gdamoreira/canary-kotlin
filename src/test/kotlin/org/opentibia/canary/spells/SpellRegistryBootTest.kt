package org.opentibia.canary.spells

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.lua.registry.LuaSpell
import org.opentibia.canary.lua.registry.SpellType as LuaSpellType
import org.opentibia.canary.lua.registry.Spells as LuaSpells

/**
 * Wave 65 B3: the boot recorder that replays the Lua registration path
 * (`Spell(type)` + `spell:register()`, spell_functions.cpp:91-206) into the
 * engine [Spells] registry through `registerInstantLuaEvent`/
 * `registerRuneLuaEvent` (spells.cpp:181-222), so `Spells::getSpellByName`
 * (spells.cpp:255-261) and the rune/vocation look text resolve the compiled
 * `InstantSpell`/`RuneSpell` actors instead of an empty registry.
 */
class SpellRegistryBootTest {

    @Test
    fun `recording an instant copies the registered state into the engine actor`() {
        val luaSpells = LuaSpells()
        luaSpells.registerInstant(
            LuaSpell(
                name = "Energy Strike",
                spellType = LuaSpellType.INSTANT,
                spellId = 88,
                words = "exori vis",
                level = 12,
                mana = 20,
                group = 1,
                groupCooldown = 2000,
                secondaryGroupCooldown = 1000,
                range = 3,
                premium = true,
                needTarget = true,
                needWeapon = false,
                aggressive = false,
                enabled = false,
                selfTarget = true,
                allowOnSelf = false,
                pzLocked = true,
                blockingSolid = true,
                blockingCreature = true,
                needDirection = true,
                hasParams = true,
                hasPlayerNameParam = true,
                needCasterTargetOrDirection = true,
                blockWalls = false,
                separator = ";",
                soundCastEffect = 10,
                soundImpactEffect = 1007,
                vocSpellMap = mutableMapOf(1 to true, 2 to false),
            ),
        )

        val registry = Spells()
        val recorded = SpellRegistryBoot.recordRegisteredSpells(luaSpells, registry)

        assertThat(recorded).isEqualTo(1)
        val actor = registry.getSpellByName("Energy Strike")
        assertThat(actor).isInstanceOf(InstantSpell::class.java)
        actor as InstantSpell
        assertThat(actor.isInstant()).isTrue()
        assertThat(actor.spellType).isEqualTo(SpellType.SPELL_INSTANT)
        assertThat(actor.name).isEqualTo("Energy Strike")
        assertThat(actor.words).isEqualTo("exori vis")
        assertThat(actor.separator).isEqualTo(";")
        assertThat(actor.spellId).isEqualTo(88)
        assertThat(actor.group).isEqualTo(SpellGroup.SPELLGROUP_ATTACK)
        assertThat(actor.cooldown).isEqualTo(1000)
        assertThat(actor.groupCooldown).isEqualTo(2000)
        assertThat(actor.secondaryGroupCooldown).isEqualTo(1000)
        assertThat(actor.level).isEqualTo(12)
        assertThat(actor.magicLevel).isZero()
        assertThat(actor.range).isEqualTo(3)
        assertThat(actor.mana).isEqualTo(20)
        assertThat(actor.manaPercent).isZero()
        assertThat(actor.soul).isZero()
        assertThat(actor.needWeapon).isFalse()
        assertThat(actor.blockingSolid).isTrue()
        assertThat(actor.blockingCreature).isTrue()
        assertThat(actor.aggressive).isFalse()
        assertThat(actor.isLearnable()).isFalse()
        assertThat(actor.enabled).isFalse()
        assertThat(actor.premium).isTrue()
        assertThat(actor.selfTarget).isTrue()
        assertThat(actor.needTarget).isTrue()
        assertThat(actor.allowOnSelf).isFalse()
        assertThat(actor.pzLocked).isTrue()
        assertThat(actor.soundCastEffect).isEqualTo(SoundEffect.SPELL_OR_RUNE)
        assertThat(actor.soundImpactEffect).isEqualTo(SoundEffect.SPELL_LIGHT_MAGIC_MISSILE_RUNE)
        assertThat(actor.vocSpellMap).containsEntry(1, true).containsEntry(2, false)
        assertThat(actor.hasParam).isTrue()
        assertThat(actor.hasPlayerNameParam).isTrue()
        assertThat(actor.needDirection).isTrue()
        assertThat(actor.casterTargetOrDirection).isTrue()
        assertThat(actor.checkLineOfSight).isFalse()
    }

    @Test
    fun `recording a rune copies the rune state into the engine actor`() {
        val luaSpells = LuaSpells()
        luaSpells.registerRune(
            LuaSpell(
                name = "explosion rune",
                spellType = LuaSpellType.RUNE,
                spellId = 18,
                runeId = 3200,
                charges = 3,
                level = 1,
                magicLevel = 2,
                group = 1,
                needTarget = true,
                allowFarUse = true,
                checkFloor = false,
                blockWalls = false,
                needLearn = true,
                vocSpellMap = mutableMapOf(3 to true),
            ),
        )

        val registry = Spells()
        val recorded = SpellRegistryBoot.recordRegisteredSpells(luaSpells, registry)

        assertThat(recorded).isEqualTo(1)
        val actor = registry.getSpellByName("explosion rune")
        assertThat(actor).isInstanceOf(RuneSpell::class.java)
        actor as RuneSpell
        assertThat(actor.isInstant()).isFalse()
        assertThat(actor.spellType).isEqualTo(SpellType.SPELL_RUNE)
        assertThat(actor.runeId).isEqualTo(3200)
        assertThat(actor.charges).isEqualTo(3)
        assertThat(actor.hasCharges).isTrue()
        assertThat(actor.level).isEqualTo(1)
        assertThat(actor.magicLevel).isEqualTo(2)
        assertThat(actor.needTarget).isTrue()
        assertThat(actor.allowFarUse).isTrue()
        assertThat(actor.checkFloor).isFalse()
        assertThat(actor.checkLineOfSight).isFalse()
        assertThat(actor.isLearnable()).isTrue()
        assertThat(actor.vocSpellMap).containsEntry(3, true)
        assertThat(registry.getRuneSpell(3200)).isSameAs(actor)
    }

    @Test
    fun `the rune wins over an instant registered with the same name`() {
        val luaSpells = LuaSpells()
        luaSpells.registerInstant(
            LuaSpell(
                name = "Shared Name",
                spellType = LuaSpellType.INSTANT,
                words = "exura shared",
            ),
        )
        luaSpells.registerRune(
            LuaSpell(
                name = "Shared Name",
                spellType = LuaSpellType.RUNE,
                runeId = 3174,
            ),
        )

        val registry = Spells()
        SpellRegistryBoot.recordRegisteredSpells(luaSpells, registry)

        assertThat(registry.getSpellByName("shared name")).isInstanceOf(RuneSpell::class.java)
    }

    @Test
    fun `recording is idempotent and only counts new registrations`() {
        val luaSpells = LuaSpells()
        luaSpells.registerInstant(
            LuaSpell(name = "summon challenge", spellType = LuaSpellType.INSTANT, words = "###1"),
        )
        luaSpells.registerRune(
            LuaSpell(name = "explosion rune", spellType = LuaSpellType.RUNE, runeId = 3200),
        )

        val registry = Spells()
        assertThat(SpellRegistryBoot.recordRegisteredSpells(luaSpells, registry)).isEqualTo(2)
        assertThat(SpellRegistryBoot.recordRegisteredSpells(luaSpells, registry)).isZero()
        assertThat(registry.getInstantSpells().keys).containsExactly("###1")
        assertThat(registry.getRuneSpell(3200)).isNotNull
    }
}
