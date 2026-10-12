package org.opentibia.canary.lua

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.ItemPacketHandler

class LuaActionDispatchTest {
    @Test
    fun `use packet dispatches the registered Lua action on the WorldCommit lane`() {
        val env = LuaEnvironment().apply { initState() }
        env.loadChunk(
            """
            local a = Action()
            a:id(1945)
            function a.onUse(player, item, fromPosition, target, toPosition, isHotkey)
                lever_used = true
                return true
            end
            a:register()
            """.trimIndent()
        )

        val map = GameMap()
        val pos = Position(100, 100, 7)
        map.getOrCreateTile(pos).addItem(Item(id = 1945))

        val player = Player(1, "Hero", 100, 100, position = pos)

        val payload = Unpooled.buffer()
            .writeShortLE(pos.x)
            .writeShortLE(pos.y)
            .writeByte(pos.z)
            .writeShortLE(1945)
            .writeByte(0)
            .writeByte(0)

        val dispatcher = GameDispatcher().apply { start() }
        try {
            val handled = ItemPacketHandler.handleUse(player, payload, map, dispatcher, env)
            assertThat(handled).isTrue()

            val deadline = System.currentTimeMillis() + 2_000
            while (!env.getGlobal("lever_used").toboolean() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10)
            }
            assertThat(env.getGlobal("lever_used").toboolean()).isTrue()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `use packet with no registered action reports unhandled`() {
        val env = LuaEnvironment().apply { initState() }
        val map = GameMap()
        val pos = Position(100, 100, 7)
        map.getOrCreateTile(pos).addItem(Item(id = 1234))
        val player = Player(1, "Hero", 100, 100, position = pos)

        val payload = Unpooled.buffer()
            .writeShortLE(pos.x)
            .writeShortLE(pos.y)
            .writeByte(pos.z)
            .writeShortLE(1234)
            .writeByte(0)
            .writeByte(0)

        val dispatcher = GameDispatcher().apply { start() }
        try {
            assertThat(ItemPacketHandler.handleUse(player, payload, map, dispatcher, env)).isFalse()
        } finally {
            dispatcher.shutdown()
        }
    }
}
