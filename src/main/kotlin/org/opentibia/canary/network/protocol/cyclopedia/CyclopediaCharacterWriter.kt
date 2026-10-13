package org.opentibia.canary.network.protocol.cyclopedia

import io.netty.buffer.ByteBuf
import kotlin.math.pow
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.players.components.RecentDeathEntry
import org.opentibia.canary.creatures.players.components.RecentPvPKillEntry
import org.opentibia.canary.items.Item
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.GameWindowWriter

/**
 * The remaining outbound cyclopedia character-info frames, ported from Canary
 * src/server/network/protocol/protocolgame.cpp. Each shares the 0xDA opcode
 * plus the `CyclopediaCharacterInfoType_t` byte; the no-data and base
 * information frames already live in `CyclopediaWindowWriter`.
 *
 *  - `sendCyclopediaCharacterGeneralStats`      (protocolgame.cpp:4807-4877)
 *  - `sendCyclopediaCharacterRecentDeaths`      (protocolgame.cpp:4879-4897)
 *  - `sendCyclopediaCharacterRecentPvPKills`    (protocolgame.cpp:4899-4918)
 *  - `sendCyclopediaCharacterAchievements`      (protocolgame.cpp:4920-4945)
 *  - `sendCyclopediaCharacterItemSummary`       (protocolgame.cpp:4947-5009)
 *  - `sendCyclopediaCharacterOutfitsMounts`     (protocolgame.cpp:5011-5112)
 *  - `sendCyclopediaCharacterStoreSummary`      (protocolgame.cpp:5114-5191)
 *  - `sendCyclopediaCharacterInspection`        (protocolgame.cpp:5193-5329)
 *  - `sendCyclopediaCharacterBadges`            (protocolgame.cpp:5331-5363)
 *  - `sendCyclopediaCharacterTitles`            (protocolgame.cpp:5365-5389)
 *  - `sendCyclopediaCharacterOffenceStats`      (protocolgame.cpp:5391-5642)
 *  - `sendCyclopediaCharacterDefenceStats`      (protocolgame.cpp:5644-5700)
 *  - `sendCyclopediaCharacterMiscStats`         (protocolgame.cpp:5702-5795)
 *  - `sendCyclopediaHouseList`                  (0xC7, protocolgame.cpp:12013-12095)
 *
 * The `!player`/`oldProtocol` guards and the live player/engine lookups stay
 * with the caller: every value Canary reads from the player, wheel, forge,
 * weapon proficiency or item registry is passed in through the frame types.
 * The `addCyclopedia*`, `handleImbuementDamage`, `getCipbia*` and
 * `calculateAbsorbValues` computations are likewise pre-resolved by the caller.
 * The `IgnoreItemList`/house-map order is preserved from the caller; every
 * multi-byte field is Little-Endian and strings are `uint16` length plus UTF-8.
 */
object CyclopediaCharacterWriter {

    private const val PACKET = CyclopediaWindowWriter.PACKET_CYCLOPEDIA_CHARACTER_INFO

    /** `sendCyclopediaHouseList` opcode (protocolgame.cpp:12015). */
    const val PACKET_CYCLOPEDIA_HOUSE_LIST: Int = 0xC7

    /** `CyclopediaCharacterInfo_OutfitType_t` (`src/server/server_definitions.hpp:92-96`). */
    const val OUTFITTYPE_NONE: Int = 0
    const val OUTFITTYPE_QUEST: Int = 1
    const val OUTFITTYPE_STORE: Int = 2

    /** `CyclopediaHouseState` (`src/enums/player_cyclopedia.hpp:64-69`). */
    const val HOUSE_STATE_AVAILABLE: Int = 0
    const val HOUSE_STATE_RENTED: Int = 2
    const val HOUSE_STATE_TRANSFER: Int = 3
    const val HOUSE_STATE_MOVE_OUT: Int = 4

    /** One `sendCyclopediaCharacterGeneralStats` skill row (`skills_t` loop). */
    data class GeneralSkillEntry(
        val clientId: Int,
        val level: Int,
        val base: Int,
        val loyalty: Int,
        val percent: Double,
    )

    /** The 0xDA `GENERALSTATS` payload. */
    data class GeneralStatsFrame(
        val experience: Long,
        val level: Int,
        val levelPercent: Double,
        val baseXpGain: Int,
        val displayGrindingXpBoost: Int,
        val displayXpBoostPercent: Int,
        val staminaXpBoost: Int,
        val xpBoostTime: Int,
        val health: Int,
        val maxHealth: Int,
        val mana: Int,
        val maxMana: Int,
        val soul: Int,
        val staminaMinutes: Int,
        val regenerationTicks: Long,
        val offlineTrainingTime: Long,
        val speed: Int,
        val baseSpeed: Int,
        val capacity: Int,
        val baseCapacity: Int,
        val freeCapacity: Int,
        val magicLevel: Int,
        val baseMagicLevel: Int,
        val loyaltyMagicLevel: Int,
        val magicLevelPercent: Double,
        val skills: List<GeneralSkillEntry>,
        val specializedMagicLevels: List<Pair<Int, Int>>,
    )

