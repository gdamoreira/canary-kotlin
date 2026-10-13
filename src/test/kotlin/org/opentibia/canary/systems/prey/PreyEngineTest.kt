package org.opentibia.canary.systems.prey

import java.util.Random
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatDamage

/**
 * Transcript tests for the prey engine core.
 *
 * Expected behavior is transcribed from:
 *  - src/io/ioprey.hpp:21-144 and src/io/ioprey.cpp:21-403 (slot, grid, actions)
 *  - src/creatures/players/player.cpp:10392-10491 (Player prey state)
 *  - src/creatures/players/player.cpp:12641-12667 (initializePrey)
 *  - src/creatures/combat/combat.cpp:887-909 (damage/defense bonus, integer math)
 *  - src/lua/functions/creatures/player/player_functions.cpp:964-1042
 *    (getPreyExperiencePercentage / getPreyLootPercentage primitives)
 *  - src/config/configmanager.cpp:123-124,331-335 (PREY_* defaults)
 */
class PreyEngineTest {

    private val fixedNow = 1_000_000L

    /** Nine-star quota pattern that matches the level 0-99 band exactly. */
    private val bandZeroStars = listOf(1, 1, 1, 2, 2, 2, 3, 3, 4)

    private fun defaultMonsters(count: Int = 120): List<PreyMonsterInfo> = monsters(count, bandZeroStars)

    private fun monsters(count: Int, stars: List<Int>): List<PreyMonsterInfo> = (1..count).map { raceId ->
        PreyMonsterInfo(
            raceid = raceId,
            name = "Monster $raceId",
            experience = 100,
            bestiaryStars = stars[(raceId - 1) % stars.size],
        )
    }

    private fun newEngine(
        config: PreyConfig = PreyConfig(),
        monsters: List<PreyMonsterInfo> = defaultMonsters(),
        random: (Int, Int) -> Int = defaultUniformRandom(Random(20240613L)),
        now: () -> Long = { fixedNow },
    ): PreyEngine = PreyEngine(config, PreyBestiary(monsters), random, now)

    private fun newPlayer(
        engine: PreyEngine,
        level: Int = 10,
        premium: Boolean = true,
        cards: Long = 0,
        bank: Long = 100_000,
    ): PlayerPreyState = PlayerPreyState().apply {
        isPremium = premium
        setLevel(level)
        setBankBalance(bank)
        addPreyCards(cards)
        engine.initializePrey(this)
    }

