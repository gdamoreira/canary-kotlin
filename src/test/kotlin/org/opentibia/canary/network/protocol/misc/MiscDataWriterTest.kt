package org.opentibia.canary.network.protocol.misc

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.MonkData

class MiscDataWriterTest {

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
    fun `writes the basic data 0x9F for a free account with no spells`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeBasicData(
            out,
            MiscDataWriter.BasicDataFrame(
                premium = false,
                premiumDays = 0,
                nowSeconds = 0,
                vocationClientId = 4,
                preyWindowUnlocked = true,
                spells = emptyList(),
                magicShield = false,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x9F,
                0x00,
                0x00, 0x00, 0x00, 0x00,
                0x04,
                0x01,
                0x00, 0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes the basic data 0x9F premium expiry as now plus days plus one`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeBasicData(
            out,
            MiscDataWriter.BasicDataFrame(
                premium = true,
                premiumDays = 1,
                nowSeconds = 1000,
                vocationClientId = 1,
                preyWindowUnlocked = false,
                spells = emptyList(),
                magicShield = true,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x9F,
                0x01,
                0xE8, 0xA6, 0x02, 0x00,
                0x01,
                0x00,
                0x00, 0x00,
                0x01,
            )
        )
    }

    @Test
    fun `writes the basic data 0x9F spell list with learnable and wheel filtering`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeBasicData(
            out,
            MiscDataWriter.BasicDataFrame(
                premium = false,
                premiumDays = 0,
                nowSeconds = 0,
                vocationClientId = 2,
                preyWindowUnlocked = false,
                spells = listOf(
                    MiscDataWriter.BasicDataSpell(spellId = 1, learnable = true, learned = false),
                    MiscDataWriter.BasicDataSpell(spellId = 2, learnable = true, learned = true, wheelGrade = 0),
                    MiscDataWriter.BasicDataSpell(spellId = 3, learnable = true, learned = true, wheelGrade = 1),
                    MiscDataWriter.BasicDataSpell(spellId = 4),
                ),
                magicShield = false,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x9F,
                0x00,
                0x00, 0x00, 0x00, 0x00,
                0x02,
                0x00,
                0x04, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x03, 0x00,
                0x04, 0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes the item price list 0xCD with tiered and untiered entries`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeItemsPrice(
            out,
            listOf(
                MiscDataWriter.ItemPriceEntry(itemId = 100, tier = 2, price = 1000),
                MiscDataWriter.ItemPriceEntry(itemId = 200, tier = null, price = 2000),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xCD,
                0x02, 0x00,
                0x64, 0x00,
                0x02,
                0xE8, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xC8, 0x00,
                0xD0, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes the Exiva restrictions 0xCA login payload with whitelist names`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeExivaRestrictions(
            out,
            MiscDataWriter.ExivaRestrictionFlags(
                allowAll = 1,
                allowOwnGuild = 0,
                allowOwnParty = 1,
                allowVipList = 0,
                allowPlayerWhitelist = 1,
                allowGuildWhitelist = 0,
            ),
            isLogin = true,
            playerWhitelistNames = listOf("Alice"),
            guildWhitelistNames = listOf("Guild"),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0xCA, 1, 0, 1, 0, 1, 0),
            *bytes(0x01, 0x00),
            *tibiaString("Alice"),
            *bytes(0x00, 0x00),
            *bytes(0x01, 0x00),
            *tibiaString("Guild"),
            *bytes(0x00, 0x00),
        )
    }

    @Test
    fun `writes the Exiva restrictions 0xCA update payload with added and removed entries`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeExivaRestrictions(
            out,
            MiscDataWriter.ExivaRestrictionFlags(
                allowAll = 1,
                allowOwnGuild = 1,
                allowOwnParty = 0,
                allowVipList = 0,
                allowPlayerWhitelist = 1,
                allowGuildWhitelist = 1,
            ),
            addedPlayerNames = listOf("A"),
            removedPlayerNames = listOf("B", "C"),
            addedGuildNames = listOf("D"),
            removedGuildNames = listOf("E"),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0xCA, 1, 1, 0, 0, 1, 1),
            *bytes(0x01, 0x00),
            *tibiaString("A"),
            *bytes(0x02, 0x00),
            *tibiaString("B"),
            *tibiaString("C"),
            *bytes(0x01, 0x00),
            *tibiaString("D"),
            *bytes(0x01, 0x00),
            *tibiaString("E"),
        )
    }

    @Test
    fun `writes the achievement screenshot 0x75 game event payload`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeTakeScreenshot(
            out,
            MiscDataWriter.ScreenshotType.Achievement,
            achievementName = "Hero",
        )

        assertThat(out.toByteArray()).containsExactly(*bytes(0x75, 0x02), *tibiaString("Hero"))
    }

    @Test
    fun `omits the screenshot 0x75 when the achievement name is empty`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeTakeScreenshot(out, MiscDataWriter.ScreenshotType.Achievement)

        assertThat(out.toByteArray()).isEmpty()
    }

    @Test
    fun `omits the screenshot 0x75 when the type is none`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeTakeScreenshot(out, MiscDataWriter.ScreenshotType.None)

        assertThat(out.toByteArray()).isEmpty()
    }

    @Test
    fun `writes the bestiary screenshot 0x75 with race and step`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeTakeScreenshot(
            out,
            MiscDataWriter.ScreenshotType.BestiaryEntryCompleted,
            raceId = 55,
            bestiaryStep = 3,
        )

        assertThat(out.toByteArray()).containsExactly(*bytes(0x75, 0x06, 0x37, 0x00, 0x03))
    }

    @Test
    fun `omits the bestiary screenshot 0x75 when the race is zero`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeTakeScreenshot(
            out,
            MiscDataWriter.ScreenshotType.BestiaryEntryUnlocked,
            raceId = 0,
        )

        assertThat(out.toByteArray()).isEmpty()
    }

    @Test
    fun `writes the level up screenshot 0x75 with the clamped player level`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeTakeScreenshot(
            out,
            MiscDataWriter.ScreenshotType.LevelUp,
            playerLevel = 500,
        )

        assertThat(out.toByteArray()).containsExactly(*bytes(0x75, 0x04, 0xF4, 0x01))
    }

    @Test
    fun `writes the skill up screenshot 0x75 with the skill id and level`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeTakeScreenshot(
            out,
            MiscDataWriter.ScreenshotType.SkillUp,
            skillId = 1,
            skillLevel = 10,
        )

        assertThat(out.toByteArray()).containsExactly(*bytes(0x75, 0x05, 0x01, 0x0A, 0x00))
    }

    @Test
    fun `writes the boss defeated screenshot 0x75 with the event selector`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeTakeScreenshot(out, MiscDataWriter.ScreenshotType.BossDefeated)

        assertThat(out.toByteArray()).containsExactly(*bytes(0x75, 0x01, 0x01))
    }

    @Test
    fun `writes the gift of life screenshot 0x75 with its selector pair`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeTakeScreenshot(out, MiscDataWriter.ScreenshotType.GiftOfLife)

        assertThat(out.toByteArray()).containsExactly(*bytes(0x75, 0x01, 0x08))
    }

    @Test
    fun `writes the Monk harmony data 0xC1 with the raw value`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeMonkData(out, MonkData.Harmony, value = 7)

        assertThat(out.toByteArray()).containsExactly(*bytes(0xC1, 0x00, 0x07))
    }

    @Test
    fun `writes the Monk serenity data 0xC1 as a normalized boolean`() {
        val truthy = Unpooled.buffer()
        val falsy = Unpooled.buffer()

        MiscDataWriter.writeMonkData(truthy, MonkData.Serenity, value = 5)
        MiscDataWriter.writeMonkData(falsy, MonkData.Serenity, value = 0)

        assertThat(truthy.toByteArray()).containsExactly(*bytes(0xC1, 0x01, 0x01))
        assertThat(falsy.toByteArray()).containsExactly(*bytes(0xC1, 0x01, 0x00))
    }

    @Test
    fun `writes the Monk virtue data 0xC1 with the virtue spell id`() {
        val harmony = Unpooled.buffer()
        val justice = Unpooled.buffer()
        val sustain = Unpooled.buffer()
        val none = Unpooled.buffer()

        MiscDataWriter.writeMonkData(harmony, MonkData.Virtue, value = 1)
        MiscDataWriter.writeMonkData(justice, MonkData.Virtue, value = 2)
        MiscDataWriter.writeMonkData(sustain, MonkData.Virtue, value = 3)
        MiscDataWriter.writeMonkData(none, MonkData.Virtue, value = 0)

        assertThat(harmony.toByteArray()).containsExactly(*bytes(0xC1, 0x02, 0x01, 0x12, 0x01))
        assertThat(justice.toByteArray()).containsExactly(*bytes(0xC1, 0x02, 0x01, 0x13, 0x01))
        assertThat(sustain.toByteArray()).containsExactly(*bytes(0xC1, 0x02, 0x01, 0x14, 0x01))
        assertThat(none.toByteArray()).containsExactly(*bytes(0xC1, 0x02, 0x00))
    }

    @Test
    fun `writes the Monk virtue data 0xC1 with an empty list for an unknown virtue`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeMonkData(out, MonkData.Virtue, value = 9)

        assertThat(out.toByteArray()).containsExactly(*bytes(0xC1, 0x02, 0x00))
    }

    @Test
    fun `writes the supply tracker 0xCE with the item id`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeUpdateSupplyTracker(out, itemId = 1234)

        assertThat(out.toByteArray()).containsExactly(*bytes(0xCE, 0xD2, 0x04))
    }

    @Test
    fun `writes the impact tracker 0xCC healing amount without an element`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeUpdateImpactTracker(out, CombatType.HEALING, amount = 50)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0xCC, 0x00, 0x32, 0x00, 0x00, 0x00)
        )
    }

    @Test
    fun `writes the impact tracker 0xCC dealt damage with the client element`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeUpdateImpactTracker(out, CombatType.FIREDAMAGE, amount = 12)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0xCC, 0x01, 0x0C, 0x00, 0x00, 0x00, 0x01)
        )
    }

    @Test
    fun `omits the impact tracker 0xCC when the element is undefined`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeUpdateImpactTracker(out, CombatType.NONE, amount = 12)

        assertThat(out.toByteArray()).isEmpty()
    }

    @Test
    fun `writes the input analyzer 0xCC received damage with the target string`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeUpdateInputAnalyzer(out, CombatType.FIREDAMAGE, amount = 10, target = "Rat")

        assertThat(out.toByteArray()).containsExactly(
            *bytes(0xCC, 0x02, 0x0A, 0x00, 0x00, 0x00, 0x01),
            *tibiaString("Rat"),
        )
    }

    @Test
    fun `omits the input analyzer 0xCC when the element is undefined`() {
        val out = Unpooled.buffer()

        MiscDataWriter.writeUpdateInputAnalyzer(out, CombatType.NONE, amount = 10, target = "Rat")

        assertThat(out.toByteArray()).isEmpty()
    }
}
