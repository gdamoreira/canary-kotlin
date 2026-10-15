package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.lua.bindings.MonsterRegisteredSpell
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the `InstantSpell::canThrowSpell` gate
 * (src/creatures/combat/spells.cpp:1299-1310) resolved through the wave-64
 * `SpellEngine.canThrowSpell` over the `Game::canThrowObjectTo` map fold
 * (src/game/game.cpp:7591) reached from
 * `InstantSpell::castSpell(creature)`'s `casterTargetOrDirection` branch
 * (spells.cpp:1315-1328).
 */
class MonsterSpellCanThrowTest {

    private class Fixture(
        val world: WorldLuaOperations,
        val map: GameMap,
    ) {
        val registry = Monsters()
        val env: LuaEnvironment = LuaEnvironment().apply { initState() }.also { world.install(it) }

        init {
            world.attachMonsters(registry)
        }
    }

    private fun fixture(range: String, withMap: Boolean = true): Fixture {
        val map = GameMap()
        val world = if (withMap) WorldLuaOperations(gameMap = map) else WorldLuaOperations()
        val fixture = Fixture(world, map)
        fixture.env.loadChunk(spellChunk(range))
        return fixture
    }

    /** Registers an `Ice Strike` instant spell and a `Rat` type attacking with it. */
    private fun spellChunk(range: String): String =
        """
        local registered = Spell("instant")
        registered:name("Ice Strike")
        registered:id(3)
        registered:words("exori frigo")
        registered:needCasterTargetOrDirection(true)
        registered:$range
        function registered.onCastSpell(creature, variant)
            last = { type = variant.type, number = variant.number }
            return true
        end
        registered:register()

        local mType = Game.createMonsterType("Rat")
        local strike = MonsterSpell()
        strike:setType("Ice Strike")
        mType:addAttack(strike)
        """.trimIndent()

    private fun cast(fixture: Fixture, attackedPosition: Position) {
        val attacker = Monster(id = 1, name = "Rat", maxHealth = 100, health = 100, position = Position(10, 10, 7))
        val attacked = Monster(id = 2, name = "Hero", maxHealth = 100, health = 100, position = attackedPosition)
        attacker.setAttackedCreature(attacked)

        val actor = fixture.registry.getMonsterType("rat")!!.info.attackSpells.single().spell as MonsterRegisteredSpell
        actor.castSpell(attacker, attacked)
    }

    @Test
    fun `caster target or direction casts when the target is inside the spell range`() {
        val fixture = fixture("range(1)")

        cast(fixture, Position(11, 10, 7))

        // InstantSpell::castSpell(creature) VARIANT_NUMBER push (spells.cpp:1319-1328).
        val last = fixture.env.getGlobal("last")
        assertThat(last.get("type").toint()).isEqualTo(1)
        assertThat(last.get("number").toint()).isEqualTo(2)
    }

    @Test
    fun `caster target or direction rejects a target outside the spell range`() {
        val fixture = fixture("range(1)")

        cast(fixture, Position(12, 10, 7))

        // `canThrowSpell` false returns before the variant build (spells.cpp:1319-1321).
        assertThat(fixture.env.getGlobal("last").isnil()).isTrue()
    }

    @Test
    fun `caster target or direction rejects a blocked sight line`() {
        val fixture = fixture("range(-1)")
        // `CONST_PROP_BLOCKPROJECTILE` between caster and target
        // (spells.cpp:1306 -> Game::canThrowObjectTo -> Map::checkSightLine).
        fixture.map.getOrCreateTile(Position(11, 10, 7))
            .setFlag(TileFlags.TILESTATE_BLOCKPROJECTILE.value)

        cast(fixture, Position(12, 10, 7))

        assertThat(fixture.env.getGlobal("last").isnil()).isTrue()
    }

    @Test
    fun `caster target or direction rejects a different floor`() {
        val fixture = fixture("range(-1)")

        cast(fixture, Position(11, 10, 8))

        // The `fromPos.z != toPos.z` guard short-circuits before the map fold
        // (spells.cpp:1303).
        assertThat(fixture.env.getGlobal("last").isnil()).isTrue()
    }

    @Test
    fun `the default range bounds by the client viewport`() {
        val fixture = fixture("range(-1)")

        cast(fixture, Position(18, 10, 7))

        // `MAP_MAX_CLIENT_VIEW_PORT_X` (8) admits a distance-8 strike...
        assertThat(fixture.env.getGlobal("last").get("number").toint()).isEqualTo(2)

        fixture.env.globals.set("last", LuaValue.NIL)
        cast(fixture, Position(19, 10, 7))

        // ...and the distance-9 strike stays out of the viewport.
        assertThat(fixture.env.getGlobal("last").isnil()).isTrue()
    }

    @Test
    fun `attachCombat re-binds the gate over the wired map`() {
        // The world is installed without a map first, exactly like
        // `LuaEnvironment.initState`; `attachCombat` then wires the boot map.
        val fixture = fixture("range(1)", withMap = false)
        fixture.world.attachCombat(fixture.map)

        cast(fixture, Position(12, 10, 7))

        assertThat(fixture.env.getGlobal("last").isnil()).isTrue()
    }
}
