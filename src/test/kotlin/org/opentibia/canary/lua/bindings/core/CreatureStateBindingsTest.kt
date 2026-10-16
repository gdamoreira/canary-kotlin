package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.WorldCreatureOperations
import org.opentibia.canary.map.Position

/**
 * Transcript of the remaining `Creature` accessor bindings
 * (src/lua/functions/creatures/creature_functions.cpp:175-1178): `isRemoved`,
 * `isInGhostMode`, `getDamageMap`, `getSummons`, `setMaxHealth`,
 * `setHiddenHealth`/`isHealthHidden`, `setDropLoot`, `setSkillLoss`,
 * `setDirectionLocked`/`isDirectionLocked`, `setSkull`/`getSkull`,
 * `setLight`/`getLight`, `setIcon`/`getIcon`/`getIcons` and
 * `setFollowCreature`. Exercised through the composed
 * `WorldCreatureOperations` so the round-trip mutates the real creature state.
 */
class CreatureStateBindingsTest {

    private fun environment(monster: Monster): LuaEnvironment {
        val environment = LuaEnvironment().apply { initState() }
        CreatureFunctions.install(environment, WorldCreatureOperations())
        environment.globals.set("c", environment.userdata.wrap("Monster", monster))
        return environment
    }

    private fun monster(): Monster =
        Monster(id = 7, name = "Rat", maxHealth = 100, health = 100, position = Position(10, 10, 7))

    @Test
    fun `isRemoved and isInGhostMode read the creature state`() {
        val monster = monster()
        val environment = environment(monster)

        environment.loadChunk("removed = c:isRemoved()")
        environment.loadChunk("ghost = c:isInGhostMode()")

        assertThat(environment.getGlobal("removed").toboolean()).isFalse()
        assertThat(environment.getGlobal("ghost").toboolean()).isFalse()
    }

    @Test
    fun `setMaxHealth replaces the maximum and clamps the current health`() {
        val monster = monster()
        val environment = environment(monster)

        environment.loadChunk("ok = c:setMaxHealth(50)")

        assertThat(environment.getGlobal("ok").toboolean()).isTrue()
        assertThat(monster.maxHealth).isEqualTo(50)
        assertThat(monster.health).isEqualTo(50)
    }

    @Test
    fun `hidden health and loot flags round-trip through the creature state`() {
        val monster = monster()
        val environment = environment(monster)

        environment.loadChunk("hidden = c:setHiddenHealth(true)")
        environment.loadChunk("hiddenRead = c:isHealthHidden()")
        environment.loadChunk("loot = c:setDropLoot(false)")
        environment.loadChunk("loss = c:setSkillLoss(false)")

        assertThat(environment.getGlobal("hidden").toboolean()).isTrue()
        assertThat(environment.getGlobal("hiddenRead").toboolean()).isTrue()
        assertThat(environment.getGlobal("loot").toboolean()).isTrue()
        assertThat(environment.getGlobal("loss").toboolean()).isTrue()
    }

    @Test
    fun `direction lock, skull and light round-trip through the creature state`() {
        val monster = monster()
        val environment = environment(monster)

        environment.loadChunk("locked = c:setDirectionLocked(true)")
        environment.loadChunk("lockedRead = c:isDirectionLocked()")
        environment.loadChunk("skullSet = c:setSkull(4)")
        environment.loadChunk("skull = c:getSkull()")
        environment.loadChunk("lightSet = c:setLight(215, 8)")
        environment.loadChunk("level, color = c:getLight()")

        assertThat(environment.getGlobal("locked").toboolean()).isTrue()
        assertThat(environment.getGlobal("lockedRead").toboolean()).isTrue()
        assertThat(environment.getGlobal("skullSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("skull").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("lightSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("level").toint()).isEqualTo(8)
        assertThat(environment.getGlobal("color").toint()).isEqualTo(215)
    }

    @Test
    fun `icons round-trip through the creature icon map`() {
        val monster = monster()
        val environment = environment(monster)

        environment.loadChunk("set = c:setIcon('questKey', 0, 4, 3)")
        environment.loadChunk("icon = c:getIcon('questKey')")
        environment.loadChunk("icons = c:getIcons()")

        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        val icon = environment.getGlobal("icon")
        assertThat(icon.get("category").toint()).isEqualTo(0)
        assertThat(icon.get("count").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("icons").istable()).isTrue()
    }

    @Test
    fun `summons and damage map expose tables`() {
        val monster = monster()
        val environment = environment(monster)

        environment.loadChunk("summons = c:getSummons()")
        environment.loadChunk("count = #summons")
        environment.loadChunk("map = c:getDamageMap()")

        assertThat(environment.getGlobal("count").toint()).isZero()
        assertThat(environment.getGlobal("map").istable()).isTrue()
    }

    @Test
    fun `setFollowCreature accepts a creature and clears with nil`() {
        val monster = monster()
        val follower = Monster(id = 8, name = "Bug", maxHealth = 10, health = 10, position = Position(11, 10, 7))
        val environment = environment(monster)
        environment.globals.set("follower", environment.userdata.wrap("Monster", follower))

        environment.loadChunk("followed = c:setFollowCreature(follower)")
        assertThat(environment.getGlobal("followed").toboolean()).isTrue()
        assertThat(monster.getFollowCreature()).isSameAs(follower)

        environment.loadChunk("cleared = c:setFollowCreature(nil)")
        assertThat(environment.getGlobal("cleared").toboolean()).isTrue()
        assertThat(monster.getFollowCreature()).isNull()
    }
}
