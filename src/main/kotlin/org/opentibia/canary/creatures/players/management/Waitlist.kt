package org.opentibia.canary.creatures.players.management

import org.opentibia.canary.account.AccountType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t

/**
 * `struct Wait` (src/creatures/players/management/waitlist.hpp:16-22): one
 * queued login with its millisecond timeout and the waiting player guid.
 */
class Wait(
    var timeout: Long,
    val playerGUID: Long,
)

/**
 * Port of the `std::pair<WaitList::iterator, std::size_t>` value stored in
 * `WaitListInfo::playerReferences` (waitlist.hpp:30). [wait] is the entry the
 * C++ iterator points at and [slot] the cached client slot.
 */
class WaitReference(
    val wait: Wait,
    var slot: Long,
)

/**
 * `struct WaitListInfo` (src/creatures/players/management/waitlist.hpp:26-31).
 * Both lists keep their insertion order (`std::list`).
 */
class WaitListInfo {
    val priorityWaitList = mutableListOf<Wait>()
    val waitList = mutableListOf<Wait>()
    val playerReferences = mutableMapOf<Long, WaitReference>()
}

/**
 * Mirror of Canary `WaitingList` (src/creatures/players/management/waitlist.hpp:33-46,
 * waitlist.cpp:18-134), the login queue that `ProtocolGame::login` consults
 * (protocolgame.cpp:1005-1008).
 *
 * AGENTS.md 1.1 replaces `WaitingList::getInstance()` and the
 * `g_configManager()`/`g_game()` reads with constructor-injected seams:
 * [maxPlayers] is `MAX_PLAYERS`, [playersOnline] is `Game::getPlayersOnline`,
 * [now] is `OTSYS_TIME()` (milliseconds) and [logDebug] is `g_logger().debug`.
 *
 * PARITY NOTES:
 *  - `clientLogin` removes a promoted waiter from the list that actually holds
 *    it (priority or regular). Canary erases the stored iterator from
 *    `info->waitList` unconditionally (waitlist.cpp:88), which corrupts the
 *    priority list when a premium waiter is admitted (undefined behaviour); the
 *    port keeps the intended unlink and the observable slot numbering.
 *  - `getTime`/`getTimeout` keep `std::size_t` slot numbering as [Long].
 */
class WaitingList(
    private val maxPlayers: () -> Int = { 0 },
    private val playersOnline: () -> Int = { 0 },
    private val now: () -> Long = { System.currentTimeMillis() },
    private val logDebug: (String) -> Unit = {},
) {
    private val info = WaitListInfo()

    /**
     * `WaitingList::clientLogin` (waitlist.cpp:69-93). Returns `true` when the
     * player may enter the game; otherwise the player is queued (or stays
     * queued) and the caller sends `getClientSlot`/`getTime`.
     */
    fun clientLogin(player: Player): Boolean {
        if (player.hasFlag(PlayerFlags_t.CanAlwaysLogin) ||
            player.getAccountType().value >= AccountType.ACCOUNT_TYPE_GAMEMASTER.value
        ) {
            return true
        }

        val maxPlayers = maxPlayers()
        if (maxPlayers == 0 ||
            (info.priorityWaitList.isEmpty() && info.waitList.isEmpty() && playersOnline() < maxPlayers)
        ) {
            return true
        }

        cleanupList(info.priorityWaitList)
        cleanupList(info.waitList)

        addPlayerToList(player)

        val reference = info.playerReferences.getValue(player.getGUID())
        if (playersOnline() + reference.slot <= maxPlayers) {
            // Should be able to login now.
            if (player.isPremium()) {
                info.priorityWaitList.remove(reference.wait)
            } else {
                info.waitList.remove(reference.wait)
            }
            info.playerReferences.remove(player.getGUID())
            return true
        }
        return false
    }

    /** `WaitingList::getClientSlot` (waitlist.cpp:121-133); 0 when not queued. */
    fun getClientSlot(player: Player): Long {
        val reference = info.playerReferences[player.getGUID()] ?: return 0

        return if (player.isPremium()) {
            info.priorityWaitList.indexOf(reference.wait) + 1L
        } else {
            info.priorityWaitList.size.toLong() + info.waitList.indexOf(reference.wait) + 1L
        }
    }

    /** `WaitingList::cleanupList` (waitlist.cpp:33-47). */
    private fun cleanupList(list: MutableList<Wait>) {
        val time = now()

        val iterator = list.iterator()
        while (iterator.hasNext()) {
            val wait = iterator.next()
            logDebug("[cleanupList] time: ${wait.timeout - time}")
            if (wait.timeout - time <= 0) {
                info.playerReferences.remove(wait.playerGUID)
                iterator.remove()
            }
        }
    }

    /** `WaitingList::getTimeout` (waitlist.cpp:49-51). */
    private fun getTimeout(slot: Long): Long = getTime(slot) + TIMEOUT_EXTRA

    /** `WaitingList::addPlayerToList` (waitlist.cpp:95-119). */
    private fun addPlayerToList(player: Player) {
        val guid = player.getGUID()
        val reference = info.playerReferences[guid]
        if (reference != null) {
            val slot = if (player.isPremium()) {
                info.priorityWaitList.indexOf(reference.wait) + 1L
            } else {
                info.priorityWaitList.size.toLong() + info.waitList.indexOf(reference.wait) + 1L
            }
            reference.slot = slot
            reference.wait.timeout = now() + getTimeout(slot) * 1000L
        } else if (player.isPremium()) {
            val slot = info.priorityWaitList.size.toLong()
            val wait = Wait(now() + getTimeout(slot + 1) * 1000L, guid)
            info.priorityWaitList.add(wait)
            info.playerReferences[guid] = WaitReference(wait, slot + 1)
        } else {
            val slot = info.priorityWaitList.size.toLong() + info.waitList.size
            val wait = Wait(now() + getTimeout(slot + 1) * 1000L, guid)
            info.waitList.add(wait)
            info.playerReferences[guid] = WaitReference(wait, slot + 1)
        }
    }

    companion object {
        /** `SLOT_LIMIT_ONE` (waitlist.cpp:20). */
        const val SLOT_LIMIT_ONE = 5L

        /** `SLOT_LIMIT_TWO` (waitlist.cpp:21). */
        const val SLOT_LIMIT_TWO = 10L

        /** `SLOT_LIMIT_THREE` (waitlist.cpp:22). */
        const val SLOT_LIMIT_THREE = 20L

        /** `SLOT_LIMIT_FOUR` (waitlist.cpp:23). */
        const val SLOT_LIMIT_FOUR = 50L

        /** `TIMEOUT_EXTRA` (waitlist.cpp:24), seconds. */
        const val TIMEOUT_EXTRA = 15L

        /** `WaitingList::getTime` (waitlist.cpp:53-67), seconds per slot. */
        fun getTime(slot: Long): Long = when {
            slot < SLOT_LIMIT_ONE -> 5
            slot < SLOT_LIMIT_TWO -> 10
            slot < SLOT_LIMIT_THREE -> 20
            slot < SLOT_LIMIT_FOUR -> 60
            else -> 120
        }
    }
}
