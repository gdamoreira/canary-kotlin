package org.opentibia.canary.network.protocol.forge

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.systems.forge.ForgeAction_t
import org.opentibia.canary.systems.forge.ForgeHistory

class ForgeHistorySerializerTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun expectedBytes(block: ByteBuf.() -> Unit): ByteArray {
        val buffer = Unpooled.buffer()
        buffer.block()
        return buffer.toByteArray()
    }

    private fun entry(
        createdAt: Long,
        action: ForgeAction_t = ForgeAction_t.FUSION,
        description: String = "",
        bonus: Int = 0,
    ): ForgeHistory = ForgeHistory(
        actionType = action,
        createdAt = createdAt,
        description = description,
        bonus = bonus,
    )

    @Test
    fun `serializes an empty forge history page`() {
        val out = Unpooled.buffer()

        ForgeHistorySerializer.writeForgeHistory(out, page = 0, history = emptyList())

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x88, // PACKET_FORGE_HISTORY
                0x00, 0x00, // current page index
                0x01, 0x00, // last page
                0x00, // history entries sent
            )
        )
    }

    @Test
    fun `writes the newest nine entries in reverse order on the first page`() {
        val history = List(10) { entry(createdAt = (it + 1).toLong()) }
        val out = Unpooled.buffer()

        ForgeHistorySerializer.writeForgeHistory(out, page = 0, history = history)

        assertThat(out.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x88)
                writeShortLE(0) // current page index
                writeShortLE(2) // last page
                writeByte(9)
                for (createdAt in 10 downTo 2) {
                    writeIntLE(createdAt)
                    writeByte(ForgeAction_t.FUSION.value)
                    writeTibiaString("")
                    writeByte(0x00)
                }
            }
        )
    }

    @Test
    fun `writes the oldest entry on the last page and clamps pages past the end`() {
        val history = List(10) { entry(createdAt = (it + 1).toLong()) }

        for (page in listOf(1, 9)) {
            val out = Unpooled.buffer()

            ForgeHistorySerializer.writeForgeHistory(out, page = page, history = history)

            assertThat(out.toByteArray())
                .describedAs("page %d clamps to the last page", page)
                .containsExactly(
                    *expectedBytes {
                        writeByte(0x88)
                        writeShortLE(1) // current page index
                        writeShortLE(2) // last page
                        writeByte(1)
                        writeIntLE(1)
                        writeByte(ForgeAction_t.FUSION.value)
                        writeTibiaString("")
                        writeByte(0x00)
                    }
                )
        }
    }

    @Test
    fun `writes the entry action description and bonus flag`() {
        val history = listOf(
            entry(0x01020304L, action = ForgeAction_t.TRANSFER, description = "Fused", bonus = 1),
            entry(2L, action = ForgeAction_t.INCREASELIMIT, description = "", bonus = 8),
            entry(3L, action = ForgeAction_t.FUSION, description = "Kept", bonus = 7),
        )
        val out = Unpooled.buffer()

        ForgeHistorySerializer.writeForgeHistory(out, page = 0, history = history)

        assertThat(out.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x88)
                writeShortLE(0)
                writeShortLE(1)
                writeByte(3)
                writeIntLE(3)
                writeByte(ForgeAction_t.FUSION.value)
                writeTibiaString("Kept")
                writeByte(0x01) // bonus 7 keeps the item
                writeIntLE(2)
                writeByte(ForgeAction_t.INCREASELIMIT.value)
                writeTibiaString("")
                writeByte(0x00) // bonus 8 is outside 1..7
                writeIntLE(0x01020304)
                writeByte(ForgeAction_t.TRANSFER.value)
                writeTibiaString("Fused")
                writeByte(0x01) // bonus 1 keeps the dust
            }
        )
    }

    @Test
    fun `duplicates each forge skill stat as two little endian uint16 values`() {
        val out = Unpooled.buffer()

        ForgeHistorySerializer.writeForgeSkillStats(
            out,
            ForgeSkillStats(onslaught = 1234, dodge = 0x0102, momentum = 7, transcendence = 0xFFFE),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD2, 0x04, 0xD2, 0x04, // onslaught 1234
                0x02, 0x01, 0x02, 0x01, // dodge 0x0102
                0x07, 0x00, 0x07, 0x00, // momentum 7
                0xFE, 0xFF, 0xFE, 0xFF, // transcendence 0xFFFE
            )
        )
    }

    @Test
    fun `skips forge skill stats for empty inventory slots`() {
        val out = Unpooled.buffer()

        ForgeHistorySerializer.writeForgeSkillStats(out, ForgeSkillStats())

        assertThat(out.toByteArray()).isEmpty()
    }

    @Test
    fun `writes only the populated forge skill slots in slot order`() {
        val out = Unpooled.buffer()

        ForgeHistorySerializer.writeForgeSkillStats(
            out,
            ForgeSkillStats(onslaught = 1234, momentum = 7),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD2, 0x04, 0xD2, 0x04, // onslaught 1234
                0x07, 0x00, 0x07, 0x00, // momentum 7
            )
        )
    }

    @Test
    fun `writes the forge history frame through an embedded channel`() {
        val channel = EmbeddedChannel()
        try {
            val out = Unpooled.buffer()
            ForgeHistorySerializer.writeForgeHistory(out, page = 0, history = emptyList())

            assertThat(channel.writeOutbound(out)).isTrue()
            val frame = channel.readOutbound<ByteBuf>()
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0x88)
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
