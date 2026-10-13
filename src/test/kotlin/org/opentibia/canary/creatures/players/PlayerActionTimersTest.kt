package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcribes the Player action/UI timer state
 * (src/creatures/players/player.cpp:3084-3090, 3412-3418, 3429-3477, 8540):
 *
 * - `Player::isUIExhausted` / `updateUIExhausted` (player.cpp:3412-3418)
 * - `Player::setNextAction` / `canDoAction` / `getNextActionTime`
 *   (player.cpp:3084-3086, 3429-3437)
 * - `Player::setNextPotionAction` / `canDoPotionAction` / `getNextPotionActionTime`
 *   (player.cpp:3088-3090, 3459-3467)
 * - `Player::setNextNecklaceAction` / `canEquipNecklace`
 *   (player.cpp:3439-3453)
 * - `Player::setNextRingAction` / `canEquipRing` (player.cpp:3445-3457)
 * - `Player::setLoginProtection` / `isLoginProtected` / `resetLoginProtection`
 *   (player.cpp:3469-3477)
 * - `Player::setModuleDelay` / `canRunModule` (player.cpp:3078-3085)
 * - `Player::receivePing` (player.cpp:8540-8542)
 * - `Player::sendPing` (player.cpp:2308-2345)
 *
 * `OTSYS_TIME()` is the injected [PlayerActionTimers] clock; `SCHEDULER_MINTICKS`
 * is 50 (src/game/scheduling/dispatcher.hpp:25).
 */
class PlayerActionTimersTest {

    private var now = 0L

    private class PingSink {
        var hasClient = false
        var pingsSent = 0
        var clearedAttacked = 0
        var canLogout = false
        var logoutEvent = false
        var loggedOut = 0
        var removed = 0
        val logs = mutableListOf<String>()
    }

    private fun newTimers(sink: PingSink = PingSink(), name: () -> String = { "Ping Hero" }) = PlayerActionTimers(
        now = { now },
        hasClient = { sink.hasClient },
        sendPingPacket = { sink.pingsSent++ },
        clearAttackedCreature = { sink.clearedAttacked++ },
        canLogout = { sink.canLogout },
        playerLogoutEvent = { sink.logoutEvent },
        logout = { sink.loggedOut++ },
        removeCreature = { sink.removed++ },
        logInfo = { sink.logs.add(it) },
        getPlayerName = name,
    )

    @Test
    fun `ui exhaustion window is strictly smaller than the configured time`() {
        // Player::isUIExhausted (player.cpp:3412-3414): default 250ms.
        val timers = newTimers()
        now = 1_000
        timers.updateUIExhausted()

        now = 1_249
        assertThat(timers.isUIExhausted()).isTrue()

        now = 1_250
        assertThat(timers.isUIExhausted()).isFalse()
    }

    @Test
    fun `ui exhaustion accepts an explicit window`() {
        // Player::isUIExhausted(uint32_t exhaustionTime) (player.cpp:3412).
        val timers = newTimers()
        now = 5_000
        timers.updateUIExhausted()

        now = 5_300
        assertThat(timers.isUIExhausted(500)).isTrue()
        assertThat(timers.isUIExhausted(100)).isFalse()
    }

    @Test
    fun `next action only moves forward and gates action use`() {
        // Player::setNextAction (player.cpp:3429-3433) and Player::canDoAction (3435-3437).
        val timers = newTimers()
        now = 1_000

        timers.setNextAction(1_400)
        assertThat(timers.canDoAction()).isFalse()

        timers.setNextAction(1_200)
        assertThat(timers.getNextActionTime()).isEqualTo(400)

        now = 1_400
        assertThat(timers.canDoAction()).isTrue()
    }

    @Test
    fun `next action time is floored at SCHEDULER_MINTICKS`() {
        // Player::getNextActionTime (player.cpp:3084-3086): max(50, nextAction - now).
        val timers = newTimers()
        now = 1_000
        timers.setNextAction(1_030)

        assertThat(timers.getNextActionTime()).isEqualTo(PlayerActionTimers.SCHEDULER_MINTICKS)

        timers.setNextAction(1_200)
        assertThat(timers.getNextActionTime()).isEqualTo(200)
    }

