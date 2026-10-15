package org.opentibia.canary.items

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.creatures.players.postAddNotification
import org.opentibia.canary.creatures.players.postRemoveNotification
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Port of `ContainerIterator` (src/items/containers/container.hpp:25-138,
 * container.cpp:1222-1328): depth-first traversal with max-depth and cycle
 * detection. `operator*` becomes [current]; the `hasNext`/`advance` contract
 * is kept verbatim.
 */
class ContainerIterator(private val initialContainer: Container?, private val maxTraversalDepth: Int) {

    private class IteratorState(
        val container: Container?,
        val items: List<Item>,
        var index: Int,
        val depth: Int,
    )

    private val states = ArrayDeque<IteratorState>()
    private val visitedContainers: MutableSet<Container> =
        Collections.newSetFromMap(IdentityHashMap<Container, Boolean>())
    private var mMaxDepthReached = false
    private var mCycleDetected = false

    init {
        if (initialContainer != null) {
            states.addLast(IteratorState(initialContainer, initialContainer.getItemList(), 0, 1))
            visitedContainers.add(initialContainer)
        }
    }

    /** `ContainerIterator::hasNext` (container.cpp:1233-1248). */
    fun hasNext(): Boolean {
        while (states.isNotEmpty()) {
            val state = states.last()
            if (state.index < state.items.size) {
                return true
            }
            states.removeLast()
        }
        return false
    }

    /** `ContainerIterator::advance` (container.cpp:1250-1297). */
    fun advance() {
        while (states.isNotEmpty()) {
            val top = states.last()
            if (top.index >= top.items.size) {
                states.removeLast()
                continue
            }

            val currentItem = top.items[top.index]
            top.index++

            val subContainer = currentItem.getContainer()
            if (subContainer == null) {
                return
            }

            val list = subContainer.getItemList()
            if (list.isEmpty()) {
                return
            }

            val newDepth = top.depth + 1
            if (newDepth > maxTraversalDepth) {
                mMaxDepthReached = true
                return
            }

            if (!visitedContainers.add(subContainer)) {
                mCycleDetected = true
                return
            }

            states.addLast(IteratorState(subContainer, list, 0, newDepth))
            return
        }
    }

    /** `ContainerIterator::operator*` (container.cpp:1299-1316). */
    fun current(): Item? {
        if (states.isEmpty()) {
            return null
        }
        val top = states.last()
        if (top.index >= top.items.size) {
            return null
        }
        return top.items[top.index]
    }

    /** `ContainerIterator::hasReachedMaxDepth` (container.cpp:1318-1320). */
    fun hasReachedMaxDepth(): Boolean = mMaxDepthReached

    /** `ContainerIterator::getCurrentIndex` (container.cpp:1322-1328). */
    fun getCurrentIndex(): Int = states.lastOrNull()?.index ?: 0

    /** `ContainerIterator::m_cycleDetected`; not exposed by the C++ header. */
    fun hasDetectedCycle(): Boolean = mCycleDetected
}

/**
 * Contract for the only `Player` member `Container::endBatchUpdate` calls
 * (`Player::sendBatchUpdateContainer`, src/creatures/players/player.cpp:9158).
 * The port injects it instead of the C++ `Player* actor` parameter.
 */
fun interface ContainerBatchActor {
    fun sendBatchUpdateContainer(container: Container, hasParent: Boolean)
}

/**
 * Port of `Container` (src/items/containers/container.hpp:140-406,
 * container.cpp:17-1387).
 *
 * The Canary constructor pair collapses into the primary constructor:
 * [capacity] is `Container::maxSize`, [maxItems] is `m_maxItems`
 * (`g_configManager().getNumber(MAX_CONTAINER_ITEM)`, container.cpp:19).
 * The client/spectator notifications, `g_game()`/`g_configManager()` lookups,
 * the decay hooks and the `Thing` hierarchy are modeled as the seams below so
 * the container model itself is exhaustively testable (AGENTS.md 1.2).
 */
