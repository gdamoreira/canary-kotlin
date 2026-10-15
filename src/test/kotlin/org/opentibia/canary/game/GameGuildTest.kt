package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.chat.ChatClient
import org.opentibia.canary.chat.ChannelEvent
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.social.guild.Guild
import org.opentibia.canary.social.guild.InMemoryGuildRepository
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the `Game` guild members:
 * `Game::getGuild` (game.cpp:11727), `Game::getGuildByName` (game.cpp:11738),
 * `Game::addGuild` (game.cpp:11750), `Game::removeGuild` (game.cpp:11757) and
 * `Game::sendGuildMotd` (game.cpp:9793).
 */
class GameGuildTest {

    private val center = Position(1000, 1000, 7)

    private val sentChannelMessages = mutableListOf<ChannelMessage>()

    private data class ChannelMessage(
        val recipient: Player,
        val author: String,
        val text: String,
        val type: SpeakClasses,
        val channelId: Int,
    )

    private inner class CapturingChatClient : ChatClient {
        override fun sendCreatureSay(recipient: Player, speaker: Creature, type: SpeakClasses, text: String) = Unit

        override fun sendChannelMessage(
            recipient: Player,
            author: String,
            text: String,
            type: SpeakClasses,
            channelId: Int,
        ) {
            sentChannelMessages.add(ChannelMessage(recipient, author, text, type, channelId))
        }

        override fun sendChannelEvent(
            recipient: Player,
            channelId: Int,
            playerName: String,
            channelEvent: ChannelEvent,
        ) = Unit

        override fun sendToChannel(
            recipient: Player,
            speaker: Creature,
            type: SpeakClasses,
            text: String,
            channelId: Int,
        ) = Unit

        override fun sendClosePrivate(recipient: Player, channelId: Int) = Unit

        override fun sendTextMessage(recipient: Player, message: String) = Unit

        override fun sendCancelMessage(recipient: Player, returnValue: ReturnValue) = Unit
    }

    private fun newGame(
        guildRepository: InMemoryGuildRepository? = null,
        saveGuild: (Guild) -> Unit = {},
    ): Game = Game(
        GameMap(),
        GameDispatcher(),
        guildRepository = guildRepository,
        saveGuild = saveGuild,
        chatClient = CapturingChatClient(),
    )

    private fun player(id: Long, name: String, position: Position = center): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position)

    private fun guild(id: Long, name: String): Guild = Guild(id, name).also {
        it.addRank(0, "Leader", 3)
        it.addRank(1, "Vice-Leader", 2)
        it.addRank(2, "Member", 1)
    }

    // ----- addGuild / getGuild / removeGuild -----

    @Test
    fun `addGuild stores the guild by id and ignores null`() {
        val game = newGame()
        val guild = guild(1, "Red Rose")

        game.addGuild(guild)
        game.addGuild(null)

        assertThat(game.getGuilds()).isEqualTo(mapOf(1L to guild))
        assertThat(game.getGuild(1)).isSameAs(guild)
        assertThat(game.getGuild(2)).isNull()
    }

    @Test
    fun `getGuild loads an offline guild only when allowOffline is set`() {
        val repository = InMemoryGuildRepository(listOf(guild(7, "Offline Guild")))
        val game = newGame(guildRepository = repository)

        assertThat(game.getGuild(7)).isNull()
        assertThat(game.getGuild(7, allowOffline = true)?.name).isEqualTo("Offline Guild")
    }

    @Test
    fun `removeGuild saves and drops the guild`() {
        val game = newGame()
        val guild = guild(1, "Red Rose")
        val saved = mutableListOf<Guild>()
        game.addGuild(guild)
        val savingGame = newGame(saveGuild = { saved.add(it) })
        savingGame.addGuild(guild)

        savingGame.removeGuild(1)

        assertThat(saved).containsExactly(guild)
        assertThat(savingGame.getGuild(1)).isNull()
    }

    @Test
    fun `a repository loaded guild leaves the game when its last member leaves`() {
        val saved = mutableListOf<Guild>()
        lateinit var game: Game
        val repository = InMemoryGuildRepository(
            listOf(guild(7, "Wolves")),
            removeGuild = { id -> game.removeGuild(id) },
        )
        game = newGame(guildRepository = repository, saveGuild = { saved.add(it) })

        val loaded = game.getGuild(7, allowOffline = true)!!
        // `IOLoginDataLoad::loadPlayerGuild` registers the loaded guild
        // (iologindata_load_player.cpp:410-411).
        game.addGuild(loaded)
        val member = player(1, "Wolfie")
        loaded.addMember(member)

        loaded.removeMember(member)

        assertThat(game.getGuilds()).doesNotContainKey(7L)
        assertThat(saved).containsExactly(loaded)
    }

    // ----- getGuildByName -----

    @Test
    fun `getGuildByName resolves through the repository guild id`() {
        val repository = InMemoryGuildRepository(listOf(guild(5, "Blue Rose")))
        val game = newGame(guildRepository = repository)

        assertThat(game.getGuildByName("Blue Rose", allowOffline = true)?.id).isEqualTo(5)
        assertThat(game.getGuildByName("Unknown", allowOffline = true)).isNull()
    }

    // ----- sendGuildMotd -----

    @Test
    fun `sendGuildMotd sends the guild motd on the guild channel when the player has a guild`() {
        val game = newGame()
        val guild = guild(1, "Red Rose")
        guild.setMotd("Welcome to the guild!")
        val player = player(1, "Knight")
        player.setGuild(guild)
        game.addPlayer(player)

        game.sendGuildMotd(1)

        assertThat(sentChannelMessages).hasSize(1)
        assertThat(sentChannelMessages.single()).isEqualTo(
            ChannelMessage(player, "Message of the Day", "Welcome to the guild!", SpeakClasses.TALKTYPE_CHANNEL_R1, 0x00),
        )
    }

    @Test
    fun `sendGuildMotd ignores a player without a guild`() {
        val game = newGame()
        val player = player(1, "Knight")
        game.addPlayer(player)

        game.sendGuildMotd(1)

        assertThat(sentChannelMessages).isEmpty()
    }

    @Test
    fun `sendGuildMotd ignores an unknown player id`() {
        val game = newGame()

        game.sendGuildMotd(99)

        assertThat(sentChannelMessages).isEmpty()
    }
}
