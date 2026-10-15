package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.luaj.vm2.LuaValue
import org.opentibia.canary.combat.Combat
import org.opentibia.canary.combat.CombatTargetState
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.FakeCombatEffects
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.bindings.LuaCombatWorld
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Wave 68 E1 tests for the production completion of
 * [WorldLuaOperations.attachCombat]: the boot call must re-bind the Lua combat
 * entry points to the game-owned [CombatTargetState] and the `g_game()` effect
 * fanout, not only the map/lookups.
 *
 *  - `GlobalFunctions::luaDoTargetCombatHealth` (global_functions.cpp:348-397)
 *    reads the target state through `Creature::isImmune` (creature.cpp:955,
 *    combat.cpp:551);
 *  - `GlobalFunctions::luaDoAreaCombatHealth` (global_functions.cpp:310-346)
 *    fans the impact effect out through `g_game().addMagicEffect`
 *    (combat.cpp:1254);
 *  - [GameCombatEffects] is the production `g_game()` adapter over the live
 *    [Game]: the magic-effect/distance/sound fanout, the field-item placement
 *    of `Combat::combatTileEffects` (combat.cpp:1161-1247) and its owner stamp.
 */
class WorldLuaOperationsCombatBindingTest {

    private class Fixture {
        val map = GameMap()
        val world = WorldLuaOperations(gameMap = map)
        val env: LuaEnvironment = LuaEnvironment().apply { initState() }.also { world.install(it) }

        fun wrap(name: String, creature: Creature): LuaValue = env.userdata.wrap(name, creature)

        fun place(creature: Creature) {
            map.getOrCreateTile(creature.position).addThing(creature)
        }

        fun caster(): Monster =
            Monster(id = 1, name = "Caster", maxHealth = 100, health = 100, position = Position(100, 100, 7))
    }

    @Test
    fun `attachCombat rebinds the target state consulted by the Lua health entry point`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val target = Monster(id = 2, name = "Rat", maxHealth = 100, health = 100, position = Position(101, 100, 7))
        fixture.place(caster)
        fixture.place(target)
        fixture.env.globals.set("caster", fixture.wrap("Monster", caster))
        fixture.env.globals.set("target", fixture.wrap("Monster", target))

        // The inert default lets the fire damage through (70 hp left).
        fixture.env.loadChunk(
            "doTargetCombatHealth(caster, target, COMBAT_FIREDAMAGE, -30, -30, CONST_ME_NONE)",
        )
        assertThat(target.health).isEqualTo(70)

        val immuneState = object : CombatTargetState {
            var immuneChecks = 0

            override fun isImmune(creature: Creature, combatType: CombatType): Boolean {
                immuneChecks++
                return combatType == CombatType.FIREDAMAGE
            }
        }
        fixture.world.attachCombat(fixture.map, targetState = immuneState, effects = FakeCombatEffects())

        fixture.env.loadChunk(
            "doTargetCombatHealth(caster, target, COMBAT_FIREDAMAGE, -30, -30, CONST_ME_NONE)",
        )

        // `blockDamage` -> `Creature::isImmune(combatType)` (combat.cpp:551)
        // now resolves through the attached state: no damage applied.
        assertThat(immuneState.immuneChecks).isGreaterThanOrEqualTo(1)
        assertThat(target.health).isEqualTo(70)
    }

    @Test
    fun `attachCombat rebinds the effects fanout used by the Lua area entry point`() {
        val fixture = Fixture()
        val caster = fixture.caster()
        val target = Monster(id = 2, name = "Rat", maxHealth = 100, health = 100, position = Position(101, 100, 7))
        fixture.place(caster)
        fixture.place(target)
        fixture.env.globals.set("caster", fixture.wrap("Monster", caster))

        val effects = FakeCombatEffects()
        fixture.world.attachCombat(fixture.map, effects = effects)

        fixture.env.loadChunk(
            "result = doAreaCombatHealth(caster, COMBAT_FIREDAMAGE, Position(101, 100, 7), 0, -30, -30, CONST_ME_POFF)",
        )

        assertThat(fixture.env.getGlobal("result").toboolean()).isTrue()
        assertThat(target.health).isEqualTo(70)
        assertThat(effects.magicEffects.map { it.effect }).containsExactly(Combat.CONST_ME_POFF)
        assertThat(effects.magicEffects.single().position).isEqualTo(Position(101, 100, 7))
    }

    @Test
    fun `the production game effects adapter fans out and places field items`() {
        val map = GameMap()
        val game = Game(map, GameDispatcher())
        val position = Position(100, 100, 7)
        val tile = map.getOrCreateTile(position).apply { ground = Item(id = 106) }
        val player = Player(id = 1, name = "Hero", maxHealth = 100, health = 100, position = position)
        game.addPlayer(player)
        game.internalPlaceCreature(player, position)
        val caster = Monster(id = 2, name = "Caster", maxHealth = 100, health = 100, position = position)
        val world = LuaCombatWorld()

        val magicEffects = mutableListOf<Triple<Player, Position, Int>>()
        game.sendMagicEffectWithSource = { spectator, pos, effect, _ -> magicEffects += Triple(spectator, pos, effect) }
        val shots = mutableListOf<Pair<Position, Position>>()
        game.sendDistanceShoot = { _, fromPos, toPos, _, _ -> shots += fromPos to toPos }

        val effects = GameCombatEffects(game, itemTypes = null)

        // `g_game().addMagicEffect(position, effect, caster)` (combat.cpp:1254).
        effects.addMagicEffect(world.actorOf(caster), position, Combat.CONST_ME_POFF)
        assertThat(magicEffects.map { it.first }).containsExactly(player)
        assertThat(magicEffects.single().second).isEqualTo(position)
        assertThat(magicEffects.single().third).isEqualTo(Combat.CONST_ME_POFF)

        // `Combat::addDistanceEffect` (combat.cpp:1265-1275) over the game
        // spectator query.
        effects.addDistanceEffect(world.actorOf(caster), position, Position(103, 100, 7), Combat.CONST_ANI_NONE)
        assertThat(shots).containsExactly(position to Position(103, 100, 7))

        // `Combat::combatTileEffects`'s `Item::CreateItem(itemId)`, owner stamp,
        // `g_game().internalAddItem(tile, item)` and the default-duration half
        // of `startDecaying` (combat.cpp:1233-1247).
        effects.createFieldItem(world.actorOf(caster), world.tileOf(tile), itemId = Combat.ITEM_FIREFIELD_PVP_FULL)
        val field = tile.items.firstOrNull { it.id == Combat.ITEM_FIREFIELD_PVP_FULL }
        assertThat(field).isNotNull
        assertThat(field!!.isOwner(caster)).isTrue()
    }
}
