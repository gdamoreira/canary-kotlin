package org.opentibia.canary.items

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * World seam replacing the `g_game()` calls of `TrashHolder::addThing`
 * (src/items/trashholder.cpp:71-75). Every method transcribes an existing
 * `Game` member; the world is injected per AGENTS.md 1.1.
 */
interface TrashHolderWorld {
    /** `Game::internalRemoveItem` (src/game/game.cpp:2785). */
    fun internalRemoveItem(item: Item, count: Int = -1): ReturnValue

    /** `Game::addMagicEffect` (src/game/game.cpp). */
    fun addMagicEffect(position: Position, effect: Int)
}

/**
 * Port of `class TrashHolder` (src/items/trashholder.hpp:15-44,
 * src/items/trashholder.cpp:14-96): the item that deletes whatever is added to
 * it. The `g_game()` side effects are routed through the injected [world].
 */
class TrashHolder(
    id: Int,
    type: ItemType? = null,
    var world: TrashHolderWorld? = null,
) : Item(id, type = type), Cylinder {

    /** `TrashHolder::getTrashHolder` (src/items/trashholder.hpp:20-22). */
    override fun getTrashHolder(): TrashHolder = this

    /** `TrashHolder::getCylinder` (src/items/trashholder.hpp:24-26). */
    override fun getCylinder(): Cylinder = this

    /** `TrashHolder::queryAdd` (src/items/trashholder.cpp:14-27). */
    override fun queryAdd(index: Int, thing: Thing?, count: Int, flags: Int, actor: Creature?): ReturnValue {
        if (thing == null) {
            return ReturnValue.NOERROR
        }

        val item = thing.getItem() ?: return ReturnValue.NOERROR
        if (item.hasOwner() && !item.isOwner(actor)) {
            return ReturnValue.ITEMISNOTYOURS
        }
        return ReturnValue.NOERROR
    }

    /** `TrashHolder::queryMaxCount` (src/items/trashholder.cpp:29-32). */
    override fun queryMaxCount(
        index: Int,
        thing: Thing?,
        count: Int,
        maxQueryCount: Int,
        flags: Int,
    ): QueryMaxCountResult = QueryMaxCountResult(ReturnValue.NOERROR, maxOf(1, count))

    /** `TrashHolder::queryRemove` (src/items/trashholder.cpp:34-36). */
    override fun queryRemove(thing: Thing?, count: Int, flags: Int, actor: Creature?): ReturnValue =
        ReturnValue.NOTPOSSIBLE

    /** `TrashHolder::queryDestination` (src/items/trashholder.cpp:38-40). */
    override fun queryDestination(index: Int, thing: Thing?, destItem: Item?, flags: Int): QueryDestinationResult =
        QueryDestinationResult(this, index, destItem, flags)

    /** `TrashHolder::addThing(thing)` (src/items/trashholder.cpp:42-44). */
    override fun addThing(thing: Thing?) {
        addThing(0, thing)
    }

    /** `TrashHolder::addThing(index, thing)` (src/items/trashholder.cpp:46-76). */
    override fun addThing(index: Int, thing: Thing?) {
        if (thing == null) {
            return
        }

        val item = thing.getItem() ?: return
        if (item === this || !item.hasProperty(ItemProperty.CONST_PROP_MOVABLE)) {
            return
        }

        val itemType = type
        if (item.isHangable() && itemType?.isGroundTile == true) {
            val tile = getParent() as? Tile
            if (tile != null && tile.hasFlag(TileFlags.TILESTATE_SUPPORTS_HANGABLE.value)) {
                return
            }
        }

        if (item.isCarpet() || item.id == ITEM_DECORATION_KIT) {
            return
        }

        world?.internalRemoveItem(item)

        val magicEffect = itemType?.magicEffect ?: CONST_ME_NONE
        if (magicEffect != CONST_ME_NONE) {
            world?.addMagicEffect(position, magicEffect)
        }
    }

    /** `TrashHolder::updateThing` (src/items/trashholder.cpp:78-80). */
    override fun updateThing(thing: Thing?, itemId: Int, count: Int) {}

    /** `TrashHolder::replaceThing` (src/items/trashholder.cpp:82-84). */
    override fun replaceThing(index: Int, thing: Thing?) {}

    /** `TrashHolder::removeThing` (src/items/trashholder.cpp:86-88). */
    override fun removeThing(thing: Thing?, count: Int) {}

    /** `TrashHolder::postAddNotification` (src/items/trashholder.cpp:90-92). */
    override fun postAddNotification(thing: Thing?, oldParent: Cylinder?, index: Int, link: CylinderLink) {
        getParent()?.postAddNotification(thing, oldParent, index, CylinderLink.LINK_PARENT)
    }

    /** `TrashHolder::postRemoveNotification` (src/items/trashholder.cpp:94-96). */
    override fun postRemoveNotification(thing: Thing?, newParent: Cylinder?, index: Int, link: CylinderLink) {
        getParent()?.postRemoveNotification(thing, newParent, index, CylinderLink.LINK_PARENT)
    }

    companion object {
        /** `ITEM_DECORATION_KIT` (src/utils/utils_definitions.hpp:554). */
        const val ITEM_DECORATION_KIT = 23398

        /** `CONST_ME_NONE` (src/utils/utils_definitions.hpp:59). */
        private const val CONST_ME_NONE = 0
    }
}
