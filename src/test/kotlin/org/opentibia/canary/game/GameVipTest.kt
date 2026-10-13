package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.players.vip.InMemoryVipRepository
import org.opentibia.canary.creatures.players.vip.PlayerVip
import org.opentibia.canary.creatures.players.vip.VipStatus
import org.opentibia.canary.database.GuidNameEx
import org.opentibia.canary.database.PlayerLoginRepository
import org.opentibia.canary.database.PlayerRecord
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass

/**
 * Transcript tests for the `Game` VIP handlers:
 * `Game::playerRequestAddVip` (game.cpp:7058), `Game::playerRequestRemoveVip`
 * (game.cpp:7098) and `Game::playerRequestEditVip` (game.cpp:7107).
 *
 * `Player::vip()` is composed through the constructor-injected [Game] `playerVip`
 * seam (player.hpp:739) and `IOLoginData::getGuidByNameEx` through the
 * [PlayerLoginRepository]; `Player::isInGhostMode` (player.cpp:568) is a seam.
 */
class GameVipTest {

    private val center = Position(1000, 1000, 7)

    private lateinit var vips: MutableMap<Long, PlayerVip>

    private fun newGame(
        loginRepository: PlayerLoginRepository? = null,
        isInGhostMode: (Player) -> Boolean = { false },
    ): Game {
        vips = mutableMapOf()
        return Game(
            GameMap(),
            GameDispatcher(),
            loginRepository = loginRepository,
            playerVip = { player -> vips[player.id] },
            isInGhostMode = isInGhostMode,
        )
    }

    private fun player(id: Long, name: String, position: Position = center): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position)

    private fun vipFor(player: Player): PlayerVip =
        PlayerVip(repository = InMemoryVipRepository()).also { vips[player.id] = it }

    private fun messagesOf(player: Player): MutableList<Pair<MessageClass, String>> {
        val messages = mutableListOf<Pair<MessageClass, String>>()
        player.sendTextMessageTyped = { type, text -> messages.add(type to text) }
        return messages
    }

    private class StubLoginRepository(private val record: GuidNameEx?) : PlayerLoginRepository {
        override fun loadRecordById(id: Long): PlayerRecord? = null
        override fun loadRecordByName(name: String): PlayerRecord? = null
        override fun getNameByGuid(guid: Long): String = ""
        override fun getGuidByName(name: String): Long = record?.guid ?: 0
        override fun getGuidByNameEx(name: String): GuidNameEx? = record
        override fun formatPlayerName(name: String): String? = record?.name
        override fun increaseBankBalance(guid: Long, amount: Long): Boolean = false
        override fun savePlayerRecord(player: Player): Boolean = false
    }

    // ----- playerRequestAddVip -----

    @Test
    fun `playerRequestAddVip rejects a name longer than 25 characters`() {
        val game = newGame()
        val player = player(1, "Knight")
        game.addPlayer(player)
        val messages = messagesOf(player)

        game.playerRequestAddVip(1, "A".repeat(26))

        assertThat(messages).isEmpty()
    }

    @Test
    fun `playerRequestAddVip reports an unknown name`() {
        val game = newGame()
        val player = player(1, "Knight")
        game.addPlayer(player)
        vipFor(player)
        val messages = messagesOf(player)

        game.playerRequestAddVip(1, "Missing")

        assertThat(messages).containsExactly(
            MessageClass.MESSAGE_FAILURE to "A player with this name does not exist.",
        )
        assertThat(vips[1]!!.getVipEntries()).isEmpty()
    }

    @Test
    fun `playerRequestAddVip adds an online player with his current status`() {
        val game = newGame()
        val player = player(1, "Knight")
        val vipPlayer = player(2, "Druid")
        game.addPlayer(player)
        game.addPlayer(vipPlayer)
        val vip = vipFor(player)
        vipFor(vipPlayer)

        game.playerRequestAddVip(1, "Druid")

        assertThat(vip.getVipEntries()).hasSize(1)
        assertThat(vip.getVipEntries().single().guid).isEqualTo(2)
        assertThat(vip.getVipEntries().single().name).isEqualTo("Druid")
        assertThat(vip.getVipEntries().single().status).isEqualTo(VipStatus.Online)
    }

    @Test
    fun `playerRequestAddVip refuses a special vip player to a regular player`() {
        val game = newGame()
        val player = player(1, "Knight")
        val vipPlayer = player(2, "Gamemaster")
        vipPlayer.setFlag(PlayerFlags_t.SpecialVIP)
        game.addPlayer(player)
        game.addPlayer(vipPlayer)
        val vip = vipFor(player)
        vipFor(vipPlayer)
        val messages = messagesOf(player)

        game.playerRequestAddVip(1, "Gamemaster")

        assertThat(messages).containsExactly(MessageClass.MESSAGE_FAILURE to "You can not add this player")
        assertThat(vip.getVipEntries()).isEmpty()
    }

    @Test
    fun `playerRequestAddVip hides a ghost player as offline`() {
        val game = newGame(isInGhostMode = { it.id == 2L })
        val player = player(1, "Knight")
        val vipPlayer = player(2, "Ghost")
        game.addPlayer(player)
        game.addPlayer(vipPlayer)
        val vip = vipFor(player)
        vipFor(vipPlayer)

        game.playerRequestAddVip(1, "Ghost")

        assertThat(vip.getVipEntries().single().status).isEqualTo(VipStatus.Offline)
    }

    @Test
    fun `playerRequestAddVip resolves an offline player through the login repository`() {
        val game = newGame(loginRepository = StubLoginRepository(GuidNameEx(guid = 42, name = "Offline", specialVip = false)))
        val player = player(1, "Knight")
        game.addPlayer(player)
        val vip = vipFor(player)

        game.playerRequestAddVip(1, "offline")

        assertThat(vip.getVipEntries().single().guid).isEqualTo(42)
        assertThat(vip.getVipEntries().single().name).isEqualTo("Offline")
        assertThat(vip.getVipEntries().single().status).isEqualTo(VipStatus.Offline)
    }

    @Test
    fun `playerRequestAddVip ignores an unknown player id`() {
        val game = newGame()

        game.playerRequestAddVip(99, "Knight")

        assertThat(vips).isEmpty()
    }

    // ----- playerRequestRemoveVip -----

    @Test
    fun `playerRequestRemoveVip drops the entry`() {
        val game = newGame()
        val player = player(1, "Knight")
        game.addPlayer(player)
        val vip = vipFor(player)
        vip.add(2, "Druid", VipStatus.Online)

        game.playerRequestRemoveVip(1, 2)

        assertThat(vip.getVipEntries()).isEmpty()
    }

    // ----- playerRequestEditVip -----

    @Test
    fun `playerRequestEditVip updates description icon and notify`() {
        val game = newGame()
        val player = player(1, "Knight")
        game.addPlayer(player)
        val vip = vipFor(player)
        vip.add(2, "Druid", VipStatus.Online)

        game.playerRequestEditVip(1, 2, "Best friend", icon = 3, notify = true, vipGroupsId = emptyList())

        val entry = vip.getVipEntries().single()
        assertThat(entry.description).isEqualTo("Best friend")
        assertThat(entry.icon).isEqualTo(3)
        assertThat(entry.notify).isTrue()
    }
}
