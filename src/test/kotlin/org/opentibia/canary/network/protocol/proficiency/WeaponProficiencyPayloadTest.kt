package org.opentibia.canary.network.protocol.proficiency

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.systems.proficiency.ProficiencyPerk

class WeaponProficiencyPayloadTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    @Test
    fun `serializes weapon proficiency window payload in canary order`() {
        val out = Unpooled.buffer()

        WeaponProficiencyPayload.writeWeaponProficiencyWindow(
            out = out,
            weaponId = 0x1234,
            experience = 123456,
            selectedPerks = listOf(
                ProficiencyPerk(level = 2, index = 3),
                ProficiencyPerk(level = 5, index = 7),
            ),
            sendDetailList = true,
        )

        assertThat(out.toByteArray()).containsExactly(
            0xC4.toByte(), // opcode
            0x34.toByte(), 0x12.toByte(), // weaponId uint16 LE
            0x40.toByte(), 0xE2.toByte(), 0x01.toByte(), 0x00.toByte(), // experience uint32 LE
            0x02.toByte(), // perk count
            0x02.toByte(), 0x03.toByte(), // perk 0: level, index
            0x05.toByte(), 0x07.toByte(), // perk 1: level, index
            0x00.toByte(), // trailing detail-list boundary
        )
    }

    @Test
    fun `omits detail list boundary when profile does not require it`() {
        val out = Unpooled.buffer()

        WeaponProficiencyPayload.writeWeaponProficiencyWindow(
            out = out,
            weaponId = 0x1234,
            experience = 123456,
            selectedPerks = emptyList(),
            sendDetailList = false,
        )

        assertThat(out.toByteArray()).containsExactly(
            0xC4.toByte(),
            0x34.toByte(), 0x12.toByte(),
            0x40.toByte(), 0xE2.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x00.toByte(), // perk count
        )
    }

    @Test
    fun `clamps perk count to a single byte and writes only the first 255 perks`() {
        val out = Unpooled.buffer()

        val perks = (0 until 300).map { ProficiencyPerk(level = it, index = it) }

        WeaponProficiencyPayload.writeWeaponProficiencyWindow(
            out = out,
            weaponId = 1,
            experience = 0,
            selectedPerks = perks,
            sendDetailList = false,
        )

        // opcode + weaponId + experience + count + 255 * 2 bytes
        assertThat(out.readableBytes()).isEqualTo(1 + 2 + 4 + 1 + 255 * 2)
        assertThat(out.readUnsignedByte().toInt()).isEqualTo(0xC4)
        out.skipBytes(2 + 4)
        assertThat(out.readUnsignedByte().toInt()).isEqualTo(0xFF)
        assertThat(out.readUnsignedByte().toInt()).isEqualTo(0) // first perk level
        assertThat(out.readUnsignedByte().toInt()).isEqualTo(0) // first perk index
        assertThat(out.readableBytes()).isEqualTo(254 * 2)
    }

    @Test
    fun `serializes weapon proficiency payload with little endian fields`() {
        val out = Unpooled.buffer()

        WeaponProficiencyPayload.writeWeaponProficiency(
            out = out,
            weaponId = 0x1234,
            experience = 123456,
            upgradeAvailable = true,
        )

        assertThat(out.toByteArray()).containsExactly(
            0x5C.toByte(), // opcode
            0x34.toByte(), 0x12.toByte(), // weaponId uint16 LE
            0x40.toByte(), 0xE2.toByte(), 0x01.toByte(), 0x00.toByte(), // experience uint32 LE
            0x01.toByte(), // upgrade available
        )
    }

    @Test
    fun `weapon proficiency payload zeroes experience and upgrade for weapon id zero`() {
        val out = Unpooled.buffer()

        WeaponProficiencyPayload.writeWeaponProficiency(
            out = out,
            weaponId = 0,
            experience = 123456,
            upgradeAvailable = true,
        )

        assertThat(out.toByteArray()).containsExactly(
            0x5C.toByte(),
            0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(),
        )
    }
}
