package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.MonsterType

/**
 * Cyclopedia monster tracker request (client opcode `0x2A`), mirroring the two
 * leading reads of `ProtocolGame::parseCyclopediaMonsterTracker`
 * (protocolgame.cpp:3634-3638): the `uint16_t` monster race id and the tracker
 * button type byte (`0` disables the tracker, `1` enables it).
 */
data class CyclopediaTrackerCommand(
    val monsterRaceId: Int,
    val trackerButtonType: Int,
)

/**
 * Decoder and applier for the cyclopedia monster tracker opcode.
 *
 *  - `0x2A` `ProtocolGame::parseCyclopediaMonsterTracker` (protocolgame.cpp:3634)
 *
 * The Canary handler resolves the race id through `g_ioBosstiary().getMonsterTypeByBossRaceId`
 * first and, when that misses, through `g_game().getBestiaryList()` +
 * `g_monsters().getMonsterType` (protocolgame.cpp:3641-3667). Neither registry
 * is reachable from the router context, so both lookups are constructor-style
 * seams ([apply] parameters) and default to no monster (PARITY GAP): the packet
 * is decoded and confined, but no tracker entry changes until a caller supplies
 * the registries.
 */
object CyclopediaTrackerPacketHandler {

    /** Client opcode for `ProtocolGame::parseCyclopediaMonsterTracker`. */
    const val OPCODE_CYCLOPEDIA_MONSTER_TRACKER: Int = 0x2A

    /** `trackerButtonType` that removes a monster from the tracker list. */
    const val TRACKER_DISABLED: Int = 0

    /** `trackerButtonType` that adds a monster to the tracker list. */
    const val TRACKER_ENABLED: Int = 1

    /**
     * 0x2A: reads the `uint16_t` race id and the tracker button byte.
     *
     * @return the decoded command, or `null` when the payload is truncated.
     */
    fun parse(buffer: ByteBuf): CyclopediaTrackerCommand? {
        if (buffer.readableBytes() < 3) {
            return null
        }
        val monsterRaceId = buffer.readUnsignedShortLE()
        val trackerButtonType = buffer.readUnsignedByte().toInt()
        return CyclopediaTrackerCommand(
            monsterRaceId = monsterRaceId,
            trackerButtonType = trackerButtonType,
        )
    }

    /**
     * Mirrors `ProtocolGame::parseCyclopediaMonsterTracker`
     * (protocolgame.cpp:3641-3667) once the race id is resolved: the bosstiary
     * lookup wins over the bestiary lookup, and the bosstiary branch requires a
     * nonzero `Player::getBestiaryKillCount(monsterRaceId)` before it toggles
     * the boss tracker.
     *
     * @param monsterTypeByBossRaceId `g_ioBosstiary().getMonsterTypeByBossRaceId`.
     * @param monsterTypeByRaceId `g_monsters().getMonsterType(g_game().getBestiaryList()[raceId])`.
     */
    fun apply(
        player: Player,
        command: CyclopediaTrackerCommand,
        monsterTypeByBossRaceId: (Int) -> MonsterType? = { null },
        monsterTypeByRaceId: (Int) -> MonsterType? = { null },
    ) {
        val bossMonsterType = monsterTypeByBossRaceId(command.monsterRaceId)
        if (bossMonsterType != null) {
            if (player.features.bestiary.getBestiaryKillCount(command.monsterRaceId) != 0) {
                if (command.trackerButtonType == TRACKER_ENABLED) {
                    player.addMonsterToCyclopediaTrackerList(bossMonsterType, isBoss = true, reloadClient = true)
                } else {
                    player.removeMonsterFromCyclopediaTrackerList(bossMonsterType, isBoss = true, reloadClient = true)
                }
            }
            return
        }

        val monsterType = monsterTypeByRaceId(command.monsterRaceId) ?: return
        if (command.trackerButtonType == TRACKER_ENABLED) {
            player.addMonsterToCyclopediaTrackerList(monsterType, isBoss = false, reloadClient = true)
        } else {
            player.removeMonsterFromCyclopediaTrackerList(monsterType, isBoss = false, reloadClient = true)
        }
    }
}
