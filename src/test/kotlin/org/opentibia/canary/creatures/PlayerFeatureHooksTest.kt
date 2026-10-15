package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatDamage
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.players.components.PlayerAsyncTask
import org.opentibia.canary.creatures.players.components.SummaryCategory
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.forge.ForgeHistory
import org.opentibia.canary.systems.prey.PreyBonus
import org.opentibia.canary.systems.prey.PreyDataState
import org.opentibia.canary.systems.prey.PreyEngine
import org.opentibia.canary.systems.prey.PreySlot
import org.opentibia.canary.systems.taskhunting.PreyTaskAction
import org.opentibia.canary.systems.taskhunting.PreyTaskDataState
import org.opentibia.canary.systems.taskhunting.TaskHuntingBestiary
import org.opentibia.canary.systems.taskhunting.TaskHuntingConfig
import org.opentibia.canary.systems.taskhunting.TaskHuntingEngine
import org.opentibia.canary.systems.taskhunting.TaskHuntingSlot
import org.opentibia.canary.systems.taskhunting.TaskMonsterInfo

/**
 * Transcript tests for the `Player` advanced-feature hooks.
 *
 * Expected behavior is transcribed from:
 *  - src/creatures/players/player.hpp:382-394 (cyclopedia tracker set)
 *  - src/creatures/players/player.hpp:1320-1367 (prey / task hunting)
 *  - src/creatures/players/player.hpp:1505, 1521-1522 (wheel / cyclopedia)
 *  - src/creatures/players/player.hpp:1164-1166 (async ongoing tasks)
 *  - src/creatures/players/player.cpp:941-996 (cyclopedia tracker)
 *  - src/creatures/players/player.cpp:10392-10681 (prey / task accessors)
 *  - src/creatures/players/player.cpp:11825-11866 (forge dust ledger)
 *  - src/creatures/players/player.cpp:12749-12798 (component interfaces)
 *  - src/creatures/combat/combat.cpp:887-909 (prey damage/defense bonus)
 */
class PlayerFeatureHooksTest {

    private fun newPlayer(level: Int = 1, isPremium: Boolean = false): Player = Player(
        id = 42,
        name = "Hook Hero",
        maxHealth = 200,
        health = 200,
        position = Position(100, 100, 7),
        level = level,
    ).apply {
        this.premium = isPremium
    }

    // ---- wheel -----------------------------------------------------------------

    @Test
    fun `wheel hook returns the features wheel component`() {
        val player = newPlayer(level = 250)

        assertThat(player.wheel()).isSameAs(player.features.wheel)
        assertThat(player.wheel().getWheelPoints()).isEqualTo(200)
    }

    // ---- forge -----------------------------------------------------------------

    @Test
    fun `forge dust hooks delegate to the forge component`() {
        val player = newPlayer()

        player.setForgeDusts(100)
        player.addForgeDusts(50)
        player.removeForgeDusts(30)
        player.addForgeDustLevel(2)
        player.removeForgeDustLevel(1)

        assertThat(player.getForgeDusts()).isEqualTo(120)
        assertThat(player.getForgeDustLevel()).isEqualTo(1)
        assertThat(player.features.forge.getForgeDusts()).isEqualTo(120)
        assertThat(player.features.forge.getForgeDustLevel()).isEqualTo(1)
    }

    @Test
    fun `forge history hook returns the player forge history`() {
        val player = newPlayer()

        assertThat(player.forgeHistory()).isSameAs(player.features.forgeHistory)

        player.forgeHistory().add(ForgeHistory(id = 3, description = "fusion"))

        assertThat(player.forgeHistory().get().map { it.id }).containsExactly(3)
    }

    // ---- prey ------------------------------------------------------------------

    @Test
    fun `prey card hooks delegate to the prey state`() {
        val player = newPlayer()

        assertThat(player.getPreyCards()).isEqualTo(0)
        player.addPreyCards(10)
        assertThat(player.usePreyCards(4)).isTrue()
        assertThat(player.usePreyCards(7)).isFalse()
        assertThat(player.getPreyCards()).isEqualTo(6)
        assertThat(player.features.prey.getPreyCards()).isEqualTo(6)
    }

    @Test
    fun `prey slot hooks delegate to the prey state`() {
        val player = newPlayer()
        val slot = PreySlot(0).apply {
            state = PreyDataState.Active
            selectedRaceId = 5
            bonusTimeLeft = 100
        }

        assertThat(player.setPreySlotClass(slot)).isTrue()
        assertThat(player.setPreySlotClass(slot)).isFalse()
        assertThat(player.getPreySlotById(0)).isSameAs(slot)
        assertThat(player.getPreyWithMonster(5)).isSameAs(slot)
        assertThat(player.getPreyBlackList()).contains(5)

        player.reloadPreySlot(0)
        assertThat(player.features.prey.reloadedPreySlots).containsExactly(0)

        player.removePreySlotById(0)
        assertThat(player.getPreySlotById(0)).isNull()
    }

