package org.opentibia.canary.network.protocol.misc

import io.netty.buffer.ByteBuf
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.MonkData
import org.opentibia.canary.creatures.Virtue_t
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.cyclopedia.CipbiaElement

/**
 * Outbound miscellaneous data/analyzer frames for the Canary
 * `ProtocolProfileId::Current` profile (`protocol_new` / 15.x:
 * `oldProtocol == false`), ported from
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendBasicData`            (0x9F, protocolgame.cpp:5823-5890)
 *  - `ProtocolGame::sendItemsPrice`           (0xCD, protocolgame.cpp:10801-10829)
 *  - `ProtocolGame::sendExivaRestrictions`    (0xCA, protocolgame.cpp:12252-12312)
 *  - `ProtocolGame::sendTakeScreenshot`       (0x75, protocolgame.cpp:11728-11805)
 *  - `ProtocolGame::sendMonkData`             (0xC1, protocolgame.cpp:12147-12192)
 *  - `ProtocolGame::sendUpdateSupplyTracker`  (0xCE, protocolgame.cpp:10427-10437)
 *  - `ProtocolGame::sendUpdateImpactTracker`  (0xCC, protocolgame.cpp:10439-10460)
 *  - `ProtocolGame::sendUpdateInputAnalyzer`  (0xCC, protocolgame.cpp:10462-10484)
 *
 * The `!player`, `oldProtocol`, `version` and protocol-feature gates stay with
 * the caller. The current profile always carries `GameEventPayload`,
 * `OfficialVocationSpecificPlayerData`, `CustomMonkPackets` and
 * `GraphicalEffectSourceByte`, so the new-wire branches are the only ones
 * modelled. Every multi-byte field is Little-Endian.
 */
object MiscDataWriter {

    const val PACKET_BASIC_DATA: Int = 0x9F
    const val PACKET_ITEMS_PRICE: Int = 0xCD
    const val PACKET_EXIVA_RESTRICTIONS: Int = 0xCA
    const val PACKET_TAKE_SCREENSHOT: Int = 0x75
    const val PACKET_VOCATION_SPECIFIC_PLAYER_DATA: Int = 0xC1
    const val PACKET_UPDATE_SUPPLY_TRACKER: Int = 0xCE
    const val PACKET_UPDATE_ANALYZER: Int = 0xCC

    /** `sendBasicData` premium byte (protocolgame.cpp:5835-5841). */
    private const val BASIC_DATA_PREMIUM: Int = 1
    private const val BASIC_DATA_FREE: Int = 0

    /** `sendBasicData` spell-list entry replaced by `0x0000` (protocolgame.cpp:5871-5883). */
    private const val BASIC_DATA_HIDDEN_SPELL: Int = 0

    /** `sendBasicData` premium expiry factor `86400` seconds/day (protocolgame.cpp:5834). */
    private const val SECONDS_PER_DAY: Long = 86400

    /** `sendTakeScreenshot` `max(level, 0xFFFF)` clamp (protocolgame.cpp:11761). */
    private const val MAX_U16: Int = 0xFFFF

    /** `Analyzer_t` selectors (server_definitions.hpp:118-120). */
    private const val ANALYZER_HEAL: Int = 0
    private const val ANALYZER_DAMAGE_DEALT: Int = 1
    private const val ANALYZER_DAMAGE_RECEIVED: Int = 2

    /** `sendTakeScreenshot` `GameEventPayload` event selectors (protocolgame.cpp:11736-11797). */
    private const val SCREENSHOT_EVENT_BOSS_DEFEATED: Int = 0x01
    private const val SCREENSHOT_EVENT_DEATH_PVE: Int = 0x02
    private const val SCREENSHOT_EVENT_DEATH_PVP: Int = 0x03
    private const val SCREENSHOT_EVENT_PLAYER_KILL_ASSIST: Int = 0x04
    private const val SCREENSHOT_EVENT_PLAYER_KILL: Int = 0x05
    private const val SCREENSHOT_EVENT_PLAYER_ATTACKING: Int = 0x06
    private const val SCREENSHOT_EVENT_TREASURE_FOUND: Int = 0x07
    private const val SCREENSHOT_EVENT_GIFT_OF_LIFE: Int = 0x08
    private const val SCREENSHOT_SELECTOR_BOSS: Int = 0x01
    private const val SCREENSHOT_SELECTOR_ACHIEVEMENT: Int = 0x02
    private const val SCREENSHOT_SELECTOR_LEVEL_UP: Int = 0x04
    private const val SCREENSHOT_SELECTOR_SKILL_UP: Int = 0x05
    private const val SCREENSHOT_SELECTOR_BESTIARY: Int = 0x06

