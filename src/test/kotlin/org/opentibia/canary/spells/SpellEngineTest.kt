package org.opentibia.canary.spells

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.combat.condition.ZoneType
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

class SpellEngineTest {

    private val world = FakeSpellWorld(learnSpells = false, rateSpellCooldown = 1.0)
    private val scripts = FakeSpellScriptRunner()
    private val registry = Spells()
    private val engine = SpellEngine(registry, world, scripts)

    private fun healSpell(mana: Int = 20): InstantSpell =
        InstantSpell().apply {
            spellType = SpellType.SPELL_INSTANT
            name = "Light Healing"
            words = "exura"
            spellId = 1
            group = SpellGroup.SPELLGROUP_HEALING
            level = 1
            this.mana = mana
            cooldown = 1000
            groupCooldown = 1000
            soundCastEffect = SoundEffect.SILENCE
        }

    private fun caster(
        level: Int = 10,
        magicLevel: Int = 0,
        mana: Int = 100,
        soul: Int = 5,
    ): FakeSpellCaster = FakeSpellCaster(
        levelValue = level,
        magicLevelValue = magicLevel,
        manaValue = mana,
        soulValue = soul,
        vocationIdValue = 2,
    )

    // `Spell::playerSpellCheck` (spells.cpp:494-615).

    @Test
    fun `spell check rejects a caster that cannot use spells without a message`() {
        val caster = caster()
        caster.setFlag(PlayerFlags_t.CannotUseSpells)

        assertThat(engine.playerSpellCheck(caster, healSpell())).isFalse()
        assertThat(caster.cancelMessages).isEmpty()
    }

    @Test
    fun `spell check lets ignored spell checks through`() {
        val caster = caster()
        caster.setFlag(PlayerFlags_t.IgnoreSpellCheck)

        assertThat(engine.playerSpellCheck(caster, healSpell())).isTrue()
    }

    @Test
    fun `spell check rejects a disabled spell`() {
        val spell = healSpell().apply { enabled = false }

        assertThat(engine.playerSpellCheck(caster(), spell)).isFalse()
    }

