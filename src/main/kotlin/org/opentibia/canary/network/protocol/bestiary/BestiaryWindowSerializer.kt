package org.opentibia.canary.network.protocol.bestiary

import io.netty.buffer.ByteBuf
import kotlin.math.pow
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.systems.bestiary.BestiaryCreatures
import org.opentibia.canary.systems.bestiary.BestiaryMonsterData
import org.opentibia.canary.systems.bestiary.BestiaryRaceSummary
import org.opentibia.canary.systems.bestiary.CharmRune
import org.opentibia.canary.systems.bestiary.IOBestiary

/**
 * One `MonsterType::info.lootItems` entry (src/creatures/creatures_definitions.hpp:1765)
 * as far as the 0xD7 frame needs it: item id, display name, `countmax` and the
 * drop `chance` that feeds `IOBestiary::calculateDifficult`.
 */
data class BestiaryLootEntry(
    val id: Int,
    val name: String = "",
    val countmax: Int = 0,
    val chance: Int = 0,
)

/**
 * The full 0xD7 payload: the application-resolved [BestiaryMonsterData] plus the
 * `MonsterType` fields the bestiary result DTO does not carry. Defaults
 * reproduce the Canary `MonsterInfo` defaults for a plain melee hostile monster
 * with no loot display, no elements and zero mitigation.
 */
data class BestiaryMonsterDataFrame(
    val data: BestiaryMonsterData,
    val animusBonus: Int = 0,
    val animusPoints: Int = 0,
    val lootItems: List<BestiaryLootEntry> = emptyList(),
    val isHostile: Boolean = true,
    val targetDistance: Boolean = false,
    val healthMax: Int = 0,
    val experience: Int = 0,
    val baseSpeed: Int = 0,
    val armor: Int = 0,
    val mitigation: Double = 0.0,
    val bestiaryLocations: String = "",
)

/**
 * One `g_game().getCharmList()` entry as far as the 0xD8 frame needs it:
 * [unlocked] is `IOBestiary::hasCharmUnlockedRuneBit`, [tier] is
 * `Player::getCharmTier` and [raceId] is `Player::parseRacebyCharm`.
 */
data class BestiaryCharmEntry(
    val charm: CharmRune,
    val unlocked: Boolean = false,
    val tier: Int = 0,
    val raceId: Int = 0,
)

/**
 * The full 0xD8 payload: player level/premium/expansion, the registered charms
 * in registration order, the runes currently in use and the stage-two monsters
 * from `IOBestiary::getBestiaryStageTwo`.
 */
data class BestiaryCharmsFrame(
    val level: Int,
    val isPremium: Boolean = false,
    val hasCharmExpansion: Boolean = false,
    val charms: List<BestiaryCharmEntry> = emptyList(),
    val usedCharmRunes: List<CharmRune> = emptyList(),
    val stageTwoMonsters: List<Int> = emptyList(),
)

/**
 * Outbound bestiary serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *  - `ProtocolGame::parseBestiarySendRaces`   (0xD5, protocolgame.cpp:3472-3502)
 *  - `ProtocolGame::parseBestiarySendCreatures` (0xD6, protocolgame.cpp:4164-4239)
 *  - `ProtocolGame::parseBestiarysendMonsterData` (0xD7, protocolgame.cpp:3515-3632)
 *  - `ProtocolGame::sendBestiaryCharms`       (0xD8, protocolgame.cpp:4081-4162)
 *  - `ProtocolGame::sendBestiaryEntryChanged` (0xD9, protocolgame.cpp:3504-3513)
 *
 * Every multi-byte field is Little-Endian, matching `NetworkMessage::add<T>()`
 * (`std::bit_cast` into the native wire buffer) and `NetworkMessage::addString`
 * (uint16 length + UTF-8 bytes). A `null` payload mirrors Canary's silent early
 * return: no frame is written at all.
 *
 * Canary calls `Player::sendBestiaryCharms` right after `parseBestiarySendRaces`
 * and after a successful charm-rune purchase; wiring that follow-up frame is the
 * caller's responsibility, so each function here writes exactly one frame.
 */
object BestiaryWindowSerializer {

    const val PACKET_BESTIARY_RACES: Int = 0xD5
    const val PACKET_BESTIARY_CREATURES: Int = 0xD6
    const val PACKET_BESTIARY_MONSTER_DATA: Int = 0xD7
    const val PACKET_BESTIARY_CHARMS: Int = 0xD8
    const val PACKET_BESTIARY_ENTRY_CHANGED: Int = 0xD9