    /** `newestAchievement` timestamps on `sendCyclopediaCharacterAchievements`. */
    data class AchievementEntry(
        val id: Int,
        val timestamp: Long,
        val secret: Boolean,
        val name: String = "",
        val description: String = "",
        val grade: Int = 0,
    )

    /** One `PlayerTitle` on `sendCyclopediaCharacterTitles`. */
    data class TitleEntry(
        val id: Int,
        val name: String,
        val description: String,
        val permanent: Boolean,
        val unlocked: Boolean,
    )

    /** One `g_game().getBadges()` row on `sendCyclopediaCharacterBadges`. */
    data class BadgeEntry(
        val id: Long,
        val name: String,
    )

    /**
     * One `ItemsTierCountList` entry (`player.hpp:1639`). [tier] is written only
     * when the item classification is greater than 0; `null` skips the byte.
     */
    data class ItemSummaryEntry(
        val itemId: Int,
        val tier: Int?,
        val count: Long,
    )

    /** One owned outfit on `sendCyclopediaCharacterOutfitsMounts`. */
    data class OutfitEntry(
        val lookType: Int,
        val name: String,
        val addons: Int,
        val type: Int,
        val active: Boolean,
    )

    /** One owned mount on `sendCyclopediaCharacterOutfitsMounts`. */
    data class MountEntry(
        val clientId: Int,
        val name: String,
        val type: Int,
    )

    /** One unlocked familiar on `sendCyclopediaCharacterOutfitsMounts`. */
    data class FamiliarEntry(
        val lookType: Int,
        val name: String,
        val type: Int,
    )

    /** The 0xDA `OUTFITSMOUNTS` payload. */
    data class OutfitsMountsFrame(
        val outfits: List<OutfitEntry>,
        val currentOutfit: Outfit,
        val mounts: List<MountEntry>,
        val currentMountOutfit: Outfit = Outfit(),
        val familiars: List<FamiliarEntry> = emptyList(),
    )

    /** One player house item on `sendCyclopediaCharacterStoreSummary`. */
    data class HouseItemEntry(
        val itemId: Int,
        val name: String,
        val count: Int,
    )

    /** The 0xDA `STORESUMMARY` payload. */
    data class StoreSummaryFrame(
        val xpBoostTime: Long,
        val dailyRewardXpBoost: Long,
        val blessings: List<Pair<String, Int>>,
        val preySlotsUnlocked: Int,
        val preyWildcards: Int,
        val weeklyExpansionUnlocked: Boolean,
        val instantRewards: Int,
        val charmExpansion: Boolean,
        val hirelings: Int,
        val hirelingSkills: List<Int>,
        val houseItems: List<HouseItemEntry>,
    )

    /** One equipped item on `sendCyclopediaCharacterInspection`. */
    data class InspectionItemEntry(
        val slot: Int,
        val name: String,
        val item: Item,
        val imbuementIconIds: List<Int> = emptyList(),
        val descriptions: List<Pair<String, String>> = emptyList(),
    )

    /** The 0xDA `INSPECTION` payload. */
    data class InspectionFrame(
        val inventory: List<InspectionItemEntry>,
        val playerName: String,
        val defaultOutfit: Outfit,
        val playerDescriptions: List<Pair<String, String>>,
    )

    /** One `addCyclopediaCriticalSkill` block (six doubles in wire order). */
    data class CriticalSkillEntry(
        val total: Double = 0.0,
        val base: Double = 0.0,
        val equipment: Double = 0.0,
        val imbuement: Double = 0.0,
        val wheel: Double = 0.0,
        val concoction: Double = 0.0,
    )

    /** One `addCyclopediaSkills` block (five doubles in wire order). */
    data class LeechSkillEntry(
        val total: Double = 0.0,
        val equipment: Double = 0.0,
        val imbuement: Double = 0.0,
        val wheel: Double = 0.0,
        val event: Double = 0.0,
    )

    /** One optional `skillPercentage` block (auto attack/spell damage/healing). */
    data class SkillPercentage(
        val skillId: Int,
        val percent: Double,
        val applied: Double,
    )

    /** The weapon branch of `sendCyclopediaCharacterOffenceStats`. */
    sealed interface CyclopediaWeapon {
        data class Wand(val maxHitChance: Int, val element: Int) : CyclopediaWeapon

        data class Distance(
            val attackTotal: Int,
            val flatBonus: Int,
            val attackValue: Int,
            val attackSkill: Int,
            val attackRawTotal: Int,
            val convertedDamage: Double,
            val convertedElement: Int,
            val accuracyPercent: List<Double> = emptyList(),
        ) : CyclopediaWeapon

        data class Melee(
            val attackTotal: Int,
            val flatBonus: Int,
            val weaponAttack: Int,
            val skillId: Int,
            val attackSkill: Int,
            val attackRawTotal: Int,
            val convertedDamage: Double,
            val convertedElement: Int,
        ) : CyclopediaWeapon

