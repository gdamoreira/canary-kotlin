package org.opentibia.canary.systems.imbuement

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Skill
import org.opentibia.canary.game.scheduling.GameDispatcher
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Scheduler tests for `ImbuementDecay` (src/creatures/players/imbuements/imbuements.cpp:499-634):
 * `startImbuementDecay` must arm the 1-second `DispatcherLane::Maintenance`
 * re-arming tick, `checkImbuementDecay` must decay/expire through it, and every
 * callback that outlives its owner, is superseded by a later timer, or arrives
 * after a cancellation/shutdown must be a no-op (AGENTS.md deferred-callback
 * lifetime safety).
 *
 * The dispatcher clock is injected so each timer fires deterministically.
 */
class ImbuementDecaySchedulerTest {

    private var now = 1_000L
    private lateinit var dispatcher: GameDispatcher

    private val imbuements = Imbuements().apply {
        assertThat(loadFromXml(TEST_XML)).isTrue()
    }
    private val strike = imbuements.imbuementMap.values.single { it.name == "Strike" }

    private fun startDispatcher(): GameDispatcher {
        val instance = GameDispatcher(clock = { now })
        instance.start()
        return instance
    }

    private fun newDecay(): ImbuementDecay = ImbuementDecay(imbuements, dispatcher, { now })

    private fun newItem(duration: Int): ImbuementItem {
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        val player = FakeImbuementPlayer()
        item.parent = player
        item.holdingPlayer = player
        item.setImbuement(0, strike.id, duration)
        return item
    }

    private fun durationOf(item: ImbuementItem): Int? =
        ImbuementEngine(imbuements).getImbuementInfo(item, 0)?.duration

