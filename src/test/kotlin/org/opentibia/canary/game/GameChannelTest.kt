package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.chat.ChatManager
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the `Game::` channel handlers:
 *
 *  - `Game::playerCreatePrivateChannel` (src/game/game.cpp:4324)
 *  - `Game::playerChannelInvite` (src/game/game.cpp:4338)
 *  - `Game::playerChannelExclude` (src/game/game.cpp:4361)
 *  - `Game::playerRequestChannels` (src/game/game.cpp:4384)
 *  - `Game::playerOpenChannel` (src/game/game.cpp:4393)
 *  - `Game::playerCloseChannel` (src/game/game.cpp:4415)
 *  - `Game::playerOpenPrivateChannel` (src/game/game.cpp:4424)
 *  - `Game::playerAutoWalk` (src/game/game.cpp:4472)
 *
 * The `g_chat()` singleton is the injected [ChatManager]; the protocol sends
 * are injected seams following the established `Game` facade pattern.
 */
class GameChannelTest {

    private val center = Position(1000, 1000, 7)

    private data class ChannelSend(val player: Player, val id: Int, val name: String)

    private val chatManager = ChatManager()

    private fun newGame(): Game = Game(GameMap(), GameDispatcher(), chatManager = chatManager)

    private fun Game.player(id: Long, name: String, premium: Boolean = true): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = center).also {
            it.premium = premium
            addPlayer(it)
        }

    @Test
    fun `playerCreatePrivateChannel creates the channel and sends it to the owner`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val sends = mutableListOf<ChannelSend>()
        game.sendCreatePrivateChannel = { owner, id, name -> sends.add(ChannelSend(owner, id, name)) }

        game.playerCreatePrivateChannel(1)

        val channel = chatManager.getPrivateChannel(player)!!
        val send = sends.single()
        assertThat(send.player).isSameAs(player)
        assertThat(send.id).isEqualTo(channel.getId())
        assertThat(send.name).isEqualTo("Hero's Channel")
    }

    @Test
    fun `playerCreatePrivateChannel rejects a non-premium player`() {
        val game = newGame()
        game.player(1, "Hero", premium = false)
        var sent = false
        game.sendCreatePrivateChannel = { _, _, _ -> sent = true }

        game.playerCreatePrivateChannel(1)

        assertThat(sent).isFalse()
    }

    @Test
    fun `playerChannelInvite invites the named player into the private channel`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val invited = game.player(2, "Buddy")
        game.sendCreatePrivateChannel = { _, _, _ -> }
        game.playerCreatePrivateChannel(1)

        game.playerChannelInvite(1, "Buddy")

        val channel = chatManager.getPrivateChannel(player)!!
        assertThat(channel.isInvited(invited.getGUID())).isTrue()
    }

    @Test
    fun `playerChannelExclude excludes the named player from the private channel`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val excluded = game.player(2, "Buddy")
        game.sendCreatePrivateChannel = { _, _, _ -> }
        game.playerCreatePrivateChannel(1)
        game.playerChannelInvite(1, "Buddy")

        game.playerChannelExclude(1, "Buddy")

        val channel = chatManager.getPrivateChannel(player)!!
        assertThat(channel.isInvited(excluded.getGUID())).isFalse()
    }

    @Test
    fun `playerRequestChannels sends the channels dialog`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val requested = mutableListOf<Player>()
        game.sendChannelsDialog = { requested.add(it) }

        game.playerRequestChannels(1)

        assertThat(requested).containsExactly(player)
    }

    @Test
    fun `playerOpenChannel sends a public channel without the users map`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        chatManager.load("<chatchannels><channel id=\"5\" name=\"World\" public=\"1\"/></chatchannels>")

        val sends = mutableListOf<Triple<Int, String, Map<Long, Player>?>>()
        game.sendChannel = { _, id, name, users, _ -> sends.add(Triple(id, name, users)) }

        game.playerOpenChannel(1, 5)

        val send = sends.single()
        assertThat(send.first).isEqualTo(5)
        assertThat(send.second).isEqualTo("World")
        assertThat(send.third).isNull()
        assertThat(chatManager.getChannel(player, 5)!!.getUsers()).containsKey(player.id)
    }

    @Test
    fun `playerOpenChannel sends a non-public channel with its users`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        chatManager.load("<chatchannels><channel id=\"2\" name=\"Guild\" public=\"0\"/></chatchannels>")

        val sends = mutableListOf<Triple<Int, String, Map<Long, Player>?>>()
        game.sendChannel = { _, id, name, users, _ -> sends.add(Triple(id, name, users)) }

        game.playerOpenChannel(1, 2)

        val send = sends.single()
        assertThat(send.first).isEqualTo(2)
        assertThat(send.second).isEqualTo("Guild")
        assertThat(send.third).containsKey(player.id)
    }

    @Test
    fun `playerCloseChannel removes the player from the channel`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        game.sendCreatePrivateChannel = { _, _, _ -> }
        game.playerCreatePrivateChannel(1)
        val channel = chatManager.getPrivateChannel(player)!!
        game.playerOpenChannel(1, channel.getId())

        game.playerCloseChannel(1, channel.getId())

        assertThat(channel.getUsers()).doesNotContainKey(player.id)
    }

    @Test
    fun `playerOpenPrivateChannel formats and opens the receiver channel`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        game.formatPlayerName = { name -> name.lowercase().replaceFirstChar { it.titlecase() } }
        val opened = mutableListOf<Pair<Player, String>>()
        game.sendOpenPrivateChannel = { owner, receiver -> opened.add(owner to receiver) }

        game.playerOpenPrivateChannel(1, "buddy")

        assertThat(opened).containsExactly(player to "Buddy")
    }

    @Test
    fun `playerOpenPrivateChannel rejects an unknown receiver`() {
        val game = newGame()
        game.player(1, "Hero")
        game.formatPlayerName = { null }
        val cancelled = mutableListOf<String>()
        game.sendCancelMessageText = { _, text -> cancelled.add(text) }

        game.playerOpenPrivateChannel(1, "missing")

        assertThat(cancelled).containsExactly("A player with this name does not exist.")
    }

    @Test
    fun `playerOpenPrivateChannel rejects the player itself`() {
        val game = newGame()
        game.player(1, "Hero")
        game.formatPlayerName = { it }
        val cancelled = mutableListOf<String>()
        game.sendCancelMessageText = { _, text -> cancelled.add(text) }

        game.playerOpenPrivateChannel(1, "Hero")

        assertThat(cancelled).containsExactly("You cannot set up a private message channel with yourself.")
    }

    @Test
    fun `playerAutoWalk resets the protection and queues the direction list`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        var loginProtectionReset = false
        var idleReset = false
        game.resetLoginProtection = { loginProtectionReset = true }
        game.resetIdleTime = { idleReset = true }

        game.playerAutoWalk(1, listOf(Direction.East, Direction.North))

        assertThat(loginProtectionReset).isTrue()
        assertThat(idleReset).isTrue()
        assertThat(player.listWalkDir).containsExactly(Direction.East, Direction.North)
    }
}