        data class Fist(
            val attackTotal: Int,
            val flatBonus: Int,
            val attackValue: Int,
            val skillId: Int,
            val attackSkill: Int,
            val attackRawTotal: Int,
        ) : CyclopediaWeapon
    }

    /** The 0xDA `OFFENCESTATS` payload. */
    data class OffenceStatsFrame(
        val criticals: List<CriticalSkillEntry>,
        val leeches: List<LeechSkillEntry>,
        val onslaughtTotal: Double,
        val onslaughtEquipment: Double,
        val cleave: Double,
        val perfectShotDamage: List<Int>,
        val flatDamageHealing: Int,
        val weapon: CyclopediaWeapon,
        val powerfulFoeDamage: Double,
        val bestiaryDamages: List<Pair<String, Double>>,
        val elementalCriticalChance: Pair<Int, Double>?,
        val runesCriticalChance: Double,
        val autoAttackCriticalChance: Double,
        val elementalCriticalDamage: Pair<Int, Double>?,
        val runesCriticalDamage: Double,
        val autoAttackCriticalDamage: Double,
        val lifeGainOnHit: Int,
        val manaGainOnHit: Int,
        val lifeGainOnKill: Int,
        val manaGainOnKill: Int,
        val autoAttackSkill: SkillPercentage?,
        val spellDamage: SkillPercentage?,
        val spellHealing: SkillPercentage?,
    )

    /** One `calculateAbsorbValues` element entry (protocolgame.cpp:359-375). */
    data class AbsorbEntry(
        val element: Int,
        val modifier: Double,
    )

    /** The 0xDA `DEFENCESTATS` payload. */
    data class DefenceStatsFrame(
        val dodgeTotal: Double,
        val dodgeEquipment: Double,
        val dodgeEquipmentDiff: Double,
        val wheelDodge: Double,
        val magicShieldTotal: Long,
        val magicShieldFlat: Int,
        val magicShieldPercent: Double,
        val reflectFlat: Int,
        val armor: Int,
        val mantra: Int,
        val defense: Int,
        val defenseEquipment: Int,
        val shielding: Int,
        val defenseWheel: Int,
        val mitigation: Double,
        val mitigationZero: Double,
        val equipmentMitigation: Double,
        val skillMitigation: Double,
        val wheelMitigation: Double,
        val absorbs: List<AbsorbEntry>,
    )

    /** One active concoction/food (`Player::getActiveConcoctions`/`getActiveFoods`). */
    data class ActiveEffect(
        val id: Int,
        val duration: Long,
    )

    /** One spell augment (`weaponProficiency`/`wheel`/equipped augments). */
    data class SpellAugment(
        val spellId: Int,
        val type: Int,
        val value: Double,
    )

    /** The 0xDA `MISCSTATS` payload. */
    data class MiscStatsFrame(
        val momentumTotal: Double,
        val momentumEquipment: Double,
        val momentumEquipmentDiff: Double,
        val wheelMomentum: Double,
        val legsForgeTotal: Double,
        val legsForgeEquipment: Double,
        val legsForgeEquipmentDiff: Double,
        val haveBlesses: Int,
        val blessingsTotal: Int,
        val concoctions: List<ActiveEffect>,
        val foods: List<ActiveEffect>,
        val weaponAugments: List<SpellAugment>,
        val wheelAugments: List<SpellAugment>,
        val equippedAugments: List<SpellAugment>,
        val feetForgeEquipment: Double = 0.0,
    )

    /** The `HouseMap` entry branches `sendCyclopediaHouseList` serializes. */
    sealed interface HouseEntry {
        val clientId: Long
        val state: Int

        data class Available(
            override val clientId: Long,
            val bidderName: String,
            val bidder: Boolean,
            val disableIndex: Int,
            val bidEndDate: Long,
            val highestBid: Long,
            val bidHolderLimit: Long = 0L,
        ) : HouseEntry {
            override val state: Int get() = HOUSE_STATE_AVAILABLE
        }

        data class Rented(
            override val clientId: Long,
            val ownerName: String,
            val paidUntil: Long,
            val rented: Boolean,
        ) : HouseEntry {
            override val state: Int get() = HOUSE_STATE_RENTED
        }

        data class Transfer(
            override val clientId: Long,
            val ownerName: String,
            val paidUntil: Long,
            val isOwner: Boolean,
            val bidEndDate: Long,
            val bidderName: String,
            val internalBid: Long,
            val isNewOwner: Boolean,
            val acceptDisableIndex: Int = 0,
        ) : HouseEntry {
            override val state: Int get() = HOUSE_STATE_TRANSFER
        }

        data class MoveOut(
            override val clientId: Long,
            val ownerName: String,
            val paidUntil: Long,
            val isOwner: Boolean,
            val bidEndDate: Long,
        ) : HouseEntry {
            override val state: Int get() = HOUSE_STATE_MOVE_OUT
        }
    }

