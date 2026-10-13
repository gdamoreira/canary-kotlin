package org.opentibia.canary.systems.prey

import java.util.Random
import java.util.TreeMap
import org.opentibia.canary.combat.CombatDamage

/**
 * Prey engine core.
 *
 * Faithful port of Canary `src/io/ioprey.cpp` / `src/io/ioprey.hpp` (classes
 * `PreySlot` and `IOPrey`), the Player-side prey state
 * (`src/creatures/players/player.cpp:10392-10491,12641-12667`) and the bonus
 * application primitives:
 *  - damage / defense: `src/creatures/combat/combat.cpp:887-909`
 *  - experience quote: `src/lua/functions/creatures/player/player_functions.cpp:964-977`
 *  - loot quote: `src/lua/functions/creatures/player/player_functions.cpp:1014-1027`
 *
 * Global singletons (`g_configManager()`, `g_game()`, `g_monsters()`,
 * `OTSYS_TIME()`, `uniform_random`) are replaced by constructor injection
 * (AGENTS.md 1.1). The Lua scripts that consume the experience/loot quote
 * primitives stay Lua (AGENTS.md 1.4).
 *
 * Deliberately out of engine scope: network serialization
 * (`ProtocolGame::sendPreyData`), database persistence
 * (`iologindata_load_player.cpp:820-840`), the metrics counters and the
 * inventory-first `Game::removeMoney` walk.
 */

/** Prey bonus ids (`src/io/ioprey.hpp:40-49`). */
enum class PreyBonus(val value: Int) {
    Damage(0),
    Defense(1),
    Experience(2),
    Loot(3),
    None(4);

    companion object {
        val FIRST: PreyBonus = Damage
        val LAST: PreyBonus = Loot

        fun fromValue(value: Int): PreyBonus = entries.first { it.value == value }
    }
}

/** Prey window state (`src/io/ioprey.hpp:30-38`). */
enum class PreyDataState(val value: Int) {
    Locked(0),
    Inactive(1),
    Active(2),
    Selection(3),
    SelectionChangeMonster(4),
    ListSelection(5),
    WildcardSelection(6),
}

/** Per-slot option (`src/io/ioprey.hpp:51-55`). */
enum class PreyOption(val value: Int) {
    None(0),
    AutomaticReroll(1),
    Locked(2),
}

/** Client action ids handled by `IOPrey::parsePreyAction` (`src/io/ioprey.hpp:57-64`). */
enum class PreyAction(val value: Int) {
    ListReroll(0),
    BonusReroll(1),
    MonsterSelection(2),
    ListAllCards(3),
    ListAllSelection(4),
    Option(5),
}

/**
 * Canary `PREY_*` configuration with the configured defaults
 * (`src/config/configmanager.cpp:123-124,331-335`).
 */
data class PreyConfig(
    val preyEnabled: Boolean = true,
    val preyFreeThirdSlot: Boolean = false,
    val preyBonusRerollPrice: Int = 1,
    val preyBonusTime: Int = 7200,
    val preyFreeRerollTime: Int = 72000,
    val preyRerollPricePerLevel: Int = 200,
    val preySelectListPrice: Int = 5,
)

/**
 * The subset of `MonsterType::MonsterInfo` (`src/creatures/monsters/monsters.hpp:75-154`)
 * that [PreySlot.reloadMonsterGrid] reads.
 */
data class PreyMonsterInfo(
    val raceid: Int = 0,
    val name: String = "",
    val experience: Long = 0,
    val bestiaryStars: Int = 0,
    val isPreyable: Boolean = true,
    val isPreyExclusive: Boolean = false,
)

/**
 * Monster-type lookup replacing `g_game().getBestiaryList()` and
 * `g_monsters().getMonsterTypeByRaceId` (AGENTS.md 1.1). Mirrors
 * [org.opentibia.canary.systems.bestiary.BestiaryRegistry]: `std::map` is
 * key-ordered, so the bestiary list is a [TreeMap] sorted by race id.
 */
