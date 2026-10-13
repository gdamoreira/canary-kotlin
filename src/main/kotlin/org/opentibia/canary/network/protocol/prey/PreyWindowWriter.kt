package org.opentibia.canary.network.protocol.prey

import io.netty.buffer.ByteBuf
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.systems.prey.PreyDataState
import org.opentibia.canary.systems.prey.PreySlot

/**
 * `MonsterType::info` fields `ProtocolGame::sendPreyData` reads for the
 * `Active`/`Selection` grids (`src/creatures/monsters/monsters.hpp:75-154`):
 * the resolved display name and outfit of a race id.
 */
data class PreyMonsterWindow(
    val name: String,
    val outfit: Outfit = Outfit(),
)

/**
 * Outbound prey window serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendPreyTimeLeft` (0xE7, protocolgame.cpp:9470-9482)
 *  - `ProtocolGame::sendPreyData`      (0xE8, protocolgame.cpp:9484-9605)
 *  - `ProtocolGame::sendPreyPrices`    (0xE9, protocolgame.cpp:9607-9628)
 *
 * Every multi-byte field is Little-Endian and strings are `uint16` length plus
 * UTF-8. The C++ `!player`/`!slot` guards stay with the caller; values Canary
 * resolves from live state (monster registry, premium, bank/level price, clock)
 * are passed in, matching how `BestiaryWindowSerializer` structures its inputs.
 *
 * The free-reroll countdown reproduces Canary's exact expression
 * `std::max<uint32_t>(static_cast<uint32_t>((freeRerollTimeStamp - OTSYS_TIME())
 * / 1000), 0)`: the signed difference truncates toward zero and wraps to
 * `uint32_t`, so a past timestamp serializes as the wrapped 32-bit value, not 0.
 */
object PreyWindowWriter {

    const val PACKET_PREY_TIME_LEFT: Int = 0xE7
    const val PACKET_PREY_DATA: Int = 0xE8
    const val PACKET_PREY_PRICES: Int = 0xE9

    /** `sendPreyTimeLeft` (protocolgame.cpp:9470-9482). */
    fun writePreyTimeLeft(out: ByteBuf, slot: PreySlot) {
        out.writeByte(PACKET_PREY_TIME_LEFT)
        out.writeByte(slot.id)
        out.writeShortLE(slot.bonusTimeLeft)
    }

    /**
     * `sendPreyData` (protocolgame.cpp:9484-9605).
     *
     * [monsterLookup] is `g_monsters().getMonsterTypeByRaceId` (monsters.cpp:375)
     * and [bestiaryRaceIds] is `Game::getBestiaryList()` key order (game.hpp:459),
     * consumed by the `ListSelection` state. When a `raceIdList` entry has no
     * monster type Canary removes it from the slot and emits an empty
     * `slot 0 / Inactive` payload; the slot mutation stays with the caller.
     *
     * @return true when a frame was written, false for the unhandled
     * `WildcardSelection` state (Canary logs a warning and sends nothing).
     */
    fun writePreyData(
        out: ByteBuf,
        slot: PreySlot,
        monsterLookup: (Int) -> PreyMonsterWindow?,
        bestiaryRaceIds: List<Int>,
        premium: Boolean,
        now: Long,
    ): Boolean {
        if (slot.state == PreyDataState.WildcardSelection) {
            return false
        }

        out.writeByte(PACKET_PREY_DATA)

        val validMonsters = ArrayList<PreyMonsterWindow>(slot.raceIdList.size)
        for (raceId in slot.raceIdList) {
            val monster = monsterLookup(raceId)
            if (monster == null) {
                out.writeByte(0)
                out.writeByte(PreyDataState.Inactive.value)
                out.writeIntLE(0)
                out.writeByte(0)
                return true
            }
            validMonsters += monster
        }

        out.writeByte(slot.id)
        out.writeByte(slot.state.value)

        when (slot.state) {
            PreyDataState.Locked -> out.writeByte(if (premium) 0x01 else 0x00)

            PreyDataState.Inactive -> Unit

            PreyDataState.Active -> {
                val monster = monsterLookup(slot.selectedRaceId)
                if (monster != null) {
                    out.writeTibiaString(monster.name)
                    writeMonsterOutfit(out, monster.outfit)
                    out.writeByte(slot.bonus.value)
                    out.writeShortLE(slot.bonusPercentage)
                    out.writeByte(slot.bonusRarity)
                    out.writeShortLE(slot.bonusTimeLeft)
                }
            }

            PreyDataState.Selection -> writeMonsterGrid(out, validMonsters)

            PreyDataState.SelectionChangeMonster -> {
                out.writeByte(slot.bonus.value)
                out.writeShortLE(slot.bonusPercentage)
                out.writeByte(slot.bonusRarity)
                writeMonsterGrid(out, validMonsters)
            }

            PreyDataState.ListSelection -> {
                out.writeShortLE(bestiaryRaceIds.size)
                for (raceId in bestiaryRaceIds) {
                    out.writeShortLE(raceId)
                }
            }

            PreyDataState.WildcardSelection -> return false
        }

        out.writeIntLE(((slot.freeRerollTimeStamp - now) / 1000).toInt())
        out.writeByte(slot.option.value)
        return true
    }

    /**
     * `sendPreyPrices` (protocolgame.cpp:9607-9628). For the
     * `ProtocolProfileId::Current` profile the `OfficialTaskboardPackets`
     * feature is present, so the trailing legacy task-hunting prices are not
     * written ([officialTaskboardPackets] defaults to that profile state).
     */
    fun writePreyPrices(
        out: ByteBuf,
        preyRerollPrice: Long,
        preyBonusRerollPrice: Int,
        preySelectListPrice: Int,
        officialTaskboardPackets: Boolean = true,
        taskHuntingRerollPrice: Long = 0,
        taskHuntingSelectListPrice: Int = 0,
        taskHuntingBonusRerollPrice: Int = 0,
    ) {
        out.writeByte(PACKET_PREY_PRICES)
        out.writeIntLE(preyRerollPrice.toInt())
        out.writeByte(preyBonusRerollPrice)
        out.writeByte(preySelectListPrice)

        if (!officialTaskboardPackets) {
            out.writeIntLE(taskHuntingRerollPrice.toInt())
            out.writeIntLE(taskHuntingRerollPrice.toInt())
            out.writeByte(taskHuntingSelectListPrice)
            out.writeByte(taskHuntingBonusRerollPrice)
        }
    }

    private fun writeMonsterGrid(out: ByteBuf, monsters: List<PreyMonsterWindow>) {
        out.writeByte(monsters.size)
        for (monster in monsters) {
            out.writeTibiaString(monster.name)
            writeMonsterOutfit(out, monster.outfit)
        }
    }

    /** `sendPreyData` writes `outfit.lookType`/`lookTypeEx`/colors directly, never a mount. */
    private fun writeMonsterOutfit(out: ByteBuf, outfit: Outfit) {
        out.writeShortLE(outfit.lookType)
        if (outfit.lookType == 0) {
            out.writeShortLE(outfit.lookTypeEx)
        } else {
            out.writeByte(outfit.lookHead)
            out.writeByte(outfit.lookBody)
            out.writeByte(outfit.lookLegs)
            out.writeByte(outfit.lookFeet)
            out.writeByte(outfit.lookAddons)
        }
    }
}
