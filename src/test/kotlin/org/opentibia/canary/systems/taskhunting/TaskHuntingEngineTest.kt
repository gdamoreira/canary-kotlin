package org.opentibia.canary.systems.taskhunting

import java.util.Random
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for the task hunting engine core.
 *
 * Expected behavior is transcribed from:
 *  - src/io/ioprey.hpp:66-240 and src/io/ioprey.cpp:134-248,405-639
 *  - src/creatures/players/player.cpp:10493-10681 (Player task state)
 *  - src/creatures/players/player.cpp:6625-6640 (addHuntingTaskKill)
 *  - src/config/configmanager.cpp:144-145,362-366 (TASK_HUNTING_* defaults)
 */
class TaskHuntingEngineTest {

    private val fixedNow = 2_000_000L

    /** Nine-star quota pattern that matches the level 0-99 band exactly. */
    private val bandZeroStars = listOf(1, 1, 1, 2, 2, 2, 3, 3, 4)

    private fun defaultMonsters(count: Int = 120): List<TaskMonsterInfo> = monsters(count, bandZeroStars)

    private fun monsters(
        count: Int,
        stars: List<Int>,
        toUnlock: Int = 100,
    ): List<TaskMonsterInfo> = (1..count).map { raceId ->
        TaskMonsterInfo(
            raceid = raceId,
            name = "Monster $raceId",
            experience = 100,
            bestiaryStars = stars[(raceId - 1) % stars.size],
            bestiaryToUnlock = toUnlock,
        )
    }

    private fun newEngine(
        config: TaskHuntingConfig = TaskHuntingConfig(),
        monsters: List<TaskMonsterInfo> = defaultMonsters(),
        random: (Int, Int) -> Int = defaultUniformRandom(Random(20240613L)),
        now: () -> Long = { fixedNow },
    ): TaskHuntingEngine = TaskHuntingEngine(config, TaskHuntingBestiary(monsters), random, now)

    private fun newPlayer(
        engine: TaskHuntingEngine,
        level: Int = 10,
        premium: Boolean = true,
        cards: Long = 0,
        bank: Long = 100_000,
        killCounts: Map<Int, Int> = emptyMap(),
    ): PlayerTaskHuntingState = PlayerTaskHuntingState(
        bestiaryKillCount = { raceId -> killCounts[raceId] ?: 0 },
    ).apply {
        isPremium = premium
        setLevel(level)
        setBankBalance(bank)
        addPreyCards(cards)
        engine.initializeTaskHunting(this)
    }

    /** Grid-only deterministic random: ctor never rolls, grid walks the list. */
    private fun gridRng(): (Int, Int) -> Int {
        var index = 0
        return { minNumber, maxNumber ->
            minNumber + (index++ % (maxNumber - minNumber + 1))
        }
    }

    private class FixedRng(vararg values: Int) {
        private val queue = values.toList()
        private var index = 0

        fun next(minNumber: Int, maxNumber: Int): Int {
            val value = queue[index % queue.size]
            index++
            check(value in minNumber..maxNumber) { "rng value $value outside [$minNumber, $maxNumber]" }
            return value
        }
    }

    /**
     * Reward-roll values for the slot/claim flows while the monster grid (range
     * above 100) keeps walking the sorted bestiary, so a grid reload can never
     * loop on a constant value.
     */
    private fun smallValueRng(vararg values: Int): (Int, Int) -> Int {
        val queue = ArrayDeque(values.toList())
        var gridIndex = 0
        return { minNumber, maxNumber ->
            if (maxNumber <= 100) {
                val value = queue.removeFirst()
                check(value in minNumber..maxNumber) { "rng value $value outside [$minNumber, $maxNumber]" }
                value
            } else {
                minNumber + (gridIndex++ % (maxNumber - minNumber + 1))
            }
        }
    }

    // ---- Config / enums -------------------------------------------------------

    @Test
    fun `task hunting config defaults match canary configmanager`() {
        val config = TaskHuntingConfig()
        assertThat(config.taskHuntingEnabled).isTrue()
        assertThat(config.taskHuntingFreeThirdSlot).isFalse()
        assertThat(config.taskHuntingBonusRerollPrice).isEqualTo(1)
        assertThat(config.taskHuntingFreeRerollTime).isEqualTo(72000)
        assertThat(config.taskHuntingLimitExhaust).isEqualTo(72000)
        assertThat(config.taskHuntingRerollPricePerLevel).isEqualTo(200)
        assertThat(config.taskHuntingSelectListPrice).isEqualTo(5)
    }

