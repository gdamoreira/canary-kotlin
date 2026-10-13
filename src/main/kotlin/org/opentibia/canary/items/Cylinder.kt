package org.opentibia.canary.items

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.systems.rewardchest.ReturnValue

/** `INDEX_WHEREEVER` (src/items/cylinder.hpp:18): add to an empty position. */
const val INDEX_WHEREEVER: Int = -1

/** `enum CylinderLink_t` (src/items/items_definitions.hpp:500-505). */
enum class CylinderLink {
    LINK_OWNER,
    LINK_PARENT,
    LINK_TOPPARENT,
    LINK_NEAR,
}

/** C++ spelling of [CylinderLink] (`CylinderLink_t`, items_definitions.hpp:500). */
typealias CylinderLink_t = CylinderLink

/**
 * Kotlin carrier for the `uint32_t &maxQueryCount` out parameter of
 * `Cylinder::queryMaxCount` (src/items/cylinder.hpp:45).
 */
data class QueryMaxCountResult(
    val returnValue: ReturnValue,
    val maxQueryCount: Int,
)

/**
 * Kotlin carrier for the mutated `index`/`destItem`/`flags` parameters of
 * `Cylinder::queryDestination` (src/items/cylinder.hpp:66).
 */
data class QueryDestinationResult(
    val cylinder: Cylinder?,
    val index: Int,
    val destItem: Item?,
    val flags: Int,
)

/**
 * Port of `class Cylinder` (src/items/cylinder.hpp:20-177) and its
 * `cylinder.cpp` base bodies. Out parameters become the [QueryMaxCountResult]
 * and [QueryDestinationResult] carriers; everything else keeps the exact C++
 * name and parameter order.
 */
interface Cylinder : Thing {
    /**
     * `Cylinder::queryAdd` — `-1` index means an empty position and
     * `FLAG_CHILDISOWNER`/`FLAG_NOLIMIT` modify the checks.
     */
    fun queryAdd(index: Int, thing: Thing?, count: Int, flags: Int, actor: Creature? = null): ReturnValue

    /** `Cylinder::queryMaxCount`. */
    fun queryMaxCount(index: Int, thing: Thing?, count: Int, maxQueryCount: Int, flags: Int): QueryMaxCountResult

    /** `Cylinder::queryRemove`. */
    fun queryRemove(thing: Thing?, count: Int, flags: Int, actor: Creature? = null): ReturnValue

    /** `Cylinder::queryDestination`; may redirect the destination cylinder/index. */
    fun queryDestination(index: Int, thing: Thing?, destItem: Item?, flags: Int): QueryDestinationResult

    /** `Cylinder::addThing(thing)`. */
    fun addThing(thing: Thing?)

    /** `Cylinder::addThing(index, thing)`. */
    fun addThing(index: Int, thing: Thing?)

    /** `Cylinder::updateThing`. */
    fun updateThing(thing: Thing?, itemId: Int, count: Int)

    /** `Cylinder::replaceThing`. */
    fun replaceThing(index: Int, thing: Thing?)

    /** `Cylinder::removeThing`. */
    fun removeThing(thing: Thing?, count: Int)

    /** `Cylinder::postAddNotification`. */
    fun postAddNotification(
        thing: Thing?,
        oldParent: Cylinder?,
        index: Int,
        link: CylinderLink = CylinderLink.LINK_OWNER,
    )

    /** `Cylinder::postRemoveNotification`. */
    fun postRemoveNotification(
        thing: Thing?,
        newParent: Cylinder?,
        index: Int,
        link: CylinderLink = CylinderLink.LINK_OWNER,
    )

    /** `Cylinder::getThingIndex` (cylinder.cpp:14-16). */
    fun getThingIndex(thing: Thing?): Int = -1

    /** `Cylinder::getFirstIndex` (cylinder.cpp:18-20). */
    fun getFirstIndex(): Int = 0

    /** `Cylinder::getLastIndex` (cylinder.cpp:22-24). */
    fun getLastIndex(): Int = 0

    /** `Cylinder::getThing` (cylinder.cpp:34-36). */
    fun getThing(index: Int): Thing? = null

    /** `Cylinder::getItemTypeCount` (cylinder.cpp:26-28). */
    fun getItemTypeCount(itemId: Int, subType: Int = -1): Int = 0

    /** `Cylinder::getAllItemTypeCount` (cylinder.cpp:30-32). */
    fun getAllItemTypeCount(countMap: MutableMap<Int, Int>): MutableMap<Int, Int> = countMap

    /** `Cylinder::internalAddThing(thing)` (cylinder.cpp:38-40). */
    fun internalAddThing(thing: Thing?) {}

    /** `Cylinder::internalAddThing(index, thing)` (cylinder.cpp:42-44). */
    fun internalAddThing(index: Int, thing: Thing?) {}

    /** `Cylinder::startDecaying` (cylinder.cpp:46-48). */
    fun startDecaying() {}

    /** `Cylinder::isBrowsefield`; base returns `false`. */
    fun isBrowsefield(): Boolean = false
}

/**
 * Port of `class VirtualCylinder` (src/items/cylinder.hpp:179-217,
 * src/items/cylinder.cpp:12): the singleton cylinder that rejects every query.
 */
class VirtualCylinder : Cylinder {
    override fun getDescription(lookDistance: Int): String = ""

    override fun getThrowRange(): Int = 1

    override fun isPushable(): Boolean = false

    override fun isRemoved(): Boolean = false

    override fun queryAdd(index: Int, thing: Thing?, count: Int, flags: Int, actor: Creature?): ReturnValue =
        ReturnValue.NOTPOSSIBLE

    override fun queryMaxCount(
        index: Int,
        thing: Thing?,
        count: Int,
        maxQueryCount: Int,
        flags: Int,
    ): QueryMaxCountResult = QueryMaxCountResult(ReturnValue.NOTPOSSIBLE, maxQueryCount)

    override fun queryRemove(thing: Thing?, count: Int, flags: Int, actor: Creature?): ReturnValue =
        ReturnValue.NOTPOSSIBLE

    override fun queryDestination(index: Int, thing: Thing?, destItem: Item?, flags: Int): QueryDestinationResult =
        QueryDestinationResult(null, index, destItem, flags)

    override fun addThing(thing: Thing?) {}

    override fun addThing(index: Int, thing: Thing?) {}

    override fun updateThing(thing: Thing?, itemId: Int, count: Int) {}

    override fun replaceThing(index: Int, thing: Thing?) {}

    override fun removeThing(thing: Thing?, count: Int) {}

    override fun postAddNotification(thing: Thing?, oldParent: Cylinder?, index: Int, link: CylinderLink) {}

    override fun postRemoveNotification(thing: Thing?, newParent: Cylinder?, index: Int, link: CylinderLink) {}

    companion object {
        /** `VirtualCylinder::virtualCylinder` (cylinder.cpp:12). */
        val virtualCylinder = VirtualCylinder()
    }
}
