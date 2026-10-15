package org.opentibia.canary.network.protocol.guild

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.getHelpers
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Spectators
import org.opentibia.canary.network.protocol.creature.CreaturePacketWriter
import org.opentibia.canary.social.guild.Guild

/**
 * Transcript tests for the guild helper broadcast of
 * `Guild::addMember`/`Guild::removeMember`
 * (src/creatures/players/grouping/guild.cpp:14-32), which ask the game to
 * refresh every online member through `g_game().updatePlayerHelpers(member)`.
 *
 * The guild seam is bound to the production `Game::updatePlayerHelpers`
 * (game.cpp:9690-9699), so each online member emits
 * `ProtocolGame::sendCreatureHelpers` (0x94, protocolgame.cpp:10928-10938)
 * frames to the spectators of its position carrying the member's
 * `Player::getHelpers()` count (player.cpp:8335-8358), written through
 * [CreaturePacketWriter].
 */
class GuildHelperBroadcastFramesTest {

    private val center = Position(1000, 1000, 7)

    private fun player(id: Long, name: String, position: Position): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position)

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    /** `writeCreatureHelpers` output as unsigned byte values for exact assertions. */
    private fun frame(creatureId: Long, helpers: Int): List<Int> {
        val out = Unpooled.buffer()
        CreaturePacketWriter.writeCreatureHelpers(out, creatureId, helpers)
        return out.toByteArray().map { it.toInt() and 0xFF }
    }

    @Test
    fun `guild membership changes broadcast the helper frames for online members`() {
        Spectators.clearCache()
        val game = Game(GameMap(), GameDispatcher())
        game.map.getOrCreateTile(center).ground = Item(id = 100)
        val watcher = player(1L, "Watcher", center)
        assertThat(game.internalPlaceCreature(watcher, center)).isTrue()

        val guild = Guild(
            id = 1,
            name = "Red Rose",
            updatePlayerHelpers = { member -> game.updatePlayerHelpers(member) },
        )
        game.getPlayerHelpers = { member ->
            member.getHelpers(
                guildMembersOnline = { it.getMembersOnline() },
                guildMemberCountOnline = { it.getMemberCountOnline().toInt() },
            )
        }
        val frames = mutableListOf<List<Int>>()
        game.sendCreatureHelpers = { _, creatureId, helpers -> frames.add(frame(creatureId, helpers)) }

        // 0x94, creature id uint32 LE, helpers uint16 LE.
        watcher.setGuild(guild)
        assertThat(frames).containsExactly(listOf(0x94, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00))

        // A member outside the map has no spectators, so only the placed watcher
        // receives the refreshed count.
        val absent = player(2L, "Absent", Position(2000, 2000, 7))
        absent.setGuild(guild)
        assertThat(frames).containsExactly(
            listOf(0x94, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00),
            listOf(0x94, 0x01, 0x00, 0x00, 0x00, 0x02, 0x00),
        )

        watcher.setGuild(null)
        assertThat(frames).containsExactly(
            listOf(0x94, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00),
            listOf(0x94, 0x01, 0x00, 0x00, 0x00, 0x02, 0x00),
            listOf(0x94, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00),
        )
    }
}
