package org.opentibia.canary.creatures.players.components

import org.opentibia.canary.account.Account
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.kv.KV
import org.opentibia.canary.kv.KVMemory
import org.opentibia.canary.kv.ValueWrapper

/**
 * `enum class CyclopediaBadge_t : uint8_t`
 * (`src/enums/player_cyclopedia.hpp:16-23`).
 */
enum class CyclopediaBadge_t(val value: Int) {
    ACCOUNT_AGE(1),
    LOYALTY(2),
    ACCOUNT_ALL_LEVEL(3),
    ACCOUNT_ALL_VOCATIONS(4),
    TOURNAMENT_PARTICIPATION(5),
    TOURNAMENT_POINTS(6),
}

/**
 * `struct Badge` (`player_badge.hpp:18-32`).
 *
 * PARITY NOTE: C++ value-initializes `m_type` to the invalid enumerator 0 in
 * the default constructor; the port models that sentinel as `null`, and the
 * registry lookups never return a default-constructed badge for a known id.
 */
data class Badge(
    val id: Int = 0,
    val type: CyclopediaBadge_t? = null,
    val name: String = "",
    val amount: Int = 0,
)

/**
 * `Game::getBadgeById/getBadgeByName/getBadges` (game.cpp:12536-12564) and the
 * `m_badges` registry seeded in `Game::Game` (game.cpp:570-597).
 *
 * PARITY NOTE: C++ stores the registry in a `std::unordered_set<Badge>`, so
 * iteration order is unspecified; the port preserves registration order to
 * make [PlayerBadge.checkAndUpdateNewBadges] deterministic.
 */
interface BadgeRegistry {
    /** `Game::getBadgeById` (game.cpp:12540-12551); `Badge()` (id 0) when absent. */
    fun getBadgeById(id: Int): Badge

    /** `Game::getBadgeByName` (game.cpp:12553-12564). */
    fun getBadgeByName(name: String): Badge

    /** `Game::getBadges` (game.cpp:12536-12538). */
    fun getBadges(): List<Badge>
}

/** Registry stand-in for local runs and tests. */
class InMemoryBadgeRegistry(badges: List<Badge> = emptyList()) : BadgeRegistry {

    private val badges = badges.toList()

    override fun getBadgeById(id: Int): Badge {
        if (id == 0) {
            return Badge()
        }
        return badges.firstOrNull { it.id == id } ?: Badge()
    }

    override fun getBadgeByName(name: String): Badge {
        if (name.isEmpty()) {
            return Badge()
        }
        return badges.firstOrNull { it.name == name } ?: Badge()
    }

    override fun getBadges(): List<Badge> = badges
}

/**
 * One resolved account character, mirroring the temporary `Player` object
 * `PlayerBadge::getPlayersInfoByAccount` builds (player_badge.cpp:139-145):
 * name, level and the CIP vocation id returned by `getPlayerVocationEnum()`.
 *
 * PARITY NOTE: the adapter folds `Player::setVocation` (which drops unknown
 * vocation ids) and `getPlayerVocationEnum` into this record, because the
 * port's `Account`/`Player` models do not carry the vocation registry through
 * the query result.
 */
data class BadgeAccountPlayer(
    val name: String,
    val level: Int,
    val vocation: Int,
)

/**
 * Seam for `PlayerBadge::getPlayersInfoByAccount` (player_badge.cpp:119-149):
 * `Account::getAccountPlayers()` (account.cpp:272-276) yields the character
 * names, and the implementation executes the
 * `SELECT name, level, vocation FROM players WHERE name IN (...)` query,
 * returning `{}` when the account is not loaded or has no characters.
 */
fun interface BadgeAccountRepository {
    fun getPlayersInfoByAccount(account: Account): List<BadgeAccountPlayer>
}

/**
 * Injected `Player` view for the badge component. Every member transcribes the
 * exact `Player` symbol `PlayerBadge` calls, with the C++ line cited.
 */
interface BadgePlayer {
    /** `Player::getName()` (player_badge.cpp:96, 109). */
    fun getName(): String

    /** `Player::getLoyaltyPoints()` (player_badge.cpp:112, 116). */
    fun getLoyaltyPoints(): Int

