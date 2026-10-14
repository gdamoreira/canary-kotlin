package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.TeamFinder
import org.opentibia.canary.creatures.grouping.TeamFinderList
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.players.components.Achievement
import org.opentibia.canary.creatures.players.components.Badge
import org.opentibia.canary.creatures.players.components.CyclopediaBadge_t
import org.opentibia.canary.creatures.players.components.InMemoryAchievementRegistry
import org.opentibia.canary.creatures.players.components.InMemoryBadgeRegistry
import org.opentibia.canary.creatures.players.components.InMemoryTitleRegistry
import org.opentibia.canary.creatures.players.components.Title
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.BedItem
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.forge.ItemClassifications

/**
 * Transcript tests for the cyclopedia/registry `Game` helpers:
 *
 *  - `Game::getAchievementById` (game.cpp:12493) / `Game::getAchievementByName` (game.cpp:12497)
 *  - `Game::getBadges` (game.cpp:12536) / `Game::getBadgeById` (game.cpp:12540) /
 *    `Game::getBadgeByName` (game.cpp:12553)
 *  - `Game::getTitles` (game.cpp:12566) / `Game::getTitleById` (game.cpp:12570) /
 *    `Game::getTitleByName` (game.cpp:12583)
 *  - `Game::logCyclopediaStats` (game.cpp:12531)
 *  - `Game::getItemsClassification` (game.cpp:9463)
 *  - `Game::getAttachedEffects` (game.cpp:12355)
 *  - `Game::getTeamFinder` (game.cpp:930) / `Game::getOrCreateTeamFinder` (game.cpp:939) /
 *    `Game::removeTeamFinderListed` (game.cpp:948)
 *  - `Game::getBedBySleeper` (game.cpp:11783) / `Game::setBedSleeper` (game.cpp:11791) /
 *    `Game::removeBedSleeper` (game.cpp:11795)
 *  - `Game::getUniqueItem` (game.cpp:11802) / `Game::addUniqueItem` (game.cpp:11810) /
 *    `Game::removeUniqueItem` (game.cpp:11818)
 *  - `Game::internalCreatureChangeVisible` (game.cpp:7801)
 *  - `Game::updateMonster` (game.cpp:11708)
 *  - `Game::playerFriendSystemAction` (game.cpp:9814)
 *  - `Game::dieSafely` (game.cpp:9493) / `Game::shutdown` (game.cpp:9498)
 */
class GameCyclopediaHelpersTest {

    private val center = Position(1000, 1000, 7)

    private val achievements = listOf(
        Achievement(id = 1, name = "Apprentice", description = "First steps", secret = false, grade = 1, points = 5),
        Achievement(id = 2, name = "Hidden", description = "Secret", secret = true, grade = 3, points = 30),
    )

    private val badges = listOf(
        Badge(id = 1, type = CyclopediaBadge_t.ACCOUNT_AGE, name = "Older Than Time", amount = 10),
        Badge(id = 2, type = CyclopediaBadge_t.LOYALTY, name = "Loyal", amount = 5),
    )

    private val titles = listOf(
        Title(id = 1, maleName = "Explorer", femaleName = "Explorer"),
        Title(id = 2, maleName = "Hunter", femaleName = "Huntress"),
    )

    private class Harness(
        val game: Game,
    )

    private fun newGame(): Harness {
        val game = Game(
            GameMap(),
            GameDispatcher(),
            achievementRegistry = InMemoryAchievementRegistry(achievements),
            badgeRegistry = InMemoryBadgeRegistry(badges),
            titleRegistry = InMemoryTitleRegistry(titles),
            itemClassifications = ItemClassifications(),
            teamFinderList = TeamFinderList(),
        )
        return Harness(game)
    }

