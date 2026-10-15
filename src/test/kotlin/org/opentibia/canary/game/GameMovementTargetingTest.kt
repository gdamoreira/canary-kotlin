package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionGeneric
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.FightMode
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.Thing
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the movement/combat/targeting `Game` handlers:
 *
 *  - `Game::canThrowObjectTo` (game.cpp:7591) / `Game::isSightClear` (game.cpp:7595)
 *  - `Game::internalCreatureTurn` (game.cpp:7599)
 *  - `Game::playerTurn` (game.cpp:7187)
 *  - `Game::internalMoveCreature(creature, direction)` (game.cpp:1909)
 *  - `Game::internalMoveCreature(creature, toTile)` (game.cpp:1963)
 *  - `Game::playerMove` (game.cpp:4282) / `Game::forcePlayerMove` (game.cpp:4296)
 *  - `Game::playerTeleport` (game.cpp:1622) / `Game::internalTeleport` (game.cpp:3540)
 *  - `Game::playerSetAttackedCreature` (game.cpp:7001) /
 *    `Game::playerCancelAttackAndFollow` (game.cpp:6990) /
 *    `Game::playerFollowCreature` (game.cpp:7033) /
 *    `Game::playerSetFightModes` (game.cpp:7044)
 *  - `Game::playerLookAt` (game.cpp:6358) / `Game::playerLookInBattleList` (game.cpp:6393)
 *  - `Game::changeSpeed` (game.cpp:7747) / `Game::setCreatureSpeed` (game.cpp:7759) /
 *    `Game::changePlayerSpeed` (game.cpp:7768)
 */
class GameMovementTargetingTest {

    private val center = Position(1000, 1000, 7)

    private fun newGame(): Game = Game(GameMap(), GameDispatcher())

    private fun Game.groundTile(position: Position = center): Tile =
        map.getOrCreateTile(position).also { it.ground = Item(id = 100) }

