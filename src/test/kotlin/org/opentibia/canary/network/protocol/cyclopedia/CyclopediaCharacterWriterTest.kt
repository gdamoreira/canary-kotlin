package org.opentibia.canary.network.protocol.cyclopedia

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.combat.condition.Skill
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.creatures.players.components.RecentDeathEntry
import org.opentibia.canary.creatures.players.components.RecentPvPKillEntry
import org.opentibia.canary.items.Abilities
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.SLOTP_ARMOR
import org.opentibia.canary.items.SLOTP_FEET
import org.opentibia.canary.items.SLOTP_HEAD
import org.opentibia.canary.items.SLOTP_LEFT
import org.opentibia.canary.items.SLOTP_LEGS
import org.opentibia.canary.systems.imbuement.Imbuement
import org.opentibia.canary.systems.imbuement.ImbuementInfo
import org.opentibia.canary.systems.proficiency.WeaponProficiencyCriticalBonus
import org.opentibia.canary.systems.wheel.WheelStat

class CyclopediaCharacterWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val total = parts.sumOf { it.size }
        val result = ByteArray(total)
        var offset = 0
        for (part in parts) {
            part.copyInto(result, offset)
            offset += part.size
        }
        return result
    }

    /** `NetworkMessage::addDouble(0.0)` at precision 4 (`INT32_MAX` + 0). */
    private val zeroDouble: ByteArray get() = bytes(0x04, 0xFF, 0xFF, 0xFF, 0x7F)

    /** `NetworkMessage::addDouble` at precision 4 for an exact scaled value. */
    private fun doubleBytes(scaled: Int): ByteArray {
        val value = scaled + Int.MAX_VALUE
        return bytes(0x04, value and 0xFF, (value shr 8) and 0xFF, (value shr 16) and 0xFF, (value shr 24) and 0xFF)
    }

    @Test
    fun `writes recent deaths 0xDA with page and timestamp entries`() {
        val out = Unpooled.buffer()

        CyclopediaCharacterWriter.writeRecentDeaths(
            out,
            page = 1,
            pages = 2,
            entries = listOf(RecentDeathEntry(cause = "Died", timestamp = 100)),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xDA, 0x03, 0x00,
                0x01, 0x00,
                0x02, 0x00,
                0x01, 0x00,
                0x64, 0x00, 0x00, 0x00,
                0x04, 0x00, 0x44, 0x69, 0x65, 0x64,
            )
        )
    }

    @Test
    fun `writes recent pvp kills 0xDA with the status byte per entry`() {
        val out = Unpooled.buffer()

        CyclopediaCharacterWriter.writeRecentPvPKills(
            out,
            page = 1,
            pages = 2,
            entries = listOf(RecentPvPKillEntry(description = "Killed X.", timestamp = 200, status = 1)),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xDA, 0x04, 0x00,
                0x01, 0x00,
                0x02, 0x00,
                0x01, 0x00,
                0xC8, 0x00, 0x00, 0x00,
                0x09, 0x00, 0x4B, 0x69, 0x6C, 0x6C, 0x65, 0x64, 0x20, 0x58, 0x2E,
                0x01,
            )
        )
    }

    @Test
    fun `writes achievements 0xDA with secret and non secret entries`() {
        val out = Unpooled.buffer()

        CyclopediaCharacterWriter.writeAchievements(
            out,
            points = 10,
            secretsUnlocked = 1,
            entries = listOf(
                CyclopediaCharacterWriter.AchievementEntry(
                    id = 5,
                    timestamp = 100L,
                    secret = true,
                    name = "Secret",
                    description = "Desc",
                    grade = 2,
                ),
                CyclopediaCharacterWriter.AchievementEntry(id = 6, timestamp = 200L, secret = false),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xDA, 0x05, 0x00,
                0x0A, 0x00,
                0x01, 0x00,
                0x02, 0x00,
                0x05, 0x00,
                0x64, 0x00, 0x00, 0x00,
                0x01,
                0x06, 0x00, 0x53, 0x65, 0x63, 0x72, 0x65, 0x74,
                0x04, 0x00, 0x44, 0x65, 0x73, 0x63,
                0x02,
                0x06, 0x00,
                0xC8, 0x00, 0x00, 0x00,
                0x00,
            )
        )
    }

    @Test
    fun `writes titles 0xDA with the current title and unlock flags`() {
        val out = Unpooled.buffer()

        CyclopediaCharacterWriter.writeTitles(
            out,
            currentTitle = 1,
            titles = listOf(
                CyclopediaCharacterWriter.TitleEntry(
                    id = 1,
                    name = "Gold",
                    description = "Desc",
                    permanent = true,
                    unlocked = true,
                ),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xDA, 0x0B, 0x00,
                0x01,
                0x01,
                0x01,
                0x04, 0x00, 0x47, 0x6F, 0x6C, 0x64,
                0x04, 0x00, 0x44, 0x65, 0x73, 0x63,
                0x01,
                0x01,
            )
        )
    }

    @Test
    fun `writes badges 0xDA with account information and badge rows`() {
        val out = Unpooled.buffer()

        CyclopediaCharacterWriter.writeBadges(
            out,
            isOnline = true,
            isPremium = false,
            loyaltyTitle = "",
            badges = listOf(CyclopediaCharacterWriter.BadgeEntry(id = 1L, name = "Age")),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xDA, 0x0A, 0x00,
                0x01,
                0x01,
                0x00,
                0x00, 0x00,
                0x01,
                0x01, 0x00, 0x00, 0x00,
                0x03, 0x00, 0x41, 0x67, 0x65,
            )
        )
    }

    @Test
    fun `writes the item summary 0xDA with tier bytes and stash entries`() {
        val out = Unpooled.buffer()

        CyclopediaCharacterWriter.writeItemSummary(
            out,
            inventoryItems = listOf(CyclopediaCharacterWriter.ItemSummaryEntry(itemId = 100, tier = null, count = 5L)),
            storeInboxItems = listOf(CyclopediaCharacterWriter.ItemSummaryEntry(itemId = 200, tier = 3, count = 2L)),
            stashItems = listOf(CyclopediaCharacterWriter.ItemSummaryEntry(itemId = 300, tier = 0, count = 7L)),
            depotBoxItems = emptyList(),
            inboxItems = emptyList(),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xDA, 0x06, 0x00,
                0x01, 0x00,
                0x64, 0x00, 0x05, 0x00, 0x00, 0x00,
                0x01, 0x00,
                0xC8, 0x00, 0x03, 0x02, 0x00, 0x00, 0x00,
                0x01, 0x00,
                0x2C, 0x01, 0x00, 0x07, 0x00, 0x00, 0x00,
                0x00, 0x00,
                0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes outfits mounts and familiars 0xDA with current colors`() {
        val out = Unpooled.buffer()

        CyclopediaCharacterWriter.writeOutfitsMounts(
            out,
            CyclopediaCharacterWriter.OutfitsMountsFrame(
                outfits = listOf(
                    CyclopediaCharacterWriter.OutfitEntry(
                        lookType = 136,
                        name = "Citizen",
                        addons = 3,
                        type = 1,
                        active = true,
                    ),
                ),
                currentOutfit = Outfit(lookType = 136, lookHead = 1, lookBody = 2, lookLegs = 3, lookFeet = 4),
                mounts = listOf(CyclopediaCharacterWriter.MountEntry(clientId = 10, name = "Horse", type = 2)),
                currentMountOutfit = Outfit(
                    lookMountHead = 5,
                    lookMountBody = 6,
                    lookMountLegs = 7,
                    lookMountFeet = 8,
                ),
                familiars = listOf(CyclopediaCharacterWriter.FamiliarEntry(lookType = 20, name = "Fox", type = 1)),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xDA, 0x07, 0x00,
                0x01, 0x00,
                0x88, 0x00, 0x07, 0x00, 0x43, 0x69, 0x74, 0x69, 0x7A, 0x65, 0x6E, 0x03, 0x01,
                0xE8, 0x03, 0x00, 0x00,
                0x01, 0x02, 0x03, 0x04,
                0x01, 0x00,
                0x0A, 0x00, 0x05, 0x00, 0x48, 0x6F, 0x72, 0x73, 0x65, 0x02,
                0xE8, 0x03, 0x00, 0x00,
                0x05, 0x06, 0x07, 0x08,
                0x01, 0x00,
                0x14, 0x00, 0x03, 0x00, 0x46, 0x6F, 0x78, 0x01,
                0x00, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes the store summary 0xDA with blessings skills and house items`() {
        val out = Unpooled.buffer()

        CyclopediaCharacterWriter.writeStoreSummary(
            out,
            CyclopediaCharacterWriter.StoreSummaryFrame(
                xpBoostTime = 100L,
                dailyRewardXpBoost = 200L,
                blessings = listOf("A" to 0, "B" to 1),
                preySlotsUnlocked = 1,
                preyWildcards = 2,
                weeklyExpansionUnlocked = false,
                instantRewards = 3,
                charmExpansion = true,
                hirelings = 4,
                hirelingSkills = listOf(1000),
                houseItems = listOf(CyclopediaCharacterWriter.HouseItemEntry(itemId = 100, name = "Box", count = 2)),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xDA, 0x08, 0x00,
                0x64, 0x00, 0x00, 0x00,
                0xC8, 0x00, 0x00, 0x00,
                0x02,
                0x01, 0x00, 0x41, 0x00,
                0x01, 0x00, 0x42, 0x01,
                0x01,
                0x02,
                0x00,
                0x03,
                0x01,
                0x04,
                0x01,
                0x00,
                0x00,
                0x01, 0x00,
                0x64, 0x00, 0x03, 0x00, 0x42, 0x6F, 0x78, 0x02,
            )
        )
    }

    @Test
    fun `writes the cyclopedia inspection 0xDA with inventory and player descriptions`() {
        val out = Unpooled.buffer()

        CyclopediaCharacterWriter.writeInspection(
            out,
            CyclopediaCharacterWriter.InspectionFrame(
                inventory = listOf(
                    CyclopediaCharacterWriter.InspectionItemEntry(
                        slot = 1,
                        name = "Sword",
                        item = Item(100),
                        imbuementIconIds = listOf(11),
                        descriptions = listOf("Attack" to "15"),
                    ),
                ),
                playerName = "Hero",
                defaultOutfit = Outfit(lookType = 136, lookHead = 1, lookBody = 2, lookLegs = 3, lookFeet = 4),
                playerDescriptions = listOf("Level" to "8", "Vocation" to "Knight"),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xDA, 0x09, 0x00,
                0x01,
                0x01,
                0x05, 0x00, 0x53, 0x77, 0x6F, 0x72, 0x64,
                0x64, 0x00,
                0x01,
                0x0B, 0x00,
                0x01,
                0x06, 0x00, 0x41, 0x74, 0x74, 0x61, 0x63, 0x6B,
                0x02, 0x00, 0x31, 0x35,
                0x04, 0x00, 0x48, 0x65, 0x72, 0x6F,
                0x88, 0x00, 0x01, 0x02, 0x03, 0x04, 0x00,
                0x02,
                0x05, 0x00, 0x4C, 0x65, 0x76, 0x65, 0x6C,
                0x01, 0x00, 0x38,
                0x08, 0x00, 0x56, 0x6F, 0x63, 0x61, 0x74, 0x69, 0x6F, 0x6E,
                0x06, 0x00, 0x4B, 0x6E, 0x69, 0x67, 0x68, 0x74,
            )
        )
    }

    @Test
    fun `writes general stats 0xDA with seven skills and specialized magic levels`() {
        val out = Unpooled.buffer()

        CyclopediaCharacterWriter.writeGeneralStats(
            out,
            CyclopediaCharacterWriter.GeneralStatsFrame(
                experience = 1000L,
                level = 8,
                levelPercent = 0.5,
                baseXpGain = 1,
                displayGrindingXpBoost = 2,
                displayXpBoostPercent = 3,
                staminaXpBoost = 4,
                xpBoostTime = 3600,
                health = 100,
                maxHealth = 200,
                mana = 30,
                maxMana = 40,
                soul = 5,
                staminaMinutes = 42,
                regenerationTicks = 5000L,
                offlineTrainingTime = 60000L,
                speed = 220,
                baseSpeed = 110,
                capacity = 400,
                baseCapacity = 500,
                freeCapacity = 350,
                magicLevel = 6,
                baseMagicLevel = 7,
                loyaltyMagicLevel = 8,
                magicLevelPercent = 0.25,
                skills = listOf(11, 9, 8, 10, 7, 6, 13).map {
                    CyclopediaCharacterWriter.GeneralSkillEntry(
                        clientId = it,
                        level = 1,
                        base = 2,
                        loyalty = 3,
                        percent = 0.5,
                    )
                },
                specializedMagicLevels = listOf(1 to 5),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xDA, 0x01, 0x00,
                0xE8, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x08, 0x00,
                0x32, 0x00,
                0x01, 0x00,
                0x02, 0x00,
                0x03, 0x00,
                0x04, 0x00,
                0x10, 0x0E,
                0x00,
                0x64, 0x00, 0x00, 0x00,
                0xC8, 0x00, 0x00, 0x00,
                0x1E, 0x00, 0x00, 0x00,
                0x28, 0x00, 0x00, 0x00,
                0x05,
                0x2A, 0x00,
                0x05, 0x00,
                0x01, 0x00,
                0xDC, 0x00,
                0x6E, 0x00,
                0x90, 0x01, 0x00, 0x00,
                0xF4, 0x01, 0x00, 0x00,
                0x5E, 0x01, 0x00, 0x00,
                0x08,
                0x01,
                0x06, 0x00,
                0x07, 0x00,
                0x08, 0x00,
                0x19, 0x00,
                0x0B, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x32, 0x00,
                0x09, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x32, 0x00,
                0x08, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x32, 0x00,
                0x0A, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x32, 0x00,
                0x07, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x32, 0x00,
                0x06, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x32, 0x00,
                0x0D, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x32, 0x00,
                0x01,
                0x01, 0x05, 0x00,
            )
        )
    }

    @Test
    fun `writes offence stats 0xDA with melee weapon and optional critical sections`() {
        val out = Unpooled.buffer()

        CyclopediaCharacterWriter.writeOffenceStats(
            out,
            CyclopediaCharacterWriter.OffenceStatsFrame(
                criticals = List(2) { CyclopediaCharacterWriter.CriticalSkillEntry() },
                leeches = List(2) { CyclopediaCharacterWriter.LeechSkillEntry() },
                onslaughtTotal = 0.0,
                onslaughtEquipment = 0.0,
                cleave = 0.5,
                perfectShotDamage = listOf(1, 2, 3, 4, 5, 6, 7),
                flatDamageHealing = 0,
                weapon = CyclopediaCharacterWriter.CyclopediaWeapon.Melee(
                    attackTotal = 10,
                    flatBonus = 0,
                    weaponAttack = 7,
                    skillId = 11,
                    attackSkill = 5,
                    attackRawTotal = 8,
                    convertedDamage = 0.0,
                    convertedElement = 0,
                ),
                powerfulFoeDamage = 0.0,
                bestiaryDamages = listOf("Slime" to 0.5),
                elementalCriticalChance = 1 to 0.5,
                runesCriticalChance = 0.0,
                autoAttackCriticalChance = 0.0,
                elementalCriticalDamage = null,
                runesCriticalDamage = 0.0,
                autoAttackCriticalDamage = 0.0,
                lifeGainOnHit = 1,
                manaGainOnHit = 2,
                lifeGainOnKill = 3,
                manaGainOnKill = 4,
                autoAttackSkill = CyclopediaCharacterWriter.SkillPercentage(11, 0.5, 1.0),
                spellDamage = null,
                spellHealing = null,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *concat(
                bytes(0xDA, 0x0D, 0x00),
                concat(*Array(12) { zeroDouble }),
                concat(*Array(10) { zeroDouble }),
                concat(*Array(4) { zeroDouble }),
                doubleBytes(5000),
                bytes(
                    0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x04, 0x00, 0x05, 0x00, 0x06, 0x00, 0x07, 0x00,
                    0x00, 0x00,
                    0x00, 0x00,
                    0x00, 0x00,
                ),
                bytes(
                    0x0A, 0x00,
                    0x00, 0x00,
                    0x07, 0x00,
                    0x0B,
                    0x05, 0x00,
                    0x02, 0x00,
                    0x00,
                ),
                zeroDouble,
                bytes(0x00, 0x00),
                zeroDouble,
                bytes(0x01, 0x00),
                bytes(0x05, 0x00, 0x53, 0x6C, 0x69, 0x6D, 0x65),
                doubleBytes(5000),
                bytes(0x01),
                bytes(0x01),
                doubleBytes(5000),
                zeroDouble,
                zeroDouble,
                bytes(0x00),
                zeroDouble,
                zeroDouble,
                bytes(0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x04, 0x00),
                bytes(0x01),
                bytes(0x0B),
                doubleBytes(5000),
                doubleBytes(10000),
                bytes(0x00),
                bytes(0x00),
                zeroDouble,
                zeroDouble,
                zeroDouble,
                bytes(0x00),
            )
        )
    }

    @Test
    fun `writes defence stats 0xDA with dodge magic shield and absorb entries`() {
        val out = Unpooled.buffer()

        CyclopediaCharacterWriter.writeDefenceStats(
            out,
            CyclopediaCharacterWriter.DefenceStatsFrame(
                dodgeTotal = 0.5,
                dodgeEquipment = 0.0,
                dodgeEquipmentDiff = 0.5,
                wheelDodge = 0.0,
                magicShieldTotal = 100L,
                magicShieldFlat = 50,
                magicShieldPercent = 0.0,
                reflectFlat = 5,
                armor = 10,
                mantra = 2,
                defense = 20,
                defenseEquipment = 15,
                shielding = 30,
                defenseWheel = 1,
                mitigation = 0.5,
                mitigationZero = 0.0,
                equipmentMitigation = 0.5,
                skillMitigation = 0.5,
                wheelMitigation = 0.5,
                absorbs = listOf(
                    CyclopediaCharacterWriter.AbsorbEntry(element = 1, modifier = 0.5),
                    CyclopediaCharacterWriter.AbsorbEntry(element = 2, modifier = 0.25),
                ),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *concat(
                bytes(0xDA, 0x0E, 0x00),
                doubleBytes(5000),
                zeroDouble,
                doubleBytes(5000),
                zeroDouble,
                zeroDouble,
                bytes(0x64, 0x00, 0x00, 0x00),
                bytes(0x32, 0x00),
                zeroDouble,
                bytes(0x05, 0x00),
                bytes(0x0A, 0x00),
                bytes(0x02, 0x00),
                bytes(0x14, 0x00),
                bytes(0x0F, 0x00),
                bytes(0x06),
                bytes(0x1E, 0x00),
                bytes(0x01, 0x00),
                doubleBytes(5000),
                zeroDouble,
                doubleBytes(5000),
                doubleBytes(5000),
                doubleBytes(5000),
                bytes(0x02, 0x04, 0x01),
                doubleBytes(5000),
                bytes(0x04, 0x02),
                doubleBytes(2500),
            )
        )
    }

    @Test
    fun `writes misc stats 0xDA with blessings concoctions foods and augments`() {
        val out = Unpooled.buffer()

        CyclopediaCharacterWriter.writeMiscStats(
            out,
            CyclopediaCharacterWriter.MiscStatsFrame(
                momentumTotal = 0.5,
                momentumEquipment = 0.0,
                momentumEquipmentDiff = 0.5,
                wheelMomentum = 0.0,
                legsForgeTotal = 0.0,
                legsForgeEquipment = 0.0,
                legsForgeEquipmentDiff = 0.0,
                haveBlesses = 7,
                blessingsTotal = 7,
                concoctions = listOf(CyclopediaCharacterWriter.ActiveEffect(id = 100, duration = 3600L)),
                foods = listOf(CyclopediaCharacterWriter.ActiveEffect(id = 200, duration = 60L)),
                weaponAugments = listOf(CyclopediaCharacterWriter.SpellAugment(spellId = 5, type = 1, value = 0.5)),
                wheelAugments = emptyList(),
                equippedAugments = emptyList(),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *concat(
                bytes(0xDA, 0x0F, 0x00),
                doubleBytes(5000),
                zeroDouble,
                doubleBytes(5000),
                zeroDouble,
                zeroDouble,
                zeroDouble,
                zeroDouble,
                zeroDouble,
                doubleBytes(900),
                zeroDouble,
                zeroDouble,
                zeroDouble,
                bytes(0x07, 0x07),
                bytes(0x01),
                bytes(0x64, 0x00, 0x00, 0x00),
                bytes(0x10, 0x0E, 0x00, 0x00),
                bytes(0x01),
                bytes(0xC8, 0x00, 0x00, 0x00),
                bytes(0x3C, 0x00, 0x00, 0x00),
                bytes(0x01),
                bytes(0x05, 0x00, 0x01),
                doubleBytes(5000),
                bytes(0x00),
                bytes(0x00),
            )
        )
    }

    @Test
    fun `writes the cyclopedia house list 0xC7 with an available house`() {
        val out = Unpooled.buffer()

        CyclopediaCharacterWriter.writeHouseList(
            out,
            listOf(
                CyclopediaCharacterWriter.HouseEntry.Available(
                    clientId = 1L,
                    bidderName = "Bob",
                    bidder = false,
                    disableIndex = 0,
                    bidEndDate = 100L,
                    highestBid = 200L,
                    bidHolderLimit = 0L,
                ),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC7,
                0x01, 0x00,
                0x01, 0x00, 0x00, 0x00,
                0x01,
                0x00,
                0x03, 0x00, 0x42, 0x6F, 0x62,
                0x00,
                0x00,
                0x64, 0x00, 0x00, 0x00,
                0xC8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `write offence stats computed overload resolves the helper blocks`() {
        val computed = Unpooled.buffer()
        val framePath = Unpooled.buffer()

        val weaponType = ItemType(
            id = 100,
            name = "sword",
            slotPosition = SLOTP_LEFT,
            weaponType = WeaponType_t.WEAPON_SWORD,
            imbuementSlot = 1,
            upgradeClassification = 1,
        )
        val weapon = Item(id = 100, type = weaponType).also { it.setTier(1) }
        val boots = Item(
            id = 101,
            type = ItemType(
                id = 101,
                name = "boots",
                slotPosition = SLOTP_FEET,
                upgradeClassification = 1,
            ),
        ).also { it.setTier(1) }
        val equipment = mapOf(
            Skill.SKILL_CRITICAL_HIT_CHANCE to SkillsEquipment(equipment = 0.125),
            Skill.SKILL_CRITICAL_HIT_DAMAGE to SkillsEquipment(equipment = 0.25),
            Skill.SKILL_LIFE_LEECH_AMOUNT to SkillsEquipment(equipment = 0.5, imbuement = 0.25),
            Skill.SKILL_MANA_LEECH_AMOUNT to SkillsEquipment(equipment = 0.125),
        )
        val fire = Imbuement(10, 1).apply {
            combatType = CombatType.FIREDAMAGE
            elementDamage = 250
        }
        val player = FakeCyclopediaPlayer(
            inventory = mapOf(Slots_t.CONST_SLOT_LEFT to weapon, Slots_t.CONST_SLOT_FEET to boots),
            skills = mapOf(Skill.SKILL_LIFE_LEECH_AMOUNT to 20000),
            baseCritical = BaseCritical(chance = 0.25, damage = 0.5),
            wheel = FakeCyclopediaWheel(
                stats = mapOf(
                    WheelStat.CRITICAL_CHANCE to 2500,
                    WheelStat.CRITICAL_DAMAGE to 5000,
                    WheelStat.LIFE_LEECH to 2500,
                ),
            ),
            proficiency = FakeCyclopediaProficiency(
                generalCritical = WeaponProficiencyCriticalBonus(chance = 0.125, damage = 0.25),
            ),
        )
        val computation = CyclopediaComputation(
            player,
            imbuements = ImbuementInfoResolver { item, slot ->
                if (item.id == 100 && slot == 0) ImbuementInfo(fire, 100) else null
            },
        )

        CyclopediaCharacterWriter.writeOffenceStats(
            computed,
            CyclopediaCharacterWriter.OffenceStatsSource(
                skillsEquipment = { skill -> equipment.getValue(skill) },
                cleave = 0.5,
                perfectShotDamage = listOf(1, 2, 3, 4, 5, 6, 7),
                flatDamageHealing = 0,
                weapon = CyclopediaCharacterWriter.CyclopediaWeaponSource.Melee(
                    attackTotal = 10,
                    flatBonus = 0,
                    weaponAttack = 7,
                    skillId = 11,
                    attackSkill = 5,
                    attackRawTotal = 8,
                    conversion = null,
                ),
                powerfulFoeDamage = 0.0,
                bestiaryDamages = listOf("Slime" to 0.5),
                elementalCriticalChance = 1 to 0.5,
                runesCriticalChance = 0.0,
                autoAttackCriticalChance = 0.0,
                elementalCriticalDamage = null,
                runesCriticalDamage = 0.0,
                autoAttackCriticalDamage = 0.0,
                lifeGainOnHit = 1,
                manaGainOnHit = 2,
                lifeGainOnKill = 3,
                manaGainOnKill = 4,
                autoAttackSkill = CyclopediaCharacterWriter.SkillPercentage(11, 0.5, 1.0),
                spellDamage = null,
                spellHealing = null,
            ),
            computation,
        )

        CyclopediaCharacterWriter.writeOffenceStats(
            framePath,
            CyclopediaCharacterWriter.OffenceStatsFrame(
                criticals = listOf(
                    CyclopediaCharacterWriter.CriticalSkillEntry(
                        total = 0.75,
                        base = 0.25,
                        equipment = 0.25,
                        imbuement = 0.0,
                        wheel = 0.25,
                        concoction = 0.0,
                    ),
                    CyclopediaCharacterWriter.CriticalSkillEntry(
                        total = 1.5,
                        base = 0.5,
                        equipment = 0.5,
                        imbuement = 0.0,
                        wheel = 0.5,
                        concoction = 0.0,
                    ),
                ),
                leeches = listOf(
                    CyclopediaCharacterWriter.LeechSkillEntry(
                        total = 2.0,
                        equipment = 0.5,
                        imbuement = 0.25,
                        wheel = 0.25,
                        event = 0.0,
                    ),
                    CyclopediaCharacterWriter.LeechSkillEntry(
                        total = 0.0,
                        equipment = 0.125,
                        imbuement = 0.0,
                        wheel = 0.0,
                        event = 0.0,
                    ),
                ),
                onslaughtTotal = 0.5 * (1 + 2.5 / 100) / 100,
                onslaughtEquipment = 0.5 / 100,
                cleave = 0.5,
                perfectShotDamage = listOf(1, 2, 3, 4, 5, 6, 7),
                flatDamageHealing = 0,
                weapon = CyclopediaCharacterWriter.CyclopediaWeapon.Melee(
                    attackTotal = 10,
                    flatBonus = 0,
                    weaponAttack = 7,
                    skillId = 11,
                    attackSkill = 5,
                    attackRawTotal = 8,
                    convertedDamage = 2.5,
                    convertedElement = 1,
                ),
                powerfulFoeDamage = 0.0,
                bestiaryDamages = listOf("Slime" to 0.5),
                elementalCriticalChance = 1 to 0.5,
                runesCriticalChance = 0.0,
                autoAttackCriticalChance = 0.0,
                elementalCriticalDamage = null,
                runesCriticalDamage = 0.0,
                autoAttackCriticalDamage = 0.0,
                lifeGainOnHit = 1,
                manaGainOnHit = 2,
                lifeGainOnKill = 3,
                manaGainOnKill = 4,
                autoAttackSkill = CyclopediaCharacterWriter.SkillPercentage(11, 0.5, 1.0),
                spellDamage = null,
                spellHealing = null,
            ),
        )

        assertThat(computed.toByteArray()).containsExactly(*framePath.toByteArray())
    }

    @Test
    fun `write defence stats computed overload resolves dodge and absorbs`() {
        val computed = Unpooled.buffer()
        val framePath = Unpooled.buffer()

        val abilities = Abilities()
        abilities.absorbPercent[CombatType.ENERGYDAMAGE.value] = 25
        val armor = Item(
            id = 300,
            type = ItemType(id = 300, name = "armor", slotPosition = SLOTP_ARMOR, abilities = abilities),
        )
        val player = FakeCyclopediaPlayer(
            inventory = mapOf(Slots_t.CONST_SLOT_ARMOR to armor),
            abilitiesEnabled = setOf(Slots_t.CONST_SLOT_ARMOR),
            wheel = FakeCyclopediaWheel(stats = mapOf(WheelStat.DODGE to 500)),
        )
        val computation = CyclopediaComputation(player)

        CyclopediaCharacterWriter.writeDefenceStats(
            computed,
            CyclopediaCharacterWriter.DefenceStatsSource(
                magicShieldTotal = 100L,
                magicShieldFlat = 50,
                magicShieldPercent = 0.0,
                reflectFlat = 5,
                armor = 10,
                mantra = 2,
                defense = 20,
                defenseEquipment = 15,
                shielding = 30,
                defenseWheel = 1,
                mitigation = 0.5,
                mitigationZero = 0.0,
                equipmentMitigation = 0.5,
                skillMitigation = 0.5,
                wheelMitigation = 0.5,
            ),
            computation,
        )

        CyclopediaCharacterWriter.writeDefenceStats(
            framePath,
            CyclopediaCharacterWriter.DefenceStatsFrame(
                dodgeTotal = 500.0,
                dodgeEquipment = 0.0,
                dodgeEquipmentDiff = 0.0,
                wheelDodge = 500.0,
                magicShieldTotal = 100L,
                magicShieldFlat = 50,
                magicShieldPercent = 0.0,
                reflectFlat = 5,
                armor = 10,
                mantra = 2,
                defense = 20,
                defenseEquipment = 15,
                shielding = 30,
                defenseWheel = 1,
                mitigation = 0.5,
                mitigationZero = 0.0,
                equipmentMitigation = 0.5,
                skillMitigation = 0.5,
                wheelMitigation = 0.5,
                absorbs = listOf(CyclopediaCharacterWriter.AbsorbEntry(element = 3, modifier = 0.25)),
            ),
        )

        assertThat(computed.toByteArray()).containsExactly(*framePath.toByteArray())
    }

    @Test
    fun `write misc stats computed overload resolves the forge and wheel blocks`() {
        val computed = Unpooled.buffer()
        val framePath = Unpooled.buffer()

        fun tierItem(id: Int, slotPosition: Int): Item {
            val type = ItemType(id = id, name = "item-$id", slotPosition = slotPosition, upgradeClassification = 1)
            return Item(id = id, type = type).also { it.setTier(1) }
        }

        val player = FakeCyclopediaPlayer(
            inventory = mapOf(
                Slots_t.CONST_SLOT_HEAD to tierItem(400, SLOTP_HEAD),
                Slots_t.CONST_SLOT_LEGS to tierItem(401, SLOTP_LEGS),
                Slots_t.CONST_SLOT_FEET to tierItem(402, SLOTP_FEET),
            ),
            wheel = FakeCyclopediaWheel(momentum = 0.5),
        )
        val computation = CyclopediaComputation(player)

        CyclopediaCharacterWriter.writeMiscStats(
            computed,
            CyclopediaCharacterWriter.MiscStatsSource(
                haveBlesses = 7,
                blessingsTotal = 7,
                concoctions = listOf(CyclopediaCharacterWriter.ActiveEffect(id = 100, duration = 3600L)),
                foods = listOf(CyclopediaCharacterWriter.ActiveEffect(id = 200, duration = 60L)),
                weaponAugments = listOf(CyclopediaCharacterWriter.SpellAugment(spellId = 5, type = 1, value = 0.5)),
                wheelAugments = emptyList(),
                equippedAugments = emptyList(),
            ),
            computation,
        )

        val momentumTotal = 2.0 * (1 + 2.5 / 100) / 100 + 0.5
        val momentumEquipment = 2.0 / 100
        val legsForgeTotal = 0.127 * (1 + 2.5 / 100) / 100
        val legsForgeEquipment = 0.127 / 100
        CyclopediaCharacterWriter.writeMiscStats(
            framePath,
            CyclopediaCharacterWriter.MiscStatsFrame(
                momentumTotal = momentumTotal,
                momentumEquipment = momentumEquipment,
                momentumEquipmentDiff = 2.0 * (1 + 2.5 / 100) / 100 - momentumEquipment,
                wheelMomentum = 0.5,
                legsForgeTotal = legsForgeTotal,
                legsForgeEquipment = legsForgeEquipment,
                legsForgeEquipmentDiff = legsForgeTotal - legsForgeEquipment,
                haveBlesses = 7,
                blessingsTotal = 7,
                concoctions = listOf(CyclopediaCharacterWriter.ActiveEffect(id = 100, duration = 3600L)),
                foods = listOf(CyclopediaCharacterWriter.ActiveEffect(id = 200, duration = 60L)),
                weaponAugments = listOf(CyclopediaCharacterWriter.SpellAugment(spellId = 5, type = 1, value = 0.5)),
                wheelAugments = emptyList(),
                equippedAugments = emptyList(),
                feetForgeEquipment = 2.5 / 100,
            ),
        )

        assertThat(computed.toByteArray()).containsExactly(*framePath.toByteArray())
    }
}