    /** `sendCyclopediaCharacterRecentDeaths` (protocolgame.cpp:4879-4897). */
    fun writeRecentDeaths(out: ByteBuf, page: Int, pages: Int, entries: List<RecentDeathEntry>) {
        out.writeByte(PACKET)
        out.writeByte(CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_RECENTDEATHS)
        out.writeByte(0x00)
        out.writeShortLE(page)
        out.writeShortLE(pages)
        out.writeShortLE(entries.size)
        for (entry in entries) {
            out.writeIntLE(entry.timestamp)
            out.writeTibiaString(entry.cause)
        }
    }

    /** `sendCyclopediaCharacterRecentPvPKills` (protocolgame.cpp:4899-4918). */
    fun writeRecentPvPKills(out: ByteBuf, page: Int, pages: Int, entries: List<RecentPvPKillEntry>) {
        out.writeByte(PACKET)
        out.writeByte(CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_RECENTPVPKILLS)
        out.writeByte(0x00)
        out.writeShortLE(page)
        out.writeShortLE(pages)
        out.writeShortLE(entries.size)
        for (entry in entries) {
            out.writeIntLE(entry.timestamp)
            out.writeTibiaString(entry.description)
            out.writeByte(entry.status)
        }
    }

    /** `sendCyclopediaCharacterAchievements` (protocolgame.cpp:4920-4945). */
    fun writeAchievements(out: ByteBuf, points: Int, secretsUnlocked: Int, entries: List<AchievementEntry>) {
        out.writeByte(PACKET)
        out.writeByte(CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_ACHIEVEMENTS)
        out.writeByte(0x00)
        out.writeShortLE(points)
        out.writeShortLE(secretsUnlocked)
        out.writeShortLE(entries.size)
        for (entry in entries) {
            out.writeShortLE(entry.id)
            out.writeIntLE(entry.timestamp.toInt())
            if (entry.secret) {
                out.writeByte(0x01)
                out.writeTibiaString(entry.name)
                out.writeTibiaString(entry.description)
                out.writeByte(entry.grade)
            } else {
                out.writeByte(0x00)
            }
        }
    }

    /** `sendCyclopediaCharacterTitles` (protocolgame.cpp:5365-5389). */
    fun writeTitles(out: ByteBuf, currentTitle: Int, titles: List<TitleEntry>) {
        out.writeByte(PACKET)
        out.writeByte(CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_TITLES)
        out.writeByte(0x00)
        out.writeByte(currentTitle)
        out.writeByte(titles.size)
        for (title in titles) {
            out.writeByte(title.id)
            out.writeTibiaString(title.name)
            out.writeTibiaString(title.description)
            out.writeByte(if (title.permanent) 0x01 else 0x00)
            out.writeByte(if (title.unlocked) 0x01 else 0x00)
        }
    }

    /**
     * `sendCyclopediaCharacterBadges` (protocolgame.cpp:5331-5363).
     * [showAccountInformation] is the fixed 0x01 byte; the caller resolves
     * `Game::getPlayerByName(...) != nullptr` into [isOnline].
     */
    fun writeBadges(
        out: ByteBuf,
        isOnline: Boolean,
        isPremium: Boolean,
        loyaltyTitle: String,
        badges: List<BadgeEntry>,
        showAccountInformation: Boolean = true,
    ) {
        out.writeByte(PACKET)
        out.writeByte(CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_BADGES)
        out.writeByte(0x00)
        out.writeByte(if (showAccountInformation) 0x01 else 0x00)
        out.writeByte(if (isOnline) 0x01 else 0x00)
        out.writeByte(if (isPremium) 0x01 else 0x00)
        out.writeTibiaString(loyaltyTitle)
        out.writeByte(badges.size)
        for (badge in badges) {
            out.writeIntLE(badge.id.toInt())
            out.writeTibiaString(badge.name)
        }
    }

    /**
     * `sendCyclopediaCharacterItemSummary` (protocolgame.cpp:4947-5009).
     * The four `ItemsTierCountList` blocks each write their own count head; the
     * supply stash block follows with the tier byte only for classified items.
     */
    fun writeItemSummary(
        out: ByteBuf,
        inventoryItems: List<ItemSummaryEntry>,
        storeInboxItems: List<ItemSummaryEntry>,
        stashItems: List<ItemSummaryEntry>,
        depotBoxItems: List<ItemSummaryEntry>,
        inboxItems: List<ItemSummaryEntry>,
    ) {
        out.writeByte(PACKET)
        out.writeByte(CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_ITEMSUMMARY)
        out.writeByte(0x00)

        writeItemList(out, inventoryItems)
        writeItemList(out, storeInboxItems)

        out.writeShortLE(stashItems.size)
        for (entry in stashItems) {
            out.writeShortLE(entry.itemId)
            entry.tier?.let { out.writeByte(it) }
            out.writeIntLE(entry.count.toInt())
        }

        writeItemList(out, depotBoxItems)
        writeItemList(out, inboxItems)
    }

