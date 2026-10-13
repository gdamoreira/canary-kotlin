package org.opentibia.canary.utils

import java.lang.ref.WeakReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.ContainerBatchActor
import org.opentibia.canary.map.Position

/**
 * Transcript tests for `BatchUpdate` (`src/utils/batch_update.cpp:15-62`) and
 * `Player::beginBatchUpdate`/`endBatchUpdate`/`isBatching`
 * (src/creatures/players/player.cpp:9140-9156, player.hpp:769-774), mirroring
 * `tests/unit/utils/test_batch_update.cpp`.
 */
class BatchUpdateTest {

    private class FakeContainer : Container {
        var beginCount = 0
        var endCount = 0

        constructor(id: Int = 1988, capacity: Int = 10) : super(id, capacity)

        override fun beginBatchUpdate() {
            ++beginCount
            super.beginBatchUpdate()
        }

        override fun endBatchUpdate(actor: ContainerBatchActor?) {
            ++endCount
            super.endBatchUpdate(actor)
        }
    }

    private class FakeActor : BatchUpdateActor, ContainerBatchActor {
        var beginCount = 0
        var endCount = 0
        var sentContainers = 0
        private var batching = 0

        override fun beginBatchUpdate() {
            ++beginCount
            ++batching
        }

        override fun endBatchUpdate() {
            ++endCount
            if (batching > 0) {
                --batching
            }
        }

        override fun sendBatchUpdateContainer(container: Container, hasParent: Boolean) {
            ++sentContainers
        }

        fun isBatching(): Boolean = batching > 0
    }

    private fun awaitCollected(owner: WeakReference<*>): Boolean {
        repeat(100) {
            System.gc()
            if (owner.get() == null) {
                return true
            }
            Thread.sleep(5)
        }
        return owner.get() == null
    }

    @Test
    fun `deduplicates containers and balances begin end calls`() {
        val actor = FakeActor()
        val container = FakeContainer()
        val batch = BatchUpdate(actor)

        assertThat(actor.beginCount).isEqualTo(1)
        assertThat(batch.add(container)).isTrue()
        assertThat(batch.add(container)).isFalse()
        assertThat(actor.isBatching()).isTrue()
        assertThat(container.beginCount).isEqualTo(1)

        batch.release()

        assertThat(actor.isBatching()).isFalse()
        assertThat(actor.endCount).isEqualTo(1)
        assertThat(container.endCount).isEqualTo(1)
    }

    @Test
    fun `add returns false for null containers`() {
        val actor = FakeActor()
        val container = FakeContainer()
        val batch = BatchUpdate(actor)

        assertThat(batch.add(container)).isTrue()
        assertThat(batch.add(null)).isFalse()
        assertThat(actor.isBatching()).isTrue()
        assertThat(container.beginCount).isEqualTo(1)

        batch.release()
    }

    @Test
    fun `addContainers processes unique containers once`() {
        val actor = FakeActor()
        val first = FakeContainer()
        val second = FakeContainer()
        val batch = BatchUpdate(actor)

        assertThat(batch.add(first)).isTrue()
        batch.addContainers(listOf(first, second))

        assertThat(actor.isBatching()).isTrue()
        assertThat(first.beginCount).isEqualTo(1)
        assertThat(second.beginCount).isEqualTo(1)

        batch.release()

        assertThat(first.endCount).isEqualTo(1)
        assertThat(second.endCount).isEqualTo(1)
    }

    @Test
    fun `release handles an expired actor`() {
        val container = FakeContainer()
        var actor: FakeActor? = FakeActor()
        val batch = BatchUpdate(actor)
        assertThat(batch.add(container)).isTrue()

        val actorRef = WeakReference(actor)
        actor = null
        assertThat(awaitCollected(actorRef)).isTrue()

        batch.release()

        assertThat(container.endCount).isEqualTo(1)
    }

    @Test
    fun `add re-caches a container after it expires`() {
        val actor = FakeActor()
        var first: FakeContainer? = FakeContainer()
        val firstRef = WeakReference(first)
        val second = FakeContainer()
        val batch = BatchUpdate(actor)

        assertThat(batch.add(first)).isTrue()
        first = null
        assertThat(awaitCollected(firstRef)).isTrue()

        assertThat(batch.add(second)).isTrue()
        assertThat(second.beginCount).isEqualTo(1)

        batch.release()
    }

    @Test
    fun `nested batch updates keep the actor batching until the outer scope ends`() {
        val actor = FakeActor()
        val first = FakeContainer()
        val second = FakeContainer()

        val outer = BatchUpdate(actor)
        assertThat(actor.isBatching()).isTrue()
        assertThat(outer.add(first)).isTrue()

        val inner = BatchUpdate(actor)
        assertThat(actor.isBatching()).isTrue()
        assertThat(inner.add(second)).isTrue()

        inner.release()

        assertThat(actor.isBatching()).isTrue()
        assertThat(first.endCount).isEqualTo(0)
        assertThat(second.endCount).isEqualTo(1)

        outer.release()

        assertThat(actor.isBatching()).isFalse()
        assertThat(first.endCount).isEqualTo(1)
        assertThat(second.endCount).isEqualTo(1)
    }

    @Test
    fun `player models the batch update actor state`() {
        val player = Player(1, "Actor", 100, 100, position = Position(100, 100, 7))

        assertThat(player.isBatching()).isFalse()

        val batch = BatchUpdate(player)
        assertThat(player.isBatching()).isTrue()

        batch.release()
        assertThat(player.isBatching()).isFalse()
    }
}