    /** `MonkData_t` virtue spell ids (protocolgame.cpp:12162-12173). */
    private const val VIRTUE_SPELL_HARMONY: Int = 274
    private const val VIRTUE_SPELL_JUSTICE: Int = 275
    private const val VIRTUE_SPELL_SUSTAIN: Int = 276

    /** `Screenshot_t` (`src/utils/utils_definitions.hpp:810-825`). */
    enum class ScreenshotType(val value: Int) {
        None(0),
        Achievement(1),
        BestiaryEntryCompleted(2),
        BestiaryEntryUnlocked(3),
        BossDefeated(4),
        DeathPve(5),
        DeathPvp(6),
        LevelUp(7),
        PlayerKillAssist(8),
        PlayerKill(9),
        PlayerAttacking(10),
        TreasureFound(11),
        SkillUp(12),
        GiftOfLife(13),
    }

    /**
     * One entry of `ProtocolGame::sendBasicData`'s valid spell list. The caller
     * filters `g_spells().getSpellsByVocation(vocationId)` down to spells with
     * `spellId > 0` (protocolgame.cpp:5851-5858); [learnable]/[learned]/
     * [wheelGrade] drive the `0x0000` substitution at protocolgame.cpp:5871-5883.
     */
    data class BasicDataSpell(
        val spellId: Int,
        val learnable: Boolean = false,
        val learned: Boolean = false,
        val wheelGrade: Int = 0,
    )

    /**
     * Inputs of `ProtocolGame::sendBasicData` (protocolgame.cpp:5823-5890).
     * [premium] is `player->isPremium() || player->isVip()`, [preyWindowUnlocked]
     * is the negation of the `vocationId == 0 && group < GROUP_TYPE_GAMEMASTER`
     * check, and [magicShield] is `vocation->getMagicShield()`.
     */
    data class BasicDataFrame(
        val premium: Boolean,
        val premiumDays: Int,
        val nowSeconds: Long,
        val vocationClientId: Int,
        val preyWindowUnlocked: Boolean,
        val spells: List<BasicDataSpell>,
        val magicShield: Boolean,
    )

    /**
     * One `g_game().getItemsPrice()` entry. [tier] is `null` when the item's
     * `upgradeClassification == 0` and the tier byte is skipped
     * (protocolgame.cpp:10815-10817).
     */
    data class ItemPriceEntry(
        val itemId: Int,
        val tier: Int?,
        val price: Long,
    )

    /** `ExivaRestrictions` flag bytes (`player.hpp`, protocolgame.cpp:12261-12267). */
    data class ExivaRestrictionFlags(
        val allowAll: Int,
        val allowOwnGuild: Int,
        val allowOwnParty: Int,
        val allowVipList: Int,
        val allowPlayerWhitelist: Int,
        val allowGuildWhitelist: Int,
    )

    /** `sendBasicData` (protocolgame.cpp:5823-5890). */
    fun writeBasicData(out: ByteBuf, frame: BasicDataFrame) {
        out.writeByte(PACKET_BASIC_DATA)
        if (frame.premium) {
            out.writeByte(BASIC_DATA_PREMIUM)
            val expiry = frame.nowSeconds + (frame.premiumDays + 1) * SECONDS_PER_DAY
            out.writeIntLE(expiry.toInt())
        } else {
            out.writeByte(BASIC_DATA_FREE)
            out.writeIntLE(0)
        }
        out.writeByte(frame.vocationClientId)
        out.writeByte(if (frame.preyWindowUnlocked) 1 else 0)
        out.writeShortLE(frame.spells.size)
        for (spell in frame.spells) {
            val wireSpellId = when {
                !spell.learnable -> spell.spellId
                !spell.learned -> BASIC_DATA_HIDDEN_SPELL
                spell.wheelGrade == 0 -> BASIC_DATA_HIDDEN_SPELL
                else -> spell.spellId
            }
            out.writeShortLE(wireSpellId)
        }
        out.writeByte(if (frame.magicShield) 1 else 0)
    }

    /** `sendItemsPrice` (protocolgame.cpp:10801-10829). */
    fun writeItemsPrice(out: ByteBuf, entries: List<ItemPriceEntry>) {
        out.writeByte(PACKET_ITEMS_PRICE)
        out.writeShortLE(entries.size)
        for (entry in entries) {
            out.writeShortLE(entry.itemId)
            entry.tier?.let { out.writeByte(it) }
            out.writeLongLE(entry.price)
        }
    }