    /** `sendCyclopediaCharacterOutfitsMounts` (protocolgame.cpp:5011-5112). */
    fun writeOutfitsMounts(out: ByteBuf, frame: OutfitsMountsFrame) {
        out.writeByte(PACKET)
        out.writeByte(CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_OUTFITSMOUNTS)
        out.writeByte(0x00)

        out.writeShortLE(frame.outfits.size)
        for (outfit in frame.outfits) {
            out.writeShortLE(outfit.lookType)
            out.writeTibiaString(outfit.name)
            out.writeByte(outfit.addons)
            out.writeByte(outfit.type)
            out.writeIntLE(if (outfit.active) 1000 else 0)
        }
        if (frame.outfits.isNotEmpty()) {
            out.writeByte(frame.currentOutfit.lookHead)
            out.writeByte(frame.currentOutfit.lookBody)
            out.writeByte(frame.currentOutfit.lookLegs)
            out.writeByte(frame.currentOutfit.lookFeet)
        }

        out.writeShortLE(frame.mounts.size)
        for (mount in frame.mounts) {
            out.writeShortLE(mount.clientId)
            out.writeTibiaString(mount.name)
            out.writeByte(mount.type)
            out.writeIntLE(1000)
        }
        if (frame.mounts.isNotEmpty()) {
            out.writeByte(frame.currentMountOutfit.lookMountHead)
            out.writeByte(frame.currentMountOutfit.lookMountBody)
            out.writeByte(frame.currentMountOutfit.lookMountLegs)
            out.writeByte(frame.currentMountOutfit.lookMountFeet)
        }

        out.writeShortLE(frame.familiars.size)
        for (familiar in frame.familiars) {
            out.writeShortLE(familiar.lookType)
            out.writeTibiaString(familiar.name)
            out.writeByte(familiar.type)
            out.writeIntLE(0)
        }
    }

    /** `sendCyclopediaCharacterStoreSummary` (protocolgame.cpp:5114-5191). */
    fun writeStoreSummary(out: ByteBuf, frame: StoreSummaryFrame) {
        out.writeByte(PACKET)
        out.writeByte(CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_STORESUMMARY)
        out.writeByte(0x00)
        out.writeIntLE(frame.xpBoostTime.toInt())
        out.writeIntLE(frame.dailyRewardXpBoost.toInt())

        out.writeByte(frame.blessings.size)
        for ((name, count) in frame.blessings) {
            out.writeTibiaString(name)
            out.writeByte(count)
        }

        out.writeByte(frame.preySlotsUnlocked)
        out.writeByte(frame.preyWildcards)
        out.writeByte(if (frame.weeklyExpansionUnlocked) 0x01 else 0x00)
        out.writeByte(frame.instantRewards)
        out.writeByte(if (frame.charmExpansion) 0x01 else 0x00)
        out.writeByte(frame.hirelings)

        out.writeByte(frame.hirelingSkills.size)
        for (id in frame.hirelingSkills) {
            out.writeByte(id - 1000)
        }

        out.writeByte(0x00)

        out.writeShortLE(frame.houseItems.size)
        for (item in frame.houseItems) {
            out.writeShortLE(item.itemId)
            out.writeTibiaString(item.name)
            out.writeByte(item.count)
        }
    }

    /** `sendCyclopediaCharacterInspection` (protocolgame.cpp:5193-5329). */
    fun writeInspection(out: ByteBuf, frame: InspectionFrame) {
        out.writeByte(PACKET)
        out.writeByte(CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_INSPECTION)
        out.writeByte(0x00)

        out.writeByte(frame.inventory.size)
        for (entry in frame.inventory) {
            out.writeByte(entry.slot)
            out.writeTibiaString(entry.name)
            GameWindowWriter.writeItem(out, entry.item)
            out.writeByte(entry.imbuementIconIds.size)
            for (iconId in entry.imbuementIconIds) {
                out.writeShortLE(iconId)
            }
            out.writeByte(entry.descriptions.size)
            for ((key, value) in entry.descriptions) {
                out.writeTibiaString(key)
                out.writeTibiaString(value)
            }
        }

        out.writeTibiaString(frame.playerName)
        GameWindowWriter.writeOutfit(out, frame.defaultOutfit, addMount = false)

        out.writeByte(frame.playerDescriptions.size)
        for ((key, value) in frame.playerDescriptions) {
            out.writeTibiaString(key)
            out.writeTibiaString(value)
        }
    }

