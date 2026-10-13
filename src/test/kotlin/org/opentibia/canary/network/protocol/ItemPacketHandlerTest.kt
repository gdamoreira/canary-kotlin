package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

            val ran = CountDownLatch(1)
            dispatcher.dispatch { ran.countDown() }
            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()

            assertThat(env.getGlobal("lever_pulled").toboolean()).isTrue()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `use item routes onUse through LuaEventDispatcher with wrapped target and toPosition`() {
        val env = LuaEnvironment().apply { initState() }
        env.loadChunk(
            """
            local a = Action()
            a:id(1945)
            function a.onUse(player, item, fromPosition, target, toPosition, isHotkey)
                seen_target_uid = target.uid
                seen_to = toPosition:toString()
                seen_hotkey = isHotkey
            end
            a:register()
            """.trimIndent()
        )

        val map = GameMap()
        val pos = Position(100, 100, 7)
        val tile = map.getOrCreateTile(pos)
        tile.addItem(Item(id = 1945))

        val player = Player(1, "Hero", 100, 100, position = pos)

        val payload = Unpooled.buffer()
        payload.writeShortLE(pos.x)
        payload.writeShortLE(pos.y)
        payload.writeByte(pos.z)
        payload.writeShortLE(1945)
        payload.writeByte(0)
        payload.writeByte(0)

        val dispatcher = GameDispatcher().apply { start() }
        try {
            val handled = ItemPacketHandler.handleUse(player, payload, map, dispatcher, env)
            assertThat(handled).isTrue()

            val ran = CountDownLatch(1)
            dispatcher.dispatch { ran.countDown() }
            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()

            assertThat(env.getGlobal("seen_target_uid").toint()).isEqualTo(0)
            assertThat(env.getGlobal("seen_to").tojstring()).isEqualTo("( 100, 100, 7 )")
            assertThat(env.getGlobal("seen_hotkey").toboolean()).isFalse()
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

    // Use item ex: 0x83 parseUseItemEx (protocolgame.cpp:2566).

    @Test
    fun `parses use item ex 0x83 from and to positions item ids and stack positions`() {
        val payload = Unpooled.buffer()
        writeUseItemEx(payload)

        val command = ItemPacketHandler.parseUseItemEx(payload)

        assertThat(command).isEqualTo(
            UseItemExCommand(
                fromPosition = Position(100, 100, 7),
                fromItemId = 1945,
                fromStackPosition = 2,
                toPosition = Position(101, 100, 7),
                toItemId = 2160,
                toStackPosition = 3,
            )
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated use item ex 0x83 payload`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(100)
        payload.writeShortLE(100)
        payload.writeByte(7)
        payload.writeShortLE(1945)
        payload.writeByte(2)
        payload.writeShortLE(101) // destination truncated

        assertThat(ItemPacketHandler.parseUseItemEx(payload)).isNull()
    }

    @Test
    fun `decodes use item ex 0x83 via EmbeddedChannel inbound message`() {
        val channel = EmbeddedChannel()
        try {
            channel.writeInbound(writeUseItemEx(Unpooled.buffer()))

            val payload = channel.readInbound<ByteBuf>()
            val command = ItemPacketHandler.parseUseItemEx(payload)

            assertThat(command).isEqualTo(
                UseItemExCommand(
                    fromPosition = Position(100, 100, 7),
                    fromItemId = 1945,
                    fromStackPosition = 2,
                    toPosition = Position(101, 100, 7),
                    toItemId = 2160,
                    toStackPosition = 3,
                )
            )
        } finally {
            channel.finishAndReleaseAll()
        }
    }

    private fun writeUseItemEx(payload: ByteBuf): ByteBuf {
        payload.writeShortLE(100) // from x
        payload.writeShortLE(100) // from y
        payload.writeByte(7) // from z
        payload.writeShortLE(1945) // from item id
        payload.writeByte(2) // from stack position
        payload.writeShortLE(101) // to x
        payload.writeShortLE(100) // to y
        payload.writeByte(7) // to z
        payload.writeShortLE(2160) // to item id
        payload.writeByte(3) // to stack position
        return payload
    }
}
