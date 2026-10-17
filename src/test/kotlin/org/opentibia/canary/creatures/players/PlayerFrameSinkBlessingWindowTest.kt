package org.opentibia.canary.creatures.players

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Item
import org.opentibia.canary.kv.ValueWrapper
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.BlessingWindowWriter
import org.opentibia.canary.network.protocol.InMemoryProtocolSink

/**
 * `ProtocolGame::sendBlessingWindow()` composition (protocolgame.cpp:5899-5968)
 * over the session sink: the stored blessing counts (with and without the store
 * summary), the promoted/skull/amulet-of-loss flags and the retro layout.
 */
class PlayerFrameSinkBlessingWindowTest {

    private fun sinkFor(player: Player): InMemoryProtocolSink =
        InMemoryProtocolSink(player, GameMap())

    private fun player(id: Long, promoVocation: Boolean = false): Player {
        val player = Player(
            id = id,
            name = "Hero",
            maxHealth = 150,
            health = 150,
            position = Position(100, 100, 7),
        )
        if (promoVocation) {
            player.vocationId = 1
        }
        return player
    }

    /** The canonical frame the writer produces for the same resolved inputs. */
    private fun expected(
        blessingCounts: List<Int>,
        storeCounts: List<Int>,
        retro: Boolean = false,
        promoted: Boolean = false,
        hasSkull: Boolean = false,
        usingAol: Boolean = false,
    ): ByteArray {
        val out = Unpooled.buffer()
        BlessingWindowWriter.writeBlessingWindow(
            out = out,
            blessingCounts = blessingCounts,
            storeCounts = storeCounts,
            retro = retro,
            promoted = promoted,
            hasSkull = hasSkull,
            usingAol = usingAol,
        )
        val bytes = ByteArray(out.readableBytes())
        out.readBytes(bytes)
        out.release()
        return bytes
    }

    @Test
    fun `sends the stored blessing counts with the empty store summary`() {
        val player = player(id = 1)
        player.addBlessing(2, 3)
        val sink = sinkFor(player)

        sink.sendBlessingWindow()

        val counts = listOf(0, 3, 0, 0, 0, 0, 0, 0)
        assertThat(sink.frames.single()).isEqualTo(expected(counts, List(8) { 0 }))
    }

    @Test
    fun `reads the store summary amount for the second column`() {
        val player = player(id = 2)
        player.kv.scoped("summary").scoped("blessings").scoped("2").set("amount", ValueWrapper(3))
        val sink = sinkFor(player)

        sink.sendBlessingWindow()

        assertThat(sink.frames.single()).isEqualTo(expected(List(8) { 0 }, listOf(0, 3, 0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun `promoted player with seven blessings reports the reduction bonuses`() {
        val player = player(id = 3, promoVocation = true)
        for (index in 1..8) {
            player.addBlessing(index, 1)
        }
        val sink = sinkFor(player)

        sink.sendBlessingWindow()

        val frame = sink.frames.single()
        assertThat(frame).isEqualTo(expected(List(8) { 1 }, List(8) { 0 }, promoted = true))
        // promoted byte, reduction base 30, min reduction (7 * 8 + 30), max PvP
        // loss and the equipment-loss pair for a fully blessed character.
        assertThat(frame[34]).isEqualTo(0x01)
        assertThat(frame[35]).isEqualTo(30.toByte())
        assertThat(frame[36]).isEqualTo(0x56.toByte())
        assertThat(frame[37]).isEqualTo(0x62.toByte())
        assertThat(frame[38]).isEqualTo(0x56.toByte())
        assertThat(frame.copyOfRange(39, 44)).containsExactly(0, 0, 0, 0, 0)
    }

    @Test
    fun `a skull makes the death-loss bytes the skull pair`() {
        val player = player(id = 4)
        player.setSkull(Skulls_t.SKULL_RED)
        val sink = sinkFor(player)

        sink.sendBlessingWindow()

        val frame = sink.frames.single()
        assertThat(frame).isEqualTo(expected(List(8) { 0 }, List(8) { 0 }, hasSkull = true))
        assertThat(frame.copyOfRange(39, 44)).containsExactly(0x64, 0x64, 0x01, 0x00, 0x00)
    }

    @Test
    fun `an amulet of loss zeroes the equipment-loss pair`() {
        val player = player(id = 5)
        assertThat(player.addItem(Item(id = 3057), Slots_t.CONST_SLOT_NECKLACE))
            .isEqualTo(org.opentibia.canary.systems.rewardchest.ReturnValue.NOERROR)
        val sink = sinkFor(player)

        sink.sendBlessingWindow()

        val frame = sink.frames.single()
        assertThat(frame).isEqualTo(expected(List(8) { 0 }, List(8) { 0 }, usingAol = true))
        assertThat(frame.copyOfRange(39, 44)).containsExactly(0x00, 0x00, 0x00, 0x01, 0x00)
    }

    @Test
    fun `nothing is sent on the old protocol`() {
        val player = player(id = 6)
        val sink = sinkFor(player)
        sink.oldProtocol = true

        sink.sendBlessingWindow()

        assertThat(sink.frames).isEmpty()
    }
}