    @Test
    fun `spell check rejects a caster below the required level`() {
        val spell = healSpell().apply { level = 30 }
        val caster = caster(level = 10)

        assertThat(engine.playerSpellCheck(caster, spell)).isFalse()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.NOTENOUGHLEVEL)
    }

    @Test
    fun `spell check rejects a caster below the required magic level`() {
        val spell = healSpell().apply { magicLevel = 5 }
        val caster = caster(magicLevel = 2)

        assertThat(engine.playerSpellCheck(caster, spell)).isFalse()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.NOTENOUGHMAGICLEVEL)
    }

    @Test
    fun `spell check rejects a caster without enough mana`() {
        val caster = caster(mana = 10)

        assertThat(engine.playerSpellCheck(caster, healSpell(mana = 20))).isFalse()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.NOTENOUGHMANA)
    }

    @Test
    fun `spell check lets infinite mana through`() {
        val caster = caster(mana = 0)
        caster.setFlag(PlayerFlags_t.HasInfiniteMana)

        assertThat(engine.playerSpellCheck(caster, healSpell(mana = 200))).isTrue()
    }

    @Test
    fun `spell check rejects a caster without enough soul`() {
        val spell = healSpell().apply { soul = 2 }
        val caster = caster(soul = 1)

        assertThat(engine.playerSpellCheck(caster, spell)).isFalse()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.NOTENOUGHSOUL)
    }

    @Test
    fun `spell check lets infinite soul through`() {
        val spell = healSpell().apply { soul = 200 }
        val caster = caster(soul = 0)
        caster.setFlag(PlayerFlags_t.HasInfiniteSoul)

        assertThat(engine.playerSpellCheck(caster, spell)).isTrue()
    }

    @Test
    fun `spell check requires learning when the learn spells config is on`() {
        val world = FakeSpellWorld(learnSpells = true)
        val engine = SpellEngine(registry, world, scripts)
        val spell = healSpell()
        val caster = caster()

        assertThat(engine.playerSpellCheck(caster, spell)).isFalse()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.YOUNEEDTOLEARNTHISSPELL)

        caster.book.learnInstantSpell(spell.name)
        assertThat(engine.playerSpellCheck(caster, spell)).isTrue()
    }

    @Test
    fun `spell check requires learning for needLearn spells when the config is off`() {
        val spell = healSpell().apply { learnable = true }
        val caster = caster()

        assertThat(engine.playerSpellCheck(caster, spell)).isFalse()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.YOUNEEDTOLEARNTHISSPELL)

        caster.book.learnInstantSpell(spell.name)
        assertThat(engine.playerSpellCheck(caster, spell)).isTrue()
    }

    @Test
    fun `spell check rejects a vocation that cannot use the spell`() {
        val spell = healSpell()
        spell.addVocMap(1, true)
        val caster = caster()
        caster.vocationIdValue = 2

        assertThat(engine.playerSpellCheck(caster, spell)).isFalse()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.YOURVOCATIONCANNOTUSETHISSPELL)
    }

    @Test
    fun `spell check lets game masters use any vocation spell`() {
        val spell = healSpell()
        spell.addVocMap(1, true)
        val caster = caster()
        caster.vocationIdValue = 2
        caster.groupIdValue = 4

        assertThat(engine.playerSpellCheck(caster, spell)).isTrue()
    }

    @Test
    fun `spell check requires a weapon for weapon spells`() {
        val spell = healSpell().apply { needWeapon = true }
        val caster = caster()
        caster.weaponTypeValue = WeaponType_t.WEAPON_WAND

        assertThat(engine.playerSpellCheck(caster, spell)).isFalse()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.YOUNEEDAWEAPONTOUSETHISSPELL)

        caster.weaponTypeValue = WeaponType_t.WEAPON_FIST
        assertThat(engine.playerSpellCheck(caster, spell)).isTrue()
    }

    @Test
    fun `spell check rejects non premium casters for premium spells`() {
        val spell = healSpell().apply { premium = true }
        val caster = caster()

        assertThat(engine.playerSpellCheck(caster, spell)).isFalse()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.YOUNEEDPREMIUMACCOUNT)

        caster.premiumValue = true
        assertThat(engine.playerSpellCheck(caster, spell)).isTrue()
    }

    @Test
    fun `spell check rejects aggressive spells in a protection zone`() {
        val spell = healSpell()
        val caster = caster()
        caster.zoneTypeValue = ZoneType.ZONE_PROTECTION

        assertThat(engine.playerSpellCheck(caster, spell)).isFalse()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.ACTIONNOTPERMITTEDINPROTECTIONZONE)

        caster.setFlag(PlayerFlags_t.IgnoreProtectionZone)
        assertThat(engine.playerSpellCheck(caster, spell)).isTrue()
    }

    @Test
    fun `spell check rejects black skulled casters without an attacked creature`() {
        val spell = healSpell().apply { range = 5 }
        val caster = caster()
        caster.skullValue = Skulls_t.SKULL_BLACK

        assertThat(engine.playerSpellCheck(caster, spell)).isFalse()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.NOTPOSSIBLE)

        caster.attackedTarget = FakeSpellTarget()
        assertThat(engine.playerSpellCheck(caster, spell)).isTrue()
    }

    @Test
    fun `spell check rejects pacified aggressive casters`() {
        val spell = healSpell()
        val caster = caster()
        caster.conditions.add(
            org.opentibia.canary.combat.condition.ConditionGeneric(
                org.opentibia.canary.combat.condition.ConditionId.CONDITIONID_COMBAT,
                ConditionType.CONDITION_PACIFIED,
                ticks = 1000,
            ),
        )

        assertThat(engine.playerSpellCheck(caster, spell)).isFalse()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.YOUAREEXHAUSTED)
    }

    @Test
    fun `spell check rejects while a spell or group cooldown is active`() {
        val spell = healSpell()
        val caster = caster()

        engine.applyCooldownConditions(caster, spell)

        assertThat(engine.playerSpellCheck(caster, spell)).isFalse()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.YOUAREEXHAUSTED)
    }

    // `Spell::playerInstantSpellCheck` (spells.cpp:617-654).

    @Test
    fun `instant check rejects stairs changes`() {
        val caster = caster()
        caster.zoneTypeValue = ZoneType.ZONE_NORMAL
        val spell = healSpell()

        assertThat(engine.playerInstantSpellCheck(caster, spell, Position(100, 100, 6))).isFalse()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.FIRSTGOUPSTAIRS)

        caster.cancelMessages.clear()
        assertThat(engine.playerInstantSpellCheck(caster, spell, Position(100, 100, 8))).isFalse()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.FIRSTGODOWNSTAIRS)
    }

    @Test
    fun `instant check rejects blocked tiles`() {
        val spell = healSpell().apply { blockingSolid = true }
        val caster = caster()
        world.tileBlockingSolid = { true }

        assertThat(engine.playerInstantSpellCheck(caster, spell, Position(101, 100, 7))).isFalse()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.NOTENOUGHROOM)
    }

    @Test
    fun `instant check accepts the 0xFFFF self position`() {
        val spell = healSpell().apply { blockingSolid = true }
        val caster = caster()
        world.tileBlockingSolid = { true }

        assertThat(engine.playerInstantSpellCheck(caster, spell, Position(0xFFFF, 0, 0))).isTrue()
    }

    // `InstantSpell::playerCastInstant` (spells.cpp:1171-1297).

    @Test
    fun `casting an instant spell executes the script and pays mana`() {
        val spell = healSpell(mana = 20)
        val caster = caster(mana = 100, soul = 5)

        assertThat(engine.playerCastInstant(caster, spell)).isTrue()

        assertThat(scripts.instantCalls).hasSize(1)
        val variant = scripts.instantCalls[0].third
        assertThat(variant.type).isEqualTo(LuaVariantType.VARIANT_POSITION)
        assertThat(variant.pos).isEqualTo(caster.getPosition())
        assertThat(caster.manaValue).isEqualTo(80)
        assertThat(caster.manaSpent).isEqualTo(20)
    }

    @Test
    fun `casting does not pay or cool down when the script refuses`() {
        val spell = healSpell(mana = 20)
        val caster = caster(mana = 100)
        scripts.instantResult = false

        assertThat(engine.playerCastInstant(caster, spell)).isFalse()

        assertThat(caster.manaValue).isEqualTo(100)
        assertThat(caster.conditions).isEmpty()
    }

    @Test
    fun `casting a self target spell sends the caster number variant`() {
        val spell = healSpell().apply { selfTarget = true }
        val caster = caster()

        assertThat(engine.playerCastInstant(caster, spell)).isTrue()

        val variant = scripts.instantCalls.single().third
        assertThat(variant.type).isEqualTo(LuaVariantType.VARIANT_NUMBER)
        assertThat(variant.number).isEqualTo(caster.getID())
    }

    @Test
    fun `casting a direction spell targets the tile the caster faces`() {
        val spell = healSpell().apply { needDirection = true }
        val caster = caster()
        caster.currentDirection = Direction.East

        assertThat(engine.playerCastInstant(caster, spell)).isTrue()

        val variant = scripts.instantCalls.single().third
        assertThat(variant.type).isEqualTo(LuaVariantType.VARIANT_POSITION)
        assertThat(variant.pos).isEqualTo(Position(101, 100, 7))
    }

    @Test
    fun `casting a targeted spell sends the target number`() {
        val spell = healSpell().apply { needTarget = true }
        val caster = caster()
        val target = FakeSpellTarget(id = 42)
        caster.attackedTarget = target

        assertThat(engine.playerCastInstant(caster, spell)).isTrue()

        val variant = scripts.instantCalls.single().third
        assertThat(variant.type).isEqualTo(LuaVariantType.VARIANT_NUMBER)
        assertThat(variant.number).isEqualTo(42)
    }

    @Test
    fun `casting a targeted spell without a target cancels`() {
        val spell = healSpell().apply { needTarget = true }
        val caster = caster()

        assertThat(engine.playerCastInstant(caster, spell)).isFalse()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.YOUCANONLYUSEITONCREATURES)
        assertThat(scripts.instantCalls).isEmpty()
    }

    @Test
    fun `casting a target or direction spell falls back to the facing tile`() {
        val spell = healSpell().apply { casterTargetOrDirection = true }
        val caster = caster()
        caster.currentDirection = Direction.West

        assertThat(engine.playerCastInstant(caster, spell)).isTrue()

        val variant = scripts.instantCalls.single().third
        assertThat(variant.type).isEqualTo(LuaVariantType.VARIANT_POSITION)
        assertThat(variant.pos).isEqualTo(Position(99, 100, 7))
    }

    @Test
    fun `casting a string param spell hands the param to the script`() {
        val spell = healSpell().apply {
            hasParam = true
            hasPlayerNameParam = false
        }
        val caster = caster()

        assertThat(engine.playerCastInstant(caster, spell, "hello")).isTrue()

        val variant = scripts.instantCalls.single().third
        assertThat(variant.type).isEqualTo(LuaVariantType.VARIANT_STRING)
        assertThat(variant.text).isEqualTo("hello")
    }

    @Test
    fun `casting a player name param spell resolves the target name`() {
        val spell = healSpell().apply {
            hasParam = true
            hasPlayerNameParam = true
        }
        val caster = caster()
        world.playerByName = { FakeSpellTarget(id = 9, name = "Thomaz") }

        assertThat(engine.playerCastInstant(caster, spell, "thomaz")).isTrue()

        val variant = scripts.instantCalls.single().third
        assertThat(variant.type).isEqualTo(LuaVariantType.VARIANT_STRING)
        assertThat(variant.text).isEqualTo("Thomaz")
    }

    @Test
    fun `casting an unresolved player name applies the cooldown and cancels`() {
        val spell = healSpell().apply {
            hasParam = true
            hasPlayerNameParam = true
        }
        val caster = caster()
        world.playerByName = { null }

        assertThat(engine.playerCastInstant(caster, spell, "nobody")).isFalse()

        // C++ applies the cooldown conditions before cancelling (spells.cpp:1250-1258).
        assertThat(caster.conditions).isNotEmpty()
        assertThat(scripts.instantCalls).isEmpty()
    }

    @Test
    fun `casting on self is rejected when allowOnSelf is false`() {
        val spell = healSpell().apply {
            hasParam = true
            hasPlayerNameParam = true
            allowOnSelf = false
        }
        val caster = caster()
        world.playerByName = { FakeSpellTarget(id = caster.getID(), name = caster.getName()) }

        assertThat(engine.playerCastInstant(caster, spell, caster.getName())).isFalse()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `powerless casters cannot cast attack group spells`() {
        val spell = healSpell().apply { group = SpellGroup.SPELLGROUP_ATTACK }
        val caster = caster()
        caster.conditions.add(
            org.opentibia.canary.combat.condition.ConditionGeneric(
                org.opentibia.canary.combat.condition.ConditionId.CONDITIONID_COMBAT,
                ConditionType.CONDITION_POWERLESS,
                ticks = 1000,
            ),
        )

        assertThat(engine.playerCastInstant(caster, spell)).isFalse()
        assertThat(scripts.instantCalls).isEmpty()
    }

    @Test
    fun `pz locked spells add fight ticks on pvp worlds`() {
        val spell = healSpell().apply {
            pzLocked = true
            aggressive = false
        }
        val pvpCaster = caster()

        assertThat(engine.playerCastInstant(pvpCaster, spell)).isTrue()
        assertThat(pvpCaster.fightTicks).hasSize(1)

        world.worldType = WorldType.NO_PVP
        val noPvpCaster = caster()

        assertThat(engine.playerCastInstant(noPvpCaster, spell)).isTrue()
        assertThat(noPvpCaster.fightTicks).isEmpty()
    }

    @Test
    fun `aggressive casts add fight ticks and aggressive action ticks`() {
        val spell = healSpell()
        val caster = caster()

        engine.postCastSpell(caster, spell)

        assertThat(caster.fightTicks).hasSize(1)
        assertThat(caster.aggressiveActions).hasSize(1)
    }

    // `Spell::postCastSpell` (spells.cpp:903-946).

    @Test
    fun `post cast spawns a spell cooldown and a group cooldown`() {
        val spell = healSpell().apply {
            cooldown = 2000
            groupCooldown = 2000
            group = SpellGroup.SPELLGROUP_HEALING
        }
        val caster = caster()

        engine.postCastSpell(caster, spell)

        val spellCooldown = caster.conditions.first { it.type == ConditionType.CONDITION_SPELLCOOLDOWN }
        assertThat(spellCooldown.subId).isEqualTo(spell.spellId)
        assertThat(spellCooldown.ticks).isEqualTo(2000)

        val groupCooldown = caster.conditions.first { it.type == ConditionType.CONDITION_SPELLGROUPCOOLDOWN }
        assertThat(groupCooldown.subId).isEqualTo(SpellGroup.SPELLGROUP_HEALING.value)
        assertThat(groupCooldown.ticks).isEqualTo(2000)
    }

    @Test
    fun `no exhaustion flag skips the cooldown conditions`() {
        val spell = healSpell()
        val caster = caster()
        caster.setFlag(PlayerFlags_t.HasNoExhaustion)

        engine.postCastSpell(caster, spell)

        assertThat(caster.conditions).isEmpty()
    }

    @Test
    fun `post cast pays mana and soul`() {
        val spell = healSpell(mana = 20).apply { soul = 2 }
        val caster = caster(mana = 100, soul = 5)

        engine.postCastSpell(caster, spell)

        assertThat(caster.manaValue).isEqualTo(80)
        assertThat(caster.manaSpent).isEqualTo(20)
        assertThat(caster.soulValue).isEqualTo(3)
    }

    @Test
    fun `infinite soul flag skips the soul payment`() {
        val spell = healSpell(mana = 0).apply { soul = 2 }
        val caster = caster(soul = 5)
        caster.setFlag(PlayerFlags_t.HasInfiniteSoul)

        engine.postCastSpell(caster, spell)

        assertThat(caster.soulValue).isEqualTo(5)
    }

    @Test
    fun `finished cast false only pays the cost`() {
        val spell = healSpell(mana = 20)
        val caster = caster(mana = 100)

        engine.postCastSpell(caster, spell, finishedCast = false)

        assertThat(caster.manaValue).isEqualTo(80)
        assertThat(caster.conditions).isEmpty()
    }

    @Test
    fun `pay cost false skips the cost but applies the cooldown`() {
        val spell = healSpell(mana = 20)
        val caster = caster(mana = 100)

        engine.postCastSpell(caster, spell, payCost = false)

        assertThat(caster.manaValue).isEqualTo(100)
        assertThat(caster.conditions).isNotEmpty()
    }

    @Test
    fun `cast sound metadata dispatches the double sound effect`() {
        val spell = healSpell().apply {
            soundCastEffect = SoundEffect.SPELL_LIGHT_HEALING
            soundImpactEffect = SoundEffect.SPELL_SALVATION
        }
        val caster = caster()

        engine.postCastSpell(caster, spell)

        assertThat(world.doubleSounds).containsExactly(
            Triple(caster.getPosition(), SoundEffect.SPELL_LIGHT_HEALING, SoundEffect.SPELL_SALVATION),
        )
    }

    @Test
    fun `silent casts dispatch no sound`() {
        val spell = healSpell().apply { soundCastEffect = SoundEffect.SILENCE }
        val caster = caster()

        engine.postCastSpell(caster, spell)

        assertThat(world.doubleSounds).isEmpty()
    }
}