class PreyBestiary(
    monsters: List<PreyMonsterInfo> = emptyList(),
) {
    private val bestiaryList = TreeMap<Int, String>()
    private val monstersByName = mutableMapOf<String, PreyMonsterInfo>()

    init {
        for (mtype in monsters) {
            registerMonster(mtype)
        }
    }

    fun registerMonster(mtype: PreyMonsterInfo) {
        monstersByName[mtype.name.lowercase()] = mtype
        if (mtype.raceid != 0) {
            bestiaryList.putIfAbsent(mtype.raceid, mtype.name)
        }
    }

    /** `Game::getBestiaryList` (game.hpp:459-461): race id to monster name, id-ordered. */
    fun getBestiaryList(): Map<Int, String> = bestiaryList

    /** `Monsters::getMonsterType` (monsters.cpp:363-372): case-insensitive name lookup. */
    fun getMonsterType(name: String): PreyMonsterInfo? = monstersByName[name.lowercase()]

    /** `Monsters::getMonsterTypeByRaceId` (monsters.cpp:375-388). */
    fun getMonsterTypeByRaceId(raceid: Int): PreyMonsterInfo? =
        bestiaryList[raceid]?.let { getMonsterType(it) }
}

/**
 * `PreySlot` (`src/io/ioprey.hpp:96-144`, `src/io/ioprey.cpp:21-131`).
 *
 * The global `uniform_random` / `OTSYS_TIME` / configuration reads become
 * constructor-injected seams; the public fields keep the C++ names so the state
 * is bit-for-bit representable by persistence and protocol code.
 */
