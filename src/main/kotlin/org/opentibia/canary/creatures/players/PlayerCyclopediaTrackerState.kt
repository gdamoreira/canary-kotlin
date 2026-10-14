package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.monsters.MonsterType

/**
 * The client view of the cyclopedia monster-tracker set
 * (src/creatures/players/player.hpp:1700-1701, player.cpp:945-984).
 *
 * `ProtocolGame::parseSendBosstiary`, `ProtocolGame::sendBestiaryEntryChanged`
 * and `ProtocolGame::refreshCyclopediaMonsterTracker` are the injected client
 * operations; a `null` client reproduces Canary's `if (!client) return;` guard
 * exactly (AGENTS.md 1.1).
 */
interface CyclopediaTrackerClient {
    /** `ProtocolGame::parseSendBosstiary()` (player.cpp:957). */
    fun parseSendBosstiary()

    /** `ProtocolGame::sendBestiaryEntryChanged(raceId)` (player.cpp:959). */
    fun sendBestiaryEntryChanged(raceId: Int)

    /** `ProtocolGame::refreshCyclopediaMonsterTracker(trackerList, isBoss)` (player.cpp:962, 982). */
    fun refreshCyclopediaMonsterTracker(trackerList: Set<MonsterType?>, isBoss: Boolean)
}

/**
 * Mirror of Canary's bestiary/bosstiary monster-tracker sets
 * (src/creatures/players/player.cpp:941-1004):
 *
 * - `Player::getCyclopediaMonsterTrackerSet` (player.cpp:941-943)
 * - `Player::addMonsterToCyclopediaTrackerList` (player.cpp:945-963)
 * - `Player::removeMonsterFromCyclopediaTrackerList` (player.cpp:965-984)
 * - `Player::isBossOnBosstiaryTracker` (player.cpp:998-1000)
 * - `Player::isMonsterOnBestiaryTracker` (player.cpp:1002-1004)
 *
 * The C++ `std::unordered_set<std::shared_ptr<MonsterType>>` keys on pointer
 * identity, so the port stores [MonsterType] references in an identity set;
 * `tracker.emplace(mtype)` also stores the potential `nullptr` entry, which is
 * kept. The `uint8_t` cap of the tracker size is transcribed on add.
 */
class PlayerCyclopediaTrackerState(
    private val client: CyclopediaTrackerClient? = null,
) {

    /** `std::unordered_set<...> m_bestiaryMonsterTracker` (player.hpp:1700). */
    private val bestiaryMonsterTracker = linkedSetOf<MonsterType?>()

    /** `std::unordered_set<...> m_bosstiaryMonsterTracker` (player.hpp:1701). */
    private val bosstiaryMonsterTracker = linkedSetOf<MonsterType?>()

    /** `Player::getCyclopediaMonsterTrackerSet` (player.cpp:941-943). */
    fun getCyclopediaMonsterTrackerSet(isBoss: Boolean): Set<MonsterType?> =
        if (isBoss) bosstiaryMonsterTracker else bestiaryMonsterTracker

    /** `Player::addMonsterToCyclopediaTrackerList` (player.cpp:945-963). */
    fun addMonsterToCyclopediaTrackerList(mtype: MonsterType?, isBoss: Boolean, reloadClient: Boolean = false) {
        val client = client ?: return

        val raceId = mtype?.info?.raceid ?: 0
        val tracker = if (isBoss) bosstiaryMonsterTracker else bestiaryMonsterTracker
        if (tracker.size < UINT8_MAX && tracker.add(mtype)) {
            if (reloadClient && raceId != 0) {
                if (isBoss) {
                    client.parseSendBosstiary()
                } else {
                    client.sendBestiaryEntryChanged(raceId)
                }
            }

            client.refreshCyclopediaMonsterTracker(tracker, isBoss)
        }
    }

    /** `Player::removeMonsterFromCyclopediaTrackerList` (player.cpp:965-984). */
    fun removeMonsterFromCyclopediaTrackerList(mtype: MonsterType?, isBoss: Boolean, reloadClient: Boolean = false) {
        val client = client ?: return

        val raceId = mtype?.info?.raceid ?: 0
        val tracker = if (isBoss) bosstiaryMonsterTracker else bestiaryMonsterTracker

        if (tracker.remove(mtype)) {
            if (reloadClient && raceId != 0) {
                if (isBoss) {
                    client.parseSendBosstiary()
                } else {
                    client.sendBestiaryEntryChanged(raceId)
                }
            }

            client.refreshCyclopediaMonsterTracker(tracker, isBoss)
        }
    }

    /** `Player::isBossOnBosstiaryTracker` (player.cpp:998-1000). */
    fun isBossOnBosstiaryTracker(monsterType: MonsterType?): Boolean =
        monsterType != null && bosstiaryMonsterTracker.contains(monsterType)

    /** `Player::isMonsterOnBestiaryTracker` (player.cpp:1002-1004). */
    fun isMonsterOnBestiaryTracker(monsterType: MonsterType?): Boolean =
        monsterType != null && bestiaryMonsterTracker.contains(monsterType)

    private companion object {
        /** `std::numeric_limits<uint8_t>::max()` (player.cpp:952). */
        const val UINT8_MAX = 255
    }
}
