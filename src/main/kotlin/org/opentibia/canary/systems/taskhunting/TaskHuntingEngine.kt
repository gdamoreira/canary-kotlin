package org.opentibia.canary.systems.taskhunting

import java.util.Random
import java.util.TreeMap

/**
 * Task hunting engine core.
 *
 * Faithful port of Canary `src/io/ioprey.cpp` / `src/io/ioprey.hpp` (classes
 * `TaskHuntingSlot`, `TaskHuntingOption` and `IOPrey`) plus the Player-side task
 * state (`src/creatures/players/player.cpp:10493-10681`) and the kill progress
 * (`Player::addHuntingTaskKill`, player.cpp:6625-6640).
 *
 * Global singletons (`g_configManager()`, `g_game()`, `g_monsters()`,
 * `OTSYS_TIME()`, `uniform_random`) are replaced by constructor injection
 * (AGENTS.md 1.1).
 *
 * Deliberately out of engine scope: the legacy `0xBA` taskboard base-data
 * packet built by `IOPrey::initializeTaskHuntOptions` (ioprey.cpp:576-604),
 * `ProtocolGame::sendTaskHuntingData`, database persistence
 * (`iologindata_load_player.cpp:870-890`) and the metrics counters.
 */

/** Task window state (`src/io/ioprey.hpp:66-73`). */
enum class PreyTaskDataState(val value: Int) {
    Locked(0),
    Inactive(1),
    Selection(2),
    ListSelection(3),
    Active(4),
    Completed(5),
}

/** Client action ids handled by `IOPrey::parseTaskHuntingAction` (`src/io/ioprey.hpp:75-82`). */
enum class PreyTaskAction(val value: Int) {
    ListReroll(0),
    RewardsReroll(1),
    ListAllCards(2),
    MonsterSelection(3),
    Cancel(4),
    Claim(5),
}

/** Task difficulty derived from bestiary stars (`src/io/ioprey.hpp:84-92`). */
enum class PreyTaskDifficult(val value: Int) {
    None(0),
    Easy(1),
    Medium(2),
    Hard(3);

    companion object {
        val FIRST: PreyTaskDifficult = Easy
        val LAST: PreyTaskDifficult = Hard

        fun fromValue(value: Int): PreyTaskDifficult = entries.first { it.value == value }
    }
}

/**
 * Canary `TASK_HUNTING_*` configuration with the configured defaults
 * (`src/config/configmanager.cpp:144-145,362-366`).
 */
data class TaskHuntingConfig(
    val taskHuntingEnabled: Boolean = true,
    val taskHuntingFreeThirdSlot: Boolean = false,
    val taskHuntingBonusRerollPrice: Int = 1,
    val taskHuntingFreeRerollTime: Int = 72000,
    val taskHuntingLimitExhaust: Int = 72000,
    val taskHuntingRerollPricePerLevel: Int = 200,
    val taskHuntingSelectListPrice: Int = 5,
)

/**
 * The subset of `MonsterType::MonsterInfo` (`src/creatures/monsters/monsters.hpp:75-154`)
 * that the task hunting engine reads.
 */
data class TaskMonsterInfo(
    val raceid: Int = 0,
    val name: String = "",
    val experience: Long = 0,
    val bestiaryStars: Int = 0,
    val bestiaryToUnlock: Int = 0,
    val isPreyable: Boolean = true,
    val isPreyExclusive: Boolean = false,
)

/**
 * Monster-type lookup replacing `g_game().getBestiaryList()` and
 * `g_monsters().getMonsterTypeByRaceId` (AGENTS.md 1.1), mirroring
 * [org.opentibia.canary.systems.bestiary.BestiaryRegistry].
 */
