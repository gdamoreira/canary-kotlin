package org.opentibia.canary.creatures.players.components

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.creatures.vocations.Vocation

private val FLEDGELING_HERO = Badge(1, CyclopediaBadge_t.ACCOUNT_AGE, "Fledegeling Hero", 1)
private val VETERAN_HERO = Badge(2, CyclopediaBadge_t.ACCOUNT_AGE, "Veteran Hero", 5)
private val TIBIA_LOYALIST = Badge(6, CyclopediaBadge_t.LOYALTY, "Tibia Loyalist (Grade 1)", 100)
private val GLOBAL_PLAYER = Badge(9, CyclopediaBadge_t.ACCOUNT_ALL_LEVEL, "Global Player (Grade 1)", 500)
private val MASTER_CLASS = Badge(12, CyclopediaBadge_t.ACCOUNT_ALL_VOCATIONS, "Master Class (Grade 1)", 100)
private val FRESHMAN = Badge(15, CyclopediaBadge_t.TOURNAMENT_PARTICIPATION, "Freshman of the Tournament", 1)
private val TOURNAMENT_COMPETITOR = Badge(18, CyclopediaBadge_t.TOURNAMENT_POINTS, "Tournament Competitor", 1000)

private val ALL_BADGES = listOf(
    FLEDGELING_HERO,
    VETERAN_HERO,
    TIBIA_LOYALIST,
    GLOBAL_PLAYER,
    MASTER_CLASS,
    FRESHMAN,
    TOURNAMENT_COMPETITOR,
)

/**
 * Transcript tests for the Canary `PlayerBadge` component.
 *
 * Source of truth: /Users/damoreira/.projects/canary
 *  - src/creatures/players/components/player_badge.hpp:18-65
 *  - src/creatures/players/components/player_badge.cpp:19-189
 *  - src/game/game.cpp:12536-12564 (`Game::getBadgeById/getBadgeByName/getBadges`)
 *  - src/game/game.cpp:570-597 (badge registry data)
 *  - src/account/account.cpp:272-276 (`Account::getAccountPlayers`)
 */
class PlayerBadgesTest {

    private class FakeBadgePlayer : BadgePlayer {
        var playerName: String = "Knight"
        var playerLoyaltyPoints: Int = 0
        var playerAccount: Account? = null

        override fun getName(): String = playerName
        override fun getLoyaltyPoints(): Int = playerLoyaltyPoints
        override fun getAccount(): Account? = playerAccount
    }

    private class Fixture(
        badges: List<Badge> = ALL_BADGES,
    ) {
        val player = FakeBadgePlayer()
        val kv = InMemoryKV()
        val registry = InMemoryBadgeRegistry(badges)
        val accountPlayers = mutableMapOf<Long, List<BadgeAccountPlayer>>()
        val debugLogs = mutableListOf<String>()
        val errorLogs = mutableListOf<String>()
        var nowSeconds = 1_700_000_000L

        val badge = PlayerBadge(
            player = player,
            kv = kv,
            registry = registry,
            accountRepository = BadgeAccountRepository { account ->
                accountPlayers[account.id] ?: emptyList()
            },
            now = { nowSeconds },
            logDebug = { debugLogs.add(it) },
            logError = { errorLogs.add(it) },
        )
    }

    @Test
    fun `hasBadge returns false for id zero`() {
        assertThat(Fixture().badge.hasBadge(0)).isFalse()
    }

    @Test
    fun `add refuses an unknown badge id`() {
        val f = Fixture()

        assertThat(f.badge.add(99)).isFalse()
        assertThat(f.badge.getUnlockedKV().keys()).isEmpty()
    }

    @Test
    fun `add persists the badge with the current timestamp`() {
        val f = Fixture()

        assertThat(f.badge.add(1)).isTrue()

        assertThat(f.badge.hasBadge(1)).isTrue()
        assertThat(f.badge.getUnlockedKV().get("Fledegeling Hero")).isEqualTo(1_700_000_000L)
    }