class PreySlot(
    val id: Int,
    private val config: PreyConfig = PreyConfig(),
    private val bestiary: PreyBestiary = PreyBestiary(),
    private val uniformRandom: (Int, Int) -> Int = defaultUniformRandom(),
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    var bonus: PreyBonus = PreyBonus.None
    var state: PreyDataState = PreyDataState.Locked
    var option: PreyOption = PreyOption.None

    val raceIdList: MutableList<Int> = mutableListOf()

    var bonusRarity: Int = 1

    var selectedRaceId: Int = 0
    var bonusPercentage: Int = 0
    var bonusTimeLeft: Int = 0

    var freeRerollTimeStamp: Long = 0

    init {
        eraseBonus()
        reloadBonusValue()
        reloadBonusType()
        freeRerollTimeStamp = now() + config.preyFreeRerollTime * 1000L
    }

    /** `PreySlot::isOccupied` (ioprey.hpp:102-104). */
    fun isOccupied(): Boolean = selectedRaceId != 0 && bonusTimeLeft > 0

    /** `PreySlot::canSelect` (ioprey.hpp:106-108). */
    fun canSelect(): Boolean =
        state == PreyDataState.Selection ||
            state == PreyDataState.SelectionChangeMonster ||
            state == PreyDataState.ListSelection ||
            state == PreyDataState.Inactive

    /** `PreySlot::eraseBonus` (ioprey.hpp:110-120). */
    fun eraseBonus(maintainBonus: Boolean = false) {
        if (!maintainBonus) {
            bonus = PreyBonus.None
            bonusPercentage = 5
            bonusRarity = 1
        }
        state = PreyDataState.Selection
        option = PreyOption.None
        selectedRaceId = 0
        bonusTimeLeft = 0
    }

    /** `PreySlot::removeMonsterType` (ioprey.hpp:122-124): erases every occurrence. */
    fun removeMonsterType(raceId: Int) {
        raceIdList.removeAll { it == raceId }
    }

    /** `PreySlot::reloadBonusType` (ioprey.cpp:29-39). */
    fun reloadBonusType() {
        if (bonusRarity == 10) {
            val bonusTmp = bonus
            while (bonus == bonusTmp) {
                bonus = randomBonus()
            }
            return
        }

        bonus = randomBonus()
    }

    /** `PreySlot::reloadBonusValue` (ioprey.cpp:41-55). */
    fun reloadBonusValue() {
        if (bonusRarity >= 9) {
            bonusRarity = 10
        } else {
            // Every time you roll it will increase the rarity (star).
            bonusRarity = uniformRandom(bonusRarity + 1, 10)
        }

        bonusPercentage = when (bonus) {
            PreyBonus.Damage -> 2 * bonusRarity + 5
            PreyBonus.Defense -> 2 * bonusRarity + 10
            else -> 3 * bonusRarity + 10
        }
    }

    /** `PreySlot::reloadMonsterGrid` (ioprey.cpp:57-131). */
    fun reloadMonsterGrid(blackList: List<Int>, level: Int) {
        raceIdList.clear()

        if (!config.preyEnabled) {
            return
        }

        // Disabling the prey system if the server has fewer than 36 registered
        // bestiary monsters: random lists without duplications are impossible.
        val bestiaryList = bestiary.getBestiaryList()
        if (bestiaryList.size < PreyEngine.MIN_BESTIARY_SIZE) {
            return
        }

        val levelStage = level / 100
        val stages = when (levelStage) {
            0 -> intArrayOf(3, 3, 2, 1) // From level 0 to 99
            in 1..2 -> intArrayOf(1, 3, 3, 2) // From level 100 to 299
            in 3..4 -> intArrayOf(1, 2, 3, 3) // From level 300 to 499
            else -> intArrayOf(1, 1, 3, 4) // From level 500 to ...
        }
        var remainingOne = stages[0]
        var remainingTwo = stages[1]
        var remainingThree = stages[2]
        var remainingFour = stages[3]

        var tries = 0
        val raceIds = bestiaryList.keys.toList()
        val maxIndex = raceIds.size - 1
        // The C++ parameter is by-value; the local copy accumulates this slot's picks.
        val picked = blackList.toMutableList()
        while (raceIdList.size < PreyEngine.MONSTER_GRID_SIZE) {
            val raceId = raceIds[uniformRandom(0, maxIndex)]
            tries++

            if (picked.contains(raceId)) {
                continue
            }

            picked.add(raceId)
            val mtype = bestiary.getMonsterTypeByRaceId(raceId)
            if (mtype == null || mtype.experience == 0L || !mtype.isPreyable || mtype.isPreyExclusive) {
                continue
            } else if (remainingOne != 0 && mtype.bestiaryStars <= 1) {
                raceIdList.add(raceId)
                remainingOne--
            } else if (remainingTwo != 0 && mtype.bestiaryStars == 2) {
                raceIdList.add(raceId)
                remainingTwo--
            } else if (remainingThree != 0 && mtype.bestiaryStars == 3) {
                raceIdList.add(raceId)
                remainingThree--
            } else if (remainingFour != 0 && mtype.bestiaryStars >= 4) {
                raceIdList.add(raceId)
                remainingFour--
            } else if (tries >= 10) {
                raceIdList.add(raceId)
                tries = 0
            }
        }
    }

    private fun randomBonus(): PreyBonus =
        PreyBonus.fromValue(uniformRandom(PreyBonus.FIRST.value, PreyBonus.LAST.value))
}

/**
 * Self-contained holder for the Player prey state so the engine can be
 * exercised without touching `Player.kt` (AGENTS.md 1.1).
 *
 * Transcribed from Canary:
 *  - `src/creatures/players/player.hpp` (`preys`, `preyCards`, level, bank)
 *  - `src/creatures/players/player.cpp:10392-10491` (prey accessors)
 *  - `src/creatures/players/player.cpp:12641-12667` (initialize/remove slot)
 */