    @Test
    fun `task enum values match canary ioprey hpp`() {
        assertThat(PreyTaskDataState.entries.map { it.value }).containsExactly(0, 1, 2, 3, 4, 5)
        assertThat(PreyTaskAction.entries.map { it.value }).containsExactly(0, 1, 2, 3, 4, 5)
        assertThat(PreyTaskDifficult.entries.map { it.value }).containsExactly(0, 1, 2, 3)
        assertThat(PreyTaskDifficult.FIRST).isEqualTo(PreyTaskDifficult.Easy)
        assertThat(PreyTaskDifficult.LAST).isEqualTo(PreyTaskDifficult.Hard)
    }

    // ---- Task option table ----------------------------------------------------

    @Test
    fun `task options table matches canary kill stage progression`() {
        val engine = newEngine()
        val options = engine.taskOption

        assertThat(options).hasSize(15)

        data class Expected(
            val difficult: PreyTaskDifficult,
            val rarity: Int,
            val firstKills: Int,
            val firstReward: Int,
        )

        val expected = listOf(
            Expected(PreyTaskDifficult.Easy, 1, 25, 10),
            Expected(PreyTaskDifficult.Easy, 2, 25, 12),
            Expected(PreyTaskDifficult.Easy, 3, 25, 14),
            Expected(PreyTaskDifficult.Easy, 4, 25, 16),
            Expected(PreyTaskDifficult.Easy, 5, 25, 19),
            Expected(PreyTaskDifficult.Medium, 1, 100, 40),
            Expected(PreyTaskDifficult.Medium, 2, 100, 50),
            Expected(PreyTaskDifficult.Medium, 3, 100, 62),
            Expected(PreyTaskDifficult.Medium, 4, 100, 77),
            Expected(PreyTaskDifficult.Medium, 5, 100, 96),
            Expected(PreyTaskDifficult.Hard, 1, 400, 160),
            Expected(PreyTaskDifficult.Hard, 2, 400, 208),
            Expected(PreyTaskDifficult.Hard, 3, 400, 270),
            Expected(PreyTaskDifficult.Hard, 4, 400, 351),
            Expected(PreyTaskDifficult.Hard, 5, 400, 456),
        )

        for ((index, row) in expected.withIndex()) {
            val option = options[index]
            assertThat(option.difficult).describedAs("option %d difficulty", index).isEqualTo(row.difficult)
            assertThat(option.rarity).describedAs("option %d rarity", index).isEqualTo(row.rarity)
            assertThat(option.firstKills).describedAs("option %d firstKills", index).isEqualTo(row.firstKills)
            assertThat(option.firstReward).describedAs("option %d firstReward", index).isEqualTo(row.firstReward)
            assertThat(option.secondKills).describedAs("option %d secondKills", index).isEqualTo(row.firstKills * 2)
            assertThat(option.secondReward).describedAs("option %d secondReward", index).isEqualTo(row.firstReward * 2)
        }
    }

    @Test
    fun `reloadReward follows canary rarity roll table`() {
        // rarity 1, chance inside (45, 70] -> rarity 2
        val slot = TaskHuntingSlot(0, uniformRandom = FixedRng(70)::next, now = { fixedNow })
        slot.rarity = 1
        slot.reloadReward()
        assertThat(slot.rarity).isEqualTo(2)

        // rarity 1, chance <= 5 -> rarity 5
        val jackpot = TaskHuntingSlot(0, uniformRandom = FixedRng(5)::next, now = { fixedNow })
        jackpot.rarity = 1
        jackpot.reloadReward()
        assertThat(jackpot.rarity).isEqualTo(5)

        // rarity 2, chance <= 45 -> rarity 3
        val medium = TaskHuntingSlot(0, uniformRandom = FixedRng(45)::next, now = { fixedNow })
        medium.rarity = 2
        medium.reloadReward()
        assertThat(medium.rarity).isEqualTo(3)

        // rarity 3, chance <= 20 -> rarity 4
        val high = TaskHuntingSlot(0, uniformRandom = FixedRng(20)::next, now = { fixedNow })
        high.rarity = 3
        high.reloadReward()
        assertThat(high.rarity).isEqualTo(4)

        // rarity 0, chance 100 -> rarity 1
        val low = TaskHuntingSlot(0, uniformRandom = FixedRng(100)::next, now = { fixedNow })
        low.rarity = 0
        low.reloadReward()
        assertThat(low.rarity).isEqualTo(1)

        // rarity 4 forces 5 without rolling (ioprey.cpp:219-222).
        val forced = TaskHuntingSlot(0, uniformRandom = { _, _ -> error("must not roll") }, now = { fixedNow })
        forced.rarity = 4
        forced.reloadReward()
        assertThat(forced.rarity).isEqualTo(5)
    }

