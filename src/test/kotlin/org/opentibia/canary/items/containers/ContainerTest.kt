package org.opentibia.canary.items.containers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.INDEX_WHEREEVER
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.QueryMaxCountResult
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for `Container` and `ContainerIterator`
 * (src/items/containers/container.cpp:17-1387, container.hpp:25-406).
 *
 * The pure model behavior (capacity, cached item/container counts, recursive
 * weight, query add/remove/destination, iterator traversal and serialization)
 * is exercised with real [Item]/[Container] instances and no game globals.
 */
class ContainerTest {

    private val goldType = ItemType(
        id = 2148,
        name = "Gold Coin",
        isStackable = true,
        weight = 0.1,
        stackSize = 100,
        isMovable = true,
    )

    private fun bag(id: Int = 1987, capacity: Int = 8, weight: Double = 10.0): Container =
        Container(id, capacity = capacity, type = ItemType(id, "Bag", isContainer = true, capacity = capacity, weight = weight, isMovable = true))

    private fun coin(count: Int = 1): Item = Item(goldType.id, count = count, type = goldType)

    private fun item(id: Int, weight: Double = 0.0): Item =
        Item(id, type = ItemType(id, "item $id", weight = weight, isMovable = true))

    // --- capacity / size (container.hpp:206-217, 249) -------------------------

