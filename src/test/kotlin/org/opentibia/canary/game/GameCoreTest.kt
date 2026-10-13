package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.zones.Zones
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.network.protocol.SourceEffect
import org.opentibia.canary.spells.SoundEffect
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the `Game` core members added on top of the existing
 * item/player facade: `Game::placeCreature` (game.cpp:1524),
 * `Game::removeCreature` (game.cpp:1551), `Game::addCreatureCheck`
 * (game.cpp:7659), `Game::removeCreatureCheck` (game.cpp:7687),
 * `Game::broadcastMessage` (game.cpp:9526), `Game::getWorldLightInfo`
 * (game.cpp:9479), `Game::gameIsDay` (game.cpp:9483), `Game::checkLight`
 * (game.cpp:9402), `Game::sendSingleSoundEffect` (game.cpp:7833),
 * `Game::sendDoubleSoundEffect` (game.cpp:7853),
 * `Game::playerStopAutoWalk` (game.cpp:4567) and the zone transition seam
 * `Game::beforeCreatureZoneChange`/`afterCreatureZoneChange` (game.cpp:12417).
 */
class GameCoreTest {

    private val center = Position(1000, 1000, 7)

    private fun newGame(): Game = Game(GameMap(), GameDispatcher())

    private fun Game.groundTile(position: Position = center): Tile =
        map.getOrCreateTile(position).also { it.ground = Item(id = 100) }