    @Test
    fun `add uses the caller timestamp when nonzero`() {
        val f = Fixture()

        assertThat(f.badge.add(1, timestamp = 500L)).isTrue()

        assertThat(f.badge.getUnlockedKV().get("Fledegeling Hero")).isEqualTo(500L)
    }

    @Test
    fun `add refuses an already granted badge`() {
        val f = Fixture()
        f.badge.add(1)
        f.badge.getUnlockedKV().set("Fledegeling Hero", 999L)

        assertThat(f.badge.add(1)).isFalse()

        assertThat(f.badge.getUnlockedKV().get("Fledegeling Hero")).isEqualTo(999L)
    }

    @Test
    fun `getUnlockedKV scopes the unlocked entries under badges unlocked`() {
        val f = Fixture()

        f.badge.getUnlockedKV().set("Veteran Hero", 5L)

        assertThat(f.kv.scoped("badges").scoped("unlocked").get("Veteran Hero")).isEqualTo(5L)
    }

    @Test
    fun `loadUnlockedBadges resolves persisted names and logs`() {
        val f = Fixture()
        f.badge.getUnlockedKV().set("Fledegeling Hero", 111L)

        f.badge.loadUnlockedBadges()

        assertThat(f.badge.hasBadge(1)).isTrue()
        assertThat(f.debugLogs).containsExactly(
            "[loadUnlockedBadges] - Loading unlocked badges: 1",
            "[loadUnlockedBadges] - Badge Fledegeling Hero found for player Knight.",
        )
    }

    @Test
    fun `loadUnlockedBadges skips unknown names and logs an error`() {
        val f = Fixture()
        f.badge.getUnlockedKV().set("Ghost Badge", 111L)

        f.badge.loadUnlockedBadges()

        assertThat(f.badge.hasBadge(1)).isFalse()
        assertThat(f.errorLogs).containsExactly("[loadUnlockedBadges] - Badge Ghost Badge not found.")
    }

    @Test
    fun `checkAndUpdateNewBadges is idempotent for hasBadge and the stored keys`() {
        val f = Fixture(badges = listOf(FLEDGELING_HERO))
        f.player.playerLoyaltyPoints = 365

        f.badge.checkAndUpdateNewBadges()
        f.badge.checkAndUpdateNewBadges()

        assertThat(f.badge.hasBadge(1)).isTrue()
        assertThat(f.badge.getUnlockedKV().keys()).containsExactly("Fledegeling Hero")
    }

    @Test
    fun `accountAge counts full years of loyalty points`() {
        val f = Fixture()
        f.player.playerLoyaltyPoints = 364
        assertThat(f.badge.accountAge(1)).isFalse()

        f.player.playerLoyaltyPoints = 365
        assertThat(f.badge.accountAge(1)).isTrue()

        f.player.playerLoyaltyPoints = 729
        assertThat(f.badge.accountAge(2)).isFalse()

        f.player.playerLoyaltyPoints = 730
        assertThat(f.badge.accountAge(2)).isTrue()
    }

    @Test
    fun `loyalty compares the loyalty points`() {
        val f = Fixture()
        f.player.playerLoyaltyPoints = 99

        assertThat(f.badge.loyalty(100)).isFalse()
        f.player.playerLoyaltyPoints = 100
        assertThat(f.badge.loyalty(100)).isTrue()
    }

    @Test
    fun `getPlayersInfoByAccount returns the repository records for the account`() {
        val f = Fixture()
        val account = Account(id = 7L, name = "acc", passwordHash = "hash")
        f.player.playerAccount = account
        f.accountPlayers[7L] = listOf(BadgeAccountPlayer("Alice", 250, Vocation.VOCATION_KNIGHT_CIP))

        assertThat(f.badge.getPlayersInfoByAccount(account))
            .containsExactly(BadgeAccountPlayer("Alice", 250, Vocation.VOCATION_KNIGHT_CIP))
    }

    @Test
    fun `getPlayersInfoByAccount returns empty without an account`() {
        val f = Fixture()

        assertThat(f.badge.getPlayersInfoByAccount(null)).isEmpty()
    }