    private fun awaitUntil(message: String, timeoutMs: Long = 3_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(10)
        }
        fail<Unit>(message)
    }

    /** Runs a no-op on the world lane so any in-flight maintenance tick has completed. */
    private fun awaitLane() {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun `startImbuementDecay arms one bounded tick and decays on the scheduled check`() {
        dispatcher = startDispatcher()
        try {
            val decay = newDecay()
            val item = newItem(duration = 100)

            decay.startImbuementDecay(item)

            assertThat(decay.hasScheduledCheck).isTrue()
            val generation = decay.scheduledGeneration

            decay.startImbuementDecay(item)
            assertThat(decay.scheduledGeneration).isEqualTo(generation)

            now += 5_000
            dispatcher.wake()

            awaitUntil("imbuement did not decay") { durationOf(item) == 95 }
            assertThat(decay.hasScheduledCheck).isTrue()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `startImbuementDecay does not arm a tick for items without imbuements`() {
        dispatcher = startDispatcher()
        try {
            val decay = newDecay()
            val item = ImbuementItem(
                id = 2400,
                imbuementSlot = 1,
                imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
            )

            decay.startImbuementDecay(item)

            assertThat(decay.hasScheduledCheck).isFalse()
            assertThat(decay.trackedItemCount).isZero()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `the scheduled check expires the imbuement and removes its stats`() {
        dispatcher = startDispatcher()
        try {
            val decay = newDecay()
            val item = newItem(duration = 10)
            val player = item.holdingPlayer as FakeImbuementPlayer
            val engine = ImbuementEngine(imbuements, decay)
            engine.addItemImbuementStats(strike, player)
            decay.startImbuementDecay(item)

            now += 10_000
            dispatcher.wake()

            awaitUntil("imbuement did not expire") { engine.getImbuementInfo(item, 0) == null }
            assertThat(player.skillBonuses[Skill.SKILL_CRITICAL_HIT_DAMAGE]).isEqualTo(0)
            assertThat(player.skillBonuses[Skill.SKILL_CRITICAL_HIT_CHANCE]).isEqualTo(0)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `the tick stops re-arming once the tracker empties`() {
        dispatcher = startDispatcher()
        try {
            val decay = newDecay()
            val item = newItem(duration = 10)
            decay.startImbuementDecay(item)

            now += 10_000
            dispatcher.wake()
            awaitUntil("imbuement did not expire") { item.getImbuementAttribute(0) == 0L }

            now += 1_000
            dispatcher.wake()
            awaitUntil("tracked item was not pruned") { decay.trackedItemCount == 0 }
            awaitLane()

            assertThat(decay.hasScheduledCheck).isFalse()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `stopImbuementDecay cancels the pending tick`() {
        dispatcher = startDispatcher()
        try {
            val decay = newDecay()
            val item = newItem(duration = 100)
            decay.startImbuementDecay(item)
            assertThat(decay.hasScheduledCheck).isTrue()

            decay.stopImbuementDecay(item)

            assertThat(decay.hasScheduledCheck).isFalse()
            now += 60_000
            dispatcher.wake()
            Thread.sleep(100)
            assertThat(durationOf(item)).isEqualTo(100)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `a stale scheduled generation is a no-op`() {
        dispatcher = startDispatcher()
        try {
            val decay = newDecay()
            val item = newItem(duration = 100)
            decay.startImbuementDecay(item)
            val liveGeneration = decay.scheduledGeneration

            decay.runScheduledCheck(0L)
            assertThat(durationOf(item)).isEqualTo(100)

            now += 5_000
            decay.runScheduledCheck(liveGeneration)
            assertThat(durationOf(item)).isEqualTo(95)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `a tick queued before cancellation is a no-op`() {
        dispatcher = startDispatcher()
        try {
            val decay = newDecay()
            val cancelled = newItem(duration = 100)
            decay.startImbuementDecay(cancelled)
            val cancelledGeneration = decay.scheduledGeneration
            decay.stopImbuementDecay(cancelled)

            val live = newItem(duration = 100)
            decay.startImbuementDecay(live)

            decay.runScheduledCheck(cancelledGeneration)
            assertThat(durationOf(live)).isEqualTo(100)

            now += 5_000
            decay.runScheduledCheck(decay.scheduledGeneration)
            assertThat(durationOf(live)).isEqualTo(95)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `shutdown cancels the scheduled tick and is idempotent`() {
        dispatcher = startDispatcher()
        try {
            val decay = newDecay()
            val item = newItem(duration = 100)
            decay.startImbuementDecay(item)
            assertThat(decay.hasScheduledCheck).isTrue()

            decay.shutdown()
            decay.shutdown()

            assertThat(decay.hasScheduledCheck).isFalse()
            assertThat(decay.trackedItemCount).isZero()

            now += 60_000
            dispatcher.wake()
            Thread.sleep(100)
            assertThat(durationOf(item)).isEqualTo(100)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `a huge elapsed interval expires the imbuement without overflow`() {
        dispatcher = startDispatcher()
        try {
            val decay = newDecay()
            val item = newItem(duration = 100)
            decay.startImbuementDecay(item)

            now += 3_000_000_000_000L
            dispatcher.wake()

            awaitUntil("imbuement did not expire") { durationOf(item) == null }
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `without a dispatcher the decay stays caller-driven`() {
        val decay = ImbuementDecay(imbuements, clock = { now })
        val item = newItem(duration = 100)

        decay.startImbuementDecay(item)

        assertThat(decay.hasScheduledCheck).isFalse()
        now += 5_000
        decay.checkImbuementDecay()
        assertThat(durationOf(item)).isEqualTo(95)
    }

    @Test
    fun `scheduled callback is inert after its owner is collected`() {
        dispatcher = startDispatcher()
        try {
            val item = newItem(duration = 100)
            val reference = armOrphanDecay(item)

            assertThat(awaitCollected(reference)).isTrue()

            now += 5_000
            dispatcher.wake()
            Thread.sleep(100)
            assertThat(durationOf(item)).isEqualTo(100)
        } finally {
            dispatcher.shutdown()
        }
    }

    /** Keeps the only strong reference inside this frame so the orphan can be collected. */
    private fun armOrphanDecay(item: ImbuementItem): WeakReference<ImbuementDecay> {
        val orphan = ImbuementDecay(imbuements, dispatcher, { now })
        orphan.startImbuementDecay(item)
        return WeakReference(orphan)
    }

    private fun awaitCollected(owner: WeakReference<*>): Boolean {
        repeat(100) {
            System.gc()
            if (owner.get() == null) {
                return true
            }
            Thread.sleep(10)
        }
        return owner.get() == null
    }
}

private const val TEST_XML = """
<imbuements>
    <base id="1" name="Basic" price="5000" protectionPrice="10000" percent="90" removecost="15000" duration="72000"/>
    <category id="3" name="Critical Hit" agressive="1"/>
    <imbuement name="Strike" base="1" category="3" iconid="1">
        <attribute key="effect" type="skill" value="critical" bonus="1500" chance="1000"/>
        <attribute key="item" value="11444" count="20"/>
    </imbuement>
</imbuements>
"""