    /**
     * `uniform_random` replacement for grid tests: slot-constructor rolls answer
     * the minimum of their range, grid rolls walk the sorted bestiary list from
     * index 0 upwards.
     */
    private fun gridRng(): (Int, Int) -> Int {
        var index = 0
        return { minNumber, maxNumber ->
            if (maxNumber <= 10) {
                minNumber
            } else {
                minNumber + (index++ % (maxNumber - minNumber + 1))
            }
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

    // ---- Config / enums -------------------------------------------------------

    @Test
    fun `prey config defaults match canary configmanager`() {
        val config = PreyConfig()
        assertThat(config.preyEnabled).isTrue()
        assertThat(config.preyFreeThirdSlot).isFalse()
        assertThat(config.preyBonusRerollPrice).isEqualTo(1)
        assertThat(config.preyBonusTime).isEqualTo(7200)
        assertThat(config.preyFreeRerollTime).isEqualTo(72000)
        assertThat(config.preyRerollPricePerLevel).isEqualTo(200)
        assertThat(config.preySelectListPrice).isEqualTo(5)
    }

    @Test
    fun `prey enum values match canary ioprey hpp`() {
        assertThat(PreyDataState.entries.map { it.value }).containsExactly(0, 1, 2, 3, 4, 5, 6)
        assertThat(PreyBonus.entries.map { it.value }).containsExactly(0, 1, 2, 3, 4)
        assertThat(PreyOption.entries.map { it.value }).containsExactly(0, 1, 2)
        assertThat(PreyAction.entries.map { it.value }).containsExactly(0, 1, 2, 3, 4, 5)
        assertThat(PreyBonus.FIRST).isEqualTo(PreyBonus.Damage)
        assertThat(PreyBonus.LAST).isEqualTo(PreyBonus.Loot)
    }

    // ---- Slot rolling ---------------------------------------------------------

    @Test
    fun `slot constructor erases bonus then rolls rarity value and type`() {
        val slot = PreySlot(0, uniformRandom = gridRng(), now = { fixedNow })

        assertThat(slot.state).isEqualTo(PreyDataState.Selection)
        assertThat(slot.bonus).isNotEqualTo(PreyBonus.None)
        assertThat(slot.bonusRarity).isBetween(2, 10)
        // reloadBonusValue runs before reloadBonusType, so the percentage is the
        // PreyBonus_None formula of the freshly rolled rarity (ioprey.cpp:41-55).
        assertThat(slot.bonusPercentage).isEqualTo(3 * slot.bonusRarity + 10)
        assertThat(slot.freeRerollTimeStamp).isEqualTo(fixedNow + 72_000_000L)
    }

    @Test
    fun `reloadBonusValue uses damage defense and fallback formulas`() {
        val rng = FixedRng(4, 2, 8, 9)
        val slot = PreySlot(0, uniformRandom = rng::next, now = { fixedNow })

        // ctor: uniform(2, 10) -> 4, uniform(0, 3) -> 2 (Experience).
        assertThat(slot.bonusRarity).isEqualTo(4)
        assertThat(slot.bonus).isEqualTo(PreyBonus.Experience)
        assertThat(slot.bonusPercentage).isEqualTo(3 * 4 + 10)

        slot.bonus = PreyBonus.Damage
        slot.reloadBonusValue() // uniform(5, 10) -> 8
        assertThat(slot.bonusRarity).isEqualTo(8)
        assertThat(slot.bonusPercentage).isEqualTo(2 * 8 + 5)

        slot.bonus = PreyBonus.Defense
        slot.reloadBonusValue() // uniform(9, 10) -> 9
        assertThat(slot.bonusRarity).isEqualTo(9)
        assertThat(slot.bonusPercentage).isEqualTo(2 * 9 + 10)

        // Rarity 9+ is forced to 10 without a roll (ioprey.cpp:42-43).
        slot.reloadBonusValue()
        assertThat(slot.bonusRarity).isEqualTo(10)
        assertThat(slot.bonusPercentage).isEqualTo(2 * 10 + 10)

        slot.bonus = PreyBonus.Loot
        slot.reloadBonusValue()
        assertThat(slot.bonusPercentage).isEqualTo(3 * 10 + 10)
    }

    @Test
    fun `rarity ten reroll always changes the bonus type`() {
        // ctor: uniform(2, 10) -> 5, uniform(0, 3) -> 0 (Damage); then the
        // rarity-ten guard rolls 0 (same) and finally 1 (Defense).
        val rng = FixedRng(5, 0, 0, 1)
        val slot = PreySlot(0, uniformRandom = rng::next, now = { fixedNow })
        slot.bonusRarity = 10
        slot.bonus = PreyBonus.Damage

        slot.reloadBonusType()

        assertThat(slot.bonus).isEqualTo(PreyBonus.Defense)
    }

    @Test
    fun `eraseBonus maintain keeps bonus but resets selection state`() {
        val slot = PreySlot(0, uniformRandom = gridRng(), now = { fixedNow })
        slot.bonus = PreyBonus.Loot
        slot.bonusRarity = 7
        slot.bonusPercentage = 31
        slot.state = PreyDataState.Active
        slot.option = PreyOption.Locked
        slot.selectedRaceId = 42
        slot.bonusTimeLeft = 120

        slot.eraseBonus(true)

        assertThat(slot.bonus).isEqualTo(PreyBonus.Loot)
        assertThat(slot.bonusRarity).isEqualTo(7)
        assertThat(slot.bonusPercentage).isEqualTo(31)
        assertThat(slot.state).isEqualTo(PreyDataState.Selection)
        assertThat(slot.option).isEqualTo(PreyOption.None)
        assertThat(slot.selectedRaceId).isEqualTo(0)
        assertThat(slot.bonusTimeLeft).isEqualTo(0)

        slot.eraseBonus()
        assertThat(slot.bonus).isEqualTo(PreyBonus.None)
        assertThat(slot.bonusPercentage).isEqualTo(5)
        assertThat(slot.bonusRarity).isEqualTo(1)
    }

    @Test
    fun `isOccupied and canSelect follow canary state masks`() {
        val slot = PreySlot(0, uniformRandom = gridRng(), now = { fixedNow })

        slot.state = PreyDataState.Selection
        assertThat(slot.canSelect()).isTrue()
        slot.selectedRaceId = 4
        slot.bonusTimeLeft = 10
        assertThat(slot.isOccupied()).isTrue()
        slot.bonusTimeLeft = 0
        assertThat(slot.isOccupied()).isFalse()

        for (state in listOf(
            PreyDataState.Selection,
            PreyDataState.SelectionChangeMonster,
            PreyDataState.ListSelection,
            PreyDataState.Inactive,
        )) {
            slot.state = state
            assertThat(slot.canSelect()).isTrue()
        }
        for (state in listOf(PreyDataState.Locked, PreyDataState.Active, PreyDataState.WildcardSelection)) {
            slot.state = state
            assertThat(slot.canSelect()).isFalse()
        }
    }

    // ---- Monster grid ---------------------------------------------------------

    @Test
    fun `reloadMonsterGrid quotas by level band`() {
        val bands = mapOf(
            0 to listOf(1, 1, 1, 2, 2, 2, 3, 3, 4),
            99 to listOf(1, 1, 1, 2, 2, 2, 3, 3, 4),
            100 to listOf(1, 2, 2, 2, 3, 3, 3, 4, 4),
            299 to listOf(1, 2, 2, 2, 3, 3, 3, 4, 4),
            300 to listOf(1, 2, 2, 3, 3, 3, 4, 4, 4),
            499 to listOf(1, 2, 2, 3, 3, 3, 4, 4, 4),
            500 to listOf(1, 2, 3, 3, 3, 4, 4, 4, 4),
        )

        for ((level, stars) in bands) {
            val slot = PreySlot(
                0,
                bestiary = PreyBestiary(monsters(60, stars)),
                uniformRandom = gridRng(),
                now = { fixedNow },
            )
            slot.reloadMonsterGrid(emptyList(), level)
            assertThat(slot.raceIdList).describedAs("level %d", level).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9)
        }
    }

    @Test
    fun `reloadMonsterGrid skips blacklisted monsters`() {
        val slot = PreySlot(
            0,
            bestiary = PreyBestiary(defaultMonsters()),
            uniformRandom = gridRng(),
            now = { fixedNow },
        )

        slot.reloadMonsterGrid(listOf(1, 2), 1)

        assertThat(slot.raceIdList).startsWith(3, 4, 5, 6, 7, 8, 9)
        assertThat(slot.raceIdList).doesNotContain(1, 2)
        assertThat(slot.raceIdList).hasSize(9)
        assertThat(slot.raceIdList).doesNotHaveDuplicates()
    }

    @Test
    fun `reloadMonsterGrid skips ineligible monsters`() {
        val list = (1..120).map { raceId ->
            PreyMonsterInfo(
                raceid = raceId,
                name = "Monster $raceId",
                experience = if (raceId == 1) 0 else 100,
                bestiaryStars = bandZeroStars[(raceId - 1) % bandZeroStars.size],
                isPreyable = raceId != 3,
                isPreyExclusive = raceId == 2,
            )
        }
        val slot = PreySlot(0, bestiary = PreyBestiary(list), uniformRandom = gridRng(), now = { fixedNow })

        slot.reloadMonsterGrid(emptyList(), 1)

        assertThat(slot.raceIdList).doesNotContain(1, 2, 3)
        assertThat(slot.raceIdList).hasSize(9)
    }

    @Test
    fun `reloadMonsterGrid no-ops when disabled or bestiary below 36`() {
        val disabled = PreyConfig(preyEnabled = false)
        val disabledSlot = PreySlot(
            0,
            config = disabled,
            bestiary = PreyBestiary(defaultMonsters()),
            uniformRandom = gridRng(),
            now = { fixedNow },
        )
        disabledSlot.reloadMonsterGrid(emptyList(), 1)
        assertThat(disabledSlot.raceIdList).isEmpty()

        val smallBestiary = PreySlot(
            0,
            bestiary = PreyBestiary(monsters(35, bandZeroStars)),
            uniformRandom = gridRng(),
            now = { fixedNow },
        )
        smallBestiary.reloadMonsterGrid(emptyList(), 1)
        assertThat(smallBestiary.raceIdList).isEmpty()
    }

    // ---- Initialization -------------------------------------------------------

    @Test
    fun `initializePrey locks slots by config and premium`() {
        val engine = newEngine()
        val nonPremium = PlayerPreyState()
        engine.initializePrey(nonPremium)

        assertThat(nonPremium.getPreySlotById(0)!!.state).isEqualTo(PreyDataState.Selection)
        assertThat(nonPremium.getPreySlotById(1)!!.state).isEqualTo(PreyDataState.Locked)
        assertThat(nonPremium.getPreySlotById(2)!!.state).isEqualTo(PreyDataState.Locked)
        assertThat(nonPremium.getPreySlotById(0)!!.raceIdList).hasSize(9)
        assertThat(nonPremium.getPreySlotById(1)!!.raceIdList).isEmpty()

        val freeThird = newEngine(config = PreyConfig(preyFreeThirdSlot = true))
        val premium = PlayerPreyState().apply { isPremium = true }
        freeThird.initializePrey(premium)

        assertThat(premium.getPreySlotById(0)!!.state).isEqualTo(PreyDataState.Selection)
        assertThat(premium.getPreySlotById(1)!!.state).isEqualTo(PreyDataState.Selection)
        assertThat(premium.getPreySlotById(2)!!.state).isEqualTo(PreyDataState.Selection)
    }

    @Test
    fun `initializePrey fills inactive slots when prey disabled`() {
        val engine = newEngine(config = PreyConfig(preyEnabled = false))
        val player = PlayerPreyState(PreyConfig(preyEnabled = false))
        engine.initializePrey(player)

        for (slotId in 0..2) {
            val slot = player.getPreySlotById(slotId)!!
            assertThat(slot.state).isEqualTo(PreyDataState.Inactive)
            assertThat(slot.raceIdList).isEmpty()
        }
    }

    // ---- Actions --------------------------------------------------------------

    @Test
    fun `locked slot rejects every prey action`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val locked = player.getPreySlotById(2)!!
        assertThat(locked.state).isEqualTo(PreyDataState.Locked)

        engine.parsePreyAction(player, 2, PreyAction.ListAllCards)
        engine.parsePreyAction(player, 2, PreyAction.MonsterSelection, index = 0)

        assertThat(player.dialogMessages).containsExactly(
            "To unlock this prey slot first you must buy it on store.",
            "To unlock this prey slot first you must buy it on store.",
        )
        assertThat(player.reloadedPreySlots).isEmpty()
    }

