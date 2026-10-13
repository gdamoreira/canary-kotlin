package org.opentibia.canary.network.protocol.wheel

import io.netty.buffer.ByteBuf
import org.opentibia.canary.systems.wheel.PlayerWheel
import org.opentibia.canary.systems.wheel.PlayerWheelGem
import org.opentibia.canary.systems.wheel.WheelGemBasicModifier
import org.opentibia.canary.systems.wheel.WheelGemQuality
import org.opentibia.canary.systems.wheel.WheelGemSupremeModifier
import org.opentibia.canary.systems.wheel.WheelGems
import org.opentibia.canary.systems.wheel.WheelSlots

/**
 * Mirror of the Canary `PromotionScroll` struct
 * (src/creatures/players/components/wheel/player_wheel.hpp:71). Only [itemId]
 * and [extraPoints] reach the wire; [name] is the KV identity.
 */
data class PromotionScroll(
    val itemId: Int,
    val name: String = "",
    val extraPoints: Int = 0,
)

/**
 * Player state required by the wheel window that the deterministic [PlayerWheel]
 * engine model does not own. Canary resolves these from the live player and
 * protocol session before serializing:
 *  - [ownerId] and [options]: `ProtocolGame::sendOpenWheelWindow(ownerId)` and
 *    `PlayerWheel::getOptions(ownerId)` (player_wheel.cpp:2064).
 *  - [canUse]: `PlayerWheel::canOpenWheel()` (player_wheel.cpp:2033), which
 *    checks premium/promotion/level/vocation.
 *  - [promotionScrolls]: `PlayerWheel::m_unlockedScrolls` (player_wheel.cpp:1032).
 *  - [completedMonkQuest]/[monkQuestBonus]: `hasCompletedMonkQuest()`
 *    (player_wheel.cpp:2055) and `WHEEL_MONK_QUEST_BONUS` config.
 *  - [officialSkillWheelPayload]: the session's
 *    `ProtocolFeature::OfficialSkillWheelPayload` (15.25 quest-flag + u16
 *    layout); `false` emits the legacy single clamped byte.
 */
data class WheelWindow(
    val ownerId: Long,
    val canUse: Boolean,
    val options: Int,
    val promotionScrolls: List<PromotionScroll> = emptyList(),
    val completedMonkQuest: Boolean = false,
    val monkQuestBonus: Int = 0,
    val officialSkillWheelPayload: Boolean = false,
)

/**
 * Outbound Wheel of Destiny window serialization, ported from Canary
 * src/creatures/players/components/wheel/player_wheel.cpp:
 *  - `PlayerWheel::sendOpenWheelWindow` (opcode 0x5F, player_wheel.cpp:1534)
 *  - `PlayerWheel::addPromotionScrolls`  (player_wheel.cpp:1032)
 *  - `PlayerWheel::addGems`              (player_wheel.cpp:1395)
 *  - `PlayerWheel::addGradeModifiers`    (player_wheel.cpp:1423)
 *
 * Byte order is Little-Endian (`writeShortLE`) per AGENTS.md 3. The window is
 * followed in Canary by individual `sendResourceBalance` frames
 * (player_wheel.cpp:1581-1587); those are separate opcodes and not part of this
 * frame. The preceding `addInitialGems` KV seeding (player_wheel.cpp:1550) is a
 * state mutation with no wire bytes and is likewise not modelled here.
 */
object WheelWindowSerializer {

    const val PACKET_OPEN_WHEEL: Int = 0x5F

    /** `PlayerWheel::getExtraPoints` returns 0 below level 51 (player_wheel.cpp:1958). */
    private const val MIN_LEVEL_FOR_EXTRA_POINTS = 51

    private const val GRADE_MODIFIERS_BASIC_MARKER = 0x2E
    private const val GRADE_MODIFIERS_SUPREME_MARKER = 0x17

    /** `PlayerWheel::getGemIndex` lookup-failure result (player_wheel.cpp:1209). */
    private const val GEM_INDEX_NOT_FOUND = 0xFF

    /**
     * Serializes the wheel window into [out]. When `window.canUse` is false only
     * the opcode, owner id and lock byte are written (player_wheel.cpp:1546).
     */
    fun writeOpenWheelWindow(out: ByteBuf, wheel: PlayerWheel, window: WheelWindow) {
        out.writeByte(PACKET_OPEN_WHEEL)
        out.writeIntLE(window.ownerId.toInt())
        out.writeByte(if (window.canUse) 0x01 else 0x00)
        if (!window.canUse) {
            return
        }

        out.writeByte(window.options and 0xFF)
        out.writeByte(wheel.vocationCipId and 0xFF)

        out.writeShortLE(wheel.getWheelPoints(includeExtraPoints = false))
        out.writeShortLE(getExtraPoints(wheel))

        for (slot in WheelSlots.entries) {
            out.writeShortLE(wheel.getPointsBySlotType(slot))
        }

        writePromotionScrolls(out, window.promotionScrolls)
        writeMonkQuestBonus(out, window)
        writeGems(out, wheel)
        writeGradeModifiers(out, wheel)
    }

