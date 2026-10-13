package org.opentibia.canary.items.decay

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.ScheduledTask
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemDecayState
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.lang.ref.WeakReference
import java.util.TreeMap

private val logger = KotlinLogging.logger {}

/** `SCHEDULER_MINTICKS` (src/game/scheduling/dispatcher.hpp:25), the bounded timer floor. */
internal const val SCHEDULER_MINTICKS = 50L

/**
 * Port of `Decay` (src/items/decay/decay.hpp:14-33, src/items/decay/decay.cpp:21-224).
 *
 * The C++ singleton (`inject<Decay>()`) becomes constructor-injected state, and
 * the `g_dispatcher().scheduleEvent`/`stopEvent` pair becomes a cancellable
 * [ScheduledTask] on the maintenance lane. Every scheduled check resolves the
 * owning instance through a [WeakReference] and validates a monotonically
 * advancing generation before touching [decayMap], so a callback that outlives
 * its owner or a superseded timer is a no-op (AGENTS.md deferred-callback
 * lifetime safety).
 *
 * PARITY GAP: the `Item::getHoldingPlayer` skill/stat compensation of
 * `internalDecayItem` (decay.cpp:175-210) needs the `ItemType::abilities` and
 * `Item::getSkill`/`getStat` models, which the Kotlin item does not carry yet.
 */
