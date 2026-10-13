package org.opentibia.canary.network.protocol.taskhunting

import io.netty.buffer.ByteBuf
import org.opentibia.canary.systems.taskhunting.PreyTaskDataState
import org.opentibia.canary.systems.taskhunting.TaskHuntingOption
import org.opentibia.canary.systems.taskhunting.TaskHuntingSlot
import org.opentibia.canary.systems.taskhunting.TaskMonsterInfo

/**
 * Outbound task hunting serialization, ported from Canary:
 *
 *  - `IOPrey::initializeTaskHuntOptions` legacy base-data frame
 *    (0xBA, src/io/ioprey.cpp:541-605)
 *  - `ProtocolGame::sendTaskHuntingData`
 *    (0xBB, src/server/network/protocol/protocolgame.cpp:10481-10549)
 *
 * Every multi-byte field is Little-Endian. The C++ `!player`/`!slot` guards and
 * the `disabledUntilTimeStamp`/`eraseTask` side effects stay with the caller;
 * [TaskMonsterInfo], [TaskHuntingOption] and [TaskHuntingSlot] are the engine
 * DTOs produced by `TaskHuntingEngine` / `PlayerTaskHuntingState`.
 *
 * The legacy 0xBB frame is gated off by
 * `ProtocolFeature::OfficialTaskboardPackets` (protocolgame.cpp:10482), which
 * the `ProtocolProfileId::Current` profile enables (protocol_profile.cpp:202);
 * [officialTaskboardPackets] therefore defaults to true and writes nothing.
 */
object TaskHuntingWindowWriter {

    const val PACKET_TASK_HUNTING_BASE_DATA: Int = 0xBA
    const val PACKET_TASK_HUNTING_DATA: Int = 0xBB

    /**
     * `IOPrey::initializeTaskHuntOptions` (ioprey.cpp:576-604). [monsters] is
     * the bestiary list resolved to `MonsterType` in race-id order; Canary
     * writes the pre-resolution list size and skips unresolvable names without
     * adjusting it, so the caller passes only resolved entries (the port drops
     * the unresolved-entry quirk, see PARITY GAPS).
     */
    fun writeTaskHuntingBaseData(
        out: ByteBuf,
        monsters: List<TaskMonsterInfo>,
        options: List<TaskHuntingOption>,
    ) {
        out.writeByte(PACKET_TASK_HUNTING_BASE_DATA)

        out.writeShortLE(monsters.size)
        for (monster in monsters) {
            out.writeShortLE(monster.raceid)
            out.writeByte(
                when {
                    monster.bestiaryStars <= 1 -> 1
                    monster.bestiaryStars <= 3 -> 2
                    else -> 3
                },
            )
        }

        out.writeByte(options.size)
        for (option in options) {
            out.writeByte(option.difficult.value)
            out.writeByte(option.rarity)
            out.writeShortLE(option.firstKills)
            out.writeShortLE(option.firstReward)
            out.writeShortLE(option.secondKills)
            out.writeShortLE(option.secondReward)
        }
    }

    /**
     * `sendTaskHuntingData` (protocolgame.cpp:10481-10549).
     *
     * [option] is `IOPrey::getTaskRewardOption(slot)`; a null option on an
     * `Active`/`Completed` slot produces no frame, exactly like Canary's silent
     * warning path. [creatureUnlocked] is
     * `Player::isCreatureUnlockedOnTaskHunting` and [bestiaryRaceIds] the
     * `Game::getBestiaryList()` race ids for the `ListSelection` state.
     *
     * The free-reroll countdown reproduces Canary's wrapped
     * `static_cast<uint32_t>((freeRerollTimeStamp - OTSYS_TIME()) / 1000)`.
     *
     * @return true when a frame was written.
     */
    fun writeTaskHuntingData(
        out: ByteBuf,
        slot: TaskHuntingSlot,
        option: TaskHuntingOption?,
        creatureUnlocked: (Int) -> Boolean,
        bestiaryRaceIds: List<Int>,
        premium: Boolean,
        officialTaskboardPackets: Boolean = true,
        now: Long,
    ): Boolean {
        if (officialTaskboardPackets) {
            return false
        }

        val needsOption = slot.state == PreyTaskDataState.Active || slot.state == PreyTaskDataState.Completed
        if (needsOption && option == null) {
            return false
        }

        out.writeByte(PACKET_TASK_HUNTING_DATA)
        out.writeByte(slot.id)
        out.writeByte(slot.state.value)

        when (slot.state) {
            PreyTaskDataState.Locked -> out.writeByte(if (premium) 0x01 else 0x00)

            PreyTaskDataState.Inactive -> Unit

            PreyTaskDataState.Selection -> {
                out.writeShortLE(slot.raceIdList.size)
                for (raceId in slot.raceIdList) {
                    out.writeShortLE(raceId)
                    out.writeByte(if (creatureUnlocked(raceId)) 0x01 else 0x00)
                }
            }

            PreyTaskDataState.ListSelection -> {
                out.writeShortLE(bestiaryRaceIds.size)
                for (raceId in bestiaryRaceIds) {
                    out.writeShortLE(raceId)
                    out.writeByte(if (creatureUnlocked(raceId)) 0x01 else 0x00)
                }
            }

            PreyTaskDataState.Active -> writeActiveSlot(out, slot, requireNotNull(option))

            PreyTaskDataState.Completed -> {
                val reward = requireNotNull(option)
                out.writeShortLE(slot.selectedRaceId)
                if (slot.upgrade) {
                    out.writeByte(0x01)
                    out.writeShortLE(reward.secondKills)
                    out.writeShortLE(minOf(slot.currentKills, reward.secondKills))
                } else {
                    out.writeByte(0x00)
                    out.writeShortLE(reward.firstKills)
                    out.writeShortLE(minOf(slot.currentKills, reward.firstKills))
                }
                out.writeByte(slot.rarity)
            }
        }

        out.writeIntLE(((slot.freeRerollTimeStamp - now) / 1000).toInt())
        return true
    }

    private fun writeActiveSlot(out: ByteBuf, slot: TaskHuntingSlot, option: TaskHuntingOption) {
        out.writeShortLE(slot.selectedRaceId)
        if (slot.upgrade) {
            out.writeByte(0x01)
            out.writeShortLE(option.secondKills)
        } else {
            out.writeByte(0x00)
            out.writeShortLE(option.firstKills)
        }
        out.writeShortLE(slot.currentKills)
        out.writeByte(slot.rarity)
    }
}
