package org.opentibia.canary.network.protocol.misc

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.connection.SessionEndInformations
import org.opentibia.canary.network.protocol.IconBakragore

class MiscSessionWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun tibiaString(value: String): ByteArray {
        val payload = value.toByteArray(StandardCharsets.UTF_8)
        return bytes(payload.size and 0xFF, (payload.size shr 8) and 0xFF) + payload
    }

    @Test
    fun `writes the re-login window 0x28 with the current-profile payload`() {
        val out = Unpooled.buffer()

        MiscSessionWriter.writeReLoginWindow(out, unfairFightReduction = 0x0A)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x28,
                0x00,
                0x0A,
                0x00,
            )
        )
    }

    @Test
    fun `writes the FYI box 0x15 with the message string`() {
        val out = Unpooled.buffer()

        MiscSessionWriter.writeFYIBox(out, "hi")

        assertThat(out.toByteArray()).containsExactly(*bytes(0x15), *tibiaString("hi"))
    }

    @Test
    fun `writes the allow bug report 0x1A with the enable byte`() {
        val out = Unpooled.buffer()

        MiscSessionWriter.writeAllowBugReport(out)

        assertThat(out.toByteArray()).containsExactly(*bytes(0x1A, 0x00))
    }

    @Test
    fun `writes the unjustified points 0xB7 with the seven progress bytes`() {
        val out = Unpooled.buffer()

        MiscSessionWriter.writeUnjustifiedPoints(
            out,
            dayProgress = 1,
            dayLeft = 2,
            weekProgress = 3,
            weekLeft = 4,
            monthProgress = 5,
            monthLeft = 6,
            skullDuration = 7,
        )

        assertThat(out.toByteArray()).containsExactly(*bytes(0xB7, 1, 2, 3, 4, 5, 6, 7))
    }

    @Test
    fun `writes the open PvP situations 0xB8 with the situations byte`() {
        val out = Unpooled.buffer()

        MiscSessionWriter.writeOpenPvpSituations(out, openPvpSituations = 2)

        assertThat(out.toByteArray()).containsExactly(*bytes(0xB8, 0x02))
    }

    @Test
    fun `writes the pending state entered 0x0A opcode only`() {
        val out = Unpooled.buffer()

        MiscSessionWriter.writePendingStateEntered(out)

        assertThat(out.toByteArray()).containsExactly(*bytes(0x0A))
    }

    @Test
    fun `writes the enter world 0x0F opcode only`() {
        val out = Unpooled.buffer()

        MiscSessionWriter.writeEnterWorld(out)

        assertThat(out.toByteArray()).containsExactly(*bytes(0x0F))
    }

    @Test
    fun `writes the session end information 0x18 with the reason byte`() {
        val out = Unpooled.buffer()

        MiscSessionWriter.writeSessionEndInformation(out, SessionEndInformations.ForceClose)

        assertThat(out.toByteArray()).containsExactly(*bytes(0x18, 0x02))
    }

    @Test
    fun `writes the disable login music 0x85 frame with the music sub-opcode`() {
        val out = Unpooled.buffer()

        MiscSessionWriter.writeDisableLoginMusic(out)

        assertThat(out.toByteArray()).containsExactly(*bytes(0x85, 0x01, 0x00, 0x00))
    }

    @Test
    fun `writes the player vocation 0x8B with the fixed vocation byte 13`() {
        val out = Unpooled.buffer()

        MiscSessionWriter.writePlayerVocation(out, targetId = 0x01020304L, vocationClientId = 4)

        assertThat(out.toByteArray()).containsExactly(*bytes(0x8B, 0x04, 0x03, 0x02, 0x01, 0x0D, 0x04))
    }

    @Test
    fun `writes the premium trigger 0x9E with sixteen set bytes`() {
        val out = Unpooled.buffer()

        MiscSessionWriter.writePremiumTrigger(out)

        assertThat(out.toByteArray()).containsExactly(*bytes(0x9E, 0x10), *ByteArray(16) { 0x01 })
    }

    @Test
    fun `writes the Bakragore icon 0xA2 with an empty icon bitset`() {
        val out = Unpooled.buffer()

        MiscSessionWriter.writeIconBakragore(out, IconBakragore.Taint7)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xA2,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x07,
            )
        )
    }

    @Test
    fun `writes the client check 0x63 with its one-byte payload`() {
        val out = Unpooled.buffer()

        MiscSessionWriter.writeClientCheck(out)

        assertThat(out.toByteArray()).containsExactly(*bytes(0x63, 0x01, 0x00, 0x00, 0x00, 0x01))
    }

    @Test
    fun `writes the game news 0x98 with category one and highlight set`() {
        val out = Unpooled.buffer()

        MiscSessionWriter.writeGameNews(out)

        assertThat(out.toByteArray()).containsExactly(*bytes(0x98, 0x01, 0x00, 0x00, 0x00, 0x01))
    }

    @Test
    fun `writes the current-profile ping 0x1D and ping back 0x1E`() {
        val ping = Unpooled.buffer()
        val pingBack = Unpooled.buffer()

        MiscSessionWriter.writePing(ping)
        MiscSessionWriter.writePingBack(pingBack)

        assertThat(ping.toByteArray()).containsExactly(*bytes(0x1D))
        assertThat(pingBack.toByteArray()).containsExactly(*bytes(0x1E))
    }

    @Test
    fun `writes the resting status 0xA9 without an active bonus`() {
        val out = Unpooled.buffer()

        MiscSessionWriter.writeRestingStatus(out, protection = 1, dailyStreak = 0)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0xA9, 0x01, 0x00),
            *tibiaString("Resting Area (no active bonus)"),
        )
    }

    @Test
    fun `writes the resting status 0xA9 with the HP bonus only`() {
        val out = Unpooled.buffer()

        MiscSessionWriter.writeRestingStatus(out, protection = 0, dailyStreak = 2)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0xA9, 0x00, 0x01),
            *tibiaString("Active Resting Area Bonuses: \nHit Points Regeneration."),
        )
    }

    @Test
    fun `writes the resting status 0xA9 with double HP, mana and stamina`() {
        val out = Unpooled.buffer()

        MiscSessionWriter.writeRestingStatus(out, protection = 1, dailyStreak = 5)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0xA9, 0x01, 0x01),
            *tibiaString(
                "Active Resting Area Bonuses: \nDouble Hit Points Regeneration," +
                    "\nMana Points Regeneration,\nStamina Points Regeneration."
            ),
        )
    }

    @Test
    fun `writes the resting status 0xA9 with every bonus at streak seven`() {
        val out = Unpooled.buffer()

        MiscSessionWriter.writeRestingStatus(out, protection = 0, dailyStreak = 7)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0xA9, 0x00, 0x01),
            *tibiaString(
                "Active Resting Area Bonuses: \nDouble Hit Points Regeneration," +
                    "\nDouble Mana Points Regeneration,\nStamina Points Regeneration," +
                    "\nSoul Points Regeneration."
            ),
        )
    }
}
