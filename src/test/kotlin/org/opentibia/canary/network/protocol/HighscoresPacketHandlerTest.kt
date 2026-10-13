package org.opentibia.canary.network.protocol

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Decode tests for `0xB1` `ProtocolGame::parseHighscores`
 * (src/server/network/protocol/protocolgame.cpp:3046-3063).
 */
class HighscoresPacketHandlerTest {

    @Test
    fun `decodes a get entries request and clamps entries per page to 30`() {
        val payload = Unpooled.buffer()
        payload.writeByte(HighscoresPacketHandler.HIGHSCORE_GETENTRIES)
        payload.writeByte(7) // category
        payload.writeIntLE(0xFFFFFFFFL.toInt()) // vocation (all)
        payload.writeTibiaString("Antica")
        payload.writeByte(1) // game world category
        payload.writeByte(2) // battleye world type
        payload.writeShortLE(3) // page
        payload.writeByte(50) // entries per page clamps to 30

        val command = HighscoresPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            HighscoresCommand(
                type = HighscoresPacketHandler.HIGHSCORE_GETENTRIES,
                category = 7,
                vocation = 0xFFFFFFFFL,
                worldName = "Antica",
                gameWorldCategory = 1,
                battlEyeWorldType = 2,
                page = 3,
                entriesPerPage = 30,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes our rank without a page field and clamps entries per page to 5`() {
        val payload = Unpooled.buffer()
        payload.writeByte(HighscoresPacketHandler.HIGHSCORE_OURRANK)
        payload.writeByte(3)
        payload.writeIntLE(4)
        payload.writeTibiaString("Antica")
        payload.writeByte(0)
        payload.writeByte(0)
        payload.writeByte(1) // entries per page clamps to 5

        val command = HighscoresPacketHandler.parse(payload)

        assertThat(command).isEqualTo(
            HighscoresCommand(
                type = HighscoresPacketHandler.HIGHSCORE_OURRANK,
                category = 3,
                vocation = 4,
                worldName = "Antica",
                gameWorldCategory = 0,
                battlEyeWorldType = 0,
                page = 1,
                entriesPerPage = 5,
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `clamps a zero page to one for get entries`() {
        val payload = Unpooled.buffer()
        payload.writeByte(HighscoresPacketHandler.HIGHSCORE_GETENTRIES)
        payload.writeByte(0)
        payload.writeIntLE(0)
        payload.writeTibiaString("")
        payload.writeByte(0)
        payload.writeByte(0)
        payload.writeShortLE(0)
        payload.writeByte(5)

        val command = HighscoresPacketHandler.parse(payload)

        assertThat(command?.page).isEqualTo(1)
        assertThat(command?.entriesPerPage).isEqualTo(5)
    }

    @Test
    fun `returns null when any highscores field is truncated`() {
        val missingEntriesPerPage = Unpooled.buffer()
        missingEntriesPerPage.writeByte(HighscoresPacketHandler.HIGHSCORE_OURRANK)
        missingEntriesPerPage.writeByte(0)
        missingEntriesPerPage.writeIntLE(0)
        missingEntriesPerPage.writeTibiaString("Antica")
        missingEntriesPerPage.writeByte(0)
        missingEntriesPerPage.writeByte(0)
        assertThat(HighscoresPacketHandler.parse(missingEntriesPerPage)).isNull()

        val missingPage = Unpooled.buffer()
        missingPage.writeByte(HighscoresPacketHandler.HIGHSCORE_GETENTRIES)
        missingPage.writeByte(0)
        missingPage.writeIntLE(0)
        missingPage.writeTibiaString("Antica")
        missingPage.writeByte(0)
        missingPage.writeByte(0)
        missingPage.writeByte(5)
        assertThat(HighscoresPacketHandler.parse(missingPage)).isNull()

        assertThat(HighscoresPacketHandler.parse(Unpooled.buffer())).isNull()
    }
}
