package org.opentibia.canary.systems.bestiary

import org.opentibia.canary.network.protocol.bestiary.BestiaryCommand
import org.opentibia.canary.network.protocol.bestiary.BestiaryMonsterDataCommand
import org.opentibia.canary.network.protocol.bestiary.BestiarySendCreaturesCommand
import org.opentibia.canary.network.protocol.bestiary.BestiarySendRacesCommand
import org.opentibia.canary.network.protocol.bestiary.BuyCharmRuneCommand
import org.opentibia.canary.network.protocol.bestiary.SearchRaceIdsCommand
import org.opentibia.canary.network.protocol.bestiary.SearchRaceByNameCommand
import java.util.TreeMap

/**
 * One bestiary race summary as resolved by `ProtocolGame::parseBestiarySendRaces`
 * (protocolgame.cpp:3481-3498): the race's class string, its registered monster
 * count and how many of its monsters the player has killed at least once.
 */
data class BestiaryRaceSummary(
    val race: BestiaryRace,
    val bestiaryClass: String,
    val monsterCount: Int,
    val unlockedCount: Int,
)

/**
 * One creature row of `ProtocolGame::parseBestiarySendCreatures`
 * (protocolgame.cpp:4204-4233): kill count, `getKillStatus` progress and the
 * monster's `bestiaryOccurrence`. Canary omits the occurrence byte when
 * [currentLevel] is zero, so [occurrence] is 0 in that case.
 */
data class BestiaryCreature(
    val raceId: Int,
    val name: String,
    val killCount: Int,
    val currentLevel: Int,
    val occurrence: Int,
)

/**
 * Payload of `ProtocolGame::parseBestiarySendCreatures`: the echoed search text
 * (empty for the race-id list branch) and the matching creatures.
 */
data class BestiaryCreatures(
    val searchText: String,
    val creatures: List<BestiaryCreature>,
)

/**
 * Payload of `ProtocolGame::parseBestiarysendMonsterData` (protocolgame.cpp:3540-3564):
 * the resolved monster, its kill counter and current `getKillStatus` level.
 */
data class BestiaryMonsterData(
    val raceId: Int,
    val bestiaryClass: String,
    val killCount: Int,
    val currentLevel: Int,
    val monster: MonsterBestiaryInfo,
)

/**
 * Outcome of [BestiaryApplication.apply]. The nullable payloads mirror Canary's
 * silent early returns: an aborted races response, an unknown creature search
 * or an unknown monster race produce no packet.
 */
sealed interface BestiaryApplyResult {
    data class Races(val races: List<BestiaryRaceSummary>?) : BestiaryApplyResult
    data class Creatures(val creatures: BestiaryCreatures?) : BestiaryApplyResult
    data class MonsterData(val monsterData: BestiaryMonsterData?) : BestiaryApplyResult
    data class CharmRune(val action: Int, val charmId: Int, val raceId: Int) : BestiaryApplyResult
}

/**
 * Application service over the ported bestiary engine: applies decoded
 * [BestiaryCommand]s to a [PlayerBestiaryState], resolving monsters through the
 * injected [BestiaryRegistry] and charms through the injected [IOBestiary].
 *
 * Ported from Canary:
 *  - `ProtocolGame::parseBestiarySendRaces` (src/server/network/protocol/protocolgame.cpp:3472-3502)
 *  - `ProtocolGame::parseBestiarySendCreatures` (protocolgame.cpp:4164-4239)
 *  - `ProtocolGame::parseBestiarysendMonsterData` (protocolgame.cpp:3515-3632)
 *  - `IOBestiary::sendBuyCharmRune` (src/io/iobestiary.cpp:459-574)
 *
 * In Canary these handlers build the cyclopedia packets while resolving the
 * same data. The port returns the resolved domain payloads instead; serializing
 * them stays with the network layer, so this service only mutates or queries
 * the player state.
 */
