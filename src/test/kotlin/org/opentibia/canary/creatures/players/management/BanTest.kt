package org.opentibia.canary.creatures.players.management

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for `Ban`
 * (src/creatures/players/management/ban.hpp:18-43, ban.cpp:23-78).
 */
class BanTest {

    private class Clock(var now: Long = 0) : () -> Long {
        override fun invoke(): Long = now

        fun advance(millis: Long) {
            now += millis
        }
    }

    private fun trackedIpCount(ban: Ban): Int {
        val field = Ban::class.java.getDeclaredField("ipConnectMap")
        field.isAccessible = true
        return (field.get(ban) as Map<*, *>).size
    }

    @Test
    fun `acceptConnection admits a new ip and records the first attempt`() {
        val clock = Clock(1_000)

        assertThat(Ban(clock).acceptConnection(0x0A000001L)).isTrue()
    }

    @Test
    fun `acceptConnection admits five attempts inside the burst window`() {
        val clock = Clock(0)
        val ban = Ban(clock)

        repeat(5) {
            assertThat(ban.acceptConnection(1L)).isTrue()
            clock.advance(100)
        }
    }

    @Test
    fun `acceptConnection blocks the sixth rapid attempt for three seconds`() {
        val clock = Clock(0)
        val ban = Ban(clock)

        repeat(5) {
            assertThat(ban.acceptConnection(1L)).isTrue()
            clock.advance(100)
        }

        // t=500: timeDiff=100 <= 500 -> count overflows -> blockTime = 3500.
        assertThat(ban.acceptConnection(1L)).isFalse()

        clock.advance(2_999)
        assertThat(ban.acceptConnection(1L)).isFalse()

        // blockTime has been extended by 250 per rejected attempt: 3750+250=4000.
        clock.advance(1_001)
        assertThat(ban.acceptConnection(1L)).isTrue()
    }

    @Test
    fun `acceptConnection extends an active block by 250ms per rejected attempt`() {
        val clock = Clock(0)
        val ban = Ban(clock)

        repeat(5) {
            ban.acceptConnection(1L)
            clock.advance(100)
        }
        // Blocked at t=500 with blockTime=3500.
        assertThat(ban.acceptConnection(1L)).isFalse()

        clock.advance(1_000)
        assertThat(ban.acceptConnection(1L)).isFalse()
        clock.advance(1_000)
        assertThat(ban.acceptConnection(1L)).isFalse()

        // Each rejection pushed blockTime past the current attempt:
        // 3500 -> 3750 (t=1500) -> 4000 (t=2500); the call at t=3001 still
        // sees 4000 > 3001 and extends the block again to 4250.
        clock.advance(501)
        assertThat(ban.acceptConnection(1L)).isFalse()
        clock.advance(1_250)
        assertThat(ban.acceptConnection(1L)).isTrue()
    }

    @Test
    fun `acceptConnection admits a sixth attempt when the burst is not rapid`() {
        val clock = Clock(0)
        val ban = Ban(clock)

        repeat(5) {
            assertThat(ban.acceptConnection(1L)).isTrue()
            clock.advance(1_000)
        }

        // t=5000, timeDiff=1000 > 500: the count resets instead of blocking.
        assertThat(ban.acceptConnection(1L)).isTrue()
    }

    @Test
    fun `acceptConnection resets the count after the burst window elapses`() {
        val clock = Clock(0)
        val ban = Ban(clock)

        repeat(3) {
            assertThat(ban.acceptConnection(1L)).isTrue()
            clock.advance(100)
        }

        clock.advance(Ban.CONNECT_BLOCK_BURST_WINDOW)
        assertThat(ban.acceptConnection(1L)).isTrue()

        // The window reset the count to 1; four more rapid attempts stay admitted.
        clock.advance(100)
        repeat(4) {
            assertThat(ban.acceptConnection(1L)).isTrue()
            clock.advance(100)
        }

        // The seventh rapid attempt overflows the count and is blocked.
        assertThat(ban.acceptConnection(1L)).isFalse()
    }

    @Test
    fun `acceptConnection tracks each ip independently`() {
        val clock = Clock(0)
        val ban = Ban(clock)

        repeat(6) {
            ban.acceptConnection(1L)
        }

        assertThat(ban.acceptConnection(2L)).isTrue()
        assertThat(ban.acceptConnection(1L)).isFalse()
    }

    @Test
    fun `acceptConnection prunes at most the budget of stale entries per call`() {
        val clock = Clock(0)
        val ban = Ban(clock)
        for (ip in 1L..40L) {
            ban.acceptConnection(ip)
        }
        assertThat(trackedIpCount(ban)).isEqualTo(40)

        clock.advance(Ban.CONNECT_BLOCK_BURST_WINDOW + 1)
        ban.acceptConnection(1_000L)

        assertThat(trackedIpCount(ban)).isEqualTo(40 - Ban.PRUNE_BUDGET + 1)

        clock.advance(Ban.CONNECT_BLOCK_BURST_WINDOW + 1)
        ban.acceptConnection(2_000L)

        assertThat(trackedIpCount(ban)).isEqualTo(1)
    }

    @Test
    fun `acceptConnection keeps an attempt recorded in the future when the clock steps back`() {
        val clock = Clock(10_000)
        val ban = Ban(clock)
        ban.acceptConnection(7L)

        clock.now = 1_000
        ban.acceptConnection(8L)

        assertThat(trackedIpCount(ban)).isEqualTo(2)
    }
}
