package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HighscoresWindowSerializerTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun frame() = HighscoresFrame(
        serverName = "Canary",
        vocations = listOf(
            HighscoresWindowSerializer.HighscoreVocation(id = 1, fromVocation = 1, clientId = 0L, name = "None"),
            HighscoresWindowSerializer.HighscoreVocation(id = 2, fromVocation = 2, clientId = 1L, name = "Knight"),
            HighscoresWindowSerializer.HighscoreVocation(id = 3, fromVocation = 1, clientId = 3L, name = "Promoted Knight"),
        ),
        vocationId = 1L,
        categories = listOf(
            HighscoresWindowSerializer.HighscoreCategory(id = 0, name = "Achievement"),
            HighscoresWindowSerializer.HighscoreCategory(id = 1, name = "PvP"),
        ),
        categoryId = 1,
        page = 1,
        pages = 5,
        characters = listOf(
            HighscoresWindowSerializer.HighscoreCharacterEntry(
                rank = 1L,
                name = "Hero",
                loyaltyTitle = "Warden",
                vocation = 1,
                level = 300,
                id = 99L,
                points = 123_456_789L,
            ),
        ),
        playerId = 99L,
        updateTimer = 3600L,
    )

    @Test
    fun `writes highscores no data 0xB1`() {
        val out = Unpooled.buffer()

        HighscoresWindowSerializer.writeHighscoresNoData(out)

        assertThat(out.toByteArray()).containsExactly(*bytes(0xB1, 0x01))
    }

    @Test
    fun `writes highscores 0xB1 with the filtered vocation list and player indicator`() {
        val out = Unpooled.buffer()

        HighscoresWindowSerializer.writeHighscores(out, frame())

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xB1,
                0x00,
                0x01,
                0x06, 0x00, 0x43, 0x61, 0x6E, 0x61, 0x72, 0x79,
                0x06, 0x00, 0x43, 0x61, 0x6E, 0x61, 0x72, 0x79,
                0x00,
                0x00,
                0x03,
                0xFF, 0xFF, 0xFF, 0xFF,
                0x05, 0x00, 0x28, 0x61, 0x6C, 0x6C, 0x29,
                0x00, 0x00, 0x00, 0x00, 0x04, 0x00, 0x4E, 0x6F, 0x6E, 0x65,
                0x01, 0x00, 0x00, 0x00, 0x06, 0x00, 0x4B, 0x6E, 0x69, 0x67, 0x68, 0x74,
                0x01, 0x00, 0x00, 0x00,
                0x02,
                0x00, 0x0B, 0x00, 0x41, 0x63, 0x68, 0x69, 0x65, 0x76, 0x65, 0x6D, 0x65, 0x6E, 0x74,
                0x01, 0x03, 0x00, 0x50, 0x76, 0x50,
                0x01,
                0x01, 0x00,
                0x05, 0x00,
                0x01,
                0x01, 0x00, 0x00, 0x00,
                0x04, 0x00, 0x48, 0x65, 0x72, 0x6F,
                0x06, 0x00, 0x57, 0x61, 0x72, 0x64, 0x65, 0x6E,
                0x01,
                0x06, 0x00, 0x43, 0x61, 0x6E, 0x61, 0x72, 0x79,
                0x2C, 0x01,
                0x01,
                0x15, 0xCD, 0x5B, 0x07, 0x00, 0x00, 0x00, 0x00,
                0xFF,
                0x00,
                0x01,
                0x10, 0x0E, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `highscores keep the all-vocations selection when no client id matches and flag other players`() {
        val out = Unpooled.buffer()

        HighscoresWindowSerializer.writeHighscores(
            out,
            frame().copy(
                vocationId = 99L,
                categoryId = 7,
                characters = frame().characters.map { it.copy(id = 100L) },
                playerId = 99L,
            ),
        )

        val written = out.toByteArray()
        assertThat(written).containsSubsequence(*bytes(0x4B, 0x6E, 0x69, 0x67, 0x68, 0x74, 0xFF, 0xFF, 0xFF, 0xFF))
        assertThat(written).containsSubsequence(*bytes(0x50, 0x76, 0x50, 0x07, 0x01, 0x00))
        assertThat(written).containsSubsequence(*bytes(0x2C, 0x01, 0x00))
    }
}
