package org.opentibia.canary.creatures.players

/**
 * Self-contained mirror of the Player action/UI timer state machine
 * (src/creatures/players/player.cpp:3084-3090, 3412-3418, 3429-3477, 8540).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). Canary
 * keeps these fields directly on `Player` (player.hpp:1674, 1742-1749); the
 * state is exposed as an injectable seam so the host player can delegate
 * without a global (AGENTS.md 1.1). Every external dependency is a constructor
 * seam: [now] replaces `OTSYS_TIME()` (player.cpp:3412, 3429, ...), the
 * `client` sink replaces `Player::client` (player.cpp:2314-2316, 2333-2338),
 * [clearAttackedCreature] replaces `Player::setAttackedCreature(nullptr)`
 * (player.cpp:2324) and the logout callbacks replace
 * `g_creatureEvents().playerLogout` / `g_game().removeCreature`
 * (player.cpp:2329-2337).
 *
 * The methods keep the exact `Player::` names of the C++ symbols they
 * transcribe, so every behavior is auditable against the C++ line references.
 */
class PlayerActionTimers(
    /** `OTSYS_TIME()` (`src/utils/time.hpp`); milliseconds. */
    private val now: () -> Long = { System.currentTimeMillis() },
    /** `Player::client != nullptr` (player.cpp:2314, 2333). */
    private val hasClient: () -> Boolean = { false },
    /** `Player::client->sendPing()` (player.cpp:2315). */
    private val sendPingPacket: () -> Unit = {},
    /** `Player::setAttackedCreature(nullptr)` (player.cpp:2324). */
    private val clearAttackedCreature: () -> Unit = {},
    /** `Player::canLogout()` (player.cpp:2329). */
    private val canLogout: () -> Boolean = { false },
    /** `g_creatureEvents().playerLogout(player)` (player.cpp:2329). */
    private val playerLogoutEvent: () -> Boolean = { false },
    /** `Player::client->logout(true, true)` (player.cpp:2332). */
    private val logout: () -> Unit = {},
    /** `g_game().removeCreature(player, true)` (player.cpp:2334). */
    private val removeCreature: () -> Unit = {},
    /** `g_logger().info(...)` (player.cpp:2330). */
    private val logInfo: (String) -> Unit = {},
    /** `Player::getName()` (player.cpp:2330). */
    private val getPlayerName: () -> String = { "" },
    /** `Player::getAttackedCreature() != nullptr` (player.cpp:2322-2323). */
    private val hasAttackedCreature: () -> Boolean = { false },
) {

    /** `Player::lastUIInteraction` (player.hpp:1742), milliseconds. */
    private var lastUIInteraction = 0L

    /**
     * `Player::lastPing` (player.hpp:1743). Canary initializes it to
     * `OTSYS_TIME()` in the player constructor (player.cpp:147) with
     * `lastPong(lastPing)` (player.cpp:148).
     */
    private var lastPing: Long = now()

    /** `Player::lastPong` (player.hpp:1744); see [lastPing]. */
    private var lastPong: Long = lastPing

    /** `Player::nextAction` (player.hpp:1745). */
    private var nextAction = 0L

    /** `Player::nextPotionAction` (player.hpp:1746). */
    private var nextPotionAction = 0L

    /** `Player::nextNecklaceAction` (player.hpp:1747). */
    private var nextNecklaceAction = 0L

    /** `Player::nextRingAction` (player.hpp:1748). */
    private var nextRingAction = 0L

    /** `Player::loginProtectionTime` (player.hpp:1751). */
    private var loginProtectionTime = 0L

    /** `Player::shouldForceLogout` (player.hpp:1888), default true. */
    private var shouldForceLogout = true

    /** `Player::moduleDelayMap` (player.hpp:1674); `std::map<uint8_t, int64_t>`. */
    private val moduleDelayMap = sortedMapOf<Int, Long>()

    /**
     * `Player::isUIExhausted` (player.cpp:3412-3414):
     * `(OTSYS_TIME() - lastUIInteraction) < exhaustionTime`. The C++ default
     * argument is 250ms (player.hpp:1263).
     */
    fun isUIExhausted(exhaustionTime: Int = 250): Boolean = (now() - lastUIInteraction) < exhaustionTime

    /** `Player::updateUIExhausted` (player.cpp:3416-3418). */
    fun updateUIExhausted() {
        lastUIInteraction = now()
    }

    /** `Player::setNextAction` (player.cpp:3429-3433): only moves the deadline forward. */
    fun setNextAction(time: Long) {
        if (time > nextAction) {
            nextAction = time
        }
    }

    /** `Player::canDoAction` (player.cpp:3435-3437). */
    fun canDoAction(): Boolean = nextAction <= now()

    /**
     * `Player::getNextActionTime` (player.cpp:3084-3086):
     * `(uint32_t)max(SCHEDULER_MINTICKS, nextAction - OTSYS_TIME())`.
     */
    fun getNextActionTime(): Long =
        maxOf(SCHEDULER_MINTICKS, nextAction - now()) and UINT32_MAX

    /** `Player::setNextNecklaceAction` (player.cpp:3439-3443). */
    fun setNextNecklaceAction(time: Long) {
        if (time > nextNecklaceAction) {
            nextNecklaceAction = time
        }
    }

    /** `Player::setNextRingAction` (player.cpp:3445-3449). */
    fun setNextRingAction(time: Long) {
        if (time > nextRingAction) {
            nextRingAction = time
        }
    }

    /** `Player::canEquipNecklace` (player.cpp:3451-3453). */
    fun canEquipNecklace(): Boolean = now() >= nextNecklaceAction

    /** `Player::canEquipRing` (player.cpp:3455-3457). */
    fun canEquipRing(): Boolean = now() >= nextRingAction

    /** `Player::setNextPotionAction` (player.cpp:3459-3463). */
    fun setNextPotionAction(time: Long) {
        if (time > nextPotionAction) {
            nextPotionAction = time
        }
    }

    /** `Player::canDoPotionAction` (player.cpp:3465-3467). */
    fun canDoPotionAction(): Boolean = nextPotionAction <= now()

    /**
     * `Player::getNextPotionActionTime` (player.cpp:3088-3090):
     * `(uint32_t)max(SCHEDULER_MINTICKS, nextPotionAction - OTSYS_TIME())`.
     */
    fun getNextPotionActionTime(): Long =
        maxOf(SCHEDULER_MINTICKS, nextPotionAction - now()) and UINT32_MAX

    /** `Player::setLoginProtection` (player.cpp:3469-3471). */
    fun setLoginProtection(time: Long) {
        loginProtectionTime = now() + time
    }

    /** `Player::isLoginProtected` (player.cpp:3472-3474). */
    fun isLoginProtected(): Boolean = loginProtectionTime > now()

    /** `Player::resetLoginProtection` (player.cpp:3475-3477). */
    fun resetLoginProtection() {
        loginProtectionTime = 0
    }

    /**
     * `Player::setModuleDelay` (player.cpp:3078-3080). The C++ key is a
     * `uint8_t` (player.hpp:1674), so the parameter is masked like the C++
     * narrowing conversion.
     */
    fun setModuleDelay(byteortype: Int, delay: Int) {
        moduleDelayMap[byteortype and UINT8_MAX] = now() + delay
    }

    /**
     * `Player::canRunModule` (player.cpp:3082-3085): an unknown key reads the
     * map's default-constructed 0, which is always <= `OTSYS_TIME()`.
     */
    fun canRunModule(byteortype: Int): Boolean {
        val delay = moduleDelayMap[byteortype and UINT8_MAX] ?: 0L
        if (delay == 0L) {
            return true
        }
        return delay <= now()
    }

    /** `Player::receivePing` (player.cpp:8540-8542). */
    fun receivePing() {
        lastPong = now()
    }

    /**
     * `Player::sendPing` (player.cpp:2308-2345). The packet is rate limited to
     * one send per 5000ms; a lost connection or a 10000ms no-pong window drops
     * the attacked creature, and a 60000ms no-pong window forces the logout
     * unless `canLogout`/`playerLogout` veto it (which clears
     * `shouldForceLogout` for good).
     */
    fun sendPing() {
        val timeNow = now()

        var hasLostConnection = false
        if ((timeNow - lastPing) >= PING_INTERVAL_MS) {
            lastPing = timeNow
            if (hasClient()) {
                sendPingPacket()
            } else {
                hasLostConnection = true
            }
        }

        val noPongTime = timeNow - lastPong
        if ((hasLostConnection || noPongTime >= PONG_TIMEOUT_MS) && hasAttackedCreature()) {
            clearAttackedCreature()
        }

        if (noPongTime >= FORCE_LOGOUT_MS && shouldForceLogout) {
            if (canLogout() && playerLogoutEvent()) {
                logInfo("Player ${getPlayerName()} has been kicked due to ping timeout. (has client: ${hasClient()})")
                if (hasClient()) {
                    logout()
                } else {
                    removeCreature()
                }
            } else {
                shouldForceLogout = false
            }
        }
    }

    companion object {
        /** `SCHEDULER_MINTICKS` (`src/game/scheduling/dispatcher.hpp:25`). */
        const val SCHEDULER_MINTICKS = 50L

        /** `Player::sendPing` rate limit (player.cpp:2312). */
        private const val PING_INTERVAL_MS = 5_000L

        /** `Player::sendPing` attacked-creature drop window (player.cpp:2321). */
        private const val PONG_TIMEOUT_MS = 10_000L

        /** `Player::sendPing` forced-logout window (player.cpp:2327). */
        private const val FORCE_LOGOUT_MS = 60_000L

        /** `std::numeric_limits<uint8_t>::max()` (player.hpp:1674). */
        private const val UINT8_MAX = 0xFF

        /** `std::numeric_limits<uint32_t>::max()` for the `uint32_t` casts. */
        private const val UINT32_MAX = 0xFFFF_FFFFL
    }
}
