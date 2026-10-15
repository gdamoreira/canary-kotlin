package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.systems.taskhunting.PreyTaskAction
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router wiring tests for the wave-35 third inbound opcode batch: private
 * channels (`0xAA`-`0xAC`), cyclopedia house auction (`0xAD`), bosstiary
 * (`0xAE`-`0xB0`), highscores (`0xB1`), weapon proficiency (`0xB3`), task
 * hunting (`0xBA`), cancel attack/follow (`0xBE`), forge history (`0xC0`),
 * client details (`0xC1`) and boss difficulty (`0xC2`). Each decoded command
 * crosses the WorldCommit confinement boundary (AGENTS.md 1.2).
 */
class InboundOpcodeBatch3PacketHandlerRouterWiringTest {

    @Test
    fun `routes every batch b3 opcode and counts them handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(ChannelPacketHandler.OPCODE_CREATE_PRIVATE_CHANNEL)
        frame.writeByte(ChannelPacketHandler.OPCODE_CHANNEL_INVITE)
        frame.writeTibiaString("Guildmate")
        frame.writeShortLE(1)
        frame.writeByte(ChannelPacketHandler.OPCODE_CHANNEL_EXCLUDE)
        frame.writeTibiaString("Rival")
        frame.writeShortLE(2)
        frame.writeByte(CyclopediaHouseAuctionPacketHandler.OPCODE_CYCLOPEDIA_HOUSE_AUCTION)
        frame.writeByte(0)
        frame.writeTibiaString("Thais")
        frame.writeByte(BosstiaryPacketHandler.OPCODE_SEND_BOSSTIARY)
        frame.writeByte(BosstiaryPacketHandler.OPCODE_SEND_BOSSTIARY_SLOTS)
        frame.writeByte(BosstiaryPacketHandler.OPCODE_BOSSTIARY_SLOT)
        frame.writeByte(1)
        frame.writeIntLE(0x0A0B)
        frame.writeByte(HighscoresPacketHandler.OPCODE_HIGHSCORES)
        frame.writeByte(HighscoresPacketHandler.HIGHSCORE_OURRANK)
        frame.writeByte(3)
        frame.writeIntLE(4)
        frame.writeTibiaString("Antica")
        frame.writeByte(0)
        frame.writeByte(0)
        frame.writeByte(10)
        frame.writeByte(WeaponProficiencyPacketHandler.OPCODE_WEAPON_PROFICIENCY)
        frame.writeByte(0)
        frame.writeShortLE(0x1234)
        frame.writeByte(TaskHuntingPacketHandler.OPCODE_TASK_HUNTING_ACTION)
        frame.writeByte(0)
        frame.writeByte(PreyTaskAction.ListReroll.value)
        frame.writeByte(0)
        frame.writeShortLE(0)
        frame.writeByte(CancelAttackAndFollowPacketHandler.OPCODE_CANCEL_ATTACK_AND_FOLLOW)
        frame.writeByte(ForgeHistoryPacketHandler.OPCODE_FORGE_BROWSE_HISTORY)
        frame.writeShortLE(0)
        frame.writeByte(2)
        frame.writeByte(ClientDetailsPacketHandler.OPCODE_CLIENT_DETAILS)
        frame.writeTibiaString("Tibia 15.25")
        frame.writeByte(BossDifficultyPacketHandler.OPCODE_BOSS_DIFFICULTY_SELECTION)
        frame.writeIntLE(7)
        frame.writeByte(1)

        assertThat(route(frame)).isEqualTo(14)
    }

    @Test
    fun `rejects a malformed bosstiary slot without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(BosstiaryPacketHandler.OPCODE_BOSSTIARY_SLOT)
        frame.writeByte(1)

        assertThat(route(frame)).isZero()
    }

    @Test
    fun `rejects a malformed weapon proficiency payload without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(WeaponProficiencyPacketHandler.OPCODE_WEAPON_PROFICIENCY)
        frame.writeByte(0x02)

        assertThat(route(frame)).isZero()
    }

    @Test
    fun `rejects a malformed house auction payload without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(CyclopediaHouseAuctionPacketHandler.OPCODE_CYCLOPEDIA_HOUSE_AUCTION)
        frame.writeByte(1)
        frame.writeIntLE(7)

        assertThat(route(frame)).isZero()
    }

    @Test
    fun `applies a task hunting action to the player state on the world commit lane`() {
        val player = hero()
        player.initializeTaskHunting()
        val frame = Unpooled.buffer()
        frame.writeByte(TaskHuntingPacketHandler.OPCODE_TASK_HUNTING_ACTION)
        frame.writeByte(0) // slot
        frame.writeByte(PreyTaskAction.ListReroll.value)
        frame.writeByte(0) // upgrade
        frame.writeShortLE(0) // race id

        assertThat(route(frame, player)).isEqualTo(1)

        assertThat(player.features.taskHunting.dialogMessages)
            .contains("You don't have enought money to reroll the task hunting slot.")
    }

    @Test
    fun `clears the attacked creature on cancel attack and follow`() {
        val player = hero()
        val target = Player(2, "Target", 100, 100, position = Position(101, 100, 7))
        assertThat(player.setAttackedCreature(target)).isTrue()

        val frame = Unpooled.buffer()
        frame.writeByte(CancelAttackAndFollowPacketHandler.OPCODE_CANCEL_ATTACK_AND_FOLLOW)

        assertThat(route(frame, player)).isEqualTo(1)
        assertThat(player.getAttackedCreature()).isNull()
    }

    private fun route(frame: ByteBuf, player: Player = hero()): Int {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val handled = GamePacketRouter.route(
                player,
                frame,
                GamePacketContext(
                    map = GameMap(),
                    dispatcher = dispatcher,
                    luaEnvironment = LuaEnvironment().apply { initState() },
                ),
            )
            awaitWorldCommit(dispatcher)
            return handled
        } finally {
            dispatcher.shutdown()
        }
    }

    private fun hero(): Player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7)).apply {
        // The attack/follow sight gates run through `Player::canSee`
        // (player.cpp:1402), which is inert without an attached client.
        hasClient = { true }
    }

    private fun awaitWorldCommit(dispatcher: GameDispatcher) {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }
}