    @Test
    fun `next potion action mirrors the action timer`() {
        // Player::setNextPotionAction (player.cpp:3459-3463), canDoPotionAction (3465-3467),
        // getNextPotionActionTime (3088-3090).
        val timers = newTimers()
        now = 2_000

        timers.setNextPotionAction(2_500)
        assertThat(timers.canDoPotionAction()).isFalse()
        assertThat(timers.getNextPotionActionTime()).isEqualTo(500)

        // The deadline only moves forward (player.cpp:3459-3463).
        timers.setNextPotionAction(2_100)
        assertThat(timers.getNextPotionActionTime()).isEqualTo(500)

        now = 2_500
        assertThat(timers.canDoPotionAction()).isTrue()
    }

    @Test
    fun `necklace and ring actions gate equipment`() {
        // Player::setNextNecklaceAction (player.cpp:3439-3443), canEquipNecklace (3449-3451),
        // setNextRingAction (3445-3449), canEquipRing (3453-3455).
        val timers = newTimers()
        now = 3_000

        timers.setNextNecklaceAction(3_600)
        timers.setNextRingAction(3_800)

        assertThat(timers.canEquipNecklace()).isFalse()
        assertThat(timers.canEquipRing()).isFalse()

        now = 3_600
        assertThat(timers.canEquipNecklace()).isTrue()
        assertThat(timers.canEquipRing()).isFalse()

        now = 3_800
        assertThat(timers.canEquipRing()).isTrue()
    }

    @Test
    fun `login protection window and reset`() {
        // Player::setLoginProtection (player.cpp:3469-3471), isLoginProtected (3472-3474),
        // resetLoginProtection (3475-3477).
        val timers = newTimers()
        now = 10_000

        timers.setLoginProtection(5_000)
        assertThat(timers.isLoginProtected()).isTrue()

        now = 15_000
        assertThat(timers.isLoginProtected()).isFalse()

        timers.setLoginProtection(1_000)
        timers.resetLoginProtection()
        assertThat(timers.isLoginProtected()).isFalse()
    }

    @Test
    fun `module delay gates repeated use per byteortype`() {
        // Player::setModuleDelay (player.cpp:3078-3080) and canRunModule (3082-3085).
        val timers = newTimers()
        now = 1_000

        assertThat(timers.canRunModule(3)).isTrue()

        timers.setModuleDelay(3, 500)
        assertThat(timers.canRunModule(3)).isFalse()
        assertThat(timers.canRunModule(4)).isTrue()

        now = 1_500
        assertThat(timers.canRunModule(3)).isTrue()
    }

    @Test
    fun `module delay byteortype is a uint8`() {
        // The C++ map key is `uint8_t byteortype` (player.hpp:1674).
        val timers = newTimers()
        now = 1_000
        timers.setModuleDelay(0x103, 500)

        assertThat(timers.canRunModule(3)).isFalse()
    }

    @Test
    fun `receivePing refreshes the pong time`() {
        // Player::receivePing (player.cpp:8540-8542) extends the no-pong window
        // checked by sendPing (player.cpp:2321-2325).
        val sink = PingSink()
        sink.hasClient = true
        var attacked = true
        val timers = PlayerActionTimers(
            now = { now },
            hasClient = { sink.hasClient },
            sendPingPacket = { sink.pingsSent++ },
            clearAttackedCreature = { sink.clearedAttacked++; attacked = false },
            canLogout = { sink.canLogout },
            playerLogoutEvent = { sink.logoutEvent },
            logout = { sink.loggedOut++ },
            removeCreature = { sink.removed++ },
            logInfo = { sink.logs.add(it) },
            getPlayerName = { "Ping Hero" },
            hasAttackedCreature = { attacked },
        )

        now = 4_000
        timers.receivePing()

        // 9_999ms after the pong is still inside the 10s window.
        now = 13_999
        timers.sendPing()
        assertThat(sink.clearedAttacked).isZero()

        now = 14_000
        timers.sendPing()
        assertThat(sink.clearedAttacked).isEqualTo(1)
    }

