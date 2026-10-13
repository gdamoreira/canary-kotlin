package org.opentibia.canary.game

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.persistence.PlayerStorage
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.util.IdentityHashMap

/**
 * Faithful facade over the Kotlin world model mirroring Canary's `Game`
 * (src/game/game.hpp:95). Every method transcribes an existing `Game::` member;
 * globals (`g_game()`) are replaced by constructor injection of the [GameMap] and
 * [GameDispatcher] per AGENTS.md 1.1.
 *
 * Implemented surface:
 *  - `internalAddItem` (game.cpp:2693, 2698) / `internalRemoveItem` (game.cpp:2785)
 *  - `internalMoveItem` (game.cpp:2436)
 *  - `internalPlaceCreature` (game.cpp:1495) / `removeCreature` (game.cpp:1551)
 *  - `getPlayers` (game.hpp:577) / `getPlayerByName` (game.cpp:1394)
 *  - `addPlayer` (game.cpp:11624) / `removePlayer` (game.cpp:11631)
 *
 * All state is thread-confined: mutating methods must run inside the injected
 * [dispatcher]'s single-threaded `WorldCommit` lane (AGENTS.md 1.2).
 */
class Game(
    val map: GameMap,
    val dispatcher: GameDispatcher,
    private val playerStorage: PlayerStorage? = null,
) {
    private val players = mutableMapOf<Long, Player>()

    /** `Creature::getParent()` equivalent (src/creatures/creature.hpp): the tile each placed creature lives on. */
    private val placedCreatures = IdentityHashMap<Creature, Tile>()

    /** `Item::getParent()` (src/items/item.hpp): the tile each item added through this facade lives on. */
    private val itemParents = IdentityHashMap<Item, Tile>()

    /** `Game::addPlayer` (src/game/game.cpp:11624). */
    fun addPlayer(player: Player) {
        players[player.id] = player
    }

    /** `Game::removePlayer` (src/game/game.cpp:11631). */
    fun removePlayer(player: Player) {
        players.remove(player.id)
    }

    /** `Game::getPlayers` (src/game/game.hpp:577): online players keyed by creature id. */
    fun getPlayers(): Map<Long, Player> = players

    /**
     * `Game::getPlayerByName` (src/game/game.cpp:1394): empty names return null and
     * lookups are case-insensitive (`asLowerCaseString`). When the player is not
     * online, `allowOffline` falls back to [playerStorage] (`IOLoginData::loadPlayerByName`
     * equivalent); no online flag exists on the Kotlin player yet.
     */
    fun getPlayerByName(name: String, allowOffline: Boolean = false): Player? {
        if (name.isEmpty()) {
            return null
        }

        players.values.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it }
        if (!allowOffline) {
            return null
        }
        return playerStorage?.loadByName(name)
    }

    /**
     * Kotlin accessor for Canary's public `Game::map` member (src/game/game.hpp:627) as
     * used at call sites such as `g_game().map.getTile(pos)` (src/game/game.cpp:1117);
     * `Map::getTile` itself is defined at src/map/map.hpp:77.
     */
    fun getTile(position: Position): Tile? = map.getTile(position)

    /**
     * `Game::internalPlaceCreature` (src/game/game.cpp:1495) with the placement
     * fallback of `Map::placeCreature` (src/map/map.cpp:559).
     *
     * A creature is rejected when it already has a parent tile (creature.hpp
     * `getParent()`), and placement falls back to the surrounding tiles of
     * `Map::placeCreature`'s `normalRelList`/`extendedRelList`. `forced` mirrors
     * `forceLogin` and bypasses the tile `queryAdd` result. `creatureCheck` maps to
     * `addCreatureCheck`/`onPlacedCreature` (game.cpp:1516-1519), which have no Kotlin
     * counterpart yet and are intentionally accepted but not acted upon.
     *
     * Parity gaps: protection-zone, teleport and `isSightClear` checks need tile
     * flags/raycasting that the Kotlin [Tile] does not model; monsters' push/field
     * rules are approximated by a plain occupancy check.
     */
    fun internalPlaceCreature(
        creature: Creature,
        pos: Position,
        extendedPos: Boolean = false,
        forced: Boolean = false,
        creatureCheck: Boolean = false,
    ): Boolean {
        if (placedCreatures.containsKey(creature)) {
            return false
        }

        val centerTile = map.getTile(pos)
        val destination = if (centerTile != null && (forced || canAddCreature(centerTile))) {
            centerTile to pos
        } else {
            findFallbackTile(pos, extendedPos, forced)
        }
        if (destination == null) {
            return false
        }

        creature.position = destination.second
        placedCreatures[creature] = destination.first
        return true
    }

    /**
     * `Game::removeCreature` (src/game/game.cpp:1551): rejected for creatures already
     * flagged by `Creature::isRemoved()`, otherwise unlinks the creature from its tile
     * and calls `Creature::setRemoved()`. `isLogout` only feeds team-finder/house
     * bookkeeping in Canary that the Kotlin model does not have.
     *
     * Parity gap: Canary keeps going when `getTile()` is null (and would then fail on
     * `getParent()->postRemoveNotification`); the facade rejects a creature that was
     * never placed instead.
     */
    fun removeCreature(creature: Creature, isLogout: Boolean = true): Boolean {
        if (creature.isRemoved()) {
            return false
        }
        placedCreatures.remove(creature) ?: return false

        creature.setRemoved()
        return true
    }

    /**
     * `Game::internalAddItem` (src/game/game.cpp:2693 and 2698 remainder overload).
     *
     * The `Cylinder` flow is reduced to its [Tile] cases: `queryDestination`
     * (tile.cpp:1009, no floor change), `queryAdd` (tile.cpp:691), `queryMaxCount`
     * (tile.cpp:978) and stackable merge. The full-count remainder recursion is
     * inlined because a tile's `queryMaxCount` never caps a single call.
     *
     * Parity gaps: container destinations, mailboxes, hangable/always-on-top
     * ordering, blocking item checks and the `addedItem`/quiver branch of game.cpp
     * need item type data (ground/blockSolid/movable/stackSize/attributes) that the
     * Kotlin [Item]/[org.opentibia.canary.items.ItemType] do not model yet. The stack
     * cap uses Canary's `ItemType::stackSize` default of 100 (src/items/items.hpp:335)
     * because the Kotlin item type does not carry a per-item value.
     */
    fun internalAddItem(
        toTile: Tile?,
        item: Item?,
        index: Int = INDEX_WHEREEVER,
        flags: Int = 0,
        test: Boolean = false,
    ): ReturnValue {
        if (toTile == null || item == null) {
            return ReturnValue.NOTPOSSIBLE
        }

        val toItem = topDownItem(toTile)

        val ret = queryAdd(toTile, flags)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        val maxQueryCount = queryMaxCount(item.count)
        if (test) {
            return ReturnValue.NOERROR
        }

        if (isStackable(item) && toItem != null && sameItemType(item, toItem)) {
            val m = minOf(item.count, maxQueryCount)
            val n = mergeCapacity(toItem, m)
            toItem.count += n

            val remainder = m - n
            if (remainder > 0) {
                if (item.count == remainder) {
                    addThing(toTile, item, index)
                } else {
                    addThing(toTile, cloneItem(item, remainder), index)
                }
            }
        } else {
            addThing(toTile, item, index)
        }

        return ReturnValue.NOERROR
    }

    /**
     * `Game::internalRemoveItem` (src/game/game.cpp:2785). The parent `Cylinder` is
     * resolved from the item's tracked parent tile (the facade's `Item::getParent()`);
     * `count == -1` removes the whole stack, partial stackable counts decrement in
     * place and a full removal unlinks the item.
     *
     * Parity gaps: `Item::canRemove`, map-loaded decay protection and the
     * `FLAG_IGNORENOTMOVABLE`/`Item::isMovable` check cannot be evaluated because the
     * Kotlin [ItemType] does not model movable/decay state.
     */
    fun internalRemoveItem(
        item: Item?,
        count: Int = -1,
        test: Boolean = false,
        flags: Int = 0,
        force: Boolean = false,
    ): ReturnValue {
        if (item == null) {
            return ReturnValue.NOTPOSSIBLE
        }
        val tile = itemParents[item] ?: return ReturnValue.NOTPOSSIBLE

        val removeCount = if (count == -1) item.count else count
        val ret = queryRemove(tile, item, removeCount, flags or CylinderFlags.FLAG_IGNORENOTMOVABLE)
        if (!force && ret != ReturnValue.NOERROR) {
            return ret
        }

        if (!test) {
            removeThing(tile, item, removeCount)
        }
        return ReturnValue.NOERROR
    }

    /**
     * `Game::internalMoveItem` (src/game/game.cpp:2436).
     *
     * Transcribes the straight tile-to-tile path: same-top-item short circuit,
     * `queryRemove`/`queryAdd`/`queryMaxCount`, source removal, stackable merge with
     * the cap of `Item::getStackSize()` and clone-on-partial-move
     * (game.cpp:2566-2611). The `movedItem` out parameter becomes unnecessary because
     * a partial stack move leaves the original on the source and adds a clone on the
     * destination.
     *
     * Parity gaps: the `browseFields` remap (game.cpp:2447-2453),
     * `RETURNVALUE_NEEDEXCHANGE` (hangable/supports, game.cpp:2494-2524), the
     * `count == 255` client fix (game.cpp:2481), `tradeItem`, `actor` notifications and
     * the clone's attributes/decay state are not modeled.
     */
    fun internalMoveItem(
        fromTile: Tile?,
        toTile: Tile?,
        index: Int = INDEX_WHEREEVER,
        item: Item?,
        count: Int = 1,
        flags: Int = 0,
    ): ReturnValue {
        if (fromTile == null || toTile == null || item == null) {
            return ReturnValue.NOTPOSSIBLE
        }

        val toItem = topDownItem(toTile)
        if (item === toItem) {
            return ReturnValue.NOERROR
        }

        var ret = queryRemove(fromTile, item, 1, flags)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        ret = queryAdd(toTile, flags)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        val maxQueryCount = queryMaxCount(count)
        val m = if (isStackable(item)) minOf(count, maxQueryCount) else maxQueryCount

        ret = queryRemove(fromTile, item, m, flags)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        removeThing(fromTile, item, m)

        if (isStackable(item)) {
            val n = if (toItem != null && sameItemType(item, toItem)) mergeCapacity(toItem, m) else 0
            if (n > 0 && toItem != null) {
                toItem.count += n
            }

            val newCount = m - n
            if (newCount > 0) {
                addThing(toTile, cloneItem(item, newCount), index)
            }
        } else {
            addThing(toTile, item, index)
        }

        return ReturnValue.NOERROR
    }

    /** `Tile::getTopDownItem` equivalent (src/items/tile.cpp:408): last inserted down item. */
    private fun topDownItem(tile: Tile): Item? = tile.items.lastOrNull()

    /** `Tile::getThingIndex` equivalent (src/items/tile.cpp:1443): ground is 0, then item list. */
    private fun thingIndex(tile: Tile, item: Item): Int {
        if (item === tile.ground) {
            return 0
        }
        val index = tile.items.indexOfFirst { it === item }
        return if (index < 0) -1 else index + if (tile.ground != null) 1 else 0
    }

    /** `Item::isStackable` (src/items/item.hpp:488). */
    private fun isStackable(item: Item): Boolean = item.type?.isStackable == true

    /** Subset of `Item::equals` (src/items/item.cpp:730): id and stackable flag. */
    private fun sameItemType(item: Item, other: Item?): Boolean =
        other != null && item.id == other.id && isStackable(item) && isStackable(other)

    /** `Item::getStackSize` (src/items/item.hpp:595) with Canary's `stackSize` default of 100. */
    private fun mergeCapacity(toItem: Item, m: Int): Int {
        val capacity = stackSize(toItem) - toItem.count
        return if (capacity <= 0) m else minOf(capacity, m)
    }

    private fun stackSize(item: Item): Int = if (isStackable(item)) DEFAULT_STACK_SIZE else 1

    private fun cloneItem(item: Item, count: Int): Item = Item(id = item.id, count = count, type = item.type)

    /**
     * `Tile::addThing(int32_t, Thing)` (src/items/tile.cpp:1131). The index is accepted
     * for parity but ignored, exactly like the C++ overload does for plain down items.
     */
    private fun addThing(tile: Tile, item: Item, index: Int) {
        tile.addItem(item)
        itemParents[item] = tile
    }

    /** `Tile::queryAdd` item branch (src/items/tile.cpp:691-962). */
    private fun queryAdd(tile: Tile, flags: Int): ReturnValue {
        if (flags and CylinderFlags.FLAG_NOLIMIT != 0) {
            return ReturnValue.NOERROR
        }
        if (tile.items.size >= MAX_TILE_ITEMS) {
            return ReturnValue.NOTPOSSIBLE
        }
        if (tile.ground == null) {
            return ReturnValue.NOTPOSSIBLE
        }
        return ReturnValue.NOERROR
    }

    /** `Tile::queryMaxCount` (src/items/tile.cpp:978). */
    private fun queryMaxCount(count: Int): Int = maxOf(1, count)

    /** `Tile::queryRemove` (src/items/tile.cpp:983). */
    private fun queryRemove(tile: Tile, item: Item, count: Int, flags: Int): ReturnValue {
        if (thingIndex(tile, item) < 0) {
            return ReturnValue.NOTPOSSIBLE
        }
        if (count == 0) {
            return ReturnValue.NOTPOSSIBLE
        }
        if (isStackable(item) && count > item.count) {
            return ReturnValue.NOTPOSSIBLE
        }
        return ReturnValue.NOERROR
    }

    /** `Tile::removeThing` (src/items/tile.cpp:1344): stackable partial decrement, otherwise unlink. */
    private fun removeThing(tile: Tile, item: Item, count: Int) {
        if (item === tile.ground) {
            tile.ground = null
            itemParents.remove(item)
            return
        }

        if (isStackable(item) && count != item.count) {
            item.count = maxOf(0, item.count - count)
        } else {
            tile.removeItem(item)
            itemParents.remove(item)
        }
    }

    /** `Tile::queryAdd` creature branch (src/items/tile.cpp:700-854). */
    private fun canAddCreature(tile: Tile): Boolean {
        if (tile.ground == null) {
            return false
        }
        return placedCreatures.values.none { it === tile }
    }

    /** `Map::placeCreature` fallback search (src/map/map.cpp:587-663). */
    private fun findFallbackTile(centerPos: Position, extendedPos: Boolean, forced: Boolean): Pair<Tile, Position>? {
        val relList = if (extendedPos) EXTENDED_REL_LIST else NORMAL_REL_LIST
        for ((dx, dy) in relList.shuffled()) {
            val tryPos = Position(centerPos.x + dx, centerPos.y + dy, centerPos.z)
            val tile = map.getTile(tryPos) ?: continue
            if (forced || canAddCreature(tile)) {
                return tile to tryPos
            }
        }
        return null
    }

    companion object {
        /** `INDEX_WHEREEVER` (src/items/cylinder.hpp:18). */
        const val INDEX_WHEREEVER = -1

        /** `ItemType::stackSize` default (src/items/items.hpp:335). */
        private const val DEFAULT_STACK_SIZE = 100

        /** `Tile::queryAdd` item list ceiling (src/items/tile.cpp:899). */
        private const val MAX_TILE_ITEMS = 0x3E8

        /** `Map::placeCreature` extended search ring (src/map/map.cpp:588). */
        private val EXTENDED_REL_LIST = listOf(
            0 to -2,
            -1 to -1, 0 to -1, 1 to -1,
            -2 to 0, -1 to 0, 1 to 0, 2 to 0,
            -1 to 1, 0 to 1, 1 to 1,
            0 to 2,
        )

        /** `Map::placeCreature` normal search ring (src/map/map.cpp:603). */
        private val NORMAL_REL_LIST = listOf(
            -1 to -1, 0 to -1, 1 to -1,
            -1 to 0, 1 to 0,
            -1 to 1, 0 to 1, 1 to 1,
        )
    }
}