class Decay(
    private val dispatcher: GameDispatcher,
    /** `OTSYS_TIME()` (src/utils/tools.hpp:181). */
    private val now: () -> Long = System::currentTimeMillis,
    /** `g_game().internalRemoveItem(item)` (decay.cpp:165, 217). */
    private val internalRemoveItem: (Item) -> ReturnValue = { ReturnValue.NOTPOSSIBLE },
    /** `g_game().transformItem(item, id)` (decay.cpp:211). */
    private val transformItem: (Item, Int) -> Unit = { _, _ -> },
) {
    /** `Decay::decayMap` (src/items/decay/decay.hpp:32); ordered by timestamp. */
    private val decayMap = TreeMap<Long, MutableList<Item>>()

    /** `Decay::eventId` (src/items/decay/decay.hpp:30), re-resolvable via [ScheduledTask.cancel]. */
    private var eventId: ScheduledTask? = null

    /**
     * Monotonic token stamped into every scheduled check. It is advanced each
     * time a check is (re)scheduled, so a callback whose generation no longer
     * matches this instance's current value is discarded (AGENTS.md).
     */
    private var timerGeneration: Long = 0

    /** `Decay::startDecay` (src/items/decay/decay.cpp:21-67). */
    fun startDecay(item: Item) {
        val decayState = item.getDecaying()
        if (decayState == ItemDecayState.DECAYING_STOPPING ||
            (!item.canDecay() && decayState == ItemDecayState.DECAYING_TRUE)
        ) {
            stopDecay(item)
            return
        }

        if (!item.canDecay() || decayState == ItemDecayState.DECAYING_TRUE) {
            return
        }

        val duration = item.getAttribute(ItemAttribute.DURATION) as? Long ?: 0L
        if (duration <= 0L && item.hasAttribute(ItemAttribute.DURATION)) {
            internalDecayItem(item)
            return
        }

        if (duration > 0L) {
            if (item.hasAttribute(ItemAttribute.DURATION_TIMESTAMP)) {
                stopDecay(item)
            }

            val timestamp = now() + duration
            if (decayMap.isEmpty() || timestamp < decayMap.firstKey()) {
                scheduleCheck(maxOf(SCHEDULER_MINTICKS, duration))
            }

            item.setDecaying(ItemDecayState.DECAYING_TRUE)
            item.setAttribute(ItemAttribute.DURATION_TIMESTAMP, timestamp)
            decayMap.getOrPut(timestamp) { mutableListOf() }.add(item)
        }
    }

    /** `Decay::stopDecay` (src/items/decay/decay.cpp:69-116). */
    fun stopDecay(item: Item) {
        if (!item.hasAttribute(ItemAttribute.DECAYSTATE)) {
            return
        }

        val timestamp = item.getAttribute(ItemAttribute.DURATION_TIMESTAMP) as? Long ?: 0L
        if (!item.hasAttribute(ItemAttribute.DURATION_TIMESTAMP)) {
            item.removeAttribute(ItemAttribute.DECAYSTATE)
            return
        }

        val decayItems = decayMap[timestamp]
        if (decayItems != null) {
            val end = decayItems.size
            if (end == 1) {
                if (item === decayItems[0]) {
                    if (item.hasAttribute(ItemAttribute.DURATION)) {
                        item.setDuration(item.getDuration())
                    }
                    item.removeAttribute(ItemAttribute.DECAYSTATE)
                    decayMap.remove(timestamp)
                }
                return
            }

            var i = 0
            while (i < end) {
                if (item === decayItems[i]) {
                    if (item.hasAttribute(ItemAttribute.DURATION)) {
                        item.setDuration(item.getDuration())
                    }
                    item.removeAttribute(ItemAttribute.DECAYSTATE)
                    decayItems[i] = decayItems[end - 1]
                    decayItems.removeAt(end - 1)
                    return
                }
                ++i
            }
        }

        item.removeAttribute(ItemAttribute.DURATION_TIMESTAMP)
    }

    /**
     * Validates a scheduled check against the current generation and runs it
     * when it is still the live timer. A superseded or post-owner callback
     * becomes a no-op (AGENTS.md deferred-callback lifetime safety).
     */
    internal fun runScheduledCheck(generation: Long) {
        if (generation != timerGeneration) {
            return
        }
        checkDecay()
    }

    /** `Decay::checkDecay` (src/items/decay/decay.cpp:118-155). */
    private fun checkDecay() {
        val timestamp = now()
        val tempItems = mutableListOf<Item>()
        var nextTimestamp: Long? = null

        val iterator = decayMap.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key > timestamp) {
                nextTimestamp = entry.key
                break
            }

            tempItems.addAll(entry.value)
            iterator.remove()
        }

        for (item in tempItems) {
            if (!item.canDecay()) {
                item.setDuration(item.getDuration())
                item.setDecaying(ItemDecayState.DECAYING_FALSE)
            } else {
                item.setDecaying(ItemDecayState.DECAYING_FALSE)
                internalDecayItem(item)
            }
        }

        if (nextTimestamp != null) {
            scheduleCheck(maxOf(SCHEDULER_MINTICKS, nextTimestamp - timestamp))
        }
    }

    /** `Decay::internalDecayItem` (src/items/decay/decay.cpp:157-224). */
    private fun internalDecayItem(item: Item) {
        val itemType = item.type ?: return

        if (itemType.id == itemType.transformEquipTo || itemType.id == itemType.transformDeEquipTo) {
            internalRemoveItem(item)
            logger.error {
                "[internalDecayItem] - item id is the same as its transform equip/de-equip id: ${itemType.id}"
            }
            return
        }

        if (itemType.decayTo != 0) {
            transformItem(item, itemType.decayTo)
        } else {
            if (item.isLoadedFromMap()) {
                return
            }

            val ret = internalRemoveItem(item)
            if (ret != ReturnValue.NOERROR) {
                logger.error {
                    "[Decay::internalDecayItem] - internalDecayItem failed, error code: ${ret.ordinal}, item id: ${item.id}"
                }
            }
        }
    }

    /**
     * `g_dispatcher().scheduleEvent(max(SCHEDULER_MINTICKS, delay), [this]{ checkDecay(); })`
     * (decay.cpp:51-61, 151-153). The previous event is stopped via
     * [ScheduledTask.cancel] exactly like `stopEvent(eventId)`.
     */
    private fun scheduleCheck(delayMs: Long) {
        eventId?.cancel()
        timerGeneration += 1
        val generation = timerGeneration
        val weakSelf = WeakReference(this)
        eventId = dispatcher.schedule(delayMs, DispatcherLane.Maintenance) {
            weakSelf.get()?.runScheduledCheck(generation)
        }
    }
}