class PlayerPreyState(
    private val config: PreyConfig = PreyConfig(),
) {
    val preys: MutableList<PreySlot> = mutableListOf()

    var isPremium: Boolean = false

    /** `Player::level` (player.hpp:1786); `Player::getLevel` (player.hpp:574). */
    private var level: Int = 1

    /** `Player::bankBalance` (player.hpp:1726); `Player::getBankBalance` (player.hpp:354). */
    private var bankBalance: Long = 0

    /** `Player::preyCards`; `Player::getPreyCards` (player.cpp:10454-10456). */
    private var preyCards: Long = 0

    /** Observable sinks for messages / reloads the engine emits. */
    val textMessages: MutableList<String> = mutableListOf()
    val dialogMessages: MutableList<String> = mutableListOf()
    val reloadedPreySlots: MutableList<Int> = mutableListOf()

    /** Latest `Player::sendPreyTimeLeft` payloads as `slotId to seconds`. */
    val sentPreyTimeLeft: MutableList<Pair<Int, Int>> = mutableListOf()

    /** `Player::getPreySlotById` (player.cpp:10415-10424). */
    fun getPreySlotById(slotId: Int): PreySlot? = preys.firstOrNull { it.id == slotId }

    /** `Player::setPreySlotClass` (player.cpp:10426-10433). */
    fun setPreySlotClass(slot: PreySlot): Boolean {
        if (getPreySlotById(slot.id) != null) {
            return false
        }
        preys.add(slot)
        return true
    }

    /** `Player::removePreySlotById` (player.cpp:12661-12667). */
    fun removePreySlotById(slotId: Int) {
        preys.removeAll { it.id == slotId }
    }

    /** `Player::usePreyCards` (player.cpp:10435-10445). */
    fun usePreyCards(amount: Int): Boolean {
        if (preyCards < amount) {
            return false
        }

        preyCards -= amount
        return true
    }

    /** `Player::addPreyCards` (player.cpp:10447-10452). */
    fun addPreyCards(amount: Long) {
        preyCards += amount
    }

    /** `Player::getPreyCards` (player.cpp:10454-10456). */
    fun getPreyCards(): Long = preyCards

    /** `Player::getPreyRerollPrice` (player.cpp:10458-10460): level * PREY_REROLL_PRICE_LEVEL. */
    fun getPreyRerollPrice(): Long = level.toLong() * config.preyRerollPricePerLevel

    /**
     * `Player::getPreyBlackList` (player.cpp:10462-10476). Unlike the task
     * hunting black list, an occupied slot still contributes its full list.
     */
    fun getPreyBlackList(): List<Int> {
        val result = mutableListOf<Int>()
        for (slot in preys) {
            if (slot.isOccupied()) {
                result.add(slot.selectedRaceId)
            }
            result.addAll(slot.raceIdList)
        }
        return result
    }

    /** `Player::getPreyWithMonster` (player.cpp:10478-10491). */
    fun getPreyWithMonster(raceId: Int): PreySlot? {
        if (!config.preyEnabled) {
            return null
        }

        return preys.firstOrNull { it.selectedRaceId == raceId }
    }

    /** `Player::reloadPreySlot` (player.cpp:10408-10413): protocol send is a recorded no-op. */
    fun reloadPreySlot(slotId: Int) {
        if (config.preyEnabled) {
            reloadedPreySlots.add(slotId)
        }
    }

    /** `Player::sendPreyTimeLeft` (player.cpp:10402-10406): protocol send is a recorded no-op. */
    fun sendPreyTimeLeft(slot: PreySlot) {
        if (config.preyEnabled) {
            sentPreyTimeLeft.add(slot.id to slot.bonusTimeLeft)
        }
    }

    /** `Player::sendTextMessage` sink (used by `IOPrey::checkPlayerPreys`). */
    fun sendTextMessage(message: String) {
        textMessages.add(message)
    }

    /** `Player::sendMessageDialog` sink (player.cpp:12608-12612). */
    fun sendMessageDialog(message: String) {
        dialogMessages.add(message)
    }

    /** `Player::getLevel` (player.hpp:574-576). */
    fun getLevel(): Int = level

    /** `Player::setLevel` (player.hpp:577-579). */
    fun setLevel(newLevel: Int) {
        level = newLevel
    }

    /** `Player::getBankBalance` (player.hpp:354). */
    fun getBankBalance(): Long = bankBalance

    /** `Player::setBankBalance` (player.hpp:355). */
    fun setBankBalance(balance: Long) {
        bankBalance = balance
    }

    /**
     * Wallet seam for `g_game().removeMoney(player, amount, 0, true)`
     * (`src/game/game.cpp:3299-3365`): Canary collects inventory money first and
     * falls back to the bank balance; both are collapsed into [bankBalance]
     * here, exactly like [org.opentibia.canary.systems.bestiary.PlayerBestiaryState].
     * A zero amount succeeds without mutation (game.cpp:3305-3307) and an
     * unaffordable amount fails without touching the balance (game.cpp:3313-3315).
     */
    fun removeMoney(amount: Long): Boolean {
        if (amount == 0L) {
            return true
        }
        if (bankBalance < amount) {
            return false
        }
        bankBalance -= amount
        return true
    }
}

