package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.npcshop.NpcShopPacketHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router wiring tests for wave-35 batch B1: ping back (`0x1D`), cyclopedia
 * monster tracker (`0x2A`), party analyzer (`0x2B`), team finder
 * (`0x2C`/`0x2D`), client options/check (`0x2E`/`0x63`), extended opcode
 * (`0x32`), player typing (`0x38`), inventory imbuements (`0x60`), stop auto
 * walk (`0x69`), character trade configuration (`0x76`), hotkey equip (`0x77`),
 * look in shop (`0x79`) and friend system (`0x81`). Each decoded command crosses
 * the WorldCommit confinement boundary (AGENTS.md 1.2).
 */
class InboundOpcodeBatchB1RouterWiringTest {

    @Test
    fun `routes the ping back opcode and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(0x1D)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `routes the cyclopedia monster tracker opcode and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(CyclopediaTrackerPacketHandler.OPCODE_CYCLOPEDIA_MONSTER_TRACKER)
        frame.writeShortLE(1234)
        frame.writeByte(1)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `rejects a malformed cyclopedia monster tracker payload`() {
        val frame = Unpooled.buffer()
        frame.writeByte(CyclopediaTrackerPacketHandler.OPCODE_CYCLOPEDIA_MONSTER_TRACKER)
        frame.writeByte(1)

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `routes the party analyzer opcode and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(PartyAnalyzerPacketHandler.OPCODE_PARTY_ANALYZER_ACTION)
        frame.writeByte(PartyAnalyzerPacketHandler.ACTION_RESET)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `routes the leader finder window actions and counts them handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(TeamFinderPacketHandler.OPCODE_LEADER_FINDER_WINDOW)
        frame.writeByte(0)
        frame.writeByte(TeamFinderPacketHandler.OPCODE_LEADER_FINDER_WINDOW)
        frame.writeByte(2)
        frame.writeIntLE(7)
        frame.writeByte(3)

        assertThat(route(frame)).isEqualTo(2)
    }

