package org.opentibia.canary.systems.bestiary

/**
 * Bestiary engine core.
 *
 * Ported from Canary `src/io/iobestiary.cpp` / `iobestiary.hpp` (class IOBestiary).
 *
 * Scope: kill-count tracking across the three unlock stages, stage-completion
 * detection, charm-point/echo awards, charm lookup, rune bit helpers and
 * creature-to-charm assignment. The combat procs (parseCharmCombat and friends)
 * and the network serialization live outside this port.
 *
 * Global singletons (`g_game()`, `g_monsters()`) are replaced by constructor
 * injection: registered charms are supplied to the constructor and monster
 * metadata is passed as [MonsterBestiaryInfo] values.
 */
class IOBestiary(
    private val charms: MutableList<Charm> = mutableListOf(),
) {
    /**
     * `IOBestiary::getBestiaryCharm` (iobestiary.cpp:231-248).
     *
     * When [force] is set and the rune is unknown, Canary creates and registers a
     * placeholder Charm with `binary = 1 << activeCharm`.
     */
    fun getBestiaryCharm(activeCharm: CharmRune, force: Boolean = false): Charm? {
        for (tmpCharm in charms) {
            if (tmpCharm.id == activeCharm) {
                return tmpCharm
            }
        }

        if (force) {
            val charm = Charm(id = activeCharm, binary = 1 shl activeCharm.value)
            charms.add(charm)
            return charm
        }

        return null
    }

    /**
     * `IOBestiary::getKillStatus` (iobestiary.cpp:272-281).
     *
     * Returns 1 below FirstUnlock, 2 below SecondUnlock, 3 below toUnlock, else 4.
     */
    fun getKillStatus(mtype: MonsterBestiaryInfo, killAmount: Int): Int {
        return when {
            killAmount < mtype.bestiaryFirstUnlock -> 1
            killAmount < mtype.bestiarySecondUnlock -> 2
            killAmount < mtype.bestiaryToUnlock -> 3
            else -> 4
        }
    }

    /**
     * `IOBestiary::addBestiaryKill` (iobestiary.cpp:383-410).
     *
     * Increments the kill count and, on each stage transition, emits the unlock
     * message and entry-changed notification. Crossing toUnlock awards exactly
     * `mtype.info.bestiaryCharmsPoints` charm points (iobestiary.cpp:401-403).
     */
    fun addBestiaryKill(player: PlayerBestiaryState, mtype: MonsterBestiaryInfo, amount: Int = 1) {
        val raceid = mtype.raceid
        if (raceid == 0) {
            return
        }

        val curCount = player.getBestiaryKillCount(raceid)
        player.addBestiaryKillCount(raceid, amount)

        val after = curCount + amount
        if (curCount == 0 ||
            (curCount < mtype.bestiaryFirstUnlock && after >= mtype.bestiaryFirstUnlock) ||
            (curCount < mtype.bestiarySecondUnlock && after >= mtype.bestiarySecondUnlock) ||
            (curCount < mtype.bestiaryToUnlock && after >= mtype.bestiaryToUnlock)
        ) {
            player.sendTextMessage("You unlocked details for the creature '${mtype.name}'")
            player.sendBestiaryEntryChanged(raceid)

            if (after >= mtype.bestiaryToUnlock) {
                addCharmPoints(player, mtype.bestiaryCharmsPoints)
            }
        }
    }

    /**
     * `IOBestiary::addCharmPoints` (iobestiary.cpp:351-365).
     *
     * Positive awards also raise the recorded maximum; negative spends do not.
     */
    fun addCharmPoints(player: PlayerBestiaryState, amount: Int, negative: Boolean = false) {
        var myCharms = player.getCharmPoints()
        if (negative) {
            myCharms -= amount
        } else {
            myCharms += amount
            player.setMaxCharmPoints(player.getMaxCharmPoints() + amount)
        }
        player.setCharmPoints(myCharms)
    }

    /** `IOBestiary::addMinorCharmEchoes` (iobestiary.cpp:367-381). */
    fun addMinorCharmEchoes(player: PlayerBestiaryState, amount: Int, negative: Boolean = false) {
        var myCharms = player.getMinorCharmEchoes()
        if (negative) {
            myCharms -= amount
        } else {
            myCharms += amount
            player.setMaxMinorCharmEchoes(player.getMaxMinorCharmEchoes() + amount)
        }
        player.setMinorCharmEchoes(myCharms)
    }

    /** `IOBestiary::hasCharmUnlockedRuneBit` (iobestiary.cpp:434-440). */
    fun hasCharmUnlockedRuneBit(charm: Charm, input: Int): Boolean {
        return (input and charm.binary) != 0
    }

    /** `IOBestiary::bitToggle` (iobestiary.cpp:442-457). */
    fun bitToggle(input: Int, charm: Charm, on: Boolean): Int {
        val binary = charm.binary
        return if (on) {
            input or binary
        } else {
            input and binary.inv()
        }
    }

    /** `IOBestiary::getCharmUsedRuneBitAll` (iobestiary.cpp:318-332). */
    fun getCharmUsedRuneBitAll(player: PlayerBestiaryState): List<CharmRune> {
        var input = player.getUsedRunesBit()
        val rtn = ArrayDeque<CharmRune>()
        var i = 0
        while (input != 0) {
            if ((input and 1) == 1) {
                CharmRune.fromValue(i)?.let { rtn.addFirst(it) }
            }
            input = input ushr 1
            i += 1
        }
        return rtn.toList()
    }

    /** `IOBestiary::setCharmRuneCreature` (iobestiary.cpp:308-316). */
    fun setCharmRuneCreature(player: PlayerBestiaryState, charm: Charm, raceid: Int) {
        player.parseRacebyCharm(charm.id, set = true, newRaceid = raceid)
        val toggle = bitToggle(player.getUsedRunesBit(), charm, on = true)
        player.setUsedRunesBit(toggle)
    }

    /** `IOBestiary::resetCharmRuneCreature` (iobestiary.cpp:283-291). */
    fun resetCharmRuneCreature(player: PlayerBestiaryState, charm: Charm) {
        val value = bitToggle(player.getUsedRunesBit(), charm, on = false)
        player.setUsedRunesBit(value)
        player.parseRacebyCharm(charm.id, set = true, newRaceid = 0)
    }

    /** `IOBestiary::resetAllCharmRuneCreatures` (iobestiary.cpp:293-306). */
    fun resetAllCharmRuneCreatures(player: PlayerBestiaryState) {
        for (charm in charms) {
            player.parseRacebyCharm(charm.id, set = true, newRaceid = 0)
            player.setCharmTier(charm.id, 0)
        }
    }

    /** `IOBestiary::getCharmFromTarget` (iobestiary.cpp:412-432). */
    fun getCharmFromTarget(
        player: PlayerBestiaryState,
        mtype: MonsterBestiaryInfo,
        category: CharmCategory = CharmCategory.CHARM_ALL,
    ): PlayerCharmsByMonster {
        val playerCharmByMonster = PlayerCharmsByMonster()
        val bestiaryEntry = mtype.raceid

        for (it in getCharmUsedRuneBitAll(player)) {
            val charm = getBestiaryCharm(it) ?: continue
            if (bestiaryEntry == player.parseRacebyCharm(charm.id) &&
                (category == CharmCategory.CHARM_ALL || charm.category == category)
            ) {
                if (charm.category == CharmCategory.CHARM_MAJOR) {
                    playerCharmByMonster.major = charm.id
                } else if (charm.category == CharmCategory.CHARM_MINOR) {
                    playerCharmByMonster.minor = charm.id
                }
            }
        }

        return playerCharmByMonster
    }

    /**
     * `IOBestiary::getBestiaryFinished` (iobestiary.cpp:626-641).
     *
     * Canary iterates `g_game().getBestiaryList()` and looks monsters up through
     * `g_monsters()`; here the caller injects the monster list.
     */
    fun getBestiaryFinished(player: PlayerBestiaryState, monsters: List<MonsterBestiaryInfo>): List<Int> {
        return monsters
            .filter { player.getBestiaryKillCount(it.raceid) >= it.bestiaryToUnlock }
            .map { it.raceid }
            .distinct()
    }

    /** `IOBestiary::getBestiaryStageTwo` (iobestiary.cpp:643-658). */
    fun getBestiaryStageTwo(player: PlayerBestiaryState, monsters: List<MonsterBestiaryInfo>): List<Int> {
        return monsters
            .filter { player.getBestiaryKillCount(it.raceid) >= it.bestiarySecondUnlock }
            .map { it.raceid }
            .distinct()
    }

    /** `IOBestiary::getBestiaryKillCountByMonsterIDs` (iobestiary.cpp:614-624). */
    fun getBestiaryKillCountByMonsterIDs(player: PlayerBestiaryState, monsterIds: List<Int>): Map<Int, Int> {
        val raceMonsters = linkedMapOf<Int, Int>()
        for (raceid in monsterIds) {
            val thisKilled = player.getBestiaryKillCount(raceid)
            if (thisKilled > 0) {
                raceMonsters[raceid] = thisKilled
            }
        }
        return raceMonsters
    }

    /**
     * `IOBestiary::calculateDifficult` (iobestiary.cpp:660-673).
     *
     * Note the C++ computes `chance / 1000` with integer division before the
     * float comparison; that truncation is reproduced deliberately.
     */
    fun calculateDifficult(chance: Int): Int {
        val chanceInPercent = (chance / 1000).toFloat()
        return when {
            chanceInPercent < 0.2f -> 4
            chanceInPercent < 1f -> 3
            chanceInPercent < 5f -> 2
            chanceInPercent < 25f -> 1
            else -> 0
        }
    }
}

