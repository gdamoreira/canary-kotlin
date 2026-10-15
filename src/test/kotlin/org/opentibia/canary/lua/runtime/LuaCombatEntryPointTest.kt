package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.luaj.vm2.LuaValue
import org.opentibia.canary.combat.CombatTargetState
import org.opentibia.canary.combat.FakeCombatEffects
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the Lua combat entry points routed through the wave-64
 * combat core:
 *
 * - `GlobalFunctions::luaDoTargetCombatHealth`/`luaDoTargetCombatMana`/
 *   `luaDoTargetCombatCondition` (src/lua/functions/core/game/global_functions.cpp)
 *   over the production `CreatureCombatTarget` adapter and
 *   `Combat::doCombatHealth`/`doCombatMana`/`doCombatCondition`
 *   (src/creatures/combat/combat.cpp:1645-1798).
 * - `GlobalFunctions::luaDoAreaCombatHealth` over the `Combat::CombatFunc`
 *   area dispatch (combat.cpp:1507-1643) and the
 *   `g_luaEnvironment().createAreaObject`/`getAreaObject` registry
 *   (lua_environment.cpp:97-109).
 * - `CombatFunctions::luaCombatExecute` (combat_functions.cpp:165-250) over
 *   `Combat::doCombat` (combat.cpp:1461-1505).
 */
class LuaCombatEntryPointTest {

    private class Fixture {
        val map = GameMap()
        val creatures = mutableMapOf<Long, Creature>()
        val attackedByPlayer = mutableListOf<Pair<Monster, Player>>()
        val effects = FakeCombatEffects()

        val world = WorldLuaOperations(
            gameMap = map,
            combatTargetState = object : CombatTargetState {
                override fun onAttackedByPlayer(monster: Monster, attackerPlayer: Player) {
                    attackedByPlayer += monster to attackerPlayer
                }
            },
            combatEffects = effects,
            creatureById = { id -> creatures[id] },
        )

        val env: LuaEnvironment = LuaEnvironment().apply { initState() }.also { world.install(it) }

        fun wrap(name: String, creature: Creature): LuaValue = env.userdata.wrap(name, creature)

        fun place(creature: Creature, position: Position = creature.position) {
            creatures[creature.id] = creature
            map.getOrCreateTile(position).addThing(creature)
        }

        fun caster(): Monster =
            Monster(id = 1, name = "Caster", maxHealth = 100, health = 100, position = Position(100, 100, 7))
    }

    @Test
    fun `doTargetCombatHealth fires the production adapter`() {
        val fixture = Fixture()
        val caster = Player(1, "Caster", 100, 100, position = Position(100, 100, 7))
        val target = Monster(id = 2, name = "Rat", maxHealth = 100, health = 100, position = Position(102, 100, 7))
        fixture.place(target)
        fixture.env.globals.set("caster", fixture.wrap("Player", caster))
        fixture.env.globals.set("target", fixture.wrap("Monster", target))

        fixture.env.loadChunk(
            "result = doTargetCombatHealth(caster, target, COMBAT_FIREDAMAGE, -30, -30, CONST_ME_NONE)",
        )

        assertThat(fixture.env.getGlobal("result").toboolean()).isTrue()
        // `Combat::doCombatHealth` -> `CombatHealthFunc` drains through
        // `CreatureCombatTarget`.
        assertThat(target.health).isEqualTo(70)
        // `Combat::CombatHealthFunc`'s player-attacking-monster branch
        // (combat.cpp:895-899) reaches the injected state through the adapter.
        assertThat(fixture.attackedByPlayer).containsExactly(target to caster)
    }

    @Test
    fun `doTargetCombatHealth heals through the adapter`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val target = Monster(id = 2, name = "Rat", maxHealth = 100, health = 50, position = Position(102, 100, 7))
        fixture.env.globals.set("caster", fixture.wrap("Monster", caster))
        fixture.env.globals.set("target", fixture.wrap("Monster", target))

        fixture.env.loadChunk(
            "result = doTargetCombatHealth(caster, target, COMBAT_HEALING, 30, 30, CONST_ME_NONE)",
        )