/**
 * `IOPrey` engine (`src/io/ioprey.cpp:250-403,541-639`) plus the prey bonus
 * application primitives.
 *
 * `IOPrey::getTaskRewardOption` and the network buffer of
 * `initializeTaskHuntOptions` belong to the task hunting engine / protocol.
 */
class PreyEngine(
    private val config: PreyConfig = PreyConfig(),
    private val bestiary: PreyBestiary = PreyBestiary(),
    private val uniformRandom: (Int, Int) -> Int = defaultUniformRandom(),
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    /** `Player::initializePrey` (player.cpp:12641-12659). */
    fun initializePrey(player: PlayerPreyState) {
        if (player.preys.isEmpty()) {
            for (slotId in PREY_SLOT_FIRST..PREY_SLOT_LAST) {
                val slot = PreySlot(slotId, config, bestiary, uniformRandom, now)
                if (!config.preyEnabled) {
                    slot.state = PreyDataState.Inactive
                } else if (slot.id == PREY_SLOT_THREE && !config.preyFreeThirdSlot) {
                    slot.state = PreyDataState.Locked
                } else if (slot.id == PREY_SLOT_TWO && !player.isPremium) {
                    slot.state = PreyDataState.Locked
                } else {
                    slot.state = PreyDataState.Selection
                    slot.reloadMonsterGrid(player.getPreyBlackList(), player.getLevel())
                }

                player.setPreySlotClass(slot)
            }
        }
    }

    /** `IOPrey::checkPlayerPreys` (ioprey.cpp:255-297). */
    fun checkPlayerPreys(player: PlayerPreyState, amount: Int) {
        for (slotId in PREY_SLOT_FIRST..PREY_SLOT_LAST) {
            val slot = player.getPreySlotById(slotId)
            if (slot == null || !slot.isOccupied()) {
                continue
            }

            if (slot.bonusTimeLeft <= amount) {
                if (slot.option == PreyOption.AutomaticReroll) {
                    if (player.usePreyCards(config.preyBonusRerollPrice)) {
                        slot.reloadBonusType()
                        slot.reloadBonusValue()
                        slot.bonusTimeLeft = config.preyBonusTime
                        player.sendTextMessage("Your prey bonus type and time has been succesfully reseted.")
                        player.reloadPreySlot(slotId)
                        continue
                    }

                    player.sendTextMessage("You don't have enought prey cards to enable automatic reroll when your slot expire.")
                } else if (slot.option == PreyOption.Locked) {
                    if (player.usePreyCards(config.preySelectListPrice)) {
                        slot.bonusTimeLeft = config.preyBonusTime
                        player.sendTextMessage("Your prey bonus time has been succesfully reseted.")
                        player.reloadPreySlot(slotId)
                        continue
                    }

                    player.sendTextMessage("You don't have enought prey cards to lock monster and bonus when the slot expire.")
                } else {
                    slot.reloadMonsterGrid(player.getPreyBlackList(), player.getLevel())
                    player.sendTextMessage("Your prey bonus has expired.")
                }

                slot.eraseBonus()
                player.reloadPreySlot(slotId)
            } else {
                slot.bonusTimeLeft -= amount
                player.sendPreyTimeLeft(slot)
            }
        }
    }

    /** `IOPrey::parsePreyAction` (ioprey.cpp:299-403). */
    fun parsePreyAction(
        player: PlayerPreyState,
        slotId: Int,
        action: PreyAction,
        option: PreyOption = PreyOption.None,
        index: Int = -1,
        raceId: Int = 0,
    ) {
        val slot = player.getPreySlotById(slotId)
        if (slot == null || slot.state == PreyDataState.Locked) {
            player.sendMessageDialog("To unlock this prey slot first you must buy it on store.")
            return
        }

        when (action) {
            PreyAction.ListReroll -> {
                val currentTime = now()
                if (slot.freeRerollTimeStamp > currentTime && !player.removeMoney(player.getPreyRerollPrice())) {
                    player.sendMessageDialog("You don't have enought money to reroll the prey slot.")
                    return
                } else if (slot.freeRerollTimeStamp <= currentTime) {
                    slot.freeRerollTimeStamp = currentTime + config.preyFreeRerollTime * 1000L
                }
                // else: `balance_decrease` metric (not ported)

                slot.eraseBonus(true)
                if (slot.bonus != PreyBonus.None) {
                    slot.state = PreyDataState.SelectionChangeMonster
                }
                slot.reloadMonsterGrid(player.getPreyBlackList(), player.getLevel())
            }

            PreyAction.ListAllCards -> {
                if (!player.usePreyCards(config.preySelectListPrice)) {
                    player.sendMessageDialog("You don't have enought prey cards to choose a monsters on the list.")
                    return
                }

                slot.bonusTimeLeft = 0
                slot.selectedRaceId = 0
                slot.state = PreyDataState.ListSelection
            }

            PreyAction.ListAllSelection -> {
                val mtype = bestiary.getMonsterTypeByRaceId(raceId)
                if (slot.isOccupied()) {
                    player.sendMessageDialog("You already have an active monster on this prey slot.")
                    return
                } else if (!slot.canSelect() || slot.state != PreyDataState.ListSelection) {
                    player.sendMessageDialog("There was an error while processing your action. Please try reopening the prey window.")
                    return
                } else if (player.getPreyWithMonster(raceId) != null) {
                    player.sendMessageDialog("This creature is already selected on another slot.")
                    return
                } else if (mtype != null && !mtype.isPreyable) {
                    player.sendMessageDialog("This creature can't be select on prey. Please choose another one.")
                    return
                }

                if (slot.bonus == PreyBonus.None) {
                    slot.reloadBonusType()
                    slot.reloadBonusValue()
                }

                slot.state = PreyDataState.Active
                slot.selectedRaceId = raceId
                slot.removeMonsterType(raceId)
                slot.bonusTimeLeft = config.preyBonusTime
            }

            PreyAction.BonusReroll -> {
                if (!slot.isOccupied()) {
                    player.sendMessageDialog("You don't have any active monster on this prey slot.")
                    return
                } else if (!player.usePreyCards(config.preyBonusRerollPrice)) {
                    player.sendMessageDialog("You don't have enought prey cards to reroll this prey slot bonus type.")
                    return
                }

                slot.reloadBonusType()
                slot.reloadBonusValue()
                slot.bonusTimeLeft = config.preyBonusTime
            }

            PreyAction.MonsterSelection -> {
                if (slot.isOccupied()) {
                    player.sendMessageDialog("You already have an active monster on this prey slot.")
                    return
                } else if (!slot.canSelect() || index == -1 || (index + 1) > slot.raceIdList.size) {
                    player.sendMessageDialog("There was an error while processing your action. Please try reopening the prey window.")
                    return
                } else if (player.getPreyWithMonster(slot.raceIdList[index]) != null) {
                    player.sendMessageDialog("This creature is already selected on another slot.")
                    return
                }

                if (slot.bonus == PreyBonus.None) {
                    slot.reloadBonusType()
                    slot.reloadBonusValue()
                }
                slot.state = PreyDataState.Active
                slot.selectedRaceId = slot.raceIdList[index]
                slot.removeMonsterType(slot.selectedRaceId)
                slot.bonusTimeLeft = config.preyBonusTime
            }

            PreyAction.Option -> {
                if (option == PreyOption.AutomaticReroll && player.getPreyCards() < config.preyBonusRerollPrice) {
                    player.sendMessageDialog("You don't have enought prey cards to enable automatic reroll when your slot expire.")
                    return
                } else if (option == PreyOption.Locked && player.getPreyCards() < config.preySelectListPrice) {
                    player.sendMessageDialog("You don't have enought prey cards to lock monster and bonus when the slot expire.")
                    return
                }

                slot.option = option
            }
        }

        player.reloadPreySlot(slotId)
    }

    /**
     * `src/creatures/combat/combat.cpp:887-893`: the player attacks a monster
     * with an active damage prey. Note Canary's `(value * percentage) / 100` is
     * integer division (the surrounding `std::ceil` is a no-op on an int).
     */
    fun applyDamageBonus(damage: CombatDamage, player: PlayerPreyState, raceId: Int) {
        val slot = player.getPreyWithMonster(raceId) ?: return
        if (slot.isOccupied() && slot.bonus == PreyBonus.Damage && slot.bonusTimeLeft > 0) {
            damage.primaryValue += damage.primaryValue * slot.bonusPercentage / 100
            damage.secondaryValue += damage.secondaryValue * slot.bonusPercentage / 100
        }
    }

    /** `src/creatures/combat/combat.cpp:902-909`: a monster attacks the player with an active defense prey. */
    fun applyDefenseBonus(damage: CombatDamage, player: PlayerPreyState, raceId: Int) {
        val slot = player.getPreyWithMonster(raceId) ?: return
        if (slot.isOccupied() && slot.bonus == PreyBonus.Defense && slot.bonusTimeLeft > 0) {
            damage.primaryValue -= damage.primaryValue * slot.bonusPercentage / 100
            damage.secondaryValue -= damage.secondaryValue * slot.bonusPercentage / 100
        }
    }

    /**
     * Engine primitive behind `player:getPreyExperiencePercentage(raceId)`
     * (`player_functions.cpp:964-977`); the `data/events/scripts/player.lua`
     * caller stays Lua (AGENTS.md 1.4).
     */
    fun getPreyExperiencePercentage(player: PlayerPreyState, raceId: Int): Int {
        val slot = player.getPreyWithMonster(raceId)
        return if (slot != null && slot.isOccupied() && slot.bonus == PreyBonus.Experience && slot.bonusTimeLeft > 0) {
            100 + slot.bonusPercentage
        } else {
            100
        }
    }

    /**
     * Engine primitive behind `player:getPreyLootPercentage(raceId)`
     * (`player_functions.cpp:1014-1027`); the loot callback stays Lua.
     */
    fun getPreyLootPercentage(player: PlayerPreyState, raceId: Int): Int {
        val slot = player.getPreyWithMonster(raceId)
        return if (slot != null && slot.isOccupied() && slot.bonus == PreyBonus.Loot) {
            slot.bonusPercentage
        } else {
            0
        }
    }

    companion object {
        /** `PreySlot_First` / `PreySlot_Last` (ioprey.hpp:21-28). */
        const val PREY_SLOT_FIRST = 0
        const val PREY_SLOT_TWO = 1
        const val PREY_SLOT_THREE = 2
        const val PREY_SLOT_LAST = 2

        /** `while (raceIdList.size() < 9)` (ioprey.cpp:102). */
        const val MONSTER_GRID_SIZE = 9

        /** `bestiary.size() < 36` guard (ioprey.cpp:68). */
        const val MIN_BESTIARY_SIZE = 36
    }
}

/**
 * Faithful port of Canary `uniform_random` (`src/utils/tools.cpp:455-464`):
 * swaps inverted bounds and draws inclusively.
 */
fun defaultUniformRandom(random: Random = Random()): (Int, Int) -> Int = { minNumber, maxNumber ->
    if (minNumber == maxNumber) {
        minNumber
    } else {
        val lower = minOf(minNumber, maxNumber)
        val upper = maxOf(minNumber, maxNumber)
        lower + random.nextInt(upper - lower + 1)
    }
}
