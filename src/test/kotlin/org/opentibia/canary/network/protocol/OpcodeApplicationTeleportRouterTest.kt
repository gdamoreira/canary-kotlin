package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Transcript of the GM map-click teleport opcode `0x73`
 * (`ProtocolGame::parseTeleport`, protocolgame.cpp:2420-2425): the payload
 * position reaches `Game::playerTeleport` (game.cpp:1622), which gates on
 * `PlayerFlags_t::CanMapClickTeleport` and calls `internalTeleport`.
 */
class OpcodeApplicationTeleportRouterTest {

    private val map = GameMap()
    private val dispatcher = GameDispatcher().apply { start() }

    private val start = Position(100, 100, 7)
    private val destination = Position(120, 120, 7)

    @Test
    fun `teleport opcode moves an access player to the requested position`() {
        map.getOrCreateTile(start).ground = Item(id = 106)
        map.getOrCreateTile(destination).ground = Item(id = 106)

        val game = Game(map, dispatcher)
        val player = Player(id = 1, name = "GM", maxHealth = 150, health = 150, position = start)
        player.setFlag(PlayerFlags_t.CanMapClickTeleport)
        game.addPlayer(player)
        game.internalPlaceCreature(player, start)

        val payload = Unpooled.buffer()
        payload.writeByte(0x73)
        payload.writeShortLE(destination.x)
        payload.writeShortLE(destination.y)
        payload.writeByte(destination.z)

        val handled = GamePacketRouter.route(
            player,
            payload,
            GamePacketContext(
                map = map,
                dispatcher = dispatcher,
                luaEnvironment = LuaEnvironment().apply { initState() },
                game = game,
            ),
        )

        assertThat(handled).isEqualTo(1)
        assertThat(player.position).isEqualTo(destination)

        dispatcher.shutdown()
    }

    @Test
    fun `teleport opcode is refused without the map-click flag`() {
        map.getOrCreateTile(start).ground = Item(id = 106)
        map.getOrCreateTile(destination).ground = Item(id = 106)

        val game = Game(map, dispatcher)
        val player = Player(id = 2, name = "Hero", maxHealth = 150, health = 150, position = start)
        game.addPlayer(player)
        game.internalPlaceCreature(player, start)

        val payload = Unpooled.buffer()
        payload.writeByte(0x73)
        payload.writeShortLE(destination.x)
        payload.writeShortLE(destination.y)
        payload.writeByte(destination.z)

        GamePacketRouter.route(
            player,
            payload,
            GamePacketContext(
                map = map,
                dispatcher = dispatcher,
                luaEnvironment = LuaEnvironment().apply { initState() },
                game = game,
            ),
        )

        assertThat(player.position).isEqualTo(start)

        dispatcher.shutdown()
    }
}
