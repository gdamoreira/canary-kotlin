package org.opentibia.canary.network.protocol.player

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.game.movement.Direction

class PlayerWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes tutorial 0xDC with the tutorial id`() {
        val out = Unpooled.buffer()

        PlayerWindowWriter.writeTutorial(out, 5)

        assertThat(out.toByteArray()).containsExactly(*bytes(0xDC, 0x05))
    }

    @Test
    fun `writes cancel target 0xA3 with the zero creature id`() {
        val out = Unpooled.buffer()

        PlayerWindowWriter.writeCancelTarget(out)

        assertThat(out.toByteArray()).containsExactly(*bytes(0xA3, 0x00, 0x00, 0x00, 0x00))
    }

    @Test
    fun `writes cancel walk 0xB5 with the player direction byte`() {
        val out = Unpooled.buffer()

        PlayerWindowWriter.writeCancelWalk(out, Direction.North)
        PlayerWindowWriter.writeCancelWalk(out, Direction.SouthWest)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xB5, 0x00,
                0xB5, 0x04,
            )
        )
    }

    @Test
    fun `writes fight modes 0xA7 with chase secure and dove`() {
        val out = Unpooled.buffer()

        PlayerWindowWriter.writeFightModes(out, chaseMode = true, secureMode = true)
        PlayerWindowWriter.writeFightModes(out, chaseMode = false, secureMode = true)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xA7, 0x01, 0x01, 0x00,
                0xA7, 0x00, 0x01, 0x00,
            )
        )
    }

    @Test
    fun `writes spell cooldown 0xA4 with the u16 spell id and u32 time`() {
        val out = Unpooled.buffer()

        PlayerWindowWriter.writeSpellCooldown(out, spellId = 170, time = 1000)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xA4,
                0xAA, 0x00,
                0xE8, 0x03, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes spell group cooldown 0xA5 with the group byte and u32 time`() {
        val out = Unpooled.buffer()

        PlayerWindowWriter.writeSpellGroupCooldown(out, SpellGroup.SPELLGROUP_HEALING, 1000)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xA5,
                0x02,
                0xE8, 0x03, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes use item cooldown 0xA6 with the u32 time`() {
        val out = Unpooled.buffer()

        PlayerWindowWriter.writeUseItemCooldown(out, 1000)

        assertThat(out.toByteArray()).containsExactly(*bytes(0xA6, 0xE8, 0x03, 0x00, 0x00))
    }

    @Test
    fun `writes cancel message as a MESSAGE_FAILURE 0xB4 text`() {
        val out = Unpooled.buffer()

        PlayerWindowWriter.writeCancelMessage(out, "Sorry.")

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xB4, 0x15,
                0x06, 0x00, 0x53, 0x6F, 0x72, 0x72, 0x79, 0x2E,
            )
        )
    }
}