class TaskHuntingBestiary(
    monsters: List<TaskMonsterInfo> = emptyList(),
) {
    private val bestiaryList = TreeMap<Int, String>()
    private val monstersByName = mutableMapOf<String, TaskMonsterInfo>()

    init {
        for (mtype in monsters) {
            registerMonster(mtype)
        }
    }

    fun registerMonster(mtype: TaskMonsterInfo) {
        monstersByName[mtype.name.lowercase()] = mtype
        if (mtype.raceid != 0) {
            bestiaryList.putIfAbsent(mtype.raceid, mtype.name)
        }
    }

    /** `Game::getBestiaryList` (game.hpp:459-461): race id to monster name, id-ordered. */
    fun getBestiaryList(): Map<Int, String> = bestiaryList

    /** `Monsters::getMonsterType` (monsters.cpp:363-372): case-insensitive name lookup. */
    fun getMonsterType(name: String): TaskMonsterInfo? = monstersByName[name.lowercase()]

    /** `Monsters::getMonsterTypeByRaceId` (monsters.cpp:375-388). */
    fun getMonsterTypeByRaceId(raceid: Int): TaskMonsterInfo? =
        bestiaryList[raceid]?.let { getMonsterType(it) }
}

/** `TaskHuntingOption` (`src/io/ioprey.hpp:199-212`), `IOPrey::taskOption` entries. */
data class TaskHuntingOption(
    var difficult: PreyTaskDifficult = PreyTaskDifficult.None,
    var rarity: Int = 1,
    var firstKills: Int = 0,
    var secondKills: Int = 0,
    var firstReward: Int = 0,
    var secondReward: Int = 0,
)

/**
 * `TaskHuntingSlot` (`src/io/ioprey.hpp:146-197`, `src/io/ioprey.cpp:134-248`).
 * Constructor / RNG / clock seams replace the Canary globals.
 */
