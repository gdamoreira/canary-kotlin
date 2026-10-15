package org.opentibia.canary.lua.runtime

import io.netty.buffer.UnpooledByteBufAllocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.players.PlayerFrameSink
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.SLOTP_HEAD
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Producer-routing tests for the Lua `player:addItem`/`player:addItemEx` entry
 * points (`PlayerFunctions::luaPlayerAddItem`, player_functions.cpp:2414 and
 * `luaPlayerAddItemEx`, player_functions.cpp:2473): the world
 * [WorldLuaOperations.attachPlayerItems] seam must route both through
 * `g_game().internalPlayerAddItem`/`internalAddItem` (game.cpp:2981/2698) so the
 * insertion fires `Player::postAddNotification` instead of the raw
 * `Player::addItem` bypass.
 */
class WorldPlayerItemProducerTest {

    private val center = Position(1000, 1000, 7)

    private fun itemsRegistry(): Items = Items().also {
        it.register(ItemType(id = 100, name = "ground", isGroundTile = true))
        it.register(ItemType(id = 2160, name = "helmet", isMovable = true, weight = 100.0, slotPosition = SLOTP_HEAD))
    }

    private class Harness(
        val game: Game,
        val player: Player,
        val items: Items,
        val frames: MutableList<ByteArray>,
        val world: WorldLuaOperations,
    )

    private fun harness(): Harness {
        val items = itemsRegistry()
        val game = Game(GameMap(), GameDispatcher(), itemTypes = items)
        val player = Player(id = 1L, name = "Hero", maxHealth = 150, health = 100, position = center)
        game.addPlayer(player)
        val frames = mutableListOf<ByteArray>()
        player.protocolSink = PlayerFrameSink(
            player = player,
            map = game.map,
            frameAllocatorProvider = { UnpooledByteBufAllocator.DEFAULT },
            frameWriter = { frame ->
                val bytes = ByteArray(frame.readableBytes())
                frame.readBytes(bytes)
                frame.release()
                frames.add(bytes)
            },
        )
        val world = WorldLuaOperations(itemTypes = items)
        world.attachPlayerItems(
            internalPlayerAddItem = { target, item, dropOnMap, slot ->
                game.internalPlayerAddItem(target, item, dropOnMap, slot)
            },
            internalAddItem = { target, item, index, flags ->
                game.internalAddItem(target, item, index, flags)
            },
        )
        return Harness(game, player, items, frames, world)
    }

    private fun opcodes(frames: List<ByteArray>): List<Byte> = frames.map { it[0] }

    @Test
    fun `player addItem routes through the bound producer and fires the inventory refresh`() {
        val harness = harness()

        val added = harness.world.player.addItem(
            player = harness.player,
            itemId = 2160,
            count = 1,
            canDropOnMap = true,
            subType = 1,
            slot = Slots_t.CONST_SLOT_HEAD.value,
            tier = 0,
        )

        assertThat(added).hasSize(1)
        assertThat(harness.player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)).isNotNull()
        assertThat(opcodes(harness.frames)).containsExactly(0x78.toByte(), 0xF5.toByte(), 0xA0.toByte())
    }

    @Test
    fun `player addItemEx without drop-on-map routes through the bound internalAddItem`() {
        val harness = harness()
        val item = Item(2160, 1, harness.items.get(2160))

        val ret = harness.world.player.addItemEx(
            player = harness.player,
            item = item,
            canDropOnMap = false,
            indexOrSlot = -1,
            flags = 0,
        )

        assertThat(ret).isEqualTo(ReturnValue.NOERROR.ordinal)
        assertThat(harness.player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)).isSameAs(item)
        assertThat(opcodes(harness.frames)).containsExactly(0x78.toByte(), 0xF5.toByte(), 0xA0.toByte())
    }
}
