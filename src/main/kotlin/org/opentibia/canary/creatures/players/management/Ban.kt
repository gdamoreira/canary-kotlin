package org.opentibia.canary.creatures.players.management

import java.util.TreeMap

/**
 * `struct BanInfo` (src/creatures/players/management/ban.hpp:12-16). Filled by
 * `IOBan::isAccountBanned`/`IOBan::isIpBanned` when a ban is active.
 */
data class BanInfo(
    var bannedBy: String = "",
    var reason: String = "",
    var expiresAt: Long = 0,
)

/**
 * `struct ConnectBlock` (src/creatures/players/management/ban.hpp:18-25):
 * one per-IP rate-limit record.
 */
class ConnectBlock(
    var lastAttempt: Long,
    var blockTime: Long,
    var count: Int,
)

/**
 * Mirror of Canary `Ban` (src/creatures/players/management/ban.hpp:29-43,
 * ban.cpp:23-78).
 *
 * Connection-level, per-IP login throttling used by `Server::onAccept`
 * (server.cpp:102). [now] replaces `OTSYS_TIME()` so the limiter is
 * deterministic in tests; [ipConnectMap] is keyed by the client ip
 * (`uint32_t` in C++) and kept in ascending key order like `std::map`.
 *
 * PARITY NOTE: Canary serialises every call under a `std::recursive_mutex`;
 * the port keeps the same mutual exclusion, and pruning already runs with the
 * lock held, so the recursion is not needed.
 */
class Ban(
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val ipConnectMap = TreeMap<Long, ConnectBlock>()
    private var pruneCursor = 0L

    /**
     * `Ban::acceptConnection` (ban.cpp:46-78). Returns `false` while the ip is
     * inside a burst block, extending the block by 250ms per rejected attempt.
     */
    @Synchronized
    fun acceptConnection(clientIP: Long): Boolean {
        val currentTime = now()
        pruneStaleEntries(currentTime)

        val connectBlock = ipConnectMap[clientIP]
        if (connectBlock == null) {
            ipConnectMap[clientIP] = ConnectBlock(currentTime, 0, 1)
            return true
        }

        if (connectBlock.blockTime > currentTime) {
            connectBlock.blockTime += 250
            return false
        }

        val timeDiff = currentTime - connectBlock.lastAttempt
        connectBlock.lastAttempt = currentTime
        if (timeDiff <= 5000) {
            if (++connectBlock.count > 5) {
                connectBlock.count = 0
                if (timeDiff <= 500) {
                    connectBlock.blockTime = currentTime + 3000
                    return false
                }
            }
        } else {
            connectBlock.count = 1
        }
        return true
    }

    /**
     * `Ban::pruneStaleEntries` (ban.cpp:23-44). Incremental: examines at most
     * [PRUNE_BUDGET] entries, resuming where the previous call stopped and
     * wrapping at the end, so the time spent under the lock stays bounded no
     * matter how large the map has grown.
     *
     * An entry is stale once its block has expired and its last attempt fell
     * out of the 5s burst window. An attempt recorded "in the future" (the
     * system clock stepped backward) is recent and is kept.
     */
    private fun pruneStaleEntries(currentTime: Long) {
        var key = ipConnectMap.ceilingKey(pruneCursor)
        val steps = minOf(PRUNE_BUDGET, ipConnectMap.size)
        for (examined in 0 until steps) {
            if (key == null) {
                key = ipConnectMap.firstKey()
            }

            val connectBlock = ipConnectMap.getValue(key)
            val stale = connectBlock.blockTime <= currentTime &&
                connectBlock.lastAttempt <= currentTime &&
                currentTime - connectBlock.lastAttempt > CONNECT_BLOCK_BURST_WINDOW

            key = if (stale) {
                val next = ipConnectMap.higherKey(key)
                ipConnectMap.remove(key)
                next
            } else {
                ipConnectMap.higherKey(key)
            }
        }
        pruneCursor = key ?: 0L
    }

    companion object {
        /**
         * Upper bound on the entries one `acceptConnection()` examines while
         * pruning (ban.hpp:34-36).
         */
        const val PRUNE_BUDGET = 32

        /**
         * An entry can no longer influence `acceptConnection()` once its block
         * has expired and its last attempt fell out of this burst window
         * (ban.cpp:17-20).
         */
        const val CONNECT_BLOCK_BURST_WINDOW = 5000L
    }
}