    /**
     * `PlayerWheel::getExtraPoints` (player_wheel.cpp:1957) also folds in the
     * task-board multiplier and monk quest bonus; the deterministic model stores
     * the already-resolved total as its constructor `extraPoints`, recoverable
     * as the difference between the inclusive and exclusive point totals.
     */
    private fun getExtraPoints(wheel: PlayerWheel): Int {
        if (wheel.level < MIN_LEVEL_FOR_EXTRA_POINTS) {
            return 0
        }
        return wheel.getWheelPoints(includeExtraPoints = true) -
            wheel.getWheelPoints(includeExtraPoints = false)
    }

    /** player_wheel.cpp:1032-1038. */
    private fun writePromotionScrolls(out: ByteBuf, scrolls: List<PromotionScroll>) {
        out.writeShortLE(scrolls.size and 0xFFFF)
        for (scroll in scrolls) {
            out.writeShortLE(scroll.itemId and 0xFFFF)
            out.writeByte(scroll.extraPoints and 0xFF)
        }
    }

    /** player_wheel.cpp:1560-1568. */
    private fun writeMonkQuestBonus(out: ByteBuf, window: WheelWindow) {
        val bonus = maxOf(0, window.monkQuestBonus)
        val hasMonkQuestBonus = window.completedMonkQuest && bonus > 0

        if (window.officialSkillWheelPayload) {
            out.writeByte(if (hasMonkQuestBonus) 0x01 else 0x00)
            out.writeShortLE(if (hasMonkQuestBonus) minOf(bonus, 0xFFFF) else 0)
        } else {
            out.writeByte(if (hasMonkQuestBonus) minOf(bonus, 0xFF) else 0x00)
        }
    }

    /** player_wheel.cpp:1395-1421. Active gems first, then the revealed list. */
    private fun writeGems(out: ByteBuf, wheel: PlayerWheel) {
        val activeGems = wheel.gems.activeGems.filterNotNull()
        out.writeByte(activeGems.size and 0xFF)
        for (gem in activeGems) {
            out.writeShortLE(gemIndex(wheel, gem))
        }

        val revealedGems = wheel.gems.revealedGems
        out.writeShortLE(revealedGems.size and 0xFFFF)
        for (index in revealedGems.indices) {
            val gem = revealedGems[index]
            out.writeShortLE(index and 0xFFFF)
            out.writeByte(if (gem.locked) 0x01 else 0x00)
            out.writeByte(gem.affinity.value and 0xFF)
            out.writeByte(gem.quality.value and 0xFF)
            out.writeByte(gem.basicModifier1.value and 0xFF)
            if (gem.quality.value >= WheelGemQuality.Regular.value) {
                out.writeByte(basicModifier2(gem).value and 0xFF)
            }
            if (gem.quality.value >= WheelGemQuality.Greater.value) {
                out.writeByte(supremeModifier(gem).value and 0xFF)
            }
        }
    }

    /**
     * `PlayerWheel::getGemIndex(uuid)` (player_wheel.cpp:1202) locates the gem in
     * the revealed list. The Kotlin model has no uuid field, so identity is used;
     * a gem absent from the list reports Canary's `0xFF` lookup failure.
     */
    private fun gemIndex(wheel: PlayerWheel, gem: PlayerWheelGem): Int {
        val index = wheel.gems.revealedGems.indexOfFirst { it === gem }
        return if (index >= 0) index else GEM_INDEX_NOT_FOUND
    }

    /**
     * Canary zero-initializes both enum fields (`{}` yields enum value 0), so an
     * unset optional modifier is written as its zero enum.
     */
    private fun basicModifier2(gem: PlayerWheelGem): WheelGemBasicModifier =
        gem.basicModifier2 ?: WheelGemBasicModifier.General_PhysicalResistance

    private fun supremeModifier(gem: PlayerWheelGem): WheelGemSupremeModifier =
        gem.supremeModifier ?: WheelGemSupremeModifier.General_Dodge

    /** player_wheel.cpp:1423-1446. */
    private fun writeGradeModifiers(out: ByteBuf, wheel: PlayerWheel) {
        out.writeByte(GRADE_MODIFIERS_BASIC_MARKER)
        for (modifier in WheelGems.modsBasicPosition) {
            out.writeByte(modifier.value and 0xFF)
            out.writeByte(wheel.gems.basicGrades[modifier.value] and 0xFF)
        }

        out.writeByte(GRADE_MODIFIERS_SUPREME_MARKER)
        val baseVocationId = baseVocationId(wheel.vocationCipId) ?: return
        val supremeModifiers = WheelGems.supremePositionByVocation[baseVocationId] ?: return
        for (modifier in supremeModifiers) {
            out.writeByte(modifier.value and 0xFF)
            out.writeByte(wheel.gems.supremeGrades[modifier.value] and 0xFF)
        }
    }

    /**
     * `Vocation::getBaseId()` for the CIP ids returned by
     * `Player::getPlayerVocationEnum()` (player.cpp:13030), read from
     * data/XML/vocations.xml `clientid` -> `baseid` and matching the
     * `modsSupremePositionByVocation` keys (player_wheel.cpp:293).
     */
    private fun baseVocationId(vocationCipId: Int): Int? = when (vocationCipId) {
        1 -> 4 // Knight
        2 -> 3 // Paladin
        3 -> 1 // Sorcerer
        4 -> 2 // Druid
        5 -> 9 // Monk
        else -> null
    }
}
