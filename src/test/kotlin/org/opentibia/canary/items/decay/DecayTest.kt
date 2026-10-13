package org.opentibia.canary.items.decay

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemDecayState
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Transcript tests for `Decay` (src/items/decay/decay.cpp:21-224) and the
 * `Item::canDecay`/`Item::isRemoved` guards it consults
 * (src/items/item.cpp:859-864, 3438-3457). The dispatcher clock is injected so
 * every timer fires deterministically (AGENTS.md deferred-callback rules).
 */
class DecayTest {

    private var now = 1_000L
    private lateinit var dispatcher: GameDispatcher
    private lateinit var decay: Decay
    private val transforms = CopyOnWriteArrayList<Pair<Item, Int>>()
    private val removals = CopyOnWriteArrayList<Item>()

    private fun startDispatcher(): GameDispatcher {
        val instance = GameDispatcher(clock = { now })
        instance.start()
        return instance
    }

    private fun newDecay(): Decay = Decay(
        dispatcher = dispatcher,
        now = { now },
        internalRemoveItem = { item ->
            removals.add(item)
            ReturnValue.NOERROR
        },
        transformItem = { item, id -> transforms.add(item to id) },
    )

    private fun attachedItem(
        id: Int = 100,
        type: ItemType,
    ): Item = Item(id = id, type = type).apply {
        parent = Tile(Position(100, 100, 7))
    }

    private fun decayingType(
        id: Int = 100,
        decayTo: Int = 200,
        decayTime: Int = 1_000,
    ): ItemType = ItemType(
        id = id,
        name = "decaying item",
        isMovable = true,
        decayTime = decayTime,
        decayTo = decayTo,
    )

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

    private fun setup(decayTime: Int = 1_000, decayTo: Int = 200): Pair<GameDispatcher, Item> {
        dispatcher = startDispatcher()
        decay = newDecay()
        val item = attachedItem(type = decayingType(decayTime = decayTime, decayTo = decayTo))
        item.setDuration(decayTime)
        return dispatcher to item
    }

