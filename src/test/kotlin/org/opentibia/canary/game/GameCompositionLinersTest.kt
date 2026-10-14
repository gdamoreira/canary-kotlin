package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.ConfigSource
import org.opentibia.canary.creatures.FlagAsyncClass_t
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Composition-liner tests for the `Game` facade:
 *
 *  - `Player::removeMessageBuffer`'s `g_configManager().getNumber(MAX_MESSAGEBUFFER)`
 *    host value (configmanager.cpp:315) applied when a player enters the world
 *    (`Game::addPlayer`, game.cpp:11624).
 *  - `Creature::enqueueAsyncTask` (creature.cpp:2031-2054) routing every
 *    non-monster creature batch through the world-shared async buckets at
 *    login (`Game::addPlayer`) and placement (`Game::internalPlaceCreature`,
 *    game.cpp:1495), with the `g_dispatcher()` attached (creature.cpp:2186-2193).
 */
class GameCompositionLinersTest {

    private val center = Position(1000, 1000, 7)

    private fun config(values: Map<String, String>): ConfigManager = ConfigManager(
        ConfigKeys.all.associateWith { it.luaIdentifier },
        properties = ConfigSource.of(values),
    )

    private fun player(id: Long = 1): Player =
        Player(id = id, name = "Hero", maxHealth = 150, health = 150, position = center)

    private fun Game.placeGround() {
        map.getOrCreateTile(center).ground = Item(id = 100, type = ItemType(id = 100, name = "ground", isGroundTile = true))
    }

    // --- MAX_MESSAGEBUFFER (configmanager.cpp:315) ------------------------------

    @Test
    fun `addPlayer applies the configured MAX_MESSAGEBUFFER`() {
        val game = Game(GameMap(), GameDispatcher(), configManager = config(mapOf("maxMessageBuffer" to "7")))
        val hero = player()

        game.addPlayer(hero)

        assertThat(hero.maxMessageBuffer).isEqualTo(7)
    }

    @Test
    fun `addPlayer falls back to the MAX_MESSAGEBUFFER default when the source omits it`() {
        val game = Game(GameMap(), GameDispatcher(), configManager = config(emptyMap()))
        val hero = player()

        game.addPlayer(hero)

        assertThat(hero.maxMessageBuffer).isEqualTo(4)
    }

    @Test
    fun `addPlayer keeps the host MAX_MESSAGEBUFFER without a config manager`() {
        val game = Game(GameMap(), GameDispatcher())
        val hero = player().apply { maxMessageBuffer = 3 }

        game.addPlayer(hero)

        assertThat(hero.maxMessageBuffer).isEqualTo(3)
    }

    // --- non-monster async buckets (creature.cpp:2031-2054) ---------------------

    @Test
    fun `addPlayer routes the session player batch through the shared buckets`() {
        // A stopped dispatcher rejects bucket admission, so the batch must
        // roll back instead of running inline (creature.cpp:2045-2053).
        val dispatcher = GameDispatcher()
        val game = Game(GameMap(), dispatcher)
        val hero = player()
        game.addPlayer(hero)

        assertThat(hero.asyncTaskDispatcher).isSameAs(dispatcher)

        var ran = false
        hero.addAsyncTask { ran = true }

        assertThat(ran).isFalse()
        assertThat(hero.hasAsyncTaskFlag(FlagAsyncClass_t.AsyncTaskRunning)).isFalse()
    }

    @Test
    fun `internalPlaceCreature routes a non-monster batch through the shared buckets`() {
        val dispatcher = GameDispatcher()
        val game = Game(GameMap(), dispatcher)
        game.placeGround()
        val npc = Npc(id = 5, name = "Bob", position = center)

        assertThat(game.internalPlaceCreature(npc, center)).isTrue()
        assertThat(npc.asyncTaskDispatcher).isSameAs(dispatcher)

        var ran = false
        npc.addAsyncTask { ran = true }

        assertThat(ran).isFalse()
        assertThat(npc.hasAsyncTaskFlag(FlagAsyncClass_t.AsyncTaskRunning)).isFalse()
    }

    @Test
    fun `a running dispatcher executes a placed non-monster batch off the caller thread`() {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val game = Game(GameMap(), dispatcher)
            game.placeGround()
            val npc = Npc(id = 5, name = "Bob", position = center)
            assertThat(game.internalPlaceCreature(npc, center)).isTrue()

            val caller = Thread.currentThread()
            var ranOn: Thread? = null
            val done = CountDownLatch(1)
            npc.addAsyncTask {
                ranOn = Thread.currentThread()
                done.countDown()
            }

            assertThat(done.await(2, TimeUnit.SECONDS)).isTrue()
            assertThat(ranOn).isNotNull
            assertThat(ranOn).isNotSameAs(caller)
            assertThat(npc.hasAsyncTaskFlag(FlagAsyncClass_t.AsyncTaskRunning)).isFalse()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `addPlayer attaches the dispatcher and executes a session batch off the caller thread`() {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val game = Game(GameMap(), dispatcher)
            val hero = player()
            game.addPlayer(hero)

            val caller = Thread.currentThread()
            var ranOn: Thread? = null
            val done = CountDownLatch(1)
            hero.addAsyncTask {
                ranOn = Thread.currentThread()
                done.countDown()
            }

            assertThat(done.await(2, TimeUnit.SECONDS)).isTrue()
            assertThat(ranOn).isNotNull
            assertThat(ranOn).isNotSameAs(caller)
        } finally {
            dispatcher.shutdown()
        }
    }
}
