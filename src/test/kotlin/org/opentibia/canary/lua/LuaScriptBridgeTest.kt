package org.opentibia.canary.lua

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class LuaScriptBridgeTest {

    @Test
    fun `executes lua script modifying kotlin player instance`() {
        val bridge = LuaScriptBridge()
        val player = Player(1, "LuaHero", 100, 50, position = Position(100, 100, 7))

        val script = """
            function onUse(player)
                player:addHealth(30)
                return true
            end
        """.trimIndent()

        bridge.loadScript(script)
        val result = bridge.invokeOnUse(player)

        assertThat(result).isTrue()
        assertThat(player.health).isEqualTo(80)
    }
}