    /**
     * `sendExivaRestrictions` (protocolgame.cpp:12252-12312). In login mode the
     * caller resolves the stored guid whitelists into [playerWhitelistNames] /
     * [guildWhitelistNames] and both blocks are terminated with a `0x0000`
     * count; update mode sends the added/removed name lists instead.
     */
    fun writeExivaRestrictions(
        out: ByteBuf,
        flags: ExivaRestrictionFlags,
        isLogin: Boolean = false,
        addedPlayerNames: List<String> = emptyList(),
        removedPlayerNames: List<String> = emptyList(),
        addedGuildNames: List<String> = emptyList(),
        removedGuildNames: List<String> = emptyList(),
        playerWhitelistNames: List<String> = emptyList(),
        guildWhitelistNames: List<String> = emptyList(),
    ) {
        out.writeByte(PACKET_EXIVA_RESTRICTIONS)
        out.writeByte(flags.allowAll)
        out.writeByte(flags.allowOwnGuild)
        out.writeByte(flags.allowOwnParty)
        out.writeByte(flags.allowVipList)
        out.writeByte(flags.allowPlayerWhitelist)
        out.writeByte(flags.allowGuildWhitelist)

        if (isLogin) {
            out.writeShortLE(playerWhitelistNames.size)
            for (name in playerWhitelistNames) {
                out.writeTibiaString(name)
            }
            out.writeShortLE(0x00)
        } else {
            out.writeShortLE(addedPlayerNames.size)
            for (name in addedPlayerNames) {
                out.writeTibiaString(name)
            }
            out.writeShortLE(removedPlayerNames.size)
            for (name in removedPlayerNames) {
                out.writeTibiaString(name)
            }
        }

        if (isLogin) {
            out.writeShortLE(guildWhitelistNames.size)
            for (name in guildWhitelistNames) {
                out.writeTibiaString(name)
            }
            out.writeShortLE(0x00)
        } else {
            out.writeShortLE(addedGuildNames.size)
            for (name in addedGuildNames) {
                out.writeTibiaString(name)
            }
            out.writeShortLE(removedGuildNames.size)
            for (name in removedGuildNames) {
                out.writeTibiaString(name)
            }
        }
    }

    /**
     * `sendTakeScreenshot` `GameEventPayload` branch (protocolgame.cpp:11728-11805).
     * The invalid-payload cases (`NONE`, empty achievement name, zero race,
     * zero skill id/level) write nothing, mirroring the C++ early returns before
     * the message is queued. The legacy single-byte branch only applies to
     * profiles without `GameEventPayload`.
     */
    fun writeTakeScreenshot(
        out: ByteBuf,
        screenshotType: ScreenshotType,
        skillId: Int = 0,
        skillLevel: Int = 0,
        achievementName: String = "",
        raceId: Int = 0,
        bestiaryStep: Int = 0,
        playerLevel: Int = 0,
    ) {
        if (screenshotType == ScreenshotType.None) {
            return
        }
        if (screenshotType == ScreenshotType.Achievement && achievementName.isEmpty()) {
            return
        }
        if (
            (screenshotType == ScreenshotType.BestiaryEntryCompleted ||
                screenshotType == ScreenshotType.BestiaryEntryUnlocked) && raceId == 0
        ) {
            return
        }
        if (screenshotType == ScreenshotType.SkillUp && (skillId == 0 || skillLevel == 0)) {
            return
        }

        out.writeByte(PACKET_TAKE_SCREENSHOT)
        when (screenshotType) {
            ScreenshotType.Achievement -> {
                out.writeByte(SCREENSHOT_SELECTOR_ACHIEVEMENT)
                out.writeTibiaString(achievementName)
            }
            ScreenshotType.BestiaryEntryCompleted,
            ScreenshotType.BestiaryEntryUnlocked,
            -> {
                out.writeByte(SCREENSHOT_SELECTOR_BESTIARY)
                out.writeShortLE(raceId)
                out.writeByte(bestiaryStep)
            }
            ScreenshotType.LevelUp -> {
                out.writeByte(SCREENSHOT_SELECTOR_LEVEL_UP)
                out.writeShortLE(minOf(playerLevel, MAX_U16))
            }
            ScreenshotType.SkillUp -> {
                out.writeByte(SCREENSHOT_SELECTOR_SKILL_UP)
                out.writeByte(skillId)
                out.writeShortLE(skillLevel)
            }
            ScreenshotType.BossDefeated ->
                writeScreenshotEvent(out, SCREENSHOT_SELECTOR_BOSS, SCREENSHOT_EVENT_BOSS_DEFEATED)
            ScreenshotType.DeathPve ->
                writeScreenshotEvent(out, SCREENSHOT_SELECTOR_BOSS, SCREENSHOT_EVENT_DEATH_PVE)
            ScreenshotType.DeathPvp ->
                writeScreenshotEvent(out, SCREENSHOT_SELECTOR_BOSS, SCREENSHOT_EVENT_DEATH_PVP)
            ScreenshotType.PlayerKillAssist ->
                writeScreenshotEvent(out, SCREENSHOT_SELECTOR_BOSS, SCREENSHOT_EVENT_PLAYER_KILL_ASSIST)
            ScreenshotType.PlayerKill ->
                writeScreenshotEvent(out, SCREENSHOT_SELECTOR_BOSS, SCREENSHOT_EVENT_PLAYER_KILL)
            ScreenshotType.PlayerAttacking ->
                writeScreenshotEvent(out, SCREENSHOT_SELECTOR_BOSS, SCREENSHOT_EVENT_PLAYER_ATTACKING)
            ScreenshotType.TreasureFound ->
                writeScreenshotEvent(out, SCREENSHOT_SELECTOR_BOSS, SCREENSHOT_EVENT_TREASURE_FOUND)
            ScreenshotType.GiftOfLife ->
                writeScreenshotEvent(out, SCREENSHOT_SELECTOR_BOSS, SCREENSHOT_EVENT_GIFT_OF_LIFE)
            ScreenshotType.None -> return
        }
    }