/** `BestiaryType_t` (creatures_definitions.hpp:524-551). */
enum class BestiaryRace(val value: Int) {
    BESTY_RACE_NONE(0),
    BESTY_RACE_AMPHIBIC(1),
    BESTY_RACE_AQUATIC(2),
    BESTY_RACE_BIRD(3),
    BESTY_RACE_CONSTRUCT(4),
    BESTY_RACE_DEMON(5),
    BESTY_RACE_DRAGON(6),
    BESTY_RACE_ELEMENTAL(7),
    BESTY_RACE_FEY(8),
    BESTY_RACE_GIANT(9),
    BESTY_RACE_HUMAN(10),
    BESTY_RACE_HUMANOID(11),
    BESTY_RACE_LYCANTHROPE(12),
    BESTY_RACE_MAGICAL(13),
    BESTY_RACE_MAMMAL(14),
    BESTY_RACE_PLANT(15),
    BESTY_RACE_REPTILE(16),
    BESTY_RACE_SLIME(17),
    BESTY_RACE_UNDEAD(18),
    BESTY_RACE_VERMIN(19),
    BESTY_RACE_EXTRA_DIMENSIONAL(20),
    BESTY_RACE_INKBORN(21),
}

/**
 * Carrier for the bestiary subset of Canary `MonsterType::info`
 * (monsters.hpp:89-99): raceid, unlock thresholds, charm points and star rating.
 */
data class MonsterBestiaryInfo(
    val raceid: Int = 0,
    val name: String = "",
    val bestiaryFirstUnlock: Int = 0,
    val bestiarySecondUnlock: Int = 0,
    val bestiaryToUnlock: Int = 0,
    val bestiaryCharmsPoints: Int = 0,
    val bestiaryStars: Int = 0,
    val bestiaryClass: String = "",
    val bestiaryRace: BestiaryRace = BestiaryRace.BESTY_RACE_NONE,
)
