package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.MonsterPipelinesHost
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.bestiary.Charm
import org.opentibia.canary.systems.bestiary.CharmRune
import org.opentibia.canary.systems.bestiary.CharmType
import org.opentibia.canary.systems.bestiary.PlayerCharmsByMonster
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.io.File

/**
 * Transcript tests for the final `Game` member group:
 *
 *  - `Game::collectRewardChestItems` (game.cpp:3898)
 *  - `Game::playerRewardChestCollect` (game.cpp:12296)
 *  - `Game::registerAchievement` / `getSecretAchievements` /
 *    `getPublicAchievements` / `getAchievements` (game.cpp:12481-12527)
 *  - `Game::loadMotdNum` (game.cpp:9592)
 *  - `Game::start` (game.cpp:862)
 *  - `Game::resetMonsters` (game.cpp:770)
 *  - `Game::addMoney` (game.cpp:3367)
 *  - `Game::updatePlayersOnline` (game.cpp:12608)
 *  - `Game::playerCheckActivity` (game.cpp:12263)
 *  - `Game::setCreateLuaItems` / `createLuaItemsOnMap` (game.cpp:11845)
 *  - `Game::parsePlayerExtendedOpcode` (game.cpp:11114)
 *  - `Game::playerDebugAssert` (game.cpp:10203)
 *  - `Game::sendOfflineTrainingDialog` (game.cpp:11134)
 *  - `Game::outfitSupportsMount` (game.cpp:10071)
 *  - `Game::playerChangeOutfit` (game.cpp:7228, 7237)
 *  - `Game::applyCharmRune` (game.cpp:8973)
 */
class GameFinalMembersTest {

    private val center = Position(1000, 1000, 7)

    private class Harness(
        val game: Game,
        val items: Items,
        val cancels: MutableList<ReturnValue>,
        val texts: MutableList<String>,
    )

    private fun itemsRegistry(): Items = Items().also {
        it.register(ItemType(id = 100, name = "ground", isGroundTile = true))
        it.register(ItemType(id = 2160, name = "magic sword", isMovable = true, weight = 10.0))
        it.register(ItemType(id = 2170, name = "gold coin", isMovable = true, isStackable = true, stackSize = 100))
        it.register(ItemType(id = 3031, name = "gold coin", isMovable = true, isStackable = true, stackSize = 100))
        it.register(ItemType(id = 3035, name = "platinum coin", isMovable = true, isStackable = true, stackSize = 100))
        it.register(ItemType(id = 3043, name = "crystal coin", isMovable = true, isStackable = true, stackSize = 100))
        it.register(ItemType(id = 2385, name = "backpack", isContainer = true, capacity = 20, isMovable = true))
        it.register(ItemType(id = 19250, name = "reward chest", isContainer = true, capacity = 32))
    }