    @Test
    fun `rejects a malformed leader finder member status payload`() {
        val frame = Unpooled.buffer()
        frame.writeByte(TeamFinderPacketHandler.OPCODE_LEADER_FINDER_WINDOW)
        frame.writeByte(2)
        frame.writeShortLE(7)

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `routes the member finder window actions and counts them handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(TeamFinderPacketHandler.OPCODE_MEMBER_FINDER_WINDOW)
        frame.writeByte(0)
        frame.writeByte(TeamFinderPacketHandler.OPCODE_MEMBER_FINDER_WINDOW)
        frame.writeByte(1)
        frame.writeIntLE(42)

        assertThat(route(frame)).isEqualTo(2)
    }

    @Test
    fun `routes the client options opcode and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(ClientOptionsPacketHandler.OPCODE_SET_CLIENT_OPTIONS)
        frame.writeByte(1)
        frame.writeByte(0)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `rejects a malformed client options payload`() {
        val frame = Unpooled.buffer()
        frame.writeByte(ClientOptionsPacketHandler.OPCODE_SET_CLIENT_OPTIONS)
        frame.writeByte(1)

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `routes the client check opcode and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(ClientOptionsPacketHandler.OPCODE_CLIENT_CHECK)
        frame.writeIntLE(2)
        frame.writeByte(1)
        frame.writeByte(1)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `rejects a malformed client check payload`() {
        val frame = Unpooled.buffer()
        frame.writeByte(ClientOptionsPacketHandler.OPCODE_CLIENT_CHECK)
        frame.writeIntLE(3)
        frame.writeByte(1)

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `routes the extended opcode and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(ExtendedOpcodePacketHandler.OPCODE_EXTENDED_OPCODE)
        frame.writeByte(1)
        frame.writeShortLE(1)
        frame.writeByte('x'.code)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `rejects a malformed extended opcode payload`() {
        val frame = Unpooled.buffer()
        frame.writeByte(ExtendedOpcodePacketHandler.OPCODE_EXTENDED_OPCODE)
        frame.writeByte(1)
        frame.writeShortLE(9)
        frame.writeByte(1)

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `routes the player typing opcode and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(PlayerTypingPacketHandler.OPCODE_PLAYER_TYPING)
        frame.writeByte(1)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `rejects a malformed player typing payload`() {
        val frame = Unpooled.buffer()
        frame.writeByte(PlayerTypingPacketHandler.OPCODE_PLAYER_TYPING)

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `routes the inventory imbuements opcode and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(InventoryImbuementsPacketHandler.OPCODE_INVENTORY_IMBUEMENTS)
        frame.writeByte(1)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `rejects a malformed inventory imbuements payload`() {
        val frame = Unpooled.buffer()
        frame.writeByte(InventoryImbuementsPacketHandler.OPCODE_INVENTORY_IMBUEMENTS)

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `routes the stop auto walk opcode and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(0x69)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `routes the character trade configuration opcode and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(CharacterTradeConfigurationPacketHandler.OPCODE_CHARACTER_TRADE_CONFIGURATION)
        frame.writeByte(1)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `rejects a malformed character trade configuration payload`() {
        val frame = Unpooled.buffer()
        frame.writeByte(CharacterTradeConfigurationPacketHandler.OPCODE_CHARACTER_TRADE_CONFIGURATION)
        frame.writeByte(2)
        frame.writeIntLE(1)
        frame.writeIntLE(2)
        frame.writeByte(1)
        frame.writeByte(7)

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `routes the hotkey equip opcode and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(HotkeyEquipPacketHandler.OPCODE_HOTKEY_EQUIP)
        frame.writeShortLE(2160)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `rejects a malformed hotkey equip payload`() {
        val frame = Unpooled.buffer()
        frame.writeByte(HotkeyEquipPacketHandler.OPCODE_HOTKEY_EQUIP)
        frame.writeByte(1)

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `routes the look in shop opcode and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(NpcShopPacketHandler.OPCODE_LOOK_IN_SHOP)
        frame.writeShortLE(2160)
        frame.writeByte(1)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `rejects a malformed look in shop payload`() {
        val frame = Unpooled.buffer()
        frame.writeByte(NpcShopPacketHandler.OPCODE_LOOK_IN_SHOP)
        frame.writeShortLE(2160)

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `routes the friend system title state and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(FriendSystemPacketHandler.OPCODE_FRIEND_SYSTEM_ACTION)
        frame.writeByte(0x0E)
        frame.writeByte(5)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `rejects a malformed friend system payload`() {
        val frame = Unpooled.buffer()
        frame.writeByte(FriendSystemPacketHandler.OPCODE_FRIEND_SYSTEM_ACTION)
        frame.writeByte(0x03)
        frame.writeShortLE(1)

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `routes a bundled batch B1 frame and counts each packet`() {
        val frame = Unpooled.buffer()
        frame.writeByte(0x1D)
        frame.writeByte(PartyAnalyzerPacketHandler.OPCODE_PARTY_ANALYZER_ACTION)
        frame.writeByte(PartyAnalyzerPacketHandler.ACTION_PRICE_TYPE)
        frame.writeByte(PlayerTypingPacketHandler.OPCODE_PLAYER_TYPING)
        frame.writeByte(0)
        frame.writeByte(InventoryImbuementsPacketHandler.OPCODE_INVENTORY_IMBUEMENTS)
        frame.writeByte(0)
        frame.writeByte(HotkeyEquipPacketHandler.OPCODE_HOTKEY_EQUIP)
        frame.writeShortLE(2160)
        frame.writeByte(NpcShopPacketHandler.OPCODE_LOOK_IN_SHOP)
        frame.writeShortLE(2160)
        frame.writeByte(2)
        frame.writeByte(0x69)

        assertThat(route(frame)).isEqualTo(7)
    }

    private fun route(frame: ByteBuf): Int {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val handled = GamePacketRouter.route(
                hero(),
                frame,
                GamePacketContext(
                    map = GameMap(),
                    dispatcher = dispatcher,
                    luaEnvironment = LuaEnvironment().apply { initState() },
                ),
            )
            awaitWorldCommit(dispatcher)
            return handled
        } finally {
            dispatcher.shutdown()
        }
    }

    private fun hero(): Player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))

    private fun awaitWorldCommit(dispatcher: GameDispatcher) {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }
}
