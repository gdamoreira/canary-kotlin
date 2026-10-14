package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountInfo
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.Faction
import org.opentibia.canary.creatures.players.vip.VipStatus
import org.opentibia.canary.map.Position

/**
 * Transcribes the Player social/VIP helpers
 * (src/creatures/players/player.cpp:4509-4525, 6234-6240, 7395-7401,
 * 7544-7554, 12617-12633):
 *
 * - `Player::getFaction` / `setFaction` (player.cpp:6234-6240)
 * - `Player::getGuildNick` / `setGuildNick` (player.cpp:7395-7401)
 * - `Player::getPremiumDays` (player.cpp:7544-7546)
 * - `Player::getPremiumLastDay` (player.cpp:7548-7550)
 * - `Player::isVip` (player.cpp:7552-7554)
 * - `Player::removeList` / `addList` (player.cpp:4511-4525)
 * - `Player::setAccount` (player.cpp:12617-12625)
 * - `Player::getAccountId` (player.cpp:12631-12633)
 *
 * `Player::guildNick` defaults to the empty string (player.hpp:1708) and
 * `Player::faction` defaults to `FACTION_PLAYER` (player.hpp:1858).
 * `VIP_SYSTEM_ENABLED` defaults to false (configmanager.cpp:168). The
 * `g_game()` player registry, `Player::vip().notifyStatusChange` and
 * `g_logger().warn` are call-site seams per AGENTS.md 1.1.
 */
class PlayerSocialStateTest {

    private fun newPlayer() = Player(
        id = 7,
        name = "Social Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    @Test
    fun `guild nick defaults to empty and round trips`() {
        // player.hpp:1708 / player.cpp:7395-7401.
        val state = PlayerSocialState()

        assertThat(state.getGuildNick()).isEmpty()

        state.setGuildNick("The Legend")
        assertThat(state.getGuildNick()).isEqualTo("The Legend")
    }

    @Test
    fun `faction defaults to FACTION_PLAYER and round trips`() {
        // player.hpp:1858 / player.cpp:6234-6240.
        val state = PlayerSocialState()

        assertThat(state.getFaction()).isEqualTo(Faction.Player)

        state.setFaction(Faction.Lion)
        assertThat(state.getFaction()).isEqualTo(Faction.Lion)
    }

    @Test
    fun `premium helpers read the account`() {
        // Player::getPremiumDays / getPremiumLastDay (player.cpp:7544-7550).
        val player = newPlayer()
        val now = 1_000_000L
        player.account = Account(
            id = 1,
            name = "acc",
            premiumLastDay = now + PREMIUM_DAYS * 86_400,
        )

        assertThat(player.getPremiumDays(now)).isEqualTo(PREMIUM_DAYS)
        assertThat(player.getPremiumLastDay()).isEqualTo(now + PREMIUM_DAYS * 86_400)
    }

    @Test
    fun `premium helpers are zero without an account`() {
        // The C++ dereferences `account`; the port keeps the safe default.
        val player = newPlayer()

        assertThat(player.getPremiumDays(1_000_000L)).isZero()
        assertThat(player.getPremiumLastDay()).isZero()
    }

    @Test
    fun `getAccountId reads the account and falls back to zero`() {
        // Player::getAccountId (player.cpp:12631-12633).
        val player = newPlayer()

        assertThat(player.getAccountId()).isZero()

        player.account = Account(id = 77, name = "acc")
        assertThat(player.getAccountId()).isEqualTo(77)
    }

    @Test
    fun `isVip requires the enabled system and premium time`() {
        // Player::isVip (player.cpp:7552-7554).
        val player = newPlayer()
        val now = 2_000_000L
        player.account = Account(id = 1, name = "acc", premiumLastDay = now + 86_400)

        assertThat(player.isVip(vipSystemEnabled = false, now = now)).isFalse()
        assertThat(player.isVip(vipSystemEnabled = true, now = now)).isTrue()

        player.account = null
        assertThat(player.isVip(vipSystemEnabled = true, now = now)).isFalse()
    }

    // --- Player::setAccount (player.cpp:12617-12625) -----------------------------

    @Test
    fun `setAccount binds a fresh account and reports the load result`() {
        // player.cpp:12617-12625: the new Account is stored before load().
        val player = newPlayer()

        val loaded = player.setAccount(
            accountId = 42,
            accountFactory = { id -> Account(id = id, repository = LoadByIdAccountRepository) },
        )

        assertThat(loaded).isTrue()
        assertThat(player.account?.id).isEqualTo(42)
        assertThat(player.account?.accLoaded).isTrue()
    }

    @Test
    fun `setAccount keeps the binding when the account fails to load`() {
        // `account = std::make_shared<Account>(accountId)` runs before load();
        // a failed load still leaves the account assigned.
        val player = newPlayer()

        val loaded = player.setAccount(accountId = 7)

        assertThat(loaded).isFalse()
        assertThat(player.account?.id).isEqualTo(7)
        assertThat(player.account?.accLoaded).isFalse()
    }

    @Test
    fun `setAccount warns and keeps an already bound account`() {
        // player.cpp:12618-12621.
        val player = newPlayer()
        player.account = Account(id = 9, name = "existing")
        var warning: String? = null

        val result = player.setAccount(
            accountId = 10,
            accountFactory = { throw AssertionError("the factory must not run") },
            logWarn = { warning = it },
        )

        assertThat(result).isTrue()
        assertThat(player.account?.id).isEqualTo(9)
        assertThat(warning).isEqualTo("Account was already set!")
    }

    // --- Player::removeList / addList (player.cpp:4511-4525) ---------------------

    @Test
    fun `removeList removes the player and marks every vip entry offline`() {
        // player.cpp:4511-4517.
        val player = newPlayer()
        val other = newPlayer()
        val removed = mutableListOf<Player>()
        val notifications = mutableListOf<Triple<Player, Player, VipStatus>>()

        player.removeList(
            removePlayerFromGame = { removed.add(it) },
            gamePlayers = { listOf(player, other) },
            notifyVipStatusChange = { recipient, loginPlayer, status ->
                notifications.add(Triple(recipient, loginPlayer, status))
            },
        )

        assertThat(removed).containsExactly(player)
        assertThat(notifications).containsExactly(
            Triple(player, player, VipStatus.Offline),
            Triple(other, player, VipStatus.Offline),
        )
    }

    @Test
    fun `addList announces the current vip status then registers the player`() {
        // player.cpp:4519-4525.
        val player = newPlayer()
        val other = newPlayer()
        val added = mutableListOf<Player>()
        val notifications = mutableListOf<Triple<Player, Player, VipStatus>>()

        player.addList(
            gamePlayers = { listOf(other) },
            vipStatus = { VipStatus.Training },
            notifyVipStatusChange = { recipient, loginPlayer, status ->
                notifications.add(Triple(recipient, loginPlayer, status))
            },
            addPlayerToGame = { added.add(it) },
        )

        assertThat(notifications).containsExactly(Triple(other, player, VipStatus.Training))
        assertThat(added).containsExactly(player)
    }

    private companion object {
        const val PREMIUM_DAYS = 5
    }
}

/** `AccountRepository::loadByID` success stub for the [Player]::setAccount transcript. */
private object LoadByIdAccountRepository : AccountRepository {
    override fun findByAccountName(name: String): Account? = null

    override fun authenticate(name: String, password: String): Account? = null

    override fun loadByID(id: Long, acc: AccountInfo): Boolean = true
}
