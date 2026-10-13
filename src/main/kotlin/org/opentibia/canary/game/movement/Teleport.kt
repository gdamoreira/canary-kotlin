package org.opentibia.canary.game.movement

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.game.Game
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * World seam replacing the `g_game()` calls of `Teleport::addThing`
 * (src/game/movement/teleport.cpp:70-113). Every method transcribes an existing
 * `Game`/`Map` member; the world is injected per AGENTS.md 1.1.
 */
interface TeleportWorld {
    /** `g_game().map.getTile(pos)` (src/map/map.cpp). */
    fun getTile(position: Position): Tile?

    /** `Game::internalCreatureTurn` (src/game/game.cpp:1920-1950). */
    fun internalCreatureTurn(creature: Creature, direction: Direction)

    /** `Map::moveCreature` (src/map/map.cpp:793). */
    fun moveCreature(creature: Creature, destTile: Tile)

    /** `Game::addMagicEffect` (src/game/game.cpp). */
    fun addMagicEffect(position: Position, effect: Int)

    /** `Game::internalMoveItem` (src/game/game.cpp:2436). */
    fun internalMoveItem(
        fromTile: Tile?,
        toTile: Tile?,
        index: Int,
        item: Item?,
        count: Int,
        flags: Int,
    ): ReturnValue
}

/**
 * Faithful port of Canary's `Teleport` (src/game/movement/teleport.hpp:17-62,
 * teleport.cpp:16-133), the item that moves creatures and items standing on it
 * to [destPos].
 *
 * Canary reads the teleport's own tile position through `Item::getPosition()`
 * (the parent tile); Kotlin items do not track their parent, so [addThing] and
 * [checkInfinityLoop] receive the containing [Tile] (or its position) explicitly.
 * The generic `Cylinder` plumbing (`updateThing`, `replaceThing`,
 * `removeThing`, `postAdd/RemoveNotification`) has no Kotlin host and is not
 * ported.
 */
class Teleport(
    id: Int,
    destPos: Position = Position(0, 0, 0),
    type: ItemType? = null,
) : Item(id, type = type) {

    /** `Teleport::destPos` (src/game/movement/teleport.hpp:61). */
    private var destPosValue: Position = destPos

    /** `Teleport::getDestPos` (src/game/movement/teleport.hpp:34-36). */
    fun getDestPos(): Position = destPosValue

    /** `Teleport::setDestPos` (src/game/movement/teleport.hpp:37-39). */
    fun setDestPos(pos: Position) {
        destPosValue = pos
    }

    /** `Teleport::getTeleport` (src/game/movement/teleport.hpp:22-24). */
    override fun getTeleport(): Teleport = this

    /** `Item::items[id].magicEffect` read by `Teleport::addThing` (teleport.cpp:94). */
    val magicEffect: Int get() = type?.magicEffect ?: CONST_ME_NONE

    /**
     * `Teleport::queryAdd` (src/game/movement/teleport.cpp:35-37): a teleport can
     * never accept a thing through the normal add path.
     */
    fun queryAdd(): ReturnValue = ReturnValue.NOTPOSSIBLE

    /** `Teleport::queryMaxCount` (src/game/movement/teleport.cpp:39-41). */
    fun queryMaxCount(): ReturnValue = ReturnValue.NOTPOSSIBLE

    /** `Teleport::queryRemove` (src/game/movement/teleport.cpp:43-45). */
    fun queryRemove(): ReturnValue = ReturnValue.NOERROR

    /** `Teleport::queryDestination` (src/game/movement/teleport.cpp:47-49): itself. */
    fun queryDestination(): Teleport = getTeleport()

    /**
     * `Teleport::checkInfinityLoop` (src/game/movement/teleport.cpp:51-64).
     *
     * When the destination tile holds a teleport whose destination is
     * [selfPosition] (the tile this teleport sits on, `getPosition()` in C++), the
     * pair would bounce forever and `true` is returned; otherwise the check
     * recurses through the destination teleport's `destPos`.
     *
     * PARITY GAP: like Canary, a detached cycle that never returns to
     * [selfPosition] recurses without a visited set.
     */
    fun checkInfinityLoop(destTile: Tile?, selfPosition: Position, world: TeleportWorld): Boolean {
        if (destTile == null) {
            return false
        }

        val teleport = getTeleportItem(destTile)
        if (teleport != null) {
            val nextDestPos = teleport.getDestPos()
            if (selfPosition == nextDestPos) {
                return true
            }
            return checkInfinityLoop(world.getTile(nextDestPos), selfPosition, world)
        }

        return false
    }

    /**
     * `Teleport::addThing` creature branch (src/game/movement/teleport.cpp:70-104).
     *
     * The creature is turned west when it came from east of [destPos] and east
     * otherwise (`origPos.x > destPos.x ? WEST : EAST`, teleport.cpp:98), moved to
     * the destination tile and, when the item type carries a magic effect, the
     * effect is shown at the original and the destination positions.
     */
    fun addThing(creature: Creature, teleportTile: Tile, world: TeleportWorld) {
        if (destPosValue.x == 0 && destPosValue.y == 0 && destPosValue.z == 0) {
            return
        }

        val destTile = world.getTile(destPosValue) ?: return
        if (checkInfinityLoop(destTile, teleportTile.position, world)) {
            return
        }

        val origPos = creature.position
        world.internalCreatureTurn(creature, if (origPos.x > destPosValue.x) Direction.West else Direction.East)
        world.moveCreature(creature, destTile)

        val effect = magicEffect
        if (effect != CONST_ME_NONE) {
            world.addMagicEffect(origPos, effect)
            world.addMagicEffect(destTile.position, effect)
        }
    }

    /**
     * `Teleport::addThing` item branch (src/game/movement/teleport.cpp:105-112).
     *
     * The effect is shown at the destination first and then at the item's own
     * position, before `Game::internalMoveItem(getTile(), destTile,
     * INDEX_WHEREEVER, item, item->getItemCount(), nullptr)` moves the stack.
     */
    fun addThing(item: Item, teleportTile: Tile, world: TeleportWorld) {
        if (destPosValue.x == 0 && destPosValue.y == 0 && destPosValue.z == 0) {
            return
        }

        val destTile = world.getTile(destPosValue) ?: return
        if (checkInfinityLoop(destTile, teleportTile.position, world)) {
            return
        }

        val effect = magicEffect
        if (effect != CONST_ME_NONE) {
            world.addMagicEffect(destTile.position, effect)
            world.addMagicEffect(teleportTile.position, effect)
        }

        world.internalMoveItem(
            teleportTile,
            destTile,
            Game.INDEX_WHEREEVER,
            item,
            item.count,
            0,
        )
    }

    /**
     * `Tile::getTeleportItem` (src/items/tile.cpp:245-258) restricted to the
     * Kotlin tile model: the item list is scanned in reverse and the
     * `TILESTATE_TELEPORT` fast path is not modelled.
     */
    private fun getTeleportItem(tile: Tile): Teleport? =
        tile.items.asReversed().firstOrNull { it is Teleport } as Teleport?

    companion object {
        /** `CONST_ME_NONE` (src/utils/utils_definitions.hpp:59). */
        private const val CONST_ME_NONE = 0
    }
}
