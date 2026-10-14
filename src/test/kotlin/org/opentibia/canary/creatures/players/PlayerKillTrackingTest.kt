package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.systems.bestiary.IOBestiary
import org.opentibia.canary.systems.concoction.ConcoctionType
import org.opentibia.canary.systems.taskhunting.PreyTaskDataState
import org.opentibia.canary.systems.taskhunting.TaskHuntingOption
import org.opentibia.canary.systems.taskhunting.TaskHuntingSlot

/**
 * Transcript tests for the Player kill trackers.
 *
 * Source of truth: /Users/damoreira/.projects/canary
 *  - src/creatures/players/player.cpp:6606-6660 (hunting/bestiary/bosstiary kills)
 *  - src/creatures/players/player.cpp:6680-6698 (`onKilledMonster`)
 *  - src/config/configmanager.cpp:228,230,234 (kill multipliers / boss bonus)
 */
class PlayerKillTrackingTest {

    private fun newPlayer() = Player(id = 1, name = "Hunter", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    private fun monsterType(raceId: Int, name: String = "Rat") = MonsterType(name).apply {
        info.raceid = raceId
    }

    @Test
    fun `addHuntingTaskKill increments the selected slot and completes at the stage`() {
        // player.cpp:6606-6621.
        val player = newPlayer()
        val slot = TaskHuntingSlot(id = 1)
        slot.selectedRaceId = 55
        player.features.taskHunting.taskHunting.add(slot)
        val messages = mutableListOf<Pair<MessageClass, String>>()
        player.sendTextMessageTyped = { type, text -> messages.add(type to text) }

        val mType = monsterType(55)
        player.addHuntingTaskKill(mType, getTaskRewardOption = { TaskHuntingOption(firstKills = 2, secondKills = 4) })

        assertThat(slot.currentKills).isEqualTo(1)
        assertThat(messages).isEmpty()

        player.addHuntingTaskKill(mType, getTaskRewardOption = { TaskHuntingOption(firstKills = 2, secondKills = 4) })

        assertThat(slot.currentKills).isEqualTo(2)
        assertThat(slot.state).isEqualTo(PreyTaskDataState.Completed)
        assertThat(messages).containsExactly(
            MessageClass.MESSAGE_STATUS to "You succesfully finished your hunting task. Your reward is ready to be claimed!",
        )
        assertThat(player.features.taskHunting.reloadedTaskSlots).containsExactly(1, 1)
    }

    @Test
    fun `addHuntingTaskKill uses the upgraded threshold for upgraded slots`() {
        // player.cpp:6633-6634 (`taskSlot->upgrade ? secondKills : firstKills`).
        val player = newPlayer()
        val slot = TaskHuntingSlot(id = 1).apply {
            selectedRaceId = 55
            upgrade = true
        }
        player.features.taskHunting.taskHunting.add(slot)

        player.addHuntingTaskKill(monsterType(55), getTaskRewardOption = { TaskHuntingOption(firstKills = 1, secondKills = 3) })

        assertThat(slot.currentKills).isEqualTo(1)
        assertThat(slot.state).isEqualTo(PreyTaskDataState.Inactive)
    }

    @Test
    fun `addBestiaryKill awards the configured multiplier`() {
        // player.cpp:6623-6641.
        val player = newPlayer()
        val bestiary = IOBestiary()

        player.addBestiaryKill(monsterType(70), iobestiary = bestiary, bestiaryKillMultiplier = 5)

        assertThat(player.features.bestiary.getBestiaryKillCount(70)).isEqualTo(5)
    }

    @Test
    fun `addBestiaryKill doubles for the event and the concoction`() {
        // player.cpp:6630-6638.
        val player = newPlayer()
        player.features.concoctions.updateConcoction(ConcoctionType.BestiaryBetterment.itemId, 60_000)

        player.addBestiaryKill(
            monsterType(70),
            iobestiary = IOBestiary(),
            bestiaryKillMultiplier = 2,
            doubleBestiaryEnabled = { true },
        )

        assertThat(player.features.bestiary.getBestiaryKillCount(70)).isEqualTo(8)
    }

    @Test
    fun `addBestiaryKill ignores bosses`() {
        // player.cpp:6624-6626.
        val player = newPlayer()
        val boss = monsterType(71).apply { info.bosstiaryClass = "Nemesis" }

        player.addBestiaryKill(boss, iobestiary = IOBestiary(), bestiaryKillMultiplier = 3)

        assertThat(player.features.bestiary.getBestiaryKillCount(71)).isZero()
    }

    @Test
    fun `addBosstiaryKill awards only bosses with the boosted bonus`() {
        // player.cpp:6643-6660.
        val player = newPlayer()
        val calls = mutableListOf<Triple<Player, MonsterType, Int>>()
        val boss = monsterType(80).apply { info.bosstiaryClass = "Nemesis" }
        val rat = monsterType(81)

        player.addBosstiaryKill(
            boss,
            ioBosstiaryAddKill = { p, m, amount -> calls.add(Triple(p, m, amount)) },
            bosstiaryKillMultiplier = 2,
            doubleBosstiaryEnabled = { true },
            boostedBossId = { 80 },
        )
        player.addBosstiaryKill(rat, ioBosstiaryAddKill = { p, m, amount -> calls.add(Triple(p, m, amount)) })

        assertThat(calls).hasSize(1)
        assertThat(calls.single().first).isSameAs(player)
        assertThat(calls.single().second).isSameAs(boss)
        // 2 (config) * 2 (double event) * 3 (boosted boss bonus).
        assertThat(calls.single().third).isEqualTo(12)
    }

    @Test
    fun `onKilledMonster skips loot and tracking for summoned monsters`() {
        // player.cpp:6681-6686.
        val player = newPlayer()
        val monster = Monster(9, "Rat", maxHealth = 10, health = 0, position = Position(100, 100, 7))
        monster.setMaster(null)
        var callback = false

        val result = player.onKilledMonster(
            monster,
            monsterType(90),
            executePlayerOnKillCallback = { _, _ -> callback = true },
        )

        assertThat(result).isFalse()
        assertThat(callback).isFalse()
    }

    @Test
    fun `onKilledMonster applies NotGenerateLoot and runs every tracker`() {
        // player.cpp:6681-6697.
        val player = newPlayer()
        player.setFlag(PlayerFlags_t.NotGenerateLoot)
        val monster = Monster(9, "Rat", maxHealth = 10, health = 0, position = Position(100, 100, 7))
        val mType = monsterType(90)
        val tracking = mutableListOf<String>()

        val result = player.onKilledMonster(
            monster,
            mType,
            executePlayerOnKillCallback = { _, _ -> tracking.add("callback") },
            addHuntingTaskKill = { _, _ -> tracking.add("hunting") },
            addBestiaryKill = { _, _ -> tracking.add("bestiary") },
            addBosstiaryKill = { _, _ -> tracking.add("bosstiary") },
        )

        assertThat(result).isFalse()
        assertThat(monster.lootDrop).isFalse()
        assertThat(tracking).containsExactly("callback", "hunting", "bestiary", "bosstiary")
    }

    @Test
    fun `onKilledMonster skips soulless-pit tracking`() {
        // player.cpp:6692-6697.
        val player = newPlayer()
        val monster = Monster(9, "Rat", maxHealth = 10, health = 0, position = Position(100, 100, 7))
        monster.setSoulPit(true)
        val tracking = mutableListOf<String>()

        player.onKilledMonster(
            monster,
            monsterType(90),
            executePlayerOnKillCallback = { _, _ -> tracking.add("callback") },
            addHuntingTaskKill = { _, _ -> tracking.add("hunting") },
            addBestiaryKill = { _, _ -> tracking.add("bestiary") },
            addBosstiaryKill = { _, _ -> tracking.add("bosstiary") },
        )

        assertThat(tracking).isEmpty()
    }

    @Test
    fun `onKilledMonster ignores a null monster type`() {
        // player.cpp:6687-6690.
        val player = newPlayer()
        val monster = Monster(9, "Rat", maxHealth = 10, health = 0, position = Position(100, 100, 7))
        var callback = false

        val result = player.onKilledMonster(
            monster,
            null,
            executePlayerOnKillCallback = { _, _ -> callback = true },
        )

        assertThat(result).isFalse()
        assertThat(callback).isFalse()
    }
}
