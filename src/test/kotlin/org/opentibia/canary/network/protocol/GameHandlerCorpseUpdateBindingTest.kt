package org.opentibia.canary.network.protocol

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Wiring tests for the `Game.sendItemUpdateToClient` seam
 * (`Item::sendUpdateToClient`, src/items/item.cpp:3648-3680): the
 * `Creature::dropCorpse` tail (creature.cpp:815) and the quick-loot senders
 * (game.cpp:3752, 6572) reach it, and the bound production seam must emit the
 * `Player::sendUpdateTileItem` 0x6B frame through each target session's
 * transport instead of the inert default.
 */
class GameHandlerCorpseUpdateBindingTest {

    private class MockAccountRepo : AccountRepository {
        override fun findByAccountName(name: String): Account? = null

        override fun authenticate(name: String, password: String): Account? = null
    }

    private val dispatcher = GameDispatcher().apply { start() }
    private val gameMap = GameMap()

    @AfterEach
    fun tearDown() {
        dispatcher.shutdown()
    }

    private fun handler(): GameHandler = GameHandler(RsaCipher(), MockAccountRepo(), gameMap, dispatcher)

    private fun player(id: Long, name: String): Player = Player(
        id = id,
        name = name,
        maxHealth = 150,
        health = 150,
        position = Position(1000, 1000, 7),
    )

    private fun placedCorpse(game: Game): Container {
        val tile = gameMap.getOrCreateTile(Position(1000, 1000, 7))
        tile.ground = Item(id = 106)
        val corpse = Container(3058, 8, ItemType(id = 3058, name = "dead rat", isContainer = true))
        assertThat(game.internalAddItem(tile, corpse)).isEqualTo(ReturnValue.NOERROR)
        return corpse
    }

    @Test
    fun `the bound corpse update seam writes the 0x6B tile item update through the owner transport`() {
        val game = handler().game
        val owner = player(1L, "Hunter")
        val sink = InMemoryProtocolSink(owner, gameMap)
        owner.protocolSink = sink
        val corpse = placedCorpse(game)

        game.sendItemUpdateToClient(owner, corpse)

        assertThat(sink.frames).hasSize(1)
        val frame = sink.frames[0]
        assertThat(frame[0]).isEqualTo(0x6B.toByte())
        // Position(1000, 1000, 7) little-endian, then the corpse stack position
        // (ground 0, corpse 1).
        assertThat(frame.copyOfRange(1, 3)).isEqualTo(byteArrayOf(0xE8.toByte(), 0x03))
        assertThat(frame.copyOfRange(3, 5)).isEqualTo(byteArrayOf(0xE8.toByte(), 0x03))
        assertThat(frame[5]).isEqualTo(7.toByte())
        assertThat(frame[6]).isEqualTo(1.toByte())
        // `AddItem`: the item id (3058 = 0x0BF2) little-endian first.
        assertThat(frame.copyOfRange(7, 9)).isEqualTo(byteArrayOf(0xF2.toByte(), 0x0B))
    }

    @Test
    fun `the bound corpse update seam fans out through the party players instead of the owner alone`() {
        val game = handler().game
        val leader = player(1L, "Leader")
        val member = player(2L, "Member")
        val leaderSink = InMemoryProtocolSink(leader, gameMap)
        val memberSink = InMemoryProtocolSink(member, gameMap)
        leader.protocolSink = leaderSink
        member.protocolSink = memberSink

        val party = Party(leader = leader, autoShareExperience = false)
        assertThat(party.invitePlayer(member)).isTrue()
        assertThat(party.joinParty(member)).isTrue()

        val corpse = placedCorpse(game)
        game.sendItemUpdateToClient(leader, corpse)

        assertThat(leaderSink.frames).hasSize(1)
        assertThat(memberSink.frames).hasSize(1)
        assertThat(memberSink.frames[0][0]).isEqualTo(0x6B.toByte())
        assertThat(memberSink.frames[0]).isEqualTo(leaderSink.frames[0])
    }

    @Test
    fun `a corpse that is not on a tile emits nothing`() {
        val game = handler().game
        val owner = player(1L, "Hunter")
        val sink = InMemoryProtocolSink(owner, gameMap)
        owner.protocolSink = sink
        val loose = Container(3058, 8, ItemType(id = 3058, name = "dead rat", isContainer = true))

        game.sendItemUpdateToClient(owner, loose)

        assertThat(sink.frames).isEmpty()
    }
}
