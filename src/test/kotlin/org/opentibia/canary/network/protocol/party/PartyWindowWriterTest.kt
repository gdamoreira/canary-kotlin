package org.opentibia.canary.network.protocol.party

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.network.protocol.CreatureAppearanceFrame
import org.opentibia.canary.network.protocol.CreatureType

class PartyWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun knownFrame(id: Long = 5000L) = CreatureAppearanceFrame(
        id = id,
        name = "Bob",
        health = 80,
        maxHealth = 100,
        direction = Direction.South,
        outfit = Outfit(
            lookType = 128,
            lookHead = 1,
            lookBody = 2,
            lookLegs = 3,
            lookFeet = 4,
            lookAddons = 5,
        ),
        light = LightInfo(level = 7, color = 215),
        stepSpeed = 200,
        creatureType = CreatureType.PLAYER,
        known = true,
        vocationClientId = 4,
    )

    private fun unknownFrame(id: Long = 5001L, removedKnown: Long = 77L) = CreatureAppearanceFrame(
        id = id,
        name = "Demon",
        health = 100,
        maxHealth = 100,
        direction = Direction.North,
        outfit = Outfit(lookType = 0, lookTypeEx = 99),
        light = LightInfo(level = 3, color = 100),
        stepSpeed = 180,
        creatureType = CreatureType.MONSTER,
        known = false,
        removedKnown = removedKnown,
        masterId = 42L,
        masterIsPlayer = true,
        guildEmblem = 2,
        speechBubble = 7,
        accessPlayer = true,
        icons = listOf(
            org.opentibia.canary.network.protocol.CreatureIcon(serialize = 1, category = 2, count = 3),
            org.opentibia.canary.network.protocol.CreatureIcon(serialize = 5, category = 6, count = 7),
            org.opentibia.canary.network.protocol.CreatureIcon(serialize = 8, category = 9, count = 10),
        ),
    )

    @Test
    fun `writes party creature update 0x8B with the known AddCreature payload`() {
        val out = Unpooled.buffer()

        PartyWindowWriter.writePartyCreatureUpdate(out, 5000L, knownFrame())

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x8B,
                0x88, 0x13, 0x00, 0x00, // creature id 5000
                0x00, // creature update
                0x62, 0x00, // known marker
                0x88, 0x13, 0x00, 0x00, // id 5000
                0x50, // health 80%
                0x02, // south
                0x80, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, // outfit
                0x00, 0x00, // no mount
                0x07, 0xD7, // light level/color
                0xC8, 0x00, // step speed
                0x00, // no icons
                0x00, // skull
                0x00, // party shield
                0x00, // player creature type
                0x04, // vocation client id
                0x00, // speech bubble
                0xFF, // unmarked
                0x00, // inspection type
                0x01, // not walkthrough
            )
        )
    }

    @Test
    fun `party creature update is suppressed for the old protocol`() {
        val out = Unpooled.buffer()

        PartyWindowWriter.writePartyCreatureUpdate(out, 5000L, knownFrame(), oldProtocol = true)

        assertThat(out.toByteArray()).isEmpty()
    }

    @Test
    fun `writes party creature shield 0x91 for a known creature`() {
        val out = Unpooled.buffer()

        PartyWindowWriter.writePartyCreatureShield(out, 5000L, knownFrame(), partyShield = 3)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0x91, 0x88, 0x13, 0x00, 0x00, 0x03)
        )
    }

    @Test
    fun `party creature shield falls back to the 0x8B update for an unknown creature`() {
        val out = Unpooled.buffer()
        val expected = Unpooled.buffer()

        PartyWindowWriter.writePartyCreatureShield(out, 5001L, unknownFrame(), partyShield = 3)
        PartyWindowWriter.writePartyCreatureUpdate(expected, 5001L, unknownFrame())

        assertThat(out.toByteArray()).containsExactly(*expected.toByteArray())
    }

    @Test
    fun `writes party creature skull 0x90 for a known creature on a pvp world`() {
        val out = Unpooled.buffer()

        PartyWindowWriter.writePartyCreatureSkull(out, 5000L, knownFrame(), skullClient = 4, pvpWorld = true)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0x90, 0x88, 0x13, 0x00, 0x00, 0x04)
        )
    }

    @Test
    fun `party creature skull is suppressed outside pvp worlds`() {
        val out = Unpooled.buffer()

        PartyWindowWriter.writePartyCreatureSkull(out, 5000L, knownFrame(), skullClient = 4, pvpWorld = false)

        assertThat(out.toByteArray()).isEmpty()
    }

    @Test
    fun `writes party creature health 0x8C and clamps the percent to 100`() {
        val out = Unpooled.buffer()

        PartyWindowWriter.writePartyCreatureHealth(out, 5000L, knownFrame(), healthPercent = 42)
        PartyWindowWriter.writePartyCreatureHealth(out, 5000L, knownFrame(), healthPercent = 250)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x8C, 0x88, 0x13, 0x00, 0x00, 0x2A,
                0x8C, 0x88, 0x13, 0x00, 0x00, 0x64,
            )
        )
    }

    @Test
    fun `writes party player mana 0x8B sub 11 after an update for an unknown creature`() {
        val out = Unpooled.buffer()
        val expected = Unpooled.buffer()

        PartyWindowWriter.writePartyPlayerMana(out, 5001L, unknownFrame(), manaPercent = 55)
        PartyWindowWriter.writePartyCreatureUpdate(expected, 5001L, unknownFrame())
        expected.writeByte(0x8B)
        expected.writeIntLE(5001)
        expected.writeByte(0x0B)
        expected.writeByte(0x37)

        assertThat(out.toByteArray()).containsExactly(*expected.toByteArray())
    }

    @Test
    fun `writes party player mana 0x8B sub 11 and clamps the percent to 100`() {
        val out = Unpooled.buffer()

        PartyWindowWriter.writePartyPlayerMana(out, 5000L, knownFrame(), manaPercent = 101)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0x8B, 0x88, 0x13, 0x00, 0x00, 0x0B, 0x64)
        )
    }

    @Test
    fun `writes party creature show status 0x8B sub 12`() {
        val out = Unpooled.buffer()

        PartyWindowWriter.writePartyCreatureShowStatus(out, 5000L, knownFrame(), showStatus = true)
        PartyWindowWriter.writePartyCreatureShowStatus(out, 5000L, knownFrame(), showStatus = false)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x8B, 0x88, 0x13, 0x00, 0x00, 0x0C, 0x01,
                0x8B, 0x88, 0x13, 0x00, 0x00, 0x0C, 0x00,
            )
        )
    }

    @Test
    fun `writes party player vocation 0x8B sub 13`() {
        val out = Unpooled.buffer()

        PartyWindowWriter.writePartyPlayerVocation(out, 5000L, knownFrame(), vocationClientId = 5)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0x8B, 0x88, 0x13, 0x00, 0x00, 0x0D, 0x05)
        )
    }

    @Test
    fun `party player vocation falls back to the 0x8B update and stops for an unknown creature`() {
        val out = Unpooled.buffer()
        val expected = Unpooled.buffer()

        PartyWindowWriter.writePartyPlayerVocation(out, 5001L, unknownFrame(), vocationClientId = 5)
        PartyWindowWriter.writePartyCreatureUpdate(expected, 5001L, unknownFrame())

        assertThat(out.toByteArray()).containsExactly(*expected.toByteArray())
    }

    @Test
    fun `party player vocation is suppressed for the old protocol`() {
        val out = Unpooled.buffer()

        PartyWindowWriter.writePartyPlayerVocation(out, 5000L, knownFrame(), vocationClientId = 5, oldProtocol = true)

        assertThat(out.toByteArray()).isEmpty()
    }
}