    /**
     * protocolgame.cpp:3472-3502.
     *
     * Canary writes the literal `BESTY_RACE_LAST` (21) as the count and then
     * iterates the 21 fixed race ids; the application always supplies exactly
     * those 21 summaries, so the supplied count is byte-identical while keeping
     * frame and payload consistent.
     */
    fun writeBestiaryRaces(out: ByteBuf, races: List<BestiaryRaceSummary>?) {
        if (races == null) {
            return
        }

        out.writeByte(PACKET_BESTIARY_RACES)
        out.writeShortLE(races.size)
        for (summary in races) {
            out.writeTibiaString(summary.bestiaryClass)
            out.writeShortLE(summary.monsterCount)
            out.writeShortLE(summary.unlockedCount)
        }
    }

    /** protocolgame.cpp:3504-3513: opcode plus the `uint16_t` race id. */
    fun writeBestiaryEntryChanged(out: ByteBuf, raceId: Int) {
        out.writeByte(PACKET_BESTIARY_ENTRY_CHANGED)
        out.writeShortLE(raceId)
    }

    /**
     * protocolgame.cpp:4164-4239.
     *
     * Canary iterates its race-id keyed `std::map`, so entries are sorted by
     * race id here. A creature with progress zero writes a single zero byte and
     * no occurrence; the animus bonus is written for every creature (zero when
     * [animusBonus] has none) and [animusPoints] is the trailing `uint16_t`.
     *
     * [animusBonus] corresponds to `AnimusMastery::getExperienceMultiplier` /
     * `has`, which are not part of this port; callers without animus state keep
     * the default zero.
     */
    fun writeBestiaryCreatures(
        out: ByteBuf,
        creatures: BestiaryCreatures?,
        animusBonus: (String) -> Int = { 0 },
        animusPoints: Int = 0,
    ) {
        if (creatures == null) {
            return
        }

        out.writeByte(PACKET_BESTIARY_CREATURES)
        out.writeTibiaString(creatures.searchText)

        val rows = creatures.creatures.sortedBy { it.raceId }
        out.writeShortLE(rows.size)
        for (creature in rows) {
            out.writeShortLE(creature.raceId)
            if (creature.currentLevel > 0) {
                out.writeByte(creature.currentLevel)
                out.writeByte(creature.occurrence)
            } else {
                out.writeByte(0)
            }
            out.writeShortLE(animusBonus(creature.name))
        }
        out.writeShortLE(animusPoints)
    }

    /**
     * protocolgame.cpp:3515-3632.
     *
     * Loot visibility keeps Canary's `SHOW_LOOTS_IN_BESTIARY || shouldAddItem`
     * gate, where `shouldAddItem` depends on the kill-status level, and hidden
     * loot still writes a zero item id plus its difficulty byte. The level-two
     * stat block and the level-three element block are appended only when the
     * resolved [BestiaryMonsterData.currentLevel] exceeds one, respectively two.
     *
     * [bestiary] supplies `calculateDifficult` and `getMonsterElements`
     * (`src/io/iobestiary.cpp`): callers without an engine instance get the
     * stateless defaults. [showLootsInBestiary] is the
     * `SHOW_LOOTS_IN_BESTIARY` configuration value.
     */
    fun writeBestiaryMonsterData(
        out: ByteBuf,
        frame: BestiaryMonsterDataFrame?,
        bestiary: IOBestiary = IOBestiary(),
        showLootsInBestiary: Boolean = false,
    ) {
        if (frame == null) {
            return
        }

        val data = frame.data
        val mtype = data.monster

        out.writeByte(PACKET_BESTIARY_MONSTER_DATA)
        out.writeShortLE(data.raceId)
        out.writeTibiaString(data.bestiaryClass)
        out.writeByte(data.currentLevel)

        out.writeShortLE(frame.animusBonus)
        out.writeShortLE(frame.animusPoints)

        out.writeIntLE(data.killCount)
        out.writeShortLE(mtype.bestiaryFirstUnlock)
        out.writeShortLE(mtype.bestiarySecondUnlock)
        out.writeShortLE(mtype.bestiaryToUnlock)
        out.writeByte(mtype.bestiaryStars)
        out.writeByte(mtype.bestiaryOccurrence)

        out.writeByte(frame.lootItems.size)
        for (loot in frame.lootItems) {
            val difficult = bestiary.calculateDifficult(loot.chance)
            val shouldAddItem = when (data.currentLevel) {
                1 -> false
                2 -> difficult < 2
                3 -> difficult < 3
                4 -> true
                else -> false
            }
            val showLoot = showLootsInBestiary || shouldAddItem

            out.writeShortLE(if (showLoot) loot.id else 0)
            out.writeByte(difficult)
            out.writeByte(0) // 1 if special event - 0 if regular loot (?)
            if (showLoot) {
                out.writeTibiaString(loot.name)
                out.writeByte(if (loot.countmax > 0) 0x01 else 0x00)
            }
        }

        if (data.currentLevel > 1) {
            out.writeShortLE(mtype.bestiaryCharmsPoints)

            var attackMode = 0
            if (!frame.isHostile) {
                attackMode = 2
            } else if (frame.targetDistance) {
                attackMode = 1
            }
            out.writeByte(attackMode)
            out.writeByte(0x02)
            out.writeIntLE(frame.healthMax)
            out.writeIntLE(frame.experience)
            out.writeShortLE(frame.baseSpeed)
            out.writeShortLE(frame.armor)
            out.writeCanaryDouble(frame.mitigation)
        }

        if (data.currentLevel > 2) {
            val elements = bestiary.getMonsterElements(mtype)
            out.writeByte(elements.size)
            for ((slot, value) in elements) {
                out.writeByte(slot)
                out.writeShortLE(value)
            }

            out.writeShortLE(1)
            out.writeTibiaString(frame.bestiaryLocations)
        }
    }