    /** `Player::getAccount()` (player_badge.cpp:152, 163). */
    fun getAccount(): Account?
}

/**
 * Mirror of Canary `PlayerBadge`
 * (`src/creatures/players/components/player_badge.hpp:41-65`,
 * player_badge.cpp:19-189).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). Global
 * and player dependencies are constructor-injected seams (AGENTS.md 1.1):
 * [kv] replaces `m_player.kv()`, [registry] replaces `g_game().getBadge*` and
 * [accountRepository] replaces the account-player query.
 *
 * PARITY NOTES:
 *  - [loadUnlockedBadges] appends to the unlocked vector without clearing it,
 *    exactly like C++ (player_badge.cpp:86-100), so the trailing call inside
 *    [checkAndUpdateNewBadges] re-adds freshly granted badges. C++ has no
 *    accessor for that vector, so the duplication is internal-only.
 *  - [getPlayersInfoByAccount] accepts a nullable account; C++ dereferences
 *    `m_player.getAccount()` without a null check (player_badge.cpp:152), the
 *    port returns `{}` instead (hardening).
 *  - The C++ `uint16_t total` accumulator of [accountAllLevel] wraps modulo
 *    65536 (player_badge.cpp:153-155); the port reproduces the truncation.
 */
class PlayerBadge(
    private val player: BadgePlayer,
    /** `m_player.kv()` — the per-player KV root (player_badge.cpp:104). */
    private val kv: KV = KVMemory(),
    /** `g_game().getBadgeById/getBadgeByName/getBadges` (player_badge.cpp:42, 55, 90). */
    private val registry: BadgeRegistry = InMemoryBadgeRegistry(),
    /** Account character query seam (player_badge.cpp:119-149, 152). */
    private val accountRepository: BadgeAccountRepository = BadgeAccountRepository { emptyList() },
    /** `OTSYS_TIME() / 1000` (player_badge.cpp:47). */
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
    /** `g_logger().debug` (player_badge.cpp:88, 96). */
    private val logDebug: (message: String) -> Unit = {},
    /** `g_logger().error` (player_badge.cpp:92). */
    private val logError: (message: String) -> Unit = {},
) {

    /** `m_badgesUnlocked` — `{Badge, time when it was unlocked}` (player_badge.hpp:63). */
    private val badgesUnlocked = mutableListOf<Pair<Badge, Long>>()

    /** `m_badgeUnlockedKV` (player_badge.hpp:62). */
    private var badgeUnlockedKV: KV? = null

    /** `PlayerBadge::hasBadge` (player_badge.cpp:22-35). */
    fun hasBadge(id: Int): Boolean {
        if (id == 0) {
            return false
        }

        return badgesUnlocked.any { it.first.id == id }
    }

    /** `PlayerBadge::add` (player_badge.cpp:37-52). */
    fun add(id: Int, timestamp: Long = 0): Boolean {
        if (hasBadge(id)) {
            return false
        }

        val badge = registry.getBadgeById(id)
        if (badge.id == 0) {
            return false
        }

        val toSaveTimestamp = if (timestamp != 0L) timestamp else now()
        getUnlockedKV().set(badge.name, ValueWrapper(toSaveTimestamp.toInt()))
        badgesUnlocked.add(badge to toSaveTimestamp)
        return true
    }

    /** `PlayerBadge::checkAndUpdateNewBadges` (player_badge.cpp:54-84). */
    fun checkAndUpdateNewBadges() {
        for (badge in registry.getBadges()) {
            when (badge.type) {
                CyclopediaBadge_t.ACCOUNT_AGE -> {
                    if (accountAge(badge.amount)) {
                        add(badge.id)
                    }
                }
                CyclopediaBadge_t.LOYALTY -> {
                    if (loyalty(badge.amount)) {
                        add(badge.id)
                    }
                }
                CyclopediaBadge_t.ACCOUNT_ALL_LEVEL -> {
                    if (accountAllLevel(badge.amount)) {
                        add(badge.id)
                    }
                }
                CyclopediaBadge_t.ACCOUNT_ALL_VOCATIONS -> {
                    if (accountAllVocations(badge.amount)) {
                        add(badge.id)
                    }
                }
                CyclopediaBadge_t.TOURNAMENT_PARTICIPATION,
                CyclopediaBadge_t.TOURNAMENT_POINTS,
                -> {
                    // Canary has no check for tournament badges (player_badge.cpp:77-79).
                }
                null -> {
                    // C++ value-initialized `m_type` sentinel; never a registry badge.
                }
            }
        }

        loadUnlockedBadges()
    }

    /**
     * `PlayerBadge::loadUnlockedBadges` (player_badge.cpp:86-100).
     *
     * PARITY NOTE: like C++ this appends to [badgesUnlocked] without clearing
     * it. C++ reads `getUnlockedKV()->get(badgeName)->getNumber()` without a
     * null check; the port treats a key that disappeared between `keys()` and
     * `get()` as 0 (hardening).
     */
    fun loadUnlockedBadges() {
        val unlockedBadges = getUnlockedKV().keys()
        logDebug("[loadUnlockedBadges] - Loading unlocked badges: ${unlockedBadges.size}")
        for (badgeName in unlockedBadges) {
            val badge = registry.getBadgeByName(badgeName)
            if (badge.id == 0) {
                logError("[loadUnlockedBadges] - Badge $badgeName not found.")
                continue
            }

            logDebug("[loadUnlockedBadges] - Badge ${badge.name} found for player ${player.getName()}.")
            badgesUnlocked.add(badge to (getUnlockedKV().get(badgeName)?.getNumber()?.toLong() ?: 0L))
        }
    }

    /** `PlayerBadge::getUnlockedKV` (player_badge.cpp:102-108). */
    fun getUnlockedKV(): KV =
        badgeUnlockedKV ?: kv.scoped("badges").scoped("unlocked").also { badgeUnlockedKV = it }

    /** `PlayerBadge::accountAge` (player_badge.cpp:111-113). */
    fun accountAge(amount: Int): Boolean = player.getLoyaltyPoints() / 365 >= amount

    /** `PlayerBadge::loyalty` (player_badge.cpp:115-117). */
    fun loyalty(amount: Int): Boolean = player.getLoyaltyPoints() >= amount

    /** `PlayerBadge::getPlayersInfoByAccount` (player_badge.cpp:119-149). */
    fun getPlayersInfoByAccount(acc: Account?): List<BadgeAccountPlayer> {
        if (acc == null) {
            return emptyList()
        }
        return accountRepository.getPlayersInfoByAccount(acc)
    }

    /**
     * `PlayerBadge::accountAllLevel` (player_badge.cpp:151-157). The C++
     * `uint16_t total` accumulator wraps modulo 65536; the port masks each
     * addition with `and 0xFFFF` to reproduce it.
     */
    fun accountAllLevel(amount: Int): Boolean {
        var total = 0
        for (accountPlayer in getPlayersInfoByAccount(player.getAccount())) {
            total = (total + accountPlayer.level) and 0xFFFF
        }
        return total >= amount
    }

    /** `PlayerBadge::accountAllVocations` (player_badge.cpp:159-179). */
    fun accountAllVocations(amount: Int): Boolean {
        var knight = false
        var paladin = false
        var druid = false
        var sorcerer = false
        for (accountPlayer in getPlayersInfoByAccount(player.getAccount())) {
            if (accountPlayer.level >= amount) {
                when (accountPlayer.vocation) {
                    Vocation.VOCATION_KNIGHT_CIP -> knight = true
                    Vocation.VOCATION_SORCERER_CIP -> sorcerer = true
                    Vocation.VOCATION_PALADIN_CIP -> paladin = true
                    Vocation.VOCATION_DRUID_CIP -> druid = true
                }
            }
        }
        return knight && paladin && druid && sorcerer
    }

    /** `PlayerBadge::tournamentParticipation` (player_badge.cpp:181-184) — Canary TODO. */
    fun tournamentParticipation(skill: Int): Boolean = false

    /** `PlayerBadge::tournamentPoints` (player_badge.cpp:186-189) — Canary TODO. */
    fun tournamentPoints(race: Int): Boolean = false
}
