package org.opentibia.canary.utils

import java.lang.ref.WeakReference
import java.util.WeakHashMap
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.ContainerBatchActor

/**
 * `Player::beginBatchUpdate`/`Player::endBatchUpdate` seam called by
 * [BatchUpdate] (src/creatures/players/player.cpp:9140-9156). Canary stores a
 * `std::weak_ptr<Player>`, so the engine only calls an actor that is still
 * alive.
 */
interface BatchUpdateActor {
    fun beginBatchUpdate()
    fun endBatchUpdate()
}

/**
 * Port of `BatchUpdate` (src/utils/batch_update.hpp:23-48,
 * batch_update.cpp:15-62): the per-actor cache of containers currently being
 * batched.
 *
 * Ownership follows the C++ layout: both the actor and the cached containers
 * are weakly held, and [release] mirrors the destructor's `endBatchUpdate`
 * cascade. The `Container*`-keyed `cachedLookup` becomes a weak-keyed map so
 * the lookup never keeps a container alive (a strong `IdentityHashMap` key
 * would defeat the C++ `weak_ptr` semantics).
 */
class BatchUpdate(actor: BatchUpdateActor?) {

    private val actorRef = WeakReference(actor)
    private val cached = mutableListOf<WeakReference<Container>>()
    private val cachedLookup = WeakHashMap<Container, Unit>()

    init {
        actorRef.get()?.beginBatchUpdate()
    }

    /** `BatchUpdate::add` (batch_update.cpp:38-56). */
    fun add(container: Container?): Boolean {
        if (container == null) {
            return false
        }

        if (cachedLookup.containsKey(container)) {
            return false
        }

        cachedLookup[container] = Unit
        cached.add(WeakReference(container))
        container.beginBatchUpdate()
        return true
    }

    /** `BatchUpdate::addContainers` (batch_update.cpp:58-62). */
    fun addContainers(containers: List<Container?>) {
        for (container in containers) {
            add(container)
        }
    }

    /** `BatchUpdate::~BatchUpdate` (batch_update.cpp:25-36). */
    fun release() {
        val actor = actorRef.get()
        val containerActor = actor as? ContainerBatchActor
        for (containerRef in cached) {
            containerRef.get()?.endBatchUpdate(containerActor)
        }
        cached.clear()
        cachedLookup.clear()
        actor?.endBatchUpdate()
    }
}
