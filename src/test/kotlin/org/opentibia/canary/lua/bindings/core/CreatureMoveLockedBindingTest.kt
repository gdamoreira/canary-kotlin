package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.WorldCreatureOperations
import org.opentibia.canary.map.Position

/**
 * Transcript of `creature:isMoveLocked()` / `creature:setMoveLocked(moveLocked)`
 * (src/lua/functions/creatures/creature_functions.cpp:648-669) over
 * `Creature::isMoveLocked`/`Creature::setMoveLocked`
 * (src/creatures/creature.hpp:189-194). Exercised through the composed
 * `WorldCreatureOperations`, so the round-trip mutates the real creature state
 * that `Tile::queryAdd` (src/items/tile.cpp:712) and
 * `Monster::getMonsterTraits` (src/creatures/monsters/monster.cpp:3781) read.
 */
class CreatureMoveLockedBindingTest {

    @Test
    fun `isMoveLocked and setMoveLocked round-trip through the creature state`() {
        val environment = LuaEnvironment().apply { initState() }
        CreatureFunctions.install(environment, WorldCreatureOperations())
        val monster = Monster(id = 7, name = "Rat", maxHealth = 100, health = 100, position = Position(10, 10, 7))
        environment.globals.set("c", environment.userdata.wrap("Monster", monster))

        environment.loadChunk("before = c:isMoveLocked()")
        assertThat(environment.getGlobal("before").toboolean()).isFalse()

        environment.loadChunk("ok = c:setMoveLocked(true)")
        assertThat(environment.getGlobal("ok").toboolean()).isTrue()
        assertThat(monster.isMoveLocked()).isTrue()

        environment.loadChunk("after = c:isMoveLocked()")
        assertThat(environment.getGlobal("after").toboolean()).isTrue()

        environment.loadChunk("c:setMoveLocked(false)")
        assertThat(monster.isMoveLocked()).isFalse()
    }
}