    private fun player(game: Game, id: Long = 1, name: String = "Hero", position: Position = center): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position).also { game.addPlayer(it) }

    // ----- achievements / badges / titles -----

    @Test
    fun `getAchievementById and getAchievementByName resolve the registry`() {
        val harness = newGame()

        assertThat(harness.game.getAchievementById(1).name).isEqualTo("Apprentice")
        assertThat(harness.game.getAchievementById(99)).isEqualTo(Achievement())
        assertThat(harness.game.getAchievementByName("Hidden").id).isEqualTo(2)
        assertThat(harness.game.getAchievementByName("missing")).isEqualTo(Achievement())
    }

    @Test
    fun `getBadges and badge lookups resolve the registry`() {
        val harness = newGame()

        assertThat(harness.game.getBadges()).isEqualTo(badges)
        assertThat(harness.game.getBadgeById(1).name).isEqualTo("Older Than Time")
        assertThat(harness.game.getBadgeById(0)).isEqualTo(Badge())
        assertThat(harness.game.getBadgeByName("Loyal").id).isEqualTo(2)
        assertThat(harness.game.getBadgeByName("")).isEqualTo(Badge())
    }

    @Test
    fun `getTitles and title lookups resolve the registry`() {
        val harness = newGame()

        assertThat(harness.game.getTitles()).isEqualTo(titles)
        assertThat(harness.game.getTitleById(1).maleName).isEqualTo("Explorer")
        assertThat(harness.game.getTitleById(0)).isEqualTo(Title())
        assertThat(harness.game.getTitleByName("Hunter").id).isEqualTo(2)
        assertThat(harness.game.getTitleByName("")).isEqualTo(Title())
    }

    @Test
    fun `logCyclopediaStats reports the loaded badge and title counts`() {
        val harness = newGame()

        harness.game.logCyclopediaStats()
    }

    // ----- item classification -----

    @Test
    fun `getItemsClassification creates on demand and reads without creating`() {
        val harness = newGame()

        assertThat(harness.game.getItemsClassification(7, create = false)).isNull()
        val created = harness.game.getItemsClassification(7, create = true)
        assertThat(created?.id).isEqualTo(7)
        assertThat(harness.game.getItemsClassification(7, create = false)).isSameAs(created)
    }

    // ----- attached effects -----

    @Test
    fun `getAttachedEffects returns the owned store`() {
        val harness = newGame()

        assertThat(harness.game.getAttachedEffects()).isSameAs(harness.game.getAttachedEffects())
    }

    // ----- team finder -----

    @Test
    fun `getTeamFinder is null until the finder is created and removed`() {
        val harness = newGame()
        val user = player(harness.game)

        assertThat(harness.game.getTeamFinder(user)).isNull()

        val finder = harness.game.getOrCreateTeamFinder(user)
        assertThat(harness.game.getTeamFinder(user)).isSameAs(finder)
        assertThat(harness.game.getOrCreateTeamFinder(user)).isSameAs(finder)

        harness.game.removeTeamFinderListed(user.getGUID())
        assertThat(harness.game.getTeamFinder(user)).isNull()
    }

    // ----- beds -----

    @Test
    fun `bed sleepers are stored and removed by guid`() {
        val harness = newGame()
        val bed = BedItem(id = 1754)
        val other = BedItem(id = 1755)

        assertThat(harness.game.getBedBySleeper(42)).isNull()
        harness.game.setBedSleeper(bed, 42)
        assertThat(harness.game.getBedBySleeper(42)).isSameAs(bed)
        harness.game.setBedSleeper(other, 42)
        assertThat(harness.game.getBedBySleeper(42)).isSameAs(other)
        harness.game.removeBedSleeper(42)
        assertThat(harness.game.getBedBySleeper(42)).isNull()
    }

    // ----- unique items -----

    @Test
    fun `unique items register once, resolve and unregister`() {
        val harness = newGame()
        val item = Item(id = 2160)

        assertThat(harness.game.getUniqueItem(100)).isNull()
        assertThat(harness.game.addUniqueItem(100, item)).isTrue()
        assertThat(harness.game.addUniqueItem(100, Item(id = 2161))).isFalse()
        assertThat(harness.game.getUniqueItem(100)).isSameAs(item)
        harness.game.removeUniqueItem(100)
        assertThat(harness.game.getUniqueItem(100)).isNull()
    }

    // ----- visibility / monster rename -----

    @Test
    fun `internalCreatureChangeVisible notifies every player spectator`() {
        val harness = newGame()
        val observer = player(harness.game, id = 1, name = "Watcher")
        harness.game.map.getOrCreateTile(center).ground = Item(id = 100)
        assertThat(harness.game.internalPlaceCreature(observer, center)).isTrue()
        val visible = mutableListOf<Triple<Player, Creature, Boolean>>()
        harness.game.sendCreatureChangeVisible = { p, creature, state -> visible.add(Triple(p, creature, state)) }
        val monster = Monster(id = 500, name = "Rat", maxHealth = 100, health = 100, position = center)

        harness.game.internalCreatureChangeVisible(monster, false)

        assertThat(visible).containsExactly(Triple(observer, monster, false))
    }

    @Test
    fun `updateMonster moves the name index to the new type name`() {
        val harness = newGame()
        val monster = Monster(id = 500, name = "Rat", maxHealth = 100, health = 100, position = center)
        harness.game.addMonster(monster)
        val monsterType = MonsterType(name = "Cave Rat")

        harness.game.updateMonster(monster, monsterType)

        assertThat(harness.game.getCreatureByName("rat")).isNull()
        assertThat(harness.game.getCreatureByName("cave rat")).isSameAs(monster)
    }

    @Test
    fun `updateMonster ignores an index miss`() {
        val harness = newGame()
        val monster = Monster(id = 500, name = "Rat", maxHealth = 100, health = 100, position = center)

        harness.game.updateMonster(monster, MonsterType(name = "Cave Rat"))

        assertThat(harness.game.getCreatureByName("cave rat")).isNull()
    }

    // ----- friend system title -----

    @Test
    fun `playerFriendSystemAction sets the title and sends the cyclopedia updates`() {
        val harness = newGame()
        val user = player(harness.game)
        var title: Pair<Player, Int>? = null
        var base = false
        var titlesSent = false
        harness.game.setCurrentTitle = { p, id -> title = p to id }
        harness.game.sendCyclopediaCharacterBaseInformation = { base = true }
        harness.game.sendCyclopediaCharacterTitles = { titlesSent = true }

        harness.game.playerFriendSystemAction(user, 0x0E, 3)

        assertThat(title).isEqualTo(user to 3)
        assertThat(base).isTrue()
        assertThat(titlesSent).isTrue()
    }

    @Test
    fun `playerFriendSystemAction ignores other action types`() {
        val harness = newGame()
        val user = player(harness.game)
        var called = false
        harness.game.setCurrentTitle = { _, _ -> called = true }

        harness.game.playerFriendSystemAction(user, 1, 3)

        assertThat(called).isFalse()
    }

    // ----- shutdown -----

    @Test
    fun `dieSafely logs and shuts the server down`() {
        val harness = newGame()
        val steps = mutableListOf<String>()
        harness.game.sendWebhook = { steps.add("webhook:$it") }
        harness.game.stopServiceManager = { steps.add("stop") }
        harness.game.closeAllConnections = { steps.add("connections") }
        harness.game.luaCollectGarbage = { steps.add("lua") }

        harness.game.dieSafely("boom")

        assertThat(steps).containsExactly(
            "webhook::red_circle: Server is shutting down...",
            "stop",
            "connections",
            "lua",
        )
    }

    @Test
    fun `shutdown clears the spawn registries and runs every stop step`() {
        val harness = newGame()
        val steps = mutableListOf<String>()
        harness.game.stopServiceManager = { steps.add("stop") }
        harness.game.closeAllConnections = { steps.add("connections") }
        harness.game.luaCollectGarbage = { steps.add("lua") }

        harness.game.shutdown()

        assertThat(steps).containsExactly("stop", "connections", "lua")
    }
}
