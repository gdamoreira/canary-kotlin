package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Spectators
import org.opentibia.canary.network.protocol.CreatureType
import org.opentibia.canary.spells.WorldType

/**
 * Transcript tests for the wave-47 world-state updaters and records helpers:
 *
 *  - `Game::changeLight` (game.cpp:7808), `Game::updateCreatureIcon`
 *    (game.cpp:7815), `Game::reloadCreature` (game.cpp:7822),
 *    `Game::sendUpdateCreature` (game.cpp:11872), `Game::removeMagicEffect`
 *    (game.cpp:9365), `Game::addDistanceEffect` (game.cpp:9378),
 *    `Game::hasEffect` (game.cpp:11825), `Game::hasDistanceEffect`
 *    (game.cpp:11835), `Game::updateCreatureWalkthrough` (game.cpp:9535),
 *    `Game::updateCreatureSkull` (game.cpp:9543), `Game::updatePlayerShield`
 *    (game.cpp:9553), `Game::updateCreatureType` (game.cpp:9559),
 *    `Game::updatePlayerHelpers` (game.cpp:9690),
 *    `Game::sendAttachedEffect`/`sendDetachEffect`/`updateCreatureShader`
 *    (game.cpp:12674-12702), `Game::refreshItem` (game.cpp:12716),
 *    `Game::playerSetTyping` (game.cpp:12704), `Game::kickPlayer`
 *    (game.cpp:9805)
 *  - records/online: `Game::checkPlayersRecord` (game.cpp:9625),
 *    `Game::updatePlayersRecord` (game.cpp:9638), `Game::loadPlayersRecord`
 *    (game.cpp:9646), `Game::groupPlayersByIP` (game.cpp:13113),
 *    `Game::getPlayerStats` (game.cpp:13129)
 *  - helpers: `Game::forceRemoveCondition` (game.cpp:11125),
 *    `Game::addBestiaryList` (game.cpp:9517), `Game::getSummaryKeyByType`
 *    (game.cpp:12596), `Game::getHirelingSkills`/`getHirelingOutfits`
 *    (game.cpp:12600-12604), `Game::getPlayersOnline`/`getMonstersOnline`
 *    (game.hpp:195-200)
 */
class GameStateUpdatersAndRecordsTest {

    private val center = Position(1000, 1000, 7)

    private fun newGame(): Game {
        Spectators.clearCache()
        return Game(GameMap(), GameDispatcher())
    }

    private fun Game.groundTile(position: Position = center) {
        map.getOrCreateTile(position).ground = Item(id = 100)
    }