    /**
     * `sendCyclopediaCharacterGeneralStats` (protocolgame.cpp:4807-4877).
     * The health/mana clamps to `uint16` and the level percent clamp to 10000
     * reproduce Canary's `std::min` expressions.
     */
    fun writeGeneralStats(out: ByteBuf, frame: GeneralStatsFrame) {
        out.writeByte(PACKET)
        out.writeByte(CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_GENERALSTATS)
        out.writeByte(0x00)

        out.writeLongLE(frame.experience)
        out.writeShortLE(frame.level)
        out.writeShortLE(minOf(((frame.levelPercent * 100).toInt()) and 0xFFFF, 10000))
        out.writeShortLE(frame.baseXpGain)
        out.writeShortLE(frame.displayGrindingXpBoost)
        out.writeShortLE(frame.displayXpBoostPercent)
        out.writeShortLE(frame.staminaXpBoost)
        out.writeShortLE(frame.xpBoostTime)
        out.writeByte(if (frame.xpBoostTime > 0) 0x00 else 0x01)
        out.writeIntLE(minOf(frame.health, 0xFFFF))
        out.writeIntLE(minOf(frame.maxHealth, 0xFFFF))
        out.writeIntLE(minOf(frame.mana, 0xFFFF))
        out.writeIntLE(minOf(frame.maxMana, 0xFFFF))
        out.writeByte(frame.soul)
        out.writeShortLE(frame.staminaMinutes)
        out.writeShortLE((frame.regenerationTicks / 1000).toInt())
        out.writeShortLE((frame.offlineTrainingTime / 60 / 1000).toInt())
        out.writeShortLE(frame.speed)
        out.writeShortLE(frame.baseSpeed)
        out.writeIntLE(frame.capacity)
        out.writeIntLE(frame.baseCapacity)
        out.writeIntLE(frame.freeCapacity)
        out.writeByte(8)
        out.writeByte(1)
        out.writeShortLE(frame.magicLevel)
        out.writeShortLE(frame.baseMagicLevel)
        out.writeShortLE(frame.loyaltyMagicLevel)
        out.writeShortLE((frame.magicLevelPercent * 100).toInt())

        for (skill in frame.skills) {
            out.writeByte(skill.clientId)
            out.writeShortLE(minOf(skill.level, 0xFFFF))
            out.writeShortLE(skill.base)
            out.writeShortLE(skill.loyalty)
            out.writeShortLE((skill.percent * 100).toInt())
        }

        out.writeByte(frame.specializedMagicLevels.size)
        for ((element, level) in frame.specializedMagicLevels) {
            out.writeByte(element)
            out.writeShortLE(level)
        }
    }

    /**
     * `sendCyclopediaCharacterOffenceStats` (protocolgame.cpp:5391-5642).
     * The `elementalCriticalChance`/`elementalCriticalDamage` optionals write
     * the presence byte plus the element byte and double when present.
     */
    fun writeOffenceStats(out: ByteBuf, frame: OffenceStatsFrame) {
        out.writeByte(PACKET)
        out.writeByte(CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_OFFENCESTATS)
        out.writeByte(0x00)

        for (critical in frame.criticals) {
            out.writeCanaryDouble(critical.total)
            out.writeCanaryDouble(critical.base)
            out.writeCanaryDouble(critical.equipment)
            out.writeCanaryDouble(critical.imbuement)
            out.writeCanaryDouble(critical.wheel)
            out.writeCanaryDouble(critical.concoction)
        }

        for (leech in frame.leeches) {
            out.writeCanaryDouble(leech.total)
            out.writeCanaryDouble(leech.equipment)
            out.writeCanaryDouble(leech.imbuement)
            out.writeCanaryDouble(leech.wheel)
            out.writeCanaryDouble(leech.event)
        }

        out.writeCanaryDouble(frame.onslaughtTotal)
        out.writeCanaryDouble(frame.onslaughtEquipment)
        out.writeCanaryDouble(frame.onslaughtTotal - frame.onslaughtEquipment)
        out.writeCanaryDouble(0.0)

        out.writeCanaryDouble(frame.cleave)

        for (range in 1..7) {
            out.writeShortLE(frame.perfectShotDamage.getOrElse(range - 1) { 0 })
        }

        out.writeShortLE(frame.flatDamageHealing)
        out.writeShortLE(frame.flatDamageHealing)
        out.writeShortLE(0)

        writeWeapon(out, frame.weapon)

        out.writeCanaryDouble(frame.powerfulFoeDamage)

        out.writeShortLE(frame.bestiaryDamages.size)
        for ((name, amount) in frame.bestiaryDamages) {
            out.writeTibiaString(name)
            out.writeCanaryDouble(amount)
        }

        out.writeByte(if (frame.elementalCriticalChance != null) 0x01 else 0x00)
        frame.elementalCriticalChance?.let {
            out.writeByte(it.first)
            out.writeCanaryDouble(it.second)
        }

        out.writeCanaryDouble(frame.runesCriticalChance)
        out.writeCanaryDouble(frame.autoAttackCriticalChance)

        out.writeByte(if (frame.elementalCriticalDamage != null) 0x01 else 0x00)
        frame.elementalCriticalDamage?.let {
            out.writeByte(it.first)
            out.writeCanaryDouble(it.second)
        }

        out.writeCanaryDouble(frame.runesCriticalDamage)
        out.writeCanaryDouble(frame.autoAttackCriticalDamage)

        out.writeShortLE(frame.lifeGainOnHit)
        out.writeShortLE(frame.manaGainOnHit)
        out.writeShortLE(frame.lifeGainOnKill)
        out.writeShortLE(frame.manaGainOnKill)

        writeSkillPercentage(out, frame.autoAttackSkill)
        writeSkillPercentage(out, frame.spellDamage)
        writeSkillPercentage(out, frame.spellHealing)

        out.writeCanaryDouble(0.0)
        out.writeCanaryDouble(0.0)
        out.writeCanaryDouble(0.0)
        out.writeByte(0x00)
    }

