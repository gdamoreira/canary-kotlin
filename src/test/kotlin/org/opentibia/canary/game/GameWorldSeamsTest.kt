package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.ForgeClassification
import org.opentibia.canary.creatures.monsters.MonsterPipelinesHost
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.SpectatorSector
import org.opentibia.canary.map.Spectators
import org.opentibia.canary.map.Tile
import org.opentibia.canary.spells.SoundEffect

/**
 * Transcript tests for the wave-47 seam wiring of `Game.kt`:
 *
 *  - `Game::internalPlaceCreature` (game.cpp:1495) delegating placement to
 *    `Map::placeCreature` (map.cpp:559)
 *  - `Game::placeCreature` (game.cpp:1524) fanning the appear frames out once
 *  - `Game::removeCreature` (game.cpp:1551) delegating the tile/spectator block
 *    to the map layer and keeping the creature-state tail
 *  - the `Spectators().find<...>` queries of `Game::sendSingleSoundEffect`
 *    (game.cpp:7833), `Game::internalCreatureTurn` (game.cpp:7599) and
 *    `Game::removeCreature` resolving through the map sectors
 *  - the forge seams resolving through `MonsterPipelinesHost.typeStateOf` and
 *    `MonsterForge` (`Game::makeInfluencedMonster` game.cpp:11882,
 *    `Game::makeFiendishMonster` game.cpp:11928, `Game::getForgeStack`
 *    monster.cpp:3903, `configureForgeSystem` monster.cpp:3866)
 */
class GameWorldSeamsTest {

    private val center = Position(1000, 1000, 7)

    private class TestCreature(id: Long, position: Position) :
        Creature(id, "creature-$id", maxHealth = 100, health = 100, position = position) {

        val appeared = mutableListOf<Pair<Creature, Boolean>>()
        val removed = mutableListOf<Pair<Creature, Boolean>>()
        var placedCount = 0

        override fun onCreatureAppear(creature: Creature, isLogin: Boolean) {
            super.onCreatureAppear(creature, isLogin)
            appeared.add(creature to isLogin)
        }

        override fun onRemoveCreature(creature: Creature, isLogout: Boolean) {
            super.onRemoveCreature(creature, isLogout)
            removed.add(creature to isLogout)
        }

        override fun onPlacedCreature() {
            placedCount++
        }
    }

    private fun newGame(host: MonsterPipelinesHost? = null): Game {
        Spectators.clearCache()
        return Game(GameMap(), GameDispatcher(), monsterHost = host)
    }

    private fun Game.groundTile(position: Position = center): Tile =
        map.getOrCreateTile(position).also { it.ground = Item(id = 100) }