    @Test
    fun `startDecay stamps the timestamp and enters the decaying state`() {
        val dispatcher = setup(decayTime = 1_000).first
        try {
            val item = attachedItem(type = decayingType(decayTime = 1_000))
            item.setDuration(1_000)

            decay.startDecay(item)

            assertThat(item.getDecaying()).isEqualTo(ItemDecayState.DECAYING_TRUE)
            assertThat(item.getAttribute(ItemAttribute.DURATION_TIMESTAMP)).isEqualTo(2_000L)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `checkDecay transforms the item when the clock reaches the timestamp`() {
        val dispatcher = setup(decayTime = 1_000, decayTo = 200).first
        try {
            val item = attachedItem(type = decayingType(decayTime = 1_000, decayTo = 200))
            item.setDuration(1_000)
            decay.startDecay(item)

            now = 2_000
            dispatcher.wake()
            awaitUntil("item was not decayed") { transforms.size == 1 }

            assertThat(transforms.single().first).isSameAs(item)
            assertThat(transforms.single().second).isEqualTo(200)
            assertThat(item.getDecaying()).isEqualTo(ItemDecayState.DECAYING_FALSE)
            assertThat(item.hasAttribute(ItemAttribute.DURATION_TIMESTAMP)).isFalse()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `duration zero decays immediately`() {
        val dispatcher = setup(decayTime = 1_000, decayTo = 200).first
        try {
            val item = attachedItem(type = decayingType(decayTime = 5_000, decayTo = 200))
            item.setDuration(0)

            decay.startDecay(item)

            assertThat(transforms.single().first).isSameAs(item)
            assertThat(transforms.single().second).isEqualTo(200)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `items whose type cannot decay are ignored`() {
        val dispatcher = setup().first
        try {
            val item = attachedItem(type = decayingType(decayTo = -1))
            item.setDuration(1_000)

            decay.startDecay(item)

            assertThat(item.getDecaying()).isEqualTo(ItemDecayState.DECAYING_FALSE)
            assertThat(item.hasAttribute(ItemAttribute.DURATION_TIMESTAMP)).isFalse()
            now = 10_000
            dispatcher.wake()
            Thread.sleep(100)
            assertThat(transforms).isEmpty()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `unique id items cannot decay`() {
        val dispatcher = setup().first
        try {
            val item = attachedItem(type = decayingType())
            item.setDuration(1_000)
            item.setUniqueId(7)

            decay.startDecay(item)

            assertThat(item.canDecay()).isFalse()
            assertThat(item.getDecaying()).isEqualTo(ItemDecayState.DECAYING_FALSE)
            now = 10_000
            dispatcher.wake()
            Thread.sleep(100)
            assertThat(transforms).isEmpty()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `removed items cannot decay`() {
        val dispatcher = setup().first
        try {
            val detached = Item(id = 100, type = decayingType())
            detached.setDuration(1_000)

            assertThat(detached.canDecay()).isFalse()
            decay.startDecay(detached)

            assertThat(detached.getDecaying()).isEqualTo(ItemDecayState.DECAYING_FALSE)
            now = 10_000
            dispatcher.wake()
            Thread.sleep(100)
            assertThat(transforms).isEmpty()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `decayTo zero removes the item instead of transforming it`() {
        val dispatcher = setup(decayTo = 0).first
        try {
            val item = attachedItem(type = decayingType(decayTo = 0))
            item.setDuration(1_000)
            decay.startDecay(item)

            now = 2_000
            dispatcher.wake()
            awaitUntil("item was not removed") { removals.size == 1 }

            assertThat(removals.single()).isSameAs(item)
            assertThat(transforms).isEmpty()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `map loaded items are not removed when decayTo is zero`() {
        val dispatcher = setup(decayTo = 0).first
        try {
            val item = attachedItem(type = decayingType(decayTo = 0)).apply { loadedFromMap = true }
            item.setDuration(1_000)
            decay.startDecay(item)

            now = 2_000
            dispatcher.wake()
            Thread.sleep(100)

            assertThat(removals).isEmpty()
            assertThat(transforms).isEmpty()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `items that transform into themselves on equip are removed`() {
        val dispatcher = setup().first
        try {
            val type = decayingType(id = 100, decayTo = 200).copy(transformEquipTo = 100)
            val item = attachedItem(id = 100, type = type)
            item.setDuration(1_000)
            decay.startDecay(item)

            now = 2_000
            dispatcher.wake()
            awaitUntil("item was not removed") { removals.size == 1 }

            assertThat(removals.single()).isSameAs(item)
            assertThat(transforms).isEmpty()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `stopDecay restores the remaining duration and detaches the state`() {
        dispatcher = startDispatcher()
        decay = newDecay()
        now = System.currentTimeMillis()
        try {
            val item = attachedItem(type = decayingType(decayTime = 60_000))
            item.setDuration(60_000)
            decay.startDecay(item)

            decay.stopDecay(item)

            assertThat(item.hasAttribute(ItemAttribute.DECAYSTATE)).isFalse()
            assertThat(item.getDecaying()).isEqualTo(ItemDecayState.DECAYING_FALSE)
            val restored = (item.getAttribute(ItemAttribute.DURATION) as Long)
            assertThat(restored).isBetween(59_000L, 60_000L)

            now += 60_000
            dispatcher.wake()
            Thread.sleep(100)
            assertThat(transforms).isEmpty()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `stopDecay removes only the matching item from a shared bucket`() {
        val dispatcher = setup(decayTime = 1_000).first
        try {
            val first = attachedItem(type = decayingType())
            val second = attachedItem(type = decayingType())
            first.setDuration(1_000)
            second.setDuration(1_000)
            decay.startDecay(first)
            decay.startDecay(second)

            decay.stopDecay(second)

            assertThat(first.getDecaying()).isEqualTo(ItemDecayState.DECAYING_TRUE)
            assertThat(second.hasAttribute(ItemAttribute.DECAYSTATE)).isFalse()

            now = 2_000
            dispatcher.wake()
            awaitUntil("first item was not decayed") { transforms.size == 1 }
            assertThat(transforms.single().first).isSameAs(first)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `stopDecay on an item outside the bucket clears the timestamp`() {
        val dispatcher = setup(decayTime = 1_000).first
        try {
            val registered = attachedItem(type = decayingType())
            registered.setDuration(1_000)
            decay.startDecay(registered)

            val outsider = attachedItem(type = decayingType()).apply {
                setDuration(1_000)
                setDecaying(ItemDecayState.DECAYING_TRUE)
                setAttribute(ItemAttribute.DURATION_TIMESTAMP, 5_000L)
            }

            decay.stopDecay(outsider)

            assertThat(outsider.hasAttribute(ItemAttribute.DURATION_TIMESTAMP)).isFalse()
            assertThat(registered.getDecaying()).isEqualTo(ItemDecayState.DECAYING_TRUE)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `stopDecay without a timestamp clears the decaying state`() {
        val dispatcher = setup().first
        try {
            val item = attachedItem(type = decayingType()).apply {
                setDecaying(ItemDecayState.DECAYING_TRUE)
            }
            item.removeAttribute(ItemAttribute.DURATION_TIMESTAMP)

            decay.stopDecay(item)

            assertThat(item.hasAttribute(ItemAttribute.DECAYSTATE)).isFalse()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `an earlier decay timestamp reschedules the timer`() {
        val dispatcher = setup(decayTime = 1_000).first
        try {
            val longItem = attachedItem(type = decayingType(decayTime = 1_000))
            val shortItem = attachedItem(type = decayingType(decayTime = 400))
            longItem.setDuration(1_000)
            shortItem.setDuration(400)

            decay.startDecay(longItem)
            decay.startDecay(shortItem)

            now = 1_400
            dispatcher.wake()
            awaitUntil("short item was not decayed") { transforms.size == 1 }
            assertThat(transforms.single().first).isSameAs(shortItem)

            now = 2_000
            dispatcher.wake()
            awaitUntil("long item was not decayed") { transforms.size == 2 }
            assertThat(transforms[1].first).isSameAs(longItem)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `the timer interval is bounded by SCHEDULER_MINTICKS`() {
        val dispatcher = setup(decayTime = 1_000).first
        try {
            val item = attachedItem(type = decayingType(decayTime = 10))
            item.setDuration(10)
            decay.startDecay(item)

            now = 1_049
            dispatcher.wake()
            Thread.sleep(100)
            assertThat(transforms).isEmpty()

            now = 1_050
            dispatcher.wake()
            awaitUntil("item was not decayed at the bounded interval") { transforms.size == 1 }
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `a stale scheduled generation is a no-op`() {
        val dispatcher = setup(decayTime = 1_000).first
        try {
            val item = attachedItem(type = decayingType(decayTime = 1_000))
            item.setDuration(1_000)
            decay.startDecay(item)

            now = 2_000
            decay.runScheduledCheck(0L)
            assertThat(item.getDecaying()).isEqualTo(ItemDecayState.DECAYING_TRUE)
            assertThat(transforms).isEmpty()

            decay.runScheduledCheck(1L)
            assertThat(transforms.single().first).isSameAs(item)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `scheduled callback is inert after its owner is collected`() {
        val dispatcher = startDispatcher()
        try {
            val item = attachedItem(type = decayingType())
            item.setDuration(1_000)
            val transformed = AtomicInteger(0)
            val owner = armOrphanDecay(dispatcher, item, transformed)

            assertThat(awaitCollected(owner)).isTrue()

            now = 2_000
            dispatcher.wake()
            Thread.sleep(100)
            assertThat(transformed.get()).isZero()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `shutting down the dispatcher stops decay ticks`() {
        val dispatcher = setup(decayTime = 1_000).first
        val item = attachedItem(type = decayingType())
        item.setDuration(1_000)
        decay.startDecay(item)

        dispatcher.shutdown()
        now = 2_000
        dispatcher.wake()
        Thread.sleep(100)

        assertThat(transforms).isEmpty()
        assertThat(item.getDecaying()).isEqualTo(ItemDecayState.DECAYING_TRUE)
    }

    @Test
    fun `the bucket can be reused after a decay completes`() {
        val dispatcher = setup(decayTime = 1_000).first
        try {
            val first = attachedItem(type = decayingType())
            first.setDuration(1_000)
            decay.startDecay(first)
            now = 2_000
            dispatcher.wake()
            awaitUntil("first item was not decayed") { transforms.size == 1 }

            val second = attachedItem(type = decayingType())
            second.setDuration(1_000)
            decay.startDecay(second)
            now = 3_000
            dispatcher.wake()
            awaitUntil("second item was not decayed") { transforms.size == 2 }

            assertThat(transforms[1].first).isSameAs(second)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `stopping a stale item leaves the registered bucket untouched`() {
        val dispatcher = setup(decayTime = 1_000).first
        try {
            val registered = attachedItem(type = decayingType())
            registered.setDuration(1_000)
            decay.startDecay(registered)

            val sameTimestampOutsider = attachedItem(type = decayingType()).apply {
                setDuration(1_000)
                setDecaying(ItemDecayState.DECAYING_TRUE)
                setAttribute(
                    ItemAttribute.DURATION_TIMESTAMP,
                    registered.getAttribute(ItemAttribute.DURATION_TIMESTAMP) as Long,
                )
            }

            decay.stopDecay(sameTimestampOutsider)

            assertThat(registered.getDecaying()).isEqualTo(ItemDecayState.DECAYING_TRUE)
            now = 2_000
            dispatcher.wake()
            awaitUntil("registered item was not decayed") { transforms.size == 1 }
        } finally {
            dispatcher.shutdown()
        }
    }

    private fun armOrphanDecay(dispatcher: GameDispatcher, item: Item, transformed: AtomicInteger): WeakReference<Decay> {
        val orphan = Decay(
            dispatcher = dispatcher,
            now = { now },
            transformItem = { _, _ -> transformed.incrementAndGet() },
        )
        orphan.startDecay(item)
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

    @Test
    fun `startDecay does not reschedule when a later timestamp already waits`() {
        val dispatcher = setup(decayTime = 1_000).first
        try {
            val shortItem = attachedItem(type = decayingType(decayTime = 400))
            val longItem = attachedItem(type = decayingType(decayTime = 1_000))
            shortItem.setDuration(400)
            longItem.setDuration(1_000)

            decay.startDecay(shortItem)
            decay.startDecay(longItem)

            now = 1_400
            dispatcher.wake()
            awaitUntil("short item was not decayed") { transforms.size == 1 }
            assertThat(transforms.single().first).isSameAs(shortItem)

            now = 2_000
            dispatcher.wake()
            awaitUntil("long item was not decayed") { transforms.size == 2 }
            assertThat(transforms[1].first).isSameAs(longItem)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `dispatcher maintenance lane runs the decay check`() {
        val dispatcher = startDispatcher()
        try {
            val executions = AtomicInteger(0)
            dispatcher.dispatch(DispatcherLane.Maintenance) { executions.incrementAndGet() }
            awaitUntil("maintenance lane did not run") { executions.get() == 1 }
        } finally {
            dispatcher.shutdown()
        }
    }
}
