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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router wiring tests for the wave-21 second inbound opcode batch: party
 * (`0xA3`-`0xA8`), imbuements (`0xB2`/`0xD5`-`0xD7`), stash/depot search
 * (`0x28`/`0x29`/`0x92`-`0x94`), quick loot (`0x8F`-`0x91`), modal answer
 * (`0xF9`) and quest log (`0xF0`/`0xF1`). Each decoded command crosses the
 * WorldCommit confinement boundary (AGENTS.md 1.2).
 */
class InboundOpcodeBatch2RouterWiringTest {

    @Test
    fun `routes every party opcode and counts them handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(PartyPacketHandler.OPCODE_INVITE_TO_PARTY)
        frame.writeIntLE(1)
        frame.writeByte(PartyPacketHandler.OPCODE_JOIN_PARTY)
        frame.writeIntLE(2)
        frame.writeByte(PartyPacketHandler.OPCODE_REVOKE_PARTY_INVITE)
        frame.writeIntLE(3)
        frame.writeByte(PartyPacketHandler.OPCODE_PASS_PARTY_LEADERSHIP)
        frame.writeIntLE(4)
        frame.writeByte(PartyPacketHandler.OPCODE_LEAVE_PARTY)
        frame.writeByte(PartyPacketHandler.OPCODE_ENABLE_SHARED_PARTY_EXPERIENCE)
        frame.writeByte(1)

        assertThat(route(frame)).isEqualTo(6)
    }

    @Test
    fun `rejects a malformed party target payload without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(PartyPacketHandler.OPCODE_INVITE_TO_PARTY)
        frame.writeShortLE(1)

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `routes every imbuement opcode and counts them handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(ImbuementPacketHandler.OPCODE_IMBUEMENT_ACTION)
        frame.writeByte(ImbuementPacketHandler.IMBUEMENT_ACTION_PICK_ITEM)
        writePosition(frame, Position(100, 100, 7))
        frame.writeShortLE(2160)
        frame.writeByte(3)
        frame.writeByte(ImbuementPacketHandler.OPCODE_APPLY_IMBUEMENT)
        frame.writeByte(1)
        frame.writeIntLE(31)
        frame.writeByte(ImbuementPacketHandler.OPCODE_CLEAR_IMBUEMENT)
        frame.writeByte(1)
        frame.writeByte(ImbuementPacketHandler.OPCODE_CLOSE_IMBUEMENT_WINDOW)

        assertThat(route(frame)).isEqualTo(4)
    }

    @Test
    fun `rejects a malformed pick item imbuement payload without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(ImbuementPacketHandler.OPCODE_IMBUEMENT_ACTION)
        frame.writeByte(ImbuementPacketHandler.IMBUEMENT_ACTION_PICK_ITEM)
        writePosition(frame, Position(100, 100, 7))

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `routes stash and depot search opcodes and counts them handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(DepotAndStashPacketHandler.OPCODE_STASH_WITHDRAW)
        frame.writeByte(StashAction.WITHDRAW.value)
        frame.writeShortLE(2160)
        frame.writeIntLE(5)
        frame.writeByte(1)
        frame.writeByte(DepotAndStashPacketHandler.OPCODE_RETRIEVE_DEPOT_SEARCH)
        frame.writeShortLE(2160)
        frame.writeByte(1)
        frame.writeByte(DepotAndStashPacketHandler.OPCODE_OPEN_DEPOT_SEARCH)
        frame.writeByte(DepotAndStashPacketHandler.OPCODE_CLOSE_DEPOT_SEARCH)
        frame.writeByte(DepotAndStashPacketHandler.OPCODE_DEPOT_SEARCH_ITEM_REQUEST)
        frame.writeShortLE(2160)

        assertThat(route(frame)).isEqualTo(5)
    }

    @Test
    fun `rejects a malformed depot search item request without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(DepotAndStashPacketHandler.OPCODE_DEPOT_SEARCH_ITEM_REQUEST)
        frame.writeByte(1)

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `routes quick loot opcodes and counts them handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(QuickLootPacketHandler.OPCODE_QUICK_LOOT)
        frame.writeByte(0)
        writePosition(frame, Position(100, 100, 7))
        frame.writeShortLE(2160)
        frame.writeByte(3)
        frame.writeByte(QuickLootPacketHandler.OPCODE_LOOT_CONTAINER)
        frame.writeByte(3)
        frame.writeByte(1)
        frame.writeByte(QuickLootPacketHandler.OPCODE_QUICK_LOOT_BLACK_WHITELIST)
        frame.writeByte(1)
        frame.writeShortLE(1)
        frame.writeShortLE(2160)

        assertThat(route(frame)).isEqualTo(3)
    }

    @Test
    fun `rejects a malformed quick loot target payload without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(QuickLootPacketHandler.OPCODE_QUICK_LOOT)
        frame.writeByte(0)
        writePosition(frame, Position(100, 100, 7))

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `routes quest log opcodes and counts them handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(QuestLogPacketHandler.OPCODE_SHOW_QUEST_LOG)
        frame.writeByte(QuestLogPacketHandler.OPCODE_QUEST_LINE)
        frame.writeShortLE(123)

        assertThat(route(frame)).isEqualTo(2)
    }

    @Test
    fun `rejects a malformed quest line payload without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(QuestLogPacketHandler.OPCODE_QUEST_LINE)
        frame.writeByte(1)

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `routes a modal answer opcode and counts it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(ModalAnswerPacketHandler.OPCODE_MODAL_WINDOW_ANSWER)
        frame.writeIntLE(1)
        frame.writeByte(1)
        frame.writeByte(0)

        assertThat(route(frame)).isEqualTo(1)
    }

    @Test
    fun `rejects a malformed modal answer payload without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(ModalAnswerPacketHandler.OPCODE_MODAL_WINDOW_ANSWER)
        frame.writeIntLE(1)

        assertThat(route(frame)).isEqualTo(0)
    }

    private fun writePosition(frame: ByteBuf, position: Position) {
        frame.writeShortLE(position.x)
        frame.writeShortLE(position.y)
        frame.writeByte(position.z)
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