/** One `account_bans` row as `IOBan::isAccountBanned` reads it (ban.cpp:83-107). */
data class AccountBanRecord(
    val reason: String = "",
    val expiresAt: Long = 0,
    val bannedAt: Long = 0,
    val bannedBy: Long = 0,
    val bannedByName: String = "",
)

/** One `ip_bans` row as `IOBan::isIpBanned` reads it (ban.cpp:117-136). */
data class IpBanRecord(
    val reason: String = "",
    val expiresAt: Long = 0,
    val bannedByName: String = "",
)

/**
 * Persistence seam replacing the `g_databaseTasks()`/`Database` calls of
 * `IOBan` (ban.cpp:80-143, AGENTS.md 1.1). [expiresAt] keeps Canary's
 * `time_t` seconds; `0` means a permanent ban. Expired bans are moved to
 * history (accounts) or deleted (ip) by the caller, exactly like the C++
 * database tasks.
 */
interface BanRepository {
    /** `SELECT reason, expires_at, banned_at, banned_by, name FROM account_bans` (ban.cpp:84). */
    fun getAccountBan(accountId: Long): AccountBanRecord?

    /** `INSERT INTO account_ban_history` (ban.cpp:95). */
    fun addAccountBanHistory(accountId: Long, reason: String, bannedAt: Long, expiredAt: Long, bannedBy: Long)

    /** `DELETE FROM account_bans` (ban.cpp:99). */
    fun deleteAccountBan(accountId: Long)

    /** `SELECT reason, expires_at, name FROM ip_bans` (ban.cpp:118). */
    fun getIpBan(clientIP: Long): IpBanRecord?

    /** `DELETE FROM ip_bans` (ban.cpp:128). */
    fun deleteIpBan(clientIP: Long)

    /** `SELECT 1 FROM player_namelocks WHERE player_id = ...` (ban.cpp:141). */
    fun isPlayerNamelocked(playerId: Long): Boolean
}

/**
 * Mirror of Canary `IOBan` (src/creatures/players/management/ban.hpp:45-50,
 * ban.cpp:80-143).
 *
 * The C++ class is a static facade over `Database`/`g_databaseTasks()`; the
 * port injects a [BanRepository] (AGENTS.md 1.1) and a wall clock so expiry is
 * deterministic. [now] is `time(nullptr)` in seconds, not the millisecond
 * `OTSYS_TIME()`.
 */
class IOBan(
    private val repository: BanRepository,
    private val now: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    /**
     * `IOBan::isAccountBanned` (ban.cpp:80-108). A timed-out ban is moved to
     * `account_ban_history` and deleted, then reported as inactive.
     */
    fun isAccountBanned(accountId: Long, banInfo: BanInfo): Boolean {
        val ban = repository.getAccountBan(accountId) ?: return false

        if (ban.expiresAt != 0L && now() > ban.expiresAt) {
            repository.addAccountBanHistory(accountId, ban.reason, ban.bannedAt, ban.expiresAt, ban.bannedBy)
            repository.deleteAccountBan(accountId)
            return false
        }

        banInfo.expiresAt = ban.expiresAt
        banInfo.reason = ban.reason
        banInfo.bannedBy = ban.bannedByName
        return true
    }

    /** `IOBan::isIpBanned` (ban.cpp:110-137). Ip zero is never banned. */
    fun isIpBanned(clientIP: Long, banInfo: BanInfo): Boolean {
        if (clientIP == 0L) {
            return false
        }

        val ban = repository.getIpBan(clientIP) ?: return false

        if (ban.expiresAt != 0L && now() > ban.expiresAt) {
            repository.deleteIpBan(clientIP)
            return false
        }

        banInfo.expiresAt = ban.expiresAt
        banInfo.reason = ban.reason
        banInfo.bannedBy = ban.bannedByName
        return true
    }

    /** `IOBan::isPlayerNamelocked` (ban.cpp:139-143). */
    fun isPlayerNamelocked(playerId: Long): Boolean = repository.isPlayerNamelocked(playerId)
}