    @Test
    fun `list all cards spends selection price and clears the slot`() {
        val engine = newEngine()
        val player = newPlayer(engine, cards = 5)
        val slot = player.getPreySlotById(0)!!
        slot.selectedRaceId = 7
        slot.bonusTimeLeft = 300

        engine.parsePreyAction(player, 0, PreyAction.ListAllCards)

        assertThat(player.getPreyCards()).isEqualTo(0)
        assertThat(slot.state).isEqualTo(PreyDataState.ListSelection)
        assertThat(slot.selectedRaceId).isEqualTo(0)
        assertThat(slot.bonusTimeLeft).isEqualTo(0)
        assertThat(player.reloadedPreySlots).containsExactly(0)
    }

    @Test
    fun `list all cards without cards keeps the slot untouched`() {
        val engine = newEngine()
        val player = newPlayer(engine, cards = 4)
        val slot = player.getPreySlotById(0)!!

        engine.parsePreyAction(player, 0, PreyAction.ListAllCards)

        assertThat(player.dialogMessages).containsExactly("You don't have enought prey cards to choose a monsters on the list.")
        assertThat(player.getPreyCards()).isEqualTo(4)
        assertThat(slot.state).isEqualTo(PreyDataState.Selection)
        assertThat(player.reloadedPreySlots).isEmpty()
    }