    private fun player(id: Long, name: String, position: Position = center): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position).apply {
            // Targeting, follow and look run through `Player::canSee`
            // (player.cpp:1402), which is inert without an attached client;
            // these tests model a connected player.
            hasClient = { true }
        }

    private fun testCreature(id: Long, position: Position = center): TestCreature =
        TestCreature(id, "creature-$id", position)

    /** Places the creature through the facade and the tile creature list, as `Map::placeCreature` would. */
    private fun Game.placeCreatureOnTile(creature: Creature, position: Position = center): Tile {
        groundTile(position)
        assertThat(internalPlaceCreature(creature, position)).isTrue()
        val tile = map.getTile(position)!!
        tile.addThing(creature)
        if (creature is Player) {
            addPlayer(creature)
        }
        return tile
    }

    private class TestCreature(id: Long, name: String, position: Position) :
        Creature(id, name, maxHealth = 100, health = 100, position = position)

    // ----- canThrowObjectTo / isSightClear -----

    @Test
    fun `canThrowObjectTo delegates to the map sight-line rules`() {
        val game = newGame()

        assertThat(game.canThrowObjectTo(center, center)).isTrue()
        assertThat(game.canThrowObjectTo(Position(1000, 1000, 7), Position(1000, 1000, 8))).isFalse()
    }

    @Test
    fun `isSightClear delegates the floor check to the map`() {
        val game = newGame()
        game.groundTile(center)
        game.groundTile(Position(1001, 1000, 7))

        assertThat(game.isSightClear(center, Position(1001, 1000, 7), false)).isTrue()
    }

    // ----- internalCreatureTurn -----

    @Test
    fun `internalCreatureTurn returns false when the direction is unchanged`() {
        val game = newGame()
        val creature = testCreature(id = 1)
        game.placeCreatureOnTile(creature)
        creature.direction = Direction.North

        assertThat(game.internalCreatureTurn(creature, Direction.North)).isFalse()
    }

    @Test
    fun `internalCreatureTurn changes direction and notifies player spectators`() {
        val game = newGame()
        val spectator = player(id = 1, name = "Spectator", position = Position(1002, 1000, 7))
        game.placeCreatureOnTile(spectator)
        val creature = testCreature(id = 2, position = Position(1001, 1000, 7))
        game.placeCreatureOnTile(creature, creature.position)
        val turns = mutableListOf<Pair<Player, Creature>>()
        game.sendCreatureTurn = { observer, target -> turns.add(observer to target) }

        assertThat(game.internalCreatureTurn(creature, Direction.East)).isTrue()

        assertThat(creature.direction).isEqualTo(Direction.East)
        assertThat(turns).containsExactly(spectator to creature)
    }

    @Test
    fun `internalCreatureTurn keeps a locked direction but still notifies`() {
        val game = newGame()
        val spectator = player(id = 1, name = "Spectator", position = Position(1002, 1000, 7))
        game.placeCreatureOnTile(spectator)
        val creature = testCreature(id = 2, position = Position(1001, 1000, 7))
        game.placeCreatureOnTile(creature, creature.position)
        creature.direction = Direction.North
        game.isDirectionLocked = { it === creature }
        val turns = mutableListOf<Creature>()
        game.sendCreatureTurn = { _, target -> turns.add(target) }

        assertThat(game.internalCreatureTurn(creature, Direction.East)).isTrue()

        assertThat(creature.direction).isEqualTo(Direction.North)
        assertThat(turns).containsExactly(creature)
    }

    // ----- playerTurn -----

    @Test
    fun `playerTurn stops on an event veto`() {
        val game = newGame()
        val player = player(id = 1, name = "Turner")
        game.placeCreatureOnTile(player)
        game.eventPlayerOnTurn = { _, _ -> false }

        game.playerTurn(1, Direction.East)

        assertThat(player.direction).isEqualTo(Direction.South)
    }

    @Test
    fun `playerTurn stops on a callback veto`() {
        val game = newGame()
        val player = player(id = 1, name = "Turner")
        game.placeCreatureOnTile(player)
        game.callbackPlayerOnTurn = { _, _ -> false }

        game.playerTurn(1, Direction.East)

        assertThat(player.direction).isEqualTo(Direction.South)
    }

    @Test
    fun `playerTurn resets the login protection and idle time before turning`() {
        val game = newGame()
        val player = player(id = 1, name = "Turner")
        game.placeCreatureOnTile(player)
        val resets = mutableListOf<String>()
        game.resetLoginProtection = { resets.add("login") }
        game.resetIdleTime = { resets.add("idle") }

        game.playerTurn(1, Direction.West)

        assertThat(player.direction).isEqualTo(Direction.West)
        assertThat(resets).containsExactly("login", "idle")
    }

    // ----- internalMoveCreature(direction) -----

    @Test
    fun `internalMoveCreature rejects a creature without base speed`() {
        val game = newGame()
        val creature = testCreature(id = 1)
        game.placeCreatureOnTile(creature)
        game.groundTile(Position(1001, 1000, 7))
        creature.setBaseSpeed(0)

        assertThat(game.internalMoveCreature(creature, Direction.East)).isEqualTo(ReturnValue.NOTMOVABLE)
    }

    @Test
    fun `internalMoveCreature rejects a missing destination tile`() {
        val game = newGame()
        val creature = testCreature(id = 1)
        game.placeCreatureOnTile(creature)

        assertThat(game.internalMoveCreature(creature, Direction.East)).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `internalMoveCreature moves the creature and records the last position`() {
        val game = newGame()
        val creature = testCreature(id = 1)
        game.placeCreatureOnTile(creature)
        game.groundTile(Position(1001, 1000, 7))

        assertThat(game.internalMoveCreature(creature, Direction.East)).isEqualTo(ReturnValue.NOERROR)

        assertThat(creature.position).isEqualTo(Position(1001, 1000, 7))
        assertThat(creature.getLastPosition()).isEqualTo(center)
        assertThat(game.getCreatureByID(1)).isSameAs(creature)
    }

    // ----- internalMoveCreature(toTile) -----

    @Test
    fun `internalMoveCreature to a tile rejects a rooted creature`() {
        val game = newGame()
        val creature = testCreature(id = 1)
        game.placeCreatureOnTile(creature)
        val toTile = game.groundTile(Position(1001, 1000, 7))
        creature.addCondition(
            ConditionGeneric(ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_ROOTED, ticks = 10),
        )

        assertThat(game.internalMoveCreature(creature, toTile)).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `internalMoveCreature to a tile propagates the tile queryAdd result`() {
        val game = newGame()
        val mover = testCreature(id = 1)
        game.placeCreatureOnTile(mover)
        val blocker = testCreature(id = 2, position = Position(1001, 1000, 7))
        val toTile = game.placeCreatureOnTile(blocker, blocker.position)

        assertThat(game.internalMoveCreature(mover, toTile)).isEqualTo(ReturnValue.NOTENOUGHROOM)
        assertThat(mover.position).isEqualTo(center)
    }

    @Test
    fun `internalMoveCreature to a tile moves through the map and keeps the facade index fresh`() {
        val game = newGame()
        val creature = testCreature(id = 1)
        game.placeCreatureOnTile(creature)
        val toTile = game.groundTile(Position(1001, 1000, 7))

        assertThat(game.internalMoveCreature(creature, toTile)).isEqualTo(ReturnValue.NOERROR)

        assertThat(creature.getTile()).isSameAs(toTile)
        assertThat(game.getCreatureByID(1)).isSameAs(creature)
    }

    // ----- playerMove / forcePlayerMove -----

    @Test
    fun `playerMove resets the player and starts a non-forced immediate walk`() {
        val game = newGame()
        val player = player(id = 1, name = "Walker")
        game.placeCreatureOnTile(player)
        val resets = mutableListOf<String>()
        game.resetLoginProtection = { resets.add("login") }
        game.resetIdleTime = { resets.add("idle") }
        game.setNextWalkActionTask = { _, task -> resets.add("nextWalkAction:$task") }
        game.cancelPush = { resets.add("cancelPush") }
        var walkForce: Boolean? = null
        var walkDirs: List<Direction>? = null
        game.startAutoWalk = { _, dirs, force ->
            walkDirs = dirs
            walkForce = force
        }

        game.playerMove(1, Direction.North)

        assertThat(resets).containsExactly("login", "idle", "nextWalkAction:null", "cancelPush")
        assertThat(walkDirs).containsExactly(Direction.North)
        assertThat(walkForce).isFalse()
    }

    @Test
    fun `forcePlayerMove starts a forced immediate walk`() {
        val game = newGame()
        val player = player(id = 1, name = "Walker")
        game.placeCreatureOnTile(player)
        var walkForce: Boolean? = null
        game.startAutoWalk = { _, _, force -> walkForce = force }

        game.forcePlayerMove(1, Direction.South)

        assertThat(walkForce).isTrue()
    }

    @Test
    fun `playerMove ignores an unknown player`() {
        val game = newGame()
        var invoked = false
        game.startAutoWalk = { _, _, _ -> invoked = true }

        game.playerMove(404, Direction.North)

        assertThat(invoked).isFalse()
    }

    // ----- playerTeleport / internalTeleport -----

    @Test
    fun `playerTeleport ignores a player without the map-click flag`() {
        val game = newGame()
        val player = player(id = 1, name = "Teleporter")
        game.placeCreatureOnTile(player)
        var cancels = 0
        game.sendCancelMessage = { _, _ -> cancels++ }

        game.playerTeleport(1, Position(1005, 1005, 7))

        assertThat(cancels).isZero()
        assertThat(player.position).isEqualTo(center)
    }

    @Test
    fun `playerTeleport sends the cancel message when the teleport fails`() {
        val game = newGame()
        val player = player(id = 1, name = "Teleporter")
        game.placeCreatureOnTile(player)
        player.setFlag(PlayerFlags_t.CanMapClickTeleport)
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerTeleport(1, Position(1005, 1005, 7))

        assertThat(cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerTeleport moves the player without a cancel message`() {
        val game = newGame()
        val player = player(id = 1, name = "Teleporter")
        game.placeCreatureOnTile(player)
        player.setFlag(PlayerFlags_t.CanMapClickTeleport)
        game.groundTile(Position(1005, 1005, 7))
        var cancels = 0
        game.sendCancelMessage = { _, _ -> cancels++ }

        game.playerTeleport(1, Position(1005, 1005, 7))

        assertThat(player.position).isEqualTo(Position(1005, 1005, 7))
        assertThat(cancels).isZero()
    }

    @Test
    fun `internalTeleport rejects a null thing`() {
        assertThat(newGame().internalTeleport(null, Position(1, 1, 7))).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `internalTeleport rejects the current position`() {
        val game = newGame()
        val creature = testCreature(id = 1)
        game.placeCreatureOnTile(creature)

        assertThat(game.internalTeleport(creature, center)).isEqualTo(ReturnValue.CONTACTADMINISTRATOR)
    }

    @Test
    fun `internalTeleport rejects a missing destination tile`() {
        val game = newGame()
        val creature = testCreature(id = 1)
        game.placeCreatureOnTile(creature)

        assertThat(game.internalTeleport(creature, Position(1005, 1005, 7))).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `internalTeleport moves a creature and refreshes the facade index`() {
        val game = newGame()
        val creature = testCreature(id = 1)
        game.placeCreatureOnTile(creature)
        val destination = Position(1005, 1005, 7)
        val toTile = game.groundTile(destination)

        assertThat(game.internalTeleport(creature, destination)).isEqualTo(ReturnValue.NOERROR)

        assertThat(creature.position).isEqualTo(destination)
        assertThat(creature.getTile()).isSameAs(toTile)
    }

    @Test
    fun `internalTeleport moves an item through internalMoveItem`() {
        val game = newGame()
        val fromTile = game.groundTile(center)
        val item = Item(id = 200)
        assertThat(game.internalAddItem(fromTile, item)).isEqualTo(ReturnValue.NOERROR)
        val destination = Position(1005, 1005, 7)
        val toTile = game.groundTile(destination)

        assertThat(game.internalTeleport(item, destination)).isEqualTo(ReturnValue.NOERROR)

        assertThat(toTile.items).contains(item)
        assertThat(fromTile.items).doesNotContain(item)
    }

    @Test
    fun `internalTeleport blocks the eleventh burst teleport`() {
        val now = longArrayOf(1)
        val game = Game(GameMap(), GameDispatcher(), otsysTimeMs = { now[0] })
        val creature = testCreature(id = 1)
        game.placeCreatureOnTile(creature)
        val destination = Position(1005, 1005, 7)
        game.groundTile(destination)

        var blocked = 0
        repeat(11) {
            if (game.internalTeleport(creature, destination) != ReturnValue.NOERROR) {
                blocked++
            }
            if (creature.position == destination) {
                creature.position = center
                creature.getTile()?.removeThing(creature, 0)
                game.map.getTile(center)!!.addThing(creature)
            }
        }

        assertThat(blocked).isEqualTo(1)
    }

    // ----- targeting -----

    @Test
    fun `playerSetAttackedCreature with id zero cancels the target window`() {
        val game = newGame()
        val player = player(id = 1, name = "Fighter")
        game.placeCreatureOnTile(player)
        var cancels = 0
        player.sendCancelTarget = { cancels++ }

        game.playerSetAttackedCreature(1, 0)

        assertThat(cancels).isEqualTo(1)
    }

    @Test
    fun `playerSetAttackedCreature with id zero clears an existing target`() {
        val game = newGame()
        val player = player(id = 1, name = "Fighter")
        game.placeCreatureOnTile(player)
        val target = testCreature(id = 2, position = Position(1001, 1000, 7))
        game.placeCreatureOnTile(target, target.position)
        player.setAttackedCreature(target)
        val resets = mutableListOf<String>()
        game.resetLoginProtection = { resets.add("login") }
        var cancels = 0
        player.sendCancelTarget = { cancels++ }

        game.playerSetAttackedCreature(1, 0)

        assertThat(player.getAttackedCreature()).isNull()
        assertThat(resets).containsExactly("login")
        assertThat(cancels).isEqualTo(1)
    }

    @Test
    fun `playerSetAttackedCreature with an unknown creature clears the target`() {
        val game = newGame()
        val player = player(id = 1, name = "Fighter")
        game.placeCreatureOnTile(player)
        player.setAttackedCreature(null)
        var cancels = 0
        player.sendCancelTarget = { cancels++ }

        game.playerSetAttackedCreature(1, 99)

        assertThat(player.getAttackedCreature()).isNull()
        assertThat(cancels).isEqualTo(1)
    }

    @Test
    fun `playerSetAttackedCreature surfaces the combat validation error`() {
        val game = newGame()
        val player = player(id = 1, name = "Fighter")
        game.placeCreatureOnTile(player)
        val target = testCreature(id = 2, position = Position(1001, 1000, 7))
        game.placeCreatureOnTile(target, target.position)
        game.canTargetCreature = { _, _ -> ReturnValue.YOUMAYNOTATTACKTHISCREATURE }
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }
        var targetCancels = 0
        player.sendCancelTarget = { targetCancels++ }

        game.playerSetAttackedCreature(1, 2)

        assertThat(player.getAttackedCreature()).isNull()
        assertThat(cancels).containsExactly(ReturnValue.YOUMAYNOTATTACKTHISCREATURE)
        assertThat(targetCancels).isEqualTo(1)
    }

    @Test
    fun `playerSetAttackedCreature stores a valid target and updates the walk`() {
        val game = newGame()
        val player = player(id = 1, name = "Fighter")
        game.placeCreatureOnTile(player)
        val target = testCreature(id = 2, position = Position(1001, 1000, 7))
        game.placeCreatureOnTile(target, target.position)
        var walkUpdates = 0
        game.updateCreatureWalk = { walkUpdates++ }

        game.playerSetAttackedCreature(1, 2)

        assertThat(player.getAttackedCreature()).isSameAs(target)
        assertThat(walkUpdates).isEqualTo(1)
    }

    @Test
    fun `playerCancelAttackAndFollow cancels both targets and stops the walk`() {
        val game = newGame()
        val player = player(id = 1, name = "Fighter")
        game.placeCreatureOnTile(player)
        val target = testCreature(id = 2, position = Position(1001, 1000, 7))
        game.placeCreatureOnTile(target, target.position)
        player.setAttackedCreature(target)
        player.setFollowCreature(target)
        var walkUpdates = 0
        game.updateCreatureWalk = { walkUpdates++ }

        game.playerCancelAttackAndFollow(1)

        assertThat(player.getAttackedCreature()).isNull()
        assertThat(player.getFollowCreature()).isNull()
        assertThat(player.cancelNextWalk).isTrue()
        assertThat(walkUpdates).isEqualTo(1)
    }

    @Test
    fun `playerFollowCreature clears the attack, updates the walk and stores the follow`() {
        val game = newGame()
        val player = player(id = 1, name = "Follower")
        game.placeCreatureOnTile(player)
        val target = testCreature(id = 2, position = Position(1001, 1000, 7))
        game.placeCreatureOnTile(target, target.position)
        player.setAttackedCreature(target)
        var walkUpdates = 0
        game.updateCreatureWalk = { walkUpdates++ }

        game.playerFollowCreature(1, target.id)

        assertThat(player.getAttackedCreature()).isNull()
        assertThat(player.getFollowCreature()).isSameAs(target)
        assertThat(walkUpdates).isEqualTo(1)
    }

    @Test
    fun `playerSetFightModes stores the fight modes and skips pvp when expert pvp is off`() {
        val game = newGame()
        val player = player(id = 1, name = "Fighter")
        game.placeCreatureOnTile(player)
        var pvpMode: PvpMode_t? = null
        game.setPvpMode = { _, mode -> pvpMode = mode }

        game.playerSetFightModes(1, FightMode.Defense, chaseMode = true, secureMode = false)

        assertThat(player.fightMode).isEqualTo(FightMode.Defense)
        assertThat(player.chaseMode).isEqualTo(org.opentibia.canary.creatures.ChaseMode.Follow)
        assertThat(player.secureMode).isFalse()
        assertThat(pvpMode).isNull()
    }

    @Test
    fun `playerSetFightModes applies the pvp mode when expert pvp is enabled`() {
        val game = newGame()
        val player = player(id = 1, name = "Fighter")
        game.placeCreatureOnTile(player)
        game.expertPvpEnabled = true
        var pvpMode: PvpMode_t? = null
        game.setPvpMode = { _, mode -> pvpMode = mode }

        game.playerSetFightModes(1, FightMode.Attack, chaseMode = false, secureMode = true, pvpMode = PvpMode_t.PVP_MODE_RED_FIST)

        assertThat(pvpMode).isEqualTo(PvpMode_t.PVP_MODE_RED_FIST)
    }

    // ----- look -----

    @Test
    fun `playerLookAt cancels when no thing exists at the position`() {
        val game = newGame()
        val player = player(id = 1, name = "Watcher")
        game.placeCreatureOnTile(player)
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerLookAt(1, 0, Position(1001, 1000, 7), 0)

        assertThat(cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerLookAt cancels when the position is out of view`() {
        val game = newGame()
        val player = player(id = 1, name = "Watcher")
        game.placeCreatureOnTile(player)
        val far = Position(1100, 1100, 7)
        game.groundTile(far)
        val item = Item(id = 200)
        assertThat(game.internalAddItem(game.map.getTile(far), item)).isEqualTo(ReturnValue.NOERROR)
        val cancels = mutableListOf<ReturnValue>()
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }

        game.playerLookAt(1, 200, far, 0)

        assertThat(cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `playerLookAt computes the distance and dispatches both look hooks`() {
        val game = newGame()
        val player = player(id = 1, name = "Watcher")
        game.placeCreatureOnTile(player)
        val itemPos = Position(1002, 1001, 7)
        val tile = game.groundTile(itemPos)
        val item = Item(id = 200)
        assertThat(game.internalAddItem(tile, item)).isEqualTo(ReturnValue.NOERROR)
        val events = mutableListOf<Triple<Position, Thing, Int>>()
        game.eventPlayerOnLook = { _, pos, thing, _, distance -> events.add(Triple(pos, thing, distance)) }
        val callbacks = mutableListOf<Int>()
        game.callbackPlayerOnLook = { _, _, _, distance -> callbacks.add(distance) }

        game.playerLookAt(1, 200, itemPos, 0)

        assertThat(events).hasSize(1)
        assertThat(events[0].first).isEqualTo(itemPos)
        assertThat(events[0].second).isSameAs(item)
        assertThat(events[0].third).isEqualTo(2)
        assertThat(callbacks).containsExactly(2)
    }

    @Test
    fun `playerLookAt looking at the player itself uses distance minus one`() {
        val game = newGame()
        val player = player(id = 1, name = "Watcher")
        game.placeCreatureOnTile(player)
        val distances = mutableListOf<Int>()
        game.eventPlayerOnLook = { _, _, _, _, distance -> distances.add(distance) }

        game.playerLookAt(1, 0, center, 0)

        assertThat(distances).containsExactly(-1)
    }

    @Test
    fun `playerLookInBattleList ignores an unknown creature`() {
        val game = newGame()
        val player = player(id = 1, name = "Watcher")
        game.placeCreatureOnTile(player)
        var invoked = false
        game.eventPlayerOnLookInBattleList = { _, _, _ -> invoked = true }

        game.playerLookInBattleList(1, 99)

        assertThat(invoked).isFalse()
    }

    @Test
    fun `playerLookInBattleList computes the distance for a visible creature`() {
        val game = newGame()
        val player = player(id = 1, name = "Watcher")
        game.placeCreatureOnTile(player)
        val target = testCreature(id = 2, position = Position(1003, 1000, 7))
        game.placeCreatureOnTile(target, target.position)
        val distances = mutableListOf<Int>()
        game.eventPlayerOnLookInBattleList = { _, _, distance -> distances.add(distance) }
        val callbackDistances = mutableListOf<Int>()
        game.callbackPlayerOnLookInBattleList = { _, _, distance -> callbackDistances.add(distance) }

        game.playerLookInBattleList(1, 2)

        assertThat(distances).containsExactly(3)
        assertThat(callbackDistances).containsExactly(3)
    }

    // ----- speed helpers -----

    @Test
    fun `changeSpeed adds to the variance and notifies the same-floor spectators`() {
        val game = newGame()
        val spectator = player(id = 1, name = "Spectator", position = Position(1002, 1000, 7))
        game.placeCreatureOnTile(spectator)
        val creature = testCreature(id = 2, position = Position(1001, 1000, 7))
        game.placeCreatureOnTile(creature, creature.position)
        creature.setSpeed(10)
        val notifications = mutableListOf<Pair<Creature, Int>>()
        game.sendChangeSpeed = { _, target, stepSpeed -> notifications.add(target to stepSpeed) }

        game.changeSpeed(creature, 15)

        assertThat(creature.getSpeed()).isEqualTo(225)
        assertThat(notifications).containsExactly(creature to creature.getStepSpeed())
    }

    @Test
    fun `setCreatureSpeed replaces the base speed and notifies`() {
        val game = newGame()
        val spectator = player(id = 1, name = "Spectator", position = Position(1002, 1000, 7))
        game.placeCreatureOnTile(spectator)
        val creature = testCreature(id = 2, position = Position(1001, 1000, 7))
        game.placeCreatureOnTile(creature, creature.position)
        val notifications = mutableListOf<Int>()
        game.sendChangeSpeed = { _, _, stepSpeed -> notifications.add(stepSpeed) }

        game.setCreatureSpeed(creature, 123)

        assertThat(creature.getBaseSpeed()).isEqualTo(123)
        assertThat(notifications).containsExactly(creature.getStepSpeed())
    }

    @Test
    fun `changePlayerSpeed updates the player variance and notifies`() {
        val game = newGame()
        val spectator = player(id = 1, name = "Spectator", position = Position(1002, 1000, 7))
        game.placeCreatureOnTile(spectator)
        val runner = player(id = 2, name = "Runner", position = Position(1001, 1000, 7))
        game.placeCreatureOnTile(runner, runner.position)
        runner.setSpeed(20)
        val notifications = mutableListOf<Int>()
        game.sendChangeSpeed = { _, _, stepSpeed -> notifications.add(stepSpeed) }

        game.changePlayerSpeed(runner, 5)

        assertThat(runner.getSpeed()).isEqualTo(245)
        assertThat(notifications).containsExactly(245, 245)
    }
}
