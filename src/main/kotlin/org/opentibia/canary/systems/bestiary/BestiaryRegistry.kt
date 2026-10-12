package org.opentibia.canary.systems.bestiary

import java.util.TreeMap

/**
 * Monster-to-bestiary-race registry.
 *
 * Replaces the `g_game().getBestiaryList()` / `g_monsters()` globals with a
 * constructor-injected, deterministic lookup (AGENTS.md 1.1). It is behaviorally
 * equivalent to Canary's two cooperating stores:
 *  - `Game::BestiaryList` (`std::map<uint16_t, std::string>`, game.hpp:876) built
 *    by `Game::addBestiaryList` (game.cpp:9517-9524) and read by
 *    `Game::getBestiaryList` (game.hpp:459-461). `std::map` is key-ordered, so the
 *    list here is kept in a [TreeMap] sorted by race id.
 *  - `Monsters::getMonsterType` (monsters.cpp:363-372) and
 *    `Monsters::getMonsterTypeByRaceId` (monsters.cpp:375-388).
 *
 * The race query helpers live on `IOBestiary` in Canary but only depend on these
 * stores, so they are provided here:
 *  - `IOBestiary::findRaceByName` (iobestiary.cpp:250-270)
 *  - `IOBestiary::getBestiaryRaceUnlocked` (iobestiary.cpp:334-349)
 */
class BestiaryRegistry(
    monsters: List<MonsterBestiaryInfo> = emptyList(),
) {
    private val bestiaryList: TreeMap<Int, String> = TreeMap()
    private val monstersByName: MutableMap<String, MonsterBestiaryInfo> = mutableMapOf()

    init {
        for (mtype in monsters) {
            registerMonster(mtype)
        }
    }

    /**
     * `Game::addBestiaryList` (game.cpp:9517-9524): inserts only when the race id
     * is not already present, so the first registered name wins.
     */
    fun addBestiaryList(raceid: Int, name: String) {
        if (bestiaryList.containsKey(raceid)) {
            return
        }
        bestiaryList[raceid] = name
    }

    /**
     * Mirrors the Lua `monsterType:raceId(id)` registration (monster_type_functions.cpp:680-695):
     * every monster becomes name-addressable, but only monsters with a non-zero
     * race id enter the bestiary list.
     */
    fun registerMonster(mtype: MonsterBestiaryInfo) {
        monstersByName[mtype.name.lowercase()] = mtype
        if (mtype.raceid != 0) {
            addBestiaryList(mtype.raceid, mtype.name)
        }
    }

    /** `Game::getBestiaryList` (game.hpp:459-461): race id to monster name, id-ordered. */
    fun getBestiaryList(): Map<Int, String> = bestiaryList

    /**
     * `Monsters::getMonsterType` (monsters.cpp:363-372): case-insensitive name lookup.
     */
    fun getMonsterType(name: String): MonsterBestiaryInfo? = monstersByName[name.lowercase()]

    /**
     * `Monsters::getMonsterTypeByRaceId` (monsters.cpp:375-388), minus the boss
     * override which belongs to the bosstiary port.
     */
    fun getMonsterTypeByRaceId(raceid: Int): MonsterBestiaryInfo? {
        val name = bestiaryList[raceid] ?: return null
        return getMonsterType(name)
    }

    /**
     * `IOBestiary::findRaceByName` (iobestiary.cpp:250-270).
     *
     * With [onlyString] the filter compares the `bestiaryClass` string; otherwise
     * it matches [raceNumber] against the monster's `BestiaryType_t` race.
     */
    fun findRaceByName(
        race: String,
        onlyString: Boolean = true,
        raceNumber: BestiaryRace = BestiaryRace.BESTY_RACE_NONE,
    ): Map<Int, String> {
        val raceList = TreeMap<Int, String>()
        for ((raceid, name) in bestiaryList) {
            val mtype = getMonsterType(name) ?: continue
            if (onlyString) {
                if (mtype.bestiaryClass == race) {
                    raceList[raceid] = name
                }
            } else {
                if (mtype.bestiaryRace == raceNumber) {
                    raceList[raceid] = name
                }
            }
        }
        return raceList
    }

    /**
     * `IOBestiary::getBestiaryRaceUnlocked` (iobestiary.cpp:334-349): the number
     * of monsters of [race] the player has killed at least once.
     */
    fun getBestiaryRaceUnlocked(player: PlayerBestiaryState, race: BestiaryRace): Int {
        var count = 0
        for ((_, name) in bestiaryList) {
            val mtype = getMonsterType(name) ?: continue
            if (mtype.bestiaryRace == race && player.getBestiaryKillCount(mtype.raceid) > 0) {
                count++
            }
        }
        return count
    }
}
