package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.map.Position

class CreatureWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes world light 0x82 with the access player override`() {
        val out = Unpooled.buffer()

        CreatureWindowWriter.writeWorldLight(out, LightInfo(level = 7, color = 215))
        CreatureWindowWriter.writeWorldLight(out, LightInfo(level = 3, color = 100), accessPlayer = true)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x82, 0x07, 0xD7,
                0x82, 0xFF, 0x64,
            )
        )
    }

    @Test
    fun `writes magic effect 0x83 with the current profile source byte`() {
        val out = Unpooled.buffer()

        CreatureWindowWriter.writeMagicEffect(out, Position(100, 200, 7), 5, SourceEffect.OWN)
        CreatureWindowWriter.writeMagicEffect(out, Position(100, 200, 7), 0x1234, SourceEffect.GLOBAL)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x83, 0x64, 0x00, 0xC8, 0x00, 0x07, 0x03, 0x05, 0x00, 0x01, 0x00,
                0x83, 0x64, 0x00, 0xC8, 0x00, 0x07, 0x03, 0x34, 0x12, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes distance shoot 0x83 with signed deltas and the source byte`() {
        val out = Unpooled.buffer()

        CreatureWindowWriter.writeDistanceShoot(
            out,
            Position(100, 200, 7),
            Position(105, 197, 7),
            24,
            SourceEffect.OTHERS,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x83,
                0x64, 0x00, 0xC8, 0x00, 0x07,
                0x04,
                0x18, 0x00,
                0x05, 0xFD,
                0x02,
                0x00,
            )
        )
    }

    @Test
    fun `writes creature health 0x8C with the ceil percent and hides hidden health`() {
        val out = Unpooled.buffer()

        CreatureWindowWriter.writeCreatureHealth(out, id = 5000L, health = 80, maxHealth = 100)
        CreatureWindowWriter.writeCreatureHealth(out, id = 5001L, health = 1, maxHealth = 1000)
        CreatureWindowWriter.writeCreatureHealth(out, id = 5002L, health = 200, maxHealth = 100)
        CreatureWindowWriter.writeCreatureHealth(out, id = 5003L, health = 100, maxHealth = 100, healthHidden = true)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x8C, 0x88, 0x13, 0x00, 0x00, 0x50,
                0x8C, 0x89, 0x13, 0x00, 0x00, 0x01,
                0x8C, 0x8A, 0x13, 0x00, 0x00, 0x64,
            )
        )
    }

    @Test
    fun `writes creature light 0x8D with the access player override`() {
        val out = Unpooled.buffer()

        CreatureWindowWriter.writeCreatureLight(out, 5000L, LightInfo(level = 7, color = 215))
        CreatureWindowWriter.writeCreatureLight(out, 5000L, LightInfo(level = 3, color = 100), accessPlayer = true)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x8D, 0x88, 0x13, 0x00, 0x00, 0x07, 0xD7,
                0x8D, 0x88, 0x13, 0x00, 0x00, 0xFF, 0x64,
            )
        )
    }

    @Test
    fun `writes creature outfit 0x8E and zeroes the mount for a support outfit`() {
        val out = Unpooled.buffer()

        CreatureWindowWriter.writeCreatureOutfit(
            out,
            5000L,
            Outfit(
                lookType = 128,
                lookHead = 1,
                lookBody = 2,
                lookLegs = 3,
                lookFeet = 4,
                lookAddons = 5,
                lookMount = 7,
                lookMountHead = 8,
                lookMountBody = 9,
                lookMountLegs = 10,
                lookMountFeet = 11,
            ),
        )
        CreatureWindowWriter.writeCreatureOutfit(
            out,
            5001L,
            Outfit(lookType = 128, lookMount = 7),
            supportOutfit = true,
        )
        CreatureWindowWriter.writeCreatureOutfit(out, 5002L, Outfit(lookTypeEx = 99))

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x8E, 0x88, 0x13, 0x00, 0x00,
                0x80, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05,
                0x07, 0x00, 0x08, 0x09, 0x0A, 0x0B,
                0x8E, 0x89, 0x13, 0x00, 0x00,
                0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00,
                0x8E, 0x8A, 0x13, 0x00, 0x00,
                0x00, 0x00, 0x63, 0x00,
                0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes change speed 0x8F with the base speed first`() {
        val out = Unpooled.buffer()

        CreatureWindowWriter.writeChangeSpeed(out, 5000L, baseSpeed = 220, speed = 250)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x8F, 0x88, 0x13, 0x00, 0x00, 0xDC, 0x00, 0xFA, 0x00,
            )
        )
    }

    @Test
    fun `writes creature walkthrough 0x92 with the inverted boolean`() {
        val out = Unpooled.buffer()

        CreatureWindowWriter.writeCreatureWalkthrough(out, 5000L, walkthrough = true)
        CreatureWindowWriter.writeCreatureWalkthrough(out, 5000L, walkthrough = false)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x92, 0x88, 0x13, 0x00, 0x00, 0x00,
                0x92, 0x88, 0x13, 0x00, 0x00, 0x01,
            )
        )
    }

    @Test
    fun `writes creature shield 0x91 and skull 0x90`() {
        val out = Unpooled.buffer()

        CreatureWindowWriter.writeCreatureShield(out, 5000L, partyShield = 2)
        CreatureWindowWriter.writeCreatureSkull(out, 5000L, skull = 3)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x91, 0x88, 0x13, 0x00, 0x00, 0x02,
                0x90, 0x88, 0x13, 0x00, 0x00, 0x03,
            )
        )
    }

    @Test
    fun `writes creature square 0x93 and creature mark 0x93 with their type bytes`() {
        val out = Unpooled.buffer()

        CreatureWindowWriter.writeCreatureSquare(out, 5000L, color = 0)
        CreatureWindowWriter.writeCreatureMark(out, 5000L, mark = 0x4E)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x93, 0x88, 0x13, 0x00, 0x00, 0x01, 0x00,
                0x93, 0x88, 0x13, 0x00, 0x00, 0x02, 0x4E,
            )
        )
    }

    @Test
    fun `writes creature type 0x95 and promotes summon others to a summon player`() {
        val out = Unpooled.buffer()

        CreatureWindowWriter.writeCreatureType(out, 5000L, CreatureType.MONSTER)
        CreatureWindowWriter.writeCreatureType(out, 5001L, CreatureType.SUMMON_OTHERS, masterId = 42L)
        CreatureWindowWriter.writeCreatureType(out, 5002L, CreatureType.SUMMON_PLAYER)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x95, 0x88, 0x13, 0x00, 0x00, 0x01,
                0x95, 0x89, 0x13, 0x00, 0x00, 0x03, 0x2A, 0x00, 0x00, 0x00,
                0x95, 0x8A, 0x13, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes creature icon 0x8B with type 14 and caps the list at three icons`() {
        val out = Unpooled.buffer()

        CreatureWindowWriter.writeCreatureIcon(
            out,
            5000L,
            listOf(
                CreatureIcon(serialize = 1, category = 2, count = 3),
                CreatureIcon(serialize = 5, category = 6, count = 7),
                CreatureIcon(serialize = 8, category = 9, count = 10),
                CreatureIcon(serialize = 11, category = 12, count = 13),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x8B, 0x88, 0x13, 0x00, 0x00, 0x0E,
                0x03,
                0x01, 0x02, 0x03, 0x00,
                0x05, 0x06, 0x07, 0x00,
                0x08, 0x09, 0x0A, 0x00,
            )
        )
    }

    @Test
    fun `writes player icons 0xA2 as a 64-bit bitset plus the bakragore byte`() {
        val out = Unpooled.buffer()

        CreatureWindowWriter.writePlayerIcons(
            out,
            setOf(PlayerIcon.Poison, PlayerIcon.Haste, PlayerIcon.MentorOther),
        )
        CreatureWindowWriter.writePlayerIcons(out, emptySet(), IconBakragore.Taint2)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xA2, 0x41, 0x00, 0x00, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xA2, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02,
            )
        )
    }

    @Test
    fun `writes tibia time 0xEF as minutes and seconds`() {
        val out = Unpooled.buffer()

        CreatureWindowWriter.writeTibiaTime(out, 3900)
        CreatureWindowWriter.writeTibiaTime(out, 65)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xEF, 0x41, 0x00,
                0xEF, 0x01, 0x05,
            )
        )
    }
}