    @Test
    fun `reloadReward no-ops when task hunting is disabled`() {
        val slot = TaskHuntingSlot(
            0,
            config = TaskHuntingConfig(taskHuntingEnabled = false),
            uniformRandom = { _, _ -> error("must not roll") },
            now = { fixedNow },
        )
        slot.rarity = 3

        slot.reloadReward()

        assertThat(slot.rarity).isEqualTo(3)
    }

    // ---- Monster grid / initialization ----------------------------------------

    @Test
    fun `task monster grid follows the level band quotas`() {
        val slot = TaskHuntingSlot(
            0,
            bestiary = TaskHuntingBestiary(monsters(60, bandZeroStars)),
            uniformRandom = gridRng(),
            now = { fixedNow },
        )

        slot.reloadMonsterGrid(emptyList(), 1)

        assertThat(slot.raceIdList).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9)
    }

    @Test
    fun `initializeTaskHunting locks slots by config and premium`() {
        val engine = newEngine()
        val nonPremium = PlayerTaskHuntingState()
        engine.initializeTaskHunting(nonPremium)

        assertThat(nonPremium.getTaskHuntingSlotById(0)!!.state).isEqualTo(PreyTaskDataState.Selection)
        assertThat(nonPremium.getTaskHuntingSlotById(1)!!.state).isEqualTo(PreyTaskDataState.Locked)
        assertThat(nonPremium.getTaskHuntingSlotById(2)!!.state).isEqualTo(PreyTaskDataState.Locked)

        val freeThird = newEngine(config = TaskHuntingConfig(taskHuntingFreeThirdSlot = true))
        val premium = PlayerTaskHuntingState().apply { isPremium = true }
        freeThird.initializeTaskHunting(premium)

        assertThat(premium.getTaskHuntingSlotById(0)!!.state).isEqualTo(PreyTaskDataState.Selection)
        assertThat(premium.getTaskHuntingSlotById(1)!!.state).isEqualTo(PreyTaskDataState.Selection)
        assertThat(premium.getTaskHuntingSlotById(2)!!.state).isEqualTo(PreyTaskDataState.Selection)

        val disabled = newEngine(config = TaskHuntingConfig(taskHuntingEnabled = false))
        val disabledPlayer = PlayerTaskHuntingState()
        disabled.initializeTaskHunting(disabledPlayer)
        for (slotId in 0..2) {
            assertThat(disabledPlayer.getTaskHuntingSlotById(slotId)!!.state).isEqualTo(PreyTaskDataState.Inactive)
        }
    }

    // ---- Monster selection ----------------------------------------------------

    @Test
    fun `monster selection activates task and only upgrades unlocked creatures`() {
        val engine = newEngine()
        val player = newPlayer(engine, killCounts = mapOf(1 to 100))
        val slot = player.getTaskHuntingSlotById(0)!!
        slot.raceIdList[0] = 1

        engine.parseTaskHuntingAction(player, 0, PreyTaskAction.MonsterSelection, upgrade = true, raceId = 1)

        assertThat(slot.state).isEqualTo(PreyTaskDataState.Active)
        assertThat(slot.selectedRaceId).isEqualTo(1)
        assertThat(slot.currentKills).isEqualTo(0)
        assertThat(slot.upgrade).isTrue()
        assertThat(slot.raceIdList).doesNotContain(1)

        val lockedPlayer = newPlayer(engine)
        val lockedSlot = lockedPlayer.getTaskHuntingSlotById(0)!!
        val raceId = lockedSlot.raceIdList[1]
        engine.parseTaskHuntingAction(
            lockedPlayer,
            0,
            PreyTaskAction.MonsterSelection,
            upgrade = true,
            raceId = raceId,
        )
        assertThat(lockedSlot.upgrade).isFalse()
    }

    @Test
    fun `monster selection rejects locked disabled and duplicate slots`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getTaskHuntingSlotById(0)!!
        val raceId = slot.raceIdList[0]

        engine.parseTaskHuntingAction(player, 0, PreyTaskAction.MonsterSelection, raceId = raceId)
        assertThat(slot.selectedRaceId).isEqualTo(raceId)

        // Duplicate on another slot: keep the race on the second list so the
        // duplicate check (not the list check) is what rejects it.
        val other = player.getTaskHuntingSlotById(1)!!
        other.raceIdList.add(raceId)
        engine.parseTaskHuntingAction(player, 1, PreyTaskAction.MonsterSelection, raceId = raceId)
        assertThat(player.dialogMessages.last()).isEqualTo("This creature is already selected on another slot.")

        // Locked slot.
        engine.parseTaskHuntingAction(player, 2, PreyTaskAction.MonsterSelection, raceId = 2)
        assertThat(player.dialogMessages.last())
            .isEqualTo("To unlock this task hunting slot first you must buy it on store.")
    }

    @Test
    fun `monster selection outside the slot list is rejected`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getTaskHuntingSlotById(0)!!
        val outside = (1..120).first { it !in slot.raceIdList }

        engine.parseTaskHuntingAction(player, 0, PreyTaskAction.MonsterSelection, raceId = outside)

        assertThat(player.dialogMessages.last())
            .isEqualTo("There was an error while processing your action. Please try reopening the task window.")
        assertThat(slot.selectedRaceId).isEqualTo(0)
    }

    // ---- Kill progress --------------------------------------------------------

    @Test
    fun `kill progress increments and completes at first kills`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getTaskHuntingSlotById(0)!!
        slot.state = PreyTaskDataState.Active
        slot.selectedRaceId = 1
        slot.rarity = 1
        slot.upgrade = false
        val mtype = TaskMonsterInfo(
            raceid = 1,
            name = "Monster 1",
            bestiaryStars = 1,
            bestiaryToUnlock = 100,
        )

        repeat(24) { engine.addHuntingTaskKill(player, mtype) }
        assertThat(slot.currentKills).isEqualTo(24)
        assertThat(slot.state).isEqualTo(PreyTaskDataState.Active)

        engine.addHuntingTaskKill(player, mtype)

        assertThat(slot.currentKills).isEqualTo(25)
        assertThat(slot.state).isEqualTo(PreyTaskDataState.Completed)
        assertThat(player.textMessages).containsExactly(
            "You succesfully finished your hunting task. Your reward is ready to be claimed!",
        )
        assertThat(player.reloadedTaskSlots).hasSize(25)
    }

    @Test
    fun `upgraded task completes at second kills`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getTaskHuntingSlotById(0)!!
        slot.state = PreyTaskDataState.Active
        slot.selectedRaceId = 1
        slot.rarity = 1
        slot.upgrade = true
        val mtype = TaskMonsterInfo(raceid = 1, name = "Monster 1", bestiaryStars = 1)

        repeat(49) { engine.addHuntingTaskKill(player, mtype) }
        assertThat(slot.state).isEqualTo(PreyTaskDataState.Active)

        engine.addHuntingTaskKill(player, mtype)

        assertThat(slot.currentKills).isEqualTo(50)
        assertThat(slot.state).isEqualTo(PreyTaskDataState.Completed)
    }

    @Test
    fun `kill progress ignores creatures without a matching active task`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getTaskHuntingSlotById(0)!!
        slot.state = PreyTaskDataState.Active
        slot.selectedRaceId = 1
        slot.rarity = 1
        val other = TaskMonsterInfo(raceid = 2, name = "Monster 2", bestiaryStars = 1)

        engine.addHuntingTaskKill(player, other)

        assertThat(slot.currentKills).isEqualTo(0)
        assertThat(player.reloadedTaskSlots).isEmpty()
    }

    // ---- Claim ----------------------------------------------------------------

    @Test
    fun `claim awards integer division reward without rarity bonus`() {
        val engine = newEngine(random = smallValueRng(5, 5))
        val player = newPlayer(engine)
        val slot = player.getTaskHuntingSlotById(0)!!
        slot.state = PreyTaskDataState.Active
        slot.selectedRaceId = 1
        slot.rarity = 1
        slot.upgrade = false
        slot.currentKills = 25

        engine.parseTaskHuntingAction(player, 0, PreyTaskAction.Claim)

        // rarity < 4 forces boostChange = 10: 10 * 10 / 10 = 10.
        assertThat(player.getTaskHuntingPoints()).isEqualTo(10)
        assertThat(player.dialogMessages.last()).isEqualTo("Congratulations! You have earned 10 Hunting Task points.")
        assertThat(slot.state).isEqualTo(PreyTaskDataState.Inactive)
        assertThat(slot.selectedRaceId).isEqualTo(0)
        assertThat(slot.currentKills).isEqualTo(0)
        assertThat(slot.disabledUntilTimeStamp).isEqualTo(fixedNow + 72_000_000L)
        assertThat(player.reloadedTaskSlots).contains(0)
    }

    @Test
    fun `claim rarity five boosts reward one hundred percent`() {
        val engine = newEngine(random = smallValueRng(5, 1))
        val player = newPlayer(engine)
        val slot = player.getTaskHuntingSlotById(0)!!
        slot.state = PreyTaskDataState.Active
        slot.selectedRaceId = 1
        slot.rarity = 5
        slot.upgrade = false
        slot.currentKills = 25

        engine.parseTaskHuntingAction(player, 0, PreyTaskAction.Claim)

        // Easy star-5 reward is 19, boostChange 20: 19 * 20 / 10 = 38, then
        // reloadReward rolls 1 -> rarity 5.
        assertThat(player.getTaskHuntingPoints()).isEqualTo(38)
        assertThat(player.dialogMessages.last())
            .isEqualTo("Congratulations! You have earned 38 Hunting Task points including a 100% bonus.")
        assertThat(slot.rarity).isEqualTo(5)
    }

    @Test
    fun `claim rarity five boosts fifty percent with integer truncation`() {
        // Easy star-5 option has firstReward 19; roll 8 gives boost 15:
        // 19 * 15 / 10 = 28 (ceil would be 29).
        val engine = newEngine(random = smallValueRng(8, 1))
        val player = newPlayer(engine)
        val slot = player.getTaskHuntingSlotById(0)!!
        slot.state = PreyTaskDataState.Active
        slot.selectedRaceId = 1
        slot.rarity = 5
        slot.upgrade = false
        slot.currentKills = 25

        engine.parseTaskHuntingAction(player, 0, PreyTaskAction.Claim)

        assertThat(player.getTaskHuntingPoints()).isEqualTo(28)
        assertThat(player.dialogMessages.last())
            .isEqualTo("Congratulations! You have earned 28 Hunting Task points including a 50% bonus.")
    }

    @Test
    fun `claim rejects empty slot and insufficient kills`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getTaskHuntingSlotById(0)!!

        engine.parseTaskHuntingAction(player, 0, PreyTaskAction.Claim)
        assertThat(player.dialogMessages.last())
            .isEqualTo("You cannot claim your task reward with an empty task hunting slot.")
        assertThat(player.getTaskHuntingPoints()).isEqualTo(0)

        slot.state = PreyTaskDataState.Active
        slot.selectedRaceId = 1
        slot.rarity = 1
        slot.currentKills = 10
        engine.parseTaskHuntingAction(player, 0, PreyTaskAction.Claim)
        assertThat(player.dialogMessages.last())
            .isEqualTo("There was an error while processing you task hunting reward. Please try reopening the window.")
        assertThat(player.getTaskHuntingPoints()).isEqualTo(0)
        assertThat(slot.state).isEqualTo(PreyTaskDataState.Active)
    }

    // ---- Cancel / reroll ------------------------------------------------------

    @Test
    fun `cancel spends level scaled money and resets the task`() {
        val engine = newEngine()
        val player = newPlayer(engine, level = 10)
        val slot = player.getTaskHuntingSlotById(0)!!
        slot.state = PreyTaskDataState.Active
        slot.selectedRaceId = 1

        engine.parseTaskHuntingAction(player, 0, PreyTaskAction.Cancel)

        assertThat(player.getTaskHuntingRerollPrice()).isEqualTo(2_000)
        assertThat(player.getBankBalance()).isEqualTo(98_000)
        assertThat(slot.state).isEqualTo(PreyTaskDataState.Selection)
        assertThat(slot.selectedRaceId).isEqualTo(0)
        assertThat(slot.raceIdList).hasSize(9)
        assertThat(player.reloadedTaskSlots).containsExactly(0)
    }

    @Test
    fun `cancel without money keeps the task`() {
        val engine = newEngine()
        val player = newPlayer(engine, bank = 0)
        val slot = player.getTaskHuntingSlotById(0)!!
        slot.state = PreyTaskDataState.Active
        slot.selectedRaceId = 1

        engine.parseTaskHuntingAction(player, 0, PreyTaskAction.Cancel)

        assertThat(player.dialogMessages.last())
            .isEqualTo("You don't have enought money to cancel your current task hunting.")
        assertThat(slot.state).isEqualTo(PreyTaskDataState.Active)
        assertThat(slot.selectedRaceId).isEqualTo(1)
        assertThat(player.reloadedTaskSlots).isEmpty()
    }

    @Test
    fun `list reroll is blocked while the slot is exhausted`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getTaskHuntingSlotById(0)!!
        slot.disabledUntilTimeStamp = fixedNow + 120_000

        engine.parseTaskHuntingAction(player, 0, PreyTaskAction.ListReroll)

        assertThat(player.dialogMessages.last())
            .isEqualTo("You need to wait 2 minutes to select a new creature on task.")
        assertThat(player.getBankBalance()).isEqualTo(100_000)
        assertThat(slot.state).isEqualTo(PreyTaskDataState.Selection)
    }

    @Test
    fun `list reroll free resets timestamp and paid withdraws price`() {
        val engine = newEngine()
        val free = newPlayer(engine)
        val freeSlot = free.getTaskHuntingSlotById(0)!!
        freeSlot.freeRerollTimeStamp = fixedNow

        engine.parseTaskHuntingAction(free, 0, PreyTaskAction.ListReroll)

        assertThat(freeSlot.freeRerollTimeStamp).isEqualTo(fixedNow + 72_000_000L)
        assertThat(free.getBankBalance()).isEqualTo(100_000)
        assertThat(freeSlot.state).isEqualTo(PreyTaskDataState.Selection)
        assertThat(freeSlot.raceIdList).hasSize(9)

        val paid = newPlayer(engine, level = 10)
        val paidSlot = paid.getTaskHuntingSlotById(0)!!
        paidSlot.freeRerollTimeStamp = fixedNow + 1_000

        engine.parseTaskHuntingAction(paid, 0, PreyTaskAction.ListReroll)

        assertThat(paid.getBankBalance()).isEqualTo(98_000)
        assertThat(paidSlot.freeRerollTimeStamp).isEqualTo(fixedNow + 1_000)
    }

    @Test
    fun `rewards reroll spends one card and rerolls rarity`() {
        val engine = newEngine(random = smallValueRng(10))
        val player = newPlayer(engine, cards = 1)
        val slot = player.getTaskHuntingSlotById(0)!!
        slot.rarity = 3

        engine.parseTaskHuntingAction(player, 0, PreyTaskAction.RewardsReroll)

        assertThat(player.getPreyCards()).isEqualTo(0)
        assertThat(slot.rarity).isEqualTo(4)

        val broke = newPlayer(engine)
        val brokeSlot = broke.getTaskHuntingSlotById(0)!!
        brokeSlot.rarity = 3
        engine.parseTaskHuntingAction(broke, 0, PreyTaskAction.RewardsReroll)
        assertThat(broke.dialogMessages.last())
            .isEqualTo("You don't have enought prey cards to reroll you task reward rarity.")
        assertThat(brokeSlot.rarity).isEqualTo(3)
    }

    @Test
    fun `list all cards spends selection price and enters list selection`() {
        val engine = newEngine()
        val player = newPlayer(engine, cards = 5)
        val slot = player.getTaskHuntingSlotById(0)!!
        slot.selectedRaceId = 1

        engine.parseTaskHuntingAction(player, 0, PreyTaskAction.ListAllCards)

        assertThat(player.getPreyCards()).isEqualTo(0)
        assertThat(slot.state).isEqualTo(PreyTaskDataState.ListSelection)
        assertThat(slot.selectedRaceId).isEqualTo(0)

        val broke = newPlayer(engine, cards = 4)
        engine.parseTaskHuntingAction(broke, 0, PreyTaskAction.ListAllCards)
        assertThat(broke.dialogMessages.last())
            .isEqualTo("You don't have enought prey cards to choose a creature on list for you task hunting slot.")
    }

    // ---- State helpers --------------------------------------------------------

    @Test
    fun `task hunting points add and spend`() {
        val player = PlayerTaskHuntingState()
        assertThat(player.getTaskHuntingPoints()).isEqualTo(0)

        player.addTaskHuntingPoints(100)
        assertThat(player.getTaskHuntingPoints()).isEqualTo(100)
        assertThat(player.useTaskHuntingPoints(40)).isTrue()
        assertThat(player.getTaskHuntingPoints()).isEqualTo(60)
        assertThat(player.useTaskHuntingPoints(61)).isFalse()
        assertThat(player.getTaskHuntingPoints()).isEqualTo(60)
    }

    @Test
    fun `is creature unlocked compares bestiary kills with bestiaryToUnlock`() {
        val player = PlayerTaskHuntingState(bestiaryKillCount = { raceId -> if (raceId == 7) 99 else 0 })
        val locked = TaskMonsterInfo(raceid = 7, name = "Monster 7", bestiaryToUnlock = 100)
        val unlocked = TaskMonsterInfo(raceid = 7, name = "Monster 7", bestiaryToUnlock = 99)

        assertThat(player.isCreatureUnlockedOnTaskHunting(locked)).isFalse()
        assertThat(player.isCreatureUnlockedOnTaskHunting(unlocked)).isTrue()
        assertThat(player.isCreatureUnlockedOnTaskHunting(null)).isFalse()
    }

    @Test
    fun `task black list mirrors canary player rules`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val first = player.getTaskHuntingSlotById(0)!!
        val second = player.getTaskHuntingSlotById(1)!!

        first.selectedRaceId = 5
        // Occupied slot contributes only the selected race; the open slot
        // contributes its whole list (player.cpp:10614-10628).
        assertThat(player.getTaskHuntingBlackList())
            .containsExactlyInAnyOrderElementsOf(listOf(5) + second.raceIdList)
    }

    @Test
    fun `getTaskRewardOption maps stars to difficulty and slot rarity`() {
        val engine = newEngine()
        val easy = TaskHuntingSlot(0, uniformRandom = { _, _ -> 0 }, now = { fixedNow })
        easy.selectedRaceId = 1
        easy.rarity = 3
        val easyOption = engine.getTaskRewardOption(easy)
        assertThat(easyOption!!.difficult).isEqualTo(PreyTaskDifficult.Easy)
        assertThat(easyOption.firstKills).isEqualTo(25)
        assertThat(easyOption.firstReward).isEqualTo(14)

        val medium = TaskHuntingSlot(0, uniformRandom = { _, _ -> 0 }, now = { fixedNow })
        medium.selectedRaceId = 4
        medium.rarity = 2
        val mediumOption = engine.getTaskRewardOption(medium)
        assertThat(mediumOption!!.difficult).isEqualTo(PreyTaskDifficult.Medium)
        assertThat(mediumOption.firstKills).isEqualTo(100)

        val hard = TaskHuntingSlot(0, uniformRandom = { _, _ -> 0 }, now = { fixedNow })
        hard.selectedRaceId = 9
        hard.rarity = 5
        val hardOption = engine.getTaskRewardOption(hard)
        assertThat(hardOption!!.difficult).isEqualTo(PreyTaskDifficult.Hard)
        assertThat(hardOption.firstReward).isEqualTo(456)

        val unknown = TaskHuntingSlot(0, uniformRandom = { _, _ -> 0 }, now = { fixedNow })
        unknown.selectedRaceId = 999
        assertThat(engine.getTaskRewardOption(unknown)).isNull()
    }
}