    private fun player(id: Long, name: String, position: Position = center): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position)

    private fun item(id: Int): Item = Item(id = id, type = ItemType(id = id, name = "item-$id", isMovable = true))

    // ----- spectator-driven updaters -----

    @Test
    fun `changeLight updateCreatureIcon sendUpdateCreature and the assists reach the spectators`() {
        val game = newGame()
        game.groundTile()
        val spectator = player(id = 1, name = "Spectator")
        assertThat(game.internalPlaceCreature(spectator, center)).isTrue()
        val creature = Monster(id = 2, name = "Rat", maxHealth = 100, health = 100, position = center)
        game.addMonster(creature)

        val lights = mutableListOf<Long>()
        val icons = mutableListOf<Long>()
        val updates = mutableListOf<Long>()
        val attached = mutableListOf<Pair<Long, Int>>()
        val detached = mutableListOf<Pair<Long, Int>>()
        val shaders = mutableListOf<Pair<Long, String>>()
        game.sendCreatureLight = { _, target -> lights.add(target.id) }
        game.sendCreatureIcon = { _, target -> icons.add(target.id) }
        game.sendUpdateCreatureClient = { _, target -> updates.add(target.id) }
        game.sendAttachedEffectTo = { _, target, effect -> attached.add(target.id to effect) }
        game.sendDetachEffectTo = { _, target, effect -> detached.add(target.id to effect) }
        game.sendCreatureShader = { _, target, shader -> shaders.add(target.id to shader) }
        game.getCreatureShader = { "shader-x" }

        game.changeLight(creature)
        game.updateCreatureIcon(creature)
        game.sendUpdateCreature(creature)
        game.sendAttachedEffect(creature, 5)
        game.sendDetachEffect(creature, 6)
        game.updateCreatureShader(creature)

        assertThat(lights).containsExactly(2L)
        assertThat(icons).containsExactly(2L)
        assertThat(updates).containsExactly(2L)
        assertThat(attached).containsExactly(2L to 5)
        assertThat(detached).containsExactly(2L to 6)
        assertThat(shaders).containsExactly(2L to "shader-x")
    }

    @Test
    fun `reloadCreature rejects a null creature and reaches the same-floor players`() {
        val game = newGame()
        game.groundTile()
        val spectator = player(id = 1, name = "Spectator")
        assertThat(game.internalPlaceCreature(spectator, center)).isTrue()
        val reloads = mutableListOf<Long>()
        game.reloadCreatureClient = { _, target -> reloads.add(target.id) }
        val creature = Monster(id = 2, name = "Rat", maxHealth = 100, health = 100, position = center)

        game.reloadCreature(null)
        game.reloadCreature(creature)

        assertThat(reloads).containsExactly(2L)
    }

    @Test
    fun `removeMagicEffect and addDistanceEffect fan out with the source classification`() {
        val game = newGame()
        game.groundTile()
        val spectator = player(id = 1, name = "Spectator")
        assertThat(game.internalPlaceCreature(spectator, center)).isTrue()
        val removals = mutableListOf<Position>()
        val shots = mutableListOf<Triple<Position, Position, Int>>()
        game.removeMagicEffectFromClient = { _, position, _ -> removals.add(position) }
        game.sendDistanceShoot = { _, from, to, effect, _ -> shots.add(Triple(from, to, effect)) }

        game.removeMagicEffect(center, 10)
        game.addDistanceEffect(center, Position(1005, 1000, 7), 11, spectator)

        assertThat(removals).containsExactly(center)
        assertThat(shots).containsExactly(Triple(center, Position(1005, 1000, 7), 11))
    }

    @Test
    fun `hasEffect and hasDistanceEffect are bounded by the Canary enum tails`() {
        val game = newGame()

        assertThat(game.hasEffect(0)).isTrue()
        assertThat(game.hasEffect(303)).isTrue()
        assertThat(game.hasEffect(304)).isFalse()

        assertThat(game.hasDistanceEffect(0)).isTrue()
        assertThat(game.hasDistanceEffect(62)).isTrue()
        assertThat(game.hasDistanceEffect(63)).isFalse()
    }

    @Test
    fun `updateCreatureSkull runs on PvP worlds only`() {
        val game = newGame()
        game.groundTile()
        val spectator = player(id = 1, name = "Spectator")
        assertThat(game.internalPlaceCreature(spectator, center)).isTrue()
        val skulls = mutableListOf<Long>()
        game.sendCreatureSkull = { _, target -> skulls.add(target.id) }
        val creature = Monster(id = 2, name = "Rat", maxHealth = 100, health = 100, position = center)

        game.setWorldType(WorldType.NO_PVP)
        game.updateCreatureSkull(creature)
        assertThat(skulls).isEmpty()

        game.setWorldType(WorldType.PVP)
        game.updateCreatureSkull(creature)
        assertThat(skulls).containsExactly(2L)
    }

    @Test
    fun `updateCreatureType classifies hidden and player-summoned creatures`() {
        val game = newGame()
        game.groundTile()
        val spectator = player(id = 1, name = "Spectator")
        assertThat(game.internalPlaceCreature(spectator, center)).isTrue()
        val types = mutableListOf<Pair<Long, CreatureType>>()
        game.sendCreatureType = { _, target, type -> types.add(target.id to type) }
        val monster = Monster(id = 2, name = "Rat", maxHealth = 100, health = 100, position = center)

        game.isHealthHidden = { it === monster }
        game.updateCreatureType(monster)
        assertThat(types).containsExactly(2L to CreatureType.HIDDEN)

        types.clear()
        game.isHealthHidden = { false }
        monster.setMaster(spectator)
        game.updateCreatureType(monster)
        assertThat(types).containsExactly(2L to CreatureType.SUMMON_PLAYER)

        types.clear()
        game.updateCreatureType(null)
        assertThat(types).isEmpty()
    }

    @Test
    fun `updatePlayerShield and updatePlayerHelpers reach the spectators`() {
        val game = newGame()
        game.groundTile()
        val spectator = player(id = 1, name = "Spectator", position = Position(1001, 1000, 7))
        game.groundTile(Position(1001, 1000, 7))
        assertThat(game.internalPlaceCreature(spectator, spectator.position)).isTrue()
        val target = player(id = 2, name = "Target")
        assertThat(game.internalPlaceCreature(target, center)).isTrue()
        val shields = mutableListOf<Long>()
        val helpers = mutableListOf<Pair<Long, Int>>()
        game.sendCreatureShield = { _, player -> shields.add(player.id) }
        game.sendCreatureHelpers = { _, id, count -> helpers.add(id to count) }
        game.getPlayerHelpers = { 42 }

        game.updatePlayerShield(target)
        game.updatePlayerHelpers(target)

        assertThat(shields).hasSize(2).containsOnly(2L)
        assertThat(helpers).hasSize(2).containsOnly(2L to 42)
    }

    @Test
    fun `refreshItem sends the tile container and inventory variants`() {
        val game = newGame()
        game.groundTile()
        val spectator = player(id = 1, name = "Spectator")
        assertThat(game.internalPlaceCreature(spectator, center)).isTrue()

        val tileSends = mutableListOf<Int>()
        val containerSends = mutableListOf<Int>()
        val inventorySends = mutableListOf<Int>()
        game.sendUpdateTileItem = { _, _, _, updated -> tileSends.add(updated.id) }
        game.sendUpdateContainerItem = { _, _, _, updated -> containerSends.add(updated.id) }
        spectator.sendInventoryItem = { _, updated -> updated?.let { inventorySends.add(it.id) } }

        val tileItem = item(2000)
        game.getTile(center)!!.internalAddThing(tileItem)
        game.refreshItem(tileItem)
        assertThat(tileSends).containsExactly(2000)

        val bag = Container(id = 3000, capacity = 20, type = ItemType(id = 3000, name = "bag", isContainer = true))
        game.getTile(center)!!.internalAddThing(bag)
        val contained = item(2001)
        bag.addItem(contained)
        game.refreshItem(contained)
        assertThat(containerSends).containsExactly(2001)

        val carried = item(2002)
        spectator.addItem(carried, org.opentibia.canary.creatures.Slots_t.CONST_SLOT_BACKPACK)
        game.refreshItem(carried)
        assertThat(inventorySends).containsExactly(2002)

        game.refreshItem(null)
    }

    @Test
    fun `playerSetTyping broadcasts to the spectators`() {
        val game = newGame()
        game.groundTile()
        val spectator = player(id = 1, name = "Spectator")
        assertThat(game.internalPlaceCreature(spectator, center)).isTrue()
        game.addPlayer(spectator)
        val typing = mutableListOf<Pair<Long, Int>>()
        game.sendPlayerTyping = { _, sender, value -> typing.add(sender.id to value) }

        game.playerSetTyping(1, 3)

        assertThat(typing).containsExactly(1L to 3)
    }

    @Test
    fun `kickPlayer disconnects through the Player removePlayer seam`() {
        val game = newGame()
        val target = player(id = 7, name = "Target")
        game.addPlayer(target)
        val kicks = mutableListOf<Pair<Long, Boolean>>()
        game.playerRemovePlayer = { player, effect -> kicks.add(player.id to effect) }

        game.kickPlayer(7, true)
        game.kickPlayer(99, false)

        assertThat(kicks).containsExactly(7L to true)
    }

    // ----- records / online -----

    @Test
    fun `checkPlayersRecord raises the peak dispatches the event and persists`() {
        val game = newGame()
        game.addPlayer(player(id = 1, name = "First"))
        game.playersRecord = 0
        val events = mutableListOf<Pair<Int, Int>>()
        val persisted = mutableListOf<Int>()
        game.executeRecord = { record, previous -> events.add(record to previous) }
        game.savePlayersRecord = { persisted.add(it) }

        game.checkPlayersRecord()

        assertThat(game.playersRecord).isEqualTo(1)
        assertThat(events).containsExactly(1 to 0)
        assertThat(persisted).containsExactly(1)

        game.addPlayer(player(id = 2, name = "Second"))
        game.checkPlayersRecord()
        assertThat(events).containsExactly(1 to 0, 2 to 1)

        game.removePlayer(game.getPlayerByID(2)!!)
        game.checkPlayersRecord()
        assertThat(game.playersRecord).isEqualTo(2)
        assertThat(persisted).containsExactly(1, 2)
    }

    @Test
    fun `loadPlayersRecord reads the stored value or creates the row`() {
        val game = newGame()
        var created = 0
        game.loadPlayersRecordValue = { null }
        game.createPlayersRecord = { created++ }

        game.loadPlayersRecord()
        assertThat(created).isEqualTo(1)
        assertThat(game.playersRecord).isZero()

        game.loadPlayersRecordValue = { 99 }
        game.loadPlayersRecord()
        assertThat(game.playersRecord).isEqualTo(99)
    }

    @Test
    fun `groupPlayersByIP excludes address-less and long idle players`() {
        val game = newGame()
        val first = player(id = 1, name = "First")
        val second = player(id = 2, name = "Second")
        val idle = player(id = 3, name = "Idle")
        val noIp = player(id = 4, name = "NoIp")
        listOf(first, second, idle, noIp).forEach(game::addPlayer)
        val ips = mapOf(1L to 10L, 2L to 10L, 3L to 20L, 4L to 0L)
        val idles = mapOf(1L to 0L, 2L to 0L, 3L to 15L * 60L * 1000L + 1L, 4L to 0L)
        game.getPlayerIP = { ips[it.id] ?: 0L }
        game.getPlayerIdleTime = { idles[it.id] ?: 0L }

        val grouped = game.groupPlayersByIP()

        assertThat(grouped.keys).containsExactly(10L)
        assertThat(grouped[10L]).containsExactly(first, second)
    }

    @Test
    fun `getPlayerStats caps the per-IP online count at four`() {
        val game = newGame()
        repeat(6) { index ->
            game.addPlayer(player(id = (index + 1).toLong(), name = "P$index"))
        }
        game.getPlayerIP = { 77L }

        val stats = game.getPlayerStats()

        assertThat(stats.totalUniqueIPs).isEqualTo(1)
        assertThat(stats.filteredOnlinePlayers).isEqualTo(4)
    }

    @Test
    fun `getPlayersOnline and getMonstersOnline expose the registry sizes`() {
        val game = newGame()
        game.addPlayer(player(id = 1, name = "First"))
        game.addMonster(Monster(id = 2, name = "Rat", maxHealth = 100, health = 100, position = center))

        assertThat(game.getPlayersOnline()).isEqualTo(1)
        assertThat(game.getMonstersOnline()).isEqualTo(1)
    }

    // ----- helpers -----

    @Test
    fun `forceRemoveCondition removes the condition from an online creature`() {
        val game = newGame()
        val target = player(id = 5, name = "Target")
        game.addPlayer(target)
        target.addCondition(
            org.opentibia.canary.combat.condition.Condition.createCondition(
                id = ConditionId.CONDITIONID_DEFAULT,
                type = ConditionType.CONDITION_INFIGHT,
                ticks = 10_000,
            ),
        )
        assertThat(target.hasCondition(ConditionType.CONDITION_INFIGHT)).isTrue()

        game.forceRemoveCondition(5, ConditionType.CONDITION_INFIGHT, ConditionId.CONDITIONID_DEFAULT)
        game.forceRemoveCondition(99, ConditionType.CONDITION_INFIGHT, ConditionId.CONDITIONID_DEFAULT)

        assertThat(target.hasCondition(ConditionType.CONDITION_INFIGHT)).isFalse()
    }

    @Test
    fun `addBestiaryList keeps the first registration and exposes the helper tables`() {
        val game = newGame()

        game.addBestiaryList(21, "Rat")
        game.addBestiaryList(21, "Other")
        game.addBestiaryList(22, "Wolf")

        assertThat(game.getBestiaryList()).containsEntry(21, "Rat").containsEntry(22, "Wolf")
        assertThat(game.getSummaryKeyByType(Summary_t.BLESSINGS.value)).isEqualTo("blessings")
        assertThat(game.getSummaryKeyByType(255)).isEmpty()
        assertThat(game.getHirelingSkills()).containsEntry(1001, "banker")
        assertThat(game.getHirelingOutfits()).containsEntry(2009, "dragon")
    }
}
