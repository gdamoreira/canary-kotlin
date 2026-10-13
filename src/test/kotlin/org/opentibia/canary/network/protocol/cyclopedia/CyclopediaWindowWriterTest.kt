package org.opentibia.canary.network.protocol.cyclopedia

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Outfit

class CyclopediaWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes cyclopedia character no data 0xDA with the requested type and error code`() {
        val out = Unpooled.buffer()

        CyclopediaWindowWriter.writeCyclopediaCharacterNoData(
            out,
            characterInfoType = CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_GENERALSTATS,
            errorCode = 1,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xDA,
                0x01,
                0x01,
            )
        )
    }

    @Test
    fun `writes cyclopedia base information 0xDA with the look type outfit and current title`() {
        val out = Unpooled.buffer()

        CyclopediaWindowWriter.writeCyclopediaCharacterBaseInformation(
            out,
            name = "Knight",
            vocationName = "Elite Knight",
            level = 300,
            defaultOutfit = Outfit(
                lookType = 128,
                lookHead = 1,
                lookBody = 2,
                lookLegs = 3,
                lookFeet = 4,
                lookAddons = 5,
            ),
            currentTitleName = "Dragon Slayer",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xDA,
                0x00,
                0x00,
                0x06, 0x00, 0x4B, 0x6E, 0x69, 0x67, 0x68, 0x74,
                0x0C, 0x00, 0x45, 0x6C, 0x69, 0x74, 0x65, 0x20, 0x4B, 0x6E, 0x69, 0x67, 0x68, 0x74,
                0x2C, 0x01,
                0x80, 0x00,
                0x01, 0x02, 0x03, 0x04, 0x05,
                0x01,
                0x0D, 0x00, 0x44, 0x72, 0x61, 0x67, 0x6F, 0x6E, 0x20, 0x53, 0x6C, 0x61, 0x79, 0x65, 0x72,
            )
        )
    }

    @Test
    fun `writes cyclopedia base information 0xDA with the item id outfit when look type is zero`() {
        val out = Unpooled.buffer()

        CyclopediaWindowWriter.writeCyclopediaCharacterBaseInformation(
            out,
            name = "A",
            vocationName = "",
            level = 1,
            defaultOutfit = Outfit(lookType = 0, lookTypeEx = 21),
            currentTitleName = "",
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xDA,
                0x00,
                0x00,
                0x01, 0x00, 0x41,
                0x00, 0x00,
                0x01, 0x00,
                0x00, 0x00, 0x15, 0x00,
                0x01,
                0x00, 0x00,
            )
        )
    }
}
