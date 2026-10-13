package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router application tests for the outfit opcode `0xD3`
 * (`Game::playerChangeOutfit`, game.cpp:7300-7332): the mount/random-mount
 * state rules run on the routed player, while `Outfits`/`canWear` registries
 * stay injected seams (the port composes no outfit registry into `Player`).
 */
class OpcodeApplicationOutfitRouterTest {

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

    private fun hero(): Player =
        Player(1, "Hero", 150, 150, position = Position(1000, 1000, 7)).also { game.addPlayer(it) }

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

    private fun writeOutfitTypeZero(frame: ByteBuf, lookType: Int, setMount: Boolean, randomized: Int) {
        frame.writeByte(OutfitPacketHandler.OPCODE_SET_OUTFIT)
        frame.writeByte(0)
        frame.writeShortLE(lookType)
        frame.writeByte(10)
        frame.writeByte(20)
        frame.writeByte(30)
        frame.writeByte(40)
        frame.writeByte(3)
        frame.writeShortLE(5)
        frame.writeByte(1)
        frame.writeByte(2)
        frame.writeByte(3)
        frame.writeByte(4)
        frame.writeByte(if (setMount) 0x01 else 0x00)
        frame.writeShortLE(0)
        frame.writeByte(randomized)
    }

    @Test
    fun `set outfit opcode 0xD3 zeroes the random mount for a support outfit`() {
        start()
        try {
            val player = hero()
            player.setCurrentOutfit(Outfit(lookType = 75))
            player.features.mounts.setRandomMount(3)

            val frame = Unpooled.buffer()
            writeOutfitTypeZero(frame, lookType = 75, setMount = false, randomized = 1)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.features.mounts.isRandomMounted()).isFalse()
        } finally {
            stop()
        }
    }

    @Test
    fun `set outfit opcode 0xD3 clears the random mount when setMount is false`() {
        start()
        try {
            val player = hero()
            player.features.mounts.setRandomMount(3)

            val frame = Unpooled.buffer()
            writeOutfitTypeZero(frame, lookType = 136, setMount = false, randomized = 1)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.features.mounts.isRandomMounted()).isFalse()
        } finally {
            stop()
        }
    }

    @Test
    fun `set outfit opcode 0xD3 leaves the outfit unchanged without an outfit registry`() {
        start()
        try {
            val player = hero()
            player.setCurrentOutfit(Outfit(lookType = 136))
            player.setDefaultOutfit(Outfit(lookType = 136))

            val frame = Unpooled.buffer()
            writeOutfitTypeZero(frame, lookType = 137, setMount = true, randomized = 0)

            assertThat(route(frame, player)).isEqualTo(1)
            assertThat(player.getCurrentOutfit().lookType).isEqualTo(136)
            assertThat(player.getDefaultOutfit().lookType).isEqualTo(136)
        } finally {
            stop()
        }
    }

    @Test
    fun `change outfit applies the default and current outfit through the canWear seam`() {
        start()
        try {
            val player = hero()
            val command = SetOutfitCommand(
                outfitType = 0,
                outfit = Outfit(lookType = 136, lookAddons = 3),
                setMount = false,
            )

            OutfitPacketHandler.apply(
                player = player,
                command = command,
                canWear = { lookType, addons -> lookType == 136 && addons == 3 },
            )

            assertThat(player.getDefaultOutfit().lookType).isEqualTo(136)
            assertThat(player.getDefaultOutfit().lookAddons).isEqualTo(3)
            assertThat(player.getCurrentOutfit().lookType).isEqualTo(136)
        } finally {
            stop()
        }
    }

    @Test
    fun `change outfit keeps the current outfit under a condition outfit`() {
        start()
        try {
            val player = hero()
            player.setCurrentOutfit(Outfit(lookType = 75))
            val command = SetOutfitCommand(
                outfitType = 0,
                outfit = Outfit(lookType = 136),
                setMount = false,
            )

            OutfitPacketHandler.apply(
                player = player,
                command = command,
                canWear = { _, _ -> true },
                hasOutfitCondition = { true },
            )

            assertThat(player.getDefaultOutfit().lookType).isEqualTo(136)
            assertThat(player.getCurrentOutfit().lookType).isEqualTo(75)
        } finally {
            stop()
        }
    }
}