    @Test
    fun `sendPing rate limits the packet to five seconds`() {
        // Player::sendPing (player.cpp:2308-2316): (timeNow - lastPing) >= 5000.
        val sink = PingSink()
        sink.hasClient = true
        val timers = newTimers(sink)
        now = 5_000
        timers.sendPing()

        now = 9_999
        timers.sendPing()
        assertThat(sink.pingsSent).isEqualTo(1)

        now = 10_000
        timers.sendPing()
        assertThat(sink.pingsSent).isEqualTo(2)
    }

    @Test
    fun `sendPing clears the attacked creature when the connection was lost`() {
        // player.cpp:2318-2325: hasLostConnection with a live target clears it.
        val sink = PingSink()
        val timers = newTimers(sink)
        var attacked = true
        val withTarget = PlayerActionTimers(
            now = { now },
            hasClient = { sink.hasClient },
            sendPingPacket = { sink.pingsSent++ },
            clearAttackedCreature = { sink.clearedAttacked++; attacked = false },
            canLogout = { sink.canLogout },
            playerLogoutEvent = { sink.logoutEvent },
            logout = { sink.loggedOut++ },
            removeCreature = { sink.removed++ },
            logInfo = { sink.logs.add(it) },
            getPlayerName = { "Ping Hero" },
            hasAttackedCreature = { attacked },
        )

        now = 5_000
        withTarget.sendPing()

        assertThat(sink.clearedAttacked).isEqualTo(1)
        assertThat(attacked).isFalse()
    }

    @Test
    fun `sendPing clears the attacked creature after ten seconds without pong`() {
        // player.cpp:2321-2325: noPongTime >= 10000 also clears the target.
        val sink = PingSink()
        sink.hasClient = true
        var attacked = true
        val timers = PlayerActionTimers(
            now = { now },
            hasClient = { sink.hasClient },
            sendPingPacket = { sink.pingsSent++ },
            clearAttackedCreature = { sink.clearedAttacked++; attacked = false },
            canLogout = { sink.canLogout },
            playerLogoutEvent = { sink.logoutEvent },
            logout = { sink.loggedOut++ },
            removeCreature = { sink.removed++ },
            logInfo = { sink.logs.add(it) },
            getPlayerName = { "Ping Hero" },
            hasAttackedCreature = { attacked },
        )
        now = 5_000
        timers.sendPing()
        sink.pingsSent = 0

        now = 10_000
        timers.sendPing()

        assertThat(sink.clearedAttacked).isEqualTo(1)
        assertThat(attacked).isFalse()
    }

    @Test
    fun `sendPing forces a client logout after a minute without pong`() {
        // player.cpp:2327-2340: noPongTime >= 60000 with shouldForceLogout.
        val sink = PingSink()
        sink.hasClient = true
        sink.canLogout = true
        sink.logoutEvent = true
        val timers = newTimers(sink)

        now = 60_000
        timers.sendPing()

        assertThat(sink.loggedOut).isEqualTo(1)
        assertThat(sink.removed).isZero()
        assertThat(sink.logs.single())
            .isEqualTo("Player Ping Hero has been kicked due to ping timeout. (has client: true)")
    }

    @Test
    fun `sendPing removes the creature when a clientless player times out`() {
        // player.cpp:2331-2338: without a client the game removes the creature.
        val sink = PingSink()
        sink.canLogout = true
        sink.logoutEvent = true
        val timers = newTimers(sink)

        now = 60_000
        timers.sendPing()

        assertThat(sink.loggedOut).isZero()
        assertThat(sink.removed).isEqualTo(1)
    }

    @Test
    fun `sendPing clears shouldForceLogout when the logout is vetoed`() {
        // player.cpp:2327-2337: a failed canLogout/playerLogout disables the kick.
        val sink = PingSink()
        sink.hasClient = true
        sink.canLogout = false
        val timers = newTimers(sink)

        now = 60_000
        timers.sendPing()
        assertThat(sink.loggedOut).isZero()

        // The flag is now false, so a later timeout no longer attempts a logout
        // even when the veto is lifted.
        sink.canLogout = true
        sink.logoutEvent = true
        now = 130_000
        timers.sendPing()

        assertThat(sink.loggedOut).isZero()
    }
}