    private fun newGame(): Harness {
        val items = itemsRegistry()
        val game = Game(GameMap(), GameDispatcher(), itemTypes = items)
        val cancels = mutableListOf<ReturnValue>()
        val texts = mutableListOf<String>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }
        game.sendCancelMessageText = { _, message -> texts.add(message) }
        game.sendTextMessage = { _, _, text -> texts.add(text) }
        return Harness(game, items, cancels, texts)
    }

    private fun Game.tile(position: Position = center) =
        map.getOrCreateTile(position).also {
            if (it.ground == null) {
                it.ground = Item(id = 100, type = ItemType(id = 100, name = "ground", isGroundTile = true))
            }
        }

    private fun player(game: Game, id: Long = 1, name: String = "Hero", capacity: Double = 20.0): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, capacity = capacity, position = center).also {
            game.addPlayer(it)
            game.tile()
            game.internalPlaceCreature(it, center)
        }

    // ----- collectRewardChestItems -----

    @Test
    fun `collectRewardChestItems returns the empty flag`() {
        val harness = newGame()
        val user = player(harness.game)

        assertThat(harness.game.collectRewardChestItems(user)).isEqualTo(ReturnValue.REWARDCHESTISEMPTY)
    }

    @Test
    fun `collectRewardChestItems reports the capacity stop`() {
        val harness = newGame()
        val user = player(harness.game, capacity = 0.0)
        val reward = user.getRewardChest().getReward(1, true)!!
        reward.addItem(item(harness, 2160))

        assertThat(harness.game.collectRewardChestItems(user)).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(harness.cancels).containsExactly(ReturnValue.NOTENOUGHCAPACITY)
        assertThat(harness.texts).containsExactly("0 of 1 objects were picked up.")
    }

    @Test
    fun `collectRewardChestItems moves the reward into the managed container`() {
        val harness = newGame()
        val user = player(harness.game)
        val reward = user.getRewardChest().getReward(1, true)!!
        val sword = item(harness, 2160)
        reward.addItem(sword)
        val managed = Container(id = 2385, capacity = 20, type = harness.items.get(2385))
        user.refreshManagedContainer(org.opentibia.canary.items.ObjectCategory_t.OBJECTCATEGORY_DEFAULT, managed, true)

        assertThat(harness.game.collectRewardChestItems(user)).isEqualTo(ReturnValue.NOERROR)
        assertThat(harness.texts).containsExactly("1 of 1 objects were picked up.")
    }

    @Test
    fun `collectRewardChestItems honours maxMoveItems`() {
        val harness = newGame()
        val user = player(harness.game)
        val reward = user.getRewardChest().getReward(1, true)!!
        reward.addItem(item(harness, 2160))
        reward.addItem(item(harness, 2160))
        val managed = Container(id = 2385, capacity = 20, type = harness.items.get(2385))
        user.refreshManagedContainer(org.opentibia.canary.items.ObjectCategory_t.OBJECTCATEGORY_DEFAULT, managed, true)

        assertThat(harness.game.collectRewardChestItems(user, maxMoveItems = 1)).isEqualTo(ReturnValue.NOERROR)
        assertThat(harness.texts).contains("You can only collect 1 items at a time. 1 of 2 objects were picked up.")
    }

    private fun item(harness: Harness, id: Int): Item = Item(id = id, type = harness.items.get(id))

    // ----- playerRewardChestCollect -----

    @Test
    fun `playerRewardChestCollect ignores an unknown player`() {
        val harness = newGame()

        harness.game.playerRewardChestCollect(404, center, 19250, 0)

        assertThat(harness.cancels).isEmpty()
    }

    @Test
    fun `playerRewardChestCollect rejects a non-chest item`() {
        val harness = newGame()
        val user = player(harness.game)
        val sword = item(harness, 2160)
        harness.game.tile().addThing(sword)

        harness.game.playerRewardChestCollect(user.id, center, 2160, 1)

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerRewardChestCollect cancels an empty chest`() {
        val harness = newGame()
        val user = player(harness.game)
        val chest = Container(id = 19250, capacity = 32, type = harness.items.get(19250))
        harness.game.tile().addThing(chest)

        harness.game.playerRewardChestCollect(user.id, center, 19250, 1)

        assertThat(harness.cancels).containsExactly(ReturnValue.REWARDCHESTISEMPTY)
    }

    @Test
    fun `playerRewardChestCollect collects the reward and reports the empty managed result`() {
        val harness = newGame()
        val user = player(harness.game)
        val chest = Container(id = 19250, capacity = 32, type = harness.items.get(19250))
        harness.game.tile().addThing(chest)
        val reward = user.getRewardChest().getReward(1, true)!!
        reward.addItem(item(harness, 2160))

        harness.game.playerRewardChestCollect(user.id, center, 19250, 1)

        assertThat(harness.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
        assertThat(harness.texts).containsExactly("0 of 1 objects were picked up.")
    }

    // ----- achievements -----

    @Test
    fun `registerAchievement feeds the secret and public list getters`() {
        val harness = newGame()

        harness.game.registerAchievement(1, "Secret", "hidden", secret = true, grade = 2, points = 10)
        harness.game.registerAchievement(2, "Public", "shown", secret = false, grade = 1, points = 5)

        assertThat(harness.game.getAchievements().map { it.id }).containsExactly(1, 2)
        assertThat(harness.game.getSecretAchievements().map { it.id }).containsExactly(1)
        assertThat(harness.game.getPublicAchievements().map { it.id }).containsExactly(2)
        assertThat(harness.game.getAchievementById(1).name).isEqualTo("Secret")
        assertThat(harness.game.getAchievementByName("Public").points).isEqualTo(5)
    }

    @Test
    fun `registerAchievement keeps the first registration per name`() {
        val harness = newGame()

        harness.game.registerAchievement(1, "Same", "first", secret = false, grade = 1, points = 1)
        harness.game.registerAchievement(2, "Same", "second", secret = false, grade = 1, points = 2)

        assertThat(harness.game.getAchievementById(2).description).isEqualTo("second")
        assertThat(harness.game.getAchievementByName("Same").id).isEqualTo(1)
    }

    // ----- loadMotdNum -----

    @Test
    fun `loadMotdNum keeps the stored value when the hash matches`() {
        val harness = newGame()
        harness.game.serverMotd = "Welcome"
        harness.game.loadMotdNumValue = { 4L }
        harness.game.loadMotdHashValue = { org.opentibia.canary.utils.Tools.transformToSHA1("Welcome") }

        harness.game.loadMotdNum()

        assertThat(harness.game.getMotdNum()).isEqualTo(4L)
    }

    @Test
    fun `loadMotdNum increments the counter when the hash differs`() {
        val harness = newGame()
        harness.game.serverMotd = "Welcome"
        harness.game.loadMotdNumValue = { 4L }
        harness.game.loadMotdHashValue = { "stale-hash" }

        harness.game.loadMotdNum()

        assertThat(harness.game.getMotdNum()).isEqualTo(5L)
    }

    @Test
    fun `loadMotdNum ignores missing rows without a database`() {
        val harness = newGame()
        harness.game.loadMotdNumValue = { null }
        harness.game.loadMotdHashValue = { null }

        harness.game.loadMotdNum()

        assertThat(harness.game.getMotdNum()).isEqualTo(0L)
        assertThat(harness.game.getMotdHash()).isEmpty()
    }

    // ----- start -----

    @Test
    fun `start schedules the maintenance and cycle events with the C++ delays`() {
        val harness = newGame()
        val scheduled = mutableListOf<Long>()
        val cycled = mutableListOf<Long>()
        harness.game.scheduleEvent = { delay, _ -> scheduled.add(delay); 1L }
        harness.game.cycleEvent = { delay, _ -> cycled.add(delay); 1L }
        harness.game.getTimeNow = { 1704090600L }

        harness.game.start()

        assertThat(scheduled).containsExactly(11000L, 11000L)
        assertThat(cycled).containsExactly(10000L, 10000L, 1000L, 600000L, 1800000L, 600000L)
    }

    @Test
    fun `start derives the light hour from the current time`() {
        val harness = newGame()
        harness.game.scheduleEvent = { _, _ -> 1L }
        harness.game.cycleEvent = { _, _ -> 1L }
        harness.game.getTimeNow = { 1704090600L }

        harness.game.start()

        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = 1704090600L * 1000
        val expected = (calendar.get(java.util.Calendar.MINUTE) * 1440) / 60
        assertThat(harness.game.getLightHour()).isEqualTo(expected)
    }

    @Test
    fun `start skips the market refresh when the interval is zero`() {
        val harness = newGame()
        val cycled = mutableListOf<Long>()
        harness.game.scheduleEvent = { _, _ -> 1L }
        harness.game.cycleEvent = { delay, _ -> cycled.add(delay); 1L }
        harness.game.marketRefreshPricesIntervalMinutes = 0

        harness.game.start()

        assertThat(cycled).containsExactly(10000L, 10000L, 1000L, 600000L, 600000L)
    }

    // ----- resetMonsters -----

    @Test
    fun `resetMonsters clears the monster target and friend lists`() {
        val items = itemsRegistry()
        val host = MonsterPipelinesHost(
            service = org.opentibia.canary.game.scheduling.MonsterComputeService(),
            mapAccess = org.opentibia.canary.map.SpectatorMapAccess { _, _ ->
                org.opentibia.canary.map.SpectatorSector()
            },
        )
        val game = Game(GameMap(), GameDispatcher(), itemTypes = items, monsterHost = host)
        val monster = Monster(id = 5, name = "Rat", maxHealth = 100, health = 100, position = center)
        game.addMonster(monster)
        host.register(monster)
        val state = host.entryOf(5)!!.targetState
        state.friendList.add(7L)

        game.resetMonsters()

        assertThat(state.targetList).isEmpty()
        assertThat(state.friendList).isEmpty()
    }

    // ----- addMoney -----

    @Test
    fun `addMoney rejects a missing cylinder`() {
        val harness = newGame()

        assertThat(harness.game.addMoney(null, 100)).isEqualTo(0L to ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `addMoney treats zero as a no-op`() {
        val harness = newGame()

        assertThat(harness.game.addMoney(harness.game.tile(), 0)).isEqualTo(0L to ReturnValue.NOERROR)
    }

    @Test
    fun `addMoney breaks the amount into crystal platinum and gold stacks`() {
        val harness = newGame()
        val tile = harness.game.tile()

        val (added, ret) = harness.game.addMoney(tile, 123456)

        assertThat(ret).isEqualTo(ReturnValue.NOERROR)
        assertThat(added).isEqualTo(123456L)
        assertThat(tile.items.map { it.id to it.count }).containsExactly(
            3043 to 12,
            3035 to 34,
            3031 to 56,
        )
    }

    @Test
    fun `addMoney creates a single gold stack for small amounts`() {
        val harness = newGame()
        val tile = harness.game.tile()

        assertThat(harness.game.addMoney(tile, 5)).isEqualTo(5L to ReturnValue.NOERROR)
        assertThat(tile.items.single().id).isEqualTo(3031)
        assertThat(tile.items.single().count).isEqualTo(5)
    }

    // ----- updatePlayersOnline -----

    @Test
    fun `updatePlayersOnline deletes stale rows when nobody is online`() {
        val harness = newGame()
        var deleted = false
        harness.game.countPlayersOnlineValue = { 3 }
        harness.game.deletePlayersOnline = { deleted = true; true }

        harness.game.updatePlayersOnline()

        assertThat(deleted).isTrue()
    }

    @Test
    fun `updatePlayersOnline skips the delete when the table is already empty`() {
        val harness = newGame()
        var deleted = false
        harness.game.countPlayersOnlineValue = { 0 }
        harness.game.deletePlayersOnline = { deleted = true; true }

        harness.game.updatePlayersOnline()

        assertThat(deleted).isFalse()
    }

    @Test
    fun `updatePlayersOnline inserts and prunes the online ids`() {
        val harness = newGame()
        val user = player(harness.game)
        val inserted = mutableListOf<List<Long>>()
        val pruned = mutableListOf<List<Long>>()
        harness.game.insertPlayersOnline = { inserted.add(it); true }
        harness.game.prunePlayersOnline = { pruned.add(it); true }

        harness.game.updatePlayersOnline()

        assertThat(inserted).containsExactly(listOf(user.getGUID()))
        assertThat(pruned).containsExactly(listOf(user.getGUID()))
    }

    // ----- playerCheckActivity -----

    @Test
    fun `playerCheckActivity ignores an unknown player`() {
        val harness = newGame()
        var disconnected = false
        harness.game.disconnectPlayer = { disconnected = true }

        harness.game.playerCheckActivity("Nobody", 1000)

        assertThat(disconnected).isFalse()
    }

    @Test
    fun `playerCheckActivity disconnects a ghosted death-screen player`() {
        val harness = newGame()
        player(harness.game, name = "Ghost")
        var disconnected = false
        harness.game.getPlayerIP = { 0L }
        harness.game.disconnectPlayer = { disconnected = true }

        harness.game.playerCheckActivity("Ghost", 1000)

        assertThat(disconnected).isTrue()
    }

    @Test
    fun `playerCheckActivity ignores a living player`() {
        val harness = newGame()
        player(harness.game, name = "Alive")
        var disconnected = false
        harness.game.getPlayerIP = { 1L }
        harness.game.disconnectPlayer = { disconnected = true }

        harness.game.playerCheckActivity("Alive", 1000)

        assertThat(disconnected).isFalse()
    }

    @Test
    fun `playerCheckActivity disconnects an idle dead player after the kick window`() {
        val harness = newGame()
        val user = Player(id = 1, name = "Dead", maxHealth = 150, health = 0, position = center)
        harness.game.addPlayer(user)
        var disconnected = false
        harness.game.getPlayerIP = { 1L }
        harness.game.disconnectPlayer = { disconnected = true }
        harness.game.kickAfterMinutes = 15

        harness.game.playerCheckActivity("Dead", 60000)
        assertThat(disconnected).isFalse()

        repeat(16) { harness.game.playerCheckActivity("Dead", 60000) }

        assertThat(disconnected).isTrue()
    }

    // ----- createLuaItemsOnMap -----

    @Test
    fun `createLuaItemsOnMap adds the stored items to their tiles`() {
        val harness = newGame()
        val tile = harness.game.tile()

        harness.game.setCreateLuaItems(center, 2160)
        harness.game.createLuaItemsOnMap()

        assertThat(tile.items.map { it.id }).containsExactly(2160)
    }

    @Test
    fun `createLuaItemsOnMap ignores positions without x`() {
        val harness = newGame()

        harness.game.setCreateLuaItems(Position(0, 0, 0), 2160)
        harness.game.createLuaItemsOnMap()

        assertThat(harness.game.map.getTile(Position(0, 0, 0))).isNull()
    }

    @Test
    fun `createLuaItemsOnMap ignores a tile that already holds the item`() {
        val harness = newGame()
        val tile = harness.game.tile()
        tile.addThing(item(harness, 2160))

        harness.game.setCreateLuaItems(center, 2160)
        harness.game.createLuaItemsOnMap()

        assertThat(tile.items).hasSize(1)
    }

    // ----- parsePlayerExtendedOpcode -----

    @Test
    fun `parsePlayerExtendedOpcode forwards every creature event`() {
        val harness = newGame()
        val user = player(harness.game)
        val executed = mutableListOf<Triple<Long, Int, String>>()
        harness.game.executeExtendedOpcode = { p, opcode, buffer -> executed.add(Triple(p.id, opcode, buffer)) }

        harness.game.parsePlayerExtendedOpcode(user.id, 42, "payload")

        assertThat(executed).containsExactly(Triple(1L, 42, "payload"))
    }

    @Test
    fun `parsePlayerExtendedOpcode ignores an unknown player`() {
        val harness = newGame()
        var executed = false
        harness.game.executeExtendedOpcode = { _, _, _ -> executed = true }

        harness.game.parsePlayerExtendedOpcode(404, 42, "payload")

        assertThat(executed).isFalse()
    }

    // ----- playerDebugAssert -----

    @Test
    fun `playerDebugAssert appends the assertion to the log file`(@TempDir tempDir: File) {
        val harness = newGame()
        val user = player(harness.game, name = "Crashy")
        val log = File(tempDir, "client_assertions.txt")
        harness.game.debugAssertionsFile = log.absolutePath
        harness.game.getPlayerIP = { 0x0100007FL }

        harness.game.playerDebugAssert(user.id, "assert(line)", "date", "description", "comment")

        val content = log.readText()
        assertThat(content).contains("Crashy (127.0.0.1)")
        assertThat(content).contains("assert(line)\ndate\ndescription\ncomment")
    }

    @Test
    fun `playerDebugAssert ignores an unknown player`(@TempDir tempDir: File) {
        val harness = newGame()
        val log = File(tempDir, "client_assertions.txt")
        harness.game.debugAssertionsFile = log.absolutePath

        harness.game.playerDebugAssert(404, "assert", "date", "description", "comment")

        assertThat(log.exists()).isFalse()
    }

    // ----- sendOfflineTrainingDialog -----

    @Test
    fun `sendOfflineTrainingDialog opens the hardcoded window once`() {
        val items = itemsRegistry()
        val opened = mutableListOf<org.opentibia.canary.game.modal.ModalWindow>()
        val manager = org.opentibia.canary.game.modal.ModalWindowManager(
            client = { window -> opened.add(window) },
        )
        val game = Game(GameMap(), GameDispatcher(), itemTypes = items, modalWindowManager = manager)
        val user = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = center)
        game.addPlayer(user)

        game.sendOfflineTrainingDialog(user)
        game.sendOfflineTrainingDialog(user)

        assertThat(opened).hasSize(1)
        assertThat(opened.single().id).isEqualTo(0xFFFFFFFFL)
        assertThat(opened.single().title).isEqualTo("Choose a Skill")
        assertThat(opened.single().message).isEqualTo("Please choose a skill:")
        assertThat(opened.single().choices.map { it.second }).containsExactly(0, 2, 3, 1, 4, 13)
        assertThat(opened.single().buttons.map { it.second }).containsExactly(1, 0)
        assertThat(opened.single().priority).isTrue()
    }

    @Test
    fun `sendOfflineTrainingDialog ignores a null player`() {
        val harness = newGame()
        harness.game.sendOfflineTrainingDialog(null)
    }

    // ----- outfitSupportsMount -----

    @Test
    fun `outfitSupportsMount accepts every look type without appearances`() {
        val harness = newGame()

        assertThat(harness.game.outfitSupportsMount(128)).isTrue()
    }

    @Test
    fun `outfitSupportsMount consults the loaded appearances`() {
        val game = Game(
            GameMap(),
            GameDispatcher(),
            appearances = org.opentibia.canary.io.appearances.Appearances.load(ByteArray(0)),
        )

        assertThat(game.outfitSupportsMount(128)).isFalse()
    }

    // ----- playerChangeOutfit -----

    @Test
    fun `playerChangeOutfit obeys the ALLOW_CHANGEOUTFIT gate`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.allowChangeOutfit = false

        harness.game.playerChangeOutfit(user.id, org.opentibia.canary.combat.condition.Outfit(lookType = 128), false)

        assertThat(user.getDefaultOutfit().lookType).isEqualTo(0)
    }

    @Test
    fun `playerChangeOutfit applies a wearable outfit`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.getOutfitByLookType = { _, lookType ->
            if (lookType == 128) {
                org.opentibia.canary.creatures.appearance.outfit.Outfit(name = "Citizen", lookType = 128, premium = false, unlocked = true, from = "")
            } else {
                null
            }
        }
        harness.game.playerCanWear = { _, _, _ -> true }

        harness.game.playerChangeOutfit(user.id, org.opentibia.canary.combat.condition.Outfit(lookType = 128, lookHead = 1), false)

        assertThat(user.getDefaultOutfit().lookType).isEqualTo(128)
        assertThat(user.getDefaultOutfit().lookHead).isEqualTo(1)
    }

    @Test
    fun `playerChangeOutfit keeps the old outfit when the player cannot wear it`() {
        val harness = newGame()
        val user = player(harness.game)
        harness.game.getOutfitByLookType = { _, _ ->
            org.opentibia.canary.creatures.appearance.outfit.Outfit(name = "Citizen", lookType = 128, premium = false, unlocked = true, from = "")
        }
        harness.game.playerCanWear = { _, _, _ -> false }

        harness.game.playerChangeOutfit(user.id, org.opentibia.canary.combat.condition.Outfit(lookType = 128), false)

        assertThat(user.getDefaultOutfit().lookType).isEqualTo(0)
    }

    @Test
    fun `playerChangeOutfit clears the mount for a support outfit`() {
        val harness = newGame()
        val user = player(harness.game)
        user.setCurrentOutfit(org.opentibia.canary.combat.condition.Outfit(lookType = 75))
        harness.game.getOutfitByLookType = { _, _ ->
            org.opentibia.canary.creatures.appearance.outfit.Outfit(name = "Citizen", lookType = 128, premium = false, unlocked = true, from = "")
        }

        val outfit = org.opentibia.canary.combat.condition.Outfit(lookType = 128, lookMount = 7)
        harness.game.playerChangeOutfit(user.id, outfit, setMount = true)

        assertThat(outfit.lookMount).isEqualTo(0)
    }

    @Test
    fun `playerChangeOutfit runs the attached-effect application seam`() {
        val harness = newGame()
        val user = player(harness.game)
        var applied: org.opentibia.canary.combat.condition.Outfit? = null
        harness.game.playerApplyAttachedEffects = { _, outfit -> applied = outfit }

        harness.game.playerChangeOutfit(user.id, org.opentibia.canary.combat.condition.Outfit(lookType = 128), false)

        assertThat(applied).isNotNull
    }

    // ----- applyCharmRune -----

    @Test
    fun `applyCharmRune ignores missing actors`() {
        val harness = newGame()
        var parsed = false
        harness.game.parseCharmCombat = { _, _, _, _ -> parsed = true }

        harness.game.applyCharmRune(null, player(harness.game), null, 10)

        assertThat(parsed).isFalse()
    }

    @Test
    fun `applyCharmRune procs an offensive charm when the roll is within chance`() {
        val harness = newGame()
        val user = player(harness.game)
        val monster = Monster(id = 5, name = "Rat", maxHealth = 100, health = 100, position = center)
        val charm = Charm(
            name = "Wound",
            id = CharmRune.CHARM_WOUND,
            type = CharmType.CHARM_OFFENSIVE,
            chance = listOf(100.0),
        )
        harness.game.getMonsterTypeByName = { _ -> MonsterType("Rat") }
        harness.game.getCharmFromTarget = { _, _ -> PlayerCharmsByMonster(major = CharmRune.CHARM_WOUND) }
        harness.game.getBestiaryCharm = { rune -> if (rune == CharmRune.CHARM_WOUND) charm else null }
        harness.game.getCharmTier = { _, _ -> 0 }
        harness.game.getCharmChanceModifier = { 0 }
        harness.game.normalRandom = { min, _ -> min }
        val parsed = mutableListOf<Charm>()
        harness.game.parseCharmCombat = { actual, _, _, _ -> parsed.add(actual) }

        harness.game.applyCharmRune(monster, user, monster, 42)

        assertThat(parsed).containsExactly(charm)
    }

    @Test
    fun `applyCharmRune skips a charm whose roll is above chance`() {
        val harness = newGame()
        val user = player(harness.game)
        val monster = Monster(id = 5, name = "Rat", maxHealth = 100, health = 100, position = center)
        harness.game.getMonsterTypeByName = { _ -> MonsterType("Rat") }
        harness.game.getCharmFromTarget = { _, _ -> PlayerCharmsByMonster(major = CharmRune.CHARM_WOUND) }
        harness.game.getBestiaryCharm = { rune ->
            if (rune == CharmRune.CHARM_WOUND) {
                Charm(name = "Wound", id = rune, type = CharmType.CHARM_OFFENSIVE, chance = listOf(0.0))
            } else {
                null
            }
        }
        harness.game.getCharmTier = { _, _ -> 0 }
        harness.game.getCharmChanceModifier = { 0 }
        harness.game.normalRandom = { _, max -> max }
        var parsed = false
        harness.game.parseCharmCombat = { _, _, _, _ -> parsed = true }

        harness.game.applyCharmRune(monster, user, monster, 42)

        assertThat(parsed).isFalse()
    }

    @Test
    fun `applyCharmRune skips defensive charms`() {
        val harness = newGame()
        val user = player(harness.game)
        val monster = Monster(id = 5, name = "Rat", maxHealth = 100, health = 100, position = center)
        harness.game.getMonsterTypeByName = { _ -> MonsterType("Rat") }
        harness.game.getCharmFromTarget = { _, _ -> PlayerCharmsByMonster(major = CharmRune.CHARM_DODGE) }
        harness.game.getBestiaryCharm = { rune ->
            Charm(name = "Dodge", id = rune, type = CharmType.CHARM_DEFENSIVE, chance = listOf(100.0))
        }
        harness.game.getCharmTier = { _, _ -> 0 }
        harness.game.getCharmChanceModifier = { 0 }
        harness.game.normalRandom = { min, _ -> min }
        var parsed = false
        harness.game.parseCharmCombat = { _, _, _, _ -> parsed = true }

        harness.game.applyCharmRune(monster, user, monster, 42)

        assertThat(parsed).isFalse()
    }

    @Test
    fun `applyCharmRune ignores the chance modifier for cripple`() {
        val harness = newGame()
        val user = player(harness.game)
        val monster = Monster(id = 5, name = "Rat", maxHealth = 100, health = 100, position = center)
        harness.game.getMonsterTypeByName = { _ -> MonsterType("Rat") }
        harness.game.getCharmFromTarget = { _, _ -> PlayerCharmsByMonster(major = CharmRune.CHARM_CRIPPLE) }
        harness.game.getBestiaryCharm = { rune ->
            Charm(name = "Cripple", id = rune, type = CharmType.CHARM_OFFENSIVE, chance = listOf(0.0))
        }
        harness.game.getCharmTier = { _, _ -> 0 }
        harness.game.getCharmChanceModifier = { 100 }
        harness.game.normalRandom = { _, max -> max }
        var parsed = false
        harness.game.parseCharmCombat = { _, _, _, _ -> parsed = true }

        harness.game.applyCharmRune(monster, user, monster, 42)

        assertThat(parsed).isFalse()
    }

    @Test
    fun `applyCharmRune adds the chance modifier for other charms`() {
        val harness = newGame()
        val user = player(harness.game)
        val monster = Monster(id = 5, name = "Rat", maxHealth = 100, health = 100, position = center)
        harness.game.getMonsterTypeByName = { _ -> MonsterType("Rat") }
        harness.game.getCharmFromTarget = { _, _ -> PlayerCharmsByMonster(major = CharmRune.CHARM_WOUND) }
        harness.game.getBestiaryCharm = { rune ->
            Charm(name = "Wound", id = rune, type = CharmType.CHARM_OFFENSIVE, chance = listOf(0.0))
        }
        harness.game.getCharmTier = { _, _ -> 0 }
        harness.game.getCharmChanceModifier = { 100 }
        harness.game.normalRandom = { _, max -> max }
        var parsed = false
        harness.game.parseCharmCombat = { _, _, _, _ -> parsed = true }

        harness.game.applyCharmRune(monster, user, monster, 42)

        assertThat(parsed).isTrue()
    }
}
