package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.playerSendSink
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Transport tests for [NettyProtocolSink], the live `Player::client` installed
 * by `GameHandler` on login. Every case drives a sender and reads the frame
 * back off an [EmbeddedChannel], pinning the same hand-derived wire fixtures
 * the in-memory sink tests use; the party case covers the extended
 * `PlayerSendSink` surface that a base-only sink silently drops.
 *
 * Canary references:
 *  - `ProtocolGame::sendStats`             (0xA0, protocolgame.cpp:9782)
 *  - `ProtocolGame::sendAddContainerItem`  (0x70, protocolgame.cpp:8934)
 *  - `ProtocolGame::sendPartyCreatureHealth` (0x8C/0x8B, protocolgame.cpp:8383)
 */
class NettyProtocolSinkTest {

    private var channel: EmbeddedChannel? = null

    @AfterEach
    fun tearDown() {
        channel?.finishAndReleaseAll()
    }

    private fun openChannel(): EmbeddedChannel =
        EmbeddedChannel().also { channel = it }

    private fun readFrame(channel: EmbeddedChannel): ByteArray {
        val frame = channel.readOutbound<ByteBuf>() ?: error("no outbound frame was written")
        val bytes = ByteArray(frame.readableBytes())
        frame.readBytes(bytes)
        frame.release()
        return bytes
    }

    private fun player(health: Int = 150): Player = Player(
        id = 1,
        name = "Observer",
        maxHealth = 150,
        health = health,
        maxMana = 50,
        mana = 50,
        capacity = 400.0,
        position = Position(100, 200, 7),
    )

    private fun rat(): Monster =
        Monster(5000, "Rat", maxHealth = 100, health = 80, position = Position(100, 200, 7))

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `the sink attached to the player resolves as the player send surface`() {
        val player = player()
        player.protocolSink = NettyProtocolSink(openChannel(), player, GameMap())

        assertThat(player.playerSendSink).isNotNull()
    }

    @Test
    fun `sendStats writes 0xA0 onto the live channel`() {
        val player = player(health = 100)
        val channel = openChannel()

        NettyProtocolSink(channel, player, GameMap()).sendStats(player)

        assertThat(readFrame(channel)).isEqualTo(
            bytes(
                0xA0,
                0x64, 0x00, 0x00, 0x00,
                0x96, 0x00, 0x00, 0x00,
                0x90, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x01, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x64, 0x00,
                0x32, 0x00, 0x00, 0x00,
                0x32, 0x00, 0x00, 0x00,
                0x00,
                0x00, 0x00,
                0xDC, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x01,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
            ),
        )
    }

    @Test
    fun `sendAddContainerItem writes 0x70 onto the live channel`() {
        val player = player()
        val channel = openChannel()

        NettyProtocolSink(channel, player, GameMap())
            .sendAddContainerItem(2, 5, Item(id = 1234))

        assertThat(readFrame(channel)).isEqualTo(
            bytes(0x70, 0x02, 0x05, 0x00, 0xD2, 0x04),
        )
    }

    @Test
    fun `sendPartyCreatureHealth writes the unknown-creature update onto the live channel`() {
        val player = player()
        val channel = openChannel()

        NettyProtocolSink(channel, player, GameMap())
            .sendPartyCreatureHealth(rat(), 80)

        assertThat(readFrame(channel)).isEqualTo(
            bytes(
                0x8B,
                0x88, 0x13, 0x00, 0x00,
                0x00,
                0x61, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x88, 0x13, 0x00, 0x00,
                0x01,
                0x03, 0x00, 'R'.code, 'a'.code, 't'.code,
                0x50,
                0x02,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0xD7,
                0xC8, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0xFF, 0x00, 0x01,
            ),
        )
    }
}