    @Test
    fun `prey reroll price follows the player level`() {
        val player = newPlayer(level = 250)

        assertThat(player.getPreyRerollPrice()).isEqualTo(50_000)
    }

    @Test
    fun `initializePrey installs the premium gated slots through the hook`() {
        val nonPremium = newPlayer(isPremium = false)
        nonPremium.initializePrey()

        assertThat(nonPremium.getPreySlotById(0)?.state).isEqualTo(PreyDataState.Selection)
        assertThat(nonPremium.getPreySlotById(1)?.state).isEqualTo(PreyDataState.Locked)
        assertThat(nonPremium.getPreySlotById(2)?.state).isEqualTo(PreyDataState.Locked)

        val premium = newPlayer(isPremium = true)
        premium.initializePrey()

        assertThat(premium.getPreySlotById(1)?.state).isEqualTo(PreyDataState.Selection)
    }

    @Test
    fun `prey damage bonus applies through the player state hook`() {
        val player = newPlayer(level = 100)
        val slot = PreySlot(0).apply {
            state = PreyDataState.Active
            selectedRaceId = 5
            bonus = PreyBonus.Damage
            bonusPercentage = 25
            bonusTimeLeft = 100
        }
        player.setPreySlotClass(slot)
        val damage = CombatDamage(primaryValue = 10, secondaryValue = 7)

        PreyEngine().applyDamageBonus(damage, player.features.prey, 5)

        assertThat(player.getPreyWithMonster(5)).isSameAs(slot)
        assertThat(damage.primaryValue).isEqualTo(12)
        assertThat(damage.secondaryValue).isEqualTo(8)
    }

    // ---- task hunting ----------------------------------------------------------

    @Test
    fun `task hunting point hooks delegate to the task state`() {
        val player = newPlayer(level = 250)

        player.addTaskHuntingPoints(100)
        assertThat(player.useTaskHuntingPoints(40)).isTrue()
        assertThat(player.useTaskHuntingPoints(61)).isFalse()

        assertThat(player.getTaskHuntingPoints()).isEqualTo(60)
        assertThat(player.getTaskHuntingRerollPrice()).isEqualTo(50_000)
        assertThat(player.features.taskHunting.getTaskHuntingPoints()).isEqualTo(60)
    }

    @Test
    fun `task hunting slot hooks delegate to the task state`() {
        val player = newPlayer()
        val slot = TaskHuntingSlot(0).apply {
            state = PreyTaskDataState.Active
            selectedRaceId = 7
        }

        assertThat(player.setTaskHuntingSlotClass(slot)).isTrue()
        assertThat(player.getTaskHuntingSlotById(0)).isSameAs(slot)
        assertThat(player.getTaskHuntingWithCreature(7)).isSameAs(slot)
        assertThat(player.getTaskHuntingBlackList()).contains(7)

        player.reloadTaskSlot(0)
        assertThat(player.features.taskHunting.reloadedTaskSlots).containsExactly(0)
    }

    @Test
    fun `initializeTaskHunting installs the premium gated slots through the hook`() {
        val nonPremium = newPlayer(isPremium = false)
        nonPremium.initializeTaskHunting()

        assertThat(nonPremium.getTaskHuntingSlotById(0)?.state).isEqualTo(PreyTaskDataState.Selection)
        assertThat(nonPremium.getTaskHuntingSlotById(1)?.state).isEqualTo(PreyTaskDataState.Locked)
        assertThat(nonPremium.getTaskHuntingSlotById(2)?.state).isEqualTo(PreyTaskDataState.Locked)

        val premium = newPlayer(isPremium = true)
        premium.initializeTaskHunting()

        assertThat(premium.getTaskHuntingSlotById(1)?.state).isEqualTo(PreyTaskDataState.Selection)
    }

    @Test
    fun `isCreatureUnlockedOnTaskHunting reads the player bestiary kill count`() {
        val player = newPlayer()
        player.features.bestiary.addBestiaryKillCount(5, 10)

        assertThat(player.isCreatureUnlockedOnTaskHunting(TaskMonsterInfo(raceid = 5, bestiaryToUnlock = 5))).isTrue()
        assertThat(player.isCreatureUnlockedOnTaskHunting(TaskMonsterInfo(raceid = 5, bestiaryToUnlock = 11))).isFalse()
        assertThat(player.isCreatureUnlockedOnTaskHunting(null)).isFalse()
    }