    /** `sendCyclopediaCharacterDefenceStats` (protocolgame.cpp:5644-5700). */
    fun writeDefenceStats(out: ByteBuf, frame: DefenceStatsFrame) {
        out.writeByte(PACKET)
        out.writeByte(CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_DEFENCESTATS)
        out.writeByte(0x00)

        out.writeCanaryDouble(frame.dodgeTotal)
        out.writeCanaryDouble(frame.dodgeEquipment)
        out.writeCanaryDouble(frame.dodgeEquipmentDiff)
        out.writeCanaryDouble(0.0)
        out.writeCanaryDouble(frame.wheelDodge)

        out.writeIntLE(frame.magicShieldTotal.toInt())
        out.writeShortLE(frame.magicShieldFlat)
        out.writeCanaryDouble(frame.magicShieldPercent)

        out.writeShortLE(frame.reflectFlat)
        out.writeShortLE(frame.armor)
        out.writeShortLE(frame.mantra)
        out.writeShortLE(frame.defense)
        out.writeShortLE(frame.defenseEquipment)
        out.writeByte(0x06)
        out.writeShortLE(frame.shielding)
        out.writeShortLE(frame.defenseWheel)

        out.writeCanaryDouble(frame.mitigation)
        out.writeCanaryDouble(frame.mitigationZero)
        out.writeCanaryDouble(frame.equipmentMitigation)
        out.writeCanaryDouble(frame.skillMitigation)
        out.writeCanaryDouble(frame.wheelMitigation)

        out.writeByte(frame.absorbs.size)
        for (absorb in frame.absorbs) {
            out.writeByte(0x04)
            out.writeByte(absorb.element)
            out.writeCanaryDouble(absorb.modifier)
        }
    }

    /** `sendCyclopediaCharacterMiscStats` (protocolgame.cpp:5702-5795). */
    fun writeMiscStats(out: ByteBuf, frame: MiscStatsFrame) {
        out.writeByte(PACKET)
        out.writeByte(CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_MISCSTATS)
        out.writeByte(0x00)

        out.writeCanaryDouble(frame.momentumTotal)
        out.writeCanaryDouble(frame.momentumEquipment)
        out.writeCanaryDouble(frame.momentumEquipmentDiff)
        out.writeCanaryDouble(frame.wheelMomentum)
        out.writeCanaryDouble(0.0)

        out.writeCanaryDouble(frame.legsForgeTotal)
        out.writeCanaryDouble(frame.legsForgeEquipment)
        out.writeCanaryDouble(frame.legsForgeEquipmentDiff)
        out.writeCanaryDouble(0.09)

        out.writeCanaryDouble(frame.feetForgeEquipment)
        out.writeCanaryDouble(frame.feetForgeEquipment)
        out.writeCanaryDouble(0.0)

        out.writeByte(frame.haveBlesses)
        out.writeByte(frame.blessingsTotal)

        out.writeByte(frame.concoctions.size)
        for (concoction in frame.concoctions) {
            writeActiveEffect(out, concoction)
        }

        out.writeByte(frame.foods.size)
        for (food in frame.foods) {
            writeActiveEffect(out, food)
        }

        writeAugments(out, frame.weaponAugments)
        writeAugments(out, frame.wheelAugments)
        writeAugments(out, frame.equippedAugments)
    }

    /** `sendCyclopediaHouseList` (protocolgame.cpp:12013-12095). */
    fun writeHouseList(out: ByteBuf, houses: List<HouseEntry>) {
        out.writeByte(PACKET_CYCLOPEDIA_HOUSE_LIST)
        out.writeShortLE(houses.size)

        for (house in houses) {
            out.writeIntLE(house.clientId.toInt())
            out.writeByte(0x01)
            out.writeByte(house.state)

            when (house) {
                is HouseEntry.Available -> {
                    out.writeTibiaString(house.bidderName)
                    out.writeByte(if (house.bidder) 0x01 else 0x00)
                    out.writeByte(house.disableIndex)
                    if (house.bidderName.isNotEmpty()) {
                        out.writeIntLE(house.bidEndDate.toInt())
                        out.writeLongLE(house.highestBid)
                        if (house.bidder) {
                            out.writeLongLE(house.bidHolderLimit)
                        }
                    }
                }

                is HouseEntry.Rented -> {
                    out.writeTibiaString(house.ownerName)
                    out.writeIntLE(house.paidUntil.toInt())
                    out.writeByte(if (house.rented) 0x01 else 0x00)
                    if (house.rented) {
                        out.writeByte(0)
                        out.writeByte(0)
                    }
                }

                is HouseEntry.Transfer -> {
                    out.writeTibiaString(house.ownerName)
                    out.writeIntLE(house.paidUntil.toInt())
                    out.writeByte(if (house.isOwner) 0x01 else 0x00)
                    if (house.isOwner) {
                        out.writeByte(0)
                        out.writeByte(0)
                    }
                    out.writeIntLE(house.bidEndDate.toInt())
                    out.writeTibiaString(house.bidderName)
                    out.writeByte(0)
                    out.writeLongLE(house.internalBid)
                    out.writeByte(if (house.isNewOwner) 0x01 else 0x00)
                    if (house.isNewOwner) {
                        out.writeByte(house.acceptDisableIndex)
                        out.writeByte(0)
                    }
                    if (house.isOwner) {
                        out.writeByte(0)
                    }
                }

                is HouseEntry.MoveOut -> {
                    out.writeTibiaString(house.ownerName)
                    out.writeIntLE(house.paidUntil.toInt())
                    out.writeByte(if (house.isOwner) 0x01 else 0x00)
                    if (house.isOwner) {
                        out.writeByte(0)
                        out.writeByte(0)
                        out.writeIntLE(house.bidEndDate.toInt())
                        out.writeByte(0)
                    } else {
                        out.writeIntLE(house.bidEndDate.toInt())
                    }
                }
            }
        }
    }

