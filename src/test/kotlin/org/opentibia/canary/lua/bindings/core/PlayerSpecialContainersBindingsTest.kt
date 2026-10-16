package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Transcript of the depot step-script path
 * (`data/scripts/movements/special_tiles.lua`):
 * `player:getDepotChest(id, true)` (player.cpp:6807-6813) and
 * `player:setSpecialContainersAvailable(stash, market, depotSearch)`
 * (player.cpp:6815-6833) over the domain menu gates. Before this wiring the
 * bindings resolved through the not-wired stub and the depot step event failed,
 * so the client never learned the depot/stash menus were available.
 */
class PlayerSpecialContainersBindingsTest {

    private fun environment(player: Player): LuaEnvironment {
        val environment = LuaEnvironment().apply { initState() }
        PlayerFunctions.install(environment)
        environment.globals.set("p", environment.userdata.wrap("Player", player))
        return environment
    }

    private fun player(): Player =
        Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    @Test
    fun `setSpecialContainersAvailable stores the gates and notifies the session`() {
        val player = player()
        val environment = environment(player)
        var notifications = 0
        player.sendSpecialContainersAvailable = { notifications++ }

        environment.loadChunk("ok = p:setSpecialContainersAvailable(true, true, false)")

        assertThat(environment.getGlobal("ok").toboolean()).isTrue()
        assertThat(player.isStashMenuAvailable()).isTrue()
        assertThat(player.marketMenu).isTrue()
        assertThat(notifications).isEqualTo(1)
    }

    @Test
    fun `getDepotChest auto-creates and returns the depot chest container`() {
        val player = player()
        val environment = environment(player)

        environment.loadChunk("chest = p:getDepotChest(1, true)")

        val chest = environment.getGlobal("chest")
        assertThat(chest.isnil()).isFalse()
        assertThat(chest.isuserdata()).isTrue()
        assertThat(player.getDepotChest(1, false)).isNotNull
    }

    @Test
    fun `getStashCount reports the domain stash contents`() {
        val player = player()
        val environment = environment(player)

        environment.loadChunk("count = p:getStashCount()")

        assertThat(environment.getGlobal("count").toint()).isEqualTo(0)
    }
}