    private fun player(id: Long, name: String, position: Position = center): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position)

    private fun testCreature(id: Long, position: Position = center): TestCreature =
        TestCreature(id, "creature-$id", position)

    private class TestCreature(id: Long, name: String, position: Position) :
        Creature(id, name, maxHealth = 100, health = 100, position = position) {

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

    // ----- placeCreature / internalPlaceCreature -----

    @Test
    fun `placeCreature notifies every spectator through sendCreatureAppear and onCreatureAppear`() {
        val game = newGame()
        val newcomerPosition = Position(1002, 1000, 7)
        game.groundTile(center)
        game.groundTile(newcomerPosition)
        game.groundTile(Position(1005, 1000, 7))
        val spectator = player(id = 1, name = "Spectator")
        assertThat(game.internalPlaceCreature(spectator, center)).isTrue()
        val observer = testCreature(id = 2, position = Position(1005, 1000, 7))
        assertThat(game.internalPlaceCreature(observer, observer.position)).isTrue()

        val appearSends = mutableListOf<Triple<Creature, Position, Boolean>>()
        spectator.sendCreatureAppear = { creature, pos, isLogin -> appearSends.add(Triple(creature, pos, isLogin)) }

        val newcomer = testCreature(id = 3, position = newcomerPosition)
        assertThat(game.placeCreature(newcomer, newcomerPosition)).isTrue()

        assertThat(observer.appeared).contains(newcomer to true)
        assertThat(appearSends).contains(Triple(newcomer, newcomerPosition, true))
        assertThat(newcomer.placedCount).isEqualTo(1)
    }

    @Test
    fun `placeCreature rejects a missing tile`() {
        val game = newGame()

        assertThat(game.placeCreature(testCreature(id = 1), Position(9999, 9999, 7))).isFalse()
    }

    @Test
    fun `placeCreature registers a creature check when player spectators exist`() {
        val game = newGame()
        val newcomerPosition = Position(1002, 1000, 7)
        game.groundTile(center)
        game.groundTile(newcomerPosition)
        val spectator = player(id = 1, name = "Spectator")
        assertThat(game.internalPlaceCreature(spectator, center)).isTrue()

        val newcomer = testCreature(id = 2, position = newcomerPosition)
        assertThat(game.placeCreature(newcomer, newcomerPosition)).isTrue()

        assertThat(newcomer.creatureCheck).isTrue()
        assertThat(newcomer.inCheckCreaturesVector).isTrue()
    }

    @Test
    fun `internalPlaceCreature with creatureCheck registers the check and the placed hook`() {
        val game = newGame()
        game.groundTile()
        val creature = testCreature(id = 1)

        assertThat(game.internalPlaceCreature(creature, center, creatureCheck = true)).isTrue()

        assertThat(creature.creatureCheck).isTrue()
        assertThat(creature.placedCount).isEqualTo(1)
    }

    // ----- removeCreature -----

    @Test
    fun `removeCreature notifies spectators and drops the summon first`() {
        val game = newGame()
        game.groundTile(center)
        game.groundTile(Position(1001, 1000, 7))
        game.groundTile(Position(1005, 1000, 7))

        val observer = testCreature(id = 1, position = Position(1005, 1000, 7))
        val master = testCreature(id = 2)
        val summon = testCreature(id = 3, position = Position(1001, 1000, 7))
        assertThat(game.internalPlaceCreature(observer, observer.position)).isTrue()
        assertThat(game.internalPlaceCreature(master, center)).isTrue()
        assertThat(game.internalPlaceCreature(summon, summon.position)).isTrue()
        summon.setMaster(master)

        assertThat(game.removeCreature(master)).isTrue()

        assertThat(master.isRemoved()).isTrue()
        assertThat(summon.isRemoved()).isTrue()
        assertThat(observer.removed).anyMatch { it.first === master && it.second }
        assertThat(observer.removed).anyMatch { it.first === summon && it.second }
    }

    // ----- addCreatureCheck / removeCreatureCheck -----

    @Test
    fun `addCreatureCheck is idempotent and removeCreatureCheck clears the check flag`() {
        val game = newGame()
        val creature = testCreature(id = 1)

        game.addCreatureCheck(creature)
        game.addCreatureCheck(creature)

        assertThat(creature.creatureCheck).isTrue()
        assertThat(creature.inCheckCreaturesVector).isTrue()

        game.removeCreatureCheck(creature)

        assertThat(creature.creatureCheck).isFalse()
    }

    @Test
    fun `addCreatureCheck ignores removed creatures`() {
        val game = newGame()
        val creature = testCreature(id = 1)
        creature.setRemoved()

        game.addCreatureCheck(creature)

        assertThat(creature.creatureCheck).isFalse()
    }

    // ----- broadcastMessage / getPlayerByID -----

    @Test
    fun `broadcastMessage delivers the typed text to every online player`() {
        val game = newGame()
        val first = player(id = 1, name = "First")
        val second = player(id = 2, name = "Second")
        val received = mutableListOf<Pair<MessageClass, String>>()
        first.sendTextMessageTyped = { type, text -> received.add(type to text) }
        second.sendTextMessageTyped = { type, text -> received.add(type to text) }
        game.addPlayer(first)
        game.addPlayer(second)

        game.broadcastMessage("Server save in 1 minute", MessageClass.MESSAGE_EVENT_ADVANCE)

        assertThat(received).containsExactly(
            MessageClass.MESSAGE_EVENT_ADVANCE to "Server save in 1 minute",
            MessageClass.MESSAGE_EVENT_ADVANCE to "Server save in 1 minute",
        )
    }

    @Test
    fun `broadcastMessage ignores empty text`() {
        val game = newGame()
        val player = player(id = 1, name = "First")
        var calls = 0
        player.sendTextMessageTyped = { _, _ -> calls++ }
        game.addPlayer(player)

        game.broadcastMessage("", MessageClass.MESSAGE_EVENT_ADVANCE)

        assertThat(calls).isZero()
    }

    @Test
    fun `getPlayerByID returns online players and null for unknown ids`() {
        val game = newGame()
        val player = player(id = 7, name = "Knight")
        game.addPlayer(player)

        assertThat(game.getPlayerByID(7)).isSameAs(player)
        assertThat(game.getPlayerByID(99)).isNull()
    }

    // ----- world lights -----

    @Test
    fun `getWorldLightInfo exposes the daytime level and the fixed color`() {
        val game = newGame()

        assertThat(game.getWorldLightInfo()).isEqualTo(LightInfo(level = 250, color = 0xD7))
        assertThat(game.gameIsDay()).isTrue()
    }

    @Test
    fun `checkLight walks the world clock into night and fans out the light change`() {
        val game = newGame()
        val player = player(id = 1, name = "First")
        val lightSends = mutableListOf<LightInfo>()
        var timeSends = 0
        var periodChanges = 0
        player.sendWorldLight = { lightSends.add(it) }
        player.sendTibiaTime = { timeSends++ }
        game.executePeriodChange = { _, _ -> periodChanges++ }
        game.addPlayer(player)

        repeat(200) { game.checkLight() }

        assertThat(game.getWorldLightInfo().level).isEqualTo(40)
        assertThat(game.gameIsDay()).isFalse()
        assertThat(lightSends).isNotEmpty()
        assertThat(lightSends.last().level).isEqualTo(40)
        assertThat(timeSends).isEqualTo(200)
        assertThat(periodChanges).isPositive()
    }

    // ----- sound effects -----

    @Test
    fun `sendSingleSoundEffect classifies the source per spectator and skips silence`() {
        val game = newGame()
        game.groundTile()
        val listener = player(id = 1, name = "Listener")
        assertThat(game.internalPlaceCreature(listener, center)).isTrue()
        val received = mutableListOf<Triple<Position, SoundEffect, SourceEffect>>()
        listener.sendSingleSoundEffect = { pos, sound, source -> received.add(Triple(pos, sound, source)) }

        game.sendSingleSoundEffect(center, SoundEffect.SILENCE, null)
        assertThat(received).isEmpty()

        game.sendSingleSoundEffect(center, SoundEffect.NO_DAMAGE, null)
        assertThat(received).containsExactly(Triple(center, SoundEffect.NO_DAMAGE, SourceEffect.GLOBAL))

        received.clear()
        game.sendSingleSoundEffect(center, SoundEffect.NO_DAMAGE, listener)
        assertThat(received).containsExactly(Triple(center, SoundEffect.NO_DAMAGE, SourceEffect.OWN))

        received.clear()
        val otherPlayer = player(id = 2, name = "Other")
        game.sendSingleSoundEffect(center, SoundEffect.NO_DAMAGE, otherPlayer)
        assertThat(received).containsExactly(Triple(center, SoundEffect.NO_DAMAGE, SourceEffect.OTHERS))

        received.clear()
        val monster = Monster(id = 3, name = "Rat", maxHealth = 100, health = 100, position = center)
        game.sendSingleSoundEffect(center, SoundEffect.NO_DAMAGE, monster)
        assertThat(received).containsExactly(Triple(center, SoundEffect.NO_DAMAGE, SourceEffect.CREATURES))

        received.clear()
        val npc = Npc(id = 4, name = "Bob", position = center)
        game.sendSingleSoundEffect(center, SoundEffect.NO_DAMAGE, npc)
        assertThat(received).containsExactly(Triple(center, SoundEffect.NO_DAMAGE, SourceEffect.GLOBAL))
    }

    @Test
    fun `sendDoubleSoundEffect falls back to the single sender when the secondary is silence`() {
        val game = newGame()
        game.groundTile()
        val listener = player(id = 1, name = "Listener")
        assertThat(game.internalPlaceCreature(listener, center)).isTrue()
        var singleSends = 0
        var doubleSends = 0
        listener.sendSingleSoundEffect = { _, _, _ -> singleSends++ }
        listener.sendDoubleSoundEffect = { _, _, _, _, _ -> doubleSends++ }

        game.sendDoubleSoundEffect(center, SoundEffect.NO_DAMAGE, SoundEffect.SILENCE, listener)
        assertThat(singleSends).isEqualTo(1)
        assertThat(doubleSends).isZero()

        game.sendDoubleSoundEffect(center, SoundEffect.NO_DAMAGE, SoundEffect.OTHER, listener)
        assertThat(singleSends).isEqualTo(1)
        assertThat(doubleSends).isEqualTo(1)
    }

    // ----- playerStopAutoWalk -----

    @Test
    fun `playerStopAutoWalk latches cancelNextWalk on the online player`() {
        val game = newGame()
        val walker = player(id = 7, name = "Walker")
        game.addPlayer(walker)

        game.playerStopAutoWalk(7)

        assertThat(walker.cancelNextWalk).isTrue()
    }

    @Test
    fun `playerStopAutoWalk ignores unknown player ids`() {
        val game = newGame()

        game.playerStopAutoWalk(99)

        assertThat(game.getPlayerByID(99)).isNull()
    }

    // ----- zone transition seams -----

    @Test
    fun `internalPlaceCreature consults the injected tile zones before and after placement`() {
        val game = newGame()
        game.groundTile()
        val zone = Zones().addZone("test")!!
        var entered = false
        game.tileZones = { setOf(zone) }
        game.checkZoneBeforeCreatureEnter = { candidate, _ -> candidate === zone }
        game.executeZoneAfterCreatureEnter = { candidate, _ -> entered = candidate === zone }

        assertThat(game.internalPlaceCreature(testCreature(id = 1), center)).isTrue()

        assertThat(entered).isTrue()
    }

    @Test
    fun `internalPlaceCreature rejects a placement refused by a zone callback`() {
        val game = newGame()
        game.groundTile()
        val zone = Zones().addZone("test")!!
        game.tileZones = { setOf(zone) }
        game.checkZoneBeforeCreatureEnter = { _, _ -> false }

        assertThat(game.internalPlaceCreature(testCreature(id = 1), center)).isFalse()
    }

    @Test
    fun `beforeCreatureZoneChange rejects a refused transition unless forced`() {
        val game = newGame()
        val zone = Zones().addZone("test")!!
        val creature = testCreature(id = 1)
        game.checkZoneBeforeCreatureLeave = { _, _ -> false }

        assertThat(game.beforeCreatureZoneChange(creature, setOf(zone), emptySet()))
            .isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(game.beforeCreatureZoneChange(creature, setOf(zone), emptySet(), force = true))
            .isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `afterCreatureZoneChange dispatches only the entered and left zones`() {
        val game = newGame()
        val leftZone = Zones().addZone("left")!!
        val enteredZone = Zones().addZone("entered")!!
        val unchangedZone = Zones().addZone("unchanged")!!
        val creature = testCreature(id = 1)
        val left = mutableListOf<Any>()
        val entered = mutableListOf<Any>()
        game.executeZoneAfterCreatureLeave = { zone, _ -> left.add(zone) }
        game.executeZoneAfterCreatureEnter = { zone, _ -> entered.add(zone) }

        game.afterCreatureZoneChange(creature, setOf(leftZone, unchangedZone), setOf(enteredZone, unchangedZone))

        assertThat(left).containsExactly(leftZone)
        assertThat(entered).containsExactly(enteredZone)
    }
}
