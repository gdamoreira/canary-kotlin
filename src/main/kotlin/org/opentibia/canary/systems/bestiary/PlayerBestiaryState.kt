package org.opentibia.canary.systems.bestiary

/**
 * Self-contained holder for the Player bestiary/charm state.
 *
 * Transcribed from Canary `Player` so that the Bestiary engine can be exercised
 * without touching Player.kt:
 *  - `src/creatures/players/player.hpp:1833-1842` (fields)
 *  - `src/creatures/players/player.cpp:12969-12979` (kill counts via storage)
 *  - `src/creatures/players/player.cpp:1830-1895` (charm points / rune bits)
 *  - `src/creatures/players/player.cpp:1862-1871` (charm tier)
 *  - `src/creatures/players/player.cpp:1940-1979` (parseRacebyCharm)
 *
 * Canary persists kill counts through `STORAGEVALUE_BESTIARYKILLCOUNT`
 * (`src/utils/const.hpp:31`, value 61305000). The storage-key indirection is
 * replaced here by a direct `raceid -> count` map, which is behaviorally
 * equivalent for a single player.
 */
class PlayerBestiaryState {
    private val bestiaryKillCount = mutableMapOf<Int, Int>()

    private var charmPoints = 0
    private var minorCharmEchoes = 0
    private var maxCharmPoints = 0
    private var maxMinorCharmEchoes = 0

    private var usedRunesBit = 0
    private var unlockedRunesBit = 0

    /**
     * `charmsArray` mirrors `std::array<CharmInfo, magic_enum::enum_count<charmRune_t>() + 1>`
     * (player.hpp:1836). `charmRune_t` has 26 enumerators (CHARM_NONE plus 0..24).
     */
    private val charmsArray = Array(CHARM_RUNE_ENUM_COUNT + 1) { CharmInfo() }

    /** Player flags read by the charm rune flow (player.cpp:1873-1879, isPremium). */
    var hasCharmExpansion: Boolean = false
    var isPremium: Boolean = false

    /** Observable sinks for the messages the Bestiary engine emits. */
    val textMessages: MutableList<String> = mutableListOf()
    val bestiaryEntriesChanged: MutableList<Int> = mutableListOf()

    /** `Player::addBestiaryKillCount` (player.cpp:12969-12973). */
    fun addBestiaryKillCount(raceid: Int, amount: Int) {
        val oldCount = getBestiaryKillCount(raceid)
        bestiaryKillCount[raceid] = oldCount + amount
    }

    /** `Player::getBestiaryKillCount` (player.cpp:12975-12979). */
    fun getBestiaryKillCount(raceid: Int): Int {
        val value = bestiaryKillCount[raceid] ?: 0
        return if (value > 0) value else 0
    }

    /**
     * Persistence seam for [org.opentibia.canary.database.FeatureStateRepository]:
     * every stored kill count, `raceid -> count`. Canary serializes these through
     * `STORAGEVALUE_BESTIARYKILLCOUNT`; the direct map is its behavioural
     * equivalent for a single player.
     */
    fun getBestiaryKillCounts(): Map<Int, Int> = bestiaryKillCount.toMap()

    /** Persistence seam: replace the stored kill counts with [counts]. */
    fun setBestiaryKillCounts(counts: Map<Int, Int>) {
        bestiaryKillCount.clear()
        bestiaryKillCount.putAll(counts)
    }

    /** `Player::getCharmPoints` (player.cpp:1830-1832). */
    fun getCharmPoints(): Int = charmPoints

    /** `Player::setCharmPoints` (player.cpp:1834-1836). */
    fun setCharmPoints(points: Int) {
        charmPoints = points
    }

    /** `Player::getMinorCharmEchoes` (player.cpp:1838-1840). */
    fun getMinorCharmEchoes(): Int = minorCharmEchoes

    /** `Player::setMinorCharmEchoes` (player.cpp:1842-1844). */
    fun setMinorCharmEchoes(points: Int) {
        minorCharmEchoes = points
    }

    /** `Player::getMaxCharmPoints` (player.cpp:1846-1848). */
    fun getMaxCharmPoints(): Int = maxCharmPoints

    /** `Player::setMaxCharmPoints` (player.cpp:1850-1852). */
    fun setMaxCharmPoints(points: Int) {
        maxCharmPoints = points
    }

    /** `Player::getMaxMinorCharmEchoes` (player.cpp:1854-1856). */
    fun getMaxMinorCharmEchoes(): Int = maxMinorCharmEchoes

    /** `Player::setMaxMinorCharmEchoes` (player.cpp:1858-1860). */
    fun setMaxMinorCharmEchoes(points: Int) {
        maxMinorCharmEchoes = points
    }

    /** `Player::getCharmTier` (player.cpp:1862-1867). */
    fun getCharmTier(charmId: CharmRune): Int {
        if (charmId == CharmRune.CHARM_NONE || charmId.value >= charmsArray.size) {
            return 0
        }
        return charmsArray[charmId.value].tier
    }

    /** `Player::setCharmTier` (player.cpp:1869-1871). */
    fun setCharmTier(charmId: CharmRune, newTier: Int) {
        if (charmId == CharmRune.CHARM_NONE) {
            return
        }
        charmsArray[charmId.value].tier = newTier
    }

    /** `Player::setUsedRunesBit` (player.cpp:1881-1883). */
    fun setUsedRunesBit(bit: Int) {
        usedRunesBit = bit
    }

    /** `Player::getUsedRunesBit` (player.cpp:1885-1887). */
    fun getUsedRunesBit(): Int = usedRunesBit

    /** `Player::setUnlockedRunesBit` (player.cpp:1889-1891). */
    fun setUnlockedRunesBit(bit: Int) {
        unlockedRunesBit = bit
    }

    /** `Player::getUnlockedRunesBit` (player.cpp:1893-1895). */
    fun getUnlockedRunesBit(): Int = unlockedRunesBit

    /** `Player::parseRacebyCharm` (player.cpp:1940-1979). */
    fun parseRacebyCharm(charmId: CharmRune, set: Boolean = false, newRaceid: Int = 0): Int {
        if (charmId == CharmRune.CHARM_NONE) {
            return 0
        }
        return if (set) {
            charmsArray[charmId.value].raceId = newRaceid
            0
        } else {
            charmsArray[charmId.value].raceId
        }
    }

    /** `Player::sendTextMessage` sink (player.hpp) used by the Bestiary engine. */
    fun sendTextMessage(message: String) {
        textMessages.add(message)
    }

    /** `Player::sendBestiaryEntryChanged` sink (player.cpp) used by the Bestiary engine. */
    fun sendBestiaryEntryChanged(raceId: Int) {
        bestiaryEntriesChanged.add(raceId)
    }

    private companion object {
        const val CHARM_RUNE_ENUM_COUNT = 26
    }
}