        assertThat(fixture.env.getGlobal("result").toboolean()).isTrue()
        assertThat(target.health).isEqualTo(80)
    }

    @Test
    fun `doTargetCombatMana drains the target mana through the adapter`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val target = Player(2, "Target", 100, 100, maxMana = 100, mana = 100, position = Position(102, 100, 7))
        fixture.env.globals.set("caster", fixture.wrap("Monster", caster))
        fixture.env.globals.set("target", fixture.wrap("Player", target))

        fixture.env.loadChunk(
            "result = doTargetCombatMana(caster, target, -40, -40, CONST_ME_NONE)",
        )

        assertThat(fixture.env.getGlobal("result").toboolean()).isTrue()
        assertThat(target.mana).isEqualTo(60)
    }

    @Test
    fun `doTargetCombatCondition applies the condition through the combat core`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val target = Monster(id = 2, name = "Rat", maxHealth = 100, health = 100, position = Position(102, 100, 7))
        fixture.env.globals.set("caster", fixture.wrap("Monster", caster))
        fixture.env.globals.set("target", fixture.wrap("Monster", target))

        fixture.env.loadChunk(
            """
            condition = Condition(CONDITION_POISON, CONDITIONID_COMBAT)
            condition:setParameter(CONDITION_PARAM_TICKS, 5000)
            condition:setParameter(CONDITION_PARAM_MINVALUE, -10)
            condition:setParameter(CONDITION_PARAM_MAXVALUE, -10)
            condition:setParameter(CONDITION_PARAM_TICKINTERVAL, 1000)
            result = doTargetCombatCondition(caster, target, condition, CONST_ME_NONE)
            """.trimIndent(),
        )

        assertThat(fixture.env.getGlobal("result").toboolean()).isTrue()
        // `Combat::doCombatCondition` -> `CombatConditionFunc` clones the
        // condition and stamps the caster owner (combat.cpp:1079-1088).
        assertThat(target.getCondition(ConditionType.CONDITION_POISON)).isNotNull()
        assertThat(target.conditions.single().type).isEqualTo(ConditionType.CONDITION_POISON)
    }

    @Test
    fun `doAreaCombatHealth consumes the registered area list`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val centreTarget = Monster(id = 2, name = "Rat", maxHealth = 100, health = 100, position = Position(101, 100, 7))
        val northTarget = Monster(id = 3, name = "Rat", maxHealth = 100, health = 100, position = Position(101, 99, 7))
        val southTarget = Monster(id = 4, name = "Rat", maxHealth = 100, health = 100, position = Position(101, 101, 7))
        val farTarget = Monster(id = 5, name = "Rat", maxHealth = 100, health = 100, position = Position(105, 100, 7))
        fixture.place(centreTarget)
        fixture.place(northTarget)
        fixture.place(southTarget)
        fixture.place(farTarget)
        fixture.env.globals.set("caster", fixture.wrap("Monster", caster))

        fixture.env.loadChunk(
            """
            areaId = createCombatArea({{1, 3, 1}})
            result = doAreaCombatHealth(caster, COMBAT_FIREDAMAGE, Position(101, 100, 7), areaId, -30, -30, CONST_ME_NONE)
            """.trimIndent(),
        )

        assertThat(fixture.env.getGlobal("areaId").isnumber()).isTrue()
        assertThat(fixture.env.getGlobal("result").toboolean()).isTrue()
        // The `[1,3,1]` row rotated East marks (101,99)..(101,101).
        assertThat(listOf(centreTarget.health, northTarget.health, southTarget.health)).containsExactly(70, 70, 70)
        assertThat(farTarget.health).isEqualTo(100)
    }

    @Test
    fun `doAreaCombatHealth with area zero strikes the bare target tile`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val target = Monster(id = 2, name = "Rat", maxHealth = 100, health = 100, position = Position(101, 100, 7))
        val neighbour = Monster(id = 3, name = "Rat", maxHealth = 100, health = 100, position = Position(101, 101, 7))
        fixture.place(target)
        fixture.place(neighbour)
        fixture.env.globals.set("caster", fixture.wrap("Monster", caster))

        fixture.env.loadChunk(
            "result = doAreaCombatHealth(caster, COMBAT_FIREDAMAGE, Position(101, 100, 7), 0, -30, -30, CONST_ME_NONE)",
        )

        // The C++ `if (area || areaId == 0)` gate admits the null-area strike on
        // the bare target tile (global_functions.cpp:323-325).
        assertThat(fixture.env.getGlobal("result").toboolean()).isTrue()
        assertThat(target.health).isEqualTo(70)
        assertThat(neighbour.health).isEqualTo(100)
    }

    @Test
    fun `doAreaCombatCondition applies the condition to the area targets`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val target = Monster(id = 2, name = "Rat", maxHealth = 100, health = 100, position = Position(101, 100, 7))
        val northTarget = Monster(id = 3, name = "Rat", maxHealth = 100, health = 100, position = Position(101, 99, 7))
        fixture.place(target)
        fixture.place(northTarget)
        fixture.env.globals.set("caster", fixture.wrap("Monster", caster))

        fixture.env.loadChunk(
            """
            areaId = createCombatArea({{1, 3, 1}})
            condition = Condition(CONDITION_POISON, CONDITIONID_COMBAT)
            condition:setParameter(CONDITION_PARAM_TICKS, 5000)
            condition:setParameter(CONDITION_PARAM_MINVALUE, -10)
            condition:setParameter(CONDITION_PARAM_MAXVALUE, -10)
            condition:setParameter(CONDITION_PARAM_TICKINTERVAL, 1000)
            result = doAreaCombatCondition(caster, Position(101, 100, 7), areaId, condition, CONST_ME_NONE)
            """.trimIndent(),
        )

        assertThat(fixture.env.getGlobal("result").toboolean()).isTrue()
        // `Combat::doCombatCondition` -> `CombatFunc(..., CombatConditionFunc, ...)`
        // clones the template condition per target (combat.cpp:1079-1088, 1761-1764).
        assertThat(target.getCondition(ConditionType.CONDITION_POISON)).isNotNull()
        assertThat(northTarget.getCondition(ConditionType.CONDITION_POISON)).isNotNull()
    }

    @Test
    fun `doAreaCombatHealth rejects an unknown area id`() {
        val fixture = Fixture()
        fixture.env.globals.set("caster", fixture.wrap("Monster", fixture.caster()))

        fixture.env.loadChunk(
            "result = doAreaCombatHealth(caster, COMBAT_FIREDAMAGE, Position(101, 100, 7), 999, -30, -30, CONST_ME_NONE)",
        )

        // combat.cpp's area-not-found branch pushes false (global_functions.cpp:326-333).
        assertThat(fixture.env.getGlobal("result").toboolean()).isFalse()
    }

    @Test
    fun `combat execute dispatches the number variant through the adapter`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val target = Monster(id = 2, name = "Rat", maxHealth = 100, health = 100, position = Position(104, 100, 7))
        fixture.place(target)
        fixture.env.globals.set("caster", fixture.wrap("Monster", caster))

        fixture.env.loadChunk(
            """
            combat = Combat()
            combat:setParameter(COMBAT_PARAM_TYPE, COMBAT_FIREDAMAGE)
            combat:setFormula(COMBAT_FORMULA_DAMAGE, -40, 0, -40, 0)
            executed = combat:execute(caster, { type = 1, number = 2, instantName = 'Test Strike' })
            """.trimIndent(),
        )

        assertThat(fixture.env.getGlobal("executed").toboolean()).isTrue()
        assertThat(target.health).isEqualTo(60)
    }

    @Test
    fun `combat execute without an area posts the target position effects`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        fixture.env.globals.set("caster", fixture.wrap("Monster", caster))

        fixture.env.loadChunk(
            """
            combat = Combat()
            combat:setParameter(COMBAT_PARAM_TYPE, COMBAT_FIREDAMAGE)
            executed = combat:execute(caster, { type = 3, pos = Position(105, 100, 7) })
            """.trimIndent(),
        )

        assertThat(fixture.env.getGlobal("executed").toboolean()).isTrue()
        // combat_functions.cpp's VARIANT_TARGETPOSITION without an area:
        // postCombatEffects + `g_game().addMagicEffect(pos, CONST_ME_POFF)`.
        assertThat(fixture.effects.magicEffects.map { it.position to it.effect })
            .containsExactly(Position(105, 100, 7) to 3)
    }

    @Test
    fun `combat execute with a registered area consumes the area list`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val target = Monster(id = 2, name = "Rat", maxHealth = 100, health = 100, position = Position(101, 100, 7))
        fixture.place(target)
        fixture.env.globals.set("caster", fixture.wrap("Monster", caster))

        fixture.env.loadChunk(
            """
            areaId = createCombatArea({{3}})
            combat = Combat()
            combat:setParameter(COMBAT_PARAM_TYPE, COMBAT_FIREDAMAGE)
            combat:setFormula(COMBAT_FORMULA_DAMAGE, -40, 0, -40, 0)
            combat:setArea(areaId)
            executed = combat:execute(caster, { type = 2, pos = Position(101, 100, 7) })
            """.trimIndent(),
        )

        assertThat(fixture.env.getGlobal("executed").toboolean()).isTrue()
        assertThat(target.health).isEqualTo(60)
    }

    @Test
    fun `combat execute resolves the level magic callback through the Lua bridge`() {
        val fixture = Fixture()
        val caster = Player(1, "Caster", 100, 100, position = Position(100, 100, 7)).apply {
            level = 10
            magLevel = 5
        }
        val target = Monster(id = 2, name = "Rat", maxHealth = 100, health = 100, position = Position(102, 100, 7))
        fixture.place(target)
        fixture.env.globals.set("caster", fixture.wrap("Player", caster))

        fixture.env.loadChunk(
            """
            function onGetFormulaValues(player, level, magicLevel)
                lastFormula = { name = player:getName(), level = level, magicLevel = magicLevel }
                return -20, -20
            end
            combat = Combat()
            combat:setParameter(COMBAT_PARAM_TYPE, COMBAT_FIREDAMAGE)
            combat:setCallback(CALLBACK_PARAM_LEVELMAGICVALUE, 'onGetFormulaValues')
            executed = combat:execute(caster, { type = 1, number = 2, instantName = 'Practise Healing' })
            """.trimIndent(),
        )

        assertThat(fixture.env.getGlobal("executed").toboolean()).isTrue()
        // `ValueCallback::getMinMaxValues` pushes `(player, level, magicLevel)`
        // (combat.cpp:2001-2006) and the returned pair drives the damage.
        val formula = fixture.env.getGlobal("lastFormula")
        assertThat(formula.get("name").tojstring()).isEqualTo("Caster")
        assertThat(formula.get("level").toint()).isEqualTo(10)
        assertThat(formula.get("magicLevel").toint()).isEqualTo(5)
        assertThat(target.health).isEqualTo(80)
    }

    @Test
    fun `combat execute fires the target callback with the creature userdata`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val target = Monster(id = 2, name = "Rat", maxHealth = 100, health = 100, position = Position(102, 100, 7))
        fixture.place(target)
        fixture.env.globals.set("caster", fixture.wrap("Monster", caster))

        fixture.env.loadChunk(
            """
            function onTargetCombat(creature, target)
                lastTarget = { casterName = creature:getName(), targetName = target:getName() }
            end
            combat = Combat()
            combat:setParameter(COMBAT_PARAM_TYPE, COMBAT_FIREDAMAGE)
            combat:setFormula(COMBAT_FORMULA_DAMAGE, -40, 0, -40, 0)
            combat:setCallback(CALLBACK_PARAM_TARGETCREATURE, 'onTargetCombat')
            executed = combat:execute(caster, { type = 1, number = 2 })
            """.trimIndent(),
        )

        assertThat(fixture.env.getGlobal("executed").toboolean()).isTrue()
        val callback = fixture.env.getGlobal("lastTarget")
        assertThat(callback.get("casterName").tojstring()).isEqualTo("Caster")
        assertThat(callback.get("targetName").tojstring()).isEqualTo("Rat")
    }

    @Test
    fun `combat execute with an unknown target id reports false`() {
        val fixture = Fixture()
        fixture.env.globals.set("caster", fixture.wrap("Monster", fixture.caster()))

        fixture.env.loadChunk(
            """
            combat = Combat()
            combat:setParameter(COMBAT_PARAM_TYPE, COMBAT_FIREDAMAGE)
            executed = combat:execute(caster, { type = 1, number = 404 })
            """.trimIndent(),
        )

        // `g_game().getCreatureByID` miss pushes false (combat_functions.cpp:186-190).
        assertThat(fixture.env.getGlobal("executed").toboolean()).isFalse()
    }
}
