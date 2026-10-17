package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.SLOTP_HEAD
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript of the hotkey-equip opcode `0x77`
 * (`ProtocolGame::parseHotkeyEquip`, protocolgame.cpp:2129-2141) reaching
 * `Game::playerEquipItem` (game.cpp:4159): the item id resolves its slot
 * through `getSlotType` and the backpack search moves the item onto the
 * player cylinder.
 */
class OpcodeApplicationHotkeyEquipRouterTest {

    private val map = GameMap()
    private val dispatcher = GameDispatcher().apply { start() }
    private val center = Position(100, 100, 7)

    private fun itemsRegistry(): Items = Items().also {
        it.register(ItemType(id = 100, name = "ground", isGroundTile = true))
        it.register(ItemType(id = 2160, name = "helmet", isMovable = true, weight = 100.0, slotPosition = SLOTP_HEAD))
        it.register(ItemType(id = 1987, name = "backpack", isContainer = true, capacity = 8))
    }


    private fun awaitWorldCommit() {
        val ran = java.util.concurrent.CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue()
    }

    private fun route(payload: io.netty.buffer.ByteBuf, player: Player, game: Game, items: Items): Int {
        val handled = GamePacketRouter.route(player, payload, context(game, items))
        awaitWorldCommit()
        return handled
    }

    private fun context(game: Game, items: Items): GamePacketContext = GamePacketContext(
        map = map,
        dispatcher = dispatcher,
        luaEnvironment = LuaEnvironment().apply { initState() },
        itemTypes = items,
        game = game,
    )

    @Test
    fun `hotkey equip moves a backpack item into its slot`() {
        val items = itemsRegistry()
        val game = Game(map, dispatcher, itemTypes = items)
        val player = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = center)
        game.addPlayer(player)
        game.internalPlaceCreature(player, center)

        val backpack = Container(1987, 8, items.get(1987))
        assertThat(player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)).isEqualTo(ReturnValue.NOERROR)
        val helmet = Item(id = 2160, type = items.get(2160))
        backpack.addThing(helmet)

        val payload = Unpooled.buffer()
        payload.writeByte(HotkeyEquipPacketHandler.OPCODE_HOTKEY_EQUIP)
        payload.writeShortLE(2160)

        val handled = route(payload, player, game, items)

        assertThat(handled).isEqualTo(1)
        assertThat(player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)).isSameAs(helmet)

        dispatcher.shutdown()
    }

    @Test
    fun `hotkey equip without the item in the backpack stays inert`() {
        val items = itemsRegistry()
        val game = Game(map, dispatcher, itemTypes = items)
        val player = Player(id = 2, name = "Hero", maxHealth = 150, health = 150, position = center)
        game.addPlayer(player)
        game.internalPlaceCreature(player, center)

        val payload = Unpooled.buffer()
        payload.writeByte(HotkeyEquipPacketHandler.OPCODE_HOTKEY_EQUIP)
        payload.writeShortLE(2160)

        assertThat(route(payload, player, game, items)).isEqualTo(1)
        assertThat(player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)).isNull()

        dispatcher.shutdown()
    }

    @Test
    fun `rejects a malformed hotkey equip payload`() {
        val items = itemsRegistry()
        val game = Game(map, dispatcher, itemTypes = items)
        val player = Player(id = 3, name = "Hero", maxHealth = 150, health = 150, position = center)
        game.addPlayer(player)

        val payload = Unpooled.buffer()
        payload.writeByte(HotkeyEquipPacketHandler.OPCODE_HOTKEY_EQUIP)
        payload.writeByte(1)

        assertThat(route(payload, player, game, items)).isEqualTo(0)

        dispatcher.shutdown()
    }
}
