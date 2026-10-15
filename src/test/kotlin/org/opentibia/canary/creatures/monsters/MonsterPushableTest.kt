package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.MonsterComputeConfig
import org.opentibia.canary.game.scheduling.MonsterComputeService
import org.opentibia.canary.items.INDEX_WHEREEVER
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.SpectatorSector
import org.opentibia.canary.map.Spectators
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcripts of `Monster::isPushable` (src/creatures/monsters/monster.cpp:275-277):
 * `m_monsterType->info.pushable && baseSpeed != 0`. Both push consumers consult it —
 * `Game::playerMoveCreature` (src/game/game.cpp:1844, `RETURNVALUE_NOTMOVABLE`) and
 * the `Tile::queryAdd` monster push loop (src/items/tile.cpp:736,
 * `RETURNVALUE_NOTPOSSIBLE`) — so a type-unpushable or zero-base-speed monster must
 * be rejected by both.
 *
 * The type is bound through `MonsterPipelinesHost.register` exactly like
 * `Game::internalPlaceCreature` does in production (game.cpp:1513-1515 reaches
 * `g_monsters().getMonsterType`), and the `info.pushable` read stays live like the
 * C++ pointer dereference.
 */
class MonsterPushableTest {

    private val center = Position(1000, 1000, 7)
    private val ratPosition = Position(1001, 1000, 7)

    private lateinit var service: MonsterComputeService
    private lateinit var host: MonsterPipelinesHost
    private val cancels = mutableListOf<ReturnValue>()

    private val items = ItemTypeRegistry().apply {
        register(ItemType(100, "ground", isGroundTile = true))
    }
    private val ratType = MonsterType("Rat")

    @BeforeEach
    fun setUp() {
        Spectators.clearCache()
        service = MonsterComputeService()
        service.start(MonsterComputeConfig(configuredThreads = 0, capacity = 16, hardwareConcurrency = 1))
        host = MonsterPipelinesHost(
            service = service,
            mapAccess = SpectatorMapAccess { _, _ -> SpectatorSector() },
        )
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
    }

    private fun gameWithHost(): Game {
        val game = Game(
            GameMap(),
            GameDispatcher(),
            itemTypes = items,
            monsterHost = host,
            monsterTypeProvider = { name -> if (name == ratType.name) ratType else null },
        )
        game.sendCancelMessage = { _, ret -> cancels.add(ret) }
        return game
    }

    private fun Game.ground(position: Position): Tile =
        map.getOrCreateTile(position).also {
            if (it.ground == null) {
                it.ground = Item(id = 100, type = items.get(100))
            }
        }

    private fun monster(id: Long, speed: Int = 110): Monster =
        Monster(id = id, name = "Rat", maxHealth = 100, health = 100, speed = speed, position = ratPosition)

    private fun placePlayer(game: Game, id: Long = 1): Player {
        val player = Player(id = id, name = "Hero", maxHealth = 150, health = 150, position = center)
        game.addPlayer(player)
        game.ground(center)
        game.internalPlaceCreature(player, center)
        return player
    }

    private fun placeRat(game: Game, rat: Monster): Boolean {
        game.ground(ratPosition)
        return game.internalPlaceCreature(rat, ratPosition)
    }

    private fun push(game: Game, user: Player, rat: Monster): Tile {
        val destination = game.ground(Position(ratPosition.x + 1, ratPosition.y, ratPosition.z))
        var event: (() -> Unit)? = null
        game.addWalkEvent = { _, _, task -> event = task; true }
        game.playerMoveCreature(user, rat, rat.position, destination)
        event!!.invoke()
        return destination
    }

    @Test
    fun `the host binds the live type pushable flag onto the monster`() {
        val rat = monster(90)
        host.register(rat, ratType)

        assertThat(rat.isPushable()).isTrue()

        // `Monster::isPushable` dereferences the live `m_monsterType->info.pushable`.
        ratType.info.pushable = false
        assertThat(rat.isPushable()).isFalse()

        ratType.info.pushable = true
        assertThat(rat.isPushable()).isTrue()
    }

    @Test
    fun `a zero base speed monster is not pushable`() {
        ratType.info.pushable = true
        val rat = monster(90, speed = 0)
        host.register(rat, ratType)

        assertThat(rat.getBaseSpeed()).isZero()
        assertThat(rat.isPushable()).isFalse()
    }

    @Test
    fun `a stale owner never reads the replacement entry type`() {
        val rat = monster(90)
        host.register(rat, ratType)
        host.unregister(90)

        val replacementType = MonsterType("Rat").apply { info.pushable = false }
        host.register(monster(90), replacementType)

        // The unbound/missing-owner default keeps the base `Creature::isPushable`.
        assertThat(rat.isPushable()).isTrue()
        assertThat(host.entryOf(90)!!.monster.isPushable()).isFalse()
    }

    @Test
    fun `a type unpushable monster rejects the player push`() {
        val game = gameWithHost()
        val user = placePlayer(game)
        ratType.info.pushable = false
        val rat = monster(90)
        assertThat(placeRat(game, rat)).isTrue()

        assertThat(rat.isPushable()).isFalse()
        val destination = push(game, user, rat)

        assertThat(cancels).containsExactly(ReturnValue.NOTMOVABLE)
        assertThat(destination.getCreatures()).isEmpty()
    }

    @Test
    fun `a pushable type monster is moved by the player push`() {
        val game = gameWithHost()
        val user = placePlayer(game)
        ratType.info.pushable = true
        val rat = monster(90)
        assertThat(placeRat(game, rat)).isTrue()

        val destination = push(game, user, rat)

        assertThat(cancels).isEmpty()
        assertThat(destination.getCreatures()).contains(rat)
    }

    @Test
    fun `a zero base speed monster rejects the player push`() {
        val game = gameWithHost()
        val user = placePlayer(game)
        ratType.info.pushable = true
        val rat = monster(90, speed = 0)
        assertThat(placeRat(game, rat)).isTrue()

        push(game, user, rat)

        assertThat(cancels).containsExactly(ReturnValue.NOTMOVABLE)
    }

    @Test
    fun `a can push creatures monster cannot enter an unpushable occupant tile`() {
        val tile = Tile(center)
        tile.internalAddThing(Item(id = 100, type = items.get(100)))
        val pusher = monster(1)
        val pusherType = MonsterType("Pusher").apply { info.canPushCreatures = true }
        tile.monsterTypeProvider = { if (it === pusher) pusherType else null }
        val occupant = monster(2)
        host.register(occupant, MonsterType("Occupant").apply { info.pushable = false })
        tile.addThing(occupant)

        assertThat(occupant.isPushable()).isFalse()
        assertThat(tile.queryAdd(INDEX_WHEREEVER, pusher, 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `a can push creatures monster enters a pushable occupant tile`() {
        val tile = Tile(center)
        tile.internalAddThing(Item(id = 100, type = items.get(100)))
        val pusher = monster(1)
        val pusherType = MonsterType("Pusher").apply { info.canPushCreatures = true }
        tile.monsterTypeProvider = { if (it === pusher) pusherType else null }
        val occupant = monster(2)
        host.register(occupant, MonsterType("Occupant"))
        tile.addThing(occupant)

        assertThat(occupant.isPushable()).isTrue()
        assertThat(tile.queryAdd(INDEX_WHEREEVER, pusher, 1, 0)).isEqualTo(ReturnValue.NOERROR)
    }
}