    @Test
    fun `accountAllLevel sums account levels and keeps the Canary uint16 truncation`() {
        val f = Fixture()
        val account = Account(id = 7L, name = "acc", passwordHash = "hash")
        f.player.playerAccount = account
        f.accountPlayers[7L] = listOf(
            BadgeAccountPlayer("Alice", 300, 0),
            BadgeAccountPlayer("Bob", 250, 0),
        )
        assertThat(f.badge.accountAllLevel(500)).isTrue()

        f.accountPlayers[7L] = listOf(
            BadgeAccountPlayer("Alice", 40_000, 0),
            BadgeAccountPlayer("Bob", 30_000, 0),
        )
        assertThat(f.badge.accountAllLevel(5_000)).isFalse()
    }

    @Test
    fun `accountAllVocations requires all four base vocations at the wanted level`() {
        val f = Fixture()
        val account = Account(id = 7L, name = "acc", passwordHash = "hash")
        f.player.playerAccount = account
        f.accountPlayers[7L] = listOf(
            BadgeAccountPlayer("Knight", 100, Vocation.VOCATION_KNIGHT_CIP),
            BadgeAccountPlayer("Paladin", 100, Vocation.VOCATION_PALADIN_CIP),
            BadgeAccountPlayer("Sorcerer", 100, Vocation.VOCATION_SORCERER_CIP),
            BadgeAccountPlayer("Druid", 99, Vocation.VOCATION_DRUID_CIP),
        )

        assertThat(f.badge.accountAllVocations(100)).isFalse()

        f.accountPlayers[7L] = listOf(
            BadgeAccountPlayer("Knight", 100, Vocation.VOCATION_KNIGHT_CIP),
            BadgeAccountPlayer("Paladin", 100, Vocation.VOCATION_PALADIN_CIP),
            BadgeAccountPlayer("Sorcerer", 100, Vocation.VOCATION_SORCERER_CIP),
            BadgeAccountPlayer("Druid", 100, Vocation.VOCATION_DRUID_CIP),
        )
        assertThat(f.badge.accountAllVocations(100)).isTrue()
    }

    @Test
    fun `checkAndUpdateNewBadges grants qualifying badges and skips tournament badges`() {
        val f = Fixture()
        val account = Account(id = 7L, name = "acc", passwordHash = "hash")
        f.player.playerAccount = account
        f.player.playerLoyaltyPoints = 365
        f.accountPlayers[7L] = listOf(
            BadgeAccountPlayer("Knight", 100, Vocation.VOCATION_KNIGHT_CIP),
            BadgeAccountPlayer("Paladin", 100, Vocation.VOCATION_PALADIN_CIP),
            BadgeAccountPlayer("Sorcerer", 100, Vocation.VOCATION_SORCERER_CIP),
            BadgeAccountPlayer("Druid", 100, Vocation.VOCATION_DRUID_CIP),
            BadgeAccountPlayer("Monk", 250, Vocation.VOCATION_MONK_CIP),
        )

        f.badge.checkAndUpdateNewBadges()

        assertThat(f.badge.hasBadge(1)).isTrue()
        assertThat(f.badge.hasBadge(6)).isTrue()
        assertThat(f.badge.hasBadge(9)).isTrue()
        assertThat(f.badge.hasBadge(12)).isTrue()
        assertThat(f.badge.hasBadge(2)).isFalse()
        assertThat(f.badge.hasBadge(15)).isFalse()
        assertThat(f.badge.hasBadge(18)).isFalse()
    }

    @Test
    fun `checkAndUpdateNewBadges persists the freshly granted badge`() {
        val f = Fixture(badges = listOf(FLEDGELING_HERO))
        f.player.playerLoyaltyPoints = 365

        f.badge.checkAndUpdateNewBadges()

        assertThat(f.badge.getUnlockedKV().get("Fledegeling Hero")).isEqualTo(1_700_000_000L)
    }

    @Test
    fun `tournamentParticipation and tournamentPoints always return false`() {
        val f = Fixture()

        assertThat(f.badge.tournamentParticipation(1)).isFalse()
        assertThat(f.badge.tournamentPoints(1)).isFalse()
    }
}