    private fun player(id: Long, name: String, position: Position = center): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position)

    private fun newHost(): MonsterPipelinesHost {
        val service = MonsterComputeService()
        return MonsterPipelinesHost(
            service = service,
            mapAccess = SpectatorMapAccess { _, _ -> SpectatorSector() },
            resolveCreature = { null },
            clock = { 0L },
        )
    }

    // ----- spectatorsOf delegates to the map sector query -----

    @Test
    fun `spectator fanout reaches creatures placed directly through the map`() {
        val game = newGame()
        val tile = game.groundTile()
        val listener = player(id = 1, name = "Listener")
        assertThat(game.map.placeCreature(center, listener, centerTile = tile)).isTrue()
        val received = mutableListOf<Triple<Position, SoundEffect, Any>>()
        listener.sendSingleSoundEffect = { position, sound, source -> received.add(Triple(position, sound, source)) }

        game.sendSingleSoundEffect(center, SoundEffect.NO_DAMAGE, null)

        assertThat(received).hasSize(1)
    }

    // ----- internalPlaceCreature / placeCreature delegation -----

    @Test
    fun `placeCreature fans the appear frames out exactly once`() {
        val game = newGame()
        val newcomerPosition = Position(1003, 1000, 7)
        game.groundTile(center)
        game.groundTile(Position(1001, 1000, 7))
        game.groundTile(newcomerPosition)
        val observer = TestCreature(id = 1, position = center)
        assertThat(game.internalPlaceCreature(observer, center)).isTrue()
        val spectator = player(id = 2, name = "Spectator")
        assertThat(game.internalPlaceCreature(spectator, Position(1001, 1000, 7))).isTrue()
        var appearSends = 0
        spectator.sendCreatureAppear = { _, _, _ -> appearSends++ }

        val newcomer = TestCreature(id = 3, position = newcomerPosition)
        assertThat(game.placeCreature(newcomer, newcomerPosition)).isTrue()

        assertThat(appearSends).isEqualTo(1)
        assertThat(observer.appeared.count { it.first === newcomer }).isEqualTo(1)
        assertThat(newcomer.placedCount).isEqualTo(1)
        assertThat(newcomer.getTile()).isSameAs(game.map.getTile(newcomerPosition))
    }

    @Test
    fun `internalPlaceCreature keeps the placement index consistent with the tile`() {
        val game = newGame()
        game.groundTile()
        val creature = TestCreature(id = 1, position = Position(9999, 9999, 7))

        assertThat(game.internalPlaceCreature(creature, center)).isTrue()

        assertThat(creature.getTile()).isSameAs(game.getTile(center))
        assertThat(game.getCreatureByID(1)).isSameAs(creature)
    }

    // ----- removeCreature delegation -----

    @Test
    fun `removeCreature drops the tile entry through the map and keeps the summon tail`() {
        val game = newGame()
        game.groundTile(center)
        val summonPosition = Position(1001, 1000, 7)
        val observerPosition = Position(1003, 1000, 7)
        game.groundTile(summonPosition)
        game.groundTile(observerPosition)
        val tile = game.getTile(center)!!
        val observer = TestCreature(id = 1, position = observerPosition)
        val master = TestCreature(id = 2, position = center)
        val summon = TestCreature(id = 3, position = summonPosition)
        assertThat(game.internalPlaceCreature(observer, observer.position)).isTrue()
        assertThat(game.internalPlaceCreature(master, center)).isTrue()
        assertThat(game.internalPlaceCreature(summon, summon.position)).isTrue()
        summon.setMaster(master)

        assertThat(game.removeCreature(master)).isTrue()

        assertThat(master.isRemoved()).isTrue()
        assertThat(tile.getCreatures()).doesNotContain(master)
        assertThat(summon.isRemoved()).isTrue()
        assertThat(observer.removed).anyMatch { it.first === master && it.second }
        assertThat(observer.removed).anyMatch { it.first === summon && it.second }
    }

    @Test
    fun `removeCreature of an unplaced creature keeps the C++ no-tile tail`() {
        val game = newGame()
        val ghost = TestCreature(id = 1, position = center)

        assertThat(game.removeCreature(ghost)).isTrue()

        assertThat(ghost.isRemoved()).isTrue()
    }

    // ----- forge seams resolve through the host type state -----

    @Test
    fun `forge state accessors read and write the host MonsterTypeState`() {
        val host = newHost()
        val game = newGame(host)
        val monster = Monster(id = 7, name = "Rat", maxHealth = 100, health = 100, position = center)
        host.register(monster)
        val state = host.typeStateOf(7)!!

        game.setMonsterForgeClassification(monster, ForgeClassification.Fiendish)
        game.setTimeToChangeFiendish(monster, 1234L)
        state.forgeStack = 5

        assertThat(host.typeStateOf(7)!!.monsterForgeClassification).isEqualTo(ForgeClassification.Fiendish)
        assertThat(host.typeStateOf(7)!!.timeToChangeFiendish).isEqualTo(1234L)
        assertThat(game.getForgeStack(monster)).isEqualTo(5)
    }

    @Test
    fun `canBeForgeMonster consults the host type state and the MonsterForge rules`() {
        val host = newHost()
        val game = newGame(host)
        val monster = Monster(id = 7, name = "Rat", maxHealth = 100, health = 100, position = center)
        val type = MonsterType("Rat").apply {
            info.isForgeCreature = true
            info.raceid = 21
            info.lootItems.add(org.opentibia.canary.creatures.monsters.LootBlock(id = 100))
            info.health = 100
            info.healthMax = 100
        }
        host.register(monster, type)

        assertThat(game.canBeForgeMonster(monster)).isTrue()

        host.typeStateOf(7)!!.forgeStack = 3
        assertThat(game.canBeForgeMonster(monster)).isFalse()
    }

    @Test
    fun `configureForgeSystem drives MonsterForge through the host state`() {
        val host = newHost()
        val game = newGame(host)
        val monster = Monster(id = 7, name = "Rat", maxHealth = 100, health = 100, position = center)
        val type = MonsterType("Rat").apply {
            info.isForgeCreature = true
            info.raceid = 21
            info.lootItems.add(org.opentibia.canary.creatures.monsters.LootBlock(id = 100))
            info.health = 100
            info.healthMax = 100
        }
        host.register(monster, type)
        val state = host.typeStateOf(7)!!
        state.monsterForgeClassification = ForgeClassification.Influenced

        game.configureForgeSystem(monster, 4)

        assertThat(state.forgeStack).isEqualTo(4)
        assertThat(monster.maxHealth).isEqualTo(195)
        assertThat(monster.getIcons().any { it.count == 4 }).isTrue()
    }

    @Test
    fun `clearFiendishStatus restores the type health through the host state`() {
        val host = newHost()
        val game = newGame(host)
        val monster = Monster(id = 7, name = "Rat", maxHealth = 141, health = 141, position = center)
        val type = MonsterType("Rat").apply {
            info.health = 100
            info.healthMax = 100
        }
        host.register(monster, type)
        val state = host.typeStateOf(7)!!
        state.forgeStack = 15
        state.monsterForgeClassification = ForgeClassification.Fiendish
        state.timeToChangeFiendish = 99L

        game.clearFiendishStatus(monster)

        assertThat(state.forgeStack).isZero()
        assertThat(state.monsterForgeClassification).isEqualTo(ForgeClassification.Normal)
        assertThat(state.timeToChangeFiendish).isZero()
        assertThat(monster.health).isEqualTo(100)
        assertThat(monster.maxHealth).isEqualTo(100)
    }
}