    @Test
    fun `task reward claim credits points through the player hooks`() {
        val player = newPlayer()
        val bestiary = TaskHuntingBestiary(
            listOf(
                TaskMonsterInfo(
                    raceid = 5,
                    name = "Rat",
                    experience = 100,
                    bestiaryStars = 1,
                    bestiaryToUnlock = 0,
                ),
            ),
        )
        val engine = TaskHuntingEngine(TaskHuntingConfig(), bestiary, { min, _ -> min }, { 0L })
        val slot = TaskHuntingSlot(0, TaskHuntingConfig(), bestiary, { min, _ -> min }, { 0L }).apply {
            state = PreyTaskDataState.Active
            selectedRaceId = 5
            rarity = 1
            currentKills = 25
        }
        player.setTaskHuntingSlotClass(slot)

        engine.parseTaskHuntingAction(player.features.taskHunting, 0, PreyTaskAction.Claim)

        assertThat(player.getTaskHuntingPoints()).isEqualTo(10)
        assertThat(slot.state).isEqualTo(PreyTaskDataState.Inactive)
    }

    // ---- cyclopedia ------------------------------------------------------------

    @Test
    fun `cyclopedia hook returns the component bound to the player kv`() {
        val player = newPlayer()

        assertThat(player.cyclopedia()).isSameAs(player.features.cyclopedia)

        player.cyclopedia().updateAmount(SummaryCategory.PREY_CARDS, 5)

        assertThat(player.cyclopedia().getAmount(SummaryCategory.PREY_CARDS)).isEqualTo(5)
        assertThat(player.kv.scoped("summary").scoped("prey-cards").get("amount")!!.getInt()).isEqualTo(5)
    }

    @Test
    fun `async ongoing task flag hooks mirror the cyclopedia contract`() {
        val player = newPlayer()

        assertThat(player.hasAsyncOngoingTask(PlayerAsyncTask.RecentDeaths)).isFalse()

        player.addAsyncOngoingTask(PlayerAsyncTask.RecentDeaths)
        assertThat(player.hasAsyncOngoingTask(PlayerAsyncTask.RecentDeaths)).isTrue()
        assertThat(player.hasAsyncOngoingTask(PlayerAsyncTask.RecentPvPKills)).isFalse()

        player.resetAsyncOngoingTask(PlayerAsyncTask.RecentDeaths)
        assertThat(player.hasAsyncOngoingTask(PlayerAsyncTask.RecentDeaths)).isFalse()
    }

    @Test
    fun `cyclopedia monster tracker hooks change state only with a client`() {
        val player = newPlayer()
        val rat = MonsterType("Rat").apply { info.raceid = 5 }
        val client = FakeCyclopediaMonsterTrackerClient()

        player.addMonsterToCyclopediaTrackerList(rat, isBoss = false)
        assertThat(player.getCyclopediaMonsterTrackerSet(false)).isEmpty()

        player.cyclopediaMonsterTrackerClient = client

        player.addMonsterToCyclopediaTrackerList(rat, isBoss = false)
        player.addMonsterToCyclopediaTrackerList(rat, isBoss = false)
        assertThat(player.getCyclopediaMonsterTrackerSet(false)).containsExactly(rat)
        assertThat(client.refreshes).hasSize(1)

        player.addMonsterToCyclopediaTrackerList(rat, isBoss = true, reloadClient = true)
        assertThat(player.getCyclopediaMonsterTrackerSet(true)).containsExactly(rat)
        assertThat(client.bosstiarySends).isEqualTo(1)

        player.removeMonsterFromCyclopediaTrackerList(rat, isBoss = true, reloadClient = true)
        assertThat(player.getCyclopediaMonsterTrackerSet(true)).isEmpty()
        assertThat(client.refreshes).hasSize(3)
    }

    private class FakeCyclopediaMonsterTrackerClient : CyclopediaMonsterTrackerClient {
        val refreshes = mutableListOf<Pair<Set<MonsterType>, Boolean>>()
        val bestiaryEntryChanges = mutableListOf<Int>()
        var bosstiarySends = 0

        override fun parseSendBosstiary() {
            bosstiarySends++
        }

        override fun sendBestiaryEntryChanged(raceId: Int) {
            bestiaryEntryChanges.add(raceId)
        }

        override fun refreshCyclopediaMonsterTracker(tracker: Set<MonsterType>, isBoss: Boolean) {
            refreshes.add(tracker.toSet() to isBoss)
        }
    }
}
