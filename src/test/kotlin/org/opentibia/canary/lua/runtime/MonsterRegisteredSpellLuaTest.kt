package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.lua.bindings.MonsterRegisteredSpell
import org.opentibia.canary.lua.registry.LuaSpell
import org.opentibia.canary.lua.registry.SpellType
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the `onCastSpell` execution of a registry-backed monster
 * spell block: `spellBlock.spell->castSpell(getMonster(), target)`
 * (src/creatures/monsters/monster.cpp:1979, 2220) reaches
 * `InstantSpell::castSpell(creature, target)` (spells.cpp:1341-1348) and
 * `RuneSpell::castSpell(creature, target)` (spells.cpp:1569-1575), which build
 * the variant and push `(creature, variant[, isHotkey])` into the Lua
 * `onCastSpell` body.
 */
class MonsterRegisteredSpellLuaTest {

    private class Fixture(
        val registry: Monsters = Monsters(),
        val env: LuaEnvironment = LuaEnvironment().apply { initState() },
    ) {
        init {
            env.worldOperations!!.attachMonsters(registry)
        }
    }

    private fun caster(): Monster =
        Monster(id = 1, name = "Rat", maxHealth = 100, health = 100, position = Position(10, 10, 7))

    private fun target(): Monster =
        Monster(id = 2, name = "Hero", maxHealth = 100, health = 100, position = Position(12, 10, 7))

    private fun registeredAttack(fixture: Fixture): MonsterRegisteredSpell =
        fixture.registry.getMonsterType("rat")!!.info.attackSpells.single().spell as MonsterRegisteredSpell

    @Test
    fun `registered instant spell executes its lua body with the position variant`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local registered = Spell("instant")
            registered:name("Light Healing")
            registered:id(1)
            registered:words("exura")
            function registered.onCastSpell(...)
                local creature, variant = ...
                last = {
                    called = true,
                    creatureName = creature:getName(),
                    type = variant.type,
                    instantName = variant.instantName,
                    args = select("#", ...),
                }
                return true
            end
            registered:register()

            local mType = Game.createMonsterType("Rat")
            local heal = MonsterSpell()
            heal:setType("Light Healing")
            mType:addAttack(heal)
            """.trimIndent(),
        )

        val actor = registeredAttack(fixture)
        val caster = caster()
        actor.castSpell(caster, target())

        val last = fixture.env.getGlobal("last")
        assertThat(last.get("called").toboolean()).isTrue()
        assertThat(last.get("creatureName").tojstring()).isEqualTo("Rat")
        // `LuaVariantType::VARIANT_POSITION` ordinal 2.
        assertThat(last.get("type").toint()).isEqualTo(2)
        assertThat(last.get("instantName").tojstring()).isEqualTo("Light Healing")
        assertThat(last.get("args").toint()).isEqualTo(2)
    }

    @Test
    fun `registered instant spell with need target sends the target number variant`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local registered = Spell("instant")
            registered:name("Wound Cleansing")
            registered:id(2)
            registered:words("exura gran")
            registered:needTarget(true)
            function registered.onCastSpell(creature, variant)
                last = { type = variant.type, number = variant.number, instantName = variant.instantName }
                return true
            end
            registered:register()

            local mType = Game.createMonsterType("Rat")
            local heal = MonsterSpell()
            heal:setType("Wound Cleansing")
            mType:addAttack(heal)
            """.trimIndent(),
        )

        val actor = registeredAttack(fixture)
        actor.castSpell(caster(), target())

        val last = fixture.env.getGlobal("last")
        // `InstantSpell::castSpell(creature, target)` leaves `instantName` empty
        // on the needTarget branch (spells.cpp:1341-1345).
        assertThat(last.get("type").toint()).isEqualTo(1)
        assertThat(last.get("number").toint()).isEqualTo(2)
        assertThat(last.get("instantName").tojstring()).isEmpty()
    }

    @Test
    fun `registered instant spell with caster target or direction casts the attacked creature`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local registered = Spell("instant")
            registered:name("Ice Strike")
            registered:id(3)
            registered:words("exori frigo")
            registered:needCasterTargetOrDirection(true)
            function registered.onCastSpell(creature, variant)
                last = { type = variant.type, number = variant.number }
                return true
            end
            registered:register()

            local mType = Game.createMonsterType("Rat")
            local strike = MonsterSpell()
            strike:setType("Ice Strike")
            mType:addAttack(strike)
            """.trimIndent(),
        )

        val attacker = caster()
        val attacked = target()
        attacker.setAttackedCreature(attacked)
        attacker.position = Position(10, 10, 7)

        registeredAttack(fixture).castSpell(attacker, attacked)

        val last = fixture.env.getGlobal("last")
        // `InstantSpell::castSpell(creature)` (spells.cpp:1315-1328).
        assertThat(last.get("type").toint()).isEqualTo(1)
        assertThat(last.get("number").toint()).isEqualTo(2)
    }

    @Test
    fun `registered rune spell sends the rune variant and the hotkey flag`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local registered = Spell("rune")
            registered:name("Avalanche Rune")
            registered:id(4)
            registered:runeId(3161)
            function registered.onCastSpell(...)
                local creature, variant, isHotkey = ...
                last = {
                    type = variant.type,
                    number = variant.number,
                    runeName = variant.runeName,
                    hotkey = isHotkey,
                    args = select("#", ...),
                }
                return true
            end
            registered:register()

            local mType = Game.createMonsterType("Rat")
            local avalanche = MonsterSpell()
            avalanche:setType("Avalanche Rune")
            mType:addAttack(avalanche)
            """.trimIndent(),
        )

        val actor = registeredAttack(fixture)
        actor.castSpell(caster(), target())

        val last = fixture.env.getGlobal("last")
        assertThat(last.get("type").toint()).isEqualTo(1)
        assertThat(last.get("number").toint()).isEqualTo(2)
        assertThat(last.get("runeName").tojstring()).isEqualTo("Avalanche Rune")
        assertThat(last.get("hotkey").toboolean()).isFalse()
        assertThat(last.get("args").toint()).isEqualTo(3)
    }

    @Test
    fun `registered spell without a callback stays inert`() {
        val fixture = Fixture()
        val spell = LuaSpell(name = "Unwired Spell", spellType = SpellType.INSTANT)
        val actor = MonsterRegisteredSpell(spell)

        actor.castSpell(caster(), target())

        assertThat(fixture.env.getGlobal("last").isnil()).isTrue()
    }

    @Test
    fun `direction spells cast from a monster use the caster facing`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local registered = Spell("instant")
            registered:name("Light Beam")
            registered:id(5)
            registered:words("exevo vis")
            registered:needDirection(true)
            function registered.onCastSpell(creature, variant)
                last = { type = variant.type }
                return true
            end
            registered:register()

            local mType = Game.createMonsterType("Rat")
            local beam = MonsterSpell()
            beam:setType("Light Beam")
            mType:addAttack(beam)
            """.trimIndent(),
        )

        val attacker = caster()
        attacker.direction = Direction.East
        registeredAttack(fixture).castSpell(attacker, target())

        val last = fixture.env.getGlobal("last")
        // `InstantSpell::castSpell(creature)` needDirection branch (spells.cpp:1328-1331).
        assertThat(last.get("type").toint()).isEqualTo(2)
    }
}