open class Container(
    id: Int,
    var capacity: Int,
    type: ItemType? = null,
    protected var unlocked: Boolean = true,
    protected var pagination: Boolean = false,
    maxItems: Int = MAX_CONTAINER_ITEM,
) : Item(id, count = 1, type = type), Cylinder {

    /** `Container::m_maxItems` (src/items/containers/container.hpp:337). */
    protected var mMaxItems: Int = maxItems

    /** `Container::itemlist` (src/items/containers/container.hpp:340). */
    protected val itemlist = ArrayDeque<Item>()

    /** Read-only alias of `Container::getItemList` kept for existing port callers. */
    val items: List<Item> get() = itemlist

    /** `Container::serializationCount` (src/items/containers/container.hpp:341). */
    var serializationCount: Int = 0

    /** `Container::totalWeight` (src/items/containers/container.hpp:339): children weight only. */
    protected var totalWeight: Double = 0.0

    /** `Container::m_cachedItemCount` (src/items/containers/container.hpp:350). */
    private var m_cachedItemCount = 0

    /** `Container::m_cachedContainerCount` (src/items/containers/container.hpp:351). */
    private var m_cachedContainerCount = 0

    /** `Container::m_batching` (src/items/containers/container.hpp:402). */
    protected var m_batching = 0

    /** `Container::m_lootHighlightActive` (src/items/containers/container.hpp:345). */
    protected var mLootHighlightActive = true

    // --- accessors (container.hpp:206-258) ------------------------------------

    /** `Container::getMaxCapacity` (container.cpp:319-321). */
    fun getMaxCapacity(): Int = mMaxItems

    /** `Container::size` (container.hpp:208-210). */
    fun size(): Int = itemlist.size

    /** `Container::empty` (container.hpp:211-213). */
    fun empty(): Boolean = itemlist.isEmpty()

    /** `Container::isEmpty`; Kotlin alias kept for existing port callers. */
    fun isEmpty(): Boolean = empty()

    /** `Container::getItemList` (container.hpp:223-225). */
    fun getItemList(): List<Item> = itemlist

    /** `Container::getReversedItems` (container.hpp:227-232). */
    fun getReversedItems(): List<Item> = itemlist.asReversed()

    /** `Container::iterator` (container.cpp:1101-1107) with the config depth default. */
    fun iterator(maxDepth: Int = MAX_CONTAINER_DEPTH): ContainerIterator = ContainerIterator(getContainer(), maxDepth)

    /** `Container::countsToLootAnalyzerBalance` (container.cpp:223-233). */
    fun countsToLootAnalyzerBalance(): Boolean = isCorpse() || id == ITEM_REWARD_CONTAINER

    /** `Container::hasParent` (container.cpp:145-153). */
    fun hasParent(): Boolean {
        val parent = getParent() ?: return false
        val isPlayer = parent.getCreature() is Player
        return id != ITEM_BROWSEFIELD && !isPlayer
    }

    /** `Container::isUnlocked` (container.hpp:252-254). */
    fun isUnlocked(): Boolean = !isCorpse() && unlocked

    /** `Container::isInbox` (container.hpp:195-197); only [org.opentibia.canary.items.containers.Inbox] returns true. */
    open fun isInbox(): Boolean = false

    /** `Container::hasPagination` (container.hpp:255-257). */
    fun hasPagination(): Boolean = pagination

    /** `Container::getWeight` (container.cpp:291-293): own weight plus every descendant. */
    override val weight: Double
        get() = super.weight + totalWeight

    // --- parent/root lookup (container.cpp:114-143) ---------------------------

    private fun getParentContainer(): Container? = getParent()?.getContainer()

    /**
     * `Container::getTopParentContainer` (container.cpp:122-139). The result is
     * the outermost container the parent chain reaches under the shared
     * container-queries rule the C++ loop implements.
     */
    private fun getTopParentContainer(): Container? {
        var thing: Cylinder? = getParent()
        var prevThing: Cylinder? = getContainer()
        if (thing == null) {
            return prevThing?.getContainer()
        }
        while (thing != null) {
            val parent = thing.getParent() ?: break
            if (parent.getContainer() == null) {
                break
            }
            prevThing = thing
            thing = parent
        }
        return prevThing?.getContainer() ?: thing.getContainer()
    }

    /** `Container::getRootContainer` (container.cpp:141-143). */
    fun getRootContainer(): Container? = getTopParentContainer()

    // --- cylinder overrides (container.hpp:170-180, 259-372) ------------------

    override fun getContainer(): Container = this

    /** `Container::getCylinder` (src/items/containers/container.hpp:178-180). */
    override fun getCylinder(): Cylinder = this

    override fun getItem(): Item = this

    override fun getParent(): Cylinder? = super<Item>.getParent()

    override fun isRemoved(): Boolean = super<Item>.isRemoved()

    override fun internalAddThing(thing: Thing?) {
        internalAddThing(0, thing)
    }

    /** `Container::internalAddThing` (container.cpp:1072-1087). */
    override fun internalAddThing(index: Int, thing: Thing?) {
        val item = thing?.getItem() ?: return
        item.setParent(getContainer())
        // Preserve deserialized child order by inserting loaded items at the front.
        itemlist.addFirst(item)
        updateItemWeight(item.weight)
        updateCacheOnAdd(item)
    }

    // --- add / update / replace (container.cpp:155-160, 773-868) --------------

    /** `Container::addItem` (container.cpp:155-160). */
    fun addItem(item: Item) {
        itemlist.addLast(item)
        item.setParent(getContainer())
        updateCacheOnAdd(item)
        updateItemWeight(item.weight)
    }

    /** `Container::addItemBack` (container.cpp:803-810). */
    fun addItemBack(item: Item) {
        addItem(item)
        if (getParent() != null) {
            onAddContainerItem(item)
        }
    }

    /** `Container::addThing` (container.cpp:773-775). */
    override fun addThing(thing: Thing?) = addThing(0, thing)

    /** `Container::addThing` (container.cpp:777-801). */
    override fun addThing(index: Int, thing: Thing?) {
        val item = thing?.getItem() ?: return
        if (index >= capacity) {
            return
        }

        updateCacheOnAdd(item)
        item.setParent(getContainer())
        itemlist.addFirst(item)
        updateItemWeight(item.weight)

        if (getParent() != null) {
            onAddContainerItem(item)
        }
    }

    /** `Container::updateThing` (container.cpp:812-834). */
    override fun updateThing(thing: Thing?, itemId: Int, count: Int) {
        val item = thing?.getItem() ?: return
        val index = getThingIndex(item)
        if (index == -1) {
            return
        }

        val oldWeight = item.weight
        updateCacheOnRemove(item)
        // `Item::setID` also recalculates decay state (src/items/item.cpp:793-811);
        // the decay engine is a PARITY GAP.
        item.id = itemId
        item.setSubType(count)
        updateCacheOnAdd(item)
        updateItemWeight(-oldWeight + item.weight)

        if (getParent() != null) {
            onUpdateContainerItem(index, item, item)
        }
    }

    /** `Container::replaceThing` (container.cpp:836-868). */
    override fun replaceThing(index: Int, thing: Thing?) {
        val item = thing?.getItem() ?: return
        if (index >= itemlist.size) {
            return
        }

        val replacedItem = getItemByIndex(index) ?: return

        itemlist[index] = item
        item.setParent(getContainer())

        updateCacheOnRemove(replacedItem)
        updateCacheOnAdd(item)
        updateItemWeight(-replacedItem.weight + item.weight)

        if (getParent() != null) {
            onUpdateContainerItem(index, replacedItem, item)
        }

        replacedItem.resetParent()
    }

    // --- remove (container.cpp:870-974, 1128-1202) ----------------------------

    /** `Container::removeThing` (container.cpp:870-898). */
    override fun removeThing(thing: Thing?, count: Int) {
        val item = thing?.getItem() ?: return
        val index = getThingIndex(item)
        if (index == -1) {
            return
        }

        if (item.isStackable() && count != item.getItemCount()) {
            val newCount = maxOf(0, item.getItemCount() - count)
            val oldWeight = item.weight
            item.setItemCount(newCount)
            updateItemWeight(-oldWeight + item.weight)

            if (getParent() != null) {
                onUpdateContainerItem(index, item, item)
            }
        } else {
            removeItemByIndex(index, count)

            if (isCorpse() && empty()) {
                clearLootHighlight()
            }
        }
    }

    /** `Container::removeItemByIndex` (container.cpp:900-945). */
    open fun removeItemByIndex(index: Int, count: Int) {
        if (index >= itemlist.size || count == 0) {
            return
        }

        val removed = itemlist[index]
        var weightDiff = 0.0

        if (removed.isStackable() && count < removed.getItemCount()) {
            val oldWeight = removed.weight
            removed.setItemCount(removed.getItemCount() - count)
            weightDiff = -oldWeight + removed.weight

            if (getParent() != null) {
                onUpdateContainerItem(index, removed, removed)
            }
        } else {
            weightDiff = -removed.weight
            updateCacheOnRemove(removed)

            if (getParent() != null) {
                onRemoveContainerItem(index, removed)
            }

            removed.stopDecaying()
            removed.onRemoved()

            removed.resetParent()
            itemlist.removeAt(index)

            if (getParent() != null) {
                postRemoveNotification(removed, null, index, CylinderLink.LINK_PARENT)
            }
        }

        updateItemWeight(weightDiff)
    }

    /**
     * `Container::removeItemById` (container.cpp:947-974). The C++
     * `g_game().internalRemoveItem(item, removeCount)` call is replaced by the
     * local model removal; the `g_game` notification/decay side effects are a
     * PARITY GAP.
     */
    fun removeItemById(itemId: Int, count: Int, subType: Int = -1): Boolean {
        if (getItemTypeCount(itemId, subType) < count) {
            return false
        }

        var removed = 0
        for (item in getItems()) {
            if (item.id != itemId) {
                continue
            }
            if (subType > -1 && item.getSubType() != subType) {
                continue
            }

            val stack = item.getItemCount()
            val removeCount = minOf(stack, count - removed)
            removeItemByIndex(getThingIndex(item), removeCount)
            removed += removeCount
            if (removed >= count) {
                break
            }
        }

        return removed == count
    }

    /** `Container::removeAllItems` (container.cpp:1128-1164). */
    fun removeAllItems(actor: ContainerBatchActor? = null, isRecursive: Boolean = false): Int {
        beginBatchUpdate()

        var removedCount = 0

        if (isRecursive) {
            val itemsToRemove = mutableListOf<Item>()
            val iterator = iterator()
            while (iterator.hasNext()) {
                iterator.current()?.let { itemsToRemove.add(it) }
                iterator.advance()
            }

            for (item in itemsToRemove) {
                removeThing(item, item.getItemCount())
                ++removedCount
            }
        } else {
            for (index in itemlist.size - 1 downTo 0) {
                val item = itemlist[index]
                removeItemByIndex(index, item.getItemCount())
                ++removedCount
            }
        }

        endBatchUpdate(actor)

        return removedCount
    }

    /** `Container::removeItem` (container.cpp:1166-1190). */
    fun removeItem(thing: Item, sendUpdateToClient: Boolean = false) {
        val itemToRemove = thing
        val it = itemlist.indexOfFirst { it === itemToRemove }
        if (it >= 0) {
            if (sendUpdateToClient) {
                val thingIndex = getThingIndex(thing)
                if (thingIndex != -1 && getParent() != null) {
                    onRemoveContainerItem(thingIndex, itemToRemove)
                }
            }

            itemlist.removeAt(it)
            itemToRemove.resetParent()

            if (isCorpse() && empty()) {
                clearLootHighlight()
            }
        }
    }

    /** `Container::clearLootHighlight` (container.cpp:1191-1202); client refresh is a PARITY GAP. */
    open fun clearLootHighlight(player: Player? = null) {
        if (!isCorpse()) {
            return
        }
        if (!mLootHighlightActive) {
            return
        }
        mLootHighlightActive = false
        // Item::sendUpdateToClient(player) needs the protocol layer (PARITY GAP).
    }

    /** `Container::hasLootHighlight` (container.hpp:318-320). */
    fun hasLootHighlight(): Boolean = mLootHighlightActive

    /** `Container::beginBatchUpdate` (container.cpp:1109-1111). */
    open fun beginBatchUpdate() {
        ++m_batching
    }

    /** `Container::endBatchUpdate` (container.cpp:1113-1126). */
    open fun endBatchUpdate(actor: ContainerBatchActor? = null) {
        if (m_batching == 0) {
            return
        }

        --m_batching
        if (m_batching > 0) {
            return
        }

        if (actor != null) {
            actor.sendBatchUpdateContainer(this, true)
        }
    }

    // --- queries (container.cpp:535-771) --------------------------------------

    /** `Container::queryAdd` (container.cpp:535-623). */
    override fun queryAdd(index: Int, thing: Thing?, count: Int, flags: Int, actor: Creature?): ReturnValue {
        val item = thing?.getItem() ?: return ReturnValue.NOTPOSSIBLE
        if (hasBitSet(CylinderFlags.FLAG_CHILDISOWNER, flags)) {
            // A child container is querying; as the top container (not carried
            // by a player) just return with no error.
            return ReturnValue.NOERROR
        }

        if (!unlocked && !hasBitSet(CylinderFlags.FLAG_LOOTPOUCH, flags)) {
            return ReturnValue.NOTPOSSIBLE
        }

        if (!item.isPickupable()) {
            return ReturnValue.CANNOTPICKUP
        }

        if (item === getItem()) {
            return ReturnValue.THISISIMPOSSIBLE
        }

        if (item.hasOwner()) {
            val toPlayer = getTopParent()?.getPlayer()
            if (toPlayer != null && !item.isOwner(toPlayer)) {
                return ReturnValue.ITEMISNOTYOURS
            }

            if (hasOwner() && getOwnerId() != item.getOwnerId()) {
                return ReturnValue.ITEMISNOTYOURS
            }
        }

        var cylinder: Cylinder? = getParent()
        val noLimit = hasBitSet(CylinderFlags.FLAG_NOLIMIT, flags)
        while (cylinder != null) {
            if (cylinder === item) {
                return ReturnValue.THISISIMPOSSIBLE
            }

            val container = cylinder.getContainer()
            if (!noLimit && container != null && container.isInbox()) {
                return ReturnValue.CONTAINERNOTENOUGHROOM
            }

            val parent = cylinder.getParent()
            if (cylinder === parent) {
                return ReturnValue.NOTPOSSIBLE
            }
            cylinder = parent
        }

        if (!noLimit && index == INDEX_WHEREEVER && size() >= capacity && !hasPagination()) {
            return ReturnValue.CONTAINERNOTENOUGHROOM
        }

        val topParentContainer = getTopParentContainer()
        if (topParentContainer != null) {
            val addContainer = item.getContainer()
            if (addContainer != null) {
                val addContainerCount = addContainer.getContainerHoldingCount() + 1
                if (addContainerCount + topParentContainer.getContainerHoldingCount() > MAX_CONTAINER) {
                    return ReturnValue.CONTAINERISFULL
                }

                val addItemCount = addContainer.getItemHoldingCount() + 1
                if (addItemCount + topParentContainer.getItemHoldingCount() > mMaxItems) {
                    return ReturnValue.CONTAINERISFULL
                }
            } else if (topParentContainer.getItemHoldingCount() + 1 > mMaxItems) {
                return ReturnValue.CONTAINERISFULL
            }
        }

        if (isQuiver() && item.getWeaponType() != WeaponType_t.WEAPON_AMMO) {
            return ReturnValue.ONLYAMMOINQUIVER
        }

        val topParent = getTopParent()
        return if (topParent !== getContainer()) {
            topParent?.queryAdd(INDEX_WHEREEVER, item, count, flags or CylinderFlags.FLAG_CHILDISOWNER, actor)
                ?: ReturnValue.NOTPOSSIBLE
        } else {
            ReturnValue.NOERROR
        }
    }

    /** `Container::queryMaxCount` (container.cpp:625-672). */
    override fun queryMaxCount(
        index: Int,
        thing: Thing?,
        count: Int,
        maxQueryCount: Int,
        flags: Int,
    ): QueryMaxCountResult {
        val item = thing?.getItem() ?: return QueryMaxCountResult(ReturnValue.NOTPOSSIBLE, maxQueryCount)
        if (hasBitSet(CylinderFlags.FLAG_NOLIMIT, flags) || hasPagination()) {
            return QueryMaxCountResult(ReturnValue.NOERROR, maxOf(1, count))
        }

        val freeSlots = maxOf(capacity - size(), 0)
        var result: Int

        if (item.isStackable()) {
            var n = 0

            if (index == INDEX_WHEREEVER) {
                // Iterate through every item and check how much free stackable slots there is.
                var slotIndex = 0
                for (containerItem in itemlist) {
                    if (containerItem !== item &&
                        containerItem.equals(item) &&
                        containerItem.getItemCount() < containerItem.getStackSize()
                    ) {
                        val remainder = containerItem.getStackSize() - containerItem.getItemCount()
                        if (queryAdd(slotIndex++, item, remainder, flags) == ReturnValue.NOERROR) {
                            n += remainder
                        }
                    }
                }
            } else {
                val destItem = getItemByIndex(index)
                if (destItem != null && item.equals(destItem) && destItem.getItemCount() < destItem.getStackSize()) {
                    n = destItem.getStackSize() - destItem.getItemCount()
                }
            }

            // result is the limit of items I can add
            result = freeSlots * item.getStackSize() + n
            if (result < count) {
                return QueryMaxCountResult(ReturnValue.CONTAINERNOTENOUGHROOM, result)
            }
        } else {
            result = freeSlots
            if (result == 0) {
                return QueryMaxCountResult(ReturnValue.CONTAINERNOTENOUGHROOM, result)
            }
        }
        return QueryMaxCountResult(ReturnValue.NOERROR, result)
    }

    /** `Container::queryRemove` (container.cpp:674-701); the HouseTile seam is a PARITY GAP. */
    override fun queryRemove(thing: Thing?, count: Int, flags: Int, actor: Creature?): ReturnValue {
        val item = thing?.getItem() ?: return ReturnValue.NOTPOSSIBLE
        val index = getThingIndex(item)
        if (index == -1) {
            return ReturnValue.NOTPOSSIBLE
        }

        if (count == 0 || (item.isStackable() && count > item.getItemCount())) {
            return ReturnValue.NOTPOSSIBLE
        }

        if (!item.isMovable() && !hasBitSet(CylinderFlags.FLAG_IGNORENOTMOVABLE, flags)) {
            return ReturnValue.NOTMOVABLE
        }

        // `HouseTile::queryRemove` (container.cpp:696-699) needs the map/house
        // layer and is a PARITY GAP.
        return ReturnValue.NOERROR
    }

    /** `Container::queryDestination` (container.cpp:703-771). */
    override fun queryDestination(index: Int, thing: Thing?, destItem: Item?, flags: Int): QueryDestinationResult {
        val item = thing?.getItem() ?: return QueryDestinationResult(getContainer(), index, destItem, flags)
        var newIndex = index
        var newDestItem = destItem

        if (!unlocked && !hasBitSet(CylinderFlags.FLAG_LOOTPOUCH, flags)) {
            return QueryDestinationResult(getContainer(), newIndex, null, flags)
        }

        if (index == 254 /* move up */) {
            newIndex = INDEX_WHEREEVER
            newDestItem = null

            val parentContainer = getParent() as? Container
            return QueryDestinationResult(parentContainer ?: getContainer(), newIndex, newDestItem, flags)
        }

        // If a maximized container shows all 20 slots and a bag inside it is
        // opened, the client may compute a slot position as if the bag has 20
        // slots; anything out of range falls back to "add wherever".
        val isOutOfRange = index >= capacity && !hasPagination()
        if (index == 255 /* add wherever */ || isOutOfRange) {
            newIndex = INDEX_WHEREEVER
            newDestItem = null
        }

        if (newIndex != INDEX_WHEREEVER) {
            val itemFromIndex = getItemByIndex(newIndex)
            if (itemFromIndex != null) {
                newDestItem = itemFromIndex
            }

            val subCylinder = newDestItem as? Cylinder
            if (subCylinder != null) {
                return QueryDestinationResult(subCylinder, INDEX_WHEREEVER, null, flags)
            }
        }

        val autoStack = !hasBitSet(CylinderFlags.FLAG_IGNOREAUTOSTACK, flags)
        if (autoStack && item.isStackable() && item.getParent() !== getContainer()) {
            if (newDestItem != null &&
                newDestItem.equals(item) &&
                newDestItem.getItemCount() < newDestItem.getStackSize()
            ) {
                return QueryDestinationResult(getContainer(), newIndex, newDestItem, flags)
            }

            // try find a suitable item to stack with
            var n = 0
            for (listItem in itemlist) {
                if (listItem !== item &&
                    listItem.equals(item) &&
                    listItem.getItemCount() < listItem.getStackSize()
                ) {
                    newDestItem = listItem
                    newIndex = n
                    return QueryDestinationResult(getContainer(), newIndex, newDestItem, flags)
                }
                ++n
            }
        }
        return QueryDestinationResult(getContainer(), newIndex, newDestItem, flags)
    }

    // --- index/count helpers (container.cpp:976-1038) -------------------------

    /** `Container::getThingIndex` (container.cpp:976-995). */
    override fun getThingIndex(thing: Thing?): Int = itemlist.indexOfFirst { it === thing }

    /** `Container::getFirstIndex` (container.cpp:997-999). */
    override fun getFirstIndex(): Int = 0

    /** `Container::getLastIndex` (container.cpp:1001-1003). */
    override fun getLastIndex(): Int = size()

    /** `Container::getItemTypeCount` (container.cpp:1005-1013). */
    override fun getItemTypeCount(itemId: Int, subType: Int): Int {
        var count = 0
        for (item in itemlist) {
            if (item.id == itemId) {
                count += Item.countByType(item, subType)
            }
        }
        return count
    }

    /** `Container::getAllItemTypeCount` (container.cpp:1015-1020). */
    override fun getAllItemTypeCount(countMap: MutableMap<Int, Int>): MutableMap<Int, Int> {
        for (item in itemlist) {
            countMap[item.id] = (countMap[item.id] ?: 0) + item.getItemCount()
        }
        return countMap
    }

    /** `Container::getThing` (container.cpp:1022-1024). */
    override fun getThing(index: Int): Item? = getItemByIndex(index)

    /** `Container::getItems` (container.cpp:1026-1038). */
    fun getItems(recursive: Boolean = false): List<Item> {
        val containerItems = mutableListOf<Item>()
        if (recursive) {
            val iterator = iterator()
            while (iterator.hasNext()) {
                iterator.current()?.let { containerItems.add(it) }
                iterator.advance()
            }
        } else {
            containerItems.addAll(itemlist)
        }
        return containerItems
    }

    /** `Container::getItemByIndex` (container.cpp:398-404). */
    fun getItemByIndex(index: Int): Item? {
        if (index < 0 || index >= size()) {
            return null
        }
        return itemlist[index]
    }

    /** `Container::isHoldingItem` (container.cpp:414-426). */
    fun isHoldingItem(item: Item): Boolean {
        val iterator = iterator()
        while (iterator.hasNext()) {
            if (iterator.current() === item) {
                return true
            }
            iterator.advance()
        }
        return false
    }

    /** `Container::isHoldingItemWithId` (container.cpp:428-440). */
    fun isHoldingItemWithId(id: Int): Boolean {
        val iterator = iterator()
        while (iterator.hasNext()) {
            if (iterator.current()?.id == id) {
                return true
            }
            iterator.advance()
        }
        return false
    }

    /** `Container::isInsideContainerWithId` (container.cpp:442-451). */
    fun isInsideContainerWithId(id: Int): Boolean {
        var nextParent = getParent()
        while (nextParent != null && nextParent.getContainer() != null) {
            if (nextParent.getContainer()?.id == id) {
                return true
            }
            nextParent = nextParent.getRealParent()
        }
        return false
    }

    /** `Container::getItemHoldingCount` (container.cpp:406-408). */
    fun getItemHoldingCount(): Int = m_cachedItemCount

    /** `Container::getContainerHoldingCount` (container.cpp:410-412). */
    fun getContainerHoldingCount(): Int = m_cachedContainerCount

    /** `Container::getFreeSlots` (container.cpp:1089-1099). */
    fun getFreeSlots(): Int {
        var counter = maxOf(0, capacity - size())
        for (item in itemlist) {
            val container = item.getContainer()
            if (container != null) {
                counter += maxOf(0, container.getFreeSlots())
            }
        }
        return counter
    }

    /** `Container::getOwnerId` (container.cpp:1204-1216). */
    override fun getOwnerId(): Long {
        val ownerId = super.getOwnerId()
        if (ownerId > 0) {
            return ownerId
        }
        for (item in itemlist) {
            val childOwner = item.getOwnerId()
            if (childOwner > 0) {
                return childOwner
            }
        }
        return 0
    }

    // --- reward chest predicates (container.cpp:453-463) ----------------------

    /** `Container::isAnyKindOfRewardChest` (container.cpp:453-455). */
    fun isAnyKindOfRewardChest(): Boolean =
        id == ITEM_REWARD_CHEST ||
            (id == ITEM_REWARD_CONTAINER && getParent()?.getContainer()?.id == ITEM_REWARD_CHEST) ||
            isBrowseFieldAndHoldsRewardChest()

    /** `Container::isAnyKindOfRewardContainer` (container.cpp:457-459). */
    fun isAnyKindOfRewardContainer(): Boolean =
        id == ITEM_REWARD_CHEST ||
            id == ITEM_REWARD_CONTAINER ||
            isHoldingItemWithId(ITEM_REWARD_CONTAINER) ||
            isInsideContainerWithId(ITEM_REWARD_CONTAINER)

    /** `Container::isBrowseFieldAndHoldsRewardChest` (container.cpp:461-463). */
    fun isBrowseFieldAndHoldsRewardChest(): Boolean =
        id == ITEM_BROWSEFIELD && isHoldingItemWithId(ITEM_REWARD_CHEST)

    /** `Container::isBrowsefield` (container.hpp:327-334). */
    override fun isBrowsefield(): Boolean = id == ITEM_BROWSEFIELD

    // --- store inbox (container.cpp:323-396) ----------------------------------

    /** `Container::isStoreInbox` (container.cpp:323-325). */
    fun isStoreInbox(): Boolean = id == ITEM_STORE_INBOX

    /** `Container::isStoreInboxFiltered` (container.cpp:327-334). */
    fun isStoreInboxFiltered(): Boolean {
        val attribute = getAttribute(ItemAttribute.STORE_INBOX_CATEGORY) as? String ?: ""
        return isStoreInbox() && attribute.isNotEmpty() && attribute != "All"
    }

    /**
     * `Container::getStoreInboxFilteredItems` (container.cpp:336-358). The
     * `unWrapId` custom attribute (item.cpp custom attributes) is not modeled;
     * only the primary type comparison is ported (PARITY GAP).
     */
    fun getStoreInboxFilteredItems(): List<Item> {
        if (!isStoreInboxFiltered()) {
            return emptyList()
        }

        val category = getAttribute(ItemAttribute.STORE_INBOX_CATEGORY) as? String ?: ""
        val result = mutableListOf<Item>()
        for (item in getItemList()) {
            val itemType = item.type ?: continue
            if (toPascalCase(itemType.primaryType) == toPascalCase(category)) {
                result.add(item)
            }
        }
        return result
    }

    /** `Container::getFilteredItemByIndex` (container.cpp:382-396). */
    fun getFilteredItemByIndex(index: Int): Item? {
        val filteredItems = getStoreInboxFilteredItems()
        if (index < 0 || index >= filteredItems.size) {
            return null
        }

        val item = filteredItems[index]
        return itemlist.firstOrNull { it === item }
    }

    // --- notifications (container.cpp:465-533, 1040-1066) ---------------------

    /** `Container::postAddNotification` (container.cpp:1040-1052). */
    override fun postAddNotification(thing: Thing?, oldParent: Cylinder?, index: Int, link: CylinderLink) {
        // The C++ `getTopParent()` reaches the holding `Player` because the
        // player is a `Cylinder`; the port resolves the recorded holding link
        // instead (`Item::getHoldingPlayer`, item.cpp:879) and routes the same
        // `LINK_TOPPARENT` notification.
        getHoldingPlayer()?.let { holder ->
            holder.postAddNotification(thing, oldParent, index, CylinderLink.LINK_TOPPARENT)
            return
        }

        val topParent = getTopParent() ?: return
        if (topParent.getCreature() != null) {
            topParent.postAddNotification(thing, oldParent, index, CylinderLink.LINK_TOPPARENT)
        } else if (topParent === getContainer()) {
            // let the tile class notify surrounding players
            getParent()?.postAddNotification(thing, oldParent, index, CylinderLink.LINK_NEAR)
        } else {
            topParent.postAddNotification(thing, oldParent, index, CylinderLink.LINK_PARENT)
        }
    }

    /** `Container::postRemoveNotification` (container.cpp:1054-1066). */
    override fun postRemoveNotification(thing: Thing?, newParent: Cylinder?, index: Int, link: CylinderLink) {
        // See [postAddNotification]: the recorded holding link routes the
        // `LINK_TOPPARENT` notification the C++ `getTopParent()` creature
        // branch would.
        getHoldingPlayer()?.let { holder ->
            holder.postRemoveNotification(thing, newParent, index, CylinderLink.LINK_TOPPARENT)
            return
        }

        val topParent = getTopParent() ?: return
        if (topParent.getCreature() != null) {
            topParent.postRemoveNotification(thing, newParent, index, CylinderLink.LINK_TOPPARENT)
        } else if (topParent === getContainer()) {
            // let the tile class notify surrounding players
            getParent()?.postRemoveNotification(thing, newParent, index, CylinderLink.LINK_NEAR)
        } else {
            topParent.postRemoveNotification(thing, newParent, index, CylinderLink.LINK_PARENT)
        }
    }

    // Spectators()/sendAddContainerItem/onAddContainerItem are the client
    // update layer (container.cpp:465-533); without the map/spectator services
    // they are no-ops (PARITY GAP).
    private fun onAddContainerItem(item: Item) {
        if (m_batching > 0) {
            return
        }
        // sendAddContainerItem(getContainer(), item) needs Spectators (PARITY GAP).
    }

    private fun onUpdateContainerItem(index: Int, oldItem: Item, newItem: Item) {
        if (m_batching > 0) {
            return
        }
        // sendUpdateContainerItem needs Spectators (PARITY GAP).
    }

    private fun onRemoveContainerItem(index: Int, item: Item) {
        if (m_batching > 0) {
            return
        }
        // sendRemoveContainerItem needs Spectators (PARITY GAP).
    }

    // --- cached weight/count bookkeeping (container.cpp:235-289) --------------

    /** `Container::updateItemWeight` (container.cpp:235-241). */
    private fun updateItemWeight(diff: Double) {
        totalWeight += diff
        val visited = Collections.newSetFromMap(IdentityHashMap<Container, Boolean>())
        visited.add(getContainer())
        var parentContainer: Container? = getContainer().getParentContainer()
        while (parentContainer != null && visited.add(parentContainer)) {
            parentContainer.totalWeight += diff
            parentContainer = parentContainer.getParentContainer()
        }
    }

    /** `Container::updateCacheOnAdd` (container.cpp:243-257). */
    private fun updateCacheOnAdd(item: Item) {
        updateCacheOnAdd(item, Collections.newSetFromMap(IdentityHashMap<Container, Boolean>()))
    }

    private fun updateCacheOnAdd(item: Item, visited: MutableSet<Container>) {
        // A self/cyclic parent chain cannot happen through game moves, but the
        // model tolerates it (AGENTS.md bounded-recursion rule).
        if (!visited.add(this)) {
            return
        }

        val subContainer = item.getContainer()
        if (subContainer != null) {
            // Adding a container: count the container itself (+1) plus all its
            // internal items and containers.
            m_cachedItemCount += 1 + subContainer.m_cachedItemCount
            m_cachedContainerCount += 1 + subContainer.m_cachedContainerCount
        } else {
            m_cachedItemCount += 1
        }

        getParentContainer()?.updateCacheOnAdd(item, visited)
    }

    /** `Container::updateCacheOnRemove` (container.cpp:259-275). */
    private fun updateCacheOnRemove(item: Item) {
        updateCacheOnRemove(item, Collections.newSetFromMap(IdentityHashMap<Container, Boolean>()))
    }

    private fun updateCacheOnRemove(item: Item, visited: MutableSet<Container>) {
        if (!visited.add(this)) {
            return
        }

        val subContainer = item.getContainer()
        if (subContainer != null) {
            // Removing a container: subtract one (itself) plus all items and
            // containers within it.
            val itemDelta = 1 + subContainer.m_cachedItemCount
            val containerDelta = 1 + subContainer.m_cachedContainerCount
            m_cachedItemCount = if (m_cachedItemCount > itemDelta) m_cachedItemCount - itemDelta else 0
            m_cachedContainerCount = if (m_cachedContainerCount > containerDelta) {
                m_cachedContainerCount - containerDelta
            } else {
                0
            }
        } else {
            m_cachedItemCount = if (m_cachedItemCount > 0) m_cachedItemCount - 1 else 0
        }

        getParentContainer()?.updateCacheOnRemove(item, visited)
    }

    /** `Container::updateItemCountCache` (container.cpp:277-289). */
    fun updateItemCountCache() {
        m_cachedItemCount = 0
        m_cachedContainerCount = 0

        for (item in getItemList()) {
            ++m_cachedItemCount
            val sub = item.getContainer()
            if (sub != null) {
                sub.updateItemCountCache()
                m_cachedItemCount += sub.getItemHoldingCount()
                m_cachedContainerCount += 1 + sub.getContainerHoldingCount()
            }
        }
    }

    // --- serialization (container.cpp:176-184) --------------------------------

    /** `Container::readAttr` (container.cpp:176-184). */
    override fun readAttr(attr: Int, propStream: PropStream): AttrReadValue {
        if (attr == ATTR_CONTAINER_ITEMS) {
            val count = propStream.readU32() ?: return AttrReadValue.ATTR_READ_ERROR
            serializationCount = count.toInt()
            return AttrReadValue.ATTR_READ_END
        }
        return super.readAttr(attr, propStream)
    }

    companion object {
        /** `Container::create(uint16_t type)` (container.cpp:39-41) over container.cpp:17-31. */
        fun create(
            type: Int,
            registry: ItemTypeRegistry? = null,
            maxContainerItem: Int = MAX_CONTAINER_ITEM,
            lootPouchMaxLimit: Int = LOOTPOUCH_MAXLIMIT,
            storeInboxMaxLimit: Int = STOREINBOX_MAXLIMIT,
        ): Container {
            val itemType = registry?.get(type)
            val container = Container(type, capacity = itemType?.capacity ?: 0, type = itemType, maxItems = maxContainerItem)

            if (type == ITEM_GOLD_POUCH) {
                container.pagination = true
                container.mMaxItems = lootPouchMaxLimit
                container.capacity = 32
            }

            if (container.isStoreInbox()) {
                container.pagination = true
                container.mMaxItems = storeInboxMaxLimit
                container.capacity = 32
            }

            return container
        }

        /** `Container::create(uint16_t type, uint16_t size, bool, bool)` (container.cpp:43-45). */
        fun create(type: Int, size: Int, unlocked: Boolean = true, pagination: Boolean = false): Container =
            Container(type, size, unlocked = unlocked, pagination = pagination)

        /** `MAX_CONTAINER_ITEM` config default (configmanager.cpp:307). */
        const val MAX_CONTAINER_ITEM = 5000

        /** `MAX_CONTAINER` config default (configmanager.cpp:308). */
        const val MAX_CONTAINER = 500

        /** `MAX_CONTAINER_DEPTH` config default (configmanager.cpp:309). */
        const val MAX_CONTAINER_DEPTH = 200

        /** `LOOTPOUCH_MAXLIMIT` config default (configmanager.cpp:299). */
        const val LOOTPOUCH_MAXLIMIT = 2000

        /** `STOREINBOX_MAXLIMIT` config default (configmanager.cpp:360). */
        const val STOREINBOX_MAXLIMIT = 2000

        /** `ITEM_BROWSEFIELD` (src/utils/utils_definitions.hpp:551). */
        const val ITEM_BROWSEFIELD = 470

        /** `ITEM_REWARD_CONTAINER` (src/utils/utils_definitions.hpp:616). */
        const val ITEM_REWARD_CONTAINER = 19202

        /** `ITEM_REWARD_CHEST` (src/utils/utils_definitions.hpp:617). */
        const val ITEM_REWARD_CHEST = 19250

        /** `ITEM_STORE_INBOX` (src/utils/utils_definitions.hpp:623). */
        const val ITEM_STORE_INBOX = 23396

        /** `ITEM_GOLD_POUCH` (src/utils/utils_definitions.hpp:557). */
        const val ITEM_GOLD_POUCH = 23721
    }
}

/** `Container::toPascalCase` helper (src/utils/tools.cpp:360-379). */
private fun toPascalCase(str: String): String {
    val result = StringBuilder()
    var capitalizeNext = true

    for (ch in str) {
        if (ch == '_' || ch.isWhitespace() || ch == '-') {
            capitalizeNext = true
        } else {
            if (capitalizeNext) {
                result.append(ch.uppercaseChar())
                capitalizeNext = false
            } else {
                result.append(ch)
            }
        }
    }

    return result.toString()
}

/** `hasBitSet` (src/utils/tools.hpp): mirrors `(value & flag) != 0`. */
private fun hasBitSet(flag: Int, flags: Int): Boolean = (flags and flag) != 0
