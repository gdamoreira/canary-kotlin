package org.opentibia.canary.network.protocol.podium

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.podium.PodiumWindowWriter.MonsterPodiumEntry
import org.opentibia.canary.network.protocol.podium.PodiumWindowWriter.PodiumLook
import org.opentibia.canary.network.protocol.podium.PodiumWindowWriter.PodiumMountEntry
import org.opentibia.canary.network.protocol.podium.PodiumWindowWriter.PodiumOutfitEntry
import org.opentibia.canary.network.protocol.podium.PodiumWindowWriter.PodiumWindowFrame

class PodiumWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `writes podium window 0xC8 with outfit mount outfit and mount lists`() {
        val out = Unpooled.buffer()

        PodiumWindowWriter.writePodiumWindow(
            out,
            PodiumWindowFrame(
                position = Position(100, 200, 7),
                itemId = 3900,
                stackpos = 3,
                lookType = PodiumLook(value = 128, head = 1, body = 2, legs = 3, feet = 4, addons = 5),
                outfits = listOf(PodiumOutfitEntry(lookType = 128, name = "Citizen", addons = 3)),
                mounts = listOf(PodiumMountEntry(clientId = 10, name = "Horse")),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC8,
                0x80, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, // outfit
                0x00, 0x00, // no mount attribute
                0x00, 0x00, 0x00, 0x00, // no mount look
                0x00, 0x00, // unknown uint16 list
                0x01, 0x00, // outfit count
                0x80, 0x00, 0x07, 0x00, 0x43, 0x69, 0x74, 0x69, 0x7A, 0x65, 0x6E, 0x03, 0x00,
                0x01, 0x00, // mount count
                0x0A, 0x00, 0x05, 0x00, 0x48, 0x6F, 0x72, 0x73, 0x65, 0x00,
                0x00, 0x00, // unknown uint16 list
                0x05, // window type
                0x00, // no mount selected
                0x00, 0x00, // unknown uint16
                0x64, 0x00, 0xC8, 0x00, 0x07, // position
                0x3C, 0x0F, // item id 3900
                0x03, // stack position
                0x01, // visible
                0x01, // outfit selected
                0x02, // default direction
            )
        )
    }

    @Test
    fun `writes podium window 0xC8 with a selected mount and no outfit attribute`() {
        val out = Unpooled.buffer()

        PodiumWindowWriter.writePodiumWindow(
            out,
            PodiumWindowFrame(
                position = Position(50, 60, 7),
                itemId = 3901,
                stackpos = 0,
                lookMount = PodiumLook(value = 0),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC8,
                0x00, 0x00, // no outfit attribute
                0x00, 0x00, // mount attribute present, value 0
                0x00, 0x00, // unknown uint16 list
                0x00, 0x00, // no outfits
                0x00, 0x00, // no mounts
                0x00, 0x00, // unknown uint16 list
                0x05,
                0x01, // mount selected
                0x00, 0x00,
                0x32, 0x00, 0x3C, 0x00, 0x07,
                0x3D, 0x0F, // item id 3901
                0x00,
                0x01,
                0x00, // no outfit attribute
                0x02,
            )
        )
    }

    @Test
    fun `writes podium details count including unresolved monsters`() {
        val out = Unpooled.buffer()

        PodiumWindowWriter.writePodiumDetails(
            out,
            monsters = listOf(
                MonsterPodiumEntry(raceId = 1, name = "Rat", lookType = 0, lookTypeEx = 21),
                null,
                MonsterPodiumEntry(raceId = 3, name = "Demon", lookType = 35, lookTypeEx = 0),
            ),
            isBoss = false,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x03, 0x00, // count includes the unresolved monster
                0x01, 0x00,
                0x03, 0x00,
            )
        )
    }

    @Test
    fun `writes boss podium details with outfit bytes and the Tentugly override`() {
        val out = Unpooled.buffer()

        PodiumWindowWriter.writePodiumDetails(
            out,
            monsters = listOf(
                MonsterPodiumEntry(raceId = 10, name = "Demon", lookType = 35, lookTypeEx = 0, head = 1, body = 2, legs = 3, feet = 4, addons = 5),
                MonsterPodiumEntry(raceId = 11, name = "Other", lookType = 0, lookTypeEx = 99),
                MonsterPodiumEntry(raceId = 12, name = "Boss", lookType = 0, lookTypeEx = 35105),
            ),
            isBoss = true,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x03, 0x00,
                0x0A, 0x00, 0x05, 0x00, 0x44, 0x65, 0x6D, 0x6F, 0x6E, 0x23, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05,
                0x0B, 0x00, 0x05, 0x00, 0x4F, 0x74, 0x68, 0x65, 0x72, 0x00, 0x00, 0x63, 0x00,
                0x0C, 0x00, 0x08, 0x00, 0x54, 0x65, 0x6E, 0x74, 0x75, 0x67, 0x6C, 0x79, 0x00, 0x00, 0x5B, 0x98,
            )
        )
    }

    @Test
    fun `writes monster podium window 0xC2 for a boss podium`() {
        val out = Unpooled.buffer()

        PodiumWindowWriter.writeMonsterPodiumWindow(
            out,
            PodiumWindowWriter.MonsterPodiumWindowFrame(
                position = Position(100, 200, 7),
                itemId = 3902,
                stackPos = 1,
                isBossPodium = true,
                lookType = 10,
                lookHead = 1,
                lookBody = 2,
                lookLegs = 3,
                lookFeet = 4,
                lookAddons = 5,
                monsters = listOf(MonsterPodiumEntry(raceId = 10, name = "Demon", lookType = 35, lookTypeEx = 0)),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC2,
                0x0A, 0x00, // boss look type
                0x01, 0x02, 0x03, 0x04, 0x05,
                0x00, 0x00, // unknown list size
                0x01, // bosstiary podium
                0x01, 0x00, // monster count
                0x0A, 0x00, 0x05, 0x00, 0x44, 0x65, 0x6D, 0x6F, 0x6E, 0x23, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x64, 0x00, 0xC8, 0x00, 0x07,
                0x3E, 0x0F, // item id 3902
                0x01, // stack position
                0x01, // visible
                0x01, // look type present
                0x02, // direction
            )
        )
    }

    @Test
    fun `writes monster podium window 0xC2 for a bestiary podium without a selected boss`() {
        val out = Unpooled.buffer()

        PodiumWindowWriter.writeMonsterPodiumWindow(
            out,
            PodiumWindowWriter.MonsterPodiumWindowFrame(
                position = Position(1, 2, 7),
                itemId = 3903,
                stackPos = 0,
                isBossPodium = false,
                monsters = listOf(MonsterPodiumEntry(raceId = 7, name = "Rat", lookType = 0, lookTypeEx = 21)),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC2,
                0x00, 0x00, // no boss selected
                0x00, 0x00, // no boss look type
                0x00, 0x00,
                0x00, // bestiary podium
                0x01, 0x00,
                0x07, 0x00,
                0x01, 0x00, 0x02, 0x00, 0x07,
                0x3F, 0x0F,
                0x00,
                0x01,
                0x00, // no look type attribute
                0x02,
            )
        )
    }
}
