package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router application tests for follow (`0xA2`): the decoded creature id must
 * reach `Game::playerFollowCreature` (game.cpp:7033) ->
 * `Player::setFollowCreature` (player.cpp:6127) ->
 * `Creature::setFollowCreature` (creature.cpp:1132) on the WorldCommit lane.
 */
class OpcodeApplicationFollowRouterTest {

    private lateinit var dispatcher: GameDispatcher
    private lateinit var map: GameMap
    private lateinit var game: Game

    private fun start() {
        dispatcher = GameDispatcher()
        dispatcher.start()
        map = GameMap()
        game = Game(map, dispatcher)
    }

    private fun stop() {
        dispatcher.shutdown()
    }

    private fun context(): GamePacketContext = GamePacketContext(
        map = map,
        dispatcher = dispatcher,
        luaEnvironment = LuaEnvironment().apply { initState() },
        game = game,
    )

    private fun player(id: Long, name: String, position: Position): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position).apply {
            // The follow sight gate runs through `Player::canSee`
            // (player.cpp:1402), which is inert without an attached client.
            hasClient = { true }
        }.also { game.addPlayer(it) }

    private fun route(frame: ByteBuf, player: Player): Int {
        val handled = GamePacketRouter.route(player, frame, context())
        awaitWorldCommit()
        return handled
    }

    private fun awaitWorldCommit() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    private fun followFrame(creatureId: Long): ByteBuf {
        val frame = Unpooled.buffer()
        frame.writeByte(FollowPacketHandler.OPCODE_FOLLOW)
        frame.writeIntLE(creatureId.toInt())
        return frame
    }

    @Test
    fun `follow opcode 0xA2 sets the follow creature and clears the attack target`() {
        start()
        try {
            val player = player(1, "Hero", Position(1000, 1000, 7))
            val target = player(2, "Target", Position(1001, 1000, 7))

            assertThat(route(followFrame(2), player)).isEqualTo(1)

            assertThat(player.getFollowCreature()).isSameAs(target)
            assertThat(player.getAttackedCreature()).isNull()
        } finally {
            stop()
        }
    }

    @Test
    fun `follow opcode 0xA2 cancels on a creature on another floor`() {
        start()
        try {
            val player = player(1, "Hero", Position(1000, 1000, 7))
            player(2, "Target", Position(1001, 1000, 8))
            val cancelMessages = mutableListOf<ReturnValue>()
            var cancelTargets = 0
            player.sendCancelMessage = { cancelMessages.add(it) }
            player.sendCancelTarget = { cancelTargets++ }

            assertThat(route(followFrame(2), player)).isEqualTo(1)

            assertThat(player.getFollowCreature()).isNull()
            assertThat(cancelMessages).containsExactly(ReturnValue.THEREISNOWAY)
            assertThat(cancelTargets).isEqualTo(1)
            assertThat(player.cancelNextWalk).isTrue()
        } finally {
            stop()
        }
    }

    @Test
    fun `follow opcode 0xA2 clears the follow state for an unknown creature`() {
        start()
        try {
            val player = player(1, "Hero", Position(1000, 1000, 7))
            player(2, "Target", Position(1001, 1000, 7))

            assertThat(route(followFrame(99), player)).isEqualTo(1)

            assertThat(player.getFollowCreature()).isNull()
        } finally {
            stop()
        }
    }
}
