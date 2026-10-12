package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

class ItemPacketHandlerTest {

    @Test
    fun `parses use item packet 0x82 and invokes registered action`() {
        val env = LuaEnvironment().apply { initState() }
        env.loadChunk(
            """
            local a = Action()
            a:id(1945)
            function a.onUse(player, item, fromPosition, target, toPosition, isHotkey)
                lever_pulled = true
                return true
            end
            a:register()
            """.trimIndent()
        )

        val map = GameMap()
        val pos = Position(100, 100, 7)
        val tile = map.getOrCreateTile(pos)
        val lever = Item(id = 1945) // Lever
        tile.addItem(lever)

        val player = Player(1, "Hero", 100, 100, position = pos)

        val payload = Unpooled.buffer()
        payload.writeShortLE(pos.x)
        payload.writeShortLE(pos.y)
        payload.writeByte(pos.z)
        payload.writeShortLE(1945) // Item ID
        payload.writeByte(0) // Stackpos
        payload.writeByte(0) // Index

        val dispatcher = GameDispatcher().apply { start() }
        try {
            val handled = ItemPacketHandler.handleUse(player, payload, map, dispatcher, env)
            assertThat(handled).isTrue()

            val deadline = System.currentTimeMillis() + 2_000
            while (!env.getGlobal("lever_pulled").toboolean() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10)
            }
            assertThat(env.getGlobal("lever_pulled").toboolean()).isTrue()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `parses throw item packet 0x78 and moves ground item to destination tile`() {
        val map = GameMap()
        val fromPos = Position(100, 100, 7)
        val toPos = Position(101, 100, 7)

        val fromTile = map.getOrCreateTile(fromPos)
        val toTile = map.getOrCreateTile(toPos)

        val goldCoin = Item(id = 2148, count = 50)
        fromTile.addItem(goldCoin)

        val player = Player(1, "Hero", 100, 100, position = fromPos)

        val payload = Unpooled.buffer()
        // From Position
        payload.writeShortLE(fromPos.x)
        payload.writeShortLE(fromPos.y)
        payload.writeByte(fromPos.z)
        payload.writeShortLE(2148) // Item ID
        payload.writeByte(0) // Stackpos
        // To Position
        payload.writeShortLE(toPos.x)
        payload.writeShortLE(toPos.y)
        payload.writeByte(toPos.z)
        payload.writeByte(50) // Count

        val handled = ItemPacketHandler.handleThrow(player, payload, map)
        assertThat(handled).isTrue()
        assertThat(fromTile.items).isEmpty()
        assertThat(toTile.items).hasSize(1)
        assertThat(toTile.items.first().id).isEqualTo(2148)
    }
}