    /**
     * `sendMonkData` `OfficialVocationSpecificPlayerData` branch
     * (protocolgame.cpp:12147-12192). The `CustomMonkPackets`-only raw-value
     * fallback does not apply to the current profile.
     */
    fun writeMonkData(out: ByteBuf, type: MonkData, value: Int) {
        out.writeByte(PACKET_VOCATION_SPECIFIC_PLAYER_DATA)
        out.writeByte(type.value)
        when (type) {
            MonkData.Harmony -> out.writeByte(value)
            MonkData.Serenity -> out.writeByte(if (value != 0) 0x01 else 0x00)
            MonkData.Virtue -> {
                val spellId = when (Virtue_t.entries.firstOrNull { it.value == value }) {
                    Virtue_t.Harmony -> VIRTUE_SPELL_HARMONY
                    Virtue_t.Justice -> VIRTUE_SPELL_JUSTICE
                    Virtue_t.Sustain -> VIRTUE_SPELL_SUSTAIN
                    else -> 0
                }
                out.writeByte(if (spellId != 0) 0x01 else 0x00)
                if (spellId != 0) {
                    out.writeShortLE(spellId)
                }
            }
        }
    }

    /** `sendUpdateSupplyTracker` (protocolgame.cpp:10427-10437). */
    fun writeUpdateSupplyTracker(out: ByteBuf, itemId: Int) {
        out.writeByte(PACKET_UPDATE_SUPPLY_TRACKER)
        out.writeShortLE(itemId)
    }

    /**
     * `sendUpdateImpactTracker` (protocolgame.cpp:10439-10460). Unmapped combat
     * types (`CIPBIA_ELEMENTAL_UNDEFINED`) write nothing.
     */
    fun writeUpdateImpactTracker(out: ByteBuf, type: CombatType, amount: Int) {
        val clientElement = CipbiaElement.fromCombatType(type)
        if (clientElement == CipbiaElement.UNDEFINED) {
            return
        }

        out.writeByte(PACKET_UPDATE_ANALYZER)
        if (type == CombatType.HEALING) {
            out.writeByte(ANALYZER_HEAL)
            out.writeIntLE(amount)
        } else {
            out.writeByte(ANALYZER_DAMAGE_DEALT)
            out.writeIntLE(amount)
            out.writeByte(clientElement)
        }
    }

    /**
     * `sendUpdateInputAnalyzer` (protocolgame.cpp:10462-10484). Unmapped combat
     * types (`CIPBIA_ELEMENTAL_UNDEFINED`) write nothing.
     */
    fun writeUpdateInputAnalyzer(out: ByteBuf, type: CombatType, amount: Int, target: String) {
        val clientElement = CipbiaElement.fromCombatType(type)
        if (clientElement == CipbiaElement.UNDEFINED) {
            return
        }

        out.writeByte(PACKET_UPDATE_ANALYZER)
        out.writeByte(ANALYZER_DAMAGE_RECEIVED)
        out.writeIntLE(amount)
        out.writeByte(clientElement)
        out.writeTibiaString(target)
    }

    private fun writeScreenshotEvent(out: ByteBuf, selector: Int, event: Int) {
        out.writeByte(selector)
        out.writeByte(event)
    }
}
