package org.opentibia.canary.network.protocol.wheel

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.systems.wheel.PlayerWheel
import org.opentibia.canary.systems.wheel.PlayerWheelGem
import org.opentibia.canary.systems.wheel.WheelGemAffinity
import org.opentibia.canary.systems.wheel.WheelGemBasicModifier
import org.opentibia.canary.systems.wheel.WheelGemQuality
import org.opentibia.canary.systems.wheel.WheelGemSupremeModifier
import org.opentibia.canary.systems.wheel.WheelSlots

class WheelWindowSerializerTest {

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

    private fun ByteBuf.writeSlots(points: IntArray = IntArray(SLOT_COUNT)) {
        for (point in points) {
            writeShortLE(point)
        }
    }

    /**
     * The two fixed grade-modifier sections written by
     * `PlayerWheel::addGradeModifiers` (player_wheel.cpp:1423): the `0x2E`
     * basic block followed by the per-vocation `0x17` supreme block.
     */
    private fun ByteBuf.writeGradeSection(
        supremeIds: IntArray,
        basicGrades: Map<Int, Int> = emptyMap(),
        supremeGrades: Map<Int, Int> = emptyMap(),
    ) {
        writeByte(0x2E)
        for (id in BASIC_MODIFIER_IDS) {
            writeByte(id)
            writeByte(basicGrades[id] ?: 0)
        }
        writeByte(0x17)
        for (id in supremeIds) {
            writeByte(id)
            writeByte(supremeGrades[id] ?: 0)
        }
    }

    private fun knight(): PlayerWheel = PlayerWheel(vocationCipId = 1, level = 100, pointsPerLevel = 1)

    @Test
    fun `stops after the owner id and cannot use byte for a locked wheel`() {
        val out = Unpooled.buffer()

        WheelWindowSerializer.writeOpenWheelWindow(
            out,
            PlayerWheel(vocationCipId = 1, level = 100, pointsPerLevel = 1),
            WheelWindow(ownerId = 0x01020304L, canUse = false, options = 2),
        )

        assertThat(out.toByteArray()).containsExactly(*bytes(0x5F, 0x04, 0x03, 0x02, 0x01, 0x00))
    }