class TaskHuntingSlot(
    val id: Int,
    private val config: TaskHuntingConfig = TaskHuntingConfig(),
    private val bestiary: TaskHuntingBestiary = TaskHuntingBestiary(),
    private val uniformRandom: (Int, Int) -> Int = defaultUniformRandom(),
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    var state: PreyTaskDataState = PreyTaskDataState.Inactive

    var upgrade: Boolean = false

    var rarity: Int = 1

    var selectedRaceId: Int = 0
    var currentKills: Int = 0

    var disabledUntilTimeStamp: Long = 0
    var freeRerollTimeStamp: Long = 0

    val raceIdList: MutableList<Int> = mutableListOf()

    init {
        freeRerollTimeStamp = now() + config.taskHuntingFreeRerollTime * 1000L
    }

    /** `TaskHuntingSlot::isOccupied` (ioprey.hpp:152-154). */
    fun isOccupied(): Boolean = selectedRaceId != 0

    /** `TaskHuntingSlot::canSelect` (ioprey.hpp:156-158). */
    fun canSelect(): Boolean =
        state == PreyTaskDataState.Selection || state == PreyTaskDataState.ListSelection

    /** `TaskHuntingSlot::eraseTask` (ioprey.hpp:160-166). */
    fun eraseTask() {
        upgrade = false
        state = PreyTaskDataState.Selection
        selectedRaceId = 0
        currentKills = 0
        rarity = 1
    }

    /** `TaskHuntingSlot::removeMonsterType` (ioprey.hpp:168-170): erases every occurrence. */
    fun removeMonsterType(raceId: Int) {
        raceIdList.removeAll { it == raceId }
    }

    /** `TaskHuntingSlot::isCreatureOnList` (ioprey.hpp:172-178). */
    fun isCreatureOnList(raceId: Int): Boolean = raceIdList.contains(raceId)

    /** `TaskHuntingSlot::reloadReward` (ioprey.cpp:214-248). */
    fun reloadReward() {
        if (!config.taskHuntingEnabled) {
            return
        }

        if (rarity >= 4) {
            rarity = 5
            return
        }

        val chance = when (rarity) {
            0 -> uniformRandom(0, 100)
            1 -> uniformRandom(0, 70)
            2 -> uniformRandom(0, 45)
            3 -> uniformRandom(0, 20)
            else -> return
        }

        rarity = when {
            chance <= 5 -> 5
            chance <= 20 -> 4
            chance <= 45 -> 3
            chance <= 70 -> 2
            else -> 1
        }
    }

    /** `TaskHuntingSlot::reloadMonsterGrid` (ioprey.cpp:139-212). */
    fun reloadMonsterGrid(blackList: List<Int>, level: Int) {
        raceIdList.clear()

        if (!config.taskHuntingEnabled) {
            return
        }

        // Disabling task hunting if the server has fewer than 36 registered
        // bestiary monsters (ioprey.cpp:146-152, silent return).
        val bestiaryList = bestiary.getBestiaryList()
        if (bestiaryList.size < TaskHuntingEngine.MIN_BESTIARY_SIZE) {
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
        while (raceIdList.size < TaskHuntingEngine.MONSTER_GRID_SIZE) {
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
}

/**
 * Self-contained holder for the Player task hunting state so the engine can be
 * exercised without touching `Player.kt` (AGENTS.md 1.1).
 *
 * Transcribed from Canary:
 *  - `src/creatures/players/player.hpp` (`taskHunting`, `taskHuntingPoints`)
 *  - `src/creatures/players/player.cpp:10493-10681` (accessors + points)
 *  - `src/creatures/players/player.cpp:10596-10612` (reload / slot by id)
 *
 * `Player::isCreatureUnlockedOnTaskHunting` (player.cpp:10527-10533) reads the
 * bestiary kill count, injected here as the [bestiaryKillCount] seam.
 */
class PlayerTaskHuntingState(
    private val config: TaskHuntingConfig = TaskHuntingConfig(),
    private val bestiaryKillCount: (Int) -> Int = { 0 },
) {
    val taskHunting: MutableList<TaskHuntingSlot> = mutableListOf()

    var isPremium: Boolean = false

    /** `Player::level` (player.hpp:1786); `Player::getLevel` (player.hpp:574). */
    private var level: Int = 1

    /** `Player::bankBalance` (player.hpp:1726); `Player::getBankBalance` (player.hpp:354). */
    private var bankBalance: Long = 0

    /** `Player::preyCards`; task hunting spends the same card resource (player.cpp:10435-10452). */
    private var preyCards: Long = 0

    /** `Player::taskHuntingPoints`; `Player::getTaskHuntingPoints` (player.cpp:10660-10662). */
    private var taskHuntingPoints: Long = 0

    /** Observable sinks for messages / reloads the engine emits. */
    val textMessages: MutableList<String> = mutableListOf()
    val dialogMessages: MutableList<String> = mutableListOf()
    val reloadedTaskSlots: MutableList<Int> = mutableListOf()

    /** `Player::getTaskHuntingSlotById` (player.cpp:10603-10612). */
    fun getTaskHuntingSlotById(slotId: Int): TaskHuntingSlot? = taskHunting.firstOrNull { it.id == slotId }

    /** `Player::setTaskHuntingSlotClass` (player.cpp:10535-10542). */
    fun setTaskHuntingSlotClass(slot: TaskHuntingSlot): Boolean {
        if (getTaskHuntingSlotById(slot.id) != null) {
            return false
        }
        taskHunting.add(slot)
        return true
    }

    /**
     * `Player::getTaskHuntingWithCreature` (player.cpp:10668-10681). The
     * `TASK_HUNTING_ENABLED` gate lives here, exactly like Canary.
     */
    fun getTaskHuntingWithCreature(raceId: Int): TaskHuntingSlot? {
        if (!config.taskHuntingEnabled) {
            return null
        }

        return taskHunting.firstOrNull { it.selectedRaceId == raceId }
    }

    /**
     * `Player::getTaskHuntingBlackList` (player.cpp:10614-10628). Unlike the
     * prey list, an occupied slot contributes only its selected race id.
     */
    fun getTaskHuntingBlackList(): List<Int> {
        val result = mutableListOf<Int>()
        for (slot in taskHunting) {
            if (slot.isOccupied()) {
                result.add(slot.selectedRaceId)
            } else {
                result.addAll(slot.raceIdList)
            }
        }
        return result
    }

    /** `Player::getTaskHuntingRerollPrice` (player.cpp:10664-10666): level * TASK_HUNTING_REROLL_PRICE_LEVEL. */
    fun getTaskHuntingRerollPrice(): Long = level.toLong() * config.taskHuntingRerollPricePerLevel

    /** `Player::addTaskHuntingPoints` (player.cpp:10641-10646). */
    fun addTaskHuntingPoints(amount: Long) {
        taskHuntingPoints += amount
    }

    /** `Player::useTaskHuntingPoints` (player.cpp:10648-10658). */
    fun useTaskHuntingPoints(amount: Long): Boolean {
        if (taskHuntingPoints < amount) {
            return false
        }

        taskHuntingPoints -= amount
        return true
    }

    /** `Player::getTaskHuntingPoints` (player.cpp:10660-10662). */
    fun getTaskHuntingPoints(): Long = taskHuntingPoints

    /** `Player::isCreatureUnlockedOnTaskHunting` (player.cpp:10527-10533). */
    fun isCreatureUnlockedOnTaskHunting(mtype: TaskMonsterInfo?): Boolean {
        if (mtype == null) {
            return false
        }

        return bestiaryKillCount(mtype.raceid) >= mtype.bestiaryToUnlock
    }

    /** `Player::reloadTaskSlot` (player.cpp:10596-10601): protocol send is a recorded no-op. */
    fun reloadTaskSlot(slotId: Int) {
        if (config.taskHuntingEnabled) {
            reloadedTaskSlots.add(slotId)
        }
    }

    /** `Player::usePreyCards` (player.cpp:10435-10445), shared by task hunting. */
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

    /** `Player::sendTextMessage` sink (used by `Player::addHuntingTaskKill`). */
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
     * (`src/game/game.cpp:3299-3365`), identical semantics to
     * [org.opentibia.canary.systems.prey.PlayerPreyState.removeMoney].
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
 * `IOPrey` task hunting engine (`src/io/ioprey.cpp:405-639`).
 */
class TaskHuntingEngine(
    private val config: TaskHuntingConfig = TaskHuntingConfig(),
    private val bestiary: TaskHuntingBestiary = TaskHuntingBestiary(),
    private val uniformRandom: (Int, Int) -> Int = defaultUniformRandom(),
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    /** `IOPrey::taskOption` (ioprey.hpp:239). */
    val taskOption: MutableList<TaskHuntingOption> = mutableListOf()

    init {
        initializeTaskHuntOptions()
    }

    /**
     * `IOPrey::initializeTaskHuntOptions` (ioprey.cpp:541-574): the option table
     * only; the legacy `0xBA` client base-data packet is protocol scope.
     *
     * Kill stage is the multiplier for kills and rewards on task hunting.
     */
    fun initializeTaskHuntOptions() {
        if (!config.taskHuntingEnabled) {
            return
        }

        val killStage = KILL_STAGE
        val limitOfStars = LIMIT_OF_STARS
        var kills = killStage
        for (difficulty in PreyTaskDifficult.FIRST.value..PreyTaskDifficult.LAST.value) {
            var reward = (10 * kills) / killStage
            for (star in 1..limitOfStars) {
                taskOption.add(
                    TaskHuntingOption(
                        difficult = PreyTaskDifficult.fromValue(difficulty),
                        rarity = star,
                        firstKills = kills,
                        firstReward = reward,
                        secondKills = kills * 2,
                        secondReward = reward * 2,
                    ),
                )
                reward = (reward * (115 + difficulty * limitOfStars)) / 100
            }
            kills *= 4
        }
    }

    /** `Player::initializeTaskHunting` (player.cpp:10495-10525), minus the legacy base-data send. */
    fun initializeTaskHunting(player: PlayerTaskHuntingState) {
        if (player.taskHunting.isEmpty()) {
            for (slotId in PREY_SLOT_FIRST..PREY_SLOT_LAST) {
                val slot = TaskHuntingSlot(slotId, config, bestiary, uniformRandom, now)
                if (!config.taskHuntingEnabled) {
                    slot.state = PreyTaskDataState.Inactive
                } else if (slot.id == PREY_SLOT_THREE && !config.taskHuntingFreeThirdSlot) {
                    slot.state = PreyTaskDataState.Locked
                } else if (slot.id == PREY_SLOT_TWO && !player.isPremium) {
                    slot.state = PreyTaskDataState.Locked
                } else {
                    slot.state = PreyTaskDataState.Selection
                    slot.reloadMonsterGrid(player.getTaskHuntingBlackList(), player.getLevel())
                }

                player.setTaskHuntingSlotClass(slot)
            }
        }
    }

    /** `IOPrey::getTaskRewardOption` (ioprey.cpp:611-639). */
    fun getTaskRewardOption(slot: TaskHuntingSlot?): TaskHuntingOption? {
        if (slot == null) {
            return null
        }

        val mtype = bestiary.getMonsterTypeByRaceId(slot.selectedRaceId) ?: return null

        val difficult = when {
            mtype.bestiaryStars <= 1 -> PreyTaskDifficult.Easy
            mtype.bestiaryStars <= 3 -> PreyTaskDifficult.Medium
            else -> PreyTaskDifficult.Hard
        }

        return taskOption.firstOrNull { it.difficult == difficult && it.rarity == slot.rarity }
    }

    /**
     * `Player::addHuntingTaskKill` (player.cpp:6625-6640), exposed on the engine
     * because the reward-option table lives here (`IOPrey::taskOption`).
     */
    fun addHuntingTaskKill(player: PlayerTaskHuntingState, mtype: TaskMonsterInfo) {
        val taskSlot = player.getTaskHuntingWithCreature(mtype.raceid) ?: return
        val option = getTaskRewardOption(taskSlot) ?: return

        taskSlot.currentKills += 1
        if ((taskSlot.upgrade && taskSlot.currentKills >= option.secondKills) ||
            (!taskSlot.upgrade && taskSlot.currentKills >= option.firstKills)
        ) {
            taskSlot.state = PreyTaskDataState.Completed
            player.sendTextMessage("You succesfully finished your hunting task. Your reward is ready to be claimed!")
        }
        player.reloadTaskSlot(taskSlot.id)
    }

    /** `IOPrey::parseTaskHuntingAction` (ioprey.cpp:405-539). */
    fun parseTaskHuntingAction(
        player: PlayerTaskHuntingState,
        slotId: Int,
        action: PreyTaskAction,
        upgrade: Boolean = false,
        raceId: Int = 0,
    ) {
        val slot = player.getTaskHuntingSlotById(slotId)
        if (slot == null || slot.state == PreyTaskDataState.Locked) {
            player.sendMessageDialog("To unlock this task hunting slot first you must buy it on store.")
            return
        }

        when (action) {
            PreyTaskAction.ListReroll -> {
                val currentTime = now()
                if (slot.disabledUntilTimeStamp >= currentTime) {
                    val minutes = (slot.disabledUntilTimeStamp - currentTime) / 60000
                    player.sendMessageDialog("You need to wait $minutes minutes to select a new creature on task.")
                    return
                } else if (slot.freeRerollTimeStamp > currentTime && !player.removeMoney(player.getTaskHuntingRerollPrice())) {
                    player.sendMessageDialog("You don't have enought money to reroll the task hunting slot.")
                    return
                } else if (slot.freeRerollTimeStamp <= currentTime) {
                    slot.freeRerollTimeStamp = currentTime + config.taskHuntingFreeRerollTime * 1000L
                }
                // else: `balance_decrease` metric (not ported)

                slot.eraseTask()
                slot.reloadReward()
                slot.state = PreyTaskDataState.Selection
                slot.reloadMonsterGrid(player.getTaskHuntingBlackList(), player.getLevel())
            }

            PreyTaskAction.RewardsReroll -> {
                if (!player.usePreyCards(config.taskHuntingBonusRerollPrice)) {
                    player.sendMessageDialog("You don't have enought prey cards to reroll you task reward rarity.")
                    return
                }

                slot.reloadReward()
            }

            PreyTaskAction.ListAllCards -> {
                val currentTime = now()
                if (slot.disabledUntilTimeStamp >= currentTime) {
                    val minutes = (slot.disabledUntilTimeStamp - currentTime) / 60000
                    player.sendMessageDialog("You need to wait $minutes minutes to select a new creature on task.")
                    return
                } else if (!player.usePreyCards(config.taskHuntingSelectListPrice)) {
                    player.sendMessageDialog("You don't have enought prey cards to choose a creature on list for you task hunting slot.")
                    return
                }

                slot.selectedRaceId = 0
                slot.state = PreyTaskDataState.ListSelection
            }

            PreyTaskAction.MonsterSelection -> {
                val currentTime = now()
                if (slot.disabledUntilTimeStamp >= currentTime) {
                    val minutes = (slot.disabledUntilTimeStamp - currentTime) / 60000
                    player.sendMessageDialog("You need to wait $minutes minutes to select a new creature on task.")
                    return
                } else if (!slot.canSelect()) {
                    player.sendMessageDialog("There was an error while processing your action. Please try reopening the task window.")
                    return
                } else if (slot.isOccupied()) {
                    player.sendMessageDialog("You already have an active monster on this task hunting slot.")
                    return
                } else if (slot.state == PreyTaskDataState.Selection && !slot.isCreatureOnList(raceId)) {
                    player.sendMessageDialog("There was an error while processing your action. Please try reopening the task window.")
                    return
                } else if (player.getTaskHuntingWithCreature(raceId) != null) {
                    player.sendMessageDialog("This creature is already selected on another slot.")
                    return
                }

                val mtype = bestiary.getMonsterTypeByRaceId(raceId)
                if (mtype != null) {
                    slot.currentKills = 0
                    slot.selectedRaceId = raceId
                    slot.removeMonsterType(raceId)
                    slot.state = PreyTaskDataState.Active
                    slot.upgrade = upgrade && player.isCreatureUnlockedOnTaskHunting(mtype)
                }
            }

            PreyTaskAction.Cancel -> {
                if (!player.removeMoney(player.getTaskHuntingRerollPrice())) {
                    player.sendMessageDialog("You don't have enought money to cancel your current task hunting.")
                    return
                }

                slot.eraseTask()
                slot.reloadReward()
                slot.state = PreyTaskDataState.Selection
                slot.reloadMonsterGrid(player.getTaskHuntingBlackList(), player.getLevel())
            }

            PreyTaskAction.Claim -> {
                if (!slot.isOccupied()) {
                    player.sendMessageDialog("You cannot claim your task reward with an empty task hunting slot.")
                    return
                }

                val option = getTaskRewardOption(slot)
                if (option != null) {
                    var boostChange = uniformRandom(0, 100)
                    if (slot.rarity >= 4 && boostChange <= 5) {
                        boostChange = 20
                    } else if (slot.rarity >= 4 && boostChange <= 10) {
                        boostChange = 15
                    } else {
                        boostChange = 10
                    }

                    val baseReward: Long
                    if (slot.upgrade && slot.currentKills >= option.secondKills) {
                        baseReward = option.secondReward.toLong()
                    } else if (!slot.upgrade && slot.currentKills >= option.firstKills) {
                        baseReward = option.firstReward.toLong()
                    } else {
                        player.sendMessageDialog("There was an error while processing you task hunting reward. Please try reopening the window.")
                        return
                    }

                    // Canary divides as integers before std::ceil: reward * boost / 10.
                    val reward = baseReward * boostChange / 10
                    val message = buildString {
                        append("Congratulations! You have earned ")
                        append(reward)
                        when (boostChange) {
                            20 -> append(" Hunting Task points including a 100% bonus.")
                            15 -> append(" Hunting Task points including a 50% bonus.")
                            else -> append(" Hunting Task points.")
                        }
                    }

                    slot.eraseTask()
                    slot.reloadReward()
                    slot.state = PreyTaskDataState.Inactive
                    player.addTaskHuntingPoints(reward)
                    player.sendMessageDialog(message)
                    slot.reloadMonsterGrid(player.getTaskHuntingBlackList(), player.getLevel())
                    slot.disabledUntilTimeStamp = now() + config.taskHuntingLimitExhaust * 1000L
                }
            }
        }

        player.reloadTaskSlot(slotId)
    }

    companion object {
        /** `PreySlot_First` / `PreySlot_Last` (ioprey.hpp:21-28). */
        const val PREY_SLOT_FIRST = 0
        const val PREY_SLOT_TWO = 1
        const val PREY_SLOT_THREE = 2
        const val PREY_SLOT_LAST = 2

        /** `while (raceIdList.size() < 9)` (ioprey.cpp:183). */
        const val MONSTER_GRID_SIZE = 9

        /** `bestiary.size() < 36` guard (ioprey.cpp:150). */
        const val MIN_BESTIARY_SIZE = 36

        /** `uint8_t killStage = 25` (ioprey.cpp:549). */
        const val KILL_STAGE = 25

        /** `uint8_t limitOfStars = 5` (ioprey.cpp:552). */
        const val LIMIT_OF_STARS = 5
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