    private fun writeItemList(out: ByteBuf, items: List<ItemSummaryEntry>) {
        out.writeShortLE(items.size)
        for (entry in items) {
            out.writeShortLE(entry.itemId)
            entry.tier?.let { out.writeByte(it) }
            out.writeIntLE(entry.count.toInt())
        }
    }

    private fun writeWeapon(out: ByteBuf, weapon: CyclopediaWeapon) {
        when (weapon) {
            is CyclopediaWeapon.Wand -> {
                out.writeShortLE(weapon.maxHitChance)
                out.writeShortLE(0)
                out.writeShortLE(0)
                out.writeByte(0x00)
                out.writeShortLE(0)
                out.writeShortLE(0)
                out.writeByte(weapon.element)
                out.writeCanaryDouble(0.0)
                out.writeByte(0x00)
                out.writeByte(0x00)
            }

            is CyclopediaWeapon.Distance -> {
                out.writeShortLE(weapon.attackTotal)
                out.writeShortLE(weapon.flatBonus)
                out.writeShortLE(weapon.attackValue)
                out.writeByte(0x07)
                out.writeShortLE(weapon.attackSkill)
                out.writeShortLE(weapon.attackTotal - weapon.attackRawTotal)
                out.writeByte(0x00)
                out.writeCanaryDouble(weapon.convertedDamage)
                out.writeByte(weapon.convertedElement)
                out.writeByte(weapon.accuracyPercent.size)
                for ((index, accuracy) in weapon.accuracyPercent.withIndex()) {
                    out.writeByte(index + 1)
                    out.writeCanaryDouble(accuracy / 100.0)
                }
            }

            is CyclopediaWeapon.Melee -> {
                out.writeShortLE(weapon.attackTotal)
                out.writeShortLE(weapon.flatBonus)
                out.writeShortLE(weapon.weaponAttack)
                out.writeByte(weapon.skillId)
                out.writeShortLE(weapon.attackSkill)
                out.writeShortLE(weapon.attackTotal - weapon.attackRawTotal)
                out.writeByte(0x00)
                out.writeCanaryDouble(weapon.convertedDamage)
                out.writeByte(weapon.convertedElement)
                out.writeByte(0x00)
            }

            is CyclopediaWeapon.Fist -> {
                out.writeShortLE(weapon.attackTotal)
                out.writeShortLE(weapon.flatBonus)
                out.writeShortLE(weapon.attackValue)
                out.writeByte(weapon.skillId)
                out.writeShortLE(weapon.attackSkill)
                out.writeShortLE(weapon.attackTotal - weapon.attackRawTotal)
                out.writeByte(0x00)
                out.writeCanaryDouble(0.0)
                out.writeByte(0x00)
                out.writeByte(0x00)
            }
        }
    }

    private fun writeSkillPercentage(out: ByteBuf, skill: SkillPercentage?) {
        out.writeByte(if (skill != null) 0x01 else 0x00)
        skill?.let {
            out.writeByte(it.skillId)
            out.writeCanaryDouble(it.percent)
            out.writeCanaryDouble(it.applied)
        }
    }

    private fun writeActiveEffect(out: ByteBuf, effect: ActiveEffect) {
        out.writeShortLE(effect.id)
        out.writeByte(0x00)
        out.writeByte(0x00)
        out.writeIntLE(effect.duration.toInt())
    }

    private fun writeAugments(out: ByteBuf, augments: List<SpellAugment>) {
        out.writeByte(augments.size)
        for (augment in augments) {
            out.writeShortLE(augment.spellId)
            out.writeByte(augment.type)
            out.writeCanaryDouble(augment.value)
        }
    }

    /**
     * `NetworkMessage::addDouble` (`src/server/network/message/networkmessage.cpp:174`):
     * precision byte plus `(value * 10^precision) + INT32_MAX` as `uint32_t`.
     */
    private fun ByteBuf.writeCanaryDouble(value: Double, precision: Int = 4) {
        writeByte(precision)
        val scaled = value * 10.0.pow(precision) + Int.MAX_VALUE
        writeIntLE(scaled.toLong().toInt())
    }
}