    @Test
    fun `queryAdd accepts items while free slots remain and rejects past capacity`() {
        val bag = bag(capacity = 2)
        val first = coin()
        val second = item(2376)
        val third = item(2389)

        assertThat(bag.queryAdd(INDEX_WHEREEVER, first, 1, 0)).isEqualTo(ReturnValue.NOERROR)
        bag.addItem(first)
        assertThat(bag.queryAdd(INDEX_WHEREEVER, second, 1, 0)).isEqualTo(ReturnValue.NOERROR)
        bag.addItem(second)

        assertThat(bag.queryAdd(INDEX_WHEREEVER, third, 1, 0)).isEqualTo(ReturnValue.CONTAINERNOTENOUGHROOM)
        assertThat(bag.queryAdd(INDEX_WHEREEVER, third, 1, CylinderFlags.FLAG_NOLIMIT)).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `queryAdd honors the CHILDISOWNER flag without checking the slot limit`() {
        val bag = bag(capacity = 1)
        bag.addItem(coin())

        assertThat(bag.queryAdd(INDEX_WHEREEVER, item(2376), 1, CylinderFlags.FLAG_CHILDISOWNER))
            .isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `queryAdd rejects a non pickupable item`() {
        val bag = bag()
        val rock = Item(1294, type = ItemType(1294, "Rock", isPickupable = false))

        assertThat(bag.queryAdd(INDEX_WHEREEVER, rock, 1, 0)).isEqualTo(ReturnValue.CANNOTPICKUP)
    }

    @Test
    fun `queryAdd rejects the container itself`() {
        val bag = bag()

        assertThat(bag.queryAdd(INDEX_WHEREEVER, bag, 1, 0)).isEqualTo(ReturnValue.THISISIMPOSSIBLE)
    }

    @Test
    fun `queryAdd rejects an item owned by another owner than the container`() {
        val bag = bag()
        bag.setAttribute(ItemAttribute.OWNER, 7L)
        val owned = coin().apply { setAttribute(ItemAttribute.OWNER, 9L) }

        assertThat(bag.queryAdd(INDEX_WHEREEVER, owned, 1, 0)).isEqualTo(ReturnValue.ITEMISNOTYOURS)
    }

    @Test
    fun `queryAdd accepts an owner matched item when the container has no owner`() {
        val bag = bag()
        val owned = coin().apply { setAttribute(ItemAttribute.OWNER, 9L) }

        assertThat(bag.queryAdd(INDEX_WHEREEVER, owned, 1, 0)).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `queryAdd caps the whole container tree at maxItems`() {
        // Container::queryAdd (container.cpp:594-611) with m_maxItems reached:
        // `addItemCount + topParentContainer->getItemHoldingCount() > m_maxItems`.
        val bag = Container(
            id = 1987,
            capacity = 8,
            type = ItemType(1987, "Bag", isContainer = true, capacity = 8),
            maxItems = 1,
        )
        bag.addItem(coin())

        assertThat(bag.queryAdd(INDEX_WHEREEVER, item(2376), 1, 0)).isEqualTo(ReturnValue.CONTAINERISFULL)
    }

    @Test
    fun `getMaxCapacity returns the configured max items`() {
        val bag = Container(id = 1987, capacity = 8, type = ItemType(1987, "Bag", isContainer = true), maxItems = 25)

        assertThat(bag.getMaxCapacity()).isEqualTo(25)
    }

    // --- queryMaxCount (container.cpp:625-672) --------------------------------

    @Test
    fun `queryMaxCount for a non stackable item returns the free slots`() {
        val bag = bag(capacity = 3)
        bag.addItem(item(2376))

        val (result, maxQueryCount) = bag.queryMaxCount(INDEX_WHEREEVER, item(2389), 1, 0, 0)

        assertThat(result).isEqualTo(ReturnValue.NOERROR)
        assertThat(maxQueryCount).isEqualTo(2)
    }

    @Test
    fun `queryMaxCount returns CONTAINERNOTENOUGHROOM when no slot is free`() {
        val bag = bag(capacity = 1)
        bag.addItem(item(2376))

        val (result, maxQueryCount) = bag.queryMaxCount(INDEX_WHEREEVER, item(2389), 1, 0, 0)

        assertThat(result).isEqualTo(ReturnValue.CONTAINERNOTENOUGHROOM)
        assertThat(maxQueryCount).isEqualTo(0)
    }

    @Test
    fun `queryMaxCount for stackable items sums free stacks and partial stack space`() {
        // container.cpp:639-664: freeSlots * stackSize + existing partial stack room.
        val bag = bag(capacity = 2)
        bag.addItem(coin(count = 30))

        val (result, maxQueryCount) = bag.queryMaxCount(INDEX_WHEREEVER, coin(count = 10), 1, 0, 0)

        assertThat(result).isEqualTo(ReturnValue.NOERROR)
        assertThat(maxQueryCount).isEqualTo(1 * 100 + 70)
    }

    @Test
    fun `queryMaxCount with FLAG_NOLIMIT or pagination returns at least the requested count`() {
        val bag = bag(capacity = 1)
        bag.addItem(item(2376))

        assertThat(bag.queryMaxCount(INDEX_WHEREEVER, coin(count = 5), 5, 0, CylinderFlags.FLAG_NOLIMIT))
            .isEqualTo(QueryMaxCountResult(ReturnValue.NOERROR, 5))

        val paginated = Container(id = 1987, capacity = 1, type = ItemType(1987, "Bag", isContainer = true), pagination = true)
        assertThat(paginated.queryMaxCount(INDEX_WHEREEVER, coin(count = 3), 3, 0, 0))
            .isEqualTo(QueryMaxCountResult(ReturnValue.NOERROR, 3))
    }

    // --- queryRemove (container.cpp:674-701) ----------------------------------

    @Test
    fun `queryRemove rejects an item that is not held`() {
        val bag = bag()

        assertThat(bag.queryRemove(item(2376), 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `queryRemove rejects zero counts and stack counts above the stack size`() {
        val bag = bag()
        val stack = coin(count = 10)
        bag.addItem(stack)

        assertThat(bag.queryRemove(stack, 0, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(bag.queryRemove(stack, 11, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(bag.queryRemove(stack, 10, 0)).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `queryRemove rejects non movable items`() {
        val bag = bag()
        val fixed = Item(1210, type = ItemType(1210, "Door", isMovable = false))
        bag.addItem(fixed)

        assertThat(bag.queryRemove(fixed, 1, 0)).isEqualTo(ReturnValue.NOTMOVABLE)
        assertThat(bag.queryRemove(fixed, 1, CylinderFlags.FLAG_IGNORENOTMOVABLE)).isEqualTo(ReturnValue.NOERROR)
    }

    // --- queryDestination (container.cpp:703-771) -----------------------------

    @Test
    fun `queryDestination resolves an index to its item and descends into containers`() {
        val bag = bag()
        val subBag = bag(id = 1988)
        bag.addItem(item(2376))
        bag.addItem(subBag)

        val plain = bag.queryDestination(0, coin(), null, 0)
        assertThat(plain.index).isEqualTo(0)
        assertThat(plain.destItem?.id).isEqualTo(2376)
        assertThat(plain.cylinder).isSameAs(bag)

        val nested = bag.queryDestination(1, coin(), null, 0)
        assertThat(nested.index).isEqualTo(INDEX_WHEREEVER)
        assertThat(nested.destItem).isNull()
        assertThat(nested.cylinder).isSameAs(subBag)
    }

    @Test
    fun `queryDestination maps add wherever and out of range indexes to INDEX_WHEREEVER`() {
        val bag = bag(capacity = 2)

        assertThat(bag.queryDestination(255, coin(), null, 0).index).isEqualTo(INDEX_WHEREEVER)
        assertThat(bag.queryDestination(2, coin(), null, 0).index).isEqualTo(INDEX_WHEREEVER)
    }

    @Test
    fun `queryDestination auto stacks onto an existing partial stack`() {
        val bag = bag()
        val stack = coin(count = 30)
        bag.addItem(stack)

        val destination = bag.queryDestination(INDEX_WHEREEVER, coin(count = 10), null, 0)

        assertThat(destination.destItem).isSameAs(stack)
        assertThat(destination.index).isEqualTo(0)
    }

    @Test
    fun `queryDestination ignores auto stacking with the IGNOREAUTOSTACK flag`() {
        val bag = bag()
        bag.addItem(coin(count = 30))

        val destination = bag.queryDestination(INDEX_WHEREEVER, coin(count = 10), null, CylinderFlags.FLAG_IGNOREAUTOSTACK)

        assertThat(destination.destItem).isNull()
    }

    // --- weight recursion (container.cpp:235-293) -----------------------------

    @Test
    fun `container weight recursively includes nested container weights`() {
        val outer = bag(weight = 10.0)
        val inner = bag(id = 1988, weight = 5.0)
        inner.addItem(coin(count = 100))
        outer.addItem(inner)

        assertThat(inner.weight).isEqualTo(15.0)
        assertThat(outer.weight).isEqualTo(25.0)
    }

    @Test
    fun `weight updates propagate to every ancestor when a nested item is added`() {
        val outer = bag(weight = 10.0)
        val inner = bag(id = 1988, weight = 5.0)
        outer.addItem(inner)

        assertThat(outer.weight).isEqualTo(15.0)

        inner.addItem(coin(count = 50))

        assertThat(inner.weight).isEqualTo(10.0)
        assertThat(outer.weight).isEqualTo(20.0)
    }

    @Test
    fun `weight updates propagate when a nested container is removed`() {
        val outer = bag(weight = 10.0)
        val inner = bag(id = 1988, weight = 5.0)
        inner.addItem(coin(count = 100))
        outer.addItem(inner)

        outer.removeItemByIndex(0, 100)

        assertThat(outer.size()).isEqualTo(0)
        assertThat(outer.weight).isEqualTo(10.0)
        assertThat(inner.getParent()).isNull()
    }

    // --- cached item / container counts (container.cpp:243-289) ---------------

    @Test
    fun `holding counts recursively count nested items and containers`() {
        val outer = bag()
        val inner = bag(id = 1988)
        val nested = bag(id = 1989)
        inner.addItem(coin())
        inner.addItem(nested)
        nested.addItem(item(2376))
        outer.addItem(inner)

        assertThat(outer.getItemHoldingCount()).isEqualTo(4)
        assertThat(outer.getContainerHoldingCount()).isEqualTo(2)
        assertThat(inner.getItemHoldingCount()).isEqualTo(3)
        assertThat(inner.getContainerHoldingCount()).isEqualTo(1)
    }

    @Test
    fun `holding counts drop when a nested container is removed`() {
        val outer = bag()
        val inner = bag(id = 1988)
        inner.addItem(coin())
        outer.addItem(inner)

        outer.removeItemByIndex(0, 1)

        assertThat(outer.getItemHoldingCount()).isEqualTo(0)
        assertThat(outer.getContainerHoldingCount()).isEqualTo(0)
    }

    @Test
    fun `getFreeSlots recurses into sub containers`() {
        val outer = bag(capacity = 3)
        val inner = bag(id = 1988, capacity = 2)
        outer.addItem(inner)
        inner.addItem(item(2376))

        assertThat(outer.getFreeSlots()).isEqualTo(2 + 1)
    }

    // --- iterator (container.cpp:1222-1328) -----------------------------------

    @Test
    fun `iterator walks depth first yielding containers before their contents`() {
        val outer = bag()
        val inner = bag(id = 1988)
        val first = item(2376)
        val nested = item(2389)
        val last = item(2390)
        outer.addItem(first)
        outer.addItem(inner)
        inner.addItem(nested)
        outer.addItem(last)

        val iterator = outer.iterator()
        val visited = mutableListOf<Item>()
        while (iterator.hasNext()) {
            visited.add(iterator.current()!!)
            iterator.advance()
        }

        assertThat(visited).containsExactly(first, inner, nested, last)
    }

    @Test
    fun `iterator stops at the maximum traversal depth`() {
        val outer = bag()
        val inner = bag(id = 1988)
        outer.addItem(inner)
        inner.addItem(item(2376))

        val iterator = outer.iterator(maxDepth = 1)
        val visited = mutableListOf<Item>()
        while (iterator.hasNext()) {
            visited.add(iterator.current()!!)
            iterator.advance()
        }

        assertThat(visited).containsExactly(inner)
        assertThat(iterator.hasReachedMaxDepth()).isTrue()
    }

    @Test
    fun `iterator detects a cycle without revisiting the container`() {
        val recursive = bag()
        recursive.addItem(recursive)

        val iterator = recursive.iterator()
        val visited = mutableListOf<Item>()
        while (iterator.hasNext()) {
            visited.add(iterator.current()!!)
            iterator.advance()
        }

        assertThat(visited).containsExactly(recursive)
    }

    @Test
    fun `iterator reports the current index`() {
        val outer = bag()
        outer.addItem(item(2376))
        outer.addItem(item(2389))

        val iterator = outer.iterator()
        iterator.advance()

        assertThat(iterator.getCurrentIndex()).isEqualTo(1)
    }

    // --- add / remove / replace (container.cpp:773-974) -----------------------

    @Test
    fun `addThing inserts at the front and internalAddThing keeps deserialized order`() {
        val bag = bag()
        bag.addItem(item(1))
        bag.addItem(item(2))
        bag.addThing(item(3))

        assertThat(bag.items.map { it.id }).containsExactly(3, 1, 2)

        val restored = bag()
        restored.internalAddThing(item(1))
        restored.internalAddThing(item(2))

        assertThat(restored.items.map { it.id }).containsExactly(2, 1)
    }

    @Test
    fun `addItem appends and sets the child parent`() {
        val bag = bag()
        val coin = coin()
        bag.addItem(coin)

        assertThat(bag.items).containsExactly(coin)
        assertThat(coin.getParent()).isSameAs(bag)
    }

    @Test
    fun `updateThing updates id, subtype, caches and weight`() {
        val bag = bag(weight = 10.0)
        val stack = coin(count = 30)
        bag.addItem(stack)
        val oldWeight = stack.weight

        bag.updateThing(stack, 2389, 5)

        assertThat(stack.id).isEqualTo(2389)
        assertThat(stack.count).isEqualTo(5)
        assertThat(bag.weight).isEqualTo(10.0 + stack.weight)
        assertThat(oldWeight).isEqualTo(3.0)
    }

    @Test
    fun `replaceThing swaps the item, updates caches and weight and resets the old parent`() {
        val bag = bag(weight = 10.0)
        val old = item(2376, weight = 40.0)
        val new = item(2389, weight = 25.0)
        bag.addItem(old)

        bag.replaceThing(0, new)

        assertThat(bag.items).containsExactly(new)
        assertThat(bag.getItemHoldingCount()).isEqualTo(1)
        assertThat(bag.weight).isEqualTo(35.0)
        assertThat(new.getParent()).isSameAs(bag)
        assertThat(old.getParent()).isNull()
    }

    @Test
    fun `removeThing decrements a partial stack in place`() {
        val bag = bag()
        val stack = coin(count = 30)
        bag.addItem(stack)

        bag.removeThing(stack, 10)

        assertThat(bag.items).containsExactly(stack)
        assertThat(stack.count).isEqualTo(20)
        assertThat(bag.weight).isEqualTo(12.0)
    }

    @Test
    fun `removeThing removes the whole stack and resets the parent`() {
        val bag = bag()
        val stack = coin(count = 30)
        bag.addItem(stack)

        bag.removeThing(stack, 30)

        assertThat(bag.items).isEmpty()
        assertThat(stack.getParent()).isNull()
        assertThat(bag.weight).isEqualTo(10.0)
    }

    @Test
    fun `removeAllItems clears the container and reports the removed count`() {
        val bag = bag()
        bag.addItem(coin())
        bag.addItem(item(2376))

        val removed = bag.removeAllItems(null, false)

        assertThat(removed).isEqualTo(2)
        assertThat(bag.items).isEmpty()
    }

    @Test
    fun `removeItem unlinks an item without client notification`() {
        val bag = bag()
        val coin = coin()
        bag.addItem(coin)

        bag.removeItem(coin, false)

        assertThat(bag.items).isEmpty()
        assertThat(coin.getParent()).isNull()
    }

    @Test
    fun `getItemTypeCount counts subtype matches and getAllItemTypeCount aggregates by id`() {
        val bag = bag()
        bag.addItem(coin(count = 10))
        bag.addItem(coin(count = 5))
        bag.addItem(item(2376))

        assertThat(bag.getItemTypeCount(2148)).isEqualTo(15)
        assertThat(bag.getItemTypeCount(2148, 10)).isEqualTo(10)
        assertThat(bag.getAllItemTypeCount(mutableMapOf())).containsEntry(2148, 15).containsEntry(2376, 1)
    }

    @Test
    fun `getItems recursively collects every nested item`() {
        val outer = bag()
        val inner = bag(id = 1988)
        val nested = item(2376)
        inner.addItem(nested)
        outer.addItem(inner)
        outer.addItem(coin())

        assertThat(outer.getItems(recursive = true)).containsExactly(inner, nested, outer.items[1])
        assertThat(outer.getItems(recursive = false)).containsExactly(inner, outer.items[1])
    }

    @Test
    fun `getOwnerId falls back to the first owned child`() {
        val bag = bag()
        bag.addItem(coin())
        val owned = coin().apply { setAttribute(ItemAttribute.OWNER, 42L) }
        bag.addItem(owned)

        assertThat(bag.getOwnerId()).isEqualTo(42L)
    }

    // --- store inbox predicates (container.cpp:323-396) -----------------------

    @Test
    fun `store inbox predicates read the store inbox category attribute`() {
        val inbox = Container(
            id = Container.ITEM_STORE_INBOX,
            capacity = 32,
            type = ItemType(Container.ITEM_STORE_INBOX, "Store Inbox", isContainer = true, capacity = 32),
        )

        assertThat(inbox.isStoreInbox()).isTrue()
        assertThat(inbox.isStoreInboxFiltered()).isFalse()

        inbox.setAttribute(ItemAttribute.STORE_INBOX_CATEGORY, "All")
        assertThat(inbox.isStoreInboxFiltered()).isFalse()

        inbox.setAttribute(ItemAttribute.STORE_INBOX_CATEGORY, "Armors")
        assertThat(inbox.isStoreInboxFiltered()).isTrue()
    }

    @Test
    fun `getStoreInboxFilteredItems keeps only the matching primary type`() {
        val inbox = Container(
            id = Container.ITEM_STORE_INBOX,
            capacity = 32,
            type = ItemType(Container.ITEM_STORE_INBOX, "Store Inbox", isContainer = true, capacity = 32),
        )
        inbox.setAttribute(ItemAttribute.STORE_INBOX_CATEGORY, "Armors")
        val armor = Item(2400, type = ItemType(2400, "Armor", primaryType = "armors"))
        val sword = Item(2401, type = ItemType(2401, "Sword", primaryType = "sword_weapons"))
        inbox.addItem(armor)
        inbox.addItem(sword)

        assertThat(inbox.getStoreInboxFilteredItems()).containsExactly(armor)
        assertThat(inbox.getFilteredItemByIndex(0)).isSameAs(armor)
        assertThat(inbox.getFilteredItemByIndex(1)).isNull()
    }

    // --- serialization (container.cpp:176-184, iomapserialize.cpp:268-286) ----

    @Test
    fun `readAttr terminates the attribute list at ATTR_CONTAINER_ITEMS`() {
        val bag = bag()
        val stream = org.opentibia.canary.io.PropWriteStream()
        stream.writeU8(23)
        stream.writeU32(4)

        assertThat(bag.unserializeAttr(org.opentibia.canary.io.PropStream(stream.getStream()))).isTrue()
        assertThat(bag.serializationCount).isEqualTo(4)
    }

    @Test
    fun `getReversedItems yields the item list in reverse insertion order`() {
        val bag = bag()
        val first = item(1)
        val second = item(2)
        bag.addItem(first)
        bag.addItem(second)

        assertThat(bag.getReversedItems()).containsExactly(second, first)
    }
}