    /**
     * protocolgame.cpp:4081-4162.
     *
     * The reset-all and remove-rune costs reproduce the level/expansion math
     * inline, including the `0xFF` all-slots marker for premium players with the
     * charm expansion. A stage-two monster drops out of the frame when two used
     * runes point at it; the surviving race ids are written as `uint32_t`, which
     * is the width Canary passes to `msg.add` at protocolgame.cpp:4152.
     *
     * Canary follows the frame with `sendCharmResourcesBalance`
     * (4 x 0xEE, protocolgame.cpp:4156-4161); that separate frame is not part of
     * this serializer.
     */
    fun writeBestiaryCharms(out: ByteBuf, frame: BestiaryCharmsFrame) {
        val level = frame.level
        var resetAllCharmsCost = 100000L + if (level > 100) level.toLong() * 11000L else 0L
        if (frame.hasCharmExpansion) {
            resetAllCharmsCost = resetAllCharmsCost * 75 / 100
        }

        out.writeByte(PACKET_BESTIARY_CHARMS)
        out.writeLongLE(resetAllCharmsCost)

        out.writeByte(frame.charms.size)
        for (entry in frame.charms) {
            out.writeByte(entry.charm.value)
            if (!entry.unlocked) {
                out.writeByte(0x00)
                out.writeByte(0x00)
                continue
            }

            out.writeByte(entry.tier)
            if (entry.raceId <= 0) {
                out.writeByte(0x00)
                continue
            }

            out.writeByte(0x01)
            out.writeShortLE(entry.raceId)
            var removeCharmCost = level.toLong() * 100L
            if (frame.hasCharmExpansion) {
                removeCharmCost = removeCharmCost * 75 / 100
            }
            out.writeIntLE(removeCharmCost.toInt())
        }

        val availableCharmSlots = if (frame.isPremium && frame.hasCharmExpansion) {
            0xFF
        } else {
            (if (frame.isPremium) 6 else 2) - frame.usedCharmRunes.size
        }
        out.writeByte(availableCharmSlots)

        val raceByCharm = frame.charms.associate { it.charm.value to it.raceId }
        val assigned = HashMap<Int, Int>()
        for (rune in frame.usedCharmRunes) {
            val raceId = raceByCharm[rune.value] ?: continue
            assigned[raceId] = (assigned[raceId] ?: 0) + 1
        }

        val stageTwo = frame.stageTwoMonsters.filter { (assigned[it] ?: 0) < 2 }
        out.writeShortLE(stageTwo.size)
        for (raceId in stageTwo) {
            out.writeIntLE(raceId)
        }
    }

    /**
     * `NetworkMessage::addDouble` (networkmessage.cpp:174-177): a precision byte
     * followed by `(value * 10^precision) + INT32_MAX` as a `uint32_t`.
     */
    private fun ByteBuf.writeCanaryDouble(value: Double, precision: Int = 4) {
        writeByte(precision)
        val scale = 10.0.pow(precision)
        writeIntLE((value * scale + Int.MAX_VALUE).toInt())
    }
}