    @Test
    fun `serializes header slot points and grade sections from a knight wheel`() {
        val wheel = knight()
        wheel.setPointsBySlotType(WheelSlots.SLOT_GREEN_200, 25)
        wheel.setPointsBySlotType(WheelSlots.SLOT_PURPLE_200, 1)
        wheel.gems.basicGrades[0] = 1
        wheel.gems.basicGrades[48] = 3
        wheel.gems.supremeGrades[0] = 2
        wheel.gems.supremeGrades[23] = 1
        val out = Unpooled.buffer()

        WheelWindowSerializer.writeOpenWheelWindow(
            out,
            wheel,
            WheelWindow(ownerId = 7, canUse = true, options = 2, officialSkillWheelPayload = true),
        )

        val slotPoints = IntArray(SLOT_COUNT)
        slotPoints[0] = 25
        slotPoints[35] = 1
        assertThat(out.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x5F)
                writeIntLE(7)
                writeByte(0x01) // can use
                writeByte(0x02) // options
                writeByte(0x01) // vocation CIP knight
                writeShortLE(50) // base points, extra points excluded
                writeShortLE(0) // extra points
                writeSlots(slotPoints)
                writeShortLE(0) // promotion scrolls
                writeByte(0x00) // official monk quest flag
                writeShortLE(0) // official monk quest bonus
                writeByte(0x00) // active gems
                writeShortLE(0) // revealed gems
                writeGradeSection(
                    KNIGHT_SUPREME_IDS,
                    basicGrades = mapOf(0 to 1, 48 to 3),
                    supremeGrades = mapOf(0 to 2, 23 to 1),
                )
            }
        )
    }

    @Test
    fun `writes base and extra points as separate little endian values`() {
        val wheel = PlayerWheel(vocationCipId = 1, level = 60, extraPoints = 9, pointsPerLevel = 1)
        val out = Unpooled.buffer()

        WheelWindowSerializer.writeOpenWheelWindow(
            out,
            wheel,
            WheelWindow(ownerId = 1, canUse = true, options = 0, officialSkillWheelPayload = true),
        )

        assertThat(out.toByteArray().copyOfRange(6, 12)).containsExactly(
            *expectedBytes {
                writeByte(0x00) // options
                writeByte(0x01) // vocation CIP knight
                writeShortLE(10) // base points
                writeShortLE(9) // extra points
            }
        )
    }

    @Test
    fun `withholds extra points below the canary level gate`() {
        val wheel = PlayerWheel(vocationCipId = 1, level = 50, extraPoints = 9, pointsPerLevel = 1)
        val out = Unpooled.buffer()

        WheelWindowSerializer.writeOpenWheelWindow(
            out,
            wheel,
            WheelWindow(ownerId = 1, canUse = true, options = 0, officialSkillWheelPayload = true),
        )

        assertThat(out.toByteArray().copyOfRange(6, 12)).containsExactly(
            *expectedBytes {
                writeByte(0x00) // options
                writeByte(0x01) // vocation CIP knight
                writeShortLE(0) // base points
                writeShortLE(0) // extra points withheld below level 51
            }
        )
    }

    @Test
    fun `serializes active and revealed gems with quality dependent modifier bytes`() {
        val wheel = knight()
        val lesser = PlayerWheelGem(
            locked = false,
            affinity = WheelGemAffinity.Green,
            quality = WheelGemQuality.Lesser,
            basicModifier1 = WheelGemBasicModifier.General_FireResistance,
        )
        val regular = PlayerWheelGem(
            locked = true,
            affinity = WheelGemAffinity.Red,
            quality = WheelGemQuality.Regular,
            basicModifier1 = WheelGemBasicModifier.General_IceResistance,
            basicModifier2 = WheelGemBasicModifier.Vocation_Health,
        )
        val greater = PlayerWheelGem(
            locked = false,
            affinity = WheelGemAffinity.Blue,
            quality = WheelGemQuality.Greater,
            basicModifier1 = WheelGemBasicModifier.General_EnergyResistance,
            basicModifier2 = WheelGemBasicModifier.General_MitigationMultiplier,
            supremeModifier = WheelGemSupremeModifier.General_Dodge,
        )
        wheel.gems.revealedGems += listOf(lesser, regular, greater)
        wheel.gems.activeGems[WheelGemAffinity.Green.value] = lesser
        wheel.gems.activeGems[WheelGemAffinity.Red.value] = regular
        wheel.gems.activeGems[WheelGemAffinity.Blue.value] = greater
        val out = Unpooled.buffer()

        WheelWindowSerializer.writeOpenWheelWindow(
            out,
            wheel,
            WheelWindow(ownerId = 7, canUse = true, options = 0, officialSkillWheelPayload = true),
        )

        assertThat(out.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x5F)
                writeIntLE(7)
                writeByte(0x01)
                writeByte(0x00)
                writeByte(0x01)
                writeShortLE(50)
                writeShortLE(0)
                writeSlots()
                writeShortLE(0)
                writeByte(0x00)
                writeShortLE(0)
                writeByte(0x03) // three active gems, Green/Red/Blue order
                writeShortLE(0)
                writeShortLE(1)
                writeShortLE(2)
                writeShortLE(3) // three revealed gems
                writeShortLE(0)
                writeByte(0x00) // locked
                writeByte(0x00) // affinity green
                writeByte(0x00) // quality lesser
                writeByte(0x03) // General_FireResistance
                writeShortLE(1)
                writeByte(0x01) // locked
                writeByte(0x01) // affinity red
                writeByte(0x01) // quality regular
                writeByte(0x05) // General_IceResistance
                writeByte(0x1F) // Vocation_Health
                writeShortLE(2)
                writeByte(0x00) // unlocked
                writeByte(0x02) // affinity blue
                writeByte(0x02) // quality greater
                writeByte(0x06) // General_EnergyResistance
                writeByte(0x1E) // General_MitigationMultiplier
                writeByte(0x00) // General_Dodge
                writeGradeSection(KNIGHT_SUPREME_IDS)
            }
        )
    }

    @Test
    fun `defaults unset optional gem modifiers to the zero enum values`() {
        val wheel = knight()
        wheel.gems.revealedGems += PlayerWheelGem(
            affinity = WheelGemAffinity.Red,
            quality = WheelGemQuality.Regular,
            basicModifier1 = WheelGemBasicModifier.General_PhysicalResistance,
        )
        wheel.gems.revealedGems += PlayerWheelGem(
            affinity = WheelGemAffinity.Blue,
            quality = WheelGemQuality.Greater,
            basicModifier1 = WheelGemBasicModifier.General_PhysicalResistance,
        )
        val out = Unpooled.buffer()

        WheelWindowSerializer.writeOpenWheelWindow(
            out,
            wheel,
            WheelWindow(ownerId = 7, canUse = true, options = 0, officialSkillWheelPayload = true),
        )

        assertThat(out.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x5F)
                writeIntLE(7)
                writeByte(0x01)
                writeByte(0x00)
                writeByte(0x01)
                writeShortLE(50)
                writeShortLE(0)
                writeSlots()
                writeShortLE(0)
                writeByte(0x00)
                writeShortLE(0)
                writeByte(0x00) // no active gems
                writeShortLE(2) // two revealed gems
                writeShortLE(0)
                writeByte(0x00)
                writeByte(0x01) // affinity red
                writeByte(0x01) // quality regular
                writeByte(0x00) // General_PhysicalResistance
                writeByte(0x00) // unset basic modifier 2 defaults to the zero enum
                writeShortLE(1)
                writeByte(0x00)
                writeByte(0x02) // affinity blue
                writeByte(0x02) // quality greater
                writeByte(0x00)
                writeByte(0x00) // unset basic modifier 2
                writeByte(0x00) // unset supreme modifier defaults to zero
                writeGradeSection(KNIGHT_SUPREME_IDS)
            }
        )
    }

    @Test
    fun `writes the not found index for an active gem missing from the revealed list`() {
        val wheel = knight()
        wheel.gems.activeGems[WheelGemAffinity.Purple.value] = PlayerWheelGem(
            affinity = WheelGemAffinity.Purple,
            quality = WheelGemQuality.Lesser,
            basicModifier1 = WheelGemBasicModifier.General_PhysicalResistance,
        )
        val out = Unpooled.buffer()

        WheelWindowSerializer.writeOpenWheelWindow(
            out,
            wheel,
            WheelWindow(ownerId = 7, canUse = true, options = 0, officialSkillWheelPayload = true),
        )

        assertThat(out.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x5F)
                writeIntLE(7)
                writeByte(0x01)
                writeByte(0x00)
                writeByte(0x01)
                writeShortLE(50)
                writeShortLE(0)
                writeSlots()
                writeShortLE(0)
                writeByte(0x00)
                writeShortLE(0)
                writeByte(0x01) // one active gem
                writeShortLE(0xFF) // uuid lookup failure index
                writeShortLE(0) // no revealed gems
                writeGradeSection(KNIGHT_SUPREME_IDS)
            }
        )
    }

    @Test
    fun `serializes promotion scrolls and the official monk quest bonus pair`() {
        val out = Unpooled.buffer()

        WheelWindowSerializer.writeOpenWheelWindow(
            out,
            knight(),
            WheelWindow(
                ownerId = 0x01020304L,
                canUse = true,
                options = 1,
                promotionScrolls = listOf(
                    PromotionScroll(itemId = 43946, name = "abridged", extraPoints = 3),
                    PromotionScroll(itemId = 43950, name = "advanced", extraPoints = 20),
                ),
                completedMonkQuest = true,
                monkQuestBonus = 300,
                officialSkillWheelPayload = true,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x5F)
                writeIntLE(0x01020304)
                writeByte(0x01)
                writeByte(0x01)
                writeByte(0x01)
                writeShortLE(50)
                writeShortLE(0)
                writeSlots()
                writeShortLE(2) // two promotion scrolls
                writeShortLE(43946)
                writeByte(3)
                writeShortLE(43950)
                writeByte(20)
                writeByte(0x01) // completed monk quest flag
                writeShortLE(300)
                writeByte(0x00)
                writeShortLE(0)
                writeGradeSection(KNIGHT_SUPREME_IDS)
            }
        )
    }

    @Test
    fun `serializes the legacy monk quest bonus byte and clamps it to one byte`() {
        val window = WheelWindow(
            ownerId = 1,
            canUse = true,
            options = 0,
            completedMonkQuest = true,
            monkQuestBonus = 300,
            officialSkillWheelPayload = false,
        )
        val out = Unpooled.buffer()

        WheelWindowSerializer.writeOpenWheelWindow(out, knight(), window)

        assertThat(out.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x5F)
                writeIntLE(1)
                writeByte(0x01)
                writeByte(0x00)
                writeByte(0x01)
                writeShortLE(50)
                writeShortLE(0)
                writeSlots()
                writeShortLE(0)
                writeByte(0xFF) // clamps the 300 bonus into the legacy byte
                writeByte(0x00)
                writeShortLE(0)
                writeGradeSection(KNIGHT_SUPREME_IDS)
            }
        )
    }

    @Test
    fun `omits the monk quest bonus when the quest is incomplete`() {
        val out = Unpooled.buffer()

        WheelWindowSerializer.writeOpenWheelWindow(
            out,
            knight(),
            WheelWindow(
                ownerId = 1,
                canUse = true,
                options = 0,
                completedMonkQuest = false,
                monkQuestBonus = 7,
                officialSkillWheelPayload = true,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *expectedBytes {
                writeByte(0x5F)
                writeIntLE(1)
                writeByte(0x01)
                writeByte(0x00)
                writeByte(0x01)
                writeShortLE(50)
                writeShortLE(0)
                writeSlots()
                writeShortLE(0)
                writeByte(0x00) // quest incomplete
                writeShortLE(0)
                writeByte(0x00)
                writeShortLE(0)
                writeGradeSection(KNIGHT_SUPREME_IDS)
            }
        )
    }

    @Test
    fun `maps each cip vocation to its base vocation supreme grade table`() {
        val supremeByVocationCip = linkedMapOf(
            1 to KNIGHT_SUPREME_IDS,
            2 to PALADIN_SUPREME_IDS,
            3 to SORCERER_SUPREME_IDS,
            4 to DRUID_SUPREME_IDS,
            5 to MONK_SUPREME_IDS,
        )

        for ((vocationCipId, supremeIds) in supremeByVocationCip) {
            val wheel = PlayerWheel(vocationCipId = vocationCipId, level = 100, pointsPerLevel = 1)
            val out = Unpooled.buffer()

            WheelWindowSerializer.writeOpenWheelWindow(
                out,
                wheel,
                WheelWindow(ownerId = 1, canUse = true, options = 0, officialSkillWheelPayload = true),
            )

            assertThat(out.toByteArray())
                .describedAs("vocation CIP id %d", vocationCipId)
                .containsExactly(
                    *expectedBytes {
                        writeByte(0x5F)
                        writeIntLE(1)
                        writeByte(0x01)
                        writeByte(0x00)
                        writeByte(vocationCipId)
                        writeShortLE(50)
                        writeShortLE(0)
                        writeSlots()
                        writeShortLE(0)
                        writeByte(0x00)
                        writeShortLE(0)
                        writeByte(0x00)
                        writeShortLE(0)
                        writeGradeSection(supremeIds)
                    }
                )
        }
    }

    @Test
    fun `writes the wheel window through an embedded channel`() {
        val channel = EmbeddedChannel()
        try {
            val out = Unpooled.buffer()
            WheelWindowSerializer.writeOpenWheelWindow(
                out,
                knight(),
                WheelWindow(ownerId = 1, canUse = false, options = 0),
            )

            assertThat(channel.writeOutbound(out)).isTrue()
            val frame = channel.readOutbound<ByteBuf>()
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0x5F)
        } finally {
            channel.finishAndReleaseAll()
        }
    }

    private companion object {
        const val SLOT_COUNT = 36

        /** `modsBasicPosition` modifier values in declaration order (player_wheel.cpp:89). */
        val BASIC_MODIFIER_IDS = intArrayOf(
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
            24, 25, 26, 27, 28, 29, 30, 31, 33, 34, 35, 36, 37, 38, 39, 40, 41, 44, 45, 46, 47, 48,
        )

        // Canary's Knight list keeps the CriticalExtraDamage variants before
        // DamageIncrease (player_wheel.cpp:127-147), so values 14/15 and 18/19
        // are deliberately not ascending.
        val KNIGHT_SUPREME_IDS = intArrayOf(
            0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15, 14, 16, 17, 19, 18, 20, 21, 22, 23,
        )
        val PALADIN_SUPREME_IDS = intArrayOf(
            0, 1, 2, 3, 5, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41,
        )
        val SORCERER_SUPREME_IDS = intArrayOf(
            0, 1, 2, 3, 4, 5, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58,
        )
        // Canary's Druid list keeps EternalWinter CriticalExtraDamage (66)
        // before DamageIncrease (65) (player_wheel.cpp:164-181).
        val DRUID_SUPREME_IDS = intArrayOf(
            0, 1, 2, 3, 4, 5, 59, 60, 61, 62, 63, 64, 66, 65, 67, 68, 69, 70, 71, 72, 73, 74, 75,
        )
        val MONK_SUPREME_IDS = intArrayOf(
            0, 1, 2, 3, 5, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93,
        )
    }
}