class BestiaryApplication(
    private val registry: BestiaryRegistry = BestiaryRegistry(),
    private val bestiary: IOBestiary = IOBestiary(),
) {

    /** Dispatches [command] to its Canary-named handler. */
    fun apply(player: PlayerBestiaryState, command: BestiaryCommand): BestiaryApplyResult = when (command) {
        is BestiarySendRacesCommand -> BestiaryApplyResult.Races(parseBestiarySendRaces(player))
        is BestiarySendCreaturesCommand -> BestiaryApplyResult.Creatures(parseBestiarySendCreatures(player, command))
        is BestiaryMonsterDataCommand -> BestiaryApplyResult.MonsterData(parseBestiarysendMonsterData(player, command))
        is BuyCharmRuneCommand -> {
            sendBuyCharmRune(player, command.action, command.charmId, command.raceId)
            BestiaryApplyResult.CharmRune(command.action, command.charmId, command.raceId)
        }
    }

    /**
     * `ProtocolGame::parseBestiarySendRaces` (protocolgame.cpp:3472-3502).
     *
     * Walks races `BESTY_RACE_FIRST..BESTY_RACE_LAST` and, per race, counts the
     * registered monsters, records the class of the last one seen in race-id
     * order and resolves the unlocked count through the player's kill counts.
     * Canary aborts the whole response when a listed monster name cannot be
     * resolved; that abort is returned as `null`.
     */
    fun parseBestiarySendRaces(player: PlayerBestiaryState): List<BestiaryRaceSummary>? {
        val bestiaryList = registry.getBestiaryList()
        val summaries = ArrayList<BestiaryRaceSummary>(BESTIARY_RACES.size)
        for (race in BESTIARY_RACES) {
            var bestiaryClass = ""
            var monsterCount = 0
            for ((_, name) in bestiaryList) {
                val mtype = registry.getMonsterType(name) ?: return null
                if (mtype.bestiaryRace == race) {
                    monsterCount += 1
                    bestiaryClass = mtype.bestiaryClass
                }
            }
            summaries += BestiaryRaceSummary(
                race = race,
                bestiaryClass = bestiaryClass,
                monsterCount = monsterCount,
                unlockedCount = registry.getBestiaryRaceUnlocked(player, race),
            )
        }
        return summaries
    }

    /**
     * `ProtocolGame::parseBestiarySendCreatures` (protocolgame.cpp:4164-4239).
     *
     * The race-id branch keeps only ids the player has killed at least once and
     * that exist in the bestiary list, deduplicated and race-id ordered.
     * The name branch resolves the class string through
     * `IOBestiary::findRaceByName` and drops the response when nothing matches.
     * Canary aborts the response mid-build when a killed race's monster name
     * cannot be resolved; that abort is returned as `null`.
     */
    fun parseBestiarySendCreatures(
        player: PlayerBestiaryState,
        command: BestiarySendCreaturesCommand,
    ): BestiaryCreatures? {
        val race: Map<Int, String>
        val searchText: String
        when (command) {
            is SearchRaceIdsCommand -> {
                val bestiaryList = registry.getBestiaryList()
                val selected = TreeMap<Int, String>()
                for (raceId in command.raceIds) {
                    if (player.getBestiaryKillCount(raceId) > 0) {
                        bestiaryList[raceId]?.let { selected[raceId] = it }
                    }
                }
                race = selected
                searchText = ""
            }

            is SearchRaceByNameCommand -> {
                val found = registry.findRaceByName(command.raceName)
                if (found.isEmpty()) {
                    return null
                }
                race = found
                searchText = command.raceName
            }
        }

        val creatures = ArrayList<BestiaryCreature>(race.size)
        for ((raceId, name) in race) {
            val killCount = player.getBestiaryKillCount(raceId)
            var currentLevel = 0
            var occurrence = 0
            if (killCount > 0) {
                val mtype = registry.getMonsterType(name) ?: return null
                currentLevel = bestiary.getKillStatus(mtype, killCount)
                occurrence = mtype.bestiaryOccurrence
            }
            creatures += BestiaryCreature(raceId, name, killCount, currentLevel, occurrence)
        }
        return BestiaryCreatures(searchText, creatures)
    }

    /**
     * `ProtocolGame::parseBestiarysendMonsterData` (protocolgame.cpp:3515-3564).
     *
     * Resolves the race id through the bestiary list and the player's kill
     * counter into the current `getKillStatus`. Canary warns and sends nothing
     * for an unknown race, returned here as `null`.
     */
    fun parseBestiarysendMonsterData(
        player: PlayerBestiaryState,
        command: BestiaryMonsterDataCommand,
    ): BestiaryMonsterData? {
        val raceId = command.raceId
        val name = registry.getBestiaryList()[raceId] ?: return null
        val mtype = registry.getMonsterType(name) ?: return null
        val killCount = player.getBestiaryKillCount(raceId)
        return BestiaryMonsterData(
            raceId = raceId,
            bestiaryClass = mtype.bestiaryClass,
            killCount = killCount,
            currentLevel = bestiary.getKillStatus(mtype, killCount),
            monster = mtype,
        )
    }

    /**
     * `IOBestiary::sendBuyCharmRune` (iobestiary.cpp:459-574).
     *
     * Unlocks/upgrades a rune (action 0), sets a creature on a rune (action 1),
     * removes a rune (action 2) or resets every rune (action 3). Rejected
     * requests emit the exact Canary FYI-box message and skip the final
     * `Player::sendBestiaryCharms`; a rune id that does not resolve only blocks
     * the non-reset actions (iobestiary.cpp:460-463).
     */
    fun sendBuyCharmRune(player: PlayerBestiaryState, action: Int, charmId: Int, raceId: Int) {
        val charm = CharmRune.fromValue(charmId)?.let { bestiary.getBestiaryCharm(it) }

        if (action == ACTION_RESET_ALL) {
            if (applyResetAllCharmRunes(player)) {
                player.sendBestiaryCharms()
            }
            return
        }

        if (charm == null) {
            return
        }

        val applied = when (action) {
            ACTION_UNLOCK_RUNE -> applyUnlockCharmRune(player, charm)
            ACTION_SET_CREATURE -> applySetCharmRuneCreature(player, charm, raceId)
            ACTION_REMOVE_RUNE -> applyRemoveCharmRune(player, charm)
            else -> true
        }

        if (applied) {
            player.sendBestiaryCharms()
        }
    }

    /**
     * Action 0 (`iobestiary.cpp:465-500`): major runes cost charm points, minor
     * runes cost minor charm echoes, major upgrades also award
     * `25 * tier^2 + 25 * tier + 50` echoes. A successful unlock toggles the
     * unlocked-rune bit. Returns `false` when Canary would `return` early.
     */
    private fun applyUnlockCharmRune(player: PlayerBestiaryState, charm: Charm): Boolean {
        val paid = when (charm.category) {
            CharmCategory.CHARM_MAJOR -> applyUnlockMajorCharmRune(player, charm)
            CharmCategory.CHARM_MINOR -> applyUnlockMinorCharmRune(player, charm)
            else -> return false
        }
        if (!paid) {
            return false
        }

        val unlocked = bestiary.bitToggle(player.getUnlockedRunesBit(), charm, on = true)
        player.setUnlockedRunesBit(unlocked)
        return true
    }

    private fun applyUnlockMajorCharmRune(player: PlayerBestiaryState, charm: Charm): Boolean {
        val charmTier = player.getCharmTier(charm.id)
        if (bestiary.isCharmTierMax(charmTier)) {
            player.sendFYIBox("Charm at max level.")
            return false
        }

        val cost = bestiary.getCharmBuyCost(charm, charmTier)
        if (player.getCharmPoints() < cost) {
            player.sendFYIBox("You don't have enough charm points to unlock this rune.")
            return false
        }

        bestiary.addCharmPoints(player, cost, negative = true)
        bestiary.addMinorCharmEchoes(player, bestiary.getMinorCharmEchoesReward(charmTier))
        player.setCharmTier(charm.id, charmTier + 1)
        return true
    }

    private fun applyUnlockMinorCharmRune(player: PlayerBestiaryState, charm: Charm): Boolean {
        val charmTier = player.getCharmTier(charm.id)
        if (bestiary.isCharmTierMax(charmTier)) {
            player.sendFYIBox("Charm at max level.")
            return false
        }

        val cost = bestiary.getCharmBuyCost(charm, charmTier)
        if (player.getMinorCharmEchoes() < cost) {
            player.sendFYIBox("You don't have enough minor charm echoes to unlock this rune.")
            return false
        }

        bestiary.addMinorCharmEchoes(player, cost, negative = true)
        player.setCharmTier(charm.id, charmTier + 1)
        return true
    }

    /**
     * Action 1 (`iobestiary.cpp:501-533`): the rune-slot limit is 2 for free
     * players, 6 for premium and 25 with the charm expansion. A major rune
     * additionally requires the monster's full bestiary completion, and a
     * monster can only carry one major and one minor rune.
     */
    private fun applySetCharmRuneCreature(player: PlayerBestiaryState, charm: Charm, raceId: Int): Boolean {
        val usedRunes = bestiary.getCharmUsedRuneBitAll(player)
        var limitRunes = FREE_CHARM_SLOTS
        if (player.isPremium) {
            limitRunes = if (player.hasCharmExpansion) EXPANSION_CHARM_SLOTS else PREMIUM_CHARM_SLOTS
        }
        if (limitRunes <= usedRunes.size) {
            player.sendFYIBox("You don't have any charm slots available.")
            return false
        }

        val mtype = registry.getMonsterTypeByRaceId(raceId)
        if (mtype != null && charm.category == CharmCategory.CHARM_MAJOR) {
            val killedAmount = player.getBestiaryKillCount(raceId)
            if (killedAmount < mtype.bestiaryToUnlock) {
                return false
            }
        }

        val onTarget = if (mtype != null) bestiary.getCharmFromTarget(player, mtype) else PlayerCharmsByMonster()
        if ((charm.category == CharmCategory.CHARM_MAJOR && onTarget.major != CharmRune.CHARM_NONE) ||
            (charm.category == CharmCategory.CHARM_MINOR && onTarget.minor != CharmRune.CHARM_NONE)
        ) {
            val category = if (charm.category == CharmCategory.CHARM_MAJOR) "Major" else "Minor"
            player.sendFYIBox("You already have this monster set on another $category Charm!")
            return false
        }

        bestiary.setCharmRuneCreature(player, charm, raceId)
        if (!player.isPremium) {
            player.sendFYIBox(
                "Creature has been set! You are a Free player, so you benefit from up to 2 runes! " +
                    "Premium players benefit from 6 and Charm Expansion allow you to set creatures to all runes at once!.",
            )
        } else if (!player.hasCharmExpansion) {
            player.sendFYIBox(
                "Creature has been set! You are a Premium player, so you benefit from up to 6 runes! " +
                    "Charm Expansion allow you to set creatures to all runes at once!",
            )
        }
        return true
    }

    /**
     * Action 2 (`iobestiary.cpp:534-546`): the removal fee is `level * 100`,
     * reduced to 75% with the charm expansion. A failed payment leaves the rune
     * assigned.
     */
    private fun applyRemoveCharmRune(player: PlayerBestiaryState, charm: Charm): Boolean {
        var fee = player.getLevel() * 100
        if (player.hasCharmExpansion) {
            fee = (fee * 75) / 100
        }

        if (!player.removeMoney(fee.toLong())) {
            player.sendFYIBox("You don't have enough gold.")
            return false
        }

        bestiary.resetCharmRuneCreature(player, charm)
        return true
    }

    /**
     * Action 3 (`iobestiary.cpp:547-571`): resets every rune for
     * `100000 + (level > 100 ? level * 11000 : 0)`, reduced to 75% with the
     * charm expansion. A paid reset restores the charm points to their maximum,
     * resets minor echoes to 100 for promoted players (0 otherwise) and clears
     * both rune bitmasks.
     */
    private fun applyResetAllCharmRunes(player: PlayerBestiaryState): Boolean {
        val playerLevel = player.getLevel()
        var resetCost = 100000L + if (playerLevel > 100) playerLevel.toLong() * 11000L else 0L
        if (player.hasCharmExpansion) {
            resetCost = (resetCost * 75) / 100
        }

        if (!player.removeMoney(resetCost)) {
            player.sendFYIBox("You don't have enough gold.")
            return false
        }

        bestiary.resetAllCharmRuneCreatures(player)
        player.setCharmPoints(player.getMaxCharmPoints())

        val echoesResetValue = if (player.isPromoted) 100 else 0
        player.setMinorCharmEchoes(echoesResetValue)
        player.setMaxMinorCharmEchoes(echoesResetValue)
        player.setUsedRunesBit(0)
        player.setUnlockedRunesBit(0)
        return true
    }

    private companion object {
        /** `BESTY_RACE_FIRST..BESTY_RACE_LAST` (creatures_definitions.hpp:549-550). */
        val BESTIARY_RACES: List<BestiaryRace> = BestiaryRace.entries.filter {
            it.value in BestiaryRace.BESTY_RACE_AMPHIBIC.value..BestiaryRace.BESTY_RACE_INKBORN.value
        }

        /** Action byte of `parseSendBuyCharmRune` (protocolgame.cpp:4028-4037). */
        const val ACTION_UNLOCK_RUNE = 0
        const val ACTION_SET_CREATURE = 1
        const val ACTION_REMOVE_RUNE = 2
        const val ACTION_RESET_ALL = 3

        /** Rune-slot limits (iobestiary.cpp:503-507). */
        const val FREE_CHARM_SLOTS = 2
        const val PREMIUM_CHARM_SLOTS = 6
        const val EXPANSION_CHARM_SLOTS = 25
    }
}
