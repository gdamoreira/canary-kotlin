package org.opentibia.canary.lua.bindings.library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.lua.bindings.core.luaToPosition

/**
 * Transcript tests for the `NetworkMessage` userdata class, mirroring
 * `NetworkMessageFunctions` (src/lua/functions/core/network/network_message_functions.cpp)
 * and the buffer arithmetic of `NetworkMessage`
 * (src/server/network/message/networkmessage.cpp).
 */
class NetworkMessageFunctionsLibraryTest {

    private class RecordingNetworkMessageOperations : NetworkMessageOperations {
        val addedItems = mutableListOf<Pair<Item, Player>>()
        val sent = mutableListOf<Pair<NetworkMessage, Player>>()
        val errors = mutableListOf<String>()

        override fun addItem(message: NetworkMessage, item: Item, player: Player): Boolean {
            addedItems += item to player
            return true
        }

        override fun sendToPlayer(message: NetworkMessage, player: Player): Boolean {
            sent += message to player
            return true
        }

        override fun reportError(message: String) {
            errors += message
        }
    }

    private fun environment(
        operations: NetworkMessageOperations = RecordingNetworkMessageOperations(),
    ): LuaEnvironment = LuaEnvironment().apply { initState() }.also { NetworkMessageFunctions.install(it, operations) }

    @Test
    fun `the constructor wraps a fresh NetworkMessage userdata`() {
        val environment = environment()

        environment.loadChunk(
            """
            message = NetworkMessage()
            unread = message:getUnreadBytes()
            byte = message:getByte()
            text = message:getString()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("message").isuserdata()).isTrue()
        assertThat(environment.getGlobal("unread").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("byte").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("text").tojstring()).isEmpty()
    }

    @Test
    fun `the write methods return true and reset plus skipBytes return true`() {
        val environment = environment()

        environment.loadChunk(
            """
            message = NetworkMessage()
            bytes = message:addByte(255)
            short = message:addU16(500)
            int = message:addU32(70000)
            long = message:addU64(1)
            signed8 = message:add8(-1)
            signed16 = message:add16(-2)
            signed32 = message:add32(-3)
            signed64 = message:add64(-4)
            text = message:addString('hello')
            position = message:addPosition({x = 100, y = 200, z = 7})
            double = message:addDouble(12.5)
            reset = message:reset()
            skipped = message:skipBytes(0)
            deleted = message:delete()
            """.trimIndent(),
        )

        listOf("bytes", "short", "int", "long", "signed8", "signed16", "signed32", "signed64", "text")
            .forEach { assertThat(environment.getGlobal(it).toboolean()).describedAs(it).isTrue() }
        assertThat(environment.getGlobal("position").toboolean()).isTrue()
        assertThat(environment.getGlobal("double").toboolean()).isTrue()
        assertThat(environment.getGlobal("reset").toboolean()).isTrue()
        assertThat(environment.getGlobal("skipped").toboolean()).isTrue()
        assertThat(environment.getGlobal("deleted").isnil()).isTrue()
    }

    @Test
    fun `the read methods decode a preloaded payload`() {
        val environment = environment()
        val byteMessage = NetworkMessage().apply {
            addByte(0xAB)
            reset()
            setPayloadLength(1)
        }
        val shortMessage = NetworkMessage().apply {
            addU16(500)
            reset()
            setPayloadLength(2)
        }
        val stringMessage = NetworkMessage().apply {
            addString("hello")
            reset()
            setPayloadLength(7)
        }
        val positionMessage = NetworkMessage().apply {
            addPosition(Position(120, 340, 7))
            reset()
            setPayloadLength(5)
        }
        environment.globals.set("byteMessage", environment.userdata.wrap("NetworkMessage", byteMessage))
        environment.globals.set("shortMessage", environment.userdata.wrap("NetworkMessage", shortMessage))
        environment.globals.set("stringMessage", environment.userdata.wrap("NetworkMessage", stringMessage))
        environment.globals.set("positionMessage", environment.userdata.wrap("NetworkMessage", positionMessage))

        environment.loadChunk(
            """
            byte = byteMessage:getByte()
            short = shortMessage:getU16()
            text = stringMessage:getString()
            unread = stringMessage:getUnreadBytes()
            pos = positionMessage:getPosition()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("byte").toint()).isEqualTo(0xAB)
        assertThat(environment.getGlobal("short").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("text").tojstring()).isEqualTo("hello")
        assertThat(environment.getGlobal("unread").toint()).isEqualTo(0)
        val pos = luaToPosition(environment.getGlobal("pos"))!!
        assertThat(pos).isEqualTo(Position(120, 340, 7))
    }

    @Test
    fun `addItem hands the item and player to the operations seam`() {
        val operations = RecordingNetworkMessageOperations()
        val environment = environment(operations)
        environment.globals.set("item", environment.userdata.wrap("Item", Item(2160, 1)))
        environment.globals.set(
            "hero",
            environment.userdata.wrap(
                "Player",
                Player(1, "Hero", 100, 100, position = Position(100, 100, 7)),
            ),
        )

        environment.loadChunk(
            """
            message = NetworkMessage()
            added = message:addItem(item, hero)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(operations.addedItems).hasSize(1)
        assertThat(operations.addedItems.single().first.id).isEqualTo(2160)
    }

    @Test
    fun `addItem reports missing userdata`() {
        val operations = RecordingNetworkMessageOperations()
        val environment = environment(operations)

        environment.loadChunk(
            """
            message = NetworkMessage()
            message:addItem('not an item', 'not a player')
            """.trimIndent(),
        )

        assertThat(operations.errors).containsExactly("Item not found")
    }

    @Test
    fun `sendToPlayer dispatches through the operations seam`() {
        val operations = RecordingNetworkMessageOperations()
        val environment = environment(operations)
        environment.globals.set(
            "hero",
            environment.userdata.wrap(
                "Player",
                Player(1, "Hero", 100, 100, position = Position(100, 100, 7)),
            ),
        )

        environment.loadChunk(
            """
            message = NetworkMessage()
            sent = message:sendToPlayer(hero)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("sent").toboolean()).isTrue()
        assertThat(operations.sent).hasSize(1)
    }


}
