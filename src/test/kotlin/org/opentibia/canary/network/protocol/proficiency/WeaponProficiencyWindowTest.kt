package org.opentibia.canary.network.protocol.proficiency

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.GamePacketContext
import org.opentibia.canary.network.protocol.GamePacketRouter
import org.opentibia.canary.network.protocol.InMemoryProtocolSink
import org.opentibia.canary.network.protocol.InspectionPacketHandler
import org.opentibia.canary.network.protocol.NettyProtocolSink
import org.opentibia.canary.systems.proficiency.ProficiencyPerk
import org.opentibia.canary.systems.proficiency.WeaponProficiencyData
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Transcript tests for `ProtocolGame::sendWeaponProficiencyWindow`
 * (0xC4, src/server/network/protocol/protocolgame.cpp:12329) wired into the
 * `INSPECT_PROFICIENCY` inspection type (protocolgame.cpp:2929): the window is
 * resolved from the persisted per-weapon proficiency state
 * (`Player::weaponProficiency().getExperience/getSelectedPerks`) and written as
 * weapon id, experience, the selected `(level, index)` perk list and the
 * profile-gated trailing detail-list boundary.
 */
class WeaponProficiencyWindowTest {

    private fun player(): Player = Player(
        id = 1L,
        name = "Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `sendWeaponProficiencyWindow writes the 0xC4 frame from the persisted proficiency`() {
        val player = player()
        player.features.weaponProficiencies[1234] = WeaponProficiencyData(
            experience = 123456,
            perks = mutableListOf(
                ProficiencyPerk(level = 2, index = 3),
                ProficiencyPerk(level = 5, index = 7),
            ),
        )
        val sink = InMemoryProtocolSink(player)

        sink.sendWeaponProficiencyWindow(1234)

        assertThat(sink.frames).containsExactly(
            bytes(
                0xC4,
                0xD2, 0x04, // weaponId uint16 LE
                0x40, 0xE2, 0x01, 0x00, // experience uint32 LE
                0x02, // perk count
                0x02, 0x03, // perk 0: level, index
                0x05, 0x07, // perk 1: level, index
            ),
        )
    }

    @Test
    fun `weapon id zero resolves the equipped weapon and an unarmed player reports zero`() {
        val player = player()
        val sink = InMemoryProtocolSink(player)

        sink.sendWeaponProficiencyWindow(0)

        assertThat(sink.frames).containsExactly(
            bytes(
                0xC4,
                0x00, 0x00, // no equipped weapon
                0x00, 0x00, 0x00, 0x00,
                0x00,
            ),
        )
    }

    @Test
    fun `the 0xCD type 4 inspection emits the proficiency window through the sink`() {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        val channel = EmbeddedChannel()
        try {
            val player = player()
            player.features.weaponProficiencies[1234] = WeaponProficiencyData(experience = 10)
            player.protocolSink = NettyProtocolSink(channel, player, GameMap())

            val frame = Unpooled.buffer()
            frame.writeByte(InspectionPacketHandler.OPCODE_INSPECTION_OBJECT)
            frame.writeByte(InspectionPacketHandler.INSPECT_PROFICIENCY)
            frame.writeShortLE(1234)
            frame.writeByte(0) // unknown byte

            val handled = GamePacketRouter.route(
                player,
                frame,
                GamePacketContext(
                    map = GameMap(),
                    dispatcher = dispatcher,
                    luaEnvironment = LuaEnvironment().apply { initState() },
                ),
            )
            awaitWorldCommit(dispatcher)

            assertThat(handled).isEqualTo(1)
            val out = channel.readOutbound<ByteBuf>()
            assertThat(out).isNotNull
            assertThat(out.readUnsignedByte().toInt()).isEqualTo(0xC4)
            assertThat(out.readUnsignedShortLE()).isEqualTo(1234)
            assertThat(out.readIntLE()).isEqualTo(10)
            assertThat(out.readUnsignedByte().toInt()).isEqualTo(0)
            out.release()
        } finally {
            channel.finishAndReleaseAll()
            dispatcher.shutdown()
        }
    }

    private fun awaitWorldCommit(dispatcher: GameDispatcher) {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }
}