    @Test
    fun `list all selection activates monster rolls bonus when none and sets time`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getPreySlotById(0)!!
        slot.state = PreyDataState.ListSelection
        slot.bonus = PreyBonus.None
        val raceId = 5

        engine.parsePreyAction(player, 0, PreyAction.ListAllSelection, raceId = raceId)

        assertThat(slot.state).isEqualTo(PreyDataState.Active)
        assertThat(slot.selectedRaceId).isEqualTo(raceId)
        assertThat(slot.bonusTimeLeft).isEqualTo(7200)
        assertThat(slot.bonus).isNotEqualTo(PreyBonus.None)
        assertThat(slot.raceIdList).doesNotContain(raceId)
        assertThat(player.reloadedPreySlots).containsExactly(0)
    }

    @Test
    fun `list all selection rejects occupied wrong state duplicate and non preyable`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getPreySlotById(0)!!
        slot.state = PreyDataState.ListSelection
        slot.selectedRaceId = 9
        slot.bonusTimeLeft = 100

        engine.parsePreyAction(player, 0, PreyAction.ListAllSelection, raceId = 5)
        assertThat(player.dialogMessages.last()).isEqualTo("You already have an active monster on this prey slot.")

        slot.selectedRaceId = 0
        slot.bonusTimeLeft = 0
        slot.state = PreyDataState.Selection
        engine.parsePreyAction(player, 0, PreyAction.ListAllSelection, raceId = 5)
        assertThat(player.dialogMessages.last())
            .isEqualTo("There was an error while processing your action. Please try reopening the prey window.")

        val other = player.getPreySlotById(1)!!
        other.selectedRaceId = 5
        other.bonusTimeLeft = 100
        slot.state = PreyDataState.ListSelection
        engine.parsePreyAction(player, 0, PreyAction.ListAllSelection, raceId = 5)
        assertThat(player.dialogMessages.last()).isEqualTo("This creature is already selected on another slot.")

        val restricted = newEngine(monsters = defaultMonsters().map {
            if (it.raceid == 6) it.copy(isPreyable = false) else it
        })
        val restrictedPlayer = newPlayer(restricted)
        val restrictedSlot = restrictedPlayer.getPreySlotById(0)!!
        restrictedSlot.state = PreyDataState.ListSelection
        restricted.parsePreyAction(restrictedPlayer, 0, PreyAction.ListAllSelection, raceId = 6)
        assertThat(restrictedPlayer.dialogMessages.last())
            .isEqualTo("This creature can't be select on prey. Please choose another one.")
    }

    @Test
    fun `monster selection by index activates and validates index bounds`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getPreySlotById(0)!!
        val raceId = slot.raceIdList[3]

        engine.parsePreyAction(player, 0, PreyAction.MonsterSelection, index = 3)

        assertThat(slot.state).isEqualTo(PreyDataState.Active)
        assertThat(slot.selectedRaceId).isEqualTo(raceId)
        assertThat(slot.bonusTimeLeft).isEqualTo(7200)
        assertThat(slot.raceIdList).doesNotContain(raceId)

        val fresh = newPlayer(engine)
        val freshSlot = fresh.getPreySlotById(0)!!
        engine.parsePreyAction(fresh, 0, PreyAction.MonsterSelection, index = -1)
        assertThat(fresh.dialogMessages.last())
            .isEqualTo("There was an error while processing your action. Please try reopening the prey window.")
        engine.parsePreyAction(fresh, 0, PreyAction.MonsterSelection, index = freshSlot.raceIdList.size)
        assertThat(fresh.dialogMessages.last())
            .isEqualTo("There was an error while processing your action. Please try reopening the prey window.")
        assertThat(freshSlot.selectedRaceId).isEqualTo(0)
    }

    @Test
    fun `bonus reroll spends one card and resets time left`() {
        val engine = newEngine()
        val player = newPlayer(engine, cards = 1)
        val slot = player.getPreySlotById(0)!!
        slot.state = PreyDataState.Active
        slot.selectedRaceId = 5
        slot.bonusTimeLeft = 10
        slot.bonusRarity = 10
        slot.bonus = PreyBonus.Damage

        engine.parsePreyAction(player, 0, PreyAction.BonusReroll)

        assertThat(player.getPreyCards()).isEqualTo(0)
        assertThat(slot.bonusTimeLeft).isEqualTo(7200)
        assertThat(slot.bonus).isNotEqualTo(PreyBonus.Damage)
        assertThat(slot.selectedRaceId).isEqualTo(5)
        assertThat(slot.state).isEqualTo(PreyDataState.Active)

        val broke = newPlayer(engine)
        val brokeSlot = broke.getPreySlotById(0)!!
        brokeSlot.selectedRaceId = 5
        brokeSlot.bonusTimeLeft = 10
        engine.parsePreyAction(broke, 0, PreyAction.BonusReroll)
        assertThat(broke.dialogMessages.last())
            .isEqualTo("You don't have enought prey cards to reroll this prey slot bonus type.")
    }

    @Test
    fun `option action enforces card thresholds`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getPreySlotById(0)!!

        engine.parsePreyAction(player, 0, PreyAction.Option, option = PreyOption.AutomaticReroll)
        assertThat(player.dialogMessages.last())
            .isEqualTo("You don't have enought prey cards to enable automatic reroll when your slot expire.")
        assertThat(slot.option).isEqualTo(PreyOption.None)

        player.addPreyCards(1)
        engine.parsePreyAction(player, 0, PreyAction.Option, option = PreyOption.AutomaticReroll)
        assertThat(slot.option).isEqualTo(PreyOption.AutomaticReroll)

        engine.parsePreyAction(player, 0, PreyAction.Option, option = PreyOption.Locked)
        assertThat(player.dialogMessages.last())
            .isEqualTo("You don't have enought prey cards to lock monster and bonus when the slot expire.")
        assertThat(slot.option).isEqualTo(PreyOption.AutomaticReroll)

        player.addPreyCards(4)
        engine.parsePreyAction(player, 0, PreyAction.Option, option = PreyOption.Locked)
        assertThat(slot.option).isEqualTo(PreyOption.Locked)
    }

    @Test
    fun `list reroll free resets timestamp without money and enters change monster`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getPreySlotById(0)!!
        slot.freeRerollTimeStamp = fixedNow
        slot.bonus = PreyBonus.Loot

        engine.parsePreyAction(player, 0, PreyAction.ListReroll)

        assertThat(player.getBankBalance()).isEqualTo(100_000)
        assertThat(slot.freeRerollTimeStamp).isEqualTo(fixedNow + 72_000_000L)
        assertThat(slot.state).isEqualTo(PreyDataState.SelectionChangeMonster)
        assertThat(slot.raceIdList).hasSize(9)
        assertThat(player.reloadedPreySlots).containsExactly(0)
    }

    @Test
    fun `list reroll paid withdraws level scaled price and keeps timestamp`() {
        val engine = newEngine()
        val player = newPlayer(engine, level = 10)
        val slot = player.getPreySlotById(0)!!
        slot.freeRerollTimeStamp = fixedNow + 1_000

        engine.parsePreyAction(player, 0, PreyAction.ListReroll)

        assertThat(player.getPreyRerollPrice()).isEqualTo(2_000)
        assertThat(player.getBankBalance()).isEqualTo(98_000)
        assertThat(slot.freeRerollTimeStamp).isEqualTo(fixedNow + 1_000)
        assertThat(slot.state).isEqualTo(PreyDataState.SelectionChangeMonster)
    }

    @Test
    fun `list reroll without money keeps slot untouched`() {
        val engine = newEngine()
        val player = newPlayer(engine, bank = 0)
        val slot = player.getPreySlotById(0)!!
        slot.freeRerollTimeStamp = fixedNow + 1_000
        slot.state = PreyDataState.Active
        slot.selectedRaceId = 9
        slot.bonusTimeLeft = 50
        val racesBefore = slot.raceIdList.toList()

        engine.parsePreyAction(player, 0, PreyAction.ListReroll)

        assertThat(player.dialogMessages.last()).isEqualTo("You don't have enought money to reroll the prey slot.")
        assertThat(slot.state).isEqualTo(PreyDataState.Active)
        assertThat(slot.selectedRaceId).isEqualTo(9)
        assertThat(slot.bonusTimeLeft).isEqualTo(50)
        assertThat(slot.raceIdList).isEqualTo(racesBefore)
        assertThat(player.reloadedPreySlots).isEmpty()
    }

    // ---- checkPlayerPreys -----------------------------------------------------

    @Test
    fun `checkPlayerPreys decrements time left above the amount`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getPreySlotById(0)!!
        slot.selectedRaceId = 5
        slot.bonusTimeLeft = 100

        engine.checkPlayerPreys(player, 5)

        assertThat(slot.bonusTimeLeft).isEqualTo(95)
        assertThat(player.sentPreyTimeLeft).containsExactly(0 to 95)
    }

    @Test
    fun `checkPlayerPreys expires default slot erases bonus and reloads grid`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getPreySlotById(0)!!
        slot.selectedRaceId = 5
        slot.bonusTimeLeft = 3
        slot.bonus = PreyBonus.Damage
        slot.bonusRarity = 8
        slot.bonusPercentage = 21

        engine.checkPlayerPreys(player, 5)

        assertThat(player.textMessages).containsExactly("Your prey bonus has expired.")
        assertThat(slot.selectedRaceId).isEqualTo(0)
        assertThat(slot.bonusTimeLeft).isEqualTo(0)
        assertThat(slot.bonus).isEqualTo(PreyBonus.None)
        assertThat(slot.bonusRarity).isEqualTo(1)
        assertThat(slot.bonusPercentage).isEqualTo(5)
        assertThat(slot.state).isEqualTo(PreyDataState.Selection)
        assertThat(slot.raceIdList).hasSize(9)
        assertThat(player.reloadedPreySlots).containsExactly(0)
    }

    @Test
    fun `checkPlayerPreys automatic reroll spends a card and keeps the monster`() {
        val engine = newEngine()
        val player = newPlayer(engine, cards = 1)
        val slot = player.getPreySlotById(0)!!
        slot.selectedRaceId = 5
        slot.bonusTimeLeft = 2
        slot.option = PreyOption.AutomaticReroll

        engine.checkPlayerPreys(player, 10)

        assertThat(player.getPreyCards()).isEqualTo(0)
        assertThat(slot.selectedRaceId).isEqualTo(5)
        assertThat(slot.bonusTimeLeft).isEqualTo(7200)
        assertThat(slot.bonus).isNotEqualTo(PreyBonus.None)
        assertThat(player.textMessages).containsExactly("Your prey bonus type and time has been succesfully reseted.")
        assertThat(player.reloadedPreySlots).containsExactly(0)
    }

    @Test
    fun `checkPlayerPreys automatic reroll without cards falls back to expiry`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getPreySlotById(0)!!
        slot.selectedRaceId = 5
        slot.bonusTimeLeft = 2
        slot.option = PreyOption.AutomaticReroll

        engine.checkPlayerPreys(player, 10)

        assertThat(player.textMessages).containsExactly(
            "You don't have enought prey cards to enable automatic reroll when your slot expire.",
        )
        assertThat(slot.selectedRaceId).isEqualTo(0)
        assertThat(slot.bonus).isEqualTo(PreyBonus.None)
    }

    @Test
    fun `checkPlayerPreys locked option buys time without rerolling monster`() {
        val engine = newEngine()
        val player = newPlayer(engine, cards = 5)
        val slot = player.getPreySlotById(0)!!
        slot.selectedRaceId = 5
        slot.bonusTimeLeft = 2
        slot.option = PreyOption.Locked
        slot.bonus = PreyBonus.Loot
        slot.bonusRarity = 7
        slot.bonusPercentage = 31

        engine.checkPlayerPreys(player, 10)

        assertThat(player.getPreyCards()).isEqualTo(0)
        assertThat(slot.selectedRaceId).isEqualTo(5)
        assertThat(slot.bonusTimeLeft).isEqualTo(7200)
        assertThat(slot.bonus).isEqualTo(PreyBonus.Loot)
        assertThat(slot.bonusPercentage).isEqualTo(31)
        assertThat(player.textMessages).containsExactly("Your prey bonus time has been succesfully reseted.")
        assertThat(player.reloadedPreySlots).containsExactly(0)
    }

    // ---- Bonus application ----------------------------------------------------

    @Test
    fun `damage bonus applies truncated percentage to primary and secondary`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getPreySlotById(0)!!
        slot.bonus = PreyBonus.Damage
        slot.bonusPercentage = 25
        slot.selectedRaceId = 5
        slot.bonusTimeLeft = 100
        val damage = CombatDamage(primaryValue = 10, secondaryValue = 7)

        engine.applyDamageBonus(damage, player, 5)

        // Canary divides as integers before std::ceil: 10*25/100 = 2, 7*25/100 = 1.
        assertThat(damage.primaryValue).isEqualTo(12)
        assertThat(damage.secondaryValue).isEqualTo(8)
    }

    @Test
    fun `defense bonus reduces truncated percentage`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getPreySlotById(0)!!
        slot.bonus = PreyBonus.Defense
        slot.bonusPercentage = 30
        slot.selectedRaceId = 5
        slot.bonusTimeLeft = 100
        val damage = CombatDamage(primaryValue = 10, secondaryValue = 3)

        engine.applyDefenseBonus(damage, player, 5)

        assertThat(damage.primaryValue).isEqualTo(7)
        assertThat(damage.secondaryValue).isEqualTo(3)
    }

    @Test
    fun `bonus application ignores inactive slots and wrong bonus types`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getPreySlotById(0)!!
        slot.bonus = PreyBonus.Loot
        slot.bonusPercentage = 40
        slot.selectedRaceId = 5
        slot.bonusTimeLeft = 100

        val damage = CombatDamage(primaryValue = 10, secondaryValue = 7)
        engine.applyDamageBonus(damage, player, 5)
        assertThat(damage.primaryValue).isEqualTo(10)
        assertThat(damage.secondaryValue).isEqualTo(7)

        slot.bonus = PreyBonus.Damage
        slot.bonusTimeLeft = 0
        engine.applyDamageBonus(damage, player, 5)
        assertThat(damage.primaryValue).isEqualTo(10)

        slot.bonusTimeLeft = 100
        engine.applyDamageBonus(damage, player, 99)
        assertThat(damage.primaryValue).isEqualTo(10)
    }

    @Test
    fun `experience and loot percentage primitives mirror canary player functions`() {
        val engine = newEngine()
        val player = newPlayer(engine)
        val slot = player.getPreySlotById(0)!!
        slot.selectedRaceId = 5
        slot.bonusTimeLeft = 100

        slot.bonus = PreyBonus.Experience
        slot.bonusPercentage = 40
        assertThat(engine.getPreyExperiencePercentage(player, 5)).isEqualTo(140)
        assertThat(engine.getPreyLootPercentage(player, 5)).isEqualTo(0)

        slot.bonus = PreyBonus.Loot
        assertThat(engine.getPreyExperiencePercentage(player, 5)).isEqualTo(100)
        assertThat(engine.getPreyLootPercentage(player, 5)).isEqualTo(40)

        slot.bonus = PreyBonus.Damage
        assertThat(engine.getPreyExperiencePercentage(player, 5)).isEqualTo(100)
        assertThat(engine.getPreyLootPercentage(player, 5)).isEqualTo(0)
    }
}
