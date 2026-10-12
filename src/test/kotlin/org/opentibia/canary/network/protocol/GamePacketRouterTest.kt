package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.wheel.WheelGemAffinity
import org.opentibia.canary.systems.wheel.WheelSlots

class GamePacketRouterTest {

    @Test
    fun `unbundles and routes multiple chained client packets within single frame`() {
        val map = GameMap()
        val startPos = Position(100, 100, 7)
        map.getOrCreateTile(startPos)
        map.getOrCreateTile(Position(100, 99, 7)) // North
        map.getOrCreateTile(Position(101, 99, 7)) // East

        val player = Player(1, "Hero", 100, 100, position = startPos)

        // Create buffer with 3 bundled packets:
        // 1. Turn North (0x6F)
        // 2. Move North (0x65)
        // 3. Move East  (0x66)
        val frame = Unpooled.buffer()
        frame.writeByte(0x6F) // Turn North
        frame.writeByte(0x65) // Move North
        frame.writeByte(0x66) // Move East

        val context = GamePacketContext(
            map = map,
            dispatcher = GameDispatcher(),
            luaEnvironment = LuaEnvironment().apply { initState() }
        )
        val handledCount = GamePacketRouter.route(player, frame, context)

        assertThat(handledCount).isEqualTo(3)
        assertThat(player.direction).isEqualTo(Direction.East)
        assertThat(player.position).isEqualTo(Position(101, 99, 7))
    }

    // Wheel of Destiny: 0x61 parseOpenWheel, 0x62 parseSaveWheel, 0xE7 parseWheelGemAction.
    @Test
    fun `recognizes open wheel opcode 0x61 and counts it handled`() {
        withStartedContext { context ->
            val frame = Unpooled.buffer()
            frame.writeByte(0x61)
            frame.writeIntLE(1)

            assertThat(GamePacketRouter.route(hero(), frame, context)).isEqualTo(1)
        }
    }

    @Test
    fun `recognizes save wheel opcode 0x62 and counts it handled`() {
        withStartedContext { context ->
            val frame = Unpooled.buffer()
            frame.writeByte(0x62)
            repeat(WheelSlots.entries.size) { frame.writeShortLE(0) }
            repeat(WheelGemAffinity.entries.size) { frame.writeByte(0) }

            assertThat(GamePacketRouter.route(hero(), frame, context)).isEqualTo(1)
        }
    }

    @Test
    fun `recognizes wheel gem action opcode 0xE7 and counts it handled`() {
        withStartedContext { context ->
            val frame = Unpooled.buffer()
            frame.writeByte(0xE7)
            frame.writeByte(0) // WheelGemAction_t::Destroy
            frame.writeShortLE(300)

            assertThat(GamePacketRouter.route(hero(), frame, context)).isEqualTo(1)
        }
    }

    // Offline training: 0x74 parseStartOfflineTraining.
    @Test
    fun `recognizes start offline training opcode 0x74 and counts it handled`() {
        withStartedContext { context ->
            val frame = Unpooled.buffer()
            frame.writeByte(0x74)
            frame.writeByte(0x02)

            assertThat(GamePacketRouter.route(hero(), frame, context)).isEqualTo(1)
        }
    }

    // Reward chest: 0xFF parseRewardChestCollect.
    @Test
    fun `recognizes reward chest collect opcode 0xFF and counts it handled`() {
        withStartedContext { context ->
            val frame = Unpooled.buffer()
            frame.writeByte(0xFF)
            writeRewardChestCollect(frame)

            assertThat(GamePacketRouter.route(hero(), frame, context)).isEqualTo(1)
        }
    }

    @Test
    fun `routes bundled wheel offline training and reward chest packets in one frame`() {
        withStartedContext { context ->
            val frame = Unpooled.buffer()

            frame.writeByte(0x61) // open wheel
            frame.writeIntLE(1)

            frame.writeByte(0x62) // save wheel
            repeat(WheelSlots.entries.size) { frame.writeShortLE(0) }
            repeat(WheelGemAffinity.entries.size) { frame.writeByte(0) }

            frame.writeByte(0xE7) // wheel gem action (destroy)
            frame.writeByte(0)
            frame.writeShortLE(300)

            frame.writeByte(0x74) // start offline training
            frame.writeByte(0x02)

            frame.writeByte(0xFF) // reward chest collect
            writeRewardChestCollect(frame)

            assertThat(GamePacketRouter.route(hero(), frame, context)).isEqualTo(5)
        }
    }

    @Test
    fun `rejects malformed save wheel payload without counting it handled`() {
        withStartedContext { context ->
            val frame = Unpooled.buffer()
            frame.writeByte(0x62)
            frame.writeShortLE(1) // truncated: only one slot point

            assertThat(GamePacketRouter.route(hero(), frame, context)).isEqualTo(0)
        }
    }

    private fun writeRewardChestCollect(frame: ByteBuf) {
        frame.writeShortLE(100) // x
        frame.writeShortLE(100) // y
        frame.writeByte(7) // z
        frame.writeShortLE(2148) // itemId
        frame.writeByte(5) // stackPosition
    }

    private fun hero(): Player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))

    private fun withStartedContext(block: (GamePacketContext) -> Unit) {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            block(
                GamePacketContext(
                    map = GameMap(),
                    dispatcher = dispatcher,
                    luaEnvironment = LuaEnvironment().apply { initState() }
                )
            )
        } finally {
            dispatcher.shutdown()
        }
    }
}
